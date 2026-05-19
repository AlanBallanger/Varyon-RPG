package fr.varyon.vrpg.profession.mineur;

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
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.ecs.BreakBlockEvent;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.modules.entitystats.modifier.Modifier;
import com.hypixel.hytale.server.core.modules.entitystats.modifier.StaticModifier;
import com.hypixel.hytale.server.core.modules.entity.item.ItemComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.npc.NPCPlugin;
import fr.varyon.vrpg.config.VrpgConfig;
import fr.varyon.vrpg.rpg.PlayerAccount;
import fr.varyon.vrpg.rpg.Profession;
import fr.varyon.vrpg.rpg.ProfessionManager;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Queue;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

public final class MinerBlockBreakSystem extends EntityEventSystem<EntityStore, BreakBlockEvent> {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final Random RANDOM = new Random();
    private static final int MAX_VEIN = 64;
    private static final double GUARDIAN_SPAWN_RATE_PER_RANK = 0.005;
    private static final int GUARDIAN_SPAWN_MULTIPLIER_TEMP = 10;
    private static final double GUARDIAN_SPAWN_RATE_MAX = 0.25;
    private static final int[][] FACE_DIRS = {
        {1,0,0},{-1,0,0},{0,1,0},{0,-1,0},{0,0,1},{0,0,-1}
    };

    private final ProfessionManager professionManager;
    private final GuardianStoneManager guardianManager;
    private final MinerComboTracker comboTracker;
    private final VeinCooldownTracker veinCooldown;
    private final ComponentType<EntityStore, PlayerRef> playerRefType = PlayerRef.getComponentType();

