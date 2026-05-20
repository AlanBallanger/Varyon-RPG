package fr.varyon.vrpg.profession.forestier;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.server.core.entity.entities.player.movement.MovementManager;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import fr.varyon.vrpg.rpg.PlayerAccount;
import fr.varyon.vrpg.rpg.Profession;
import fr.varyon.vrpg.rpg.ProfessionManager;

import javax.annotation.Nullable;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class RodeurSylvestreTickSystem extends EntityTickingSystem<EntityStore> {

    private static final int CHECK_INTERVAL = 20;
    private static final String NODE_ID = "6";

    private final ProfessionManager professionManager;
    private final ComponentType<EntityStore, PlayerRef> playerRefType = PlayerRef.getComponentType();
    private final Map<UUID, Integer> tickCounters = new ConcurrentHashMap<>();
    final Map<UUID, Integer> activeRanks = new ConcurrentHashMap<>();

    private boolean zoneReflectionInit = false;
    private boolean zoneReflectionAvailable = false;
    private Method getZoneAtPositionMethod;
    private Method getZoneNameMethod;
    private Object zoneConfig;

    public RodeurSylvestreTickSystem(ProfessionManager professionManager) {
        this.professionManager = professionManager;
    }

    public int getActiveRank(UUID uuid) {
        return activeRanks.getOrDefault(uuid, 0);
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
            PlayerAccount acc = professionManager.getAccount(uuid);
            if (acc == null || !acc.isActive(Profession.FORESTIER)) {
                deactivate(uuid, chunk, index, store);
                return;
            }

            int rank = acc.getTalentRank(Profession.FORESTIER, NODE_ID);
            if (rank == 0) {
                deactivate(uuid, chunk, index, store);
                return;
            }

            Ref<EntityStore> ref = chunk.getReferenceTo(index);
            TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
            boolean inForest = false;
            if (transform != null) {
                var pos = transform.getPosition();
                inForest = isInForestZone(pos.x, pos.z);
            }

            int prev = activeRanks.getOrDefault(uuid, 0);

            if (!inForest) {
                if (prev != 0) deactivate(uuid, chunk, index, store);
                return;
            }

            if (prev != rank) {
                activeRanks.put(uuid, rank);
                applySpeedBoost(rank, ref, store, playerRef);
            }
        } catch (Exception ignored) {}
    }

    private void applySpeedBoost(int rank, Ref<EntityStore> ref,
                                  Store<EntityStore> store, PlayerRef playerRef) {
        try {
            MovementManager mm = store.getComponent(ref, MovementManager.getComponentType());
            if (mm == null) return;

            float defaultSpeed = mm.getDefaultSettings().baseSpeed;
            mm.getSettings().baseSpeed = defaultSpeed * (1.0f + rank * 0.04f);
            mm.update(playerRef.getPacketHandler());
        } catch (Exception ignored) {}
    }

    private void deactivate(UUID uuid,
                             ArchetypeChunk<EntityStore> chunk, int index,
                             Store<EntityStore> store) {
        if (activeRanks.remove(uuid) != null) {
            try {
                Ref<EntityStore> ref = chunk.getReferenceTo(index);
                MovementManager mm = store.getComponent(ref, MovementManager.getComponentType());
                if (mm != null) mm.resetDefaultsAndUpdate(ref, store);
            } catch (Exception ignored) {}
        }
    }

    private boolean isInForestZone(double x, double z) {
        try {
            if (!zoneReflectionInit) initZoneReflection();
            if (!zoneReflectionAvailable) return false;

            Object zone = getZoneAtPositionMethod.invoke(null, x, z, zoneConfig);
            if (zone == null) return false;
            String name = (String) getZoneNameMethod.invoke(zone);
            return name != null && name.contains("Forest");
        } catch (Exception ignored) {}
        return false;
    }

    private synchronized void initZoneReflection() {
        if (zoneReflectionInit) return;
        zoneReflectionInit = true;
        try {
            Class<?> pluginClass = Class.forName("com.varyon.VaryonPlugin");
            Object varyonPlugin = pluginClass.getMethod("getInstance").invoke(null);
            if (varyonPlugin == null) return;

            Object configManager = pluginClass.getMethod("getConfigManager").invoke(varyonPlugin);
            if (configManager == null) return;

            zoneConfig = configManager.getClass().getMethod("getZoneConfig").invoke(configManager);
            if (zoneConfig == null) return;

            Class<?> zoneConfigClass = Class.forName("com.varyon.config.ZoneConfig");
            getZoneAtPositionMethod = Class.forName("com.varyon.util.ZoneCalculator")
                .getMethod("getZoneAtPosition", double.class, double.class, zoneConfigClass);
            getZoneNameMethod = Class.forName("com.varyon.config.DifficultyZone").getMethod("getName");
            zoneReflectionAvailable = true;
        } catch (Exception ignored) {}
    }

    public void removePlayer(UUID uuid) {
        tickCounters.remove(uuid);
        activeRanks.remove(uuid);
    }

    @Nullable
    @Override
    public Query<EntityStore> getQuery() {
        return playerRefType;
    }
}
