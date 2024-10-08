package top.nicelee.purehost.vpn.server;

import android.util.Log;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.SocketException;
import java.util.Locale;

import top.nicelee.purehost.vpn.LocalVpnServiceKT;
import top.nicelee.purehost.vpn.ip.CommonMethods;
import top.nicelee.purehost.vpn.ip.IPHeader;
import top.nicelee.purehost.vpn.ip.UDPHeader;


public class UDPServer implements Runnable {
    private static final String TAG = "UDPServer";
    public static final String udpServerLocalIP = "198.198.198.102";
    public static final int udpServerLocalIPInt = CommonMethods.ipStringToInt(udpServerLocalIP);
    public int port;
    public String vpnLocalIP;
    private final LocalVpnServiceKT vpnService;

    final int MAX_LENGTH = 1024 * 20;
    byte[] receMsgs = new byte[MAX_LENGTH];

    DatagramSocket udpDatagramSocket;
    DatagramPacket datagramPacket;
    DatagramPacket sendPacket;

    Thread udpThread;

    public void start() {
        udpThread = new Thread(this);
        udpThread.setName("UDPServer - Thread");
        udpThread.start();
    }

    public void stop() {
        udpDatagramSocket.close();
        udpThread.interrupt();
    }

    public UDPServer(LocalVpnServiceKT vpnService, String vpnLocalIP) {
        this.vpnService = vpnService;
        this.vpnLocalIP = vpnLocalIP;
        try {
            udpDatagramSocket = new DatagramSocket();// 填写参数，可以固定端口，如果去掉之后，会随机分配端口
            vpnService.protect(udpDatagramSocket);
            port = udpDatagramSocket.getLocalPort();

            datagramPacket = new DatagramPacket(receMsgs, 28, receMsgs.length - 28);

            SocketAddress socketAddress = udpDatagramSocket.getLocalSocketAddress();
            Log.d(TAG, "UDP服务器启动, 地址为: ==============>\t" + socketAddress);
        } catch (SocketException e) {
            Log.e(TAG, "创建udpDatagramSocket失败", e);
        }
    }


    private void service() {
        Log.d(TAG, "UDP服务器启动, 端口为: " + port);
        try {
            while (udpThread != null && !udpThread.isInterrupted()) {
                Log.d(TAG, "阻塞等待，UDP消息");
                udpDatagramSocket.receive(datagramPacket);

                InetSocketAddress socketAddress = (InetSocketAddress) datagramPacket.getSocketAddress();
                int socketPort = datagramPacket.getPort();

                String hostAddress = socketAddress.getAddress().getHostAddress();
                if (hostAddress == null || hostAddress.isEmpty()) {
                    Log.e(TAG, "hostAddress为空");
                    continue;
                }

                Log.d(TAG, "收到udp消息: " + socketAddress);
                if (udpServerLocalIP.equals(hostAddress)) {
                    Log.d(TAG, "UDPServer收到本地消息" + socketAddress);
                    NATSession session = NATSessionManager.getSession("UDP", (short) socketPort);
                    if (session == null) {
                        Log.d(TAG, "NATSessionManager中未找到session" + socketPort);
                        continue;
                    }
                    Log.d(TAG, "NATSessionManager中找到session" + socketPort);
                    sendPacket = new DatagramPacket(receMsgs, 28, datagramPacket.getLength(), CommonMethods.ipIntToInet4Address(session.remoteIP), session.remotePort);
                    Log.d(TAG, "构建数据包发送到远端目标服务器：remote:" + CommonMethods.ipIntToInet4Address(session.remoteIP) + ":" + (session.remotePort & 0xFFFF));
                    udpDatagramSocket.send(sendPacket);
                } else {
                    Log.d(TAG, "UDPServer收到外部消息: " + socketAddress);
                    //如果消息来自外部, 转进来
                    NATSession session = new NATSession();
                    session.remoteIP = CommonMethods.ipStringToInt(hostAddress);
                    session.remotePort = (short) socketPort;
                    Short port = NATSessionManager.getPort("UDP", session);
                    if (port == null) {
                        Log.d(TAG, "收到外部UDP消息, 未在Session中找到");
                        continue;
                    }
                    Log.d(TAG, "收到外部UDP消息, 在Session中找到, port: " + (port & 0xFFFF));

                    IPHeader ipHeader = new IPHeader(receMsgs, 0);
                    UDPHeader udpHeader = new UDPHeader(receMsgs, 20);

                    Log.d(TAG, String.format(Locale.ENGLISH, "第二次NAT:: sourceIP:sourcePort, %s:%s -> %s:%s",
                            CommonMethods.ipIntToString(ipHeader.getSourceIP()), udpHeader.getSourcePortInt(),
                            CommonMethods.ipIntToString(UDPServer.udpServerLocalIPInt), udpHeader.getSourcePortInt()));

                    Log.d(TAG, String.format(Locale.ENGLISH, "第二次NAT:: dstIP:dstPort, %s:%s -> %s:%s",
                            CommonMethods.ipIntToString(ipHeader.getDestinationIP()), udpHeader.getDestinationPortInt(),
                            CommonMethods.ipIntToString(CommonMethods.ipStringToInt(vpnLocalIP)), (port & 0xFFFF)));

                    Log.d(TAG, String.format(Locale.ENGLISH, "第二次NAT:: 最终：%s:%s -> %s:%s",
                            CommonMethods.ipIntToString(UDPServer.udpServerLocalIPInt), udpHeader.getSourcePortInt(),
                            CommonMethods.ipIntToString(CommonMethods.ipStringToInt(vpnLocalIP)), (port & 0xFFFF)));

                    ipHeader.setSourceIP(session.remoteIP);
                    ipHeader.setDestinationIP(CommonMethods.ipStringToInt(vpnLocalIP));

                    ipHeader.setTos((byte) 0);
                    ipHeader.setIdentification(0);
                    ipHeader.setFlagsAndOffset((short) 0);
                    ipHeader.setTotalLength(20 + 8 + datagramPacket.getLength());
                    ipHeader.setHeaderLength(20);
                    ipHeader.setProtocol(IPHeader.UDP);
                    ipHeader.setTTL((byte) 30);

                    udpHeader.setDestinationPort((short) port);
                    udpHeader.setSourcePort(session.remotePort);
                    udpHeader.setTotalLength(8 + datagramPacket.getLength());

                    Log.d(TAG, "UDP, 把数据写回VPNService，此时发出UDP的App应该会收到消息");

                    vpnService.sendUDPPacket(ipHeader, udpHeader);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "UDP服务异常", e);
        } finally {
            // 关闭socket
            Log.d(TAG, "udpServer已关闭");
            if (udpDatagramSocket != null) {
                udpDatagramSocket.close();
            }
        }
    }

    @Override
    public void run() {
        service();
    }
}
