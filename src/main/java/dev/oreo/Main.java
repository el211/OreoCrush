package dev.oreo;

import com.jme3.app.SimpleApplication;
import com.jme3.input.MouseInput;
import com.jme3.input.controls.ActionListener;
import com.jme3.input.controls.MouseButtonTrigger;
import com.jme3.material.Material;
import com.jme3.material.MatParam;
import com.jme3.math.ColorRGBA;
import com.jme3.math.FastMath;
import com.jme3.math.Vector2f;
import com.jme3.math.Vector3f;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.jme3.scene.shape.Quad;
import com.jme3.system.AppSettings;
import com.jme3.texture.Texture;
import com.jme3.ui.Picture;
import com.jme3.font.BitmapFont;
import com.jme3.font.BitmapText;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

public class Main extends SimpleApplication {

    private static final int BOARD_SIZE = 8;
    private static final int COLORS = 9;
    private static final float TILE = 78f;
    private static final float GAP = 8f;
    private static final float CELL = TILE + GAP;
    private static final float BOARD_PAD = 18f;

    private enum Special {
        NONE,
        ROCKET_H,
        ROCKET_V,
        BOMB,
        RAINBOW
    }

    private enum Language {
        EN,
        FR
    }

    private static final class Piece {
        int color;
        Special special = Special.NONE;
        Geometry tile;
        Geometry glow;
        Geometry specialOverlay;
        Vector3f from = new Vector3f();
        Vector3f target = new Vector3f();
        float scale = 1f;
        float clearAge;
        float wobbleSeed;
        boolean clearing;
    }

    private static final class FloatLabel {
        BitmapText text;
        float age;
    }

    private static final class RocketEffect {
        Geometry rocket;
        Geometry beam;
        Vector3f start = new Vector3f();
        Vector3f end = new Vector3f();
        float age;
        float duration;
    }

    private static final class ShardEffect {
        Geometry shard;
        Vector3f pos = new Vector3f();
        Vector3f velocity = new Vector3f();
        float age;
        float duration;
        float spin;
    }

    private final Random random = new Random();
    private final Piece[][] board = new Piece[BOARD_SIZE][BOARD_SIZE];
    private final Node boardNode = new Node("board");
    private final Node effectsNode = new Node("effects");
    private final Node languageNode = new Node("language");
    private final List<FloatLabel> labels = new ArrayList<FloatLabel>();
    private final List<RocketEffect> rocketEffects = new ArrayList<RocketEffect>();
    private final List<ShardEffect> shardEffects = new ArrayList<ShardEffect>();
    private final AudioManager audio = new AudioManager();

    private Texture[] cookieTextures;
    private Texture horizontalRocketTexture;
    private Texture verticalRocketTexture;
    private Texture specialBombTexture;
    private Texture rainbowTexture;
    private Picture backgroundPicture;
    private BitmapFont font;
    private BitmapText titleText;
    private BitmapText scoreText;
    private BitmapText moveText;
    private BitmapText objectiveText;
    private BitmapText statusText;
    private BitmapText comboText;
    private BitmapText restartText;
    private BitmapText levelText;
    private BitmapText languageTitleText;
    private BitmapText englishText;
    private BitmapText frenchText;

    private float boardX;
    private float boardY;
    private float languageButtonY;
    private float englishButtonX;
    private float frenchButtonX;
    private int selectedRow = -1;
    private int selectedCol = -1;
    private int score;
    private int moves;
    private int combo;
    private int bestCombo;
    private int targetScore;
    private int level = 1;
    private int currentLevelIndex;
    private LevelConfig config;
    private boolean busy;
    private float busyTimer;
    private boolean levelCompleteAnnounced;
    private float sfxClock;
    private float lastComboVoiceAt = -10f;
    private float lastClearSfxAt = -10f;
    private Language language = Language.EN;
    private boolean languageSelected;
    private String status = "Match cookies. Four creates rockets, five creates rainbow.";

    private static final class LevelConfig {
        final int level;
        final int moves;
        final int target;
        final int colors;
        final String objectiveEn;
        final String objectiveFr;

        LevelConfig(int level, int moves, int target, int colors, String objectiveEn, String objectiveFr) {
            this.level = level;
            this.moves = moves;
            this.target = target;
            this.colors = colors;
            this.objectiveEn = objectiveEn;
            this.objectiveFr = objectiveFr;
        }
    }

    private static final LevelConfig[] LEVELS = new LevelConfig[]{
            new LevelConfig(1, 18, 1700, 5, "Warmup: reach the score target.", "Echauffement : atteins le score cible."),
            new LevelConfig(2, 18, 2400, 6, "Use deliberate swaps to build a bigger score.", "Fais des echanges precis pour marquer plus."),
            new LevelConfig(3, 20, 2600, 6, "Create rockets with 4-cookie lines.", "Cree des fusees avec des lignes de 4 cookies."),
            new LevelConfig(4, 18, 3700, 7, "Fire specials to clear rows and columns.", "Declenche des bonus pour vider lignes et colonnes."),
            new LevelConfig(5, 18, 4500, 7, "Make rainbow cookies with 5-cookie lines.", "Cree des cookies arc-en-ciel avec 5 cookies."),
            new LevelConfig(6, 19, 5400, 8, "Plan smart moves for bonus scoring.", "Prepare des coups precis pour marquer gros."),
            new LevelConfig(7, 19, 6300, 8, "Use every special type together.", "Utilise tous les types de bonus ensemble."),
            new LevelConfig(8, 20, 7400, 9, "High score challenge.", "Defi gros score."),
            new LevelConfig(9, 21, 8800, 9, "Final mix: score, specials, precision.", "Melange final : score, bonus et precision.")
    };

    public static void main(String[] args) {
        Main app = new Main();
        AppSettings settings = new AppSettings(true);
        settings.setTitle("Oreo Crush");
        settings.setResolution(1280, 800);
        settings.setSamples(4);
        settings.setVSync(true);
        app.setSettings(settings);
        app.setShowSettings(false);
        app.start();
    }

