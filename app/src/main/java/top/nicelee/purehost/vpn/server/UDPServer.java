package top.nicelee.purehost.vpn.server;

import android.util.Log;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketAddress;
import java.net.SocketException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import top.nicelee.purehost.vpn.LocalVpnServiceKT;
import top.nicelee.purehost.vpn.ip.CommonMethods;
import top.nicelee.purehost.vpn.ip.IPHeader;
import top.nicelee.purehost.vpn.ip.UDPHeader;


public class UDPServer implements Runnable {
    private static final String TAG = "UDPServer";
    public static final String udpServerLocalIP = "7.7.7.7";
    public static final int udpServerLocalIPInt = CommonMethods.ipStringToInt(udpServerLocalIP);
    public int port;
    public String vpnLocalIP;
    private LocalVpnServiceKT vpnService;

    final int MAX_LENGTH = 1024 * 20;
    byte[] receMsgs = new byte[MAX_LENGTH];

    DatagramSocket udpDatagramSocket;
    DatagramPacket datagramPacket;
    DatagramPacket sendPacket;
    Pattern patternURL = Pattern.compile("^/([^:]+):(.*)$");

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
            Log.d(TAG, "UDP服务器启动, 地址为: " + socketAddress);
        } catch (SocketException e) {
            Log.e(TAG, "创建udpDatagramSocket失败", e);
        }
    }


    private void service() {
        Log.d(TAG, "UDP服务器启动, 端口为: " + port);
        try {
            while (true) {
                Log.d(TAG, "阻塞等待，UDP消息");
                udpDatagramSocket.receive(datagramPacket);

                SocketAddress socketAddress = datagramPacket.getSocketAddress();
                int socketPort = datagramPacket.getPort();

                Matcher matcher = patternURL.matcher(socketAddress.toString());
                matcher.find();
                Log.d(TAG, "收到udp消息: " + socketAddress);
                if (udpServerLocalIP.equals(matcher.group(1))) {
                    Log.d(TAG, "UDPServer收到本地消息" + socketAddress);
                    NATSession session = NATSessionManager.getSession((short) socketPort);
                    if (session == null) {
                        Log.d(TAG, "NATSessionManager中未找到session" + socketPort);
                        continue;
                    }
                    Log.d(TAG, "NATSessionManager中找到session" + socketPort);
                    sendPacket = new DatagramPacket(receMsgs, 28, datagramPacket.getLength(), CommonMethods.ipIntToInet4Address(session.remoteIP), session.remotePort);
                    udpDatagramSocket.send(sendPacket);
                } else {
                    Log.d(TAG, "UDPServer收到外部消息: " + socketAddress);
                    //如果消息来自外部, 转进来
                    NATSession session = new NATSession();
                    session.remoteIP = CommonMethods.ipStringToInt(matcher.group(1));
                    session.remotePort = (short) socketPort;
                    Short port = NATSessionManager.getPort(session);
                    if (port == null) {
                        Log.d(TAG, "收到外部UDP消息, 未在Session中找到");
                        continue;
                    }
                    Log.d(TAG, "收到外部UDP消息, 在Session中找到, port: " + port + " ,port & 0xFF:" + (port & 0xFFFF));

                    IPHeader ipHeader = new IPHeader(receMsgs, 0);
                    ipHeader.Default();
                    ipHeader.setDestinationIP(CommonMethods.ipStringToInt(vpnLocalIP));
                    ipHeader.setSourceIP(session.remoteIP);
                    ipHeader.setTotalLength(20 + 8 + datagramPacket.getLength());
                    ipHeader.setHeaderLength(20);
                    ipHeader.setProtocol(IPHeader.UDP);
                    ipHeader.setTTL((byte) 30);

                    UDPHeader udpHeader = new UDPHeader(receMsgs, 20);
                    udpHeader.setDestinationPort((short) port);
                    udpHeader.setSourcePort(session.remotePort);
                    udpHeader.setTotalLength(8 + datagramPacket.getLength());

                    //LocalVpnService.Instance.sendUDPPacket(ipHeader, udpHeader);
                    vpnService.sendUDPPacket(ipHeader, udpHeader);
                }
            }
        } catch (SocketException e) {
            //e.printStackTrace();
            //ConfigReader.writeHost(e.toString());
        } catch (IOException e) {
            //e.printStackTrace();
            //ConfigReader.writeHost(e.toString());
        } catch (Exception e) {
            //e.printStackTrace();
            //ConfigReader.writeHost(e.toString());
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
