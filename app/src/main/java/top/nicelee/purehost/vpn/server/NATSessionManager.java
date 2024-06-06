package top.nicelee.purehost.vpn.server;

import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;

import top.nicelee.purehost.vpn.ip.CommonMethods;


public class NATSessionManager {
	static final int MAX_SESSION_COUNT = 60;
    static final long SESSION_TIMEOUT_NS = 60 * 1000000000L;
    static final ConcurrentHashMap<Integer, NATSession> sSessions = new ConcurrentHashMap<>();
    static final ConcurrentHashMap<NATSession, Short> sUDPNATSessions = new ConcurrentHashMap<>();

    public static Short getPort(NATSession socket) {
    	return sUDPNATSessions.get(socket);
    }
    
    public static NATSession getSession(int portKey) {
    	NATSession session = sSessions.get(portKey);
        if (session!=null) {
            session.lastNanoTime = System.nanoTime();
        }
        return sSessions.get(portKey);
    }

    public static int getSessionCount() {
        return sSessions.size();
    }

    static void clearExpiredSessions() {
        long now = System.nanoTime();
        for (Entry<Integer, NATSession> entry: sSessions.entrySet()) {
        	NATSession session = entry.getValue();
            if (now - session.lastNanoTime > SESSION_TIMEOUT_NS) {
            	sSessions.remove(entry.getKey());
            	sUDPNATSessions.remove(entry.getValue());
            }
        }
    }

    public static NATSession createSession(int portKey, int remoteIP, short remotePort) {
        if (sSessions.size() > MAX_SESSION_COUNT) {
            clearExpiredSessions();//清理过期的会话。
        }

        NATSession session = new NATSession();
        session.lastNanoTime = System.nanoTime();
        session.remoteIP = remoteIP;
        session.remotePort = remotePort;

        if (session.remoteHost == null) {
            session.remoteHost = CommonMethods.ipIntToString(remoteIP);
        }
        sSessions.put(portKey, session);
        sUDPNATSessions.put(session, (short)portKey);
        return session;
    }
}
