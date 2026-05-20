package fr.varyon.vrpg.profession.forestier;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.modules.entitystats.modifier.Modifier;
import com.hypixel.hytale.server.core.modules.entitystats.modifier.StaticModifier;
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
 * Poumons de Loutre (nœud 14, Forestier) — passive oxygen boost.
 *
 * Rank → max oxygen multiplier :
 *   1 → ×1.2 (+20%)
 *   2 → ×1.4 (+40%)
 *   3 → ×1.6 (+60%)
 *   4 → ×1.8 (+80%)
 *   5 → ×2.0 (+100% — doubled)
 */
public final class PoumonLoutreTickSystem extends EntityTickingSystem<EntityStore> {

    private static final int CHECK_INTERVAL = 20;
    private static final String NODE_ID = "14";
    private static final String MODIFIER_KEY = "poumon_loutre_oxygen";

    private final ProfessionManager professionManager;
    private final ComponentType<EntityStore, PlayerRef> playerRefType = PlayerRef.getComponentType();
    private final Map<UUID, Integer> tickCounters = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> appliedRanks = new ConcurrentHashMap<>();

    public PoumonLoutreTickSystem(ProfessionManager professionManager) {
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
            if (acc == null || !acc.isActive(Profession.FORESTIER)) {
                clearModifier(uuid, chunk, index, store);
                return;
            }

            int rank = acc.getTalentRank(Profession.FORESTIER, NODE_ID);
            Integer lastApplied = appliedRanks.get(uuid);

            if (rank == 0) {
                if (lastApplied != null) clearModifier(uuid, chunk, index, store);
                return;
            }

            if (lastApplied != null && lastApplied == rank) return;

            applyModifier(uuid, rank, chunk, index, store);
        } catch (Exception ignored) {}
    }

    private void applyModifier(UUID uuid, int rank,
                                ArchetypeChunk<EntityStore> chunk, int index,
                                Store<EntityStore> store) {
        try {
            Ref<EntityStore> ref = chunk.getReferenceTo(index);
            EntityStatMap statMap = store.getComponent(ref, EntityStatMap.getComponentType());
            if (statMap == null) return;

            float multiplier = 1.0f + rank * 0.2f;
            statMap.putModifier(
                DefaultEntityStatTypes.getOxygen(),
                MODIFIER_KEY,
                new StaticModifier(Modifier.ModifierTarget.MAX, StaticModifier.CalculationType.MULTIPLICATIVE, multiplier)
            );
            appliedRanks.put(uuid, rank);
        } catch (Exception ignored) {}
    }

    private void clearModifier(UUID uuid,
                                ArchetypeChunk<EntityStore> chunk, int index,
                                Store<EntityStore> store) {
        try {
            Ref<EntityStore> ref = chunk.getReferenceTo(index);
            EntityStatMap statMap = store.getComponent(ref, EntityStatMap.getComponentType());
            if (statMap == null) return;
            statMap.removeModifier(DefaultEntityStatTypes.getOxygen(), MODIFIER_KEY);
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
        return playerRefType;
    }
}
