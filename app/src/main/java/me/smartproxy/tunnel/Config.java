package me.smartproxy.tunnel;

import java.net.InetSocketAddress;

public abstract class Config {
	public InetSocketAddress ServerAddress;
	public IEncryptor Encryptor;
}
