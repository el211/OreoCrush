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
import java.util.prefs.Preferences;

public class OreoCrushGame extends JPanel {

    private static final int BOARD_SIZE = 8;
    private static final int TILE_SIZE = 60;
    private static final int CANVAS_SIZE = BOARD_SIZE * TILE_SIZE;
    private static final int MAX_BACKGROUND_LEVEL = 7;

    private static final int BASE_COLOR_COUNT = 4;

    private static final int SWAP_MS = 170;
    private static final int CLEAR_MS = 190;
    private static final int FALL_MS = 230;

    private static final int FADE_OUT_MS = 350;
    private static final int FADE_IN_MS = 350;
    private static final int INTRO_DELAY_MS = 1800;
    private static final int SUMMARY_MS = 1650;
    private static final int HINT_DELAY_MS = 5000;

    private static final String[] COLOR_NAMES = {"Classic", "Choco", "Vanilla", "Sprinkle"};

    private enum Special {
        NONE,
        ROCKET_H,
        ROCKET_V,
        BOMB,
        RAINBOW
    }

    private enum ObjectiveType {
        SCORE,
        CLEAR_COLOR,
        CLEAR_BLOCKERS,
        TRIGGER_SPECIALS
    }

    private enum AnimState {
        IDLE,
        SWAPPING,
        CLEARING,
        FALLING
    }

    private static class Piece {
        int color;
        Special special;
        float px;
        float py;
        float tx;
        float ty;
        float alpha = 1f;
        float scale = 1f;

        Piece(int color, Special special, float px, float py) {
            this.color = color;
            this.special = special;
            this.px = px;
            this.py = py;
            this.tx = px;
            this.ty = py;
        }
    }

    private static class Objective {
        final ObjectiveType type;
        final int target;
        final int colorType;
        int progress;

        Objective(ObjectiveType type, int target, int colorType) {
            this.type = type;
            this.target = target;
            this.colorType = colorType;
        }

        boolean complete() {
            return progress >= target;
        }

        String title() {
            switch (type) {
                case CLEAR_COLOR:
                    return "Clear " + target + " " + COLOR_NAMES[colorType].toLowerCase(Locale.ENGLISH) + " cookies.";
                case CLEAR_BLOCKERS:
                    return "Break every frosting layer on the board.";
                case TRIGGER_SPECIALS:
                    return "Create " + target + " special cookies (match 4+).";
                case SCORE:
                default:
                    return "Reach " + target + " score.";
            }
        }

        String progressText() {
            int capped = Math.min(progress, target);
            return capped + " / " + target;
        }
    }

    private static class LevelConfig {
        final int normalColors;
        final int moves;
        final int targetScore;
        final Objective objective;
        final int[][] blockers;

        LevelConfig(int normalColors, int moves, int targetScore, Objective objective, int[][] blockers) {
            this.normalColors = normalColors;
            this.moves = moves;
            this.targetScore = targetScore;
            this.objective = objective;
            this.blockers = blockers;
        }
    }

    private static class Run {
        final boolean horizontal;
        final int fixed;
        final int start;
        final int length;

        Run(boolean horizontal, int fixed, int start, int length) {
            this.horizontal = horizontal;
            this.fixed = fixed;
            this.start = start;
            this.length = length;
        }
    }

    private static class MatchResult {
        final boolean[][] match = new boolean[BOARD_SIZE][BOARD_SIZE];
        final LinkedHashMap<Point, Special> specialSpawns = new LinkedHashMap<Point, Special>();
        boolean any;
    }

    private static class Activation {
        final int row;
        final int col;
        final Special special;
        final int color;

        Activation(int row, int col, Special special, int color) {
            this.row = row;
            this.col = col;
            this.special = special;
            this.color = color;
        }
    }

    private static class ClearPayload {
        final boolean[][] clear = new boolean[BOARD_SIZE][BOARD_SIZE];
        final LinkedHashMap<Point, Special> specialSpawns = new LinkedHashMap<Point, Special>();
        int specialTriggers;
        String banner;
        boolean strongClear;
        String sfxHint;
    }

    private static class SwapAnim {
        Point a;
        Point b;
        Piece pa;
        Piece pb;
        long startMs;
        boolean reverting;
    }

    private static class ClearAnim {
        ClearPayload payload;
        final List<Piece> clearingPieces = new ArrayList<Piece>();
        long startMs;
    }

    private static class FallAnim {
        final Map<Piece, Float> startY = new HashMap<Piece, Float>();
        long startMs;
    }

    private static class MoveHint {
        final Point a;
        final Point b;

        MoveHint(Point a, Point b) {
            this.a = a;
            this.b = b;
        }
    }

    private final Random random = new Random();
    private final AudioManager audio = new AudioManager();
    private final Preferences prefs = Preferences.userNodeForPackage(OreoCrushGame.class);

    private final BufferedImage[] cookieImages = new BufferedImage[BASE_COLOR_COUNT];
    private BufferedImage bombStamp;
    private BufferedImage blockedTileImage;
    private BufferedImage bgImage;

    private final Piece[][] grid = new Piece[BOARD_SIZE][BOARD_SIZE];
    private final int[][] blockerLayers = new int[BOARD_SIZE][BOARD_SIZE];

    private ScoreboardPanel scoreboard;
    private Runnable uiStateListener;

    private Point selected;
    private MoveHint hintMove;

    private final Timer timer;
    private long nowMs;
    private long lastInputMs;

    private AnimState animState = AnimState.IDLE;
    private SwapAnim swapAnim;
    private ClearAnim clearAnim;
    private FallAnim fallAnim;

    private int level = 1;
    private int pendingLevel = -1;
    private int unlockedLevel = 1;
    private int normalColorCount = 3;
    private int moves;
    private int scoreThisLevel;
    private int targetScore;
    private int combo;
    private int bestCombo;
    private int currentStars;
    private Objective objective;

    private boolean levelTransition;
    private boolean introPlayed;
    private boolean loopStarted;
    private boolean transitionLevelLoaded;
    private float bgFade;
    private long transitionStartMs;
    private long delayedMusicStartMs = -1L;
    private int delayedMusicLevel = -1;

    private boolean levelSummary;
    private long summaryStartMs;
    private int summaryStars;
    private int summaryBonus;

    private String bannerText = "";
    private Color bannerColor = new Color(255, 244, 190);
    private long bannerUntilMs;

    private long shakeUntilMs;
    private int shakeMagnitude;
    private float flashAlpha;

    public OreoCrushGame() {
        setPreferredSize(new Dimension(CANVAS_SIZE, CANVAS_SIZE));
        setMinimumSize(new Dimension(CANVAS_SIZE, CANVAS_SIZE));
        setBackground(new Color(12, 18, 32));

        loadTextures();
        unlockedLevel = Math.max(1, prefs.getInt("unlockedLevel", 1));

        nowMs = System.currentTimeMillis();
        loadLevel(level);
        queueLevelVoiceAndMusic(level);
        setupMouse();

        timer = new Timer(1000 / 60, e -> {
            nowMs = System.currentTimeMillis();
            tick();
            pushHud();
            repaint();
        });
        timer.start();
    }

    public int getLevel() {
        return level;
    }

    public int getUnlockedLevel() {
        return unlockedLevel;
    }

    public void setUiStateListener(Runnable uiStateListener) {
        this.uiStateListener = uiStateListener;
        pushHud();
    }

    public void setScoreboard(ScoreboardPanel scoreboard) {
        this.scoreboard = scoreboard;
        pushHud();
    }

    public void restartCurrentLevel() {
        levelTransition = false;
        levelSummary = false;
        pendingLevel = -1;
        audio.stopMusic();
        loadLevel(level);
        queueLevelVoiceAndMusic(level);
        pushHud();
    }

