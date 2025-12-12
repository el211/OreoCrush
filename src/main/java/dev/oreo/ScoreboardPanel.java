package dev.oreo;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.InputStream;

public class ScoreboardPanel extends JPanel {

    private BufferedImage bg;
    private int level, moves, score, target;

    public ScoreboardPanel() {
        setPreferredSize(new Dimension(260, 8 * 60)); // match board height
        setOpaque(false);
        bg = loadImage("/ui/scoreboard.png"); // put your image here
    }

    public void setStats(int level, int moves, int score, int target) {
        this.level = level;
        this.moves = moves;
        this.score = score;
        this.target = target;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        if (bg != null) {
            g2.drawImage(bg, 0, 0, getWidth(), getHeight(), null);
        } else {
            g2.setColor(new Color(10, 15, 25));
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), 18, 18);
        }

        g2.setColor(new Color(248, 250, 252));
        g2.setFont(getFont().deriveFont(Font.BOLD, 18f));
        g2.drawString("Oreo Crush", 18, 40);

        g2.setFont(getFont().deriveFont(Font.PLAIN, 14f));
        g2.drawString("Level: " + level, 18, 90);
        g2.drawString("Moves: " + moves, 18, 115);
        g2.drawString("Score: " + score, 18, 140);
        g2.drawString("Target: " + target, 18, 165);

        // progress bar
        int barX = 18, barY = 190, barW = getWidth() - 36, barH = 14;
        float p = target <= 0 ? 0f : Math.min(1f, score / (float) target);

        g2.setColor(new Color(255, 255, 255, 50));
        g2.fillRoundRect(barX, barY, barW, barH, 10, 10);

        g2.setColor(new Color(255, 255, 255, 160));
        g2.fillRoundRect(barX, barY, Math.round(barW * p), barH, 10, 10);

        g2.dispose();
    }

    private BufferedImage loadImage(String path) {
        try (InputStream in = getClass().getResourceAsStream(path)) {
            if (in == null) return null;
            return ImageIO.read(in);
        } catch (Exception e) {
            return null;
        }
    }
}
