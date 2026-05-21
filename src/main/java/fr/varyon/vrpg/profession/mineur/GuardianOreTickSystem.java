package fr.varyon.vrpg.profession.mineur;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.modules.entitystats.modifier.Modifier;
import com.hypixel.hytale.server.core.modules.entitystats.modifier.StaticModifier;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.ParticleUtil;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.NPCPlugin;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class GuardianOreTickSystem extends EntityTickingSystem<EntityStore> {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final int CHECK_INTERVAL = 60;
    private static final double PARTICLE_RANGE_SQ = 32.0 * 32.0;

    private final GuardianStoneManager guardianManager;
    private final ComponentType<EntityStore, PlayerRef> playerRefType = PlayerRef.getComponentType();
    private final Map<UUID, Integer> tickCounters = new ConcurrentHashMap<>();

    public GuardianOreTickSystem(GuardianStoneManager guardianManager) {
        this.guardianManager = guardianManager;
    }

    @Override
    public void tick(float deltaTime, int index,
                     ArchetypeChunk<EntityStore> chunk,
                     Store<EntityStore> store,
                     CommandBuffer<EntityStore> commandBuffer) {

        guardianManager.drainRepops();

        PlayerRef playerRef = chunk.getComponent(index, playerRefType);
        if (playerRef == null) return;

        UUID uuid = playerRef.getUuid();
        Vector3d spawnPos = guardianManager.pollGuardianSpawn(uuid);
        if (spawnPos != null) {
            scheduleGuardianSpawn(store, spawnPos);
        }

        int tc = tickCounters.merge(uuid, 1, Integer::sum);
        if (tc % CHECK_INTERVAL != 0) return;

        Set<String> positions = guardianManager.getPositions();
        if (positions.isEmpty()) return;

        try {
            Ref<EntityStore> ref = chunk.getReferenceTo(index);
            TransformComponent tc2 = store.getComponent(ref, TransformComponent.getComponentType());
            if (tc2 == null) return;
            Vector3d playerPos = tc2.getPosition();

            for (String posKey : positions) {
                String[] parts = posKey.split(",");
                double gx = Double.parseDouble(parts[0]) + 0.5;
                double gy = Double.parseDouble(parts[1]) + 0.5;
                double gz = Double.parseDouble(parts[2]) + 0.5;

                double dx = playerPos.x - gx;
                double dy = playerPos.y - gy;
                double dz = playerPos.z - gz;
                if (dx * dx + dy * dy + dz * dz > PARTICLE_RANGE_SQ) continue;

                ParticleUtil.spawnParticleEffect("Varyon_OreGlow_Crystal",
                        new Vector3d(gx, gy, gz), store);
            }
        } catch (Exception ignored) {}
    }

    private static void scheduleGuardianSpawn(@Nonnull Store<EntityStore> store, @Nonnull Vector3d pos) {
        World world = store.getExternalData().getWorld();
        if (world == null) {
            LOGGER.atWarning().log("[GardienPierre] world null — spawn Golem_Crystal_Earth annulé x="
                    + pos.x + " y=" + pos.y + " z=" + pos.z);
            return;
        }
        final double x = pos.x;
        final double y = pos.y;
        final double z = pos.z;
        world.execute(() -> {
            try {
                Store<EntityStore> entityStore = world.getEntityStore().getStore();
                spawnGuardianGolem(entityStore, new Vector3d(x, y, z));
            } catch (Exception e) {
                LOGGER.atWarning().withCause(e).log("[GardienPierre] spawn Golem_Crystal_Earth ERREUR x="
                        + x + " y=" + y + " z=" + z);
            }
        });
    }

    private static void spawnGuardianGolem(@Nonnull Store<EntityStore> store, @Nonnull Vector3d pos) {
        try {
            var pair = NPCPlugin.get().spawnNPC(store, "Golem_Crystal_Earth", null, pos, new Vector3f(0f, 0f, 0f));
            if (pair == null) {
                LOGGER.atWarning().log("[GardienPierre] spawnNPC null — role 'Golem_Crystal_Earth' introuvable x="
                        + pos.x + " y=" + pos.y + " z=" + pos.z);
                return;
            }
            Ref<EntityStore> golemRef = pair.left();
            EntityStatMap statMap = store.getComponent(golemRef, EntityStatMap.getComponentType());
            if (statMap == null) {
                LOGGER.atWarning().log("[GardienPierre] EntityStatMap null sur Golem_Crystal_Earth");
                return;
            }
            int healthIdx = DefaultEntityStatTypes.getHealth();
            statMap.putModifier(healthIdx, "guardian_hp_bonus",
                    new StaticModifier(Modifier.ModifierTarget.MAX, StaticModifier.CalculationType.ADDITIVE, 160f));
            statMap.maximizeStatValue(healthIdx);
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("[GardienPierre] spawn Golem_Crystal_Earth ERREUR x="
                    + pos.x + " y=" + pos.y + " z=" + pos.z);
        }
    }

    @Nullable
    @Override
    public Query<EntityStore> getQuery() {
        return playerRefType;
    }
}
