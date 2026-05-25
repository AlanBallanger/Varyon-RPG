package fr.varyon.vrpg.rpg;

public final class XpCurve {

    public static final int MAX_LEVEL = 30;

    private XpCurve() {}

    public static long xpForLevel(int level) {
        if (level >= MAX_LEVEL) return 0L;
        if (level < 1) level = 1;
        long l = level;
        return 50L * l * (75L + l * l) / 75L;
    }

    public static long cumulativeXp(int level, long xpInLevel) {
        long total = xpInLevel;
        for (int i = 1; i < Math.min(level, MAX_LEVEL); i++) {
            total += xpForLevel(i);
        }
        return total;
    }

    public static int talentPointsAtLevel(int level) {
        if (level <= 1) return 0;
        int points = 0;
        int cap = Math.min(level, MAX_LEVEL);
        for (int L = 2; L <= cap; L++) {
            switch (L) {
                case 10 -> points += 2;
                case 20 -> points += 3;
                case MAX_LEVEL -> points += 4;
                default -> points += 1;
            }
        }
        return points;
    }
}