    @Override
    public void simpleInitApp() {
        flyCam.setEnabled(false);
        inputManager.setCursorVisible(true);
        guiNode.detachAllChildren();

        font = assetManager.loadFont("Interface/Fonts/Default.fnt");
        cookieTextures = new Texture[]{
                assetManager.loadTexture("assets/cookie_classic.png"),
                assetManager.loadTexture("assets/cookie_choco.png"),
                assetManager.loadTexture("assets/cookie_vanilla.png"),
                assetManager.loadTexture("assets/cookie_sprinkle.png"),
                assetManager.loadTexture("assets/mintcookie.png"),
                assetManager.loadTexture("assets/pistachiocookie.png"),
                assetManager.loadTexture("assets/strawberrycookie.png"),
                assetManager.loadTexture("assets/caramelcookie.png"),
                assetManager.loadTexture("assets/blueberrycookie.png")
        };
        horizontalRocketTexture = assetManager.loadTexture("assets/HorizontalRocket.png");
        verticalRocketTexture = assetManager.loadTexture("assets/VerticalRocket.png");
        specialBombTexture = assetManager.loadTexture("assets/bonb.png");
        rainbowTexture = assetManager.loadTexture("assets/raimbow.png");

        buildScene();
        buildLanguageScreen();
        inputManager.addMapping("click", new MouseButtonTrigger(MouseInput.BUTTON_LEFT));
        inputManager.addListener(clickListener, "click");
    }

    private void buildScene() {
        backgroundPicture = new Picture("background");
        backgroundPicture.setImage(assetManager, "backgrounds/bg_level1.png", true);
        backgroundPicture.setWidth(settings.getWidth());
        backgroundPicture.setHeight(settings.getHeight());
        backgroundPicture.setPosition(0, 0);
        guiNode.attachChild(backgroundPicture);

        Geometry shade = rect("shade", 0, 0, settings.getWidth(), settings.getHeight(),
                new ColorRGBA(0.02f, 0.025f, 0.035f, 0.54f));
        guiNode.attachChild(shade);

        boardX = Math.max(40f, settings.getWidth() * 0.08f);
        boardY = (settings.getHeight() - (BOARD_SIZE * CELL - GAP) - BOARD_PAD * 2f) / 2f;

        Geometry boardPanel = rect("board-panel",
                boardX - BOARD_PAD,
                boardY - BOARD_PAD,
                BOARD_SIZE * CELL - GAP + BOARD_PAD * 2f,
                BOARD_SIZE * CELL - GAP + BOARD_PAD * 2f,
                new ColorRGBA(0.035f, 0.045f, 0.065f, 0.82f));
        guiNode.attachChild(boardPanel);

        Geometry boardLine = rect("board-accent",
                boardX - BOARD_PAD,
                boardY + BOARD_SIZE * CELL - GAP + BOARD_PAD - 5f,
                BOARD_SIZE * CELL - GAP + BOARD_PAD * 2f,
                5f,
                new ColorRGBA(0.25f, 0.72f, 1f, 0.95f));
        guiNode.attachChild(boardLine);

        guiNode.attachChild(boardNode);
        guiNode.attachChild(effectsNode);

        float hudX = boardX + BOARD_SIZE * CELL + 74f;
        Geometry hud = rect("hud", hudX, boardY - BOARD_PAD, 380f,
                BOARD_SIZE * CELL - GAP + BOARD_PAD * 2f, new ColorRGBA(0.025f, 0.032f, 0.046f, 0.84f));
        guiNode.attachChild(hud);
        guiNode.attachChild(rect("hud-accent", hudX, boardY - BOARD_PAD, 5f,
                BOARD_SIZE * CELL - GAP + BOARD_PAD * 2f, new ColorRGBA(1f, 0.78f, 0.24f, 1f)));

        titleText = text("OREO CRUSH", hudX + 30f, boardY + 610f, 42f, ColorRGBA.White);
        levelText = text("", hudX + 32f, boardY + 565f, 22f, new ColorRGBA(1f, 0.78f, 0.24f, 1f));
        scoreText = text("", hudX + 32f, boardY + 520f, 28f, new ColorRGBA(0.88f, 0.95f, 1f, 1f));
        moveText = text("", hudX + 32f, boardY + 475f, 28f, new ColorRGBA(1f, 0.9f, 0.62f, 1f));
        comboText = text("", hudX + 32f, boardY + 430f, 23f, new ColorRGBA(0.58f, 1f, 0.78f, 1f));
        objectiveText = text("", hudX + 32f, boardY + 346f, 20f, new ColorRGBA(0.82f, 0.9f, 1f, 1f));
        statusText = text("", hudX + 32f, boardY + 205f, 19f, new ColorRGBA(1f, 0.88f, 0.52f, 1f));
        restartText = text("NEW BOARD", hudX + 32f, boardY + 82f, 24f, ColorRGBA.White);
        guiNode.attachChild(titleText);
        guiNode.attachChild(levelText);
        guiNode.attachChild(scoreText);
        guiNode.attachChild(moveText);
        guiNode.attachChild(comboText);
        guiNode.attachChild(objectiveText);
        guiNode.attachChild(statusText);
        guiNode.attachChild(restartText);
        guiNode.attachChild(rect("restart-button", hudX + 24f, boardY + 44f, 210f, 58f,
                new ColorRGBA(0.18f, 0.48f, 0.92f, 0.92f)));
        restartText.setLocalTranslation(hudX + 50f, boardY + 82f, 5f);
    }

    private void buildLanguageScreen() {
        languageNode.detachAllChildren();
        guiNode.attachChild(languageNode);

        Geometry panel = rect("language-panel",
                settings.getWidth() / 2f - 310f,
                settings.getHeight() / 2f - 150f,
                620f,
                300f,
                new ColorRGBA(0.025f, 0.032f, 0.046f, 0.92f));
        languageNode.attachChild(panel);

        languageTitleText = text("Choose language / Choisir la langue",
                settings.getWidth() / 2f - 235f,
                settings.getHeight() / 2f + 88f,
                28f,
                ColorRGBA.White);
        languageNode.attachChild(languageTitleText);

        languageButtonY = settings.getHeight() / 2f - 45f;
        englishButtonX = settings.getWidth() / 2f - 245f;
        frenchButtonX = settings.getWidth() / 2f + 25f;
        languageNode.attachChild(rect("english-button", englishButtonX, languageButtonY, 220f, 74f,
                new ColorRGBA(0.18f, 0.48f, 0.92f, 0.95f)));
        languageNode.attachChild(rect("french-button", frenchButtonX, languageButtonY, 220f, 74f,
                new ColorRGBA(0.92f, 0.42f, 0.18f, 0.95f)));

        englishText = text("English", englishButtonX + 58f, languageButtonY + 47f, 26f, ColorRGBA.White);
        frenchText = text("Francais", frenchButtonX + 52f, languageButtonY + 47f, 26f, ColorRGBA.White);
        languageNode.attachChild(englishText);
        languageNode.attachChild(frenchText);
    }