    public void goToLevel(int requestedLevel) {
        int clamped = Math.max(1, Math.min(requestedLevel, unlockedLevel));
        if (clamped == level) {
            restartCurrentLevel();
            return;
        }

        levelTransition = false;
        levelSummary = false;
        pendingLevel = -1;
        audio.stopMusic();
        loadLevel(clamped);
        queueLevelVoiceAndMusic(level);
        showBanner("Loaded level " + level, new Color(166, 225, 255));
        pushHud();
    }

    private void loadTextures() {
        cookieImages[0] = loadImage("/assets/cookie_classic.png");
        cookieImages[1] = loadImage("/assets/cookie_choco.png");
        cookieImages[2] = loadImage("/assets/cookie_vanilla.png");
        cookieImages[3] = loadImage("/assets/cookie_sprinkle.png");
        bombStamp = loadImage("/assets/cookie_tnt.png");
        blockedTileImage = loadImage("/assets/blocked_tile.png");
    }

    private BufferedImage loadImage(String path) {
        try (InputStream in = getClass().getResourceAsStream(path)) {
            if (in == null) return null;
            return ImageIO.read(in);
        } catch (Exception e) {
            System.err.println("Image load failed: " + path);
            e.printStackTrace();
            return null;
        }
    }

    private void loadLevel(int newLevel) {
        level = Math.max(1, newLevel);
        LevelConfig config = buildLevelConfig(level);

        normalColorCount = config.normalColors;
        moves = config.moves;
        targetScore = config.targetScore;
        objective = config.objective;
        scoreThisLevel = 0;
        combo = 0;
        bestCombo = 0;
        currentStars = 0;
        selected = null;
        hintMove = null;
        animState = AnimState.IDLE;
        flashAlpha = 0f;
        bgImage = loadBackgroundFor(level);
        copyBlockers(config.blockers);
        initBoardNoMatches();
        lastInputMs = nowMs;
        showBanner(objective.title(), new Color(180, 225, 255));
    }

    private BufferedImage loadBackgroundFor(int levelNumber) {
        for (int bgLevel = Math.min(levelNumber, MAX_BACKGROUND_LEVEL); bgLevel >= 1; bgLevel--) {
            BufferedImage image = loadImage("/backgrounds/bg_level" + bgLevel + ".png");
            if (image != null) {
                return image;
            }
        }

        return null;
    }

    private LevelConfig buildLevelConfig(int levelNumber) {
        int[][] blockers = generateStoneLayout(levelNumber);

        int colors = levelNumber <= 1 ? 3 : 4;
        int target = 1000 + (levelNumber * 480);
        Objective objectiveForLevel;
        int movesForLevel = 17 + Math.min(6, levelNumber / 3);

        if (levelNumber == 1) {
            objectiveForLevel = new Objective(ObjectiveType.SCORE, 750, -1);
            movesForLevel = 20;
            target = 1000;
        } else {
            int cycle = Math.floorMod(levelNumber - 2, 4);
            switch (cycle) {
                case 0:
                    objectiveForLevel = new Objective(ObjectiveType.SCORE, 1200 + levelNumber * 480, -1);
                    movesForLevel += 1;
                    break;
                case 1:
                    objectiveForLevel = new Objective(ObjectiveType.CLEAR_COLOR, 15 + levelNumber * 2, Math.floorMod(levelNumber, BASE_COLOR_COUNT));
                    break;
                case 2:
                    objectiveForLevel = new Objective(ObjectiveType.TRIGGER_SPECIALS, 3 + (levelNumber / 4), -1);
                    movesForLevel += 2;
                    target += 300;
                    break;
                case 3:
                default:
                    objectiveForLevel = new Objective(ObjectiveType.SCORE, 1300 + levelNumber * 520, -1);
                    target += 350;
                    break;
            }
        }

        return new LevelConfig(colors, movesForLevel, target, objectiveForLevel, blockers);
    }

    private int[][] generateStoneLayout(int levelNumber) {
        int[][] layout = new int[BOARD_SIZE][BOARD_SIZE];
        if (levelNumber <= 1) {
            return layout;
        }

        int targetCount = Math.min(12, 2 + levelNumber);
        if (levelNumber == 2) targetCount = 4;
        if (levelNumber == 3) targetCount = 6;
        if (levelNumber == 4) targetCount = 7;

        Random layoutRandom = new Random(97L * levelNumber + 13L);
        int placed = 0;
        int tries = 0;

        while (placed < targetCount && tries++ < 800) {
            int row = 1 + layoutRandom.nextInt(BOARD_SIZE - 2);
            int col = layoutRandom.nextInt(BOARD_SIZE);

            if (!canPlaceStone(layout, row, col)) {
                continue;
            }

            layout[row][col] = 1;
            placed++;
        }

        return layout;
    }

    private boolean canPlaceStone(int[][] layout, int row, int col) {
        if (layout[row][col] > 0) return false;

        int neighbors = 0;
        for (int dr = -1; dr <= 1; dr++) {
            for (int dc = -1; dc <= 1; dc++) {
                if (dr == 0 && dc == 0) continue;
                int rr = row + dr;
                int cc = col + dc;
                if (rr < 0 || rr >= BOARD_SIZE || cc < 0 || cc >= BOARD_SIZE) continue;
                if (layout[rr][cc] > 0) neighbors++;
            }
        }

        if (neighbors > 1) return false;

        int rowRun = 1;
        for (int c = col - 1; c >= 0 && layout[row][c] > 0; c--) rowRun++;
        for (int c = col + 1; c < BOARD_SIZE && layout[row][c] > 0; c++) rowRun++;
        if (rowRun >= 3) return false;

        int colRun = 1;
        for (int r = row - 1; r >= 0 && layout[r][col] > 0; r--) colRun++;
        for (int r = row + 1; r < BOARD_SIZE && layout[r][col] > 0; r++) colRun++;
        if (colRun >= 3) return false;

        return true;
    }

    private int[][] decodePattern(String[] rows) {
        int[][] result = new int[BOARD_SIZE][BOARD_SIZE];
        for (int r = 0; r < BOARD_SIZE && r < rows.length; r++) {
            for (int c = 0; c < BOARD_SIZE && c < rows[r].length(); c++) {
                char ch = rows[r].charAt(c);
                if (ch >= '1' && ch <= '9') {
                    result[r][c] = ch - '0';
                }
            }
        }
        return result;
    }

    private void copyBlockers(int[][] source) {
        for (int r = 0; r < BOARD_SIZE; r++) {
            for (int c = 0; c < BOARD_SIZE; c++) {
                blockerLayers[r][c] = source[r][c];
            }
        }
    }

    private int countBlockerCells(int[][] layers) {
        int count = 0;
        for (int r = 0; r < BOARD_SIZE; r++) {
            for (int c = 0; c < BOARD_SIZE; c++) {
                if (layers[r][c] > 0) count++;
            }
        }
        return count;
    }

    private void initBoardNoMatches() {
        for (int r = 0; r < BOARD_SIZE; r++) {
            for (int c = 0; c < BOARD_SIZE; c++) {
                if (isBlockedCell(r, c)) {
                    setPiece(r, c, null);
                    continue;
                }

                Piece piece;
                do {
                    piece = new Piece(random.nextInt(normalColorCount), Special.NONE, c * TILE_SIZE, r * TILE_SIZE);
                    setPiece(r, c, piece);
                } while (createsMatchAt(r, c));
            }
        }

        if (!hasPossibleMove()) {
            reshuffleBoard(false);
        }
    }

