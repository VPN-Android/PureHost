package top.nicelee.purehost.vpn.server;

public class NATSession {
	public int remoteIP;
    public short remotePort;
    public String remoteHost;
    public int BytesSent;
    public int PacketSent;
    public long lastNanoTime;
    
    @Override
    public boolean equals(Object obj) {
    	if( obj instanceof NATSession) {
    		NATSession session = (NATSession)obj;
    		if(this.remoteIP == session.remoteIP && this.remotePort == session.remotePort) {
    			return true;
    		}
    	}
    	return false;
    }
    
    @Override
    public int hashCode() {
    	int hash = remotePort * 31 + remoteIP;
    	return hash;
    }
}
