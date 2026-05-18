package fr.varyon.vrpg.profession.fermier;

import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.ecs.BreakBlockEvent;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.entity.item.ItemComponent;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.modules.entitystats.modifier.Modifier;
import com.hypixel.hytale.server.core.modules.entitystats.modifier.StaticModifier;
import com.hypixel.hytale.server.core.command.system.CommandManager;
import com.hypixel.hytale.server.core.console.ConsoleSender;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.NPCPlugin;
import fr.varyon.vrpg.config.VrpgConfig;
import fr.varyon.vrpg.rpg.PlayerAccount;
import fr.varyon.vrpg.rpg.Profession;
import fr.varyon.vrpg.rpg.ProfessionManager;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Random;
import java.util.UUID;

public final class FarmerBlockBreakSystem extends EntityEventSystem<EntityStore, BreakBlockEvent> {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final Random RANDOM = new Random();

    private static final double[] ETERNAL_SEED_CHANCES = {0.005, 0.0075, 0.01, 0.0125, 0.015};
    private static final double[] SNACK_CHANCES = {0.01, 0.0125, 0.015, 0.0175, 0.02};

    private final ProfessionManager professionManager;
    private final FarmerComboTracker comboTracker;
    private final GuardianCropManager guardianCropManager;
    private final ComponentType<EntityStore, PlayerRef> playerRefType = PlayerRef.getComponentType();

