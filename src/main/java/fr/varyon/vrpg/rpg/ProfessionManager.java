package fr.varyon.vrpg.rpg;

import com.hypixel.hytale.logger.HytaleLogger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;

public final class ProfessionManager {

    private static final HytaleLogger LOGGER = HytaleLogger.getLogger().getSubLogger("VaryonRPG");
    private static final long AUTOSAVE_SECONDS = 30L;
    public static final long RECONVERT_COOLDOWN_MS = 12L * 60L * 60L * 1000L;

    private final ProfessionStorage storage;

    private final ConcurrentHashMap<UUID, PlayerAccount> cache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, ReentrantLock> locks = new ConcurrentHashMap<>();
    private final Set<UUID> dirty = ConcurrentHashMap.newKeySet();
    private final ScheduledExecutorService scheduler;

    public ProfessionManager(@Nonnull Path dataDirectory) {
        this.storage = new SqliteProfessionStorage(dataDirectory);
        this.storage.initialize().join();
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "VaryonRPG-AutoSave");
            t.setDaemon(false);
            return t;
        });
        this.scheduler.scheduleAtFixedRate(this::flushDirty,
            AUTOSAVE_SECONDS, AUTOSAVE_SECONDS, TimeUnit.SECONDS);
        LOGGER.at(Level.INFO).log("ProfessionManager ready (%s, auto-save %ds)",
            storage.getName(), AUTOSAVE_SECONDS);
    }

    @Nonnull
    public ProfessionStorage getStorage() {
        return storage;
    }

    private ReentrantLock lockFor(UUID uuid) {
        return locks.computeIfAbsent(uuid, k -> new ReentrantLock());
    }

    public void ensureAccount(@Nonnull UUID uuid, @Nullable String playerName) {
        cache.computeIfAbsent(uuid, k -> storage.loadPlayer(k).join());
        if (playerName != null) {
            ReentrantLock lock = lockFor(uuid);
            lock.lock();
            try {
                PlayerAccount acc = cache.get(uuid);
                if (acc != null && !playerName.equals(acc.getPlayerName())) {
                    acc.setPlayerName(playerName);
                    dirty.add(uuid);
                }
            } finally {
                lock.unlock();
            }
        }
    }

    @Nullable
    public PlayerAccount getAccount(@Nonnull UUID uuid) {
        return cache.get(uuid);
    }

    @Nonnull
    public PlayerAccount getOrLoad(@Nonnull UUID uuid) {
        return cache.computeIfAbsent(uuid, k -> storage.loadPlayer(k).join());
    }

    public void markDirty(@Nonnull UUID uuid) {
        dirty.add(uuid);
    }

    public int addXp(@Nonnull UUID uuid, @Nonnull Profession profession, long amount) {
        if (amount <= 0L) return 0;
        ReentrantLock lock = lockFor(uuid);
        lock.lock();
        try {
            PlayerAccount acc = getOrLoad(uuid);
            int levelsGained = acc.getProgress(profession).addXp(amount);
            dirty.add(uuid);
            return levelsGained;
        } finally {
            lock.unlock();
        }
    }

    public void setLevel(@Nonnull UUID uuid, @Nonnull Profession profession, int level) {
        ReentrantLock lock = lockFor(uuid);
        lock.lock();
        try {
            PlayerAccount acc = getOrLoad(uuid);
            acc.getProgress(profession).setLevel(level, 0L);
            dirty.add(uuid);
        } finally {
            lock.unlock();
        }
    }

    public void resetProfession(@Nonnull UUID uuid, @Nonnull Profession profession) {
        ReentrantLock lock = lockFor(uuid);
        lock.lock();
        try {
            PlayerAccount acc = getOrLoad(uuid);
            acc.getProgress(profession).setLevel(1, 0L);
            acc.resetTalents(profession);
            dirty.add(uuid);
        } finally {
            lock.unlock();
        }
    }

    public void resetAccount(@Nonnull UUID uuid) {
        ReentrantLock lock = lockFor(uuid);
        lock.lock();
        try {
            PlayerAccount acc = getOrLoad(uuid);
            for (Profession p : Profession.values()) {
                acc.getProgress(p).setLevel(1, 0L);
                acc.resetTalents(p);
            }
            acc.setActiveSlot0(Profession.MINEUR);
            acc.setActiveSlot1(Profession.FERMIER);
            acc.setLastReconvertAt(0L);
            dirty.add(uuid);
        } finally {
            lock.unlock();
        }
    }

    public boolean allocateTalent(@Nonnull UUID uuid, @Nonnull Profession profession, @Nonnull String nodeId, int nodeMax) {
        ReentrantLock lock = lockFor(uuid);
        lock.lock();
        try {
            PlayerAccount acc = getOrLoad(uuid);
            int currentRank = acc.getTalentRank(profession, nodeId);
            if (currentRank >= nodeMax) return false;
            if (acc.availableTalentPoints(profession) <= 0) return false;
            acc.setTalentRank(profession, nodeId, currentRank + 1);
            dirty.add(uuid);
            return true;
        } finally {
            lock.unlock();
        }
    }

    public void resetTalents(@Nonnull UUID uuid, @Nonnull Profession profession) {
        ReentrantLock lock = lockFor(uuid);
        lock.lock();
        try {
            PlayerAccount acc = getOrLoad(uuid);
            acc.resetTalents(profession);
            dirty.add(uuid);
        } finally {
            lock.unlock();
        }
    }

    public enum ReconvertResult { SUCCESS, COOLDOWN_ACTIVE, INVALID_SLOT, NOT_UNLOCKED, SAME_PROFESSION, NO_CHANGE }

    public ReconvertResult setActiveSlot(@Nonnull UUID uuid, int slotIndex, @Nullable Profession newProfession) {
        if (slotIndex < 0 || slotIndex > 1) return ReconvertResult.INVALID_SLOT;
        ReentrantLock lock = lockFor(uuid);
        lock.lock();
        try {
            PlayerAccount acc = getOrLoad(uuid);
            Profession current = slotIndex == 0 ? acc.getActiveSlot0() : acc.getActiveSlot1();
            Profession other   = slotIndex == 0 ? acc.getActiveSlot1() : acc.getActiveSlot0();
            if (newProfession != null) {
                if (!acc.isUnlocked(newProfession)) return ReconvertResult.NOT_UNLOCKED;
                if (newProfession == other) return ReconvertResult.SAME_PROFESSION;
            }
            if (newProfession == current) return ReconvertResult.NO_CHANGE;

            long now = System.currentTimeMillis();
            long elapsed = now - acc.getLastReconvertAt();
            if (acc.getLastReconvertAt() > 0L && elapsed < RECONVERT_COOLDOWN_MS) {
                return ReconvertResult.COOLDOWN_ACTIVE;
            }

            if (slotIndex == 0) acc.setActiveSlot0(newProfession);
            else                acc.setActiveSlot1(newProfession);
            acc.setLastReconvertAt(now);
            dirty.add(uuid);
            return ReconvertResult.SUCCESS;
        } finally {
            lock.unlock();
        }
    }

    public long getReconvertCooldownRemainingMs(@Nonnull UUID uuid) {
        PlayerAccount acc = cache.get(uuid);
        if (acc == null || acc.getLastReconvertAt() == 0L) return 0L;
        long left = RECONVERT_COOLDOWN_MS - (System.currentTimeMillis() - acc.getLastReconvertAt());
        return Math.max(0L, left);
    }

    public void onPlayerDisconnect(@Nonnull UUID uuid) {
        PlayerAccount acc = cache.get(uuid);
        if (acc != null) {
            storage.savePlayer(uuid, acc);
            dirty.remove(uuid);
        }
        locks.remove(uuid);
        cache.remove(uuid);
    }

    private void flushDirty() {
        if (dirty.isEmpty()) return;
        Set<UUID> snapshot = new HashSet<>(dirty);
        dirty.clear();
        Map<UUID, PlayerAccount> map = new HashMap<>();
        for (UUID u : snapshot) {
            PlayerAccount acc = cache.get(u);
            if (acc != null) map.put(u, acc);
        }
        storage.saveAll(map).exceptionally(t -> {
            LOGGER.at(Level.WARNING).log("Auto-save failed, will retry next cycle: %s", t.getMessage());
            dirty.addAll(snapshot);
            return null;
        });
    }

    public void forceSave() {
        flushDirty();
    }

    public void shutdown() {
        LOGGER.at(Level.INFO).log("ProfessionManager shutdown starting...");
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
        }
        try {
            storage.saveAllSync(new HashMap<>(cache));
        } catch (Exception e) {
            LOGGER.at(Level.SEVERE).log("Final flush failed: %s", e.getMessage());
        }
        try {
            storage.shutdown().get(5, TimeUnit.SECONDS);
        } catch (Exception e) {
            LOGGER.at(Level.WARNING).log("Storage shutdown error: %s", e.getMessage());
        }
        cache.clear();
        locks.clear();
        dirty.clear();
        LOGGER.at(Level.INFO).log("ProfessionManager shutdown complete");
    }
}
