package top.nicelee.purehost.vpn.server;

import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;

import top.nicelee.purehost.vpn.ip.CommonMethods;


public class NATSessionManager {
	static final int MAX_SESSION_COUNT = 60;
    static final long SESSION_TIMEOUT_NS = 60 * 1000000000L;

    static final ConcurrentHashMap<Integer, NATSession> sTCPSessions = new ConcurrentHashMap<>();
    static final ConcurrentHashMap<NATSession, Short> sTCPNATSessions = new ConcurrentHashMap<>();

    public static Short getPort(String type, NATSession socket) {
        return sTCPNATSessions.get(socket);
    }
    
    public static NATSession getSession(String type, int portKey) {
        NATSession session = sTCPSessions.get(portKey);
        if (session!=null) {
            session.lastNanoTime = System.nanoTime();
        }
        return sTCPSessions.get(portKey);
    }

    static void clearExpiredSessions(String type) {
        long now = System.nanoTime();
        for (Entry<Integer, NATSession> entry: sTCPSessions.entrySet()) {
            NATSession session = entry.getValue();
            if (now - session.lastNanoTime > SESSION_TIMEOUT_NS) {
                sTCPSessions.remove(entry.getKey());
                sTCPNATSessions.remove(entry.getValue());
            }
        }
    }

    public static NATSession createSession(String type, int portKey, int remoteIP, short remotePort) {
        if (sTCPSessions.size() > MAX_SESSION_COUNT) {
            clearExpiredSessions("TCP");//清理过期的会话。
        }
        NATSession session = new NATSession();
        session.lastNanoTime = System.nanoTime();
        session.remoteIP = remoteIP;
        session.remotePort = remotePort;
        sTCPSessions.put(portKey, session);
        sTCPNATSessions.put(session, (short)portKey);
        return session;
    }
}
