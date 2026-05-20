package fr.varyon.vrpg.profession.forestier;

import com.hypixel.hytale.builtin.mounts.MountedComponent;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.protocol.BlockMountType;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatValue;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import fr.varyon.vrpg.rpg.PlayerAccount;
import fr.varyon.vrpg.rpg.Profession;
import fr.varyon.vrpg.rpg.ProfessionManager;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class LitDeFortuneTickSystem extends EntityTickingSystem<EntityStore> {

    private static final int CHECK_INTERVAL = 20;
    private static final String NODE_ID = "15";
    private static final float REGEN_PERCENT = 0.02f;

    private final ProfessionManager professionManager;
    private final ComponentType<EntityStore, PlayerRef> playerRefType = PlayerRef.getComponentType();
    private final ComponentType<EntityStore, MountedComponent> mountedType = MountedComponent.getComponentType();
    private final ComponentType<EntityStore, EntityStatMap> statMapType = EntityStatMap.getComponentType();

    private final Map<UUID, Integer> tickCounters = new ConcurrentHashMap<>();

    public LitDeFortuneTickSystem(ProfessionManager professionManager) {
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
            PlayerAccount acc = professionManager.getAccount(uuid);
            if (acc == null || !acc.isActive(Profession.FORESTIER)) return;

            int rank = acc.getTalentRank(Profession.FORESTIER, NODE_ID);
            if (rank == 0) return;

            MountedComponent mounted = chunk.getComponent(index, mountedType);
            if (mounted == null || mounted.getBlockMountType() != BlockMountType.Bed) return;

            Ref<EntityStore> ref = chunk.getReferenceTo(index);
            EntityStatMap statMap = store.getComponent(ref, statMapType);
            if (statMap == null) return;

            int healthIdx = DefaultEntityStatTypes.getHealth();
            EntityStatValue healthStat = statMap.get(healthIdx);
            if (healthStat == null) return;

            float maxHp = healthStat.getMax();
            if (maxHp <= 0) return;

            float current = healthStat.get();
            if (current >= maxHp) return;

            statMap.addStatValue(healthIdx, maxHp * REGEN_PERCENT);
        } catch (Exception ignored) {}
    }

    public void removePlayer(UUID uuid) {
        tickCounters.remove(uuid);
    }

    @Nullable
    @Override
    public Query<EntityStore> getQuery() {
        return Query.and(playerRefType, mountedType, statMapType);
    }
}
