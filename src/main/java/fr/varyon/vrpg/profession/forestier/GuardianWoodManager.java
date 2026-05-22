package fr.varyon.vrpg.profession.forestier;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

import javax.annotation.Nonnull;

public final class GuardianWoodManager {

    private final Set<String> positions = ConcurrentHashMap.newKeySet();
    private final ConcurrentLinkedQueue<DelayedRepop> pendingDelayedRepops = new ConcurrentLinkedQueue<>();

    private static final class DelayedRepop {
        final long executeAtMs;
        final Runnable action;

        DelayedRepop(long executeAtMs, Runnable action) {
            this.executeAtMs = executeAtMs;
            this.action = action;
        }
    }

    private static String key(int x, int y, int z) {
        return x + "," + y + "," + z;
    }

    public boolean isGuardian(int x, int y, int z) {
        return positions.contains(key(x, y, z));
    }

    public Set<String> getPositions() {
        return Collections.unmodifiableSet(positions);
    }

    public void track(int x, int y, int z) {
        positions.add(key(x, y, z));
    }

    public void untrack(int x, int y, int z) {
        positions.remove(key(x, y, z));
    }

    public void queueDelayedRepop(@Nonnull Runnable action, long delayMs) {
        pendingDelayedRepops.add(new DelayedRepop(System.currentTimeMillis() + delayMs, action));
    }

    public void queueRepop(@Nonnull Runnable action) {
        queueDelayedRepop(action, 0L);
    }

    public void drainRepops() {
        long now = System.currentTimeMillis();
        int pending = pendingDelayedRepops.size();
        for (int i = 0; i < pending; i++) {
            DelayedRepop repop = pendingDelayedRepops.poll();
            if (repop == null) break;
            if (repop.executeAtMs <= now) {
                try { repop.action.run(); } catch (Exception ignored) {}
            } else {
                pendingDelayedRepops.add(repop);
            }
        }
    }

    public void clear() {
        positions.clear();
        pendingDelayedRepops.clear();
    }
}
