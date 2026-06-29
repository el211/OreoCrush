package dev.oreo;

import dev.oreo.net.MultiplayerClient;

import javax.swing.*;
import java.awt.*;
import java.net.URI;

public class Main {

    private static final String CARD_MENU = "menu";
    private static final String CARD_SP = "sp";
    private static final String CARD_MP = "mp";

    private static JFrame frame;
    private static JPanel cards;
    private static CardLayout layout;
    private static MultiplayerShell mpShell;
    private static SinglePlayerShell spShell;

    public static void main(String[] args) {
        SwingUtilities.invokeLater(Main::buildAndShow);
    }

    private static void buildAndShow() {
        frame = new JFrame("Oreo Crush");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        layout = new CardLayout();
        cards = new JPanel(layout);
        cards.setBackground(new Color(8, 12, 18));

        cards.add(buildMenu(), CARD_MENU);

        frame.setContentPane(cards);
        frame.pack();
        frame.setSize(1200, 820);
        frame.setLocationRelativeTo(null);
        frame.setExtendedState(JFrame.MAXIMIZED_BOTH);
        frame.setVisible(true);
    }

    private static JPanel buildMenu() {
        JPanel root = new JPanel(new GridBagLayout());
        root.setBackground(new Color(8, 12, 18));

        JPanel column = new JPanel();
        column.setOpaque(false);
        column.setLayout(new BoxLayout(column, BoxLayout.Y_AXIS));

        JLabel title = new JLabel("OREO CRUSH");
        title.setForeground(new Color(255, 235, 200));
        title.setFont(title.getFont().deriveFont(Font.BOLD, 56f));
        title.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel subtitle = new JLabel("pick a mode");
        subtitle.setForeground(new Color(180, 200, 230));
        subtitle.setFont(subtitle.getFont().deriveFont(Font.PLAIN, 18f));
        subtitle.setAlignmentX(Component.CENTER_ALIGNMENT);

        JButton sp = bigButton("Single Player");
        JButton mp = bigButton("Multiplayer (online)");
        sp.setAlignmentX(Component.CENTER_ALIGNMENT);
        mp.setAlignmentX(Component.CENTER_ALIGNMENT);

        sp.addActionListener(e -> showSinglePlayer());
        mp.addActionListener(e -> showMultiplayer());

        column.add(title);
        column.add(Box.createVerticalStrut(8));
        column.add(subtitle);
        column.add(Box.createVerticalStrut(36));
        column.add(sp);
        column.add(Box.createVerticalStrut(14));
        column.add(mp);

        root.add(column);
        return root;
    }

    private static JButton bigButton(String text) {
        JButton b = new JButton(text);
        b.setFont(b.getFont().deriveFont(Font.BOLD, 20f));
        b.setPreferredSize(new Dimension(320, 56));
        b.setMaximumSize(new Dimension(320, 56));
        b.setFocusPainted(false);
        return b;
    }

    private static void showSinglePlayer() {
        if (spShell == null) {
            spShell = new SinglePlayerShell(Main::showMenu);
            cards.add(spShell.root(), CARD_SP);
        }
        layout.show(cards, CARD_SP);
    }

    private static void showMultiplayer() {
        if (mpShell == null) {
            mpShell = new MultiplayerShell(Main::showMenu);
            cards.add(mpShell.root(), CARD_MP);
        }
        mpShell.showLobby();
        layout.show(cards, CARD_MP);
    }

    static void showMenu() {
        if (mpShell != null) mpShell.disconnect();
        layout.show(cards, CARD_MENU);
    }

    /** Wraps the existing single-player game with a "Back to Menu" button. */
    static class SinglePlayerShell {
        private final JPanel root;

        SinglePlayerShell(Runnable onBack) {
            OreoCrushGame board = new OreoCrushGame();
            ScoreboardPanel scoreboard = new ScoreboardPanel();
            board.setScoreboard(scoreboard);

            JButton previous = new JButton("Prev");
            JButton restart = new JButton("Restart");
            JButton next = new JButton("Next");
            JButton back = new JButton("Back to Menu");

            previous.addActionListener(e -> board.goToLevel(board.getLevel() - 1));
            restart.addActionListener(e -> board.restartCurrentLevel());
            next.addActionListener(e -> board.goToLevel(board.getLevel() + 1));
            back.addActionListener(e -> onBack.run());

            JPanel controls = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 0));
            controls.setOpaque(false);
            controls.add(back);
            controls.add(previous);
            controls.add(restart);
            controls.add(next);

            JLabel tips = new JLabel("Swap cookies to match. Click any glowing special cookie to fire it.");
            tips.setForeground(new Color(230, 237, 246));

            JPanel boardShell = new JPanel(new BorderLayout(0, 10));
            boardShell.setOpaque(false);
            boardShell.add(controls, BorderLayout.NORTH);
            boardShell.add(board, BorderLayout.CENTER);
            boardShell.add(tips, BorderLayout.SOUTH);

