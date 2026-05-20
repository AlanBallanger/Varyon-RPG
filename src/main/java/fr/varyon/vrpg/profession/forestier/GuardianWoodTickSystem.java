package fr.varyon.vrpg.profession.forestier;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.ParticleUtil;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class GuardianWoodTickSystem extends EntityTickingSystem<EntityStore> {

    private static final int CHECK_INTERVAL = 60;
    private static final double PARTICLE_RANGE_SQ = 32.0 * 32.0;

    private final GuardianWoodManager guardianWoodManager;
    private final ComponentType<EntityStore, PlayerRef> playerRefType = PlayerRef.getComponentType();
    private final Map<UUID, Integer> tickCounters = new ConcurrentHashMap<>();

    public GuardianWoodTickSystem(GuardianWoodManager guardianWoodManager) {
        this.guardianWoodManager = guardianWoodManager;
    }

    @Override
    public void tick(float deltaTime, int index,
                     ArchetypeChunk<EntityStore> chunk,
                     Store<EntityStore> store,
                     CommandBuffer<EntityStore> commandBuffer) {

        guardianWoodManager.drainRepops();

        PlayerRef playerRef = chunk.getComponent(index, playerRefType);
        if (playerRef == null) return;

        UUID uuid = playerRef.getUuid();
        int tc = tickCounters.merge(uuid, 1, Integer::sum);
        if (tc % CHECK_INTERVAL != 0) return;

        Set<String> positions = guardianWoodManager.getPositions();
        if (positions.isEmpty()) return;

        try {
            Ref<EntityStore> ref = chunk.getReferenceTo(index);
            TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
            if (transform == null) return;
            Vector3d playerPos = transform.getPosition();

            for (String posKey : positions) {
                String[] parts = posKey.split(",");
                double gx = Double.parseDouble(parts[0]) + 0.5;
                double gy = Double.parseDouble(parts[1]) + 0.5;
                double gz = Double.parseDouble(parts[2]) + 0.5;

                double dx = playerPos.x - gx;
                double dy = playerPos.y - gy;
                double dz = playerPos.z - gz;
                if (dx * dx + dy * dy + dz * dz > PARTICLE_RANGE_SQ) continue;

                ParticleUtil.spawnParticleEffect("Varyon_WoodGlow_Forest",
                        new Vector3d(gx, gy, gz), store);
            }
        } catch (Exception ignored) {}
    }

    @Nullable
    @Override
    public Query<EntityStore> getQuery() {
        return playerRefType;
    }
}
