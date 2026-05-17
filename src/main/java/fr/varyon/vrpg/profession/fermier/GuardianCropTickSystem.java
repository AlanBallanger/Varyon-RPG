package fr.varyon.vrpg.profession.fermier;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nullable;

public final class GuardianCropTickSystem extends EntityTickingSystem<EntityStore> {

    private final GuardianCropManager guardianCropManager;
    private final ComponentType<EntityStore, PlayerRef> playerRefType = PlayerRef.getComponentType();

    public GuardianCropTickSystem(GuardianCropManager guardianCropManager) {
        this.guardianCropManager = guardianCropManager;
    }

    @Override
    public void tick(float deltaTime, int index,
                     ArchetypeChunk<EntityStore> chunk,
                     Store<EntityStore> store,
                     CommandBuffer<EntityStore> commandBuffer) {
        guardianCropManager.drainRepops();
    }

    @Nullable
    @Override
    public Query<EntityStore> getQuery() {
        return playerRefType;
    }
}
