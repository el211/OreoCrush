package dev.oreo;

import javax.swing.*;
import java.awt.*;

public class Main {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            OreoCrushGame board = new OreoCrushGame();
            ScoreboardPanel scoreboard = new ScoreboardPanel();
            board.setScoreboard(scoreboard);

            JButton previous = new JButton("Prev");
            JButton restart = new JButton("Restart");
            JButton next = new JButton("Next");

            previous.addActionListener(e -> board.goToLevel(board.getLevel() - 1));
            restart.addActionListener(e -> board.restartCurrentLevel());
            next.addActionListener(e -> board.goToLevel(board.getLevel() + 1));

            JPanel controls = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 0));
            controls.setOpaque(false);
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

            JPanel root = new JPanel(new BorderLayout(12, 12));
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

            JFrame frame = new JFrame("Oreo Crush");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setResizable(false);
            frame.setContentPane(root);
            frame.pack();
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
        });
    }
}
