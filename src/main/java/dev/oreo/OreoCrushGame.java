package dev.oreo;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.*;
import java.util.List;
import javax.swing.Timer;

public class OreoCrushGame extends JPanel {

    private static final int BOARD_SIZE = 8;
    private static final int TILE_SIZE = 60;
    private static final int CANVAS_SIZE = BOARD_SIZE * TILE_SIZE;

    private static final int MOVE_LIMIT_BASE = 20;

    private static final int CLASSIC = 0;
    private static final int CHOCO = 1;
    private static final int VANILLA = 2;
    private static final int SPRINKLE = 3;
    private static final int TNT = 4;
    private static final int TYPE_COUNT = 5;

    private static final int SWAP_MS = 160;
    private static final int CLEAR_MS = 180;
    private static final int FALL_MS = 220;
    private static final int EXPLODE_MS = 220;

    private final Random random = new Random();

    private final BufferedImage[] cookieImages = new BufferedImage[TYPE_COUNT];

    // grid of Piece objects
    private final Piece[][] grid = new Piece[BOARD_SIZE][BOARD_SIZE];

    private Point selected = null;

    // game state / animations
    private final Timer timer;
    private long nowMs;

    private AnimState animState = AnimState.IDLE;

    private SwapAnim swapAnim;
    private ClearAnim clearAnim;
    private FallAnim fallAnim;
    private ExplodeAnim explodeAnim;

    // scoring & levels
    private int level = 1;
    private int moves = MOVE_LIMIT_BASE;
    private int scoreThisLevel = 0;
    private int targetScore = 500; // level 1 target

    public OreoCrushGame() {
        setPreferredSize(new Dimension(CANVAS_SIZE, CANVAS_SIZE));
        setBackground(new Color(12, 18, 32));

        loadTextures();
        initBoardNoMatches();
        setupMouse();

        timer = new Timer(1000 / 60, e -> {
            nowMs = System.currentTimeMillis();
            tick();
            repaint();
        });
        timer.start();
    }

    // ---------- Assets ----------
    private void loadTextures() {
        cookieImages[CLASSIC] = loadImage("/assets/cookie_classic.png");
        cookieImages[CHOCO] = loadImage("/assets/cookie_choco.png");
        cookieImages[VANILLA] = loadImage("/assets/cookie_vanilla.png");
        cookieImages[SPRINKLE] = loadImage("/assets/cookie_sprinkle.png");
        cookieImages[TNT] = loadImage("/assets/cookie_tnt.png"); // <-- add this file
    }

