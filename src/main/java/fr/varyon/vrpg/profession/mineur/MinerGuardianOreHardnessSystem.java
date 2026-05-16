package fr.varyon.vrpg.profession.mineur;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.server.core.event.events.ecs.DamageBlockEvent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class MinerGuardianOreHardnessSystem extends EntityEventSystem<EntityStore, DamageBlockEvent> {

    public static final float DAMAGE_RECEIVED_FACTOR_VS_ADAMANTITE_MAGMA = 0.2f;

    private final ComponentType<EntityStore, PlayerRef> playerRefType = PlayerRef.getComponentType();

    public MinerGuardianOreHardnessSystem() {
        super(DamageBlockEvent.class);
    }

    @Override
    @Nullable
    public Query<EntityStore> getQuery() {
        return playerRefType;
    }

    @Override
    public void handle(int index,
            @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
            @Nonnull Store<EntityStore> store,
            @Nonnull CommandBuffer<EntityStore> commandBuffer,
            @Nonnull DamageBlockEvent event) {

        try {
            if (event.isCancelled()) return;
            String id = String.valueOf(event.getBlockType().getId());
            if (!GuardianStoneManager.GUARDIAN_BLOCK_ID.equals(id)) return;
            float d = event.getDamage();
            if (d <= 0f) return;
            event.setDamage(d * DAMAGE_RECEIVED_FACTOR_VS_ADAMANTITE_MAGMA);
        } catch (Exception ignored) {}
    }
}
