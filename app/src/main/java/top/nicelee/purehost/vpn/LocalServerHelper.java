package top.nicelee.purehost.vpn;

import android.os.ParcelFileDescriptor;
import android.util.Log;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.regex.Matcher;

import top.nicelee.purehost.vpn.config.ConfigReader;
import top.nicelee.purehost.vpn.dns.DnsPacket;
import top.nicelee.purehost.vpn.dns.Question;
import top.nicelee.purehost.vpn.dns.ResourcePointer;
import top.nicelee.purehost.vpn.ip.CommonMethods;
import top.nicelee.purehost.vpn.ip.IPHeader;
import top.nicelee.purehost.vpn.ip.TCPHeader;
import top.nicelee.purehost.vpn.ip.UDPHeader;
import top.nicelee.purehost.vpn.server.NATSession;
import top.nicelee.purehost.vpn.server.NATSessionManager;
import top.nicelee.purehost.vpn.server.TCPServer;
import top.nicelee.purehost.vpn.server.UDPServer;

public class LocalServerHelper {

    private static final String TAG = "LocalVpnService";

    String vpnLocalIP = "168.168.168.168";
    int vpnLocalIPInt = CommonMethods.ipStringToInt(vpnLocalIP);
    TCPServer tcpServer;
    UDPServer udpServer;

    //方便解析
    private IPHeader m_IPHeader;
    private TCPHeader m_TCPHeader;
    private UDPHeader m_UDPHeader;
    private ByteBuffer m_DNSBuffer;
    private FileOutputStream vpnOutput;

    public void createServer(LocalVpnServiceKT vpnService, ParcelFileDescriptor fileDescriptor, byte[] buffer) {

        vpnOutput = new FileOutputStream(fileDescriptor.getFileDescriptor());

        m_IPHeader = new IPHeader(buffer, 0);
        m_TCPHeader = new TCPHeader(buffer, 20);
        m_UDPHeader = new UDPHeader(buffer, 20);
        m_DNSBuffer = ((ByteBuffer) ByteBuffer.wrap(buffer).position(28)).slice();


        tcpServer = new TCPServer(vpnService, vpnLocalIP);
        udpServer = new UDPServer(vpnService, vpnLocalIP);
        //tcpServer.start();
        udpServer.start();
    }

    public void onPacketReceived(int size) {
        switch (m_IPHeader.getProtocol()) {
            case IPHeader.TCP:
                onTCPPacketReceived(size);
                break;
            case IPHeader.UDP:
                onUDPPacketReceived(this.vpnOutput, this.m_IPHeader, this.m_UDPHeader, this.m_DNSBuffer, size);
                break;
            default:
                //Log.e(TAG, "不支持的协议类型: " + m_IPHeader.getProtocol());
        }
    }

    private void onTCPPacketReceived(int size) {
        if (tcpServer == null) {
            return;
        }

        FileOutputStream vpnOutput = this.vpnOutput;

        TCPHeader m_TCPHeader = this.m_TCPHeader;
        IPHeader ipHeader = this.m_IPHeader;

        m_TCPHeader.m_Offset = ipHeader.getHeaderLength();

        Log.d(TAG, "TCP消息:" + ipHeader + "tcp: " + m_TCPHeader);
        if (ipHeader.getDestinationIP() == CommonMethods.ipStringToInt(tcpServer.localIP)) {
            //来自TCP服务器
            NATSession session = NATSessionManager.getSession(m_TCPHeader.getDestinationPort());
            if (session != null) {
                ipHeader.setSourceIP(session.RemoteIP);
                m_TCPHeader.setSourcePort(session.RemotePort);
                ipHeader.setDestinationIP(vpnLocalIPInt);

                CommonMethods.ComputeTCPChecksum(ipHeader, m_TCPHeader);
                try {
                    vpnOutput.write(ipHeader.m_Data, ipHeader.m_Offset, size);
                    vpnOutput.flush();
                } catch (IOException e) {
                    Log.e(TAG, "发送TCP数据包失败:" + e);
                }
            } else {
                Log.d(TAG, "NoSession:" + ipHeader + ", " + m_TCPHeader);
            }
        } else {
            //来自本地
            // 添加端口映射
            int portKey = m_TCPHeader.getSourcePort();
            NATSession session = NATSessionManager.getSession(portKey);
            if (session == null || session.RemoteIP != ipHeader.getDestinationIP() || session.RemotePort != m_TCPHeader.getDestinationPort()) {
                session = NATSessionManager.createSession(portKey, ipHeader.getDestinationIP(), m_TCPHeader.getDestinationPort());
                Log.d(TAG, "LocalVpnService Session: key Port: " + portKey);
                Log.d(TAG, "LocalVpnService Session: ip : " + CommonMethods.ipIntToString(ipHeader.getDestinationIP()));
                Log.d(TAG, "LocalVpnService Session: port : " + (int) (m_TCPHeader.getDestinationPort()));
            }
            ipHeader.setSourceIP(CommonMethods.ipStringToInt(tcpServer.localIP));
            //tcpHeader.setSourcePort((short)13221);
            ipHeader.setDestinationIP(vpnLocalIPInt);
            m_TCPHeader.setDestinationPort((short) tcpServer.port);
            CommonMethods.ComputeTCPChecksum(ipHeader, m_TCPHeader);
            try {
                vpnOutput.write(ipHeader.m_Data, ipHeader.m_Offset, size);
                vpnOutput.flush();
            } catch (IOException e) {
                Log.e(TAG, "发送TCP数据包失败:" + e);
            }
        }
    }