    public MinerBlockBreakSystem(@Nonnull ProfessionManager professionManager,
                                  @Nonnull GuardianStoneManager guardianManager,
                                  @Nonnull MinerComboTracker comboTracker,
                                  @Nonnull VeinCooldownTracker veinCooldown) {
        super(BreakBlockEvent.class);
        this.professionManager = professionManager;
        this.guardianManager = guardianManager;
        this.comboTracker = comboTracker;
        this.veinCooldown = veinCooldown;
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

        String rawId = String.valueOf(blockType.getId());
        boolean isGuardian = GuardianStoneManager.GUARDIAN_BLOCK_ID.equals(rawId);
        String xpKey = MinerXpTable.resolveXpKey(rawId);
        long baseXp = MinerXpTable.getXp(xpKey);
        boolean isOre = baseXp > 0;
        boolean isRock = !isGuardian && rawId.toLowerCase().contains("rock");
        if (!isOre && !isRock && !isGuardian) return;

        PlayerRef playerRef = archetypeChunk.getComponent(index, playerRefType);
        if (playerRef == null) return;
        UUID playerUuid = playerRef.getUuid();

        if (isGuardian) {
            handleGuardianBreak(event, playerRef, store);
            return;
        }

        PlayerAccount acc = professionManager.getAccount(playerUuid);
        if (acc == null) return;
        if (acc.getActiveSlot0() != Profession.MINEUR && acc.getActiveSlot1() != Profession.MINEUR) return;

        Player player = null;
        Inventory inventory = null;
        try {
            player = playerRef.getComponent(Player.getComponentType());
            if (player != null) inventory = player.getInventory();
        } catch (Exception ignored) {}

        boolean dbg = VrpgConfig.isDebugTalents();
        String dbgId = dbg ? "[" + playerUuid.toString().substring(0, 8) + "|" + rawId + "] " : null;

        if (isOre) {
            // Node 0 — Poches Pleines : chance de doubler les ressources (5% par rang, max 25%)
            int lootRank = acc.getTalentRank(Profession.MINEUR, "0");
            if (lootRank > 0 && event.getTargetBlock() != null && RANDOM.nextDouble() < lootRank * 0.05) {
                String extraItemId = MinerXpTable.resolveOreItemId(rawId);
                if (dbg) LOGGER.atInfo().log(dbgId + "N0 PochesPleines PROC — item=" + extraItemId);
                if (extraItemId != null) {
                    try {
                        Ref<EntityStore> ref = archetypeChunk.getReferenceTo(index);
                        if (ref != null && ref.isValid()) {
                            Vector3d blockCenter = new Vector3d(
                                event.getTargetBlock().x + 0.5,
                                event.getTargetBlock().y + 0.5,
                                event.getTargetBlock().z + 0.5
                            );
                            dropOreAtBlock(commandBuffer, extraItemId, blockCenter);
                        } else {
                            if (dbg) LOGGER.atWarning().log(dbgId + "N0 PochesPleines ref null/invalid");
                        }
                    } catch (Exception e) {
                        LOGGER.atWarning().withCause(e).log(dbgId + "N0 PochesPleines dropOreAtBlock ERREUR item=" + extraItemId);
                    }
                }
            }

            int xpRank = acc.getTalentRank(Profession.MINEUR, "1");
            double xpRankMult = 1.0 + xpRank * 0.05;
            double xpMult = xpRankMult;

            // Node 5 — C-C-Combo : +rankPercent% XP et loot par combo (max 10)
            int comboRank = acc.getTalentRank(Profession.MINEUR, "5");
            int comboCount = comboTracker.onOreMined(playerUuid);
            double comboPercent = comboRank > 0 ? (0.005 + (comboRank - 1) * 0.005) : 0.0;
            double comboBonus = comboRank > 0 ? comboCount * comboPercent : 0.0;
            if (dbg && comboRank > 0) LOGGER.atInfo().log(dbgId + "N5 CCombo combo=" + comboCount
                + " bonus=" + String.format("%.3f%%", comboBonus * 100));
            xpMult += comboBonus;

            long finalXp = Math.round(baseXp * xpMult);
            if (dbg) LOGGER.atInfo().log(dbgId + "XP +" + finalXp
                + " (base=" + baseXp + " × " + String.format("%.3f", xpMult) + ")"
                + (xpRank > 0 ? " [N1 FrontPoussiereux rank=" + xpRank + "]" : ""));
            professionManager.addXp(playerUuid, Profession.MINEUR, finalXp, playerRef);

            // Node 5 — loot bonus : chance supplémentaire de drop proportionnelle au combo
            if (comboRank > 0 && comboCount > 0 && event.getTargetBlock() != null && RANDOM.nextDouble() < comboBonus) {
                String extraItemId = MinerXpTable.resolveOreItemId(rawId);
                if (dbg) LOGGER.atInfo().log(dbgId + "N5 CCombo loot PROC — item=" + extraItemId);
                if (extraItemId != null) {
                    try {
                        Ref<EntityStore> ref = archetypeChunk.getReferenceTo(index);
                        if (ref != null && ref.isValid()) {
                            Vector3d blockCenter = new Vector3d(
                                event.getTargetBlock().x + 0.5,
                                event.getTargetBlock().y + 0.5,
                                event.getTargetBlock().z + 0.5
                            );
                            dropOreAtBlock(commandBuffer, extraItemId, blockCenter);
                        } else {
                            if (dbg) LOGGER.atWarning().log(dbgId + "N5 CCombo loot ref null/invalid");
                        }
                    } catch (Exception e) {
                        LOGGER.atWarning().withCause(e).log(dbgId + "N5 CCombo loot dropOreAtBlock ERREUR item=" + extraItemId);
                    }
                }
            }

            // Node 8 — Chant de la Veine : éclate toute la veine d'un coup (cooldown par rang)
            int veinRank = acc.getTalentRank(Profession.MINEUR, "8");
            if (veinRank > 0 && player != null && event.getTargetBlock() != null
                    && veinCooldown.tryTrigger(playerUuid, veinRank)) {
                try {
                    World world = player.getWorld();
                    if (world != null) {
                        int bx = event.getTargetBlock().x;
                        int by = event.getTargetBlock().y;
                        int bz = event.getTargetBlock().z;
                        List<int[]> vein = findVein(world, bx, by, bz, rawId);
                        if (dbg) LOGGER.atInfo().log(dbgId + "N8 ChantVeine PROC — " + vein.size() + " blocs");
                        if (vein.size() > 1) {
                            Ref<EntityStore> playerEntityRef = archetypeChunk.getReferenceTo(index);
                            for (int[] pos : vein) {
                                if (pos[0] == bx && pos[1] == by && pos[2] == bz) continue;
                                try {
                                    world.setBlock(pos[0], pos[1], pos[2], "Empty");
                                    applyVeinBlockDrops(acc, playerUuid, playerRef, playerEntityRef, commandBuffer,
                                        rawId, baseXp, xpRankMult, comboRank, pos, dbg, dbgId);
                                } catch (Exception ignored) {}
                            }
                        }
                    }
                } catch (Exception ignored) {}
            }

            // Node 3 — Pioche de Vétéran : annule la consommation (7% par rang, max 35%)
            // Node 6 — Incassable : gagne +1 dura (5% par rang, max 25%)
            // On gère toute la consommation de durabilité nous-mêmes.
            if (inventory != null) {
                try {
                    byte slot = inventory.getActiveHotbarSlot();
                    ItemStack held = inventory.getHotbar().getItemStack((short) slot);
                    if (held != null && held.getMaxDurability() > 0) {
                        int veranRank = acc.getTalentRank(Profession.MINEUR, "3");
                        int incassableRank = acc.getTalentRank(Profession.MINEUR, "6");
                        boolean veranProc = veranRank > 0 && RANDOM.nextDouble() < veranRank * 0.07;
                        boolean incassableProc = !veranProc && incassableRank > 0 && RANDOM.nextDouble() < incassableRank * 0.05;
                        if (dbg && veranProc) LOGGER.atInfo().log(dbgId + "N3 PiocheVeteran PROC");
                        if (dbg && incassableProc) LOGGER.atInfo().log(dbgId + "N6 Incassable PROC");
                        double delta = incassableProc ? 1.0 : veranProc ? 0.0 : -1.0;
                        if (delta != 0.0) {
                            inventory.getHotbar().setItemStackForSlot((short) slot,
                                held.withIncreasedDurability(delta));
                        }
                    }
                } catch (Exception ignored) {}
            }

            // Node 4 — Minerai Immortel : repop au même emplacement (4% par rang, max 20%)
            int repopRank = acc.getTalentRank(Profession.MINEUR, "4");
            if (repopRank > 0 && player != null && event.getTargetBlock() != null
                    && RANDOM.nextDouble() < repopRank * 0.04) {
                try {
                    World world = player.getWorld();
                    if (world != null) {
                        int bx = event.getTargetBlock().x;
                        int by = event.getTargetBlock().y;
                        int bz = event.getTargetBlock().z;
                        if (dbg) LOGGER.atInfo().log(dbgId + "N4 MineraiImmortel PROC — setBlock(" + bx + "," + by + "," + bz + ", \"" + rawId + "\") [deferred]");
                        final World w = world;
                        final String id = rawId;
                        guardianManager.queueRepop(() -> w.setBlock(bx, by, bz, id));
                    }
                } catch (Exception e) {
                    if (dbg) LOGGER.atWarning().withCause(e).log(dbgId + "N4 MineraiImmortel ERREUR");
                }
            }

            int guardianRank = acc.getTalentRank(Profession.MINEUR, "9");
            double guardianChance = Math.min(guardianRank * GUARDIAN_SPAWN_RATE_PER_RANK * GUARDIAN_SPAWN_MULTIPLIER_TEMP, GUARDIAN_SPAWN_RATE_MAX);
            if (guardianRank > 0 && event.getTargetBlock() != null && player != null
                    && RANDOM.nextDouble() < guardianChance) {
                int bx = event.getTargetBlock().x;
                int by = event.getTargetBlock().y;
                int bz = event.getTargetBlock().z;
                World gw = player.getWorld();
                if (dbg) LOGGER.atInfo().log(dbgId + "N9 GardienDePierre PROC — spawn shell pos(" + bx + "," + by + "," + bz + ")");
                if (gw != null) {
                    final World wGuard = gw;
                    final int gx = bx, gy = by, gz = bz;
                    guardianManager.queueRepop(() -> {
                        try {
                            wGuard.setBlock(gx, gy, gz, GuardianStoneManager.GUARDIAN_BLOCK_ID);
                            guardianManager.trackShellForParticles(gx, gy, gz);
                        } catch (Exception e) {
                            LOGGER.atWarning().withCause(e).log("[GardienPierre] setBlock/trackShell pos=" + gx + "," + gy + "," + gz);
                        }
                    });
                }
            }
        }

        if (isRock) {
            // Node 7 — Briseur de Roche : annule la consommation (15% par rang, max 75%)
            if (inventory != null) {
                try {
                    byte slot = inventory.getActiveHotbarSlot();
                    ItemStack held = inventory.getHotbar().getItemStack((short) slot);
                    if (held != null && held.getMaxDurability() > 0) {
                        int rocRank = acc.getTalentRank(Profession.MINEUR, "7");
                        boolean rocProc = rocRank > 0 && RANDOM.nextDouble() < rocRank * 0.15;
                        if (dbg && rocProc) LOGGER.atInfo().log(dbgId + "N7 BriseurDeRoche PROC");
                        double delta = rocProc ? 0.0 : -1.0;
                        if (delta != 0.0) {
                            inventory.getHotbar().setItemStackForSlot((short) slot,
                                held.withIncreasedDurability(delta));
                        }
                    }
                } catch (Exception ignored) {}
            }

            // Node 14 — Deux pour le Prix d'un : casse aussi le bloc de roche en dessous
            int deuxRank = acc.getTalentRank(Profession.MINEUR, "14");
            if (deuxRank > 0 && player != null && event.getTargetBlock() != null) {
                try {
                    World world = player.getWorld();
                    if (world != null) {
                        int bx = event.getTargetBlock().x;
                        int by = event.getTargetBlock().y;
                        int bz = event.getTargetBlock().z;
                        try {
                            String belowId = world.getBlockType(bx, by - 1, bz).getId();
                            if (belowId != null && belowId.toLowerCase().contains("rock")) {
                                world.setBlock(bx, by - 1, bz, "Empty");
                            }
                        } catch (Exception ignored) {}
                    }
                } catch (Exception ignored) {}
            }
        }
    }

