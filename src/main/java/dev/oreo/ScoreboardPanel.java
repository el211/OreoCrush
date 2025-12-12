package dev.oreo;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.InputStream;

public class ScoreboardPanel extends JPanel {

    private BufferedImage bg;

    private int level;
    private int moves;
    private int score;
    private int target;

    public ScoreboardPanel() {
        setOpaque(false);
        setPreferredSize(new Dimension(320, 480)); // match your image

        // IMPORTANT: resource path must match src/main/resources/ui/scoreboard_panel.png
        bg = loadImage("/ui/scoreboard_panel.png");
        if (bg == null) {
            System.err.println("[ScoreboardPanel] Background image NOT found: /ui/scoreboard_panel.png");
        }
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
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // draw background image
        if (bg != null) {
            g2.drawImage(bg, 0, 0, getWidth(), getHeight(), null);
        } else {
            // fallback so you SEE something even if image missing
            g2.setPaint(new GradientPaint(0, 0, new Color(10, 16, 30), 0, getHeight(), new Color(3, 7, 18)));
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), 26, 26);
        }

        // text overlay (aligned like your screenshot)
        g2.setColor(new Color(240, 245, 255));

        g2.setFont(getFont().deriveFont(Font.BOLD, 20f));
        g2.drawString("Oreo Crush", 26, 52);

        g2.setFont(getFont().deriveFont(Font.PLAIN, 14f));
        g2.drawString("Level: " + level, 26, 105);
        g2.drawString("Moves: " + moves, 26, 132);
        g2.drawString("Score: " + score, 26, 159);
        g2.drawString("Target: " + target, 26, 186);

        // simple progress bar
        int barX = 26, barY = 235, barW = getWidth() - 52, barH = 14;
        float p = (target <= 0) ? 0f : Math.min(1f, score / (float) target);

        g2.setColor(new Color(255, 255, 255, 40));
        g2.fillRoundRect(barX, barY, barW, barH, 10, 10);

        g2.setColor(new Color(120, 200, 255, 160));
        g2.fillRoundRect(barX, barY, (int) (barW * p), barH, 10, 10);

        g2.dispose();
    }

    private BufferedImage loadImage(String resourcePath) {
        try (InputStream in = getClass().getResourceAsStream(resourcePath)) {
            if (in == null) return null;
            return ImageIO.read(in);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}