    public FarmerBlockBreakSystem(@Nonnull ProfessionManager professionManager,
                                   @Nonnull FarmerComboTracker comboTracker,
                                   @Nonnull GuardianCropManager guardianCropManager) {
        super(BreakBlockEvent.class);
        this.professionManager = professionManager;
        this.comboTracker = comboTracker;
        this.guardianCropManager = guardianCropManager;
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
            @Nonnull BreakBlockEvent event) {

        BlockType blockType = event.getBlockType();
        if (blockType == null) return;

        boolean dbg = VrpgConfig.isDebugTalents();

        String rawId = String.valueOf(blockType.getId());
        String id = rawId.startsWith("hytale:") ? rawId.substring(7) : rawId;
        if (id.startsWith("*")) id = id.substring(1);

        if (event.getTargetBlock() != null) {
            int gx = event.getTargetBlock().x;
            int gy = event.getTargetBlock().y;
            int gz = event.getTargetBlock().z;
            boolean isGuard = guardianCropManager.isGuardian(gx, gy, gz);
            if (dbg) LOGGER.atInfo().log("[GardienChamps] breakCheck pos(" + gx + "," + gy + "," + gz + ") isGuardian=" + isGuard + " id=" + rawId);
            if (isGuard) {
                PlayerRef pr = archetypeChunk.getComponent(index, playerRefType);
                if (pr != null) handleGuardianCropBreak(event, pr, store, gx, gy, gz);
                return;
            }
        }

        if (!FarmerXpTable.isCrop(id)) {
            if (dbg) LOGGER.atInfo().log("[Fermier-DBG] bloc ignoré (pas une culture) id=" + id);
            return;
        }
        if (id.toLowerCase().contains("eternal")) return;

        PlayerRef playerRef = archetypeChunk.getComponent(index, playerRefType);
        if (playerRef == null) return;
        UUID uuid = playerRef.getUuid();

        PlayerAccount acc = professionManager.getAccount(uuid);
        if (acc == null || !acc.isActive(Profession.FERMIER)) {
            if (dbg) LOGGER.atInfo().log("[Fermier-DBG] joueur non actif fermier id=" + id
                + " active0=" + (acc == null ? "null" : acc.getActiveSlot0())
                + " active1=" + (acc == null ? "null" : acc.getActiveSlot1()));
            return;
        }

        String dbgId = dbg ? "[" + uuid.toString().substring(0, 8) + "|" + id + "] " : null;

        // Node 6 — C-C-Combo : XP et loot bonus par combo (1%/combo au rang 1, +0.5% par rang)
        int comboRank = acc.getTalentRank(Profession.FERMIER, "6");
        int comboCount = comboTracker.onCropHarvested(uuid);
        double comboPercent = comboRank > 0 ? (0.01 + (comboRank - 1) * 0.005) : 0.0;
        double comboBonus = comboRank > 0 ? comboCount * comboPercent : 0.0;
        if (dbg && comboRank > 0) LOGGER.atInfo().log(dbgId + "N6 CCombo combo=" + comboCount
            + " bonus=" + String.format("%.3f%%", comboBonus * 100));

        int xpRank = acc.getTalentRank(Profession.FERMIER, "1");
        double xpMult = 1.0 + xpRank * 0.05 + comboBonus;
        long finalXp = Math.round(FarmerXpTable.BASE_HARVEST_XP * xpMult);
        if (dbg) LOGGER.atInfo().log(dbgId + "XP +" + finalXp
            + " (base=" + FarmerXpTable.BASE_HARVEST_XP + " × " + String.format("%.3f", xpMult) + ")"
            + (xpRank > 0 ? " [N1 MainsTerreuses rank=" + xpRank + "]" : ""));
        professionManager.addXp(uuid, Profession.FERMIER, finalXp);

        Ref<EntityStore> ref = null;

        // Node 0 — Paniers Trop Pleins : chance de doubler les récoltes (5% par rang, max 25%)
        int lootRank = acc.getTalentRank(Profession.FERMIER, "0");
        if (lootRank > 0 && event.getTargetBlock() != null && RANDOM.nextDouble() < lootRank * 0.05) {
            String extraItemId = FarmerXpTable.resolveCropItemId(id);
            if (dbg) LOGGER.atInfo().log(dbgId + "N0 PaniersTropPleins PROC — item=" + extraItemId);
            if (extraItemId != null) {
                try {
                    if (ref == null) ref = archetypeChunk.getReferenceTo(index);
                    if (ref != null && ref.isValid()) {
                        Vector3d blockCenter = new Vector3d(
                            event.getTargetBlock().x + 0.5,
                            event.getTargetBlock().y + 0.5,
                            event.getTargetBlock().z + 0.5
                        );
                        dropItemAtBlock(commandBuffer, extraItemId, blockCenter);
                    } else {
                        if (dbg) LOGGER.atWarning().log(dbgId + "N0 PaniersTropPleins ref null/invalid");
                    }
                } catch (Exception e) {
                    LOGGER.atWarning().withCause(e).log(dbgId + "N0 PaniersTropPleins drop ERREUR item=" + extraItemId);
                }
            }
        }

        // Node 6 — C-C-Combo loot : drop bonus proportionnel au combo
        if (comboRank > 0 && comboCount > 0 && event.getTargetBlock() != null
                && RANDOM.nextDouble() < comboBonus) {
            String comboItemId = FarmerXpTable.resolveCropItemId(id);
            if (dbg) LOGGER.atInfo().log(dbgId + "N6 CCombo loot PROC — item=" + comboItemId);
            if (comboItemId != null) {
                try {
                    if (ref == null) ref = archetypeChunk.getReferenceTo(index);
                    if (ref != null && ref.isValid()) {
                        Vector3d blockCenter = new Vector3d(
                            event.getTargetBlock().x + 0.5,
                            event.getTargetBlock().y + 0.5,
                            event.getTargetBlock().z + 0.5
                        );
                        dropItemAtBlock(commandBuffer, comboItemId, blockCenter);
                    }
                } catch (Exception e) {
                    LOGGER.atWarning().withCause(e).log(dbgId + "N6 CCombo loot drop ERREUR item=" + comboItemId);
                }
            }
        }

        // Node 5 — Grains Sans Fin : chance d'obtenir une graine éternelle (0.5/0.75/1/1.25/1.5% par rang)
        int grainRank = acc.getTalentRank(Profession.FERMIER, "5");
        if (grainRank > 0 && event.getTargetBlock() != null) {
            double chance = ETERNAL_SEED_CHANCES[Math.min(grainRank, ETERNAL_SEED_CHANCES.length) - 1];
            if (RANDOM.nextDouble() < chance) {
                String eternalSeedId = FarmerXpTable.resolveEternalSeedId(id);
                if (dbg) LOGGER.atInfo().log(dbgId + "N5 GrainsSansFin PROC — item=" + eternalSeedId);
                if (eternalSeedId != null) {
                    try {
                        if (ref == null) ref = archetypeChunk.getReferenceTo(index);
                        if (ref != null && ref.isValid()) {
                            Vector3d blockCenter = new Vector3d(
                                event.getTargetBlock().x + 0.5,
                                event.getTargetBlock().y + 0.5,
                                event.getTargetBlock().z + 0.5
                            );
                            dropItemAtBlock(commandBuffer, eternalSeedId, blockCenter);
                        } else {
                            if (dbg) LOGGER.atWarning().log(dbgId + "N5 GrainsSansFin ref null/invalid");
                        }
                    } catch (Exception e) {
                        LOGGER.atWarning().withCause(e).log(dbgId + "N5 GrainsSansFin drop ERREUR item=" + eternalSeedId);
                    }
                }
            }
        }

        // Node 11 — Gardiens des Champs : chance de faire apparaître un gardien (0.5% par rang)
        int guardianRank = acc.getTalentRank(Profession.FERMIER, "11");
        if (guardianRank > 0 && event.getTargetBlock() != null && RANDOM.nextDouble() < guardianRank * 0.005) {
            Player player = null;
            try { player = playerRef.getComponent(Player.getComponentType()); } catch (Exception ignored) {}
            if (player != null) {
                World world = player.getWorld();
                if (world != null) {
                    int bx = event.getTargetBlock().x;
                    int by = event.getTargetBlock().y;
                    int bz = event.getTargetBlock().z;
                    if (dbg) LOGGER.atInfo().log(dbgId + "N11 GardienDesChamps PROC — pos(" + bx + "," + by + "," + bz + ") block=" + rawId);
                    final World w = world;
                    final String guardianBlockId = rawId;
                    guardianCropManager.queueRepop(() -> {
                        try {
                            w.setBlock(bx, by, bz, guardianBlockId);
                            guardianCropManager.track(bx, by, bz);
                        } catch (Exception e) {
                            LOGGER.atWarning().withCause(e).log("[GardienChamps] setBlock ERREUR pos=" + bx + "," + by + "," + bz);
                        }
                    });
                }
            }
        }

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
                boolean isSickle = heldId != null && heldId.toLowerCase().contains("sickle");
                if (dbg) LOGGER.atInfo().log(dbgId + "N8 check item=" + heldId
                    + " sickle=" + isSickle
                    + " dur=" + (held != null ? held.getDurability() : "?")
                    + "/" + (held != null ? held.getMaxDurability() : "?"));
                if (isSickle) {
                    int faucilleRank = acc.getTalentRank(Profession.FERMIER, "8");
                    boolean skipLoss = faucilleRank > 0 && RANDOM.nextDouble() < faucilleRank * 0.15;
                    if (dbg) LOGGER.atInfo().log(dbgId + "N8 FaucilleEternelle rank=" + faucilleRank + " proc=" + skipLoss);
                    if (!skipLoss) {
                        inventory.getHotbar().setItemStackForSlot((short) slot,
                            held.withIncreasedDurability(-1.0));
                    }
                }
            } catch (Exception ignored) {}
        }

        // Node 9 — Casse-Croûte Fermier : chance de restaurer faim ou soif (1/1.25/1.5/1.75/2% par rang)
        int snackRank = acc.getTalentRank(Profession.FERMIER, "9");
        if (snackRank > 0) {
            double snackChance = SNACK_CHANCES[Math.min(snackRank, SNACK_CHANCES.length) - 1];
            if (RANDOM.nextDouble() < snackChance) {
                String username = playerRef.getUsername();
                if (username != null) {
                    String cmd = RANDOM.nextBoolean() ? "hset " + username + " 100" : "wset " + username + " 100";
                    if (dbg) LOGGER.atInfo().log(dbgId + "N9 CasseCroute PROC — cmd=" + cmd);
                    try {
                        CommandManager.get().handleCommand(ConsoleSender.INSTANCE, cmd);
                    } catch (Exception e) {
                        LOGGER.atWarning().withCause(e).log(dbgId + "N9 CasseCroute cmd ERREUR cmd=" + cmd);
                    }
                }
            }
        }
    }

    private void handleGuardianCropBreak(@Nonnull BreakBlockEvent event,
                                          @Nonnull PlayerRef playerRef,
                                          @Nonnull Store<EntityStore> store,
                                          int bx, int by, int bz) {
        LOGGER.atInfo().log("[GardienChamps] handleGuardianCropBreak — pos(" + bx + "," + by + "," + bz + ")");
        try {
            Player player = playerRef.getComponent(Player.getComponentType());
            World world = player != null ? player.getWorld() : null;
            if (world == null) {
                LOGGER.atWarning().log("[GardienChamps] world null — abandon spawn");
                return;
            }

            spawnCowUndead(store, new Vector3d(bx + 0.5, by, bz + 0.5));
            try {
                world.setBlock(bx, by, bz, "Empty");
            } catch (Exception e) {
                LOGGER.atWarning().withCause(e).log("[GardienChamps] setBlock Empty ERREUR pos=" + bx + "," + by + "," + bz);
            }
            guardianCropManager.untrack(bx, by, bz);
            event.setCancelled(true);
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("[GardienChamps] handleGuardianCropBreak ERREUR pos=" + bx + "," + by + "," + bz);
        }
    }

    private static void spawnCowUndead(@Nonnull Store<EntityStore> store, @Nonnull Vector3d pos) {
        LOGGER.atInfo().log("[GardienChamps] spawnNPC Cow_Undead — pos=" + pos);
        try {
            var pair = NPCPlugin.get().spawnNPC(store, "Cow_Undead", null, pos, new Vector3f(0f, 0f, 0f));
            if (pair == null) {
                LOGGER.atWarning().log("[GardienChamps] spawnNPC retourné null — vérifier le role name 'Cow_Undead'");
                return;
            }
            Ref<EntityStore> cowRef = pair.left();
            EntityStatMap statMap = store.getComponent(cowRef, EntityStatMap.getComponentType());
            if (statMap == null) {
                LOGGER.atWarning().log("[GardienChamps] EntityStatMap null sur Cow_Undead");
                return;
            }
            int healthIdx = DefaultEntityStatTypes.getHealth();
            statMap.putModifier(healthIdx, "guardian_crop_hp",
                new StaticModifier(Modifier.ModifierTarget.MAX, StaticModifier.CalculationType.MULTIPLICATIVE, 3.0f));
            statMap.maximizeStatValue(healthIdx);
            LOGGER.atInfo().log("[GardienChamps] Cow_Undead spawned OK pos=" + pos);
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("[GardienChamps] spawnNPC ERREUR pos=" + pos);
        }
    }

    private static void dropItemAtBlock(@Nonnull ComponentAccessor<EntityStore> accessor,
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
