package fr.varyon.vrpg.profession.mineur;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

public final class GuardianStoneManager {

    public static final String GUARDIAN_BLOCK_ID = "Varyon_Guardian_Ore";

    private final Set<String> positions = ConcurrentHashMap.newKeySet();
    private final ConcurrentLinkedQueue<Runnable> pendingRepops = new ConcurrentLinkedQueue<>();

    private static String key(int x, int y, int z) {
        return x + "," + y + "," + z;
    }

    public void trackShellForParticles(int x, int y, int z) {
        positions.add(key(x, y, z));
    }

    public void untrackShell(int x, int y, int z) {
        positions.remove(key(x, y, z));
    }

    public Set<String> getPositions() {
        return Collections.unmodifiableSet(positions);
    }

    public void queueRepop(Runnable action) {
        pendingRepops.add(action);
    }

    public void drainRepops() {
        Runnable r;
        while ((r = pendingRepops.poll()) != null) {
            try { r.run(); } catch (Exception ignored) {}
        }
    }

    public void clear() {
        positions.clear();
        pendingRepops.clear();
    }
}