    private BufferedImage loadImage(String path) {
        try (InputStream in = getClass().getResourceAsStream(path)) {
            if (in == null) {
                System.err.println("Missing resource: " + path);
                return null;
            }
            return ImageIO.read(in);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    // ---------- Piece ----------
    private static class Piece {
        int type;
        float px, py;          // current pixel position
        float tx, ty;          // target pixel position
        float alpha = 1f;      // for fade
        float scale = 1f;      // for pop
        boolean clearing = false;

        Piece(int type, float px, float py) {
            this.type = type;
            this.px = px; this.py = py;
            this.tx = px; this.ty = py;
        }
    }

    private enum AnimState { IDLE, SWAPPING, CLEARING, FALLING, EXPLODING }

    // ---------- Board init ----------
    private void initBoardNoMatches() {
        for (int r = 0; r < BOARD_SIZE; r++) {
            for (int c = 0; c < BOARD_SIZE; c++) {
                int t;
                do {
                    t = randomNormalType();
                    setPiece(r, c, new Piece(t, c * TILE_SIZE, r * TILE_SIZE));
                } while (createsMatchAt(r, c));
            }
        }

        selected = null;
        moves = MOVE_LIMIT_BASE + Math.max(0, level - 1); // tiny scaling if you want
        scoreThisLevel = 0;
        targetScore = 500 + (level - 1) * 250;
        animState = AnimState.IDLE;
    }

    private int randomNormalType() {
        // exclude TNT from random fill by default
        return random.nextInt(4);
    }

    private void setPiece(int r, int c, Piece p) {
        grid[r][c] = p;
        if (p != null) {
            p.tx = c * TILE_SIZE;
            p.ty = r * TILE_SIZE;
        }
    }

    private boolean createsMatchAt(int row, int col) {
        Piece p = grid[row][col];
        if (p == null) return false;
        int type = p.type;

        if (col >= 2 && grid[row][col - 1].type == type && grid[row][col - 2].type == type) return true;
        if (row >= 2 && grid[row - 1][col].type == type && grid[row - 2][col].type == type) return true;
        return false;
    }

    private boolean isInsideBoard(int r, int c) {
        return r >= 0 && r < BOARD_SIZE && c >= 0 && c < BOARD_SIZE;
    }

    // ---------- Matching ----------
    private MatchResult findMatches() {
        boolean[][] match = new boolean[BOARD_SIZE][BOARD_SIZE];
        List<Point> spawnTNT = new ArrayList<>();
        boolean any = false;

        // horizontal runs
        for (int r = 0; r < BOARD_SIZE; r++) {
            int start = 0;
            while (start < BOARD_SIZE) {
                int type = grid[r][start].type;
                int c = start + 1;
                while (c < BOARD_SIZE && grid[r][c].type == type) c++;
                int len = c - start;

                if (len >= 3) {
                    any = true;
                    for (int x = start; x < c; x++) match[r][x] = true;

                    // reward: len >= 4 => spawn TNT at middle of the run (it should survive the clear)
                    if (len >= 4 && type != TNT) {
                        int mid = start + len / 2;
                        spawnTNT.add(new Point(mid, r)); // x=col, y=row
                    }
                }
                start = c;
            }
        }

        // vertical runs
        for (int c = 0; c < BOARD_SIZE; c++) {
            int start = 0;
            while (start < BOARD_SIZE) {
                int type = grid[start][c].type;
                int r = start + 1;
                while (r < BOARD_SIZE && grid[r][c].type == type) r++;
                int len = r - start;

                if (len >= 3) {
                    any = true;
                    for (int y = start; y < r; y++) match[y][c] = true;

                    if (len >= 4 && type != TNT) {
                        int mid = start + len / 2;
                        spawnTNT.add(new Point(c, mid)); // x=col, y=row
                    }
                }
                start = r;
            }
        }

        if (!any) return null;

        // If a TNT is spawned, it should NOT be cleared
        for (Point p : spawnTNT) {
            if (isInsideBoard(p.y, p.x)) match[p.y][p.x] = false;
        }

        return new MatchResult(match, spawnTNT);
    }

    private static class MatchResult {
        boolean[][] match;
        List<Point> spawnTNT;
        MatchResult(boolean[][] match, List<Point> spawnTNT) {
            this.match = match;
            this.spawnTNT = spawnTNT;
        }
    }

    // ---------- Animations tick ----------
    private void tick() {
        switch (animState) {
            case SWAPPING:
                tickSwap();
                break;
            case CLEARING:
                tickClear();
                break;
            case FALLING:
                tickFall();
                break;
            case EXPLODING:
                tickExplode();
                break;
            case IDLE:
            default:
                break;
        }
    }


    // ---------- Swap ----------
    private static class SwapAnim {
        Point a, b;
        Piece pa, pb;
        long startMs;
    }

    private void startSwap(Point a, Point b) {
        int r1 = a.y, c1 = a.x;
        int r2 = b.y, c2 = b.x;

        swapAnim = new SwapAnim();
        swapAnim.a = a;
        swapAnim.b = b;
        swapAnim.pa = grid[r1][c1];
        swapAnim.pb = grid[r2][c2];
        swapAnim.startMs = nowMs;

        // set targets to swap visually
        swapAnim.pa.tx = c2 * TILE_SIZE;
        swapAnim.pa.ty = r2 * TILE_SIZE;
        swapAnim.pb.tx = c1 * TILE_SIZE;
        swapAnim.pb.ty = r1 * TILE_SIZE;

        animState = AnimState.SWAPPING;
    }

    private void tickSwap() {
        float t = clamp01((nowMs - swapAnim.startMs) / (float) SWAP_MS);
        float e = easeInOut(t);

        lerpToTarget(swapAnim.pa, e);
        lerpToTarget(swapAnim.pb, e);

        if (t >= 1f) {
            // commit swap in grid
            int r1 = swapAnim.a.y, c1 = swapAnim.a.x;
            int r2 = swapAnim.b.y, c2 = swapAnim.b.x;

            Piece pa = swapAnim.pa;
            Piece pb = swapAnim.pb;

            setPiece(r1, c1, pb);
            setPiece(r2, c2, pa);

            // snap to target
            snapPiece(pb);
            snapPiece(pa);

            // check matches
            MatchResult mr = findMatches();
            if (mr == null) {
                // swap back (revert) with another animation
                Point backA = new Point(c1, r1);
                Point backB = new Point(c2, r2);

                // start reverse swap by reusing
                startSwap(backA, backB);
                // BUT: we need to restore original positions in grid first to animate back correctly
                // so swap grid immediately and animate again
                setPiece(r1, c1, pa);
                setPiece(r2, c2, pb);
                snapPiece(pa);
                snapPiece(pb);

                // and update animation pieces references
                swapAnim.pa = pa;
                swapAnim.pb = pb;
                swapAnim.a = backA;
                swapAnim.b = backB;

                // after reverse ends, we should return to IDLE (no extra clear)
                // mark by setting startMs and a flag
                // easiest: if no match, after reverse swap ends we stop in IDLE
                // we detect it by checking matches again at the end => will still be null.
                return;
            }

            // successful move
            moves--;

            // spawn TNT rewards now (before clearing anim) so it appears immediately
            for (Point p : mr.spawnTNT) {
                int rr = p.y, cc = p.x;
                if (isInsideBoard(rr, cc)) {
                    Piece tnt = grid[rr][cc];
                    if (tnt != null) tnt.type = TNT;
                }
            }

            startClear(mr.match);
        }
    }

    // ---------- Clear ----------
    private static class ClearAnim {
        boolean[][] match;
        List<Piece> clearingPieces = new ArrayList<>();
        long startMs;
    }

    private void startClear(boolean[][] match) {
        clearAnim = new ClearAnim();
        clearAnim.match = match;
        clearAnim.startMs = nowMs;

        clearAnim.clearingPieces.clear();
        for (int r = 0; r < BOARD_SIZE; r++) {
            for (int c = 0; c < BOARD_SIZE; c++) {
                if (match[r][c]) {
                    Piece p = grid[r][c];
                    if (p != null) {
                        p.clearing = true;
                        p.alpha = 1f;
                        p.scale = 1f;
                        clearAnim.clearingPieces.add(p);
                    }
                }
            }
        }

        animState = AnimState.CLEARING;
    }

    private void tickClear() {
        float t = clamp01((nowMs - clearAnim.startMs) / (float) CLEAR_MS);
        float e = easeOut(t);

        for (Piece p : clearAnim.clearingPieces) {
            p.alpha = 1f - e;
            p.scale = 1f + 0.25f * e; // slight pop
        }

        if (t >= 1f) {
            // remove matched pieces
            int cleared = 0;
            for (int r = 0; r < BOARD_SIZE; r++) {
                for (int c = 0; c < BOARD_SIZE; c++) {
                    if (clearAnim.match[r][c]) {
                        setPiece(r, c, null);
                        cleared++;
                    }
                }
            }

            // scoring: combo-ish reward
            int gained = cleared * 10;
            scoreThisLevel += gained;

            startFall();
        }
    }

    // ---------- Falling (collapse + refill) ----------
    private static class FallAnim {
        Map<Piece, Float> startY = new HashMap<>();
        long startMs;
    }

    private void startFall() {
        // collapse columns in grid (logical), but keep pieces to animate from old py to new ty
        fallAnim = new FallAnim();
        fallAnim.startMs = nowMs;

        // rebuild each column bottom-up
        for (int c = 0; c < BOARD_SIZE; c++) {
            List<Piece> kept = new ArrayList<>();
            for (int r = BOARD_SIZE - 1; r >= 0; r--) {
                if (grid[r][c] != null) kept.add(grid[r][c]);
            }

            int writeR = BOARD_SIZE - 1;
            for (Piece p : kept) {
                // remember old Y for animation
                fallAnim.startY.put(p, p.py);
                setPiece(writeR, c, p);
                writeR--;
            }

            // refill remaining with new pieces spawning above the top
            for (int r = writeR; r >= 0; r--) {
                int t = randomNormalType();

                // small TNT chance on refill (optional)
                if (level >= 3 && random.nextFloat() < 0.03f) t = TNT;

                Piece np = new Piece(t, c * TILE_SIZE, -TILE_SIZE * (writeR - r + 1));
                // spawn above and fall to target
                np.tx = c * TILE_SIZE;
                np.ty = r * TILE_SIZE;
                fallAnim.startY.put(np, np.py);
                setPiece(r, c, np);
            }
        }

        animState = AnimState.FALLING;
    }

    private void tickFall() {
        float t = clamp01((nowMs - fallAnim.startMs) / (float) FALL_MS);
        float e = easeInOut(t);

        for (int r = 0; r < BOARD_SIZE; r++) {
            for (int c = 0; c < BOARD_SIZE; c++) {
                Piece p = grid[r][c];
                if (p == null) continue;

                Float sy = fallAnim.startY.get(p);
                if (sy == null) continue;

                p.px = p.tx;
                p.py = lerp(sy, p.ty, e);
                p.alpha = 1f;
                p.scale = 1f;
                p.clearing = false;
            }
        }

        if (t >= 1f) {
            // snap
            for (int r = 0; r < BOARD_SIZE; r++) {
                for (int c = 0; c < BOARD_SIZE; c++) {
                    Piece p = grid[r][c];
                    if (p != null) snapPiece(p);
                }
            }

            // chain reactions (auto resolve)
            MatchResult mr = findMatches();
            if (mr != null) {
                for (Point p : mr.spawnTNT) {
                    int rr = p.y, cc = p.x;
                    if (isInsideBoard(rr, cc) && grid[rr][cc] != null) grid[rr][cc].type = TNT;
                }
                startClear(mr.match);
                return;
            }

            // level up?
            if (scoreThisLevel >= targetScore) {
                level++;
                initBoardNoMatches();
                return;
            }

            animState = AnimState.IDLE;
        }
    }

    // ---------- Explosion (TNT click) ----------
    private static class ExplodeAnim {
        int centerR, centerC;
        List<Point> cells = new ArrayList<>();
        List<Piece> pieces = new ArrayList<>();
        long startMs;
    }

    private void startExplode(int r, int c) {
        explodeAnim = new ExplodeAnim();
        explodeAnim.centerR = r;
        explodeAnim.centerC = c;
        explodeAnim.startMs = nowMs;

        explodeAnim.cells.clear();
        explodeAnim.pieces.clear();

        for (int dr = -1; dr <= 1; dr++) {
            for (int dc = -1; dc <= 1; dc++) {
                int rr = r + dr, cc = c + dc;
                if (!isInsideBoard(rr, cc)) continue;
                if (grid[rr][cc] == null) continue;
                explodeAnim.cells.add(new Point(cc, rr));
                explodeAnim.pieces.add(grid[rr][cc]);
            }
        }

        animState = AnimState.EXPLODING;
    }

    private void tickExplode() {
        float t = clamp01((nowMs - explodeAnim.startMs) / (float) EXPLODE_MS);
        float e = easeOut(t);

        for (Piece p : explodeAnim.pieces) {
            p.alpha = 1f - e;
            p.scale = 1f + 0.35f * e;
        }

        if (t >= 1f) {
            // clear explosion cells
            int cleared = 0;
            for (Point cell : explodeAnim.cells) {
                int rr = cell.y, cc = cell.x;
                if (grid[rr][cc] != null) {
                    setPiece(rr, cc, null);
                    cleared++;
                }
            }

            // TNT costs a move
            moves--;

            // score reward
            scoreThisLevel += cleared * 12;

            startFall();
        }
    }

    // ---------- Input ----------
    private void setupMouse() {
        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (animState != AnimState.IDLE) return;

                if (moves <= 0) {
                    initBoardNoMatches();
                    repaint();
                    return;
                }

                int c = e.getX() / TILE_SIZE;
                int r = e.getY() / TILE_SIZE;
                if (!isInsideBoard(r, c)) return;

                Piece clicked = grid[r][c];
                if (clicked == null) return;

                // TNT: click to explode immediately
                if (clicked.type == TNT) {
                    startExplode(r, c);
                    selected = null;
                    return;
                }

                if (selected == null) {
                    selected = new Point(c, r);
                } else {
                    Point second = new Point(c, r);
                    handleSwap(selected, second);
                    selected = null;
                }
            }
        });
    }

    private boolean adjacent(Point a, Point b) {
        int dx = Math.abs(a.x - b.x);
        int dy = Math.abs(a.y - b.y);
        return dx + dy == 1;
    }

    private void handleSwap(Point a, Point b) {
        if (!adjacent(a, b)) return;

        int r1 = a.y, c1 = a.x;
        int r2 = b.y, c2 = b.x;

        // start a visual swap; commit/revert happens when animation ends
        startSwap(a, b);
    }

    // ---------- Rendering ----------
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // background
        g2.setPaint(new GradientPaint(0, 0, new Color(15, 23, 42),
                0, getHeight(), new Color(3, 7, 18)));
        g2.fillRect(0, 0, getWidth(), getHeight());

        // draw slots
        for (int r = 0; r < BOARD_SIZE; r++) {
            for (int c = 0; c < BOARD_SIZE; c++) {
                int x = c * TILE_SIZE;
                int y = r * TILE_SIZE;
                g2.setColor(new Color(30, 41, 59));
                g2.fillRoundRect(x + 4, y + 4, TILE_SIZE - 8, TILE_SIZE - 8, 12, 12);
            }
        }

        // draw pieces by pixel position
        for (int r = 0; r < BOARD_SIZE; r++) {
            for (int c = 0; c < BOARD_SIZE; c++) {
                Piece p = grid[r][c];
                if (p == null) continue;

                int t = p.type;
                BufferedImage img = (t >= 0 && t < TYPE_COUNT) ? cookieImages[t] : null;

                float cx = p.px + TILE_SIZE / 2f;
                float cy = p.py + TILE_SIZE / 2f;

                Composite oldComp = g2.getComposite();
                g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, clamp01(p.alpha)));

                AffineTransform oldTx = g2.getTransform();
                AffineTransform tx = new AffineTransform(oldTx);
                tx.translate(cx, cy);
                tx.scale(p.scale, p.scale);
                tx.translate(-cx, -cy);
                g2.setTransform(tx);

                int drawX = Math.round(p.px) + 6;
                int drawY = Math.round(p.py) + 6;
                int size = TILE_SIZE - 12;

                if (img != null) {
                    g2.drawImage(img, drawX, drawY, size, size, null);
                } else {
                    g2.setColor(Color.WHITE);
                    g2.fillOval(drawX + 4, drawY + 4, size - 8, size - 8);
                }

                // TNT visual fallback if missing png
                if (t == TNT && img == null) {
                    g2.setColor(new Color(255, 80, 80));
                    g2.setFont(getFont().deriveFont(Font.BOLD, 16f));
                    g2.drawString("TNT", drawX + 10, drawY + 28);
                }

                g2.setTransform(oldTx);
                g2.setComposite(oldComp);
            }
        }

        // selection highlight
        if (selected != null && animState == AnimState.IDLE) {
            int x = selected.x * TILE_SIZE;
            int y = selected.y * TILE_SIZE;
            g2.setColor(new Color(248, 250, 252));
            Stroke old = g2.getStroke();
            g2.setStroke(new BasicStroke(3f));
            g2.drawRoundRect(x + 3, y + 3, TILE_SIZE - 6, TILE_SIZE - 6, 14, 14);
            g2.setStroke(old);
        }

        // HUD
        g2.setColor(new Color(248, 250, 252));
        g2.setFont(getFont().deriveFont(Font.BOLD, 14f));
        g2.drawString("Level: " + level, 10, 18);
        g2.drawString("Moves: " + moves, 10, 36);
        g2.drawString("Score: " + scoreThisLevel + " / " + targetScore, 10, 54);

        // game over overlay
        if (moves <= 0) {
            g2.setColor(new Color(15, 23, 42, 210));
            g2.fillRect(0, 0, getWidth(), getHeight());

            g2.setColor(new Color(248, 250, 252));
            g2.setFont(getFont().deriveFont(Font.BOLD, 28f));
            String t1 = "Game Over!";
            int w1 = g2.getFontMetrics().stringWidth(t1);
            g2.drawString(t1, (getWidth() - w1) / 2, getHeight() / 2 - 10);

            g2.setFont(getFont().deriveFont(Font.PLAIN, 18f));
            String t2 = "Click to restart.";
            int w2 = g2.getFontMetrics().stringWidth(t2);
            g2.drawString(t2, (getWidth() - w2) / 2, getHeight() / 2 + 20);
        }

        g2.dispose();
    }

    // ---------- Helpers ----------
    private void lerpToTarget(Piece p, float t) {
        p.px = lerp(p.px, p.tx, t);
        p.py = lerp(p.py, p.ty, t);
    }

    private void snapPiece(Piece p) {
        p.px = p.tx;
        p.py = p.ty;
        p.alpha = 1f;
        p.scale = 1f;
        p.clearing = false;
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    private static float clamp01(float v) {
        return Math.max(0f, Math.min(1f, v));
    }

    private static float easeInOut(float t) {
        // smoothstep
        return t * t * (3f - 2f * t);
    }

    private static float easeOut(float t) {
        float u = 1f - t;
        return 1f - u * u;
    }
}