    private void onUDPPacketReceived(FileOutputStream vpnOutput, IPHeader ipHeader, UDPHeader udpHeader, ByteBuffer dnsBuffer, int size) {
        if (udpServer == null) {
            Log.e(TAG, "UDP服务未启动");
            return;
        }

        Log.d(TAG, "UDP, udpHeader.m_Offset: " + udpHeader.m_Offset + ", getHeaderLength:" + ipHeader.getHeaderLength());

        udpHeader.m_Offset = ipHeader.getHeaderLength();

        int originSourceIP = ipHeader.getSourceIP();
        short originSourcePort = udpHeader.getSourcePort();
        int dstIP = ipHeader.getDestinationIP();
        short dstPort = udpHeader.getDestinationPort();

        Log.d(TAG, "UDP, source port: " + originSourcePort + ", ===>> destination port:" + dstPort);

        //本地报文, 转发给[本地UDP服务器]
//        if (ipHeader.getSourceIP() == intLocalIP  && m_UDPHeader.getSourcePort() != udpServer.port) {
        if (dstIP != UDPServer.udpServerLocalIPInt) {
            try {
                dnsBuffer.clear();
                dnsBuffer.limit(ipHeader.getDataLength() - 8);
                DnsPacket dnsPacket = DnsPacket.FromBytes(dnsBuffer);
                //Short dnsId = dnsPacket.Header.getID();

                if (dnsPacket == null) {
                    Log.e(TAG, "UDP, DNS 解析失败, 丢弃数据包");
                    return;
                }

                boolean isNeedPollution = false;
                Question question = dnsPacket.Questions[0];
                Log.d(TAG, "UDP, DNS 查询的地址是:" + question.Domain);
                String ipAddr = ConfigReader.domainIpMap.get(question.Domain);
                if (ipAddr != null) {
                    isNeedPollution = true;
                } else {
                    Matcher matcher = ConfigReader.patternRootDomain.matcher(question.Domain);
                    if (matcher.find()) {
                        Log.d(TAG, "UDP, DNS 查询的地址根目录是: " + matcher.group(1));
                        ipAddr = ConfigReader.rootDomainIpMap.get(matcher.group(1));
                        if (ipAddr != null) {
                            isNeedPollution = true;
                        }
                    }
                }
                if (isNeedPollution) {
                    createDNSResponseToAQuery(udpHeader.m_Data, dnsPacket, ipAddr);

                    ipHeader.setTotalLength(20 + 8 + dnsPacket.Size);
                    udpHeader.setTotalLength(8 + dnsPacket.Size);

                    ipHeader.setSourceIP(dstIP);
                    udpHeader.setSourcePort(dstPort);
                    ipHeader.setDestinationIP(originSourceIP);
                    udpHeader.setDestinationPort(originSourcePort);

                    CommonMethods.ComputeUDPChecksum(ipHeader, udpHeader);
                    vpnOutput.write(ipHeader.m_Data, ipHeader.m_Offset, ipHeader.getTotalLength());
                    vpnOutput.flush();
                } else {
                    if (NATSessionManager.getSession(originSourcePort) == null) {
                        NATSessionManager.createSession(originSourcePort, dstIP, dstPort);
                    }
                    ipHeader.setSourceIP(UDPServer.udpServerLocalIPInt);
                    //udpHeader.setSourcePort(originPort);
                    ipHeader.setDestinationIP(vpnLocalIPInt);
                    udpHeader.setDestinationPort((short) udpServer.port);

                    ipHeader.setProtocol(IPHeader.UDP);
                    CommonMethods.ComputeUDPChecksum(ipHeader, udpHeader);


                    vpnOutput.write(ipHeader.m_Data, ipHeader.m_Offset, ipHeader.getTotalLength());
                    vpnOutput.flush();
                    Log.d(TAG, "本地UDP信息转发给服务器:" + ipHeader + " udpServer端口:" + udpServer.port + " session: " + originSourcePort);
                }
            } catch (Exception e) {
                Log.d(TAG, "当前udp包不是DNS报文");
            }
        } else {
            Log.d(TAG, "其它UDP信息,不做处理:" + ipHeader);
            Log.d(TAG, "其它UDP信息,不做处理:" + udpHeader);
            //vpnOutput.write(ipHeader.m_Data, ipHeader.m_Offset, ipHeader.getTotalLength());
        }
    }

    public void stop() {

        Log.d(TAG, "销毁程序调用中...");

        if (tcpServer != null) {
            tcpServer.stop();
        }

        tcpServer = null;
        udpServer = null;

        if (vpnOutput != null) {
            try {
                vpnOutput.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void createDNSResponseToAQuery(byte[] rawData, DnsPacket dnsPacket, String ipAddr) {
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
            FileOutputStream vpnOutput = this.vpnOutput;
            CommonMethods.ComputeUDPChecksum(ipHeader, udpHeader);
            vpnOutput.write(ipHeader.m_Data, ipHeader.m_Offset, ipHeader.getTotalLength());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}