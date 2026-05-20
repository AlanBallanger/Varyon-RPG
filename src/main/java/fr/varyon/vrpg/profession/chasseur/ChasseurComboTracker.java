package fr.varyon.vrpg.profession.chasseur;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ChasseurComboTracker {

    public static final int MAX_COMBO = 15;
    public static final long COMBO_TIMEOUT_MS = 15_000L;

    private static final class State {
        int count;
        long lastKillMs;
    }

    private final ConcurrentHashMap<UUID, State> states = new ConcurrentHashMap<>();

    public int onKill(UUID uuid) {
        long now = System.currentTimeMillis();
        State s = states.computeIfAbsent(uuid, k -> new State());
        if (now - s.lastKillMs > COMBO_TIMEOUT_MS) {
            s.count = 0;
        } else {
            s.count = Math.min(s.count + 1, MAX_COMBO);
        }
        s.lastKillMs = now;
        return s.count;
    }

    public int getCurrentCombo(UUID uuid) {
        State s = states.get(uuid);
        if (s == null) return 0;
        long now = System.currentTimeMillis();
        return (now - s.lastKillMs <= COMBO_TIMEOUT_MS) ? s.count : 0;
    }

    public void remove(UUID uuid) {
        states.remove(uuid);
    }

    public void clear() {
        states.clear();
    }
}