    private void handleGuardianBreak(@Nonnull BreakBlockEvent event,
                                      @Nonnull PlayerRef playerRef,
                                      @Nonnull Store<EntityStore> store) {
        if (event.getTargetBlock() == null) return;
        int bx = (int) event.getTargetBlock().x;
        int by = (int) event.getTargetBlock().y;
        int bz = (int) event.getTargetBlock().z;
        try {
            Player player = playerRef.getComponent(Player.getComponentType());
            World world = player != null ? player.getWorld() : null;
            if (world == null) return;

            spawnGuardianGolem(store, new Vector3d(bx + 0.5, by, bz + 0.5));
            try {
                world.setBlock(bx, by, bz, "Empty");
            } catch (Exception e2) {
                LOGGER.atWarning().withCause(e2).log("[GuardianBreak] setBlock Empty pos=" + bx + "," + by + "," + bz);
            }
            guardianManager.untrackShell(bx, by, bz);
            event.setCancelled(true);
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("[GuardianBreak] ERREUR pos=" + bx + "," + by + "," + bz);
        }
    }


    private List<int[]> findVein(@Nonnull World world, int sx, int sy, int sz, @Nonnull String targetId) {
        List<int[]> result = new ArrayList<>();
        Set<Long> visited = new HashSet<>();
        Queue<int[]> queue = new ArrayDeque<>();
        queue.add(new int[]{sx, sy, sz});
        visited.add(packPos(sx, sy, sz));
        while (!queue.isEmpty() && result.size() < MAX_VEIN) {
            int[] pos = queue.poll();
            result.add(pos);
            for (int[] d : FACE_DIRS) {
                int nx = pos[0] + d[0], ny = pos[1] + d[1], nz = pos[2] + d[2];
                long key = packPos(nx, ny, nz);
                if (visited.contains(key)) continue;
                visited.add(key);
                try {
                    BlockType bt = world.getBlockType(nx, ny, nz);
                    if (bt != null && targetId.equals(String.valueOf(bt.getId()))) {
                        queue.add(new int[]{nx, ny, nz});
                    }
                } catch (Exception ignored) {}
            }
        }
        return result;
    }

