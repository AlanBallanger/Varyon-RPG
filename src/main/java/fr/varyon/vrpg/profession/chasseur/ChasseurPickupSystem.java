package fr.varyon.vrpg.profession.chasseur;

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

public final class ChasseurPickupSystem extends EntityEventSystem<EntityStore, InteractivelyPickupItemEvent> {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final Random RANDOM = new Random();

    private final ProfessionManager professionManager;
    private final ChasseurComboTracker comboTracker;

    public ChasseurPickupSystem(@Nonnull ProfessionManager professionManager,
                                 @Nonnull ChasseurComboTracker comboTracker) {
        super(InteractivelyPickupItemEvent.class);
        this.professionManager = professionManager;
        this.comboTracker = comboTracker;
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

        if (!ChasseurXpTable.isHuntingDrop(itemId)) return;

        Ref<EntityStore> ref = archetypeChunk.getReferenceTo(index);
        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        if (playerRef == null) return;
        UUID uuid = playerRef.getUuid();

        PlayerAccount acc = professionManager.getAccount(uuid);
        if (acc == null || !acc.isActive(Profession.CHASSEUR)) return;

        boolean dbg = VrpgConfig.isDebugTalents();
        String dbgId = dbg ? "[" + uuid.toString().substring(0, 8) + "|Pickup|" + itemId + "] " : null;

        TransformComponent tcmp = store.getComponent(ref, TransformComponent.getComponentType());
        if (tcmp == null) return;
        Vector3d dropPos = new Vector3d(
            tcmp.getPosition().x,
            tcmp.getPosition().y + 0.5,
            tcmp.getPosition().z
        );

        int comboRank = acc.getTalentRank(Profession.CHASSEUR, "5");
        int comboCount = comboTracker.getCurrentCombo(uuid);
        double comboPercent = comboRank > 0 ? (0.01 + (comboRank - 1) * 0.005) : 0.0;
        double comboBonus = comboRank > 0 ? comboCount * comboPercent : 0.0;

        if (ChasseurXpTable.isMeatHideFeather(itemId)) {
            int mhfRank = acc.getTalentRank(Profession.CHASSEUR, "0");
            double mhfChance = mhfRank * 0.05 + comboBonus;
            if (mhfRank > 0 && RANDOM.nextDouble() < mhfChance) {
                if (dbg) LOGGER.atInfo().log(dbgId + "N0 MainsBoucher PROC — item=" + itemId);
                try { dropItemNearPlayer(commandBuffer, itemId, dropPos); }
                catch (Exception e) { LOGGER.atWarning().withCause(e).log(dbgId + "N0 drop ERREUR"); }
            }
        }

        int lootRank = acc.getTalentRank(Profession.CHASSEUR, "4");
        double lootChance = lootRank * 0.05 + comboBonus;
        if (lootRank > 0 && RANDOM.nextDouble() < lootChance) {
            if (dbg) LOGGER.atInfo().log(dbgId + "N4 Depouillleur PROC — item=" + itemId);
            try { dropItemNearPlayer(commandBuffer, itemId, dropPos); }
            catch (Exception e) { LOGGER.atWarning().withCause(e).log(dbgId + "N4 drop ERREUR"); }
        }

        if (ChasseurXpTable.isExoticDrop(itemId)) {
            int exoRank = acc.getTalentRank(Profession.CHASSEUR, "7");
            if (exoRank > 0 && RANDOM.nextDouble() < exoRank * 0.05) {
                if (dbg) LOGGER.atInfo().log(dbgId + "N7 MateriauxExotiques PROC — item=" + itemId);
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
