package fr.varyon.vrpg.rpg;

public final class XpBoost {

    private final int tier;
    private final double bonus;
    private volatile long expiryMs;

    public XpBoost(int tier, double bonus, long expiryMs) {
        this.tier = tier;
        this.bonus = bonus;
        this.expiryMs = expiryMs;
    }

    public int getTier()       { return tier; }
    public double getBonus()   { return bonus; }
    public long getExpiryMs()  { return expiryMs; }
    public void setExpiryMs(long v) { this.expiryMs = v; }

    public boolean isActive() {
        return System.currentTimeMillis() < expiryMs;
    }

    public long getRemainingMs() {
        return Math.max(0L, expiryMs - System.currentTimeMillis());
    }
}
