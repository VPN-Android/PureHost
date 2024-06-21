package top.nicelee.purehost.vpn;

import android.net.VpnService;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.regex.Matcher;

import me.smartproxy.core.HttpHostHeaderParser;
import top.nicelee.purehost.vpn.config.ConfigReader;
import top.nicelee.purehost.vpn.dns.DnsPacket;
import top.nicelee.purehost.vpn.dns.Question;
import top.nicelee.purehost.vpn.dns.ResourcePointer;
import top.nicelee.purehost.vpn.server.NATSession;
import top.nicelee.purehost.vpn.server.TCPServer;
import top.nicelee.purehost.vpn.server.TcpProxyServer;
import top.nicelee.purehost.vpn.server.UDPServer;
import top.nicelee.purehost.vpn.ip.CommonMethods;
import top.nicelee.purehost.vpn.ip.IPHeader;
import top.nicelee.purehost.vpn.ip.TCPHeader;
import top.nicelee.purehost.vpn.ip.UDPHeader;
import  top.nicelee.purehost.vpn.server.NATSessionManager;

public class LocalVpnService extends VpnService implements Runnable {

    private static final String TAG = "LocalVpnService";
    
    public static LocalVpnService Instance;
    ParcelFileDescriptor fileDescriptor;
    FileInputStream vpnInput;
    FileOutputStream vpnOutput;

    //收到的IP报文Buffer
    byte[] m_Packet;

    //方便解析
    IPHeader m_IPHeader;
    TCPHeader m_TCPHeader;
    UDPHeader m_UDPHeader;
    ByteBuffer m_DNSBuffer;

    String localIP = "10.8.0.2";
    int intLocalIP = CommonMethods.ipStringToInt(localIP);
//    TCPServer tcpServer;
    TcpProxyServer tcpServer;
    UDPServer udpServer;

    boolean isClosed = false;
    public void stopVPN(){
        if(isClosed)
            return;

        Log.d(TAG, "销毁程序调用中...");

        udpServer.stop();
        //tcpServer.stop();
        try{
            vpnInput.close();
            vpnOutput.close();
            fileDescriptor.close();
            fileDescriptor = null;
        }catch (Exception e){
            e.printStackTrace();
        }
        stopSelf();
        isClosed= true;
    }
    @Override
    public void onDestroy(){
        super.onDestroy();
        //stopVPN();
    }
    @Override
    public void onCreate()
    {
        super.onCreate();
        isClosed = false;
        m_Packet = new byte[20000];
        m_IPHeader = new IPHeader(m_Packet, 0);
        m_TCPHeader = new TCPHeader(m_Packet, 20);
        m_UDPHeader = new UDPHeader(m_Packet, 20);
        m_DNSBuffer = ((ByteBuffer) ByteBuffer.wrap(m_Packet).position(28)).slice();

        Builder builder = new Builder();
        //builder.setMtu(...);
        builder.addAddress(localIP, 24);
        builder.addRoute("0.0.0.0", 0);
        builder.setSession("PureHost");
        //builder.addDnsServer("222.66.251.8");
        if(ConfigReader.dnsList.isEmpty()){
            ConfigReader.initDNS(this);
            Log.d(TAG,"根据系统默认DNS初始化...");
        }else{
            Log.d(TAG,"根据提供的DNS初始化...");
        }
        for(String dns :ConfigReader.dnsList){
            builder.addDnsServer(dns);
            builder.addRoute(dns, 32);
        }
        ConfigReader.dnsList.clear();
//        try{
//            Log.d(TAG,"\n\n\n\n\n\n\n创建Server!!!!!!!!!!!!!!!!!!!!!!!!!\n\n\n\n\n\n\n\n\n\n" + this.getPackageName());
//            //将本程序加入VPN列表
//            builder.addAllowedApplication(this.getPackageName());
//
//            PackageManager packageManager= this.getPackageManager();
//            List<PackageInfo> list=packageManager.getInstalledPackages(0);
//            for(PackageInfo pkg :  list){
//                Log.d(TAG,pkg.packageName + "加入列表");
//                builder.addAllowedApplication(pkg.packageName);
//            }
//        }catch (Exception e){
//            e.printStackTrace();
//            Log.d(TAG,"\n\n\n\n\n\n\n创建Server出现错误!!!!!!!!!!!!!!!!!!!!!!!!!\n\n\n\n\n\n\n\n\n\n" + this.getPackageName());
//        }

        //builder.addDnsServer(...);
        //builder.addSearchDomain(...);
        //builder.setConfigureIntent(...);

        fileDescriptor = builder.establish();
        vpnInput = new FileInputStream(fileDescriptor.getFileDescriptor());
        vpnOutput = new FileOutputStream(fileDescriptor.getFileDescriptor());

        Instance = this;
//        tcpServer = new TCPServer(localIP);
        tcpServer = new TcpProxyServer(0);
        udpServer = new UDPServer(localIP);
        Thread th = new Thread(this);
        th.setName("VPN Service - Thread");
        th.start();

        tcpServer.start();
        udpServer.start();
    }

