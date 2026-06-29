package dev.oreo.server;

import org.java_websocket.WebSocket;

class Match {

    final String id;
    final long seed;
    final int targetScore;

    final WebSocket sockA;
    final WebSocket sockB;
    final String nameA;
    final String nameB;

    int scoreA;
    int scoreB;
    boolean ended;

    Match(String id, long seed, int targetScore,
          WebSocket sockA, String nameA,
          WebSocket sockB, String nameB) {
        this.id = id;
        this.seed = seed;
        this.targetScore = targetScore;
        this.sockA = sockA;
        this.nameA = nameA;
        this.sockB = sockB;
        this.nameB = nameB;
    }

    boolean isPlayerA(WebSocket s) {
        return s == sockA;
    }

    WebSocket opponentOf(WebSocket s) {
        return s == sockA ? sockB : sockA;
    }

    String nameOf(WebSocket s) {
        return s == sockA ? nameA : nameB;
    }
}