            root = new JPanel(new BorderLayout(12, 12));
            root.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
            root.setBackground(new Color(8, 12, 18));
            root.add(boardShell, BorderLayout.CENTER);
            root.add(scoreboard, BorderLayout.EAST);

            Runnable syncButtons = () -> {
                previous.setEnabled(board.getLevel() > 1);
                next.setEnabled(board.getLevel() < board.getUnlockedLevel());
            };
            board.setUiStateListener(syncButtons);
            syncButtons.run();
        }

        JPanel root() {
            return root;
        }
    }

    /** Manages lobby + in-match views and the network client. */
    static class MultiplayerShell {
        private final JPanel root;
        private final CardLayout inner = new CardLayout();
        private final JPanel lobby;
        private final JPanel matchHolder;
        private final JTextField nameField;
        private final JTextField serverField;
        private final JLabel statusLabel;
        private final JButton findButton;
        private final JButton cancelButton;
        private final Runnable onBackToMenu;

        private MultiplayerClient client;
        private OreoCrushGame board;
        private String username;

        MultiplayerShell(Runnable onBackToMenu) {
            this.onBackToMenu = onBackToMenu;
            this.root = new JPanel(inner);
            root.setBackground(new Color(8, 12, 18));

            lobby = new JPanel(new GridBagLayout());
            lobby.setBackground(new Color(8, 12, 18));

            JPanel column = new JPanel();
            column.setOpaque(false);
            column.setLayout(new BoxLayout(column, BoxLayout.Y_AXIS));

            JLabel title = new JLabel("Multiplayer Lobby");
            title.setForeground(new Color(255, 235, 200));
            title.setFont(title.getFont().deriveFont(Font.BOLD, 36f));
            title.setAlignmentX(Component.CENTER_ALIGNMENT);

            nameField = new JTextField("Player" + (System.currentTimeMillis() % 1000));
            nameField.setMaximumSize(new Dimension(320, 36));
            nameField.setAlignmentX(Component.CENTER_ALIGNMENT);

            serverField = new JTextField("ws://localhost:7070");
            serverField.setMaximumSize(new Dimension(320, 36));
            serverField.setAlignmentX(Component.CENTER_ALIGNMENT);

            findButton = bigButton("Find Match");
            cancelButton = bigButton("Cancel");
            cancelButton.setEnabled(false);

            findButton.addActionListener(e -> findMatch());
            cancelButton.addActionListener(e -> cancelMatch());

            JButton back = bigButton("Back to Menu");
            back.addActionListener(e -> onBackToMenu.run());

            statusLabel = new JLabel(" ");
            statusLabel.setForeground(new Color(200, 215, 235));
            statusLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

            column.add(title);
            column.add(Box.createVerticalStrut(20));
            column.add(label("Username"));
            column.add(nameField);
            column.add(Box.createVerticalStrut(10));
            column.add(label("Server"));
            column.add(serverField);
            column.add(Box.createVerticalStrut(20));
            column.add(findButton);
            column.add(Box.createVerticalStrut(8));
            column.add(cancelButton);
            column.add(Box.createVerticalStrut(20));
            column.add(statusLabel);
            column.add(Box.createVerticalStrut(20));
            column.add(back);

            lobby.add(column);

            matchHolder = new JPanel(new BorderLayout());
            matchHolder.setBackground(new Color(8, 12, 18));

            root.add(lobby, "lobby");
            root.add(matchHolder, "match");
        }

        JPanel root() {
            return root;
        }

        void showLobby() {
            inner.show(root, "lobby");
        }

        private JLabel label(String text) {
            JLabel l = new JLabel(text);
            l.setForeground(new Color(180, 200, 230));
            l.setAlignmentX(Component.CENTER_ALIGNMENT);
            return l;
        }

        private void findMatch() {
            username = nameField.getText().trim();
            if (username.isEmpty()) {
                statusLabel.setText("Pick a username first.");
                return;
            }

            URI uri;
            try {
                uri = new URI(serverField.getText().trim());
            } catch (Exception ex) {
                statusLabel.setText("Invalid server URL: " + ex.getMessage());
                return;
            }

            findButton.setEnabled(false);
            nameField.setEnabled(false);
            serverField.setEnabled(false);
            statusLabel.setText("Connecting to " + uri + "...");

            disconnect();
            client = new MultiplayerClient(uri, username, new EdtListener(new MultiplayerClient.Listener() {
                @Override public void onConnected() {
                    statusLabel.setText("Connected. Looking for an opponent...");
                    cancelButton.setEnabled(true);
                    client.findMatch();
                }
                @Override public void onMatchStart(long seed, int targetScore, String opponentName) {
                    enterMatch(seed, targetScore, opponentName);
                }
                @Override public void onOpponentScore(int value) {
                    if (board != null) board.setOpponentScore(value);
                }
                @Override public void onOpponentLeft() {
                    if (board != null) {
                        board.endMultiplayerMatch(username, board.getScoreThisLevel(), 0, "OPPONENT_LEFT");
                    }
                }
                @Override public void onMatchEnd(String winner, int yourScore, int opponentScore, String reason) {
                    if (board != null) {
                        board.endMultiplayerMatch(winner, yourScore, opponentScore, reason);
                        showEndDialog(winner, yourScore, opponentScore, reason);
                    }
                }
                @Override public void onError(String message) {
                    statusLabel.setText("Error: " + message);
                    resetLobbyControls();
                }
                @Override public void onDisconnected() {
                    statusLabel.setText("Disconnected.");
                    resetLobbyControls();
                }
            }));
            client.connect();
        }

        private void cancelMatch() {
            if (client != null) client.cancelMatch();
            disconnect();
            statusLabel.setText("Cancelled.");
            resetLobbyControls();
        }

        private void resetLobbyControls() {
            findButton.setEnabled(true);
            nameField.setEnabled(true);
            serverField.setEnabled(true);
            cancelButton.setEnabled(false);
        }

        private void enterMatch(long seed, int targetScore, String opponentName) {
            matchHolder.removeAll();

            board = new OreoCrushGame();
            board.setLocalUsername(username);
            ScoreboardPanel scoreboard = new ScoreboardPanel();
            board.setScoreboard(scoreboard);
            board.setScoreListener(score -> {
                if (client != null) client.sendScore(score);
            });

            JButton leave = new JButton("Forfeit & Leave");
            leave.addActionListener(e -> {
                disconnect();
                onBackToMenu.run();
            });

            JLabel tips = new JLabel("Race to the target score. Same starting board for both players.");
            tips.setForeground(new Color(230, 237, 246));

            JPanel controls = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 0));
            controls.setOpaque(false);
            controls.add(leave);

            JPanel boardShell = new JPanel(new BorderLayout(0, 10));
            boardShell.setOpaque(false);
            boardShell.add(controls, BorderLayout.NORTH);
            boardShell.add(board, BorderLayout.CENTER);
            boardShell.add(tips, BorderLayout.SOUTH);

            JPanel root = new JPanel(new BorderLayout(12, 12));
            root.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
            root.setBackground(new Color(8, 12, 18));
            root.add(boardShell, BorderLayout.CENTER);
            root.add(scoreboard, BorderLayout.EAST);

            matchHolder.add(root, BorderLayout.CENTER);
            matchHolder.revalidate();
            matchHolder.repaint();

            board.startMultiplayerMatch(seed, targetScore, opponentName);
            inner.show(this.root, "match");
        }

        private void showEndDialog(String winner, int yourScore, int opponentScore, String reason) {
            boolean won = winner != null && winner.equals(username);
            String header = won ? "You win!" : "You lose";
            if ("OPPONENT_LEFT".equals(reason)) header += " (opponent left)";
            String body = header + "\nYour score: " + yourScore + "\nOpponent: " + opponentScore;
            int choice = JOptionPane.showOptionDialog(
                    frame,
                    body,
                    "Match Over",
                    JOptionPane.DEFAULT_OPTION,
                    won ? JOptionPane.INFORMATION_MESSAGE : JOptionPane.WARNING_MESSAGE,
                    null,
                    new Object[]{"Play Again", "Back to Lobby"},
                    "Play Again"
            );
            if (choice == 0) {
                if (client != null) client.findMatch();
                statusLabel.setText("Looking for another opponent...");
                showLobby();
            } else {
                showLobby();
            }
        }

        void disconnect() {
            if (client != null) {
                client.close();
                client = null;
            }
            if (board != null) {
                board.exitMultiplayerMode();
                board = null;
            }
        }
    }

    /** Forwards Listener callbacks onto the EDT so Swing code is safe. */
    static class EdtListener implements MultiplayerClient.Listener {
        private final MultiplayerClient.Listener delegate;
        EdtListener(MultiplayerClient.Listener delegate) { this.delegate = delegate; }
        private void run(Runnable r) { SwingUtilities.invokeLater(r); }
        @Override public void onConnected() { run(delegate::onConnected); }
        @Override public void onMatchStart(long seed, int targetScore, String opp) { run(() -> delegate.onMatchStart(seed, targetScore, opp)); }
        @Override public void onOpponentScore(int value) { run(() -> delegate.onOpponentScore(value)); }
        @Override public void onOpponentLeft() { run(delegate::onOpponentLeft); }
        @Override public void onMatchEnd(String w, int ys, int os, String r) { run(() -> delegate.onMatchEnd(w, ys, os, r)); }
        @Override public void onError(String m) { run(() -> delegate.onError(m)); }
        @Override public void onDisconnected() { run(delegate::onDisconnected); }
    }
}
