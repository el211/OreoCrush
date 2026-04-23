package dev.oreo;

public class GameStats {
    public final int level;
    public final int moves;
    public final int score;
    public final int targetScore;
    public final String objectiveTitle;
    public final String objectiveProgress;
    public final int combo;
    public final int bestCombo;
    public final int stars;
    public final int bestStars;
    public final int unlockedLevel;
    public final int totalStars;
    public final String statusText;

    public GameStats(int level,
                     int moves,
                     int score,
                     int targetScore,
                     String objectiveTitle,
                     String objectiveProgress,
                     int combo,
                     int bestCombo,
                     int stars,
                     int bestStars,
                     int unlockedLevel,
                     int totalStars,
                     String statusText) {
        this.level = level;
        this.moves = moves;
        this.score = score;
        this.targetScore = targetScore;
        this.objectiveTitle = objectiveTitle;
        this.objectiveProgress = objectiveProgress;
        this.combo = combo;
        this.bestCombo = bestCombo;
        this.stars = stars;
        this.bestStars = bestStars;
        this.unlockedLevel = unlockedLevel;
        this.totalStars = totalStars;
        this.statusText = statusText;
    }
}
