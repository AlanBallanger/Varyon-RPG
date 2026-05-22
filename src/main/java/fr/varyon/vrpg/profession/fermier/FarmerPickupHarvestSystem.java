package fr.varyon.vrpg.profession.fermier;

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
import com.hypixel.hytale.server.core.command.system.CommandManager;
import com.hypixel.hytale.server.core.console.ConsoleSender;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.ecs.InteractivelyPickupItemEvent;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.item.ItemComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import fr.varyon.vrpg.audio.TalentProcSounds;
import fr.varyon.vrpg.config.VrpgConfig;
import fr.varyon.vrpg.rpg.PlayerAccount;
import fr.varyon.vrpg.rpg.Profession;
import fr.varyon.vrpg.rpg.ProfessionManager;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class FarmerPickupHarvestSystem extends EntityEventSystem<EntityStore, InteractivelyPickupItemEvent> {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final long SICKLE_DEBOUNCE_MS = 50L;
    private static final Random RANDOM = new Random();
    private static final double[] ETERNAL_SEED_CHANCES = {0.005, 0.0075, 0.01, 0.0125, 0.015};
    private static final double[] GHOST_SEED_CHANCES   = {0.005, 0.0075, 0.01, 0.0125, 0.015};
    private static final double[] SNACK_CHANCES = {0.01, 0.0125, 0.015, 0.0175, 0.02};

    private final ProfessionManager professionManager;
    private final FarmerComboTracker comboTracker;
    private final GuardianCropManager guardianCropManager;
    private final ConcurrentHashMap<UUID, Long> lastSickleCheckMillis = new ConcurrentHashMap<>();

    public FarmerPickupHarvestSystem(@Nonnull ProfessionManager professionManager,
                                     @Nonnull FarmerComboTracker comboTracker,
                                     @Nonnull GuardianCropManager guardianCropManager) {
        super(InteractivelyPickupItemEvent.class);
        this.professionManager = professionManager;
        this.comboTracker = comboTracker;
        this.guardianCropManager = guardianCropManager;
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
        if (!FarmerXpTable.isCropHarvestItem(itemId)) return;

        Ref<EntityStore> ref = archetypeChunk.getReferenceTo(index);
        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        if (playerRef == null) return;
        UUID uuid = playerRef.getUuid();

        PlayerAccount acc = professionManager.getAccount(uuid);
        if (acc == null || !acc.isActive(Profession.FERMIER)) return;

        boolean dbg = VrpgConfig.isDebugTalents();
        String dbgId = dbg ? "[" + uuid.toString().substring(0, 8) + "|Pickup|" + itemId + "] " : null;

        long now = System.currentTimeMillis();

        int comboRank = acc.getTalentRank(Profession.FERMIER, "6");
        int comboCount = comboTracker.onCropHarvested(uuid);
        double comboPercent = comboRank > 0 ? (0.01 + (comboRank - 1) * 0.005) : 0.0;
        double comboBonus = comboRank > 0 ? comboCount * comboPercent : 0.0;
        if (dbg && comboRank > 0) LOGGER.atInfo().log(dbgId
            + "N6 CCombo combo=" + comboCount + " bonus=" + String.format("%.3f%%", comboBonus * 100));

        int xpRank = acc.getTalentRank(Profession.FERMIER, "1");
        double xpMult = 1.0 + xpRank * 0.05 + comboBonus;
        double finalXp = FarmerXpTable.BASE_HARVEST_XP * xpMult;
        if (dbg) LOGGER.atInfo().log(dbgId + "XP +" + finalXp
            + " (base=" + FarmerXpTable.BASE_HARVEST_XP + " × " + String.format("%.3f", xpMult) + ")");
        professionManager.addXp(uuid, Profession.FERMIER, finalXp, playerRef);

        TransformComponent tcmp = store.getComponent(ref, TransformComponent.getComponentType());
        if (tcmp == null) return;
        Vector3d dropPos = new Vector3d(
            tcmp.getPosition().x,
            tcmp.getPosition().y + 0.5,
            tcmp.getPosition().z
        );

        if (comboRank > 0 && comboCount > 0) {
            TalentProcSounds.playCombo(acc, Profession.FERMIER, comboRank, comboCount,
                playerRef, ref, commandBuffer, dropPos);
        }

        int lootRank = acc.getTalentRank(Profession.FERMIER, "0");
        if (lootRank > 0 && RANDOM.nextDouble() < lootRank * 0.05) {
            if (dbg) LOGGER.atInfo().log(dbgId + "N0 PaniersTropPleins PROC — item=" + itemId);
            TalentProcSounds.playLootDouble(acc, Profession.FERMIER, playerRef, ref, commandBuffer, dropPos);
            try { dropItemNearPlayer(commandBuffer, itemId, dropPos); }
            catch (Exception e) { LOGGER.atWarning().withCause(e).log(dbgId + "N0 drop ERREUR"); }
        }

        if (comboRank > 0 && comboCount > 0 && RANDOM.nextDouble() < comboBonus) {
            if (dbg) LOGGER.atInfo().log(dbgId + "N6 CCombo loot PROC — item=" + itemId);
            try { dropItemNearPlayer(commandBuffer, itemId, dropPos); }
            catch (Exception e) { LOGGER.atWarning().withCause(e).log(dbgId + "N6 loot ERREUR"); }
        }

        int ghostRank = acc.getTalentRank(Profession.FERMIER, "3");
        if (ghostRank > 0) {
            double chance = GHOST_SEED_CHANCES[Math.min(ghostRank, GHOST_SEED_CHANCES.length) - 1];
            if (RANDOM.nextDouble() < chance) {
                if (dbg) LOGGER.atInfo().log(dbgId + "N3 GrainesFantomatiques PROC");
                TalentProcSounds.playFantomatique(acc, Profession.FERMIER, playerRef, ref, commandBuffer, dropPos);
                try { dropItemNearPlayer(commandBuffer, "Plant_Seeds_Corrupted", dropPos); }
                catch (Exception e) { LOGGER.atWarning().withCause(e).log(dbgId + "N3 ERREUR"); }
            }
        }

        int grainRank = acc.getTalentRank(Profession.FERMIER, "5");
        if (grainRank > 0) {
            double chance = ETERNAL_SEED_CHANCES[Math.min(grainRank, ETERNAL_SEED_CHANCES.length) - 1];
            if (RANDOM.nextDouble() < chance) {
                String eternalSeedId = FarmerXpTable.resolveEternalSeedFromItem(itemId);
                if (dbg) LOGGER.atInfo().log(dbgId + "N5 GrainsSansFin PROC — seed=" + eternalSeedId);
                if (eternalSeedId != null) {
                    TalentProcSounds.playTalent(acc, Profession.FERMIER, "5", TalentProcSounds.IMMORTEL_SOUND_ID,
                        playerRef, ref, commandBuffer, dropPos);
                    try { dropItemNearPlayer(commandBuffer, eternalSeedId, dropPos); }
                    catch (Exception e) { LOGGER.atWarning().withCause(e).log(dbgId + "N5 ERREUR"); }
                }
            }
        }

        int guardianRank = acc.getTalentRank(Profession.FERMIER, "11");
        if (guardianRank > 0) {
            double guardianChance = guardianRank * 0.05;
            if (RANDOM.nextDouble() < guardianChance) {
                if (dbg) LOGGER.atInfo().log(dbgId + "N11 GardienDesChamps PROC — pos=" + dropPos
                    + " chance=" + String.format("%.1f%%", guardianChance * 100));
                try {
                    Player player = playerRef.getComponent(Player.getComponentType());
                    World world = player != null ? player.getWorld() : null;
                    if (world != null) {
                        boolean soundOn = acc.isTalentSoundEnabled(Profession.FERMIER, "11");
                        FarmerGuardianSpawner.scheduleSpawn(guardianCropManager, world, dropPos, soundOn);
                    }
                } catch (Exception e) {
                    LOGGER.atWarning().withCause(e).log(dbgId + "N11 GardienDesChamps schedule ERREUR");
                }
            }
        }

        int snackRank = acc.getTalentRank(Profession.FERMIER, "9");
        if (snackRank > 0) {
            double snackChance = SNACK_CHANCES[Math.min(snackRank, SNACK_CHANCES.length) - 1];
            if (RANDOM.nextDouble() < snackChance) {
                String username = playerRef.getUsername();
                if (username != null) {
                    String cmd = RANDOM.nextBoolean() ? "hset " + username + " 100" : "wset " + username + " 100";
                    if (dbg) LOGGER.atInfo().log(dbgId + "N9 CasseCroute PROC — cmd=" + cmd);
                    TalentProcSounds.playTalent(acc, Profession.FERMIER, "9", TalentProcSounds.REFEED_SOUND_ID,
                        playerRef, ref, commandBuffer, dropPos);
                    try { CommandManager.get().handleCommand(ConsoleSender.INSTANCE, cmd); }
                    catch (Exception e) { LOGGER.atWarning().withCause(e).log(dbgId + "N9 ERREUR"); }
                }
            }
        }

        Long lastSickleMs = lastSickleCheckMillis.get(uuid);
        boolean firstSickleCheck = (lastSickleMs == null || now - lastSickleMs > SICKLE_DEBOUNCE_MS);
        if (firstSickleCheck) {
            lastSickleCheckMillis.put(uuid, now);
            Inventory inventory = null;
            try {
                Player player = playerRef.getComponent(Player.getComponentType());
                if (player != null) inventory = player.getInventory();
            } catch (Exception ignored) {}
            if (inventory != null) {
                try {
                    byte slot = inventory.getActiveHotbarSlot();
                    ItemStack held = inventory.getHotbar().getItemStack((short) slot);
                    String heldId = held != null ? held.getItemId() : null;
                    if (dbg) LOGGER.atInfo().log(dbgId + "N8 item tenu=" + heldId
                        + " maxDura=" + (held != null ? held.getMaxDurability() : "null"));
                    boolean isSickle = heldId != null && heldId.toLowerCase().contains("sickle");
                    if (isSickle && held.getMaxDurability() > 0) {
                        int faucilleRank = acc.getTalentRank(Profession.FERMIER, "8");
                        boolean proc = faucilleRank > 0 && RANDOM.nextDouble() < faucilleRank * 0.15;
                        double delta = proc ? 0.0 : -1.0;
                        if (dbg) {
                            double duraBefore = held.getDurability();
                            double duraMax = held.getMaxDurability();
                            String action = proc ? "N8 FaucilleEternelle PROC (annulé)" : "dura normale (-1)";
                            if (delta != 0.0) {
                                ItemStack after = held.withIncreasedDurability(delta);
                                LOGGER.atInfo().log(dbgId + action + " rank=" + faucilleRank
                                    + " dura=" + duraBefore + "/" + duraMax
                                    + " → " + after.getDurability() + "/" + after.getMaxDurability());
                                inventory.getHotbar().setItemStackForSlot((short) slot, after);
                            } else {
                                LOGGER.atInfo().log(dbgId + action + " rank=" + faucilleRank
                                    + " dura=" + duraBefore + "/" + duraMax + " (inchangé)");
                            }
                        } else if (delta != 0.0) {
                            inventory.getHotbar().setItemStackForSlot((short) slot,
                                held.withIncreasedDurability(delta));
                        }
                    } else if (dbg && held != null && held.getMaxDurability() > 0) {
                        LOGGER.atInfo().log(dbgId + "N8 ignoré — item=" + heldId + " ne contient pas 'sickle'");
                    }
                } catch (Exception ignored) {}
            }
        }
    }

    public void removePlayer(@Nonnull UUID uuid) {
        lastSickleCheckMillis.remove(uuid);
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
