package fr.varyon.vrpg.profession.forestier;

import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.server.core.event.events.ecs.InteractivelyPickupItemEvent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.item.ItemComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import fr.varyon.vrpg.config.VrpgConfig;
import fr.varyon.vrpg.rpg.PlayerAccount;
import fr.varyon.vrpg.rpg.Profession;
import fr.varyon.vrpg.rpg.ProfessionManager;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Random;
import java.util.UUID;

public final class ForestierPickupSystem extends EntityEventSystem<EntityStore, InteractivelyPickupItemEvent> {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final Random RANDOM = new Random();

    private final ProfessionManager professionManager;

    public ForestierPickupSystem(@Nonnull ProfessionManager professionManager) {
        super(InteractivelyPickupItemEvent.class);
        this.professionManager = professionManager;
    }

    @Nullable
    @Override
    public Query<EntityStore> getQuery() {
        return PlayerRef.getComponentType();
    }

    @Override
    public void handle(int index,
                       @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
                       @Nonnull Store<EntityStore> store,
                       @Nonnull CommandBuffer<EntityStore> commandBuffer,
                       @Nonnull InteractivelyPickupItemEvent event) {
        if (event.isCancelled()) return;

        ItemStack item = event.getItemStack();
        if (item == null) return;
        String itemId = item.getItemId();
        if (!ForestierXpTable.isFishItem(itemId)) return;

        Ref<EntityStore> ref = archetypeChunk.getReferenceTo(index);
        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        if (playerRef == null) return;
        UUID uuid = playerRef.getUuid();

        PlayerAccount acc = professionManager.getAccount(uuid);
        if (acc == null || !acc.isActive(Profession.FORESTIER)) return;

        boolean dbg = VrpgConfig.isDebugTalents();
        String dbgId = dbg ? "[" + uuid.toString().substring(0, 8) + "|Fish|" + itemId + "] " : null;

        // Node 7 ÔÇö P├¬che Miraculeuse : chance de doubler le poisson (5% par rang, max 25%)
        int fishRank = acc.getTalentRank(Profession.FORESTIER, "7");
        if (fishRank > 0 && RANDOM.nextDouble() < fishRank * 0.05) {
            if (dbg) LOGGER.atInfo().log(dbgId + "N7 PecheMiraculeuse PROC ÔÇö item=" + itemId);
            TransformComponent tcmp = store.getComponent(ref, TransformComponent.getComponentType());
            if (tcmp != null) {
                Vector3d dropPos = new Vector3d(
                    tcmp.getPosition().x,
                    tcmp.getPosition().y + 0.5,
                    tcmp.getPosition().z
                );
                try { dropItemNearPlayer(commandBuffer, itemId, dropPos); }
                catch (Exception e) { LOGGER.atWarning().withCause(e).log(dbgId + "N7 drop ERREUR"); }
            }
        }
    }

    private static void dropItemNearPlayer(@Nonnull ComponentAccessor<EntityStore> accessor,
                                           @Nonnull String itemId,
                                           @Nonnull Vector3d position) {
        ItemStack stack = new ItemStack(itemId, 1);
        if (stack.isEmpty() || !stack.isValid()) return;
        float vx = (RANDOM.nextFloat() - 0.5f) * 2.5f;
        float vz = (RANDOM.nextFloat() - 0.5f) * 2.5f;
        Holder<EntityStore> holder = ItemComponent.generateItemDrop(accessor, stack, position, new Vector3f(0f, 0f, 0f), vx, 3.25f, vz);
        if (holder == null) return;
        accessor.addEntity(holder, AddReason.SPAWN);
    }
}
