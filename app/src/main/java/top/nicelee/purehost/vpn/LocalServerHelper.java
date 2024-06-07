package top.nicelee.purehost.vpn;

import android.os.ParcelFileDescriptor;
import android.util.Log;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Locale;
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

    private static final String TAG = "LocalServerHelper";

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
        tcpServer.start();
        udpServer.start();
    }

    public void onPacketReceived(int size) {
        byte protocol = m_IPHeader.getProtocol();
        switch (protocol) {
            case IPHeader.UDP:
                onUDPPacketReceived(this.vpnOutput, this.m_IPHeader, this.m_UDPHeader, this.m_DNSBuffer, size);
                break;
            case IPHeader.TCP:
                onTCPPacketReceived(this.vpnOutput, this.m_IPHeader, this.m_TCPHeader, size);
                break;
            case IPHeader.ICMP:
                Log.e(TAG, "ICMP 无法支持 " + protocol);
                break;
            default:
                // https://en.wikipedia.org/wiki/List_of_IP_protocol_numbers
                Log.e(TAG, "不支持的协议类型: 0x" + Integer.toHexString(protocol & 0xFF));
        }
    }

    private void onUDPPacketReceived(FileOutputStream vpnOutput, IPHeader ipHeader, UDPHeader udpHeader, ByteBuffer dnsBuffer, int size) {
        if (udpServer == null) {
            Log.e(TAG + "_UDP", "UDP服务未启动");
            return;
        }


        Log.d(TAG + "_UDP", "从TUN读取到UDP消息:: " + CommonMethods.ipIntToString(ipHeader.getSourceIP()) + ":" + udpHeader.getSourcePortInt() + " -> " + CommonMethods.ipIntToString(ipHeader.getDestinationIP()) + ":" + udpHeader.getDestinationPortInt());

        int dstIP = ipHeader.getDestinationIP();

        //本地报文, 转发给[本地UDP服务器]
//        if (ipHeader.getSourceIP() == intLocalIP  && m_UDPHeader.getSourcePort() != udpServer.port) {
        if (dstIP != UDPServer.udpServerLocalIPInt) {
            try {
                dnsBuffer.clear();
                dnsBuffer.limit(ipHeader.getDataLength() - 8);
                DnsPacket dnsPacket = DnsPacket.FromBytes(dnsBuffer);
                //Short dnsId = dnsPacket.Header.getID();

                if (dnsPacket == null) {
                    Log.e(TAG + "_UDP", "UDP, DNS 解析失败, 丢弃数据包");
                    return;
                }

                boolean isNeedPollution = false;
                Question question = dnsPacket.Questions[0];
                String ipAddr = ConfigReader.domainIpMap.get(question.Domain);
                Log.d(TAG + "_UDP", "UDP, DNS 查询的地址是:" + question.Domain + " 查询结果：" + ipAddr + ", isNeedPollution:" + isNeedPollution);
                if (ipAddr != null) {
                    isNeedPollution = true;
                } else {
                    Matcher matcher = ConfigReader.patternRootDomain.matcher(question.Domain);
                    if (matcher.find()) {
                        ipAddr = ConfigReader.rootDomainIpMap.get(matcher.group(1));
                        if (ipAddr != null) {
                            isNeedPollution = true;
                        }
                        Log.d(TAG + "_UDP", "UDP, DNS 查询的地址根目录是: " + matcher.group(1) + ", 查询结果：" + ipAddr + ", isNeedPollution:" + isNeedPollution);
                    }
                }

                short originSourcePort = udpHeader.getSourcePort();
                short dstPort = udpHeader.getDestinationPort();

                if (isNeedPollution) {

                    createDNSResponseToAQuery(udpHeader.m_Data, dnsPacket, ipAddr);

                    ipHeader.setTotalLength(20 + 8 + dnsPacket.Size);
                    udpHeader.setTotalLength(8 + dnsPacket.Size);

                    ipHeader.setSourceIP(dstIP);
                    udpHeader.setSourcePort(dstPort);
                    ipHeader.setDestinationIP(ipHeader.getSourceIP());
                    udpHeader.setDestinationPort(originSourcePort);

                    CommonMethods.computeUDPChecksum(ipHeader, udpHeader);
                    vpnOutput.write(ipHeader.m_Data, ipHeader.m_Offset, ipHeader.getTotalLength());
                    vpnOutput.flush();
                } else {
                    if (NATSessionManager.getSession(originSourcePort) == null) {
                        NATSessionManager.createSession(originSourcePort, dstIP, dstPort);
                    }

                    //Log.d(TAG, "第一次NAT:" + ipHeader + " udpServer端口:" + udpServer.port + " session: " + originSourcePort + ", 0x" + (originSourcePort & 0xFFFF));
                    Log.d(TAG + "_UDP", "第一次NAT:: udpLocalServer: " + CommonMethods.ipIntToString(UDPServer.udpServerLocalIPInt) + ":" + udpServer.port);

                    Log.d(TAG + "_UDP", String.format(Locale.ENGLISH, "第一次NAT:: sourceIP:sourcePort, %s:%s -> %s:%s",
                            CommonMethods.ipIntToString(ipHeader.getSourceIP()), udpHeader.getSourcePortInt(),
                            CommonMethods.ipIntToString(UDPServer.udpServerLocalIPInt), udpHeader.getSourcePortInt()));

                    Log.d(TAG + "_UDP", String.format(Locale.ENGLISH, "第一次NAT:: dstIP:dstPort, %s:%s -> %s:%s",
                            CommonMethods.ipIntToString(ipHeader.getDestinationIP()), udpHeader.getDestinationPortInt(),
                            CommonMethods.ipIntToString(vpnLocalIPInt), (udpServer.port & 0xFFFF)));

                    Log.d(TAG + "_UDP", String.format(Locale.ENGLISH, "第一次NAT:: 最终：%s:%s -> %s:%s",
                            CommonMethods.ipIntToString(UDPServer.udpServerLocalIPInt), udpHeader.getSourcePortInt(),
                            CommonMethods.ipIntToString(vpnLocalIPInt), (udpServer.port & 0xFFFF)));

                    ipHeader.setSourceIP(UDPServer.udpServerLocalIPInt);    // 7.7.7.7:7777
                    //udpHeader.setSourcePort(originPort);
                    ipHeader.setDestinationIP(vpnLocalIPInt);
                    udpHeader.setDestinationPort((short) udpServer.port);

                    //ipHeader.setProtocol(IPHeader.UDP);
                    CommonMethods.computeUDPChecksum(ipHeader, udpHeader);


                    vpnOutput.write(ipHeader.m_Data, ipHeader.m_Offset, ipHeader.getTotalLength());
                    vpnOutput.flush();
                }
            } catch (Exception e) {
                Log.d(TAG + "_UDP", "当前udp包不是DNS报文");
            }
        } else {
            Log.d(TAG + "_UDP", "其它UDP信息,不做处理:" + ipHeader);
            Log.d(TAG + "_UDP", "其它UDP信息,不做处理:" + udpHeader);
            //vpnOutput.write(ipHeader.m_Data, ipHeader.m_Offset, ipHeader.getTotalLength());
        }
    }

    private void onTCPPacketReceived(FileOutputStream vpnOutput, IPHeader ipHeader, TCPHeader tcpHeader, int size) {

        Log.d(TAG + "_TCP", "从TUN读取到TCP消息:" + ipHeader + "，tcp: " + tcpHeader);

        if (ipHeader.getDestinationIP() == CommonMethods.ipStringToInt(tcpServer.tcpServerLocalIP)) {
            // 本地TCP服务器，与外界服务器通信后，返回了数据
            NATSession session = NATSessionManager.getSession(tcpHeader.getDestinationPort());
            if (session != null) {

                ipHeader.setSourceIP(session.remoteIP);
                tcpHeader.setSourcePort(session.remotePort);
                ipHeader.setDestinationIP(vpnLocalIPInt);

                CommonMethods.ComputeTCPChecksum(ipHeader, tcpHeader);

                Log.d(TAG + "_TCP", "第二次NAT，修改包的地址等信息，" + ipHeader);

                try {
                    Log.d(TAG + "_TCP", "写回给虚拟网卡，" + ipHeader + " " + tcpHeader);

                    vpnOutput.write(ipHeader.m_Data, ipHeader.m_Offset, size);
                    vpnOutput.flush();
                } catch (IOException e) {
                    Log.e(TAG + "_TCP", "发送TCP数据包失败:" + e);
                }
            } else {
                Log.e(TAG + "_TCP", "NoSession:" + ipHeader + ", " + tcpHeader);
            }
        } else {
            //来自本地
            // 添加端口映射
            int portKey = tcpHeader.getSourcePort();
            NATSession session = NATSessionManager.getSession(portKey);
            if (session == null || session.remoteIP != ipHeader.getDestinationIP() || session.remotePort != tcpHeader.getDestinationPort()) {
                session = NATSessionManager.createSession(portKey, ipHeader.getDestinationIP(), tcpHeader.getDestinationPort());

                Log.d(TAG + "_TCP", CommonMethods.ipIntToString(ipHeader.getSourceIP()) + ":" + tcpHeader.getSourcePortInt()
                        + " -> " + CommonMethods.ipIntToString(ipHeader.getDestinationIP()) + ":" + tcpHeader.getDestinationPortInt());

                Log.d(TAG + "_TCP", "---------- createSession, :" + session.remoteHost + " " + CommonMethods.ipIntToString(session.remoteIP) + " " + session.remotePort);
            } else {
                Log.d(TAG + "_TCP", "---------- getSession, :" + session.remoteHost + " " + CommonMethods.ipIntToString(session.remoteIP) + " " + session.remotePort);
            }

            ipHeader.setSourceIP(TCPServer.tcpServerLocalIPInt);        // 6.6.6.6
            //tcpHeader.setSourcePort((short)13221);
            ipHeader.setDestinationIP(vpnLocalIPInt);

            tcpHeader.setDestinationPort((short) tcpServer.port);
            CommonMethods.ComputeTCPChecksum(ipHeader, tcpHeader);

            Log.d(TAG + "_TCP", "第一次NAT，修改包的地址等信息，" + ipHeader);

            try {
                Log.d(TAG + "_TCP", "写入虚拟网卡，正常情况下，TCP本地服务会收到数据。" + ipHeader);
                vpnOutput.write(ipHeader.m_Data, ipHeader.m_Offset, size);
                vpnOutput.flush();
            } catch (IOException e) {
                Log.e(TAG + "_TCP", "发送TCP数据包失败:" + e);
            }
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
            CommonMethods.computeUDPChecksum(ipHeader, udpHeader);
            vpnOutput.write(ipHeader.m_Data, ipHeader.m_Offset, ipHeader.getTotalLength());
        } catch (IOException e) {
            Log.e(TAG, "发送UDP数据包失败:" + e);
        }
    }
}