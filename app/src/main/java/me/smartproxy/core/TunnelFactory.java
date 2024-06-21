package me.smartproxy.core;

import java.net.InetSocketAddress;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;

import me.smartproxy.tunnel.RawTunnel;
import me.smartproxy.tunnel.Tunnel;

public class TunnelFactory {
	
	public static Tunnel wrap(SocketChannel channel,Selector selector){
		return new RawTunnel(channel, selector);
	}
 
	public static Tunnel createTunnelByConfig(InetSocketAddress destAddress,Selector selector) throws Exception {
		return new RawTunnel(destAddress, selector);
	}

}
