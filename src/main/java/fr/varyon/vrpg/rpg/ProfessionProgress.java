package fr.varyon.vrpg.rpg;

public final class ProfessionProgress {

    private final Profession profession;
    private int level;
    private long xpInLevel;

    public ProfessionProgress(Profession profession, int level, long xpInLevel) {
        this.profession = profession;
        this.level = Math.max(1, Math.min(XpCurve.MAX_LEVEL, level));
        this.xpInLevel = Math.max(0L, xpInLevel);
    }

    public static ProfessionProgress freshLevel1(Profession profession) {
        return new ProfessionProgress(profession, 1, 0L);
    }

    public Profession getProfession() { return profession; }
    public int getLevel()              { return level; }
    public long getXpInLevel()         { return xpInLevel; }

    public long getXpToNextLevel() {
        return XpCurve.xpForLevel(level);
    }

    public boolean isMaxLevel() {
        return level >= XpCurve.MAX_LEVEL;
    }

    public int addXp(long amount) {
        if (amount <= 0L) return 0;
        int levelsGained = 0;
        long remaining = amount;
        while (remaining > 0L && level < XpCurve.MAX_LEVEL) {
            long needed = XpCurve.xpForLevel(level) - xpInLevel;
            if (remaining < needed) {
                xpInLevel += remaining;
                remaining = 0L;
            } else {
                remaining -= needed;
                xpInLevel = 0L;
                level++;
                levelsGained++;
            }
        }
        if (level >= XpCurve.MAX_LEVEL) {
            xpInLevel = 0L;
        }
        return levelsGained;
    }

    public void setLevel(int newLevel, long newXpInLevel) {
        this.level = Math.max(1, Math.min(XpCurve.MAX_LEVEL, newLevel));
        this.xpInLevel = Math.max(0L, newXpInLevel);
        if (this.level >= XpCurve.MAX_LEVEL) {
            this.xpInLevel = 0L;
        }
    }
}