    private void setupMouse() {
        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (levelTransition || levelSummary) return;
                if (animState != AnimState.IDLE) return;

                lastInputMs = nowMs;
                hintMove = null;

                if (moves <= 0) {
                    restartCurrentLevel();
                    return;
                }

                float scale = getBoardScale();
                int canvasX = (int) ((e.getX() - getBoardOffsetX()) / scale);
                int canvasY = (int) ((e.getY() - getBoardOffsetY()) / scale);
                int c = canvasX / TILE_SIZE;
                int r = canvasY / TILE_SIZE;

                if (!isInsideBoard(r, c)) return;

                Piece clicked = grid[r][c];
                if (clicked == null) return;

                audio.playSfx("/sfx/click.wav");

                if (clicked.special != Special.NONE) {
                    selected = null;
                    combo = 0;
                    consumeMove();
                    ClearPayload tapPayload = buildActivationPayload(
                            Collections.singletonList(new Activation(r, c, clicked.special, clicked.color)),
                            actionTextFor(clicked.special));
                    tapPayload.sfxHint = sfxForSpecial(clicked.special);
                    startClear(tapPayload);
                    return;
                }

                if (selected == null) {
                    selected = new Point(c, r);
                    return;
                }

                Point second = new Point(c, r);
                if (selected.equals(second)) {
                    selected = null;
                    return;
                }

                if (!adjacent(selected, second)) {
                    selected = second;
                    showBanner("Pick a neighboring cookie.", new Color(255, 225, 160));
                    return;
                }

                handleSwap(selected, second);
                selected = null;
            }
        });
    }

    private void handleSwap(Point a, Point b) {
        if (!adjacent(a, b)) return;
        startSwap(a, b, false);
    }

    private void startSwap(Point a, Point b, boolean reverting) {
        int r1 = a.y;
        int c1 = a.x;
        int r2 = b.y;
        int c2 = b.x;

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

    private void startClear(ClearPayload payload) {
        clearAnim = new ClearAnim();
        clearAnim.payload = payload;
        clearAnim.startMs = nowMs;
        clearAnim.clearingPieces.clear();

        for (int r = 0; r < BOARD_SIZE; r++) {
            for (int c = 0; c < BOARD_SIZE; c++) {
                if (!payload.clear[r][c]) continue;
                Piece piece = grid[r][c];
                if (piece == null) continue;
                piece.alpha = 1f;
                piece.scale = 1f;
                clearAnim.clearingPieces.add(piece);
            }
        }

        combo++;
        bestCombo = Math.max(bestCombo, combo);
        if (combo == 4) {
            moves++;
            showBanner("Combo reward: +1 move", new Color(166, 255, 214));
        } else if (payload.banner != null) {
            showBanner(payload.banner, new Color(255, 230, 166));
        }

        if (payload.sfxHint != null) {
            audio.playSfx(payload.sfxHint);
        } else if (payload.strongClear) {
            audio.playSfx("/sfx/tnt.wav");
        } else if (combo > 1) {
            audio.playSfx("/music/sfxsound/combocombo.mp3");
        } else {
            audio.playSfx("/music/sfxsound/nomnom.mp3");
        }
        if (combo == 4) {
            audio.playSfx("/music/sfxsound/yummypower.mp3");
        }

        if (payload.strongClear) {
            flashAlpha = 0.26f;
            triggerShake(10, 140);
        } else {
            flashAlpha = 0.14f;
        }

        animState = AnimState.CLEARING;
    }

    private void startFall() {
        fallAnim = new FallAnim();
        fallAnim.startMs = nowMs;

        for (int c = 0; c < BOARD_SIZE; c++) {
            List<Piece> kept = new ArrayList<Piece>();
            for (int r = BOARD_SIZE - 1; r >= 0; r--) {
                if (isBlockedCell(r, c)) continue;
                if (grid[r][c] != null) {
                    kept.add(grid[r][c]);
                }
            }

            for (int r = 0; r < BOARD_SIZE; r++) {
                if (!isBlockedCell(r, c)) {
                    grid[r][c] = null;
                }
            }

            int index = 0;
            for (int r = BOARD_SIZE - 1; r >= 0 && index < kept.size(); r--) {
                if (isBlockedCell(r, c)) continue;
                Piece piece = kept.get(index++);
                fallAnim.startY.put(piece, piece.py);
                setPiece(r, c, piece);
            }

            for (int r = BOARD_SIZE - 1; r >= 0; r--) {
                if (isBlockedCell(r, c) || grid[r][c] != null) continue;
                Piece spawned = new Piece(random.nextInt(normalColorCount), Special.NONE, c * TILE_SIZE,
                        -TILE_SIZE * (BOARD_SIZE - r));
                fallAnim.startY.put(spawned, spawned.py);
                setPiece(r, c, spawned);
            }
        }

        animState = AnimState.FALLING;
    }

    private void tick() {
        flashAlpha = Math.max(0f, flashAlpha - 0.012f);

        if (levelSummary) {
            tickLevelSummary();
            return;
        }

        if (levelTransition) {
            tickLevelTransition();
            return;
        }

        tickDelayedMusicStart();

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
            case IDLE:
            default:
                updateHint();
                break;
        }
    }

    private void tickSwap() {
        float t = clamp01((nowMs - swapAnim.startMs) / (float) SWAP_MS);
        float e = easeInOut(t);

        lerpToTarget(swapAnim.pa, e);
        lerpToTarget(swapAnim.pb, e);

        if (t < 1f) return;

        int r1 = swapAnim.a.y;
        int c1 = swapAnim.a.x;
        int r2 = swapAnim.b.y;
        int c2 = swapAnim.b.x;

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

        ClearPayload specialSwap = buildSpecialSwapPayload(swapAnim.a, swapAnim.b);
        if (specialSwap != null) {
            combo = 0;
            consumeMove();
            startClear(specialSwap);
            return;
        }

        MatchResult match = findMatches(swapAnim.a, swapAnim.b);
        if (!match.any) {
            triggerShake(6, 110);
            showBanner("No match. Try a setup move.", new Color(255, 204, 166));
            audio.playSfx("/music/sfxsound/oopsie.mp3");
            startSwap(swapAnim.a, swapAnim.b, true);
            return;
        }

        combo = 0;
        consumeMove();
        startClear(payloadFromMatch(match));
    }

    private void tickClear() {
        float t = clamp01((nowMs - clearAnim.startMs) / (float) CLEAR_MS);
        float e = easeOut(t);

        for (Piece piece : clearAnim.clearingPieces) {
            piece.alpha = 1f - e;
            piece.scale = 1f + 0.24f * e;
        }

        if (t < 1f) return;

        int cleared = 0;
        int[] clearedColors = new int[BASE_COLOR_COUNT];

        for (int r = 0; r < BOARD_SIZE; r++) {
            for (int c = 0; c < BOARD_SIZE; c++) {
                if (!clearAnim.payload.clear[r][c]) continue;
                Piece piece = grid[r][c];
                if (piece == null) continue;

                if (piece.color >= 0 && piece.color < BASE_COLOR_COUNT) {
                    clearedColors[piece.color]++;
                }

                setPiece(r, c, null);
                cleared++;
            }
        }

        for (Map.Entry<Point, Special> entry : clearAnim.payload.specialSpawns.entrySet()) {
            Point point = entry.getKey();
            Piece piece = grid[point.y][point.x];
            if (piece == null) continue;

            piece.special = entry.getValue();
            if (piece.special == Special.RAINBOW) {
                piece.color = -1;
            } else if (piece.color < 0) {
                piece.color = random.nextInt(normalColorCount);
            }
        }

        if (!clearAnim.payload.specialSpawns.isEmpty()) {
            audio.playSfx("/music/sfxsound/supercookie.mp3");
            showBanner(specialCreatedText(clearAnim.payload.specialSpawns.values()), new Color(255, 230, 166));
        }

        int blockerCellsCleared = damageBlockers(clearAnim.payload.clear);
        int specialsSpawned = clearAnim.payload.specialSpawns.size();
        scoreThisLevel += Math.round((cleared * 24 + blockerCellsCleared * 75 + clearAnim.payload.specialTriggers * 80)
                * (1f + Math.max(0, combo - 1) * 0.35f));

        updateObjective(clearedColors, blockerCellsCleared, clearAnim.payload.specialTriggers + specialsSpawned);

        if (objective.complete() && !levelSummary) {
            beginLevelSummary();
            return;
        }

        startFall();
    }

    private void tickFall() {
        float t = clamp01((nowMs - fallAnim.startMs) / (float) FALL_MS);
        float e = easeInOut(t);

        for (int r = 0; r < BOARD_SIZE; r++) {
            for (int c = 0; c < BOARD_SIZE; c++) {
                Piece piece = grid[r][c];
                if (piece == null) continue;

                Float startY = fallAnim.startY.get(piece);
                if (startY == null) continue;

                piece.px = piece.tx;
                piece.py = lerp(startY, piece.ty, e);
                piece.alpha = 1f;
                piece.scale = 1f;
            }
        }

        if (t < 1f) return;

        for (int r = 0; r < BOARD_SIZE; r++) {
            for (int c = 0; c < BOARD_SIZE; c++) {
                if (grid[r][c] != null) {
                    snapPiece(grid[r][c]);
                }
            }
        }

        MatchResult match = findMatches(null, null);
        if (match.any) {
            startClear(payloadFromMatch(match));
            return;
        }

        if (!hasPossibleMove()) {
            reshuffleBoard(true);
        }

        if (objective.complete() && !levelSummary) {
            beginLevelSummary();
            return;
        }

        animState = AnimState.IDLE;
    }

    private void tickLevelSummary() {
        if (nowMs - summaryStartMs < SUMMARY_MS) return;

        levelSummary = false;
        startLevelTransition(level + 1);
    }

    private void startLevelTransition(int nextLevel) {
        pendingLevel = nextLevel;
        levelTransition = true;
        transitionStartMs = nowMs;
        bgFade = 0f;
        introPlayed = false;
        loopStarted = false;
        transitionLevelLoaded = false;
        selected = null;
        hintMove = null;
        animState = AnimState.IDLE;
        delayedMusicStartMs = -1L;
        delayedMusicLevel = -1;
        audio.stopMusic();
    }

    private void tickLevelTransition() {
        long dt = nowMs - transitionStartMs;

        if (dt <= FADE_OUT_MS) {
            bgFade = clamp01(dt / (float) FADE_OUT_MS);
            return;
        }

        if (!introPlayed) {
            loadLevel(pendingLevel);
            transitionLevelLoaded = true;
            audio.playSfx(introMusicPathFor(pendingLevel));
            audio.playMusicLoop(loopMusicPathFor(level));
            introPlayed = true;
            loopStarted = true;
            return;
        }

        if (transitionLevelLoaded) {
            long fadeInDt = dt - FADE_OUT_MS;
            bgFade = 1f - clamp01(fadeInDt / (float) FADE_IN_MS);
            if (fadeInDt >= FADE_IN_MS) {
                bgFade = 0f;
                levelTransition = false;
                pendingLevel = -1;
                transitionLevelLoaded = false;
            }
        }
    }

    private void queueLevelVoiceAndMusic(int levelNumber) {
        delayedMusicStartMs = -1L;
        delayedMusicLevel = -1;
        audio.playSfx(introMusicPathFor(levelNumber));
        audio.playMusicLoop(loopMusicPathFor(levelNumber));
    }

    private void tickDelayedMusicStart() {
        if (delayedMusicStartMs < 0L || delayedMusicLevel < 0) return;
        if (nowMs < delayedMusicStartMs) return;

        audio.playMusicLoop(loopMusicPathFor(delayedMusicLevel));
        delayedMusicStartMs = -1L;
        delayedMusicLevel = -1;
    }

    private void updateHint() {
        if (nowMs - lastInputMs < HINT_DELAY_MS) {
            hintMove = null;
            return;
        }

        if (hintMove == null) {
            hintMove = findHint();
            if (hintMove != null) {
                showBanner("Hint: the board still has a strong move.", new Color(189, 223, 255));
            }
        }
    }

    private MatchResult findMatches(Point preferredA, Point preferredB) {
        MatchResult result = new MatchResult();
        boolean allowSpecialSpawns = preferredA != null || preferredB != null;
        int[][] horizontalLengths = new int[BOARD_SIZE][BOARD_SIZE];
        int[][] verticalLengths = new int[BOARD_SIZE][BOARD_SIZE];
        List<Run> horizontalRuns = new ArrayList<Run>();
        List<Run> verticalRuns = new ArrayList<Run>();

        for (int r = 0; r < BOARD_SIZE; r++) {
            int c = 0;
            while (c < BOARD_SIZE) {
                Piece piece = grid[r][c];
                if (piece == null || piece.color < 0) {
                    c++;
                    continue;
                }

                int start = c;
                while (c < BOARD_SIZE && grid[r][c] != null && grid[r][c].color == piece.color) {
                    c++;
                }

                int length = c - start;
                if (length >= 3) {
                    result.any = true;
                    horizontalRuns.add(new Run(true, r, start, length));
                    for (int x = start; x < c; x++) {
                        result.match[r][x] = true;
                        horizontalLengths[r][x] = length;
                    }
                }
            }
        }

        for (int c = 0; c < BOARD_SIZE; c++) {
            int r = 0;
            while (r < BOARD_SIZE) {
                Piece piece = grid[r][c];
                if (piece == null || piece.color < 0) {
                    r++;
                    continue;
                }

                int start = r;
                while (r < BOARD_SIZE && grid[r][c] != null && grid[r][c].color == piece.color) {
                    r++;
                }

                int length = r - start;
                if (length >= 3) {
                    result.any = true;
                    verticalRuns.add(new Run(false, c, start, length));
                    for (int y = start; y < r; y++) {
                        result.match[y][c] = true;
                        verticalLengths[y][c] = length;
                    }
                }
            }
        }

        if (!result.any) {
            return result;
        }

        if (allowSpecialSpawns) {
            maybeAddPreferredSpawn(result, preferredA, horizontalLengths, verticalLengths);
            maybeAddPreferredSpawn(result, preferredB, horizontalLengths, verticalLengths);

            for (int r = 0; r < BOARD_SIZE; r++) {
                for (int c = 0; c < BOARD_SIZE; c++) {
                    if (!result.match[r][c]) continue;
                    addSpecialSpawn(result, c, r, horizontalLengths[r][c], verticalLengths[r][c]);
                }
            }

            for (Run run : horizontalRuns) {
                if (run.length < 4) continue;
                int spawnCol = run.length >= 5 ? run.start + 2 : run.start + 1;
                addSpecialSpawn(result, spawnCol, run.fixed, run.length, 0);
            }

            for (Run run : verticalRuns) {
                if (run.length < 4) continue;
                int spawnRow = run.length >= 5 ? run.start + 2 : run.start + 1;
                addSpecialSpawn(result, run.fixed, spawnRow, 0, run.length);
            }
        }

        for (Point point : result.specialSpawns.keySet()) {
            result.match[point.y][point.x] = false;
        }

        return result;
    }

    private void maybeAddPreferredSpawn(MatchResult result, Point point, int[][] horizontalLengths, int[][] verticalLengths) {
        if (point == null || !isInsideBoard(point.y, point.x)) return;
        if (!result.match[point.y][point.x]) return;

        addSpecialSpawn(result, point.x, point.y, horizontalLengths[point.y][point.x], verticalLengths[point.y][point.x]);
    }

    private void addSpecialSpawn(MatchResult result, int c, int r, int horizontalLength, int verticalLength) {
        Special special = specialFor(horizontalLength, verticalLength);
        if (special == Special.NONE) return;

        Point point = new Point(c, r);
        Special existing = result.specialSpawns.get(point);
        if (existing == null || specialPriority(special) > specialPriority(existing)) {
            result.specialSpawns.put(point, special);
        }
    }

    private Special specialFor(int horizontalLength, int verticalLength) {
        if (horizontalLength >= 5 || verticalLength >= 5) return Special.RAINBOW;
        if (horizontalLength >= 3 && verticalLength >= 3) return Special.BOMB;
        if (horizontalLength == 4) return Special.ROCKET_H;
        if (verticalLength == 4) return Special.ROCKET_V;
        return Special.NONE;
    }

    private int specialPriority(Special special) {
        switch (special) {
            case RAINBOW:
                return 4;
            case BOMB:
                return 3;
            case ROCKET_H:
            case ROCKET_V:
                return 2;
            case NONE:
            default:
                return 0;
        }
    }

    private ClearPayload payloadFromMatch(MatchResult match) {
        ClearPayload payload = new ClearPayload();
        for (int r = 0; r < BOARD_SIZE; r++) {
            System.arraycopy(match.match[r], 0, payload.clear[r], 0, BOARD_SIZE);
        }
        payload.specialSpawns.putAll(match.specialSpawns);
        payload.strongClear = match.specialSpawns.size() > 0;

        if (match.specialSpawns.containsValue(Special.RAINBOW)) {
            payload.banner = "Rainbow cookie created";
        } else if (match.specialSpawns.containsValue(Special.BOMB)) {
            payload.banner = "Bomb cookie created";
        } else if (match.specialSpawns.containsValue(Special.ROCKET_H) || match.specialSpawns.containsValue(Special.ROCKET_V)) {
            payload.banner = "Rocket cookie created";
        }

        return payload;
    }

    private ClearPayload buildSpecialSwapPayload(Point a, Point b) {
        Piece first = grid[a.y][a.x];
        Piece second = grid[b.y][b.x];
        if (first == null || second == null) return null;
        if (first.special == Special.NONE && second.special == Special.NONE) return null;

        if (first.special == Special.RAINBOW && second.special == Special.RAINBOW) {
            ClearPayload payload = new ClearPayload();
            for (int r = 0; r < BOARD_SIZE; r++) {
                for (int c = 0; c < BOARD_SIZE; c++) {
                    payload.clear[r][c] = true;
                }
            }
            payload.specialTriggers = 2;
            payload.strongClear = true;
            payload.banner = "Double rainbow board wipe";
            payload.sfxHint = "/music/sfxsound/raimbowcoookie2.mp3";
            return payload;
        }

        if (first.special == Special.RAINBOW || second.special == Special.RAINBOW) {
            Piece other = first.special == Special.RAINBOW ? second : first;
            int rainbowRow = first.special == Special.RAINBOW ? a.y : b.y;
            int rainbowCol = first.special == Special.RAINBOW ? a.x : b.x;
            ClearPayload payload = buildActivationPayload(Collections.singletonList(
                    new Activation(rainbowRow, rainbowCol, Special.RAINBOW, Math.max(0, other.color))),
                    "Rainbow color clear");
            payload.clear[a.y][a.x] = true;
            payload.clear[b.y][b.x] = true;
            payload.strongClear = true;
            payload.sfxHint = "/music/sfxsound/mixymixy.mp3";
            return payload;
        }

        if (first.special != Special.NONE && second.special != Special.NONE) {
            ClearPayload payload = buildActivationPayload(Arrays.asList(
                    new Activation(a.y, a.x, first.special, first.color),
                    new Activation(b.y, b.x, second.special, second.color)),
                    "Special combo");

            if (first.special == Special.BOMB && second.special == Special.BOMB) {
                markArea(payload, null, a.y, a.x, 2);
                markArea(payload, null, b.y, b.x, 2);
            } else if ((isRocket(first.special) && second.special == Special.BOMB)
                    || (isRocket(second.special) && first.special == Special.BOMB)) {
                markPlus(payload, null, a.y, a.x, 1);
                markPlus(payload, null, b.y, b.x, 1);
            }

            payload.strongClear = true;
            payload.sfxHint = "/music/sfxsound/mixymixy.mp3";
            return payload;
        }

        Piece specialPiece = first.special != Special.NONE ? first : second;
        Point specialPoint = first.special != Special.NONE ? a : b;
        ClearPayload payload = buildActivationPayload(Collections.singletonList(
                new Activation(specialPoint.y, specialPoint.x, specialPiece.special, specialPiece.color)),
                "Special swap");
        payload.strongClear = true;
        payload.sfxHint = sfxForSpecial(specialPiece.special);
        return payload;
    }

    private ClearPayload buildActivationPayload(Collection<Activation> seeds, String banner) {
        ClearPayload payload = new ClearPayload();
        Queue<Activation> queue = new ArrayDeque<Activation>(seeds);
        Set<String> activated = new HashSet<String>();
        payload.banner = banner;

        while (!queue.isEmpty()) {
            Activation activation = queue.poll();
            String key = activation.row + ":" + activation.col + ":" + activation.special + ":" + activation.color;
            if (!activated.add(key)) continue;

            payload.specialTriggers++;

            switch (activation.special) {
                case ROCKET_H:
                    for (int c = 0; c < BOARD_SIZE; c++) {
                        markTriggeredCell(payload, queue, activation.row, c);
                    }
                    break;
                case ROCKET_V:
                    for (int r = 0; r < BOARD_SIZE; r++) {
                        markTriggeredCell(payload, queue, r, activation.col);
                    }
                    break;
                case BOMB:
                    markArea(payload, queue, activation.row, activation.col, 1);
                    break;
                case RAINBOW:
                    int color = activation.color >= 0 ? activation.color : mostCommonColorOnBoard();
                    for (int r = 0; r < BOARD_SIZE; r++) {
                        for (int c = 0; c < BOARD_SIZE; c++) {
                            Piece piece = grid[r][c];
                            if (piece == null) continue;
                            if (piece.color == color) {
                                markTriggeredCell(payload, queue, r, c);
                            } else if (piece.special == Special.RAINBOW) {
                                // consume other rainbows so they can't stack up
                                payload.clear[r][c] = true;
                            }
                        }
                    }
                    break;
                case NONE:
                default:
                    markTriggeredCell(payload, queue, activation.row, activation.col);
                    break;
            }
        }

        return payload;
    }

    private void markArea(ClearPayload payload, Queue<Activation> queue, int centerRow, int centerCol, int radius) {
        for (int dr = -radius; dr <= radius; dr++) {
            for (int dc = -radius; dc <= radius; dc++) {
                markTriggeredCell(payload, queue, centerRow + dr, centerCol + dc);
            }
        }
    }

    private void markPlus(ClearPayload payload, Queue<Activation> queue, int centerRow, int centerCol, int radius) {
        for (int offset = -radius; offset <= radius; offset++) {
            markTriggeredCell(payload, queue, centerRow + offset, centerCol);
            markTriggeredCell(payload, queue, centerRow, centerCol + offset);
        }
    }

    private void markTriggeredCell(ClearPayload payload, Queue<Activation> queue, int r, int c) {
        if (!isInsideBoard(r, c)) return;
        Piece piece = grid[r][c];
        if (piece == null) return;

        payload.clear[r][c] = true;
        if (queue == null || piece.special == Special.NONE) return;

        if (piece.special == Special.RAINBOW) {
            queue.add(new Activation(r, c, Special.RAINBOW, mostCommonColorOnBoard()));
        } else {
            queue.add(new Activation(r, c, piece.special, piece.color));
        }
    }

    private int mostCommonColorOnBoard() {
        int[] counts = new int[BASE_COLOR_COUNT];
        for (int r = 0; r < BOARD_SIZE; r++) {
            for (int c = 0; c < BOARD_SIZE; c++) {
                Piece piece = grid[r][c];
                if (piece != null && piece.color >= 0 && piece.color < BASE_COLOR_COUNT) {
                    counts[piece.color]++;
                }
            }
        }

        int bestIndex = 0;
        for (int i = 1; i < counts.length; i++) {
            if (counts[i] > counts[bestIndex]) {
                bestIndex = i;
            }
        }
        return bestIndex;
    }

    private int damageBlockers(boolean[][] clearMask) {
        return 0;
    }

    private boolean adjacentToClear(boolean[][] clearMask, int row, int col) {
        for (int dr = -1; dr <= 1; dr++) {
            for (int dc = -1; dc <= 1; dc++) {
                int rr = row + dr;
                int cc = col + dc;
                if (isInsideBoard(rr, cc) && clearMask[rr][cc]) return true;
            }
        }
        return false;
    }

    private void updateObjective(int[] clearedColors, int blockerCellsCleared, int specialTriggers) {
        switch (objective.type) {
            case CLEAR_COLOR:
                objective.progress += clearedColors[objective.colorType];
                break;
            case CLEAR_BLOCKERS:
                objective.progress += blockerCellsCleared;
                break;
            case TRIGGER_SPECIALS:
                objective.progress += specialTriggers;
                break;
            case SCORE:
            default:
                objective.progress = scoreThisLevel;
                break;
        }
    }

    private void beginLevelSummary() {
        if (levelSummary) return;

        summaryBonus = moves * 75 + bestCombo * 40;
        scoreThisLevel += summaryBonus;
        updateObjective(new int[BASE_COLOR_COUNT], 0, 0);
        summaryStars = calculateStars();
        currentStars = summaryStars;

        saveProgress();
        unlockedLevel = Math.max(unlockedLevel, level + 1);
        prefs.putInt("unlockedLevel", unlockedLevel);

        levelSummary = true;
        summaryStartMs = nowMs;
        showBanner("Level complete", new Color(188, 255, 208));
        audio.playSfx("/music/sfxsound/yaay.mp3");
    }

    private int calculateStars() {
        int stars = 1;
        if (scoreThisLevel >= targetScore) stars = 2;
        if (scoreThisLevel >= Math.round(targetScore * 1.45f)) stars = 3;
        return stars;
    }

    private void saveProgress() {
        int bestScore = Math.max(getBestScore(level), scoreThisLevel);
        int bestStars = Math.max(getBestStars(level), summaryStars);
        prefs.putInt(scoreKey(level), bestScore);
        prefs.putInt(starKey(level), bestStars);
        prefs.putInt("unlockedLevel", Math.max(unlockedLevel, level + 1));
    }

    private int getBestScore(int levelNumber) {
        return prefs.getInt(scoreKey(levelNumber), 0);
    }

    private int getBestStars(int levelNumber) {
        return prefs.getInt(starKey(levelNumber), 0);
    }

    private int getTotalStars() {
        int total = 0;
        for (int i = 1; i <= Math.max(unlockedLevel, level); i++) {
            total += getBestStars(i);
        }
        return total;
    }

    private String scoreKey(int levelNumber) {
        return "level." + levelNumber + ".score";
    }

    private String starKey(int levelNumber) {
        return "level." + levelNumber + ".stars";
    }

    private void consumeMove() {
        moves = Math.max(0, moves - 1);
        lastInputMs = nowMs;
        hintMove = null;
        if (moves == 0 && !objective.complete()) {
            triggerShake(10, 180);
            showBanner("Out of moves. Click to retry.", new Color(255, 190, 166));
        }
    }

    private boolean hasPossibleMove() {
        return findHint() != null;
    }

    private MoveHint findHint() {
        for (int r = 0; r < BOARD_SIZE; r++) {
            for (int c = 0; c < BOARD_SIZE; c++) {
                if (c + 1 < BOARD_SIZE && isUsefulSwap(r, c, r, c + 1)) {
                    return new MoveHint(new Point(c, r), new Point(c + 1, r));
                }
                if (r + 1 < BOARD_SIZE && isUsefulSwap(r, c, r + 1, c)) {
                    return new MoveHint(new Point(c, r), new Point(c, r + 1));
                }
            }
        }
        return null;
    }

    private boolean isUsefulSwap(int r1, int c1, int r2, int c2) {
        Piece first = grid[r1][c1];
        Piece second = grid[r2][c2];
        if (first == null || second == null) return false;
        if (first.special != Special.NONE || second.special != Special.NONE) return true;

        swapGridPieces(r1, c1, r2, c2);
        boolean valid = formsMatchAt(r1, c1) || formsMatchAt(r2, c2);
        swapGridPieces(r1, c1, r2, c2);
        return valid;
    }

    private void reshuffleBoard(boolean announce) {
        List<Piece> pieces = new ArrayList<Piece>();
        for (int r = 0; r < BOARD_SIZE; r++) {
            for (int c = 0; c < BOARD_SIZE; c++) {
                if (grid[r][c] != null) {
                    pieces.add(grid[r][c]);
                }
            }
        }

        boolean solved = false;
        for (int attempt = 0; attempt < 250 && !solved; attempt++) {
            Collections.shuffle(pieces, random);
            int index = 0;
            for (int r = 0; r < BOARD_SIZE; r++) {
                for (int c = 0; c < BOARD_SIZE; c++) {
                    if (isBlockedCell(r, c)) {
                        setPiece(r, c, null);
                        continue;
                    }
                    Piece piece = pieces.get(index++);
                    setPiece(r, c, piece);
                    snapPiece(piece);
                }
            }

            if (findMatches(null, null).any) continue;
            solved = hasPossibleMove();
        }

        if (!solved) {
            initBoardNoMatches();
        }

        if (announce) {
            showBanner("Fresh shuffle. New lines, new specials.", new Color(166, 225, 255));
            triggerShake(8, 120);
            audio.playSfx("/music/sfxsound/awnocookies.mp3");
        }

        animState = AnimState.IDLE;
    }

    private void swapGridPieces(int r1, int c1, int r2, int c2) {
        Piece temp = grid[r1][c1];
        grid[r1][c1] = grid[r2][c2];
        grid[r2][c2] = temp;
    }

    private boolean createsMatchAt(int row, int col) {
        Piece piece = grid[row][col];
        if (piece == null || piece.color < 0) return false;
        return formsMatchAt(row, col);
    }

    private boolean formsMatchAt(int row, int col) {
        Piece piece = grid[row][col];
        if (piece == null || piece.color < 0) return false;

        int color = piece.color;
        int run = 1;
        for (int c = col - 1; c >= 0 && sameColor(row, c, color); c--) run++;
        for (int c = col + 1; c < BOARD_SIZE && sameColor(row, c, color); c++) run++;
        if (run >= 3) return true;

        run = 1;
        for (int r = row - 1; r >= 0 && sameColor(r, col, color); r--) run++;
        for (int r = row + 1; r < BOARD_SIZE && sameColor(r, col, color); r++) run++;
        return run >= 3;
    }

    private boolean sameColor(int row, int col, int color) {
        return isInsideBoard(row, col) && grid[row][col] != null && grid[row][col].color == color;
    }

    private boolean adjacent(Point a, Point b) {
        return Math.abs(a.x - b.x) + Math.abs(a.y - b.y) == 1;
    }

    private boolean isInsideBoard(int row, int col) {
        return row >= 0 && row < BOARD_SIZE && col >= 0 && col < BOARD_SIZE;
    }

    private boolean isBlockedCell(int row, int col) {
        return blockerLayers[row][col] > 0;
    }

    private boolean isRocket(Special special) {
        return special == Special.ROCKET_H || special == Special.ROCKET_V;
    }

    private void setPiece(int row, int col, Piece piece) {
        grid[row][col] = piece;
        if (piece != null) {
            piece.tx = col * TILE_SIZE;
            piece.ty = row * TILE_SIZE;
        }
    }

    private void showBanner(String text, Color color) {
        bannerText = text;
        bannerColor = color;
        bannerUntilMs = nowMs + 1650;
    }

    private String specialCreatedText(Collection<Special> specials) {
        boolean hasRainbow = false;
        boolean hasBomb = false;
        boolean hasRocket = false;

        for (Special special : specials) {
            if (special == Special.RAINBOW) hasRainbow = true;
            if (special == Special.BOMB) hasBomb = true;
            if (isRocket(special)) hasRocket = true;
        }

        if (hasRainbow) return "Rainbow created. Click the glowing rainbow cookie.";
        if (hasBomb) return "Bomb created. Click any glowing bomb cookie.";
        if (hasRocket) return "Rocket created. Click any glowing arrow cookie.";
        return "Special cookie created. Click the glowing cookie.";
    }

    private String actionTextFor(Special special) {
        switch (special) {
            case ROCKET_H:
                return "Row rocket fired";
            case ROCKET_V:
                return "Column rocket fired";
            case BOMB:
                return "Bomb exploded";
            case RAINBOW:
                return "Rainbow fired";
            case NONE:
            default:
                return "Special triggered";
        }
    }

    private String sfxForSpecial(Special s) {
        switch (s) {
            case ROCKET_H: case ROCKET_V: return "/music/sfxsound/weerocket.mp3";
            case RAINBOW: return "/music/sfxsound/raimbowcookie1.mp3";
            default: return "/sfx/tnt.wav";
        }
    }

    private void triggerShake(int magnitude, int durationMs) {
        shakeMagnitude = magnitude;
        shakeUntilMs = nowMs + durationMs;
    }

    private void lerpToTarget(Piece piece, float t) {
        piece.px = lerp(piece.px, piece.tx, t);
        piece.py = lerp(piece.py, piece.ty, t);
    }

    private void snapPiece(Piece piece) {
        piece.px = piece.tx;
        piece.py = piece.ty;
        piece.alpha = 1f;
        piece.scale = 1f;
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    private static float clamp01(float value) {
        return Math.max(0f, Math.min(1f, value));
    }

    private static float easeInOut(float t) {
        return t * t * (3f - 2f * t);
    }

    private static float easeOut(float t) {
        float u = 1f - t;
        return 1f - u * u;
    }

    private String loopMusicPathFor(int levelNumber) {
        int clamped = Math.max(1, Math.min(levelNumber, 9));
        return "/music/level" + clamped + ".mp3";
    }

    private String introMusicPathFor(int levelNumber) {
        if (levelNumber <= 1) return "/music/LEVEL1VOICE.mp3";
        if (levelNumber == 2) return "/music/LEVEL2VOICE.mp3";
        if (levelNumber == 3) return "/music/LEVEL3VOICE.mp3";
        if (levelNumber == 4) return "/music/LEVEL4VOICE.mp3";
        if (levelNumber == 5) return "/music/LEVEL5VOICE.mp3";
        if (levelNumber == 6) return "/music/LEVEL6VOICE.mp3";
        if (levelNumber == 7) return "/music/LEVEL7VOICE.mp3";
        if (levelNumber == 8) return "/music/LEVEL8VOICE.mp3";
        return "/music/LEVEL9VOICE.mp3";
    }

    private int introDelayMsFor(int levelNumber) {
        if (levelNumber <= 9) {
            return 1500;
        }
        return INTRO_DELAY_MS;
    }

    private void pushHud() {
        if (scoreboard != null) {
            scoreboard.setStats(new GameStats(
                    level,
                    moves,
                    scoreThisLevel,
                    targetScore,
                    objective.title(),
                    objective.progressText(),
                    combo,
                    bestCombo,
                    currentStars,
                    getBestStars(level),
                    unlockedLevel,
                    getTotalStars(),
                    bannerUntilMs > nowMs
                            ? bannerText
                            : hasSpecialsOnBoard()
                            ? "Arrow cookies are rockets. Click any glowing special cookie."
                            : "Best score: " + getBestScore(level)
            ));
        }

        if (uiStateListener != null) {
            uiStateListener.run();
        }
    }

    private float getBoardScale() {
        return Math.min(getWidth(), getHeight()) / (float) CANVAS_SIZE;
    }

    private int getBoardOffsetX() {
        return Math.max(0, (getWidth() - Math.round(CANVAS_SIZE * getBoardScale())) / 2);
    }

    private int getBoardOffsetY() {
        return Math.max(0, (getHeight() - Math.round(CANVAS_SIZE * getBoardScale())) / 2);
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        // Background always fills the whole panel
        if (bgImage != null) {
            g2.drawImage(bgImage, 0, 0, getWidth(), getHeight(), null);
        } else {
            g2.setPaint(new GradientPaint(0, 0, new Color(18, 29, 52), 0, getHeight(), new Color(7, 12, 25)));
            g2.fillRect(0, 0, getWidth(), getHeight());
        }

        // Scale and center the game board
        float scale = getBoardScale();
        g2.translate(getBoardOffsetX(), getBoardOffsetY());
        g2.scale(scale, scale);

        if (shakeUntilMs > nowMs) {
            int dx = random.nextInt(shakeMagnitude * 2 + 1) - shakeMagnitude;
            int dy = random.nextInt(shakeMagnitude * 2 + 1) - shakeMagnitude;
            g2.translate(dx, dy);
        }

        drawBoard(g2);
        drawPieces(g2);
        drawSelectionAndHints(g2);

        if (flashAlpha > 0f) {
            Composite old = g2.getComposite();
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, flashAlpha));
            g2.setColor(new Color(255, 255, 255));
            g2.fillRoundRect(8, 8, CANVAS_SIZE - 16, CANVAS_SIZE - 16, 20, 20);
            g2.setComposite(old);
        }

        if (moves <= 0 && !objective.complete() && !levelTransition && !levelSummary) {
            drawGameOver(g2);
        }

        if (levelSummary) {
            drawLevelSummary(g2);
        }

        if (bannerUntilMs > nowMs) {
            drawBanner(g2);
        }

        if (bgFade > 0f) {
            Composite old = g2.getComposite();
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, clamp01(bgFade)));
            g2.setColor(Color.BLACK);
            g2.fillRect(0, 0, CANVAS_SIZE, CANVAS_SIZE);
            g2.setComposite(old);
        }

        g2.dispose();
    }

    private void drawBoard(Graphics2D g2) {
        for (int r = 0; r < BOARD_SIZE; r++) {
            for (int c = 0; c < BOARD_SIZE; c++) {
                int x = c * TILE_SIZE;
                int y = r * TILE_SIZE;

                g2.setColor(new Color(8, 12, 22, 110));
                g2.fillRoundRect(x + 3, y + 3, TILE_SIZE - 6, TILE_SIZE - 6, 14, 14);

                if (blockerLayers[r][c] > 0) {
                    Composite old = g2.getComposite();
                    float alpha = 0.96f;
                    g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));

                    if (blockedTileImage != null) {
                        g2.drawImage(blockedTileImage, x + 2, y + 2, TILE_SIZE - 4, TILE_SIZE - 4, null);
                    } else {
                        g2.setColor(new Color(175, 183, 184, 220));
                        g2.fillRoundRect(x + 5, y + 5, TILE_SIZE - 10, TILE_SIZE - 10, 16, 16);
                    }

                    g2.setComposite(old);
                    g2.setColor(new Color(28, 43, 56, 210));
                    g2.setStroke(new BasicStroke(2f));
                    g2.drawRoundRect(x + 3, y + 3, TILE_SIZE - 6, TILE_SIZE - 6, 16, 16);
                }
            }
        }
    }

    private void drawPieces(Graphics2D g2) {
        for (int r = 0; r < BOARD_SIZE; r++) {
            for (int c = 0; c < BOARD_SIZE; c++) {
                Piece piece = grid[r][c];
                if (piece == null) continue;

                float cx = piece.px + TILE_SIZE / 2f;
                float cy = piece.py + TILE_SIZE / 2f;

                Composite oldComposite = g2.getComposite();
                g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, clamp01(piece.alpha)));

                AffineTransform oldTransform = g2.getTransform();
                AffineTransform transform = new AffineTransform(oldTransform);
                transform.translate(cx, cy);
                transform.scale(piece.scale, piece.scale);
                transform.translate(-cx, -cy);
                g2.setTransform(transform);

                int drawX = Math.round(piece.px) + 6;
                int drawY = Math.round(piece.py) + 6;
                int size = TILE_SIZE - 12;
                drawPiece(g2, piece, drawX, drawY, size);

                g2.setTransform(oldTransform);
                g2.setComposite(oldComposite);
            }
        }
    }

    private void drawPiece(Graphics2D g2, Piece piece, int drawX, int drawY, int size) {
        if (piece.special == Special.RAINBOW) {
            Paint oldPaint = g2.getPaint();
            g2.setPaint(new GradientPaint(drawX, drawY, new Color(255, 120, 120),
                    drawX + size, drawY + size, new Color(120, 190, 255), true));
            g2.fillOval(drawX + 2, drawY + 2, size - 4, size - 4);
            g2.setPaint(oldPaint);
            g2.setColor(new Color(255, 255, 255, 210));
            g2.drawOval(drawX + 4, drawY + 4, size - 8, size - 8);
            g2.drawOval(drawX + 9, drawY + 9, size - 18, size - 18);
            return;
        }

        if (piece.special == Special.BOMB) {
            if (bombStamp != null) {
                g2.drawImage(bombStamp, drawX, drawY, size, size, null);
            } else {
                g2.setColor(new Color(247, 246, 244));
                g2.fillOval(drawX, drawY, size, size);
                drawBombOverlay(g2, drawX, drawY, size);
            }
            return;
        }

        BufferedImage image = piece.color >= 0 && piece.color < cookieImages.length ? cookieImages[piece.color] : null;
        if (image != null) {
            g2.drawImage(image, drawX, drawY, size, size, null);
        } else {
            g2.setColor(new Color(247, 246, 244));
            g2.fillOval(drawX, drawY, size, size);
        }

        switch (piece.special) {
            case ROCKET_H:
            case ROCKET_V:
                drawRocketOverlay(g2, piece.special, drawX, drawY, size);
                break;
            case NONE:
            case RAINBOW:
            default:
                break;
        }
    }

    private void drawRocketOverlay(Graphics2D g2, Special special, int x, int y, int size) {
        g2.setColor(new Color(255, 255, 255, 220));
        Stroke old = g2.getStroke();
        g2.setStroke(new BasicStroke(4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        if (special == Special.ROCKET_H) {
            g2.drawLine(x + 10, y + size / 2, x + size - 10, y + size / 2);
            g2.drawLine(x + size - 18, y + size / 2 - 8, x + size - 8, y + size / 2);
            g2.drawLine(x + size - 18, y + size / 2 + 8, x + size - 8, y + size / 2);
        } else {
            g2.drawLine(x + size / 2, y + 10, x + size / 2, y + size - 10);
            g2.drawLine(x + size / 2 - 8, y + 18, x + size / 2, y + 8);
            g2.drawLine(x + size / 2 + 8, y + 18, x + size / 2, y + 8);
        }
        g2.setStroke(old);
    }

    private void drawBombOverlay(Graphics2D g2, int x, int y, int size) {
        if (bombStamp != null) {
            g2.drawImage(bombStamp, x + size / 4, y + size / 4, size / 2, size / 2, null);
            return;
        }

        g2.setColor(new Color(42, 42, 42, 220));
        g2.fillOval(x + 13, y + 13, size - 26, size - 26);
        g2.setColor(new Color(255, 220, 88));
        g2.drawLine(x + size / 2, y + 10, x + size / 2 + 10, y + 2);
    }

    private void drawSelectionAndHints(Graphics2D g2) {
        if (selected != null && animState == AnimState.IDLE && !levelTransition && !levelSummary) {
            drawCellOutline(g2, selected.x, selected.y, new Color(248, 250, 252), 3f);
        }

        if (animState == AnimState.IDLE) {
            List<Point> specials = collectSpecialPoints();
            if (!specials.isEmpty()) {
                float pulse = 0.5f + 0.2f * (float) Math.sin(nowMs / 120.0);
                Composite old = g2.getComposite();
                g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, clamp01(pulse)));
                for (Point point : specials) {
                    drawCellOutline(g2, point.x, point.y, new Color(255, 226, 120), 5f);
                }
                g2.setComposite(old);
            }
        }

        if (hintMove != null && animState == AnimState.IDLE) {
            float pulse = 0.35f + 0.25f * (float) Math.sin(nowMs / 180.0);
            Composite old = g2.getComposite();
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, clamp01(pulse)));
            drawCellOutline(g2, hintMove.a.x, hintMove.a.y, new Color(120, 220, 255), 3f);
            drawCellOutline(g2, hintMove.b.x, hintMove.b.y, new Color(120, 220, 255), 3f);
            g2.setComposite(old);
        }
    }

    private void drawCellOutline(Graphics2D g2, int col, int row, Color color, float thickness) {
        int x = col * TILE_SIZE;
        int y = row * TILE_SIZE;
        g2.setColor(color);
        Stroke old = g2.getStroke();
        g2.setStroke(new BasicStroke(thickness));
        g2.drawRoundRect(x + 4, y + 4, TILE_SIZE - 8, TILE_SIZE - 8, 14, 14);
        g2.setStroke(old);
    }

    private void drawGameOver(Graphics2D g2) {
        g2.setColor(new Color(10, 16, 28, 225));
        g2.fillRect(0, 0, CANVAS_SIZE, CANVAS_SIZE);

        g2.setColor(new Color(248, 250, 252));
        g2.setFont(getFont().deriveFont(Font.BOLD, 30f));
        drawCentered(g2, "Out of moves", CANVAS_SIZE / 2, CANVAS_SIZE / 2 - 24);

        g2.setFont(getFont().deriveFont(Font.PLAIN, 18f));
        drawCentered(g2, "Click anywhere to retry level " + level + ".", CANVAS_SIZE / 2, CANVAS_SIZE / 2 + 10);
    }

    private void drawLevelSummary(Graphics2D g2) {
        g2.setColor(new Color(6, 10, 18, 220));
        g2.fillRoundRect(38, 110, CANVAS_SIZE - 76, 210, 24, 24);

        g2.setColor(new Color(244, 247, 252));
        g2.setFont(getFont().deriveFont(Font.BOLD, 26f));
        drawCentered(g2, "Level " + level + " cleared", CANVAS_SIZE / 2, 156);

        g2.setFont(getFont().deriveFont(Font.PLAIN, 16f));
        drawCentered(g2, "Bonus: +" + summaryBonus, CANVAS_SIZE / 2, 188);
        drawCentered(g2, "Best combo: x" + Math.max(1, bestCombo), CANVAS_SIZE / 2, 212);
        drawCentered(g2, starsLabel(summaryStars), CANVAS_SIZE / 2, 246);
        drawCentered(g2, "Next level loading...", CANVAS_SIZE / 2, 282);
    }

    private void drawBanner(Graphics2D g2) {
        int bannerWidth = CANVAS_SIZE - 36;
        int x = 18;
        int y = 18;
        g2.setColor(new Color(6, 10, 18, 190));
        g2.fillRoundRect(x, y, bannerWidth, 42, 20, 20);
        g2.setColor(bannerColor);
        g2.setFont(getFont().deriveFont(Font.BOLD, 15f));
        drawCentered(g2, bannerText, CANVAS_SIZE / 2, y + 27);
    }

    private boolean hasSpecialsOnBoard() {
        for (int r = 0; r < BOARD_SIZE; r++) {
            for (int c = 0; c < BOARD_SIZE; c++) {
                Piece piece = grid[r][c];
                if (piece != null && piece.special != Special.NONE) {
                    return true;
                }
            }
        }
        return false;
    }

    private List<Point> collectSpecialPoints() {
        List<Point> points = new ArrayList<Point>();
        for (int r = 0; r < BOARD_SIZE; r++) {
            for (int c = 0; c < BOARD_SIZE; c++) {
                Piece piece = grid[r][c];
                if (piece != null && piece.special != Special.NONE) {
                    points.add(new Point(c, r));
                }
            }
        }
        return points;
    }

    private void drawCentered(Graphics2D g2, String text, int centerX, int y) {
        int width = g2.getFontMetrics().stringWidth(text);
        g2.drawString(text, centerX - width / 2, y);
    }

    private String starsLabel(int starCount) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 3; i++) {
            sb.append(i < starCount ? "[*]" : "[ ]");
        }
        return sb.toString();
    }
}
