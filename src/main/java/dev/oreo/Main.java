package dev.oreo;

import javax.swing.*;
import java.awt.*;

public class Main {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            OreoCrushGame game = new OreoCrushGame();
            ScoreboardPanel hud = new ScoreboardPanel();
            game.setScoreboard(hud);

            JPanel root = new JPanel(new BorderLayout(12, 12));
            root.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
            root.setBackground(new Color(8, 12, 18));

            root.add(game, BorderLayout.CENTER);
            root.add(hud, BorderLayout.EAST);

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