    private static long packPos(int x, int y, int z) {
        return (((long)(x + 1048576)) << 42) | (((long)(y + 1048576)) << 21) | ((long)(z + 1048576));
    }

    private void applyVeinBlockDrops(@Nonnull PlayerAccount acc, @Nonnull UUID playerUuid,
            @Nonnull PlayerRef playerRef, @Nullable Ref<EntityStore> playerEntityRef,
            @Nonnull CommandBuffer<EntityStore> buffer,
            @Nonnull String rawId, long baseXp, double xpRankMult, int comboRank,
            int[] pos, boolean dbg, @Nullable String dbgId) {
        int comboCount = comboTracker.onOreMined(playerUuid);
        double comboPercent = comboRank > 0 ? (0.005 + (comboRank - 1) * 0.005) : 0.0;
        double comboBonus = comboRank > 0 ? comboCount * comboPercent : 0.0;
        professionManager.addXp(playerUuid, Profession.MINEUR, Math.round(baseXp * (xpRankMult + comboBonus)), playerRef);

        String oreItemId = MinerXpTable.resolveOreItemId(rawId);
        if (oreItemId == null || playerEntityRef == null || !playerEntityRef.isValid()) return;

        Vector3d center = new Vector3d(pos[0] + 0.5, pos[1] + 0.5, pos[2] + 0.5);
        try { dropOreAtBlock(buffer, oreItemId, center); } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("[ChantVeine] dropOreAtBlock ERREUR item=" + oreItemId);
        }