    @Override
    public void run() {
        int size = 0;
        try{
            Log.d(TAG,"读取报文中!!!!!!!!!!!!!!!!!!!!!!!!!");
            while ((size = vpnInput.read(m_Packet)) >= 0 ){
                if (isClosed) {
                    vpnInput.close();
                    vpnOutput.close();
                    throw new Exception("LocalServer stopped.");
                }
                if( size == 0){
                    continue;
                }
                Log.d(TAG,"读取报文中!!!!!!!!!!!!!!!!!!!!!!!!!");
                onIPPacketReceived(m_IPHeader, size);
            }
        }catch (Exception e){
            //e.printStackTrace();
            Log.d(TAG,"接收报文出现错误!!!!!!!!!!!!!!!!!!!!!!!!!");
        }finally {
            stopVPN();
        }

    }

    void onIPPacketReceived(IPHeader ipHeader, int size) throws IOException {
        //Log.d(TAG,"LocalVpnService: 收到IP报文"+ size +"!!!!!!!!!!!!!!!!!!!!!!!!!" + ipHeader.toString());
        switch (ipHeader.getProtocol()) {
            case IPHeader.TCP:
                TCPHeader tcpHeader = m_TCPHeader;
                tcpHeader.m_Offset = ipHeader.getHeaderLength();
                Log.d(TAG + "_TCP","LocalVpnService: TCP消息:"+ipHeader.toString() + "tcp: "+ tcpHeader.toString());

                if (ipHeader.getSourceIP() == intLocalIP) {
                    // 收到本地 TcpProxyServer 服务器数据
                    if (ipHeader.getSourceIP() == tcpServer.Port) {
                        Log.d(TAG + "_TCP",tcpHeader.getSourcePort() +", 目的端口:"+ ((int)tcpHeader.getDestinationPort()) );

                        NATSession session = NATSessionManager.getSession(tcpHeader.getDestinationPort());
                        if (session != null) {
                            ipHeader.setSourceIP(ipHeader.getDestinationIP());
                            tcpHeader.setSourcePort(session.RemotePort);
                            ipHeader.setDestinationIP(intLocalIP);

                            CommonMethods.ComputeTCPChecksum(ipHeader, tcpHeader);
                            Log.d(TAG + "_TCP","TCP 1 NAT");
                            vpnOutput.write(ipHeader.m_Data, ipHeader.m_Offset, size);
                            vpnOutput.flush();
                        } else {
                            System.out.printf("NoSession: %s %s\n", ipHeader.toString(), tcpHeader.toString());
                        }
                    } else {
                        // 添加端口映射
                        Log.d(TAG + "_TCP", "添加端口映射");
                        int portKey = tcpHeader.getSourcePort();
                        NATSession session = NATSessionManager.getSession(portKey);
                        if(session==null||session.RemoteIP!=ipHeader.getDestinationIP()||session.RemotePort!=tcpHeader.getDestinationPort()){
                            session=NATSessionManager.createSession(portKey, ipHeader.getDestinationIP(), tcpHeader.getDestinationPort());
                        }
                        session.LastNanoTime=System.nanoTime();
                        session.PacketSent++;//注意顺序

                        int tcpDataSize=ipHeader.getDataLength()-tcpHeader.getHeaderLength();
                        if(session.PacketSent==2&&tcpDataSize==0){
                            return;//丢弃tcp握手的第二个ACK报文。因为客户端发数据的时候也会带上ACK，这样可以在服务器Accept之前分析出HOST信息。
                        }

                        //分析数据，找到host
                        if(session.BytesSent==0&&tcpDataSize>10){
                            int dataOffset=tcpHeader.m_Offset+tcpHeader.getHeaderLength();
                            String host= HttpHostHeaderParser.parseHost(tcpHeader.m_Data, dataOffset, tcpDataSize);
                            if(host!=null){
                                session.RemoteHost=host;
                            }
                        }

                        // 转发给本地 TcpProxyServer 服务器
                        ipHeader.setSourceIP(ipHeader.getDestinationIP());
                        ipHeader.setDestinationIP(intLocalIP);
                        tcpHeader.setDestinationPort(tcpServer.Port);

                        CommonMethods.ComputeTCPChecksum(ipHeader, tcpHeader);

                        Log.d(TAG + "_TCP","TCP 2 NAT");

                        vpnOutput.write(ipHeader.m_Data, ipHeader.m_Offset, size);
                        vpnOutput.flush();
                        session.BytesSent+=tcpDataSize;//注意顺序
                    }
                }
                break;
            case IPHeader.UDP:

                UDPHeader udpHeader = m_UDPHeader;
                udpHeader.m_Offset = ipHeader.getHeaderLength();
                int originIP = ipHeader.getSourceIP();
                short originPort = udpHeader.getSourcePort();
                int dstIP = ipHeader.getDestinationIP();
                short dstPort = udpHeader.getDestinationPort() ;

                Log.d(TAG + "_UDP","LocalVpnService: 收到一个转发到UDPServer的包, 来源: " + udpHeader.getSourcePort() +", 目的端口:"+ ((int)udpHeader.getDestinationPort()) );

                //本地报文, 转发给本地UDP服务器
                if (ipHeader.getSourceIP() == intLocalIP  && udpHeader.getSourcePort() != udpServer.port) {
//                if (ipHeader.getSourceIP() == intLocalIP && udpHeader.getDestinationPort() == 53) {
//                if (ipHeader.getDestinationIP() != CommonMethods.ipStringToInt(udpServer.localIP)){

                    try{
                        m_DNSBuffer.clear();
                        m_DNSBuffer.limit(ipHeader.getDataLength() - 8);
                        DnsPacket dnsPacket = DnsPacket.FromBytes(m_DNSBuffer);
                        //Short dnsId = dnsPacket.Header.getID();

                        boolean isNeedPollution = false;
                        Question question = dnsPacket.Questions[0];
                        Log.d(TAG + "_UDP","DNS 查询的地址是" + question.Domain);
                        String ipAddr = ConfigReader.domainIpMap.get(question.Domain);
                        if (ipAddr != null) {
                            isNeedPollution = true;
                        }else{
                            Matcher matcher = ConfigReader.patternRootDomain.matcher(question.Domain);
                            if(matcher.find()){
                                Log.d(TAG + "_UDP","DNS 查询的地址根目录是" + matcher.group(1));
                                ipAddr = ConfigReader.rootDomainIpMap.get(matcher.group(1));
                                if (ipAddr != null){
                                    isNeedPollution = true;
                                }
                            }
                        }
                        if(isNeedPollution){
                            createDNSResponseToAQuery(udpHeader.m_Data, dnsPacket, ipAddr);

                            ipHeader.setTotalLength(20 + 8 + dnsPacket.Size);
                            udpHeader.setTotalLength(8 + dnsPacket.Size);

                            ipHeader.setSourceIP(dstIP);
                            udpHeader.setSourcePort(dstPort);
                            ipHeader.setDestinationIP(originIP);
                            udpHeader.setDestinationPort(originPort);

                            Log.d(TAG + "_UDP","第2次NAT UDP");
                            CommonMethods.ComputeUDPChecksum(ipHeader, udpHeader);
                            vpnOutput.write(ipHeader.m_Data, ipHeader.m_Offset, ipHeader.getTotalLength());
                            vpnOutput.flush();
                            break;
                        }
                    }catch (Exception e){
                        Log.d(TAG,"当前udp包不是DNS报文");
                    }

                    if(NATSessionManager.getSession(originPort) == null){
                        NATSessionManager.createSession(originPort, dstIP, dstPort);
                    }
                    ipHeader.setSourceIP(CommonMethods.ipStringToInt(UDPServer.localIP));
                    //udpHeader.setSourcePort(originPort);
                    ipHeader.setDestinationIP(intLocalIP);
                    udpHeader.setDestinationPort((short)udpServer.port);

                    ipHeader.setProtocol(IPHeader.UDP);
                    CommonMethods.ComputeUDPChecksum(ipHeader, udpHeader);


                    Log.d(TAG + "_UDP","第一次NAT UDP");
                    vpnOutput.write(ipHeader.m_Data, ipHeader.m_Offset, ipHeader.getTotalLength());
                    vpnOutput.flush();
                    Log.d(TAG + "_UDP","LocalVpnService: 本地UDP信息转发给服务器:"+ipHeader.toString() + "udp端口" +udpServer.port + "session: "+ originPort);
                }else{
                    Log.d(TAG + "_UDP","LocalVpnService: 其它UDP信息,不做处理:"+ipHeader.toString());
                    Log.d(TAG + "_UDP","LocalVpnService: 其它UDP信息,不做处理:"+udpHeader.toString());
                    //vpnOutput.write(ipHeader.m_Data, ipHeader.m_Offset, ipHeader.getTotalLength());
                }
                break;
            default:
                 //vpnOutput.write(ipHeader.m_Data, ipHeader.m_Offset, size);
                 break;
        }
    }

    public void createDNSResponseToAQuery(byte[] rawData, DnsPacket dnsPacket, String ipAddr) {
        Question question = dnsPacket.Questions[0];

        dnsPacket.Header.setResourceCount((short) 1);
        dnsPacket.Header.setAResourceCount((short) 0);
        dnsPacket.Header.setEResourceCount((short) 0);

        ResourcePointer rPointer = new ResourcePointer(rawData, question.Offset() + question.Length());
        rPointer.setDomain((short) 0xC00C);
        rPointer.setType(question.Type);
        rPointer.setClass(question.Class);
        rPointer.setTTL(300);
        rPointer.setDataLength((short) 4);
        rPointer.setIP(CommonMethods.ipStringToInt(ipAddr));

        dnsPacket.Size = 12 + question.Length() + 16;

    }
    public void sendUDPPacket(IPHeader ipHeader, UDPHeader udpHeader) {
        try {
            CommonMethods.ComputeUDPChecksum(ipHeader, udpHeader);
            this.vpnOutput.write(ipHeader.m_Data, ipHeader.m_Offset, ipHeader.getTotalLength());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}