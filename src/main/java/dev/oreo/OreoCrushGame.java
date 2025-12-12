package dev.oreo;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.Timer;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.*;
import java.util.List;

public class OreoCrushGame extends JPanel {

    // ====== BOARD CONFIG ======
    private static final int BOARD_SIZE = 8;
    private static final int TILE_SIZE  = 60;
    private static final int CANVAS_SIZE = BOARD_SIZE * TILE_SIZE;

    private static final int MOVE_LIMIT_BASE = 20;

    // ====== LEVEL TRANSITION (fade + intro voice + new music) ======
    private boolean levelTransition = false;
    private float bgFade = 0f;                 // 0 = normal, 1 = fully black overlay
    private long transitionStartMs = 0L;

    private static final int FADE_OUT_MS = 350;
    private static final int FADE_IN_MS  = 350;

    // MUST match your intro voice length (ex: 2.1s). Example path:
    // /music/level2_intro.wav  (voice "Oreo Level 2" + sting)
    private static final int INTRO_DELAY_MS = 2100;

    private boolean introPlayed = false;
    private boolean loopStarted = false;

    // ====== PIECE TYPES ======
    private static final int CLASSIC  = 0;
    private static final int CHOCO    = 1;
    private static final int VANILLA  = 2;
    private static final int SPRINKLE = 3;
    private static final int TNT      = 4;
    private static final int TYPE_COUNT = 5;

    // ====== ANIM TIMINGS ======
    private static final int SWAP_MS    = 160;
    private static final int CLEAR_MS   = 180;
    private static final int FALL_MS    = 220;
    private static final int EXPLODE_MS = 220;

    // ====== RNG ======
    private final Random random = new Random();

    // ====== AUDIO ======
    private final AudioManager audio = new AudioManager();

    // ====== BACKGROUND ======
    private BufferedImage bgImage;

    // ====== DIFFICULTY ======
    // number of normal oreo types unlocked (0..unlockedTypes-1). TNT is special and not included here.
    private int unlockedTypes = 4;
    // TNT chance on refill, increases with level
    private float tntRefillChance = 0f;
    // holes (blocked cells)
    private final boolean[][] blocked = new boolean[BOARD_SIZE][BOARD_SIZE];

    // ====== TEXTURES ======
    private final BufferedImage[] cookieImages = new BufferedImage[TYPE_COUNT];
    private BufferedImage blockedTileImage;

    // ====== GAME GRID ======
    private final Piece[][] grid = new Piece[BOARD_SIZE][BOARD_SIZE];

    // ====== INPUT ======
    private Point selected = null;

    // ====== TIMER / CLOCK ======
    private final Timer timer;
    private long nowMs;

    // ====== ANIM STATE ======
    private AnimState animState = AnimState.IDLE;
    private SwapAnim swapAnim;
    private ClearAnim clearAnim;
    private FallAnim fallAnim;
    private ExplodeAnim explodeAnim;

    // ====== SCORING / LEVELS ======
    private int level = 1;
    private int moves = MOVE_LIMIT_BASE;
    private int scoreThisLevel = 0;
    private int targetScore = 500;

    // combo multiplier for chain reactions
    private int combo = 0;

    // optional external HUD panel
    private ScoreboardPanel scoreboard;

    // ====== CONSTRUCTOR ======
    public OreoCrushGame() {
        setPreferredSize(new Dimension(CANVAS_SIZE, CANVAS_SIZE));
        setBackground(new Color(12, 18, 32));

        loadTextures();

        // Initial level setup (difficulty + blocked + background)
        applyLevelSettings();
        // Start level 1 music immediately (no intro on game start, optional)
        audio.playMusicLoop("/music/level" + level + ".wav");

        initBoardNoMatches();
        setupMouse();

        timer = new Timer(1000 / 60, e -> {
            nowMs = System.currentTimeMillis();
            tick();
            pushHud();
            repaint();
        });
        timer.start();
    }

    // ====== ASSETS ======
    private void loadTextures() {
        cookieImages[CLASSIC]  = loadImage("/assets/cookie_classic.png");
        cookieImages[CHOCO]    = loadImage("/assets/cookie_choco.png");
        cookieImages[VANILLA]  = loadImage("/assets/cookie_vanilla.png");
        cookieImages[SPRINKLE] = loadImage("/assets/cookie_sprinkle.png");
        cookieImages[TNT]      = loadImage("/assets/cookie_tnt.png"); // add this file
        blockedTileImage = loadImage("/assets/blocked_tile.png"); // you'll send this later

    }

    private BufferedImage loadImage(String path) {
        try (InputStream in = getClass().getResourceAsStream(path)) {
            if (in == null) {
                System.err.println("Missing resource: " + path);
                return null;
            }
            return ImageIO.read(in);
        } catch (Exception e) {
            System.err.println("Image load failed: " + path);
            e.printStackTrace();
            return null;
        }
    }

    // ====== LEVEL SETTINGS (difficulty + blocked + background ONLY, no loop music here) ======
    private void applyLevelSettings() {
        // you currently have 4 normal oreos (0..3). keep TNT special
        unlockedTypes = Math.min(4, 4 + (level - 1));

        // fewer moves each level, but never below 8
        moves = Math.max(8, MOVE_LIMIT_BASE - (level - 1));

        // progression curve
        targetScore = 1200 + (int) (Math.pow(level, 1.55) * 900);

        // TNT refill chance scales
        tntRefillChance = (level >= 3)
                ? Math.min(0.10f, 0.03f + (level - 3) * 0.02f)
                : 0f;

        // blocked holes scale with level
        clearBlocked();
        int blocks = Math.min(10, (level - 1) * 2);
        addRandomBlocks(blocks);

        // background per level
        bgImage = loadImage("/backgrounds/bg_level" + level + ".png");
        if (bgImage == null) bgImage = loadImage("/backgrounds/bg_level1.png");
    }

    private void clearBlocked() {
        for (int r = 0; r < BOARD_SIZE; r++) {
            for (int c = 0; c < BOARD_SIZE; c++) blocked[r][c] = false;
        }
    }

    private void addRandomBlocks(int count) {
        int tries = 0;
        while (count > 0 && tries++ < 500) {
            int r = 1 + random.nextInt(BOARD_SIZE - 2);
            int c = 1 + random.nextInt(BOARD_SIZE - 2);
            if (blocked[r][c]) continue;
            blocked[r][c] = true;
            count--;
        }
    }

    // ====== PIECE ======
    private static class Piece {
        int type;
        float px, py;   // current position (pixels)
        float tx, ty;   // target position (pixels)
        float alpha = 1f;
        float scale = 1f;

        Piece(int type, float px, float py) {
            this.type = type;
            this.px = px;
            this.py = py;
            this.tx = px;
            this.ty = py;
        }
    }

    private enum AnimState { IDLE, SWAPPING, CLEARING, FALLING, EXPLODING }

    // ====== BOARD INIT ======
    private void initBoardNoMatches() {
        for (int r = 0; r < BOARD_SIZE; r++) {
            for (int c = 0; c < BOARD_SIZE; c++) {

                if (blocked[r][c]) {
                    setPiece(r, c, null);
                    continue;
                }

                int t;
                do {
                    t = randomNormalType();
                    setPiece(r, c, new Piece(t, c * TILE_SIZE, r * TILE_SIZE));
                } while (createsMatchAt(r, c));
            }
        }

        scoreThisLevel = 0;
        combo = 0;
        selected = null;
        animState = AnimState.IDLE;

        pushHud();
    }

    private int randomNormalType() {
        return random.nextInt(Math.max(1, unlockedTypes));
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

        if (col >= 2
                && grid[row][col - 1] != null
                && grid[row][col - 2] != null
                && grid[row][col - 1].type == type
                && grid[row][col - 2].type == type) return true;

        if (row >= 2
                && grid[row - 1][col] != null
                && grid[row - 2][col] != null
                && grid[row - 1][col].type == type
                && grid[row - 2][col].type == type) return true;

        return false;
    }

    private boolean isInsideBoard(int r, int c) {
        return r >= 0 && r < BOARD_SIZE && c >= 0 && c < BOARD_SIZE;
    }

    // ====== MATCHING ======
    private MatchResult findMatches() {
        boolean[][] match = new boolean[BOARD_SIZE][BOARD_SIZE];
        List<Point> spawnTNT = new ArrayList<>();
        boolean any = false;

        // horizontal
        for (int r = 0; r < BOARD_SIZE; r++) {
            int start = 0;
            while (start < BOARD_SIZE) {
                if (grid[r][start] == null) { start++; continue; }

                int type = grid[r][start].type;
                int c = start + 1;

                while (c < BOARD_SIZE && grid[r][c] != null && grid[r][c].type == type) c++;

                int len = c - start;
                if (type != TNT && len >= 3) {
                    any = true;
                    for (int x = start; x < c; x++) match[r][x] = true;

                    if (len >= 4) {
                        int mid = start + len / 2;
                        spawnTNT.add(new Point(mid, r)); // x=col, y=row
                    }
                }

                start = c;
            }
        }

        // vertical
        for (int c = 0; c < BOARD_SIZE; c++) {
            int start = 0;
            while (start < BOARD_SIZE) {
                if (grid[start][c] == null) { start++; continue; }

                int type = grid[start][c].type;
                int r = start + 1;

                while (r < BOARD_SIZE && grid[r][c] != null && grid[r][c].type == type) r++;

                int len = r - start;
                if (type != TNT && len >= 3) {
                    any = true;
                    for (int y = start; y < r; y++) match[y][c] = true;

                    if (len >= 4) {
                        int mid = start + len / 2;
                        spawnTNT.add(new Point(c, mid)); // x=col, y=row
                    }
                }

                start = r;
            }
        }

        if (!any) return null;

        // TNT spawn cells should not be cleared
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

    // ====== CLEAR SOUNDS ======
    private void playClearSounds(boolean[][] match) {
        boolean hasRow = false;
        boolean hasCol = false;

        for (int r = 0; r < BOARD_SIZE; r++) {
            int run = 0;
            for (int c = 0; c < BOARD_SIZE; c++) {
                if (match[r][c]) run++;
                else {
                    if (run >= 3) hasRow = true;
                    run = 0;
                }
            }
            if (run >= 3) hasRow = true;
        }

        for (int c = 0; c < BOARD_SIZE; c++) {
            int run = 0;
            for (int r = 0; r < BOARD_SIZE; r++) {
                if (match[r][c]) run++;
                else {
                    if (run >= 3) hasCol = true;
                    run = 0;
                }
            }
            if (run >= 3) hasCol = true;
        }

        if (hasRow) audio.playSfx("/sfx/clear_row.wav");
        if (hasCol) audio.playSfx("/sfx/clear_col.wav");
    }

    // ====== GAME TICK ======
    private void tick() {
        // Level transition has priority (fade out -> intro voice -> new music -> fade in)
        if (levelTransition) {
            tickLevelTransition();
            return;
        }

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

    // ====== SWAP ======
    private static class SwapAnim {
        Point a, b;
        Piece pa, pb;
        long startMs;
        boolean reverting;
    }

    private void startSwap(Point a, Point b, boolean reverting) {
        int r1 = a.y, c1 = a.x;
        int r2 = b.y, c2 = b.x;

        swapAnim = new SwapAnim();
        swapAnim.a = a;
        swapAnim.b = b;
        swapAnim.pa = grid[r1][c1];
        swapAnim.pb = grid[r2][c2];
        swapAnim.startMs = nowMs;
        swapAnim.reverting = reverting;

        if (swapAnim.pa == null || swapAnim.pb == null) {
            animState = AnimState.IDLE;
            return;
        }

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
            int r1 = swapAnim.a.y, c1 = swapAnim.a.x;
            int r2 = swapAnim.b.y, c2 = swapAnim.b.x;

            Piece pa = swapAnim.pa;
            Piece pb = swapAnim.pb;

            setPiece(r1, c1, pb);
            setPiece(r2, c2, pa);
            snapPiece(pb);
            snapPiece(pa);

            if (swapAnim.reverting) {
                animState = AnimState.IDLE;
                return;
            }

            MatchResult mr = findMatches();
            if (mr == null) {
                startSwap(new Point(c1, r1), new Point(c2, r2), true);
                setPiece(r1, c1, pa);
                setPiece(r2, c2, pb);
                snapPiece(pa);
                snapPiece(pb);
                return;
            }

            audio.playSfx("/sfx/swap.wav");
            moves--;
            if (moves <= 0) audio.playSfx("/sfx/gameover.wav");

            combo = 0;

            for (Point p : mr.spawnTNT) {
                int rr = p.y, cc = p.x;
                if (isInsideBoard(rr, cc) && grid[rr][cc] != null) grid[rr][cc].type = TNT;
            }

            startClear(mr.match);
        }
    }

    // ====== CLEAR ======
    private static class ClearAnim {
        boolean[][] match;
        List<Piece> clearingPieces = new ArrayList<>();
        long startMs;
    }

    private void startClear(boolean[][] match) {
        playClearSounds(match);

        clearAnim = new ClearAnim();
        clearAnim.match = match;
        clearAnim.startMs = nowMs;
        clearAnim.clearingPieces.clear();

        for (int r = 0; r < BOARD_SIZE; r++) {
            for (int c = 0; c < BOARD_SIZE; c++) {
                if (match[r][c] && grid[r][c] != null) {
                    Piece p = grid[r][c];
                    p.alpha = 1f;
                    p.scale = 1f;
                    clearAnim.clearingPieces.add(p);
                }
            }
        }

        combo++;
        animState = AnimState.CLEARING;
    }

    private void tickClear() {
        float t = clamp01((nowMs - clearAnim.startMs) / (float) CLEAR_MS);
        float e = easeOut(t);

        for (Piece p : clearAnim.clearingPieces) {
            p.alpha = 1f - e;
            p.scale = 1f + 0.25f * e;
        }

        if (t >= 1f) {
            int cleared = 0;

            for (int r = 0; r < BOARD_SIZE; r++) {
                for (int c = 0; c < BOARD_SIZE; c++) {
                    if (clearAnim.match[r][c]) {
                        setPiece(r, c, null);
                        cleared++;
                    }
                }
            }

            int mult = Math.min(5, combo);
            scoreThisLevel += cleared * 10 * mult;

            startFall();
        }
    }

    // ====== FALLING (BLOCKED-AWARE) ======
    private static class FallAnim {
        Map<Piece, Float> startY = new HashMap<>();
        long startMs;
    }

    private void startFall() {
        fallAnim = new FallAnim();
        fallAnim.startMs = nowMs;

        for (int c = 0; c < BOARD_SIZE; c++) {

            List<Piece> kept = new ArrayList<>();
            for (int r = BOARD_SIZE - 1; r >= 0; r--) {
                if (blocked[r][c]) continue;
                if (grid[r][c] != null) kept.add(grid[r][c]);
            }

            List<Integer> targets = new ArrayList<>();
            for (int r = BOARD_SIZE - 1; r >= 0; r--) {
                if (!blocked[r][c]) targets.add(r);
            }

            for (int r = 0; r < BOARD_SIZE; r++) {
                if (!blocked[r][c]) grid[r][c] = null;
            }

            int idx = 0;
            for (; idx < kept.size() && idx < targets.size(); idx++) {
                int tr = targets.get(idx);
                Piece p = kept.get(idx);
                fallAnim.startY.put(p, p.py);
                setPiece(tr, c, p);
            }

            for (; idx < targets.size(); idx++) {
                int tr = targets.get(idx);

                int t = randomNormalType();
                if (random.nextFloat() < tntRefillChance) t = TNT;

                float spawnY = -TILE_SIZE * (idx + 1);
                Piece np = new Piece(t, c * TILE_SIZE, spawnY);
                np.tx = c * TILE_SIZE;
                np.ty = tr * TILE_SIZE;

                fallAnim.startY.put(np, np.py);
                setPiece(tr, c, np);
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
            }
        }

        if (t >= 1f) {
            for (int r = 0; r < BOARD_SIZE; r++) {
                for (int c = 0; c < BOARD_SIZE; c++) {
                    if (grid[r][c] != null) snapPiece(grid[r][c]);
                }
            }

            MatchResult mr = findMatches();
            if (mr != null) {
                for (Point p : mr.spawnTNT) {
                    int rr = p.y, cc = p.x;
                    if (isInsideBoard(rr, cc) && grid[rr][cc] != null) grid[rr][cc].type = TNT;
                }
                startClear(mr.match);
                return;
            }

            // LEVEL UP -> fade out -> voice intro -> new loop music -> fade in
            if (scoreThisLevel >= targetScore) {
                level++;
                audio.playSfx("/sfx/levelup.wav");

                // preload next background (optional)
                bgImage = loadImage("/backgrounds/bg_level" + level + ".png");
                if (bgImage == null) bgImage = loadImage("/backgrounds/bg_level1.png");

                startLevelTransition();
                return;
            }

            animState = AnimState.IDLE;
        }
    }

    // ====== EXPLOSION (TNT CLICK) ======
    private static class ExplodeAnim {
        List<Point> cells = new ArrayList<>();
        List<Piece> pieces = new ArrayList<>();
        long startMs;
    }

    private void startExplode(int r, int c) {
        audio.playSfx("/sfx/tnt.wav");

        explodeAnim = new ExplodeAnim();
        explodeAnim.startMs = nowMs;
        explodeAnim.cells.clear();
        explodeAnim.pieces.clear();

        for (int dr = -1; dr <= 1; dr++) {
            for (int dc = -1; dc <= 1; dc++) {
                int rr = r + dr, cc = c + dc;
                if (!isInsideBoard(rr, cc)) continue;
                if (blocked[rr][cc]) continue;
                if (grid[rr][cc] == null) continue;

                explodeAnim.cells.add(new Point(cc, rr));
                explodeAnim.pieces.add(grid[rr][cc]);
            }
        }

        combo = 0;
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
            int cleared = 0;
            for (Point cell : explodeAnim.cells) {
                int rr = cell.y, cc = cell.x;
                if (grid[rr][cc] != null) {
                    setPiece(rr, cc, null);
                    cleared++;
                }
            }

            moves--;
            if (moves <= 0) audio.playSfx("/sfx/gameover.wav");

            scoreThisLevel += cleared * 12;
            startFall();
        }
    }

    // ====== INPUT ======
    private void setupMouse() {
        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                // block user input during level transition
                if (levelTransition) return;

                if (animState != AnimState.IDLE) return;

                if (moves <= 0) {
                    level = 1;
                    applyLevelSettings();
                    audio.stopMusic();
                    audio.playMusicLoop("/music/level" + level + ".wav");
                    initBoardNoMatches();
                    repaint();
                    return;
                }

                int c = e.getX() / TILE_SIZE;
                int r = e.getY() / TILE_SIZE;

                if (!isInsideBoard(r, c)) return;
                if (blocked[r][c]) return;
                if (grid[r][c] == null) return;

                audio.playSfx("/sfx/click.wav");

                Piece clicked = grid[r][c];

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
        if (grid[a.y][a.x] == null || grid[b.y][b.x] == null) return;

        startSwap(a, b, false);
    }

    // ====== LEVEL TRANSITION IMPLEMENTATION ======
    private void startLevelTransition() {
        levelTransition = true;
        transitionStartMs = nowMs;
        bgFade = 0f;

        introPlayed = false;
        loopStarted = false;

        // stop any running music now (hard cut; you can replace with fade if you want)
        audio.stopMusic();

        // prevent visual selection from sticking
        selected = null;

        // ensure we are not in the middle of animations
        animState = AnimState.IDLE;
    }

    private void tickLevelTransition() {
        long dt = nowMs - transitionStartMs;

        // 1) fade out to black
        if (dt <= FADE_OUT_MS) {
            bgFade = clamp01(dt / (float) FADE_OUT_MS);
            return;
        }

        // 2) play intro voice ONCE after fade-out
        if (!introPlayed) {
            // IMPORTANT: file should be named like /music/level2_intro.wav
            audio.playSfx("/music/level" + level + "_intro.wav");
            introPlayed = true;
            return;
        }

        // 3) after intro duration, apply new settings, rebuild board, start looping music
        if (!loopStarted && dt >= (FADE_OUT_MS + INTRO_DELAY_MS)) {
            applyLevelSettings();
            initBoardNoMatches();
            audio.playMusicLoop("/music/level" + level + ".wav");
            loopStarted = true;
            return;
        }

        // 4) fade back in from black
        if (loopStarted) {
            long fadeInStart = FADE_OUT_MS + INTRO_DELAY_MS;
            long fadeInDt = dt - fadeInStart;

            bgFade = 1f - clamp01(fadeInDt / (float) FADE_IN_MS);

            if (fadeInDt >= FADE_IN_MS) {
                bgFade = 0f;
                levelTransition = false;
            }
        }
    }

    // ====== RENDER ======
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // background per level
        if (bgImage != null) {
            g2.drawImage(bgImage, 0, 0, getWidth(), getHeight(), null);
        } else {
            g2.setPaint(new GradientPaint(0, 0, new Color(15, 23, 42),
                    0, getHeight(), new Color(3, 7, 18)));
            g2.fillRect(0, 0, getWidth(), getHeight());
        }

        // slots
        // draw ONLY blocked cells (no background squares for normal cells)
        for (int r = 0; r < BOARD_SIZE; r++) {
            for (int c = 0; c < BOARD_SIZE; c++) {
                if (!blocked[r][c]) continue;

                int x = c * TILE_SIZE;
                int y = r * TILE_SIZE;

                if (blockedTileImage != null) {
                    // draw your custom blocked texture
                    g2.drawImage(blockedTileImage, x, y, TILE_SIZE, TILE_SIZE, null);
                } else {
                    // fallback: subtle dark tile (not pure black)
                    g2.setColor(new Color(10, 10, 14, 160));
                    g2.fillRoundRect(x + 4, y + 4, TILE_SIZE - 8, TILE_SIZE - 8, 12, 12);
                }
            }
        }


        // pieces
        for (int r = 0; r < BOARD_SIZE; r++) {
            for (int c = 0; c < BOARD_SIZE; c++) {
                Piece p = grid[r][c];
                if (p == null) continue;

                BufferedImage img = (p.type >= 0 && p.type < TYPE_COUNT) ? cookieImages[p.type] : null;

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

                    if (p.type == TNT) {
                        g2.setColor(new Color(255, 80, 80));
                        g2.setFont(getFont().deriveFont(Font.BOLD, 16f));
                        g2.drawString("TNT", drawX + 10, drawY + 28);
                    }
                }

                g2.setTransform(oldTx);
                g2.setComposite(oldComp);
            }
        }

        // selection highlight
        if (selected != null && animState == AnimState.IDLE && !levelTransition) {
            int x = selected.x * TILE_SIZE;
            int y = selected.y * TILE_SIZE;
            g2.setColor(new Color(248, 250, 252));
            Stroke old = g2.getStroke();
            g2.setStroke(new BasicStroke(3f));
            g2.drawRoundRect(x + 3, y + 3, TILE_SIZE - 6, TILE_SIZE - 6, 14, 14);
            g2.setStroke(old);
        }

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

        // fade overlay (used during level transition)
        if (bgFade > 0f) {
            Composite old = g2.getComposite();
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, clamp01(bgFade)));
            g2.setColor(Color.BLACK);
            g2.fillRect(0, 0, getWidth(), getHeight());
            g2.setComposite(old);
        }

        g2.dispose();
    }

    // ====== HELPERS ======
    private void lerpToTarget(Piece p, float t) {
        p.px = lerp(p.px, p.tx, t);
        p.py = lerp(p.py, p.ty, t);
    }

    private void snapPiece(Piece p) {
        p.px = p.tx;
        p.py = p.ty;
        p.alpha = 1f;
        p.scale = 1f;
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    private static float clamp01(float v) {
        return Math.max(0f, Math.min(1f, v));
    }

    private static float easeInOut(float t) {
        return t * t * (3f - 2f * t);
    }

    private static float easeOut(float t) {
        float u = 1f - t;
        return 1f - u * u;
    }

    // ====== SCOREBOARD PANEL INTEGRATION ======
    public void setScoreboard(ScoreboardPanel scoreboard) {
        this.scoreboard = scoreboard;
        pushHud();
    }

    private void pushHud() {
        if (scoreboard != null) {
            scoreboard.setStats(level, moves, scoreThisLevel, targetScore);
        }
    }
}