        int lootRank = acc.getTalentRank(Profession.MINEUR, "0");
        if (lootRank > 0 && RANDOM.nextDouble() < lootRank * 0.05) {
            try { dropOreAtBlock(buffer, oreItemId, center); } catch (Exception e) {
                LOGGER.atWarning().withCause(e).log("[ChantVeine] N0 dropOreAtBlock ERREUR item=" + oreItemId);
            }
        }

        if (comboRank > 0 && comboCount > 0 && RANDOM.nextDouble() < comboBonus) {
            try { dropOreAtBlock(buffer, oreItemId, center); } catch (Exception e) {
                LOGGER.atWarning().withCause(e).log("[ChantVeine] N5 dropOreAtBlock ERREUR item=" + oreItemId);
            }
        }
    }

    private static void dropOreAtBlock(@Nonnull ComponentAccessor<EntityStore> accessor,
                                       @Nonnull String oreItemId,
                                       @Nonnull Vector3d position) {
        ItemStack stack = new ItemStack(oreItemId, 1);
        if (stack.isEmpty() || !stack.isValid()) return;
        float vx = (RANDOM.nextFloat() - 0.5f) * 2.5f;
        float vz = (RANDOM.nextFloat() - 0.5f) * 2.5f;
        Holder<EntityStore> holder = ItemComponent.generateItemDrop(accessor, stack, position, new Vector3f(0f, 0f, 0f), vx, 3.25f, vz);
        if (holder == null) return;
        accessor.addEntity(holder, AddReason.SPAWN);
    }

    private void spawnGuardianGolem(@Nonnull Store<EntityStore> store, @Nonnull Vector3d pos) {
        try {
            var pair = NPCPlugin.get().spawnNPC(store, "Golem_Crystal_Earth", null, pos, new Vector3f(0f, 0f, 0f));
            if (pair == null) {
                LOGGER.atWarning().log("[GardienPierre] spawnNPC Golem_Crystal_Earth a retourné null pos=" + pos);
                return;
            }
            Ref<EntityStore> golemRef = pair.left();
            EntityStatMap statMap = store.getComponent(golemRef, EntityStatMap.getComponentType());
            if (statMap == null) return;
            int healthIdx = DefaultEntityStatTypes.getHealth();
            statMap.putModifier(healthIdx, "guardian_hp_bonus",
                    new StaticModifier(Modifier.ModifierTarget.MAX, StaticModifier.CalculationType.ADDITIVE, 160f));
            statMap.maximizeStatValue(healthIdx);
        } catch (Exception ignored) {}
    }
}
