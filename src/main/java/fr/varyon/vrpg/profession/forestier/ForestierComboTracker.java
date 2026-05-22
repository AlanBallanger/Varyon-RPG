package fr.varyon.vrpg.profession.forestier;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ForestierComboTracker {

    public static final int MAX_COMBO = 10;
    public static final long COMBO_TIMEOUT_MS = 5_000L;

    private static final class State {
        int count;
        long lastChopMs;
    }

    private final ConcurrentHashMap<UUID, State> states = new ConcurrentHashMap<>();

    public int onLogChopped(UUID uuid) {
        long now = System.currentTimeMillis();
        State s = states.computeIfAbsent(uuid, k -> new State());
        if (now - s.lastChopMs > COMBO_TIMEOUT_MS) {
            s.count = 0;
        }
        s.count = s.count >= MAX_COMBO ? 0 : s.count + 1;
        s.lastChopMs = now;
        return s.count;
    }

    public void remove(UUID uuid) {
        states.remove(uuid);
    }

    public void clear() {
        states.clear();
    }
}
