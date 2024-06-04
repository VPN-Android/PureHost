package top.nicelee.purehost.vpn.server;

import android.net.VpnService;
import android.util.Log;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class TCPServer implements Runnable{
	private static final String TAG = "TCPServer";
	// Socket协议服务端
	public String localIP = "6.6.6.6";
	public int port;
	public String vpnLocalIP;
	ServerSocketChannel serverSocketChannel;
	Selector selector = null;

	Thread tcpThread;
	public void start(){
		try{
			selector = Selector.open();
			serverSocketChannel = ServerSocketChannel.open();
			//serverSocketChannel.socket().setReuseAddress(true);
			//serverSocketChannel.socket().bind(null);
			serverSocketChannel.socket().bind(new InetSocketAddress(12320));
			serverSocketChannel.configureBlocking(false);
			serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);
			//Log.d(TAG,"TCPServer: protect是否成功: " + LocalVpnService.Instance.protect(port));
			port = serverSocketChannel.socket().getLocalPort();

		}catch (Exception e){
		}
		tcpThread = new Thread(this);
		tcpThread.setName("TCPServer - Thread");
		tcpThread.start();
	}

	public void stop(){
		tcpThread.interrupt();
		try{
			serverSocketChannel.socket().close();
		}catch (Exception e){
		}
		try{
			selector.close();
		}catch (Exception e){
		}

	}

	private VpnService vpnService;
	public TCPServer(VpnService vpnService, String localIP) {
		this.vpnService = vpnService;
		this.vpnLocalIP = localIP;

	}

	/* 服务器服务方法 */
	public void service(VpnService vpnService) throws Exception {
		//
		// NATSessionManager.createSession(9867,
		// CommonMethods.ipStringToInt("192.168.1.103"), (short) 7777);
		//
		Log.d(TAG,"TCPServer: TCP服务器启动, 端口为: " + port);
		/** 外循环，已经发生了SelectionKey数目 */
		while (selector.select() > 0) {
			/* 得到已经被捕获了的SelectionKey的集合 */
			if(!selector.isOpen()){
				throw new Exception("TCPServer: selector已经关闭");
			}

			Iterator<SelectionKey> iterator = selector.selectedKeys().iterator();
			while (iterator.hasNext()) {
				//Log.d(TAG,"TCPServer: TCP服务器收到消息");
				SelectionKey key = null;
				SocketChannel sc = null;
				try {
					key = (SelectionKey) iterator.next();
					iterator.remove();
					if (key.isAcceptable()) {
						ServerSocketChannel ssc = (ServerSocketChannel) key.channel();
						sc = ssc.accept();
//						Log.d(TAG,"客户端机子的地址是 " + sc.socket().getRemoteSocketAddress() + "  本地机子的端口号是 "
//								+ sc.socket().getLocalPort());
						sc.configureBlocking(false);

						TwinsChannel twins = new TwinsChannel(sc, selector);
						twins.connectRemoteSc(vpnService);
						sc.register(selector, SelectionKey.OP_READ, twins);// buffer通过附件方式，传递
					}
					if (key.isReadable()) {
						reveice(key);
					}
				} catch (NullPointerException e) {
					// 没有找到对应的Session
					try {
						if (sc != null) {
							sc.close();
						}
					} catch (Exception cex) {
						cex.printStackTrace();
					}
				} catch (Exception e) {
					//e.printStackTrace();
					try {
						if (sc != null) {
							sc.close();
						}
						if (key != null) {
							key.cancel();
							key.channel().close();
						}
					} catch (Exception cex) {
						//cex.printStackTrace();
					}
				} finally {

				}
			}
		}
		Log.d(TAG,"-----程序结束-----");
	}

	Pattern patternURL = Pattern.compile("^/([^:]+):(.*)$");

	public void reveice(SelectionKey key) throws IOException {
		// Log.d(TAG,"----收到Read事件----");
		if (key == null)
			return;

		SocketChannel sc = (SocketChannel) key.channel();
		// Log.d(TAG,"消息来自: " + sc.getRemoteAddress().toString());
		Matcher matcher = patternURL.matcher(sc.getRemoteAddress().toString());
		matcher.find();

		TwinsChannel twins = (TwinsChannel) key.attachment();
		// 如果消息来自本地, 转发出去
		if (localIP.equals(matcher.group(1))) {

			if (!twins.remoteSc.isConnected()) {
				// 如果正在连接，则完成连接
				twins.remoteSc.finishConnect();
				twins.remoteSc.configureBlocking(false);
			} else {
				// Log.d(TAG,"已经连接完成..");
				//Log.d(TAG,"消息来自本地: " + sc.getRemoteAddress().toString());
				ByteBuffer buf = ByteBuffer.allocate(2014);
				int bytesRead = sc.read(buf);
				//String content = "";
				while (bytesRead > 0) {
					//content += new String(buf.array(), 0, buf.position());
					buf.flip();
					twins.remoteSc.write(buf);
					buf.clear();
					bytesRead = sc.read(buf);
				}
				//Log.d(TAG,"来自内部的消息是: " + content.trim());
			}
		} else {
			// 如果消息来自外部, 转给内部
			//Log.d(TAG,"消息来自外部: " + sc.getRemoteAddress().toString());
			ByteBuffer buf = ByteBuffer.allocate(2014);
			int bytesRead = sc.read(buf);
			//String content = "";
			while (bytesRead > 0) {
				//content += new String(buf.array(), 0, buf.position());
				buf.flip();
				twins.localSc.write(buf);
				buf.clear();
				bytesRead = sc.read(buf);
			}
			//Log.d(TAG,"来自外部的消息是: " + content.trim());
		}
	}

	@Override
	public void run() {
		try {
			service(this.vpnService);
		} catch (Exception e) {
			//e.printStackTrace();
		}
	}

}