    private final ActionListener clickListener = new ActionListener() {
        @Override
        public void onAction(String name, boolean pressed, float tpf) {
            if (!pressed || busy) return;
            Vector2f cursor = inputManager.getCursorPosition();
            if (!languageSelected) {
                handleLanguageClick(cursor);
                return;
            }
            if (isRestartClick(cursor)) {
                if (score >= targetScore) {
                    startLevel(level + 1);
                } else {
                    startLevel(level);
                }
                return;
            }
            handleBoardClick(cursor);
        }
    };

    private void handleLanguageClick(Vector2f cursor) {
        if (inside(cursor, englishButtonX, languageButtonY, 220f, 74f)) {
            chooseLanguage(Language.EN);
        } else if (inside(cursor, frenchButtonX, languageButtonY, 220f, 74f)) {
            chooseLanguage(Language.FR);
        }
    }

    private void chooseLanguage(Language selectedLanguage) {
        language = selectedLanguage;
        languageSelected = true;
        languageNode.removeFromParent();
        startLevel(1);
    }

    private boolean isRestartClick(Vector2f cursor) {
        Spatial restart = guiNode.getChild("restart-button");
        if (restart == null) return false;
        Vector3f p = restart.getLocalTranslation();
        return inside(cursor, p.x, p.y, 210f, 58f);
    }

    private boolean inside(Vector2f cursor, float x, float y, float w, float h) {
        return cursor.x >= x && cursor.x <= x + w && cursor.y >= y && cursor.y <= y + h;
    }

    private void handleBoardClick(Vector2f cursor) {
        audio.playSfx("/music/sfxsound/nomnom.mp3");
        int col = (int) ((cursor.x - boardX) / CELL);
        int row = BOARD_SIZE - 1 - (int) ((cursor.y - boardY) / CELL);
        if (row < 0 || row >= BOARD_SIZE || col < 0 || col >= BOARD_SIZE) {
            clearSelection();
            return;
        }

        Piece piece = board[row][col];
        if (piece == null) return;

        if (piece.special != Special.NONE) {
            clearSelection();
            startSpecialActivation(row, col, piece);
            return;
        }

        if (selectedRow < 0) {
            select(row, col);
            return;
        }

        if (selectedRow == row && selectedCol == col) {
            clearSelection();
            return;
        }

        if (Math.abs(selectedRow - row) + Math.abs(selectedCol - col) != 1) {
            select(row, col);
            status = textFor("pickNeighbor");
            updateHud();
            return;
        }

        int oldRow = selectedRow;
        int oldCol = selectedCol;
        clearSelection();
        swap(oldRow, oldCol, row, col);
        Set<Integer> matches = findMatches();
        if (matches.isEmpty()) {
            swap(oldRow, oldCol, row, col);
            status = textFor("noMatch");
            combo = 0;
            audio.playSfx("/music/sfxsound/oopsie.mp3");
            pulse(piece, 1.18f);
            updateHud();
            return;
        }
        audio.playSfx("/music/sfxsound/mixymixy.mp3");
        consumeMove();
        combo = 0;
        clearCells(matches, textFor("niceMatch"), false, true);
    }

    private void startLevel(int requestedLevel) {
        currentLevelIndex = Math.max(0, Math.min(requestedLevel - 1, LEVELS.length - 1));
        config = LEVELS[currentLevelIndex];
        level = config.level;
        applyLevelMedia();
        boardNode.detachAllChildren();
        effectsNode.detachAllChildren();
        labels.clear();
        score = 0;
        moves = config.moves;
        combo = 0;
        bestCombo = 0;
        targetScore = config.target;
        selectedRow = -1;
        selectedCol = -1;
        busy = false;
        levelCompleteAnnounced = false;
        status = textFor("levelStart");

        for (int r = 0; r < BOARD_SIZE; r++) {
            for (int c = 0; c < BOARD_SIZE; c++) {
                Piece p;
                do {
                    p = createPiece(random.nextInt(activeColorCount()), c, r);
                    board[r][c] = p;
                } while (createsMatch(r, c));
                boardNode.attachChild(p.glow);
                boardNode.attachChild(p.tile);
                boardNode.attachChild(p.specialOverlay);
            }
        }
        removePassiveMatches();
        updateHud();
        refreshAllTargets();
    }

    private void applyLevelMedia() {
        int bgLevel = Math.max(1, Math.min(level, 7));
        backgroundPicture.setImage(assetManager, "backgrounds/bg_level" + bgLevel + ".png", true);
        backgroundPicture.setWidth(settings.getWidth());
        backgroundPicture.setHeight(settings.getHeight());

        int audioLevel = Math.max(1, Math.min(level, 9));
        audio.stopMusic();
        audio.playVoice("/music/LEVEL" + audioLevel + "VOICE.mp3");
        audio.playMusicLoop("/music/level" + audioLevel + ".mp3");
    }

    private Piece createPiece(int color, int col, int row) {
        Piece p = new Piece();
        p.color = color;
        p.tile = imageQuad("piece", cookieTextures[color], TILE, TILE);
        p.glow = rect("glow", 0, 0, TILE + 12f, TILE + 12f, new ColorRGBA(0.4f, 0.8f, 1f, 0f));
        p.specialOverlay = imageQuad("special-overlay", horizontalRocketTexture, TILE, TILE);
        setAlpha(p.specialOverlay, 0f);
        p.wobbleSeed = random.nextFloat() * FastMath.TWO_PI;
        p.target = cellPos(row, col);
        p.from.set(p.target);
        p.tile.setLocalTranslation(p.target);
        p.glow.setLocalTranslation(p.target.x - 6f, p.target.y - 6f, 1f);
        p.specialOverlay.setLocalTranslation(p.target.x, p.target.y, 4f);
        return p;
    }

