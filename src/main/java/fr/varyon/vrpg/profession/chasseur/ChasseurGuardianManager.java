package fr.varyon.vrpg.profession.chasseur;

import java.util.concurrent.ConcurrentLinkedQueue;

import javax.annotation.Nonnull;

public final class ChasseurGuardianManager {

    private final ConcurrentLinkedQueue<DelayedRepop> pendingDelayedRepops = new ConcurrentLinkedQueue<>();

    private static final class DelayedRepop {
        final long executeAtMs;
        final Runnable action;

        DelayedRepop(long executeAtMs, Runnable action) {
            this.executeAtMs = executeAtMs;
            this.action = action;
        }
    }

    public void queueDelayedRepop(@Nonnull Runnable action, long delayMs) {
        pendingDelayedRepops.add(new DelayedRepop(System.currentTimeMillis() + delayMs, action));
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
        pendingDelayedRepops.clear();
    }
}
