package top.nicelee.purehost.vpn.server;

import android.net.VpnService;
import android.util.Log;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import top.nicelee.purehost.vpn.LocalVpnServiceKT;
import top.nicelee.purehost.vpn.ip.CommonMethods;
import top.nicelee.purehost.vpn.ip.IPHeader;
import top.nicelee.purehost.vpn.ip.UDPHeader;


public class UDPServer implements Runnable {
	private static final String TAG = "UDPServer";
	public String localIP = "7.7.7.7";
	public int port = 7777;
	public String vpnLocalIP;
	
	final int MAX_LENGTH = 1024;
	byte[] receMsgs = new byte[MAX_LENGTH];

	DatagramSocket udpSocket;
	DatagramPacket packet;
	DatagramPacket sendPacket;
	Pattern patternURL = Pattern.compile("^/([^:]+):(.*)$");

	Thread udpThread;
	public void start(){
		udpThread = new Thread(this);
		udpThread.setName("UDPServer - Thread");
		udpThread.start();
	}

	public void stop(){
		udpSocket.close();
	}
	public UDPServer(VpnService vpnService, String localIP) {
		this.vpnLocalIP = localIP;
		try {
			init(vpnService);
		} catch (UnknownHostException | SocketException e) {
			e.printStackTrace();
		}
	}
	
	public void init(VpnService vpnService) throws UnknownHostException, SocketException {
		udpSocket = new DatagramSocket();
		vpnService.protect(udpSocket);
		port = udpSocket.getLocalPort();
		packet = new DatagramPacket(receMsgs, 28 , receMsgs.length - 28);
	}

	private void service() {
		Log.d(TAG,"UDPServer: UDP服务器启动, 端口为: " + port);
		try {
			while (true) {
				udpSocket.receive(packet);
				
				Matcher matcher = patternURL.matcher(packet.getSocketAddress().toString());
				matcher.find();
				//Log.d(TAG,"UDPServer: 收到udp消息" + packet.getSocketAddress().toString());
				if (localIP.equals(matcher.group(1))) {
					
					//Log.d(TAG,"UDPServer: UDPServer收到本地消息" + packet.getSocketAddress().toString());
					NATSession session = NATSessionManager.getSession((short)packet.getPort());
					if( session == null) {
						//Log.d(TAG,"UDPServer: NATSessionManager中未找到session" + packet.getPort());
						continue;
					}
					//Log.d(TAG,"UDPServer: NATSessionManager中找到session"+ packet.getPort());
					sendPacket = new DatagramPacket(receMsgs, 28, packet.getLength(), CommonMethods.ipIntToInet4Address(session.RemoteIP), (int)session.RemotePort);
					udpSocket.send(sendPacket);
				}else {
					//Log.d(TAG,"UDPServer: UDPServer收到外部消息"+ packet.getSocketAddress().toString());
					//如果消息来自外部, 转进来
					NATSession session = new NATSession();
					session.RemoteIP = CommonMethods.ipStringToInt(matcher.group(1));
					session.RemotePort = (short) packet.getPort();
					Short port = NATSessionManager.getPort(session);
					if( port == null) {
						//Log.d(TAG,"UDPServer: 收到外部UDP消息, 未在Session中找到");
						continue;
					}
					//Log.d(TAG,"UDPServer: 收到外部UDP消息, 在Session中找到, port" + port +" ,port & 0xFF:" + (port & 0xFFFF));


					IPHeader ipHeader = new IPHeader(receMsgs, 0);
					ipHeader.Default();
					ipHeader.setDestinationIP(CommonMethods.ipStringToInt(vpnLocalIP));
					ipHeader.setSourceIP(session.RemoteIP);
					ipHeader.setTotalLength(20 + 8 + packet.getLength());
					ipHeader.setHeaderLength(20);
					ipHeader.setProtocol(IPHeader.UDP);
					ipHeader.setTTL((byte)30);

					UDPHeader udpHeader = new UDPHeader(receMsgs, 20);
					udpHeader.setDestinationPort((short)port);
					udpHeader.setSourcePort(session.RemotePort);
					udpHeader.setTotalLength(8 + packet.getLength());

					//LocalVpnService.Instance.sendUDPPacket(ipHeader, udpHeader);
					LocalVpnServiceKT.Companion.getInstance().sendUDPPacket(ipHeader, udpHeader);
				}
			}
		} catch (SocketException e) {
			//e.printStackTrace();
			//ConfigReader.writeHost(e.toString());
		}catch (IOException e) {
			//e.printStackTrace();
			//ConfigReader.writeHost(e.toString());
		} catch (Exception e) {
			//e.printStackTrace();
			//ConfigReader.writeHost(e.toString());
		} finally {
			// 关闭socket
			Log.d(TAG,"UDPServer: udpServer已关闭");
			if (udpSocket != null) {
				udpSocket.close();
			}
		}
	}

	@Override
	public void run() {
		service();
	}
}