    private Geometry imageQuad(String name, Texture texture, float w, float h) {
        Geometry g = new Geometry(name, new Quad(w, h));
        Material mat = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
        mat.setTexture("ColorMap", texture);
        mat.setColor("Color", ColorRGBA.White);
        mat.getAdditionalRenderState().setBlendMode(com.jme3.material.RenderState.BlendMode.Alpha);
        g.setMaterial(mat);
        g.setQueueBucket(com.jme3.renderer.queue.RenderQueue.Bucket.Gui);
        return g;
    }

    private Geometry rect(String name, float x, float y, float w, float h, ColorRGBA color) {
        Geometry g = new Geometry(name, new Quad(w, h));
        Material mat = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
        mat.setColor("Color", color);
        mat.getAdditionalRenderState().setBlendMode(com.jme3.material.RenderState.BlendMode.Alpha);
        g.setMaterial(mat);
        g.setQueueBucket(com.jme3.renderer.queue.RenderQueue.Bucket.Gui);
        g.setLocalTranslation(x, y, 0);
        return g;
    }

    private BitmapText text(String value, float x, float y, float size, ColorRGBA color) {
        BitmapText t = new BitmapText(font);
        t.setText(value);
        t.setSize(size);
        t.setColor(color);
        t.setLocalTranslation(x, y, 3f);
        return t;
    }

    private Vector3f cellPos(int row, int col) {
        return new Vector3f(boardX + col * CELL, boardY + (BOARD_SIZE - 1 - row) * CELL, 3f);
    }

    private void select(int row, int col) {
        clearSelection();
        selectedRow = row;
        selectedCol = col;
        Piece p = board[row][col];
        setColor(p.glow, new ColorRGBA(0.45f, 0.85f, 1f, 0.72f));
        pulse(p, 1.12f);
    }

    private void clearSelection() {
        if (selectedRow >= 0) {
            Piece p = board[selectedRow][selectedCol];
            if (p != null) setColor(p.glow, new ColorRGBA(0.4f, 0.8f, 1f, 0f));
        }
        selectedRow = -1;
        selectedCol = -1;
    }

    private void swap(int r1, int c1, int r2, int c2) {
        Piece a = board[r1][c1];
        Piece b = board[r2][c2];
        board[r1][c1] = b;
        board[r2][c2] = a;
        setTarget(b, r1, c1);
        setTarget(a, r2, c2);
    }

    private void setTarget(Piece p, int row, int col) {
        p.from.set(p.tile.getLocalTranslation());
        p.target.set(cellPos(row, col));
    }

    private void startSpecialActivation(int row, int col, Piece piece) {
        consumeMove();
        combo = 0;
        Set<Integer> clear = activationCells(row, col, piece.special, piece.color);
        audio.playSfx(sfxForSpecial(piece.special));

        float delay = 0.36f;
        if (piece.special == Special.ROCKET_H || piece.special == Special.ROCKET_V) {
            spawnRocketEffect(row, col, piece.special);
            delay = 0.48f;
        } else if (piece.special == Special.BOMB) {
            spawnBombFragments(clear);
            delay = 0.48f;
        } else if (piece.special == Special.RAINBOW) {
            spawnRainbowFlash(clear);
            delay = 0.38f;
        }

        busy = true;
        busyTimer = delay;
        status = specialName(piece.special) + " " + textFor("fired");
        updateHud();
        queueBoardAction(() -> clearCells(clear, textFor("specialFired"), true, false));
    }

    private void clearCells(Set<Integer> cells,
                            String message,
                            boolean specialClear,
                            boolean allowSpecialSpawn) {
        combo++;
        bestCombo = Math.max(bestCombo, combo);
        int gained = cells.size() * 36 + Math.max(0, combo - 1) * cells.size() * 18;
        if (specialClear) gained += 180;
        score += gained;
        status = message + "  +" + gained;
        playClearSfx(specialClear);
        spawnScoreLabel("+" + gained, averageX(cells), averageY(cells), combo > 1);

        Special spawn = allowSpecialSpawn ? specialForCells(cells) : Special.NONE;
        int spawnKey = pickSpawnCell(cells);
        for (Integer key : cells) {
            int r = key / BOARD_SIZE;
            int c = key % BOARD_SIZE;
            Piece p = board[r][c];
            if (p == null) continue;
            p.clearing = true;
            p.clearAge = 0f;
            p.from.set(p.tile.getLocalTranslation());
            setColor(p.glow, new ColorRGBA(1f, 0.78f, 0.22f, 0.65f));
        }

        busy = true;
        busyTimer = 0.34f;
        updateHud();

        queueBoardAction(() -> {
            for (Integer key : cells) {
                int r = key / BOARD_SIZE;
                int c = key % BOARD_SIZE;
                Piece p = board[r][c];
                if (p == null) continue;
                boardNode.detachChild(p.tile);
                boardNode.detachChild(p.glow);
                boardNode.detachChild(p.specialOverlay);
                board[r][c] = null;
            }
            if (spawn != Special.NONE && spawnKey >= 0) {
                int r = spawnKey / BOARD_SIZE;
                int c = spawnKey % BOARD_SIZE;
                Piece p = createPiece(random.nextInt(activeColorCount()), c, r);
                p.special = spawn;
                decorateSpecial(p);
                board[r][c] = p;
                boardNode.attachChild(p.glow);
                boardNode.attachChild(p.tile);
                boardNode.attachChild(p.specialOverlay);
                status = specialName(spawn) + " " + textFor("created");
                audio.playSfx("/music/sfxsound/supercookie.mp3");
            }
            collapseBoard();
            finishBoardSettle();
            updateHud();
        });
    }

    private final List<Runnable> pending = new ArrayList<Runnable>();

    private void queueBoardAction(Runnable action) {
        pending.add(action);
    }

    private void collapseBoard() {
        for (int c = 0; c < BOARD_SIZE; c++) {
            int write = BOARD_SIZE - 1;
            for (int r = BOARD_SIZE - 1; r >= 0; r--) {
                Piece p = board[r][c];
                if (p == null) continue;
                board[write][c] = p;
                if (write != r) board[r][c] = null;
                setTarget(p, write, c);
                write--;
            }
            while (write >= 0) {
                Piece p = createPiece(random.nextInt(activeColorCount()), c, write);
                p.tile.setLocalTranslation(boardX + c * CELL, boardY + BOARD_SIZE * CELL + (BOARD_SIZE - write) * 25f, 3f);
                p.from.set(p.tile.getLocalTranslation());
                board[write][c] = p;
                boardNode.attachChild(p.glow);
                boardNode.attachChild(p.tile);
                boardNode.attachChild(p.specialOverlay);
                setTarget(p, write, c);
                write--;
            }
        }
        removePassiveMatches();
    }

