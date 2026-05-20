package fr.varyon.vrpg.profession.forestier;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageCause;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageEventSystem;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nullable;

public final class RodeurSylvestreFallSystem extends DamageEventSystem {

    private final RodeurSylvestreTickSystem tickSystem;
    private final ComponentType<EntityStore, PlayerRef> playerRefType = PlayerRef.getComponentType();

    public RodeurSylvestreFallSystem(RodeurSylvestreTickSystem tickSystem) {
        this.tickSystem = tickSystem;
    }

    @Override
    public void handle(int index,
                       ArchetypeChunk<EntityStore> chunk,
                       Store<EntityStore> store,
                       CommandBuffer<EntityStore> commandBuffer,
                       Damage damage) {
        try {
            DamageCause cause = damage.getCause();
            if (cause == null || !"Fall".equals(cause.getId())) return;

            PlayerRef playerRef = chunk.getComponent(index, playerRefType);
            if (playerRef == null) return;

            int rank = tickSystem.getActiveRank(playerRef.getUuid());
            if (rank == 0) return;

            float reduction = rank * 0.10f;
            damage.setAmount(damage.getAmount() * (1.0f - reduction));
        } catch (Exception ignored) {}
    }

    @Nullable
    @Override
    public Query<EntityStore> getQuery() {
        return playerRefType;
    }
}
