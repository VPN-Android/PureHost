package top.nicelee.purehost.vpn.server;

import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;

import top.nicelee.purehost.vpn.ip.CommonMethods;


public class NATSessionManager {
	static final int MAX_SESSION_COUNT = 60;
    static final long SESSION_TIMEOUT_NS = 60 * 1000000000L;
    static final ConcurrentHashMap<Integer, NATSession> sUDPSessions = new ConcurrentHashMap<>();
    static final ConcurrentHashMap<NATSession, Short> sUDPNATSessions = new ConcurrentHashMap<>();

    static final ConcurrentHashMap<Integer, NATSession> sTCPSessions = new ConcurrentHashMap<>();
    static final ConcurrentHashMap<NATSession, Short> sTCPNATSessions = new ConcurrentHashMap<>();

    public static Short getPort(String type, NATSession socket) {
        if (type.equals("TCP")) {
            return sTCPNATSessions.get(socket);
        } else {
            return sUDPNATSessions.get(socket);
        }
    }
    
    public static NATSession getSession(String type, int portKey) {
        if (type.equals("TCP")) {
            NATSession session = sTCPSessions.get(portKey);
            if (session!=null) {
                session.lastNanoTime = System.nanoTime();
            }
            return sTCPSessions.get(portKey);
        } else {
            NATSession session = sUDPSessions.get(portKey);
            if (session!=null) {
                session.lastNanoTime = System.nanoTime();
            }
            return sUDPSessions.get(portKey);
        }
    }

    static void clearExpiredSessions(String type) {
        long now = System.nanoTime();
        if (type.equals("TCP")) {
            for (Entry<Integer, NATSession> entry: sTCPSessions.entrySet()) {
                NATSession session = entry.getValue();
                if (now - session.lastNanoTime > SESSION_TIMEOUT_NS) {
                    sTCPSessions.remove(entry.getKey());
                    sTCPNATSessions.remove(entry.getValue());
                }
            }
        } else {
            for (Entry<Integer, NATSession> entry: sUDPSessions.entrySet()) {
                NATSession session = entry.getValue();
                if (now - session.lastNanoTime > SESSION_TIMEOUT_NS) {
                    sUDPSessions.remove(entry.getKey());
                    sUDPNATSessions.remove(entry.getValue());
                }
            }
        }
    }

    public static NATSession createSession(String type, int portKey, int remoteIP, short remotePort) {

        if (type.equals("TCP")) {
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
        } else {
            if (sUDPSessions.size() > MAX_SESSION_COUNT) {
                clearExpiredSessions("UDP");//清理过期的会话。
            }
            NATSession session = new NATSession();
            session.lastNanoTime = System.nanoTime();
            session.remoteIP = remoteIP;
            session.remotePort = remotePort;
            if (session.remoteHost == null) {
                session.remoteHost = CommonMethods.ipIntToString(remoteIP);
            }
            sUDPSessions.put(portKey, session);
            sUDPNATSessions.put(session, (short)portKey);
            return session;
        }
    }
}
