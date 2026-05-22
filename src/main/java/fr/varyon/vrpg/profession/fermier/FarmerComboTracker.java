package fr.varyon.vrpg.profession.fermier;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class FarmerComboTracker {

    public static final int MAX_COMBO = 10;
    public static final long COMBO_TIMEOUT_MS = 5_000L;

    private static final class State {
        int count;
        long lastHarvestMs;
    }

    private final ConcurrentHashMap<UUID, State> states = new ConcurrentHashMap<>();

    public int getCurrentCombo(UUID uuid) {
        State s = states.get(uuid);
        if (s == null) return 0;
        long now = System.currentTimeMillis();
        return (now - s.lastHarvestMs <= COMBO_TIMEOUT_MS) ? s.count : 0;
    }

    public int onCropHarvested(UUID uuid) {
        long now = System.currentTimeMillis();
        State s = states.computeIfAbsent(uuid, k -> new State());
        if (now - s.lastHarvestMs > COMBO_TIMEOUT_MS) {
            s.count = 0;
        }
        s.count = s.count >= MAX_COMBO ? 0 : s.count + 1;
        s.lastHarvestMs = now;
        return s.count;
    }

    public void remove(UUID uuid) {
        states.remove(uuid);
    }

    public void clear() {
        states.clear();
    }
}
