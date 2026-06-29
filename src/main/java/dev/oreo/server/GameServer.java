package dev.oreo.server;

import dev.oreo.net.Protocol;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public class GameServer extends WebSocketServer {

    private static final int DEFAULT_PORT = 7070;
    private static final int DEFAULT_TARGET_SCORE = 1000;

    /** Connection-level state stored as the WebSocket attachment. */
    private static class Conn {
        String name;
        Match match;
        boolean searching;
    }

    private final Object lock = new Object();
    private WebSocket waiting;
    private final Map<String, Match> matches = new HashMap<>();
    private final int targetScore;

    public GameServer(InetSocketAddress address, int targetScore) {
        super(address);
        this.targetScore = targetScore;
    }

    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        conn.setAttachment(new Conn());
        System.out.println("[server] open " + conn.getRemoteSocketAddress());
    }

    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        System.out.println("[server] close " + conn.getRemoteSocketAddress() + " (" + reason + ")");
        Conn c = conn.getAttachment();
        if (c == null) return;

        synchronized (lock) {
            if (waiting == conn) {
                waiting = null;
            }
            Match m = c.match;
            if (m != null && !m.ended) {
                m.ended = true;
                matches.remove(m.id);
                WebSocket opp = m.opponentOf(conn);
                Conn oppConn = opp.getAttachment();
                if (oppConn != null) oppConn.match = null;
                int oppScore = m.isPlayerA(conn) ? m.scoreB : m.scoreA;
                int myScore = m.isPlayerA(conn) ? m.scoreA : m.scoreB;
                if (opp.isOpen()) {
                    opp.send(Protocol.encode(new Protocol.MatchEnd(
                            m.nameOf(opp),
                            oppScore,
                            myScore,
                            Protocol.END_REASON_OPPONENT_LEFT
                    )));
                }
            }
        }
    }

    @Override
    public void onMessage(WebSocket conn, String message) {
        Conn c = conn.getAttachment();
        if (c == null) return;
        try {
            String type = Protocol.typeOf(message);
            switch (type) {
                case Protocol.TYPE_HELLO: {
                    Protocol.Hello hello = Protocol.decode(message, Protocol.Hello.class);
                    String name = hello.name == null ? "" : hello.name.trim();
                    if (name.isEmpty()) name = "Player-" + Integer.toHexString(System.identityHashCode(conn));
                    c.name = name;
                    break;
                }
                case Protocol.TYPE_FIND_MATCH:
                    handleFindMatch(conn, c);
                    break;
                case Protocol.TYPE_CANCEL_MATCH:
                    synchronized (lock) {
                        if (waiting == conn) waiting = null;
                        c.searching = false;
                    }
                    break;
                case Protocol.TYPE_SCORE: {
                    Protocol.ScoreUpdate s = Protocol.decode(message, Protocol.ScoreUpdate.class);
                    handleScore(conn, c, s.value);
                    break;
                }
                default:
                    conn.send(Protocol.encode(new Protocol.ErrorMsg("unknown type: " + type)));
            }
        } catch (Exception e) {
            e.printStackTrace();
            try {
                conn.send(Protocol.encode(new Protocol.ErrorMsg("bad message: " + e.getMessage())));
            } catch (Exception ignored) {
            }
        }
    }

    private void handleFindMatch(WebSocket conn, Conn c) {
        if (c.name == null) {
            conn.send(Protocol.encode(new Protocol.ErrorMsg("send HELLO first")));
            return;
        }
        synchronized (lock) {
            if (c.match != null) return;
            if (waiting == null || waiting == conn || !waiting.isOpen()) {
                waiting = conn;
                c.searching = true;
                return;
            }
            WebSocket other = waiting;
            waiting = null;
            Conn otherC = other.getAttachment();
            otherC.searching = false;
            c.searching = false;

            String matchId = UUID.randomUUID().toString();
            long seed = ThreadLocalRandom.current().nextLong();
            Match match = new Match(matchId, seed, targetScore,
                    other, otherC.name, conn, c.name);
            matches.put(matchId, match);
            otherC.match = match;
            c.match = match;

            other.send(Protocol.encode(new Protocol.MatchStart(seed, targetScore, c.name)));
            conn.send(Protocol.encode(new Protocol.MatchStart(seed, targetScore, otherC.name)));
            System.out.println("[server] match " + matchId + ": " + otherC.name + " vs " + c.name);
        }
    }

    private void handleScore(WebSocket conn, Conn c, int value) {
        Match m = c.match;
        if (m == null || m.ended) return;

        boolean iAmA;
        synchronized (lock) {
            if (m.ended) return;
            iAmA = m.isPlayerA(conn);
            if (iAmA) {
                if (value > m.scoreA) m.scoreA = value;
            } else {
                if (value > m.scoreB) m.scoreB = value;
            }

            WebSocket opp = m.opponentOf(conn);
            int myScore = iAmA ? m.scoreA : m.scoreB;
            int oppScore = iAmA ? m.scoreB : m.scoreA;
            if (opp.isOpen()) {
                opp.send(Protocol.encode(new Protocol.OpponentScore(myScore)));
            }

            if (myScore >= m.targetScore || oppScore >= m.targetScore) {
                m.ended = true;
                matches.remove(m.id);
                Conn oppConn = opp.getAttachment();
                if (oppConn != null) oppConn.match = null;
                c.match = null;

                String winner;
                if (myScore >= m.targetScore && oppScore >= m.targetScore) {
                    winner = myScore >= oppScore ? c.name : m.nameOf(opp);
                } else if (myScore >= m.targetScore) {
                    winner = c.name;
                } else {
                    winner = m.nameOf(opp);
                }

                conn.send(Protocol.encode(new Protocol.MatchEnd(
                        winner, myScore, oppScore, Protocol.END_REASON_TARGET)));
                if (opp.isOpen()) {
                    opp.send(Protocol.encode(new Protocol.MatchEnd(
                            winner, oppScore, myScore, Protocol.END_REASON_TARGET)));
                }
                System.out.println("[server] match " + m.id + " ended, winner=" + winner);
            }
        }
    }

    @Override
    public void onError(WebSocket conn, Exception ex) {
        ex.printStackTrace();
    }

    @Override
    public void onStart() {
        System.out.println("[server] listening on " + getAddress());
        setConnectionLostTimeout(60);
    }

    public static void main(String[] args) {
        int port = DEFAULT_PORT;
        int target = DEFAULT_TARGET_SCORE;

        String envPort = System.getenv("OCRUSH_PORT");
        if (envPort != null) port = Integer.parseInt(envPort.trim());
        String envTarget = System.getenv("OCRUSH_TARGET");
        if (envTarget != null) target = Integer.parseInt(envTarget.trim());
        if (args.length >= 1) port = Integer.parseInt(args[0]);
        if (args.length >= 2) target = Integer.parseInt(args[1]);

        GameServer server = new GameServer(new InetSocketAddress(port), target);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                server.stop(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }));
        server.start();
    }
}
