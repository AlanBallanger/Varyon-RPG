package fr.varyon.vrpg.profession.chasseur;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.protocol.ColorLight;
import com.hypixel.hytale.protocol.ComponentUpdateType;
import com.hypixel.hytale.protocol.DynamicLightUpdate;
import com.hypixel.hytale.server.core.modules.entity.tracker.EntityTrackerSystems;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import fr.varyon.vrpg.rpg.PlayerAccount;
import fr.varyon.vrpg.rpg.Profession;
import fr.varyon.vrpg.rpg.ProfessionManager;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Yeux de Lynx (nœud 9, Chasseur) — vision nocturne passive.
 *
 * Rank → rayon de lumière (byte) :
 *   1 → 10  (Vision faible)
 *   2 → 20  (Vision modérée)
 *   3 → 30  (Vision renforcée)
 *   4 → 40  (Vision avancée)
 *   5 → 50  (Vision parfaite)
 */
public final class YeuxLynxTickSystem extends EntityTickingSystem<EntityStore> {

    private static final int CHECK_INTERVAL = 20;
    private static final String NODE_ID = "9";
    private static final byte[] RADIUS_PER_RANK = {0, 10, 20, 30, 40, 50};

    private final ProfessionManager professionManager;
    private final ComponentType<EntityStore, PlayerRef> playerRefType = PlayerRef.getComponentType();
    private final ComponentType<EntityStore, EntityTrackerSystems.EntityViewer> viewerType =
            EntityTrackerSystems.EntityViewer.getComponentType();

    private final Map<UUID, Integer> tickCounters = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> appliedRanks = new ConcurrentHashMap<>();

    public YeuxLynxTickSystem(ProfessionManager professionManager) {
        this.professionManager = professionManager;
    }

    @Override
    public void tick(float deltaTime, int index,
                     ArchetypeChunk<EntityStore> chunk,
                     Store<EntityStore> store,
                     CommandBuffer<EntityStore> commandBuffer) {

        PlayerRef playerRef = chunk.getComponent(index, playerRefType);
        if (playerRef == null) return;

        UUID uuid = playerRef.getUuid();

        int tc = tickCounters.merge(uuid, 1, Integer::sum);
        if (tc % CHECK_INTERVAL != 0) return;

        try {
            EntityTrackerSystems.EntityViewer viewer = chunk.getComponent(index, viewerType);
            if (viewer == null) {
                appliedRanks.remove(uuid);
                return;
            }

            PlayerAccount acc = professionManager.getAccount(uuid);
            if (acc == null || !acc.isActive(Profession.CHASSEUR)) {
                clearLight(uuid, chunk, index, viewer);
                return;
            }

            int rank = acc.getTalentRank(Profession.CHASSEUR, NODE_ID);
            Integer lastApplied = appliedRanks.get(uuid);

            if (rank == 0) {
                if (lastApplied != null) clearLight(uuid, chunk, index, viewer);
                return;
            }

            if (lastApplied != null && lastApplied == rank) return;

            applyLight(uuid, rank, chunk, index, viewer);
        } catch (Exception ignored) {}
    }

    private void applyLight(UUID uuid, int rank,
                             ArchetypeChunk<EntityStore> chunk, int index,
                             EntityTrackerSystems.EntityViewer viewer) {
        try {
            Ref<EntityStore> ref = chunk.getReferenceTo(index);
            byte radius = RADIUS_PER_RANK[rank];
            DynamicLightUpdate update = new DynamicLightUpdate(new ColorLight(radius, (byte) 1, (byte) 1, (byte) 1));
            viewer.queueUpdate(ref, update);
            appliedRanks.put(uuid, rank);
        } catch (Exception ignored) {}
    }

    private void clearLight(UUID uuid,
                             ArchetypeChunk<EntityStore> chunk, int index,
                             EntityTrackerSystems.EntityViewer viewer) {
        try {
            Ref<EntityStore> ref = chunk.getReferenceTo(index);
            viewer.queueRemove(ref, ComponentUpdateType.DynamicLight);
            appliedRanks.remove(uuid);
        } catch (Exception ignored) {}
    }

    public void removePlayer(UUID uuid) {
        tickCounters.remove(uuid);
        appliedRanks.remove(uuid);
    }

    @Nullable
    @Override
    public Query<EntityStore> getQuery() {
        return Query.and(playerRefType, viewerType);
    }
}
