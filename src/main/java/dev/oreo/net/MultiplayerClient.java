package dev.oreo.net;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import java.net.URI;

public class MultiplayerClient {

    public interface Listener {
        void onConnected();
        void onMatchStart(long seed, int targetScore, String opponentName);
        void onOpponentScore(int value);
        void onOpponentLeft();
        void onMatchEnd(String winner, int yourScore, int opponentScore, String reason);
        void onError(String message);
        void onDisconnected();
    }

    private final URI uri;
    private final String username;
    private final Listener listener;
    private WebSocketClient socket;
    private volatile boolean helloSent;

    public MultiplayerClient(URI uri, String username, Listener listener) {
        this.uri = uri;
        this.username = username;
        this.listener = listener;
    }

    public void connect() {
        socket = new WebSocketClient(uri) {
            @Override
            public void onOpen(ServerHandshake handshakedata) {
                send(Protocol.encode(new Protocol.Hello(username)));
                helloSent = true;
                listener.onConnected();
            }

            @Override
            public void onMessage(String message) {
                handleMessage(message);
            }

            @Override
            public void onClose(int code, String reason, boolean remote) {
                listener.onDisconnected();
            }

            @Override
            public void onError(Exception ex) {
                listener.onError(ex.getMessage() == null ? ex.toString() : ex.getMessage());
            }
        };
        socket.connect();
    }

    private void handleMessage(String message) {
        try {
            String type = Protocol.typeOf(message);
            switch (type) {
                case Protocol.TYPE_MATCH_START: {
                    Protocol.MatchStart m = Protocol.decode(message, Protocol.MatchStart.class);
                    listener.onMatchStart(m.seed, m.targetScore, m.opponent);
                    break;
                }
                case Protocol.TYPE_OPPONENT_SCORE: {
                    Protocol.OpponentScore m = Protocol.decode(message, Protocol.OpponentScore.class);
                    listener.onOpponentScore(m.value);
                    break;
                }
                case Protocol.TYPE_OPPONENT_LEFT:
                    listener.onOpponentLeft();
                    break;
                case Protocol.TYPE_MATCH_END: {
                    Protocol.MatchEnd m = Protocol.decode(message, Protocol.MatchEnd.class);
                    listener.onMatchEnd(m.winner, m.yourScore, m.opponentScore, m.reason);
                    break;
                }
                case Protocol.TYPE_ERROR: {
                    Protocol.ErrorMsg m = Protocol.decode(message, Protocol.ErrorMsg.class);
                    listener.onError(m.message);
                    break;
                }
                default:
                    listener.onError("unknown server message: " + type);
            }
        } catch (Exception e) {
            listener.onError("bad server message: " + e.getMessage());
        }
    }

    public void findMatch() {
        if (socket != null && socket.isOpen() && helloSent) {
            socket.send(Protocol.encode(new Protocol.FindMatch()));
        }
    }

    public void cancelMatch() {
        if (socket != null && socket.isOpen()) {
            socket.send(Protocol.encode(new Protocol.CancelMatch()));
        }
    }

    public void sendScore(int score) {
        if (socket != null && socket.isOpen()) {
            socket.send(Protocol.encode(new Protocol.ScoreUpdate(score)));
        }
    }

    public void close() {
        if (socket != null) {
            try {
                socket.close();
            } catch (Exception ignored) {
            }
        }
    }
}
