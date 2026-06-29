package dev.oreo.net;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public final class Protocol {

    public static final String TYPE_HELLO = "HELLO";
    public static final String TYPE_FIND_MATCH = "FIND_MATCH";
    public static final String TYPE_CANCEL_MATCH = "CANCEL_MATCH";
    public static final String TYPE_MATCH_START = "MATCH_START";
    public static final String TYPE_SCORE = "SCORE";
    public static final String TYPE_OPPONENT_SCORE = "OPPONENT_SCORE";
    public static final String TYPE_MATCH_END = "MATCH_END";
    public static final String TYPE_OPPONENT_LEFT = "OPPONENT_LEFT";
    public static final String TYPE_ERROR = "ERROR";

    public static final String END_REASON_TARGET = "TARGET_REACHED";
    public static final String END_REASON_OPPONENT_LEFT = "OPPONENT_LEFT";

    private static final Gson GSON = new Gson();

    private Protocol() {}

    public static class Envelope {
        public String type;
    }

    public static class Hello extends Envelope {
        public String name;

        public Hello(String name) {
            this.type = TYPE_HELLO;
            this.name = name;
        }
    }

    public static class FindMatch extends Envelope {
        public FindMatch() {
            this.type = TYPE_FIND_MATCH;
        }
    }

    public static class CancelMatch extends Envelope {
        public CancelMatch() {
            this.type = TYPE_CANCEL_MATCH;
        }
    }

    public static class MatchStart extends Envelope {
        public long seed;
        public int targetScore;
        public String opponent;

        public MatchStart(long seed, int targetScore, String opponent) {
            this.type = TYPE_MATCH_START;
            this.seed = seed;
            this.targetScore = targetScore;
            this.opponent = opponent;
        }
    }

    public static class ScoreUpdate extends Envelope {
        public int value;

        public ScoreUpdate(int value) {
            this.type = TYPE_SCORE;
            this.value = value;
        }
    }

    public static class OpponentScore extends Envelope {
        public int value;

        public OpponentScore(int value) {
            this.type = TYPE_OPPONENT_SCORE;
            this.value = value;
        }
    }

    public static class MatchEnd extends Envelope {
        public String winner;
        public int yourScore;
        public int opponentScore;
        public String reason;

        public MatchEnd(String winner, int yourScore, int opponentScore, String reason) {
            this.type = TYPE_MATCH_END;
            this.winner = winner;
            this.yourScore = yourScore;
            this.opponentScore = opponentScore;
            this.reason = reason;
        }
    }

    public static class OpponentLeft extends Envelope {
        public OpponentLeft() {
            this.type = TYPE_OPPONENT_LEFT;
        }
    }

    public static class ErrorMsg extends Envelope {
        public String message;

        public ErrorMsg(String message) {
            this.type = TYPE_ERROR;
            this.message = message;
        }
    }

    public static String encode(Envelope msg) {
        return GSON.toJson(msg);
    }

    public static String typeOf(String json) {
        JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
        return obj.get("type").getAsString();
    }

    public static <T extends Envelope> T decode(String json, Class<T> type) {
        return GSON.fromJson(json, type);
    }
}