    private void spawnRocketEffect(int row, int col, Special special) {
        boolean horizontal = special == Special.ROCKET_H;
        Vector3f origin = cellPos(row, col);
        Texture texture = horizontal ? horizontalRocketTexture : verticalRocketTexture;
        Geometry rocket = imageQuad("rocket-effect", texture,
                horizontal ? TILE * 1.55f : TILE,
                horizontal ? TILE : TILE * 1.55f);

        RocketEffect effect = new RocketEffect();
        effect.rocket = rocket;
        effect.duration = 0.46f;
        effect.start.set(origin.x, origin.y, 22f);
        if (horizontal) {
            effect.end.set(boardX + (BOARD_SIZE - 1) * CELL + TILE * 0.15f, origin.y, 22f);
            effect.beam = rect("rocket-beam",
                    boardX,
                    origin.y + TILE * 0.38f,
                    BOARD_SIZE * CELL - GAP,
                    TILE * 0.18f,
                    new ColorRGBA(0.2f, 0.78f, 1f, 0.55f));
        } else {
            effect.end.set(origin.x, boardY + (BOARD_SIZE - 1) * CELL + TILE * 0.15f, 22f);
            effect.beam = rect("rocket-beam",
                    origin.x + TILE * 0.38f,
                    boardY,
                    TILE * 0.18f,
                    BOARD_SIZE * CELL - GAP,
                    new ColorRGBA(0.2f, 0.78f, 1f, 0.55f));
        }

        rocket.setLocalTranslation(effect.start);
        effect.beam.setLocalTranslation(effect.beam.getLocalTranslation().x, effect.beam.getLocalTranslation().y, 18f);
        effectsNode.attachChild(effect.beam);
        effectsNode.attachChild(rocket);
        rocketEffects.add(effect);
    }

    private void spawnBombFragments(Set<Integer> cells) {
        for (Integer key : cells) {
            int row = key / BOARD_SIZE;
            int col = key % BOARD_SIZE;
            Vector3f base = cellPos(row, col);
            for (int i = 0; i < 5; i++) {
                ShardEffect shard = new ShardEffect();
                float size = 12f + random.nextFloat() * 10f;
                shard.shard = rect("cookie-shard", 0, 0, size, size,
                        shardColor(board[row][col]));
                shard.pos.set(base.x + TILE * 0.5f, base.y + TILE * 0.5f, 24f);
                float angle = random.nextFloat() * FastMath.TWO_PI;
                float speed = 120f + random.nextFloat() * 150f;
                shard.velocity.set(FastMath.cos(angle) * speed,
                        FastMath.sin(angle) * speed + 90f,
                        0f);
                shard.duration = 0.62f + random.nextFloat() * 0.2f;
                shard.spin = (random.nextFloat() - 0.5f) * 10f;
                shard.shard.setLocalTranslation(shard.pos);
                effectsNode.attachChild(shard.shard);
                shardEffects.add(shard);
            }
        }
    }

    private void spawnRainbowFlash(Set<Integer> cells) {
        for (Integer key : cells) {
            int row = key / BOARD_SIZE;
            int col = key % BOARD_SIZE;
            Vector3f pos = cellPos(row, col);
            ShardEffect sparkle = new ShardEffect();
            sparkle.shard = rect("rainbow-spark", 0, 0, 14f, 14f,
                    new ColorRGBA(random.nextFloat(), 0.55f + random.nextFloat() * 0.45f, 1f, 0.82f));
            sparkle.pos.set(pos.x + TILE * 0.5f, pos.y + TILE * 0.5f, 24f);
            sparkle.velocity.set((random.nextFloat() - 0.5f) * 100f,
                    90f + random.nextFloat() * 110f,
                    0f);
            sparkle.duration = 0.5f;
            sparkle.spin = 6f;
            sparkle.shard.setLocalTranslation(sparkle.pos);
            effectsNode.attachChild(sparkle.shard);
            shardEffects.add(sparkle);
        }
    }

    private ColorRGBA shardColor(Piece piece) {
        if (piece == null) return new ColorRGBA(0.9f, 0.78f, 0.55f, 0.95f);
        switch (piece.color) {
            case 1:
                return new ColorRGBA(0.32f, 0.18f, 0.11f, 0.95f);
            case 2:
                return new ColorRGBA(0.92f, 0.86f, 0.68f, 0.95f);
            case 3:
                return new ColorRGBA(0.95f, 0.82f, 0.45f, 0.95f);
            case 0:
            default:
                return new ColorRGBA(0.86f, 0.76f, 0.62f, 0.95f);
        }
    }

    private void finishBoardSettle() {
        busy = false;
        combo = 0;
        if (score >= targetScore) {
            status = textFor("targetReached");
            if (!levelCompleteAnnounced) {
                levelCompleteAnnounced = true;
                audio.playSfx("/music/sfxsound/yaay.mp3");
            }
        }
        if (moves <= 0) {
            status = score >= targetScore ? textFor("runComplete") : textFor("outOfMoves");
        }
        updateHud();
    }

    private void playClearSfx(boolean specialClear) {
        if (specialClear) {
            audio.playSfx("/music/sfxsound/mixymixy.mp3");
            lastClearSfxAt = sfxClock;
            return;
        }

        if (combo > 1) {
            if (sfxClock - lastComboVoiceAt >= 1.35f) {
                audio.playSfx("/music/sfxsound/combocombo.mp3");
                lastComboVoiceAt = sfxClock;
            }
            return;
        }

        if (sfxClock - lastClearSfxAt >= 0.22f) {
            audio.playSfx("/music/sfxsound/nomnom.mp3");
            lastClearSfxAt = sfxClock;
        }
    }

    private void consumeMove() {
        if (moves > 0) moves--;
    }

    private int activeColorCount() {
        int loaded = cookieTextures == null ? COLORS : cookieTextures.length;
        return Math.max(3, Math.min(config.colors, loaded));
    }

