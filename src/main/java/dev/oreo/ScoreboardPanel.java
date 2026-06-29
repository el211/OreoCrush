package dev.oreo;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.InputStream;

public class ScoreboardPanel extends JPanel {

    private BufferedImage bg;
    private GameStats stats = new GameStats(1, 0, 0, 0, "", "", 0, 0, 0, 0, 1, 0, "");

    public ScoreboardPanel() {
        setOpaque(false);
        setPreferredSize(new Dimension(320, 480));

        bg = loadImage("/ui/scoreboard_panel.png");
        if (bg == null) {
            System.err.println("[ScoreboardPanel] Background image NOT found: /ui/scoreboard_panel.png");
        }
    }

    public void setStats(GameStats stats) {
        this.stats = stats;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        if (bg != null) {
            g2.drawImage(bg, 0, 0, getWidth(), getHeight(), null);
        } else {
            g2.setPaint(new GradientPaint(0, 0, new Color(16, 23, 40), 0, getHeight(), new Color(4, 9, 20)));
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), 26, 26);
        }

        int x = 26;
        g2.setColor(new Color(240, 245, 255));
        g2.setFont(getFont().deriveFont(Font.BOLD, 22f));
        g2.drawString("Oreo Crush", x, 50);

        g2.setFont(getFont().deriveFont(Font.PLAIN, 14f));
        g2.setColor(new Color(215, 226, 242));
        g2.drawString("Level " + stats.level + " / Unlocked " + stats.unlockedLevel, x, 86);
        g2.drawString("Moves: " + stats.moves, x, 112);
        g2.drawString("Score: " + stats.score, x, 138);
        g2.drawString("Star Goal: " + stats.targetScore, x, 164);

        drawProgressBar(g2, x, 184, getWidth() - 52, 16,
                stats.targetScore <= 0 ? 0f : Math.min(1f, stats.score / (float) stats.targetScore),
                new Color(110, 208, 255, 180));

        g2.setFont(getFont().deriveFont(Font.BOLD, 15f));
        g2.setColor(new Color(248, 250, 252));
        g2.drawString("Objective", x, 228);

        g2.setFont(getFont().deriveFont(Font.PLAIN, 13f));
        g2.setColor(new Color(220, 228, 238));
        drawWrapped(g2, stats.objectiveTitle, x, 250, getWidth() - 52, 17);
        g2.setColor(new Color(164, 212, 255));
        g2.drawString(stats.objectiveProgress, x, 292);

        g2.setFont(getFont().deriveFont(Font.BOLD, 15f));
        g2.setColor(new Color(248, 250, 252));
        g2.drawString("Session", x, 334);

        g2.setFont(getFont().deriveFont(Font.PLAIN, 13f));
        g2.setColor(new Color(220, 228, 238));
        g2.drawString("Combo: x" + Math.max(1, stats.combo), x, 356);
        g2.drawString("Best combo: x" + Math.max(1, stats.bestCombo), x, 378);
        g2.drawString("Stars this run:", x, 400);
        drawStars(g2, x + 102, 385, stats.stars, 16);
        g2.drawString("Best level stars:", x, 422);
        drawStars(g2, x + 118, 407, stats.bestStars, 16);
        g2.drawString("Total stars: " + stats.totalStars, x, 444);

        if (stats.statusText != null && !stats.statusText.isEmpty()) {
            g2.setColor(new Color(255, 241, 184));
            drawWrapped(g2, stats.statusText, x, 470, getWidth() - 52, 16);
        }

        g2.dispose();
    }

    private void drawProgressBar(Graphics2D g2, int x, int y, int w, int h, float p, Color fill) {
        g2.setColor(new Color(255, 255, 255, 40));
        g2.fillRoundRect(x, y, w, h, 10, 10);
        g2.setColor(fill);
        g2.fillRoundRect(x, y, Math.max(0, (int) (w * p)), h, 10, 10);
    }

    private void drawWrapped(Graphics2D g2, String text, int x, int y, int width, int lineHeight) {
        if (text == null) return;

        FontMetrics fm = g2.getFontMetrics();
        String[] words = text.split("\\s+");
        String line = "";
        int lineY = y;

        for (String word : words) {
            String test = line.isEmpty() ? word : line + " " + word;
            if (fm.stringWidth(test) > width && !line.isEmpty()) {
                g2.drawString(line, x, lineY);
                line = word;
                lineY += lineHeight;
            } else {
                line = test;
            }
        }

        if (!line.isEmpty()) {
            g2.drawString(line, x, lineY);
        }
    }

    private void drawStars(Graphics2D g2, int x, int y, int count, int size) {
        for (int i = 0; i < 3; i++) {
            Shape star = starShape(x + i * (size + 7) + size / 2f, y + size / 2f, size / 2f, size / 4.5f);
            g2.setColor(i < count ? new Color(255, 218, 92) : new Color(255, 255, 255, 55));
            g2.fill(star);
            g2.setColor(new Color(40, 30, 16, 150));
            g2.draw(star);
        }
    }

    private Shape starShape(float cx, float cy, float outer, float inner) {
        Polygon polygon = new Polygon();
        for (int i = 0; i < 10; i++) {
            double angle = -Math.PI / 2.0 + i * Math.PI / 5.0;
            float radius = i % 2 == 0 ? outer : inner;
            polygon.addPoint(Math.round(cx + (float) Math.cos(angle) * radius),
                    Math.round(cy + (float) Math.sin(angle) * radius));
        }
        return polygon;
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
