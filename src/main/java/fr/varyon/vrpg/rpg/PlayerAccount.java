package fr.varyon.vrpg.rpg;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class PlayerAccount {

    private final UUID uuid;
    private volatile String playerName;
    private volatile Profession activeSlot0;
    private volatile Profession activeSlot1;
    private volatile long lastReconvertAt;

    private final EnumMap<Profession, ProfessionProgress> progress = new EnumMap<>(Profession.class);
    private final EnumMap<Profession, Map<String, Integer>> talents = new EnumMap<>(Profession.class);
    private final Map<String, Boolean> talentSoundEnabled = new HashMap<>();

    public PlayerAccount(@Nonnull UUID uuid, @Nullable String playerName) {
        this.uuid = uuid;
        this.playerName = playerName;
        for (Profession p : Profession.values()) {
            progress.put(p, ProfessionProgress.freshLevel1(p));
            talents.put(p, new HashMap<>());
        }
        this.activeSlot0 = Profession.MINEUR;
        this.activeSlot1 = Profession.FERMIER;
        this.lastReconvertAt = 0L;
    }

    @Nonnull public UUID getUuid()         { return uuid; }
    @Nullable public String getPlayerName(){ return playerName; }
    public void setPlayerName(@Nullable String name) { this.playerName = name; }

    @Nullable public Profession getActiveSlot0() { return activeSlot0; }
    @Nullable public Profession getActiveSlot1() { return activeSlot1; }
    public void setActiveSlot0(@Nullable Profession p) { this.activeSlot0 = p; }
    public void setActiveSlot1(@Nullable Profession p) { this.activeSlot1 = p; }

    public long getLastReconvertAt()        { return lastReconvertAt; }
    public void setLastReconvertAt(long t)  { this.lastReconvertAt = t; }

    public boolean isActive(@Nonnull Profession p) {
        return p == activeSlot0 || p == activeSlot1;
    }

    @Nonnull
    public ProfessionProgress getProgress(@Nonnull Profession p) {
        return progress.get(p);
    }

    @Nonnull
    public Map<Profession, ProfessionProgress> getAllProgress() {
        return progress;
    }

    @Nonnull
    public Map<String, Integer> getTalents(@Nonnull Profession p) {
        return talents.get(p);
    }

    public int getTalentRank(@Nonnull Profession p, @Nonnull String nodeId) {
        return talents.get(p).getOrDefault(nodeId, 0);
    }

    public void setTalentRank(@Nonnull Profession p, @Nonnull String nodeId, int rank) {
        if (rank <= 0) {
            talents.get(p).remove(nodeId);
        } else {
            talents.get(p).put(nodeId, rank);
        }
    }

    public int totalTalentRanks(@Nonnull Profession p) {
        int sum = 0;
        for (int r : talents.get(p).values()) sum += r;
        return sum;
    }

    public void resetTalents(@Nonnull Profession p) {
        talents.get(p).clear();
    }

    public boolean isUnlocked(@Nonnull Profession p) {
        if (p.isBase()) return true;
        Profession parent = p.getPrereq();
        return parent != null && progress.get(parent).getLevel() >= p.getPrereqLevel();
    }

    public int availableTalentPoints(@Nonnull Profession p) {
        int earned = XpCurve.talentPointsAtLevel(progress.get(p).getLevel());
        return Math.max(0, earned - totalTalentRanks(p));
    }

    @Nonnull
    public static String talentSoundKey(@Nonnull Profession p, @Nonnull String nodeId) {
        return p.getId() + ":" + nodeId;
    }

    public boolean isTalentSoundEnabled(@Nonnull Profession p, @Nonnull String nodeId) {
        return talentSoundEnabled.getOrDefault(talentSoundKey(p, nodeId), true);
    }

    public void setTalentSoundEnabled(@Nonnull Profession p, @Nonnull String nodeId, boolean enabled) {
        String key = talentSoundKey(p, nodeId);
        if (enabled) {
            talentSoundEnabled.remove(key);
        } else {
            talentSoundEnabled.put(key, false);
        }
    }

    @Nonnull
    public Map<String, Boolean> getTalentSoundPrefs() {
        return talentSoundEnabled;
    }

    public void applyTalentSoundPref(@Nonnull String key, boolean enabled) {
        if (enabled) {
            talentSoundEnabled.remove(key);
        } else {
            talentSoundEnabled.put(key, false);
        }
    }
}