    private void removePassiveMatches() {
        for (int pass = 0; pass < BOARD_SIZE * BOARD_SIZE; pass++) {
            Set<Integer> matches = findMatches();
            if (matches.isEmpty()) return;

            for (Integer key : matches) {
                int row = key / BOARD_SIZE;
                int col = key % BOARD_SIZE;
                Piece piece = board[row][col];
                if (piece == null || piece.special != Special.NONE) continue;
                setPieceColor(piece, safeColorFor(row, col, piece.color));
            }
        }
    }

    private int safeColorFor(int row, int col, int currentColor) {
        int count = activeColorCount();
        int start = random.nextInt(count);
        for (int offset = 0; offset < count; offset++) {
            int color = (start + offset) % count;
            if (color == currentColor) continue;
            if (!wouldCreateMatch(row, col, color)) {
                return color;
            }
        }

        for (int color = 0; color < count; color++) {
            if (!wouldCreateMatch(row, col, color)) {
                return color;
            }
        }
        return currentColor;
    }

    private boolean wouldCreateMatch(int row, int col, int color) {
        int horizontal = 1;
        for (int c = col - 1; c >= 0 && colorAt(row, c) == color; c--) horizontal++;
        for (int c = col + 1; c < BOARD_SIZE && colorAt(row, c) == color; c++) horizontal++;
        if (horizontal >= 3) return true;

        int vertical = 1;
        for (int r = row - 1; r >= 0 && colorAt(r, col) == color; r--) vertical++;
        for (int r = row + 1; r < BOARD_SIZE && colorAt(r, col) == color; r++) vertical++;
        return vertical >= 3;
    }

    private int colorAt(int row, int col) {
        Piece piece = board[row][col];
        return piece == null ? -1 : piece.color;
    }

    private void setPieceColor(Piece piece, int color) {
        piece.color = color;
        Material mat = piece.tile.getMaterial();
        mat.setTexture("ColorMap", cookieTextures[color]);
        setAlpha(piece.tile, 1f);
    }

    private Set<Integer> findMatches() {
        Set<Integer> cells = new HashSet<Integer>();
        for (int r = 0; r < BOARD_SIZE; r++) {
            int start = 0;
            for (int c = 1; c <= BOARD_SIZE; c++) {
                if (c < BOARD_SIZE && sameColor(r, c, r, start)) continue;
                int len = c - start;
                if (len >= 3) {
                    for (int k = start; k < c; k++) cells.add(r * BOARD_SIZE + k);
                }
                start = c;
            }
        }
        for (int c = 0; c < BOARD_SIZE; c++) {
            int start = 0;
            for (int r = 1; r <= BOARD_SIZE; r++) {
                if (r < BOARD_SIZE && sameColor(r, c, start, c)) continue;
                int len = r - start;
                if (len >= 3) {
                    for (int k = start; k < r; k++) cells.add(k * BOARD_SIZE + c);
                }
                start = r;
            }
        }
        return cells;
    }

    private boolean createsMatch(int row, int col) {
        Piece p = board[row][col];
        if (p == null) return false;
        int color = p.color;
        int run = 1;
        for (int c = col - 1; c >= 0 && board[row][c] != null && board[row][c].color == color; c--) run++;
        for (int c = col + 1; c < BOARD_SIZE && board[row][c] != null && board[row][c].color == color; c++) run++;
        if (run >= 3) return true;
        run = 1;
        for (int r = row - 1; r >= 0 && board[r][col] != null && board[r][col].color == color; r--) run++;
        for (int r = row + 1; r < BOARD_SIZE && board[r][col] != null && board[r][col].color == color; r++) run++;
        return run >= 3;
    }

    private boolean sameColor(int r1, int c1, int r2, int c2) {
        Piece a = board[r1][c1];
        Piece b = board[r2][c2];
        return a != null && b != null && a.color == b.color;
    }

    private Special specialForCells(Set<Integer> cells) {
        int longestRow = 0;
        int longestCol = 0;
        for (int r = 0; r < BOARD_SIZE; r++) {
            int run = 0;
            for (int c = 0; c < BOARD_SIZE; c++) {
                if (cells.contains(r * BOARD_SIZE + c)) {
                    run++;
                    longestRow = Math.max(longestRow, run);
                } else {
                    run = 0;
                }
            }
        }
        for (int c = 0; c < BOARD_SIZE; c++) {
            int run = 0;
            for (int r = 0; r < BOARD_SIZE; r++) {
                if (cells.contains(r * BOARD_SIZE + c)) {
                    run++;
                    longestCol = Math.max(longestCol, run);
                } else {
                    run = 0;
                }
            }
        }

        if (longestRow >= 5 || longestCol >= 5) return Special.RAINBOW;
        if (longestRow == 4) return Special.ROCKET_H;
        if (longestCol == 4) return Special.ROCKET_V;
        if (longestRow >= 3 && longestCol >= 3) return Special.BOMB;
        return Special.NONE;
    }

    private int pickSpawnCell(Set<Integer> cells) {
        for (Integer key : cells) return key;
        return -1;
    }

    private Set<Integer> activationCells(int row, int col, Special special, int color) {
        Set<Integer> cells = new HashSet<Integer>();
        if (special == Special.ROCKET_H) {
            for (int c = 0; c < BOARD_SIZE; c++) cells.add(row * BOARD_SIZE + c);
        } else if (special == Special.ROCKET_V) {
            for (int r = 0; r < BOARD_SIZE; r++) cells.add(r * BOARD_SIZE + col);
        } else if (special == Special.BOMB) {
            for (int dr = -1; dr <= 1; dr++) {
                for (int dc = -1; dc <= 1; dc++) {
                    int rr = row + dr;
                    int cc = col + dc;
                    if (rr >= 0 && rr < BOARD_SIZE && cc >= 0 && cc < BOARD_SIZE) {
                        cells.add(rr * BOARD_SIZE + cc);
                    }
                }
            }
        } else if (special == Special.RAINBOW) {
            int target = color >= 0 ? color : random.nextInt(activeColorCount());
            for (int r = 0; r < BOARD_SIZE; r++) {
                for (int c = 0; c < BOARD_SIZE; c++) {
                    Piece p = board[r][c];
                    if (p != null && p.color == target) cells.add(r * BOARD_SIZE + c);
                }
            }
            cells.add(row * BOARD_SIZE + col);
        }
        return cells;
    }

