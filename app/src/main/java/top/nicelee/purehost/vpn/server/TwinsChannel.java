package top.nicelee.purehost.vpn.server;

import android.net.VpnService;
import android.util.Log;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import top.nicelee.purehost.vpn.ip.CommonMethods;

public class TwinsChannel {
	
	final static Pattern patternURL = Pattern.compile("^/([^:]+):(.*)$");
	private static final String TAG = "TwinsChannel";
	Selector selector;

	SocketChannel localSc;
	SocketChannel remoteSc;
	
	public TwinsChannel(SocketChannel localSc, Selector selector) {
		this.localSc = localSc;
		this.selector = selector;
	}
	
	SocketChannel connectRemoteSc(VpnService vpnService) throws IOException {
		if (remoteSc != null) {
			Log.e(TAG,"VpnService Socket 已经连接过了");
			return remoteSc;
		}

		remoteSc = SocketChannel.open();
		remoteSc.configureBlocking(false);
//        Log.d(TAG,"VpnService Socket Protect成功: "+ LocalVpnService.Instance.protect(remoteSc.socket()));
//        LocalVpnService.Instance.protect(remoteSc.socket());
		boolean protect = vpnService.protect(remoteSc.socket());
		Log.d(TAG,"VpnService Socket Protect成功: "+ protect);

		Matcher matcher = patternURL.matcher(localSc.getRemoteAddress().toString());
		matcher.find();
		NATSession session = NATSessionManager.getSession("TCP",(short)Integer.parseInt(matcher.group(2)));
		Log.d(TAG,"VpnService Socket Session: key Port: "+ (short)Integer.parseInt(matcher.group(2)));
		if(session != null){
			Log.d(TAG,"VpnService Socket Session: ip : "+ CommonMethods.ipIntToString(session.remoteIP));
			Log.d(TAG,"VpnService Socket Session: port : "+ (int)(session.remotePort));
		}
		//Log.d(TAG,"VpnService Socket RemoteIP:  192.168.1.103");
		//Log.d(TAG,"VpnService Socket RemotePort:  7778");
		//建立远程连接
		InetSocketAddress soAddr = new InetSocketAddress(CommonMethods.ipIntToString(session.remoteIP), (session.remotePort));
		Log.d(TAG,"VpnService Socket 正在连接中... "+ soAddr.toString());
		remoteSc.connect(soAddr);
		remoteSc.register(selector, SelectionKey.OP_READ, this);
		Log.d(TAG,"VpnService Socket 已经注册... "+ soAddr.toString());
		return remoteSc;
	}
}
