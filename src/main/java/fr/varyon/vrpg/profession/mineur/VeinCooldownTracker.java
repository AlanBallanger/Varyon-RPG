package fr.varyon.vrpg.profession.mineur;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class VeinCooldownTracker {

    private static final long[] COOLDOWNS_MS = {260_000L, 200_000L, 140_000L, 100_000L, 60_000L};

    private final ConcurrentHashMap<UUID, Long> lastVeinMs = new ConcurrentHashMap<>();

    public boolean tryTrigger(UUID uuid, int rank) {
        if (rank < 1 || rank > 5) return false;
        long cd = COOLDOWNS_MS[rank - 1];
        long now = System.currentTimeMillis();
        Long last = lastVeinMs.get(uuid);
        if (last != null && now - last < cd) return false;
        lastVeinMs.put(uuid, now);
        return true;
    }

    public void remove(UUID uuid) {
        lastVeinMs.remove(uuid);
    }

    public void clear() {
        lastVeinMs.clear();
    }
}