    private void decorateSpecial(Piece p) {
        setTexture(p.specialOverlay, textureForSpecial(p.special));
        setAlpha(p.specialOverlay, 1f);
        setColor(p.glow, new ColorRGBA(1f, 0.85f, 0.25f, 0.45f));
    }

    private Texture textureForSpecial(Special special) {
        if (special == Special.ROCKET_H) return horizontalRocketTexture;
        if (special == Special.ROCKET_V) return verticalRocketTexture;
        if (special == Special.BOMB) return specialBombTexture;
        if (special == Special.RAINBOW) return rainbowTexture;
        return horizontalRocketTexture;
    }

    private String specialName(Special special) {
        if (language == Language.FR) {
            if (special == Special.RAINBOW) return "Cookie arc-en-ciel";
            if (special == Special.BOMB) return "Bombe";
            return "Fusee";
        }
        if (special == Special.RAINBOW) return "Rainbow cookie";
        if (special == Special.BOMB) return "Bomb cookie";
        return "Rocket cookie";
    }

    private String sfxForSpecial(Special special) {
        if (special == Special.ROCKET_H || special == Special.ROCKET_V) {
            return "/music/sfxsound/weerocket.mp3";
        }
        if (special == Special.RAINBOW) {
            return "/music/sfxsound/raimbowcookie1.mp3";
        }
        return "/music/sfxsound/yummypower.mp3";
    }

    private void pulse(Piece p, float scale) {
        p.scale = scale;
    }

    private float averageX(Set<Integer> cells) {
        float sum = 0f;
        for (Integer key : cells) sum += cellPos(key / BOARD_SIZE, key % BOARD_SIZE).x + TILE / 2f;
        return cells.isEmpty() ? boardX : sum / cells.size();
    }

    private float averageY(Set<Integer> cells) {
        float sum = 0f;
        for (Integer key : cells) sum += cellPos(key / BOARD_SIZE, key % BOARD_SIZE).y + TILE / 2f;
        return cells.isEmpty() ? boardY : sum / cells.size();
    }

    private void spawnScoreLabel(String value, float x, float y, boolean hot) {
        FloatLabel label = new FloatLabel();
        label.text = new BitmapText(font);
        label.text.setText(value);
        label.text.setSize(hot ? 31f : 25f);
        label.text.setColor(hot ? new ColorRGBA(0.58f, 1f, 0.78f, 1f) : new ColorRGBA(1f, 0.88f, 0.42f, 1f));
        label.text.setLocalTranslation(x - 30f, y, 20f);
        labels.add(label);
        effectsNode.attachChild(label.text);
    }

    private void updateHud() {
        titleText.setText("OREO CRUSH  L" + level);
        levelText.setText(textFor("level") + " " + level + " / " + LEVELS.length);
        scoreText.setText(textFor("score") + "  " + score + " / " + targetScore);
        moveText.setText(textFor("moves") + "  " + moves);
        comboText.setText(textFor("combo") + "  x" + Math.max(1, combo) + "    "
                + textFor("best") + " x" + Math.max(1, bestCombo));
        objectiveText.setText(wrapText(textFor("objective") + ": " + objectiveTextFor(config)
                + " " + textFor("specialNote"), 31));
        statusText.setText(wrapText(status, 30));
        restartText.setText(score >= targetScore ? textFor("nextLevel") : textFor("newBoard"));
    }

    private String objectiveTextFor(LevelConfig levelConfig) {
        return language == Language.FR ? levelConfig.objectiveFr : levelConfig.objectiveEn;
    }

    private String textFor(String key) {
        boolean fr = language == Language.FR;
        switch (key) {
            case "pickNeighbor":
                return fr ? "Choisis un cookie voisin." : "Pick a neighboring cookie.";
            case "noMatch":
                return fr ? "Aucune combinaison. Essaie un autre echange." : "No match. Try a different swap.";
            case "niceMatch":
                return fr ? "Belle combinaison" : "Nice match";
            case "levelStart":
                return fr ? "Niveau " + level + ". Aligne les cookies et cree des bonus."
                        : "Level " + level + ". Match cookies and build specials.";
            case "fired":
                return fr ? "declenchee" : "fired";
            case "specialFired":
                return fr ? "Bonus declenche" : "Special fired";
            case "created":
                return fr ? "cree" : "created";
            case "targetReached":
                return fr ? "Objectif atteint. Appuie sur NIVEAU SUIVANT."
                        : "Target reached. Press NEXT LEVEL.";
            case "runComplete":
                return fr ? "Partie terminee. Nouveau plateau ?" : "Run complete. New board?";
            case "outOfMoves":
                return fr ? "Plus de coups. Nouveau plateau ?" : "Out of moves. New board?";
            case "level":
                return fr ? "Niveau" : "Level";
            case "score":
                return fr ? "Score" : "Score";
            case "moves":
                return fr ? "Coups" : "Moves";
            case "combo":
                return fr ? "Combo" : "Combo";
            case "best":
                return fr ? "Meilleur" : "Best";
            case "objective":
                return fr ? "Objectif" : "Objective";
            case "specialNote":
                return fr ? "Les bonus utilisent les icones fusee, bombe et arc-en-ciel."
                        : "Specials use rocket, bomb, and rainbow icons.";
            case "nextLevel":
                return fr ? "NIVEAU SUIVANT" : "NEXT LEVEL";
            case "newBoard":
                return fr ? "NOUVEAU PLATEAU" : "NEW BOARD";
            default:
                return key;
        }
    }

    private String wrapText(String text, int maxChars) {
        if (text == null || text.isEmpty()) return "";

        StringBuilder wrapped = new StringBuilder();
        StringBuilder line = new StringBuilder();
        String[] words = text.split("\\s+");
        for (String word : words) {
            int nextLength = line.length() == 0 ? word.length() : line.length() + 1 + word.length();
            if (nextLength > maxChars && line.length() > 0) {
                if (wrapped.length() > 0) wrapped.append('\n');
                wrapped.append(line);
                line.setLength(0);
            }
            if (line.length() > 0) line.append(' ');
            line.append(word);
        }
        if (line.length() > 0) {
            if (wrapped.length() > 0) wrapped.append('\n');
            wrapped.append(line);
        }
        return wrapped.toString();
    }

    private void refreshAllTargets() {
        for (int r = 0; r < BOARD_SIZE; r++) {
            for (int c = 0; c < BOARD_SIZE; c++) {
                if (board[r][c] != null) setTarget(board[r][c], r, c);
            }
        }
    }

    private void setColor(Geometry g, ColorRGBA color) {
        Material mat = g.getMaterial();
        mat.setColor("Color", color);
    }

    private void setTexture(Geometry g, Texture texture) {
        Material mat = g.getMaterial();
        mat.setTexture("ColorMap", texture);
        setAlpha(g, 1f);
    }

    private void setAlpha(Geometry g, float alpha) {
        Material mat = g.getMaterial();
        MatParam param = mat.getParam("Color");
        ColorRGBA color = param != null && param.getValue() instanceof ColorRGBA
                ? ((ColorRGBA) param.getValue()).clone()
                : ColorRGBA.White.clone();
        color.a = Math.max(0f, Math.min(1f, alpha));
        mat.setColor("Color", color);
    }

    private float easeOut(float t) {
        float clamped = Math.max(0f, Math.min(1f, t));
        float inv = 1f - clamped;
        return 1f - inv * inv * inv;
    }

    private float easeIn(float t) {
        float clamped = Math.max(0f, Math.min(1f, t));
        return clamped * clamped * clamped;
    }

    @Override
    public void simpleUpdate(float tpf) {
        sfxClock += tpf;
        updateRocketEffects(tpf);
        updateShardEffects(tpf);
        for (int r = 0; r < BOARD_SIZE; r++) {
            for (int c = 0; c < BOARD_SIZE; c++) {
                Piece p = board[r][c];
                if (p == null) continue;
                if (p.clearing) {
                    p.clearAge += tpf;
                    float t = Math.min(1f, p.clearAge / 0.34f);
                    float pop = t < 0.42f
                            ? 1f + 0.22f * easeOut(t / 0.42f)
                            : 1.22f * (1f - easeIn((t - 0.42f) / 0.58f));
                    float alpha = 1f - easeIn(t);
                    p.tile.setLocalScale(Math.max(0.05f, pop));
                    p.glow.setLocalScale(1f + 0.35f * t);
                    p.specialOverlay.setLocalScale(Math.max(0.05f, pop));
                    setAlpha(p.tile, alpha);
                    setAlpha(p.glow, 0.65f * alpha);
                    setAlpha(p.specialOverlay, p.special == Special.NONE ? 0f : alpha);
                    continue;
                }

                Vector3f current = p.tile.getLocalTranslation();
                float distance = current.distance(p.target);
                float moveT = Math.min(1f, tpf * (distance > 120f ? 10f : 14f));
                moveT = easeOut(moveT);
                current.interpolateLocal(p.target, moveT);
                if (distance < 0.35f) current.set(p.target);
                p.tile.setLocalTranslation(current);
                float idlePulse = p.special == Special.NONE ? 1f
                        : 1f + 0.035f * FastMath.sin(timer.getTimeInSeconds() * 4.6f + p.wobbleSeed);
                p.scale = FastMath.interpolateLinear(Math.min(1f, tpf * 9f), p.scale, idlePulse);
                p.tile.setLocalScale(p.scale);
                setAlpha(p.tile, 1f);
                boolean selectedPiece = selectedRow == r && selectedCol == c;
                setAlpha(p.glow, selectedPiece ? 0.72f : p.special == Special.NONE ? 0f : 0.45f);
                setAlpha(p.specialOverlay, p.special == Special.NONE ? 0f : 1f);
                p.glow.setLocalTranslation(current.x - 6f, current.y - 6f, 1f);
                p.specialOverlay.setLocalTranslation(current.x, current.y, 4f);
                float overlayScale = p.special == Special.NONE
                        ? 1f
                        : 0.92f + 0.08f * FastMath.sin(timer.getTimeInSeconds() * 5f + p.wobbleSeed);
                p.specialOverlay.setLocalScale(overlayScale);
            }
        }

        for (int i = labels.size() - 1; i >= 0; i--) {
            FloatLabel label = labels.get(i);
            label.age += tpf;
            Vector3f pos = label.text.getLocalTranslation();
            label.text.setLocalTranslation(pos.x, pos.y + 42f * tpf, pos.z);
            label.text.setAlpha(Math.max(0f, 1f - label.age / 0.85f));
            if (label.age >= 0.85f) {
                effectsNode.detachChild(label.text);
                labels.remove(i);
            }
        }

        if (busy) {
            busyTimer -= tpf;
            if (busyTimer <= 0f && !pending.isEmpty()) {
                List<Runnable> copy = new ArrayList<Runnable>(pending);
                pending.clear();
                for (Runnable action : copy) action.run();
            }
        }
    }

    private void updateRocketEffects(float tpf) {
        for (int i = rocketEffects.size() - 1; i >= 0; i--) {
            RocketEffect effect = rocketEffects.get(i);
            effect.age += tpf;
            float t = Math.min(1f, effect.age / effect.duration);
            float eased = easeOut(t);
            Vector3f pos = effect.start.clone().interpolateLocal(effect.end, eased);
            effect.rocket.setLocalTranslation(pos);
            effect.rocket.setLocalScale(1f + 0.12f * FastMath.sin(t * FastMath.PI));
            float alpha = 1f - easeIn(Math.max(0f, (t - 0.72f) / 0.28f));
            setAlpha(effect.rocket, alpha);
            setAlpha(effect.beam, 0.55f * (1f - t * 0.65f));
            if (t >= 1f) {
                effectsNode.detachChild(effect.rocket);
                effectsNode.detachChild(effect.beam);
                rocketEffects.remove(i);
            }
        }
    }

    private void updateShardEffects(float tpf) {
        for (int i = shardEffects.size() - 1; i >= 0; i--) {
            ShardEffect shard = shardEffects.get(i);
            shard.age += tpf;
            shard.velocity.y -= 360f * tpf;
            shard.pos.x += shard.velocity.x * tpf;
            shard.pos.y += shard.velocity.y * tpf;
            shard.shard.setLocalTranslation(shard.pos);
            shard.shard.rotate(0f, 0f, shard.spin * tpf);
            float t = Math.min(1f, shard.age / shard.duration);
            setAlpha(shard.shard, 1f - easeIn(t));
            shard.shard.setLocalScale(1f + 0.45f * t);
            if (t >= 1f) {
                effectsNode.detachChild(shard.shard);
                shardEffects.remove(i);
            }
        }
    }
}
