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
import com.hypixel.hytale.server.core.universe.world.SoundUtil;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.asset.type.soundevent.config.SoundEvent;
import com.hypixel.hytale.protocol.SoundCategory;
import com.hypixel.hytale.protocol.ToClientPacket;
import com.hypixel.hytale.protocol.packets.world.PlaySoundEvent2D;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.npc.NPCPlugin;
import fr.varyon.vrpg.audio.TalentProcSounds;
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

    private static final String MINERAI_IMMORTEL_SOUND_ID = "SFX_Vrpg_MineraiImmortel";
    private static final String CHANT_VEINE_SOUND_ID = "SFX_Vrpg_ChantVeine";
    private static final String GARDIEN_PIERRE_SOUND_ID = "SFX_Vrpg_GardienPierre";
    private static final String MINERAI_IMMORTEL_NODE_ID = "4";
    private static final String CHANT_VEINE_NODE_ID = "8";
    private static final String GARDIEN_PIERRE_NODE_ID = "9";
    private static final long MINERAI_IMMORTEL_REPOP_DELAY_MS = 300L;
    private static final long GARDIEN_PIERRE_SPAWN_DELAY_MS = 500L;

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
        String xpKey = MinerXpTable.resolveXpKey(rawId);
        long baseXp = MinerXpTable.getXp(xpKey);
        boolean isOre = baseXp > 0;
        boolean isRock = rawId.toLowerCase().contains("rock");
        if (!isOre && !isRock) return;

        PlayerRef playerRef = archetypeChunk.getComponent(index, playerRefType);
        if (playerRef == null) return;
        UUID playerUuid = playerRef.getUuid();

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
                            TalentProcSounds.playLootDouble(acc, Profession.MINEUR, playerRef, ref, commandBuffer, blockCenter);
                            dropOreAtBlock(commandBuffer, extraItemId, blockCenter);
                        } else {
                            if (dbg) LOGGER.atWarning().log(dbgId + "N0 PochesPleines ref null/invalid");
                        }
                    } catch (Exception e) {
                        LOGGER.atWarning().withCause(e).log(dbgId + "N0 PochesPleines dropOreAtBlock ERREUR item=" + extraItemId);
                    }
                }
            }

            int ghostOreRank = acc.getTalentRank(Profession.MINEUR, "2");
            if (ghostOreRank > 0 && event.getTargetBlock() != null) {
                double ghostChance = 0.01 + (ghostOreRank - 1) * 0.005;
                if (RANDOM.nextDouble() < ghostChance) {
                    if (dbg) LOGGER.atInfo().log(dbgId + "N2 MineraiFantomatique PROC");
                    try {
                        Ref<EntityStore> ref = archetypeChunk.getReferenceTo(index);
                        if (ref != null && ref.isValid()) {
                            Vector3d blockCenter = new Vector3d(
                                event.getTargetBlock().x + 0.5,
                                event.getTargetBlock().y + 0.5,
                                event.getTargetBlock().z + 0.5
                            );
                            TalentProcSounds.playFantomatique(acc, Profession.MINEUR, playerRef, ref, commandBuffer, blockCenter);
                            dropOreAtBlock(commandBuffer, "Ore_Ghost", blockCenter);
                        }
                    } catch (Exception e) {
                        LOGGER.atWarning().withCause(e).log(dbgId + "N2 MineraiFantomatique drop ERREUR");
                    }
                }
            }

            int xpRank = acc.getTalentRank(Profession.MINEUR, "1");
            double xpRankMult = 1.0 + xpRank * 0.05;
            double xpMult = xpRankMult;

            // Node 5 — C-C-Combo : +rankPercent% XP et loot par combo (max 8)
            int comboRank = acc.getTalentRank(Profession.MINEUR, "5");
            int comboCount = comboTracker.onOreMined(playerUuid);
            double comboPercent = comboRank > 0 ? (0.005 + (comboRank - 1) * 0.005) : 0.0;
            double comboBonus = comboRank > 0 ? comboCount * comboPercent : 0.0;
            if (dbg && comboRank > 0) LOGGER.atInfo().log(dbgId + "N5 CCombo combo=" + comboCount
                + " bonus=" + String.format("%.3f%%", comboBonus * 100));
            xpMult += comboBonus;

            if (comboRank > 0 && comboCount > 0 && event.getTargetBlock() != null) {
                try {
                    Ref<EntityStore> ref = archetypeChunk.getReferenceTo(index);
                    if (ref != null && ref.isValid()) {
                        Vector3d blockCenter = new Vector3d(
                            event.getTargetBlock().x + 0.5,
                            event.getTargetBlock().y + 0.5,
                            event.getTargetBlock().z + 0.5
                        );
                        TalentProcSounds.playCombo(acc, Profession.MINEUR, comboRank, comboCount,
                            playerRef, ref, commandBuffer, blockCenter);
                    }
                } catch (Exception ignored) {}
            }

            double finalXp = baseXp * xpMult;
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
                            Vector3d blockCenter = new Vector3d(bx + 0.5, by + 0.5, bz + 0.5);
                            if (playerEntityRef != null && playerEntityRef.isValid()
                                    && acc.isTalentSoundEnabled(Profession.MINEUR, CHANT_VEINE_NODE_ID)) {
                                playTalentProcSound(CHANT_VEINE_SOUND_ID, playerRef, playerEntityRef, commandBuffer,
                                    blockCenter, dbg, dbgId, "N8 ChantVeine");
                            }
                            for (int[] pos : vein) {
                                if (pos[0] == bx && pos[1] == by && pos[2] == bz) continue;
                                try {
                                    world.setBlock(pos[0], pos[1], pos[2], "Empty");
                                    applyVeinBlockDrops(acc, playerUuid, playerRef, playerEntityRef, commandBuffer,
                                        player, rawId, baseXp, xpRankMult, comboRank, pos, dbg, dbgId);
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
                        double delta = incassableProc ? 1.0 : veranProc ? 0.0 : -1.0;
                        if (dbg) {
                            double duraBefore = held.getDurability();
                            double duraMax = held.getMaxDurability();
                            String action = veranProc ? "N3 PiocheVeteran PROC (annulé)" : incassableProc ? "N6 Incassable PROC (+1)" : "dura normale (-1)";
                            if (delta != 0.0) {
                                ItemStack after = held.withIncreasedDurability(delta);
                                LOGGER.atInfo().log(dbgId + action + " item=" + held.getItemId()
                                    + " dura=" + duraBefore + "/" + duraMax
                                    + " → " + after.getDurability() + "/" + after.getMaxDurability());
                                inventory.getHotbar().setItemStackForSlot((short) slot, after);
                            } else {
                                LOGGER.atInfo().log(dbgId + action + " item=" + held.getItemId()
                                    + " dura=" + duraBefore + "/" + duraMax + " (inchangé)");
                            }
                        } else if (delta != 0.0) {
                            inventory.getHotbar().setItemStackForSlot((short) slot,
                                held.withIncreasedDurability(delta));
                        }
                    } else if (dbg && held != null) {
                        LOGGER.atInfo().log(dbgId + "dura ignorée — item=" + held.getItemId()
                            + " maxDura=" + held.getMaxDurability() + " (pas de durabilité)");
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
                        if (dbg) LOGGER.atInfo().log(dbgId + "N4 MineraiImmortel PROC — setBlock(" + bx + "," + by + "," + bz + ", \"" + rawId + "\") [+" + MINERAI_IMMORTEL_REPOP_DELAY_MS + "ms]");
                        final World w = world;
                        final String id = rawId;
                        final double sx = bx + 0.5;
                        final double sy = by + 0.5;
                        final double sz = bz + 0.5;
                        final boolean immortelSound = acc.isTalentSoundEnabled(Profession.MINEUR, MINERAI_IMMORTEL_NODE_ID);
                        guardianManager.queueDelayedRepop(() -> w.execute(() -> {
                            try {
                                w.setBlock(bx, by, bz, id);
                                if (immortelSound) {
                                    playMineraiImmortelPlaceSound(w, sx, sy, sz, dbg, dbgId);
                                }
                            } catch (Exception e) {
                                if (dbg) LOGGER.atWarning().withCause(e).log(dbgId + "N4 MineraiImmortel setBlock ERREUR");
                            }
                        }), MINERAI_IMMORTEL_REPOP_DELAY_MS);
                    }
                } catch (Exception e) {
                    if (dbg) LOGGER.atWarning().withCause(e).log(dbgId + "N4 MineraiImmortel ERREUR");
                }
            }

            int guardianRank = acc.getTalentRank(Profession.MINEUR, "9");
            double guardianChance = Math.min(guardianRank * GUARDIAN_SPAWN_RATE_PER_RANK * GUARDIAN_SPAWN_MULTIPLIER_TEMP, GUARDIAN_SPAWN_RATE_MAX);
            if (guardianRank > 0 && event.getTargetBlock() != null && player != null
                    && RANDOM.nextDouble() < guardianChance) {
                try {
                    World world = player.getWorld();
                    if (world != null) {
                        int bx = event.getTargetBlock().x;
                        int by = event.getTargetBlock().y;
                        int bz = event.getTargetBlock().z;
                        if (dbg) LOGGER.atInfo().log(dbgId + "N9 GardienDePierre PROC — pos(" + bx + "," + by + "," + bz + ") [+" + GARDIEN_PIERRE_SPAWN_DELAY_MS + "ms]");
                        final World w = world;
                        final double sx = bx + 0.5;
                        final double sy = by;
                        final double sz = bz + 0.5;
                        final boolean gardienSound = acc.isTalentSoundEnabled(Profession.MINEUR, GARDIEN_PIERRE_NODE_ID);
                        guardianManager.queueDelayedRepop(() -> w.execute(() -> {
                            try {
                                if (gardienSound) {
                                    playGardienPierreSpawnSound(w, sx, sy, sz, dbg, dbgId);
                                }
                                Store<EntityStore> entityStore = w.getEntityStore().getStore();
                                spawnGuardianGolem(entityStore, new Vector3d(sx, sy, sz));
                            } catch (Exception e) {
                                if (dbg) LOGGER.atWarning().withCause(e).log(dbgId + "N9 GardienDePierre spawn ERREUR");
                            }
                        }), GARDIEN_PIERRE_SPAWN_DELAY_MS);
                    }
                } catch (Exception e) {
                    if (dbg) LOGGER.atWarning().withCause(e).log(dbgId + "N9 GardienDePierre ERREUR");
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
            @Nonnull CommandBuffer<EntityStore> buffer, @Nullable Player player,
            @Nonnull String rawId, long baseXp, double xpRankMult, int comboRank,
            int[] pos, boolean dbg, @Nullable String dbgId) {
        int comboCount = comboTracker.onOreMined(playerUuid);
        double comboPercent = comboRank > 0 ? (0.005 + (comboRank - 1) * 0.005) : 0.0;
        double comboBonus = comboRank > 0 ? comboCount * comboPercent : 0.0;
        professionManager.addXp(playerUuid, Profession.MINEUR, baseXp * (xpRankMult + comboBonus), playerRef);

        String oreItemId = MinerXpTable.resolveOreItemId(rawId);
        if (oreItemId == null || playerEntityRef == null || !playerEntityRef.isValid()) return;

        Vector3d center = new Vector3d(pos[0] + 0.5, pos[1] + 0.5, pos[2] + 0.5);
        if (comboRank > 0 && comboCount > 0) {
            TalentProcSounds.playCombo(acc, Profession.MINEUR, comboRank, comboCount,
                playerRef, playerEntityRef, buffer, center);
        }
        try { dropOreAtBlock(buffer, oreItemId, center); } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("[ChantVeine] dropOreAtBlock ERREUR item=" + oreItemId);
        }

        int lootRank = acc.getTalentRank(Profession.MINEUR, "0");
        if (lootRank > 0 && RANDOM.nextDouble() < lootRank * 0.05) {
            TalentProcSounds.playLootDouble(acc, Profession.MINEUR, playerRef, playerEntityRef, buffer, center);
            try { dropOreAtBlock(buffer, oreItemId, center); } catch (Exception e) {
                LOGGER.atWarning().withCause(e).log("[ChantVeine] N0 dropOreAtBlock ERREUR item=" + oreItemId);
            }
        }

        int ghostOreRank = acc.getTalentRank(Profession.MINEUR, "2");
        if (ghostOreRank > 0) {
            double ghostChance = 0.01 + (ghostOreRank - 1) * 0.005;
            if (RANDOM.nextDouble() < ghostChance) {
                TalentProcSounds.playFantomatique(acc, Profession.MINEUR, playerRef, playerEntityRef, buffer, center);
                try { dropOreAtBlock(buffer, "Ore_Ghost", center); } catch (Exception e) {
                    LOGGER.atWarning().withCause(e).log("[ChantVeine] N2 dropOreAtBlock ERREUR");
                }
            }
        }

        if (comboRank > 0 && comboCount > 0 && RANDOM.nextDouble() < comboBonus) {
            try { dropOreAtBlock(buffer, oreItemId, center); } catch (Exception e) {
                LOGGER.atWarning().withCause(e).log("[ChantVeine] N5 dropOreAtBlock ERREUR item=" + oreItemId);
            }
        }

        int repopRank = acc.getTalentRank(Profession.MINEUR, "4");
        if (repopRank > 0 && player != null && RANDOM.nextDouble() < repopRank * 0.04) {
            try {
                World world = player.getWorld();
                if (world != null) {
                    int bx = pos[0], by = pos[1], bz = pos[2];
                    if (dbg) LOGGER.atInfo().log((dbgId != null ? dbgId : "") + "[ChantVeine] N4 MineraiImmortel PROC — setBlock(" + bx + "," + by + "," + bz + ")");
                    final World w = world;
                    final String id = rawId;
                    final double sx = bx + 0.5, sy = by + 0.5, sz = bz + 0.5;
                    final boolean immortelSound = acc.isTalentSoundEnabled(Profession.MINEUR, MINERAI_IMMORTEL_NODE_ID);
                    guardianManager.queueDelayedRepop(() -> w.execute(() -> {
                        try {
                            w.setBlock(bx, by, bz, id);
                            if (immortelSound) {
                                playMineraiImmortelPlaceSound(w, sx, sy, sz, dbg, dbgId);
                            }
                        } catch (Exception e) {
                            if (dbg) LOGGER.atWarning().withCause(e).log((dbgId != null ? dbgId : "") + "[ChantVeine] N4 MineraiImmortel setBlock ERREUR");
                        }
                    }), MINERAI_IMMORTEL_REPOP_DELAY_MS);
                }
            } catch (Exception e) {
                if (dbg) LOGGER.atWarning().withCause(e).log((dbgId != null ? dbgId : "") + "[ChantVeine] N4 MineraiImmortel ERREUR");
            }
        }
    }

    private static void dropOreAtBlock(@Nonnull ComponentAccessor<EntityStore> accessor,
                                       @Nonnull String oreItemId,
                                       @Nonnull Vector3d position) {
        ItemStack stack = new ItemStack(oreItemId, 1);
        if (stack.isEmpty() || !stack.isValid()) return;
        float vx = (RANDOM.nextFloat() - 0.5f) * 2.5f;
        float vy = 0.5f + RANDOM.nextFloat() * 0.5f;
        float vz = (RANDOM.nextFloat() - 0.5f) * 2.5f;
        Holder<EntityStore> holder = ItemComponent.generateItemDrop(accessor, stack, position, Vector3f.ZERO, vx, vy, vz);
        if (holder == null) return;
        accessor.addEntity(holder, AddReason.SPAWN);
    }

    private static void playMineraiImmortelPlaceSound(@Nonnull World world,
                                                     double x, double y, double z,
                                                     boolean dbg,
                                                     @Nullable String dbgId) {
        try {
            int idx = SoundEvent.getAssetMap().getIndex(MINERAI_IMMORTEL_SOUND_ID);
            if (idx <= 0) {
                if (dbg) LOGGER.atWarning().log((dbgId != null ? dbgId : "")
                        + "N4 MineraiImmortel son — SoundEvent introuvable: " + MINERAI_IMMORTEL_SOUND_ID);
                return;
            }
            Store<EntityStore> entityStore = world.getEntityStore().getStore();
            SoundUtil.playSoundEvent3d(idx, SoundCategory.SFX, x, y, z, entityStore);
            if (dbg) LOGGER.atInfo().log((dbgId != null ? dbgId : "")
                    + "N4 MineraiImmortel son joué idx=" + idx);
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log((dbgId != null ? dbgId : "") + "N4 MineraiImmortel son ERREUR");
        }
    }

    private static void playGardienPierreSpawnSound(@Nonnull World world,
                                                   double x, double y, double z,
                                                   boolean dbg,
                                                   @Nullable String dbgId) {
        try {
            int idx = SoundEvent.getAssetMap().getIndex(GARDIEN_PIERRE_SOUND_ID);
            if (idx <= 0) {
                if (dbg) LOGGER.atWarning().log((dbgId != null ? dbgId : "")
                        + "N9 GardienDePierre son — SoundEvent introuvable: " + GARDIEN_PIERRE_SOUND_ID);
                return;
            }
            Store<EntityStore> entityStore = world.getEntityStore().getStore();
            SoundUtil.playSoundEvent3d(idx, SoundCategory.SFX, x, y, z, entityStore);
            if (dbg) LOGGER.atInfo().log((dbgId != null ? dbgId : "")
                    + "N9 GardienDePierre son joué idx=" + idx);
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log((dbgId != null ? dbgId : "") + "N9 GardienDePierre son ERREUR");
        }
    }

    private static void playTalentProcSound(@Nonnull String soundEventId,
                                            @Nonnull PlayerRef playerRef,
                                            @Nonnull Ref<EntityStore> ref,
                                            @Nonnull CommandBuffer<EntityStore> commandBuffer,
                                            @Nonnull Vector3d at,
                                            boolean dbg,
                                            @Nullable String dbgId,
                                            @Nonnull String dbgLabel) {
        try {
            int idx = SoundEvent.getAssetMap().getIndex(soundEventId);
            if (idx <= 0) {
                if (dbg) LOGGER.atWarning().log((dbgId != null ? dbgId : "")
                        + dbgLabel + " son — SoundEvent introuvable: " + soundEventId);
                return;
            }
            playerRef.getPacketHandler().writeNoCache(
                    (ToClientPacket) new PlaySoundEvent2D(idx, SoundCategory.SFX, 1.0f, 1.0f));
            SoundUtil.playSoundEvent3d(idx, SoundCategory.SFX, at.x, at.y, at.z, commandBuffer);
            if (dbg) LOGGER.atInfo().log((dbgId != null ? dbgId : "")
                    + dbgLabel + " son joué idx=" + idx);
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log((dbgId != null ? dbgId : "") + dbgLabel + " son ERREUR");
        }
    }

    private void spawnGuardianGolem(@Nonnull Store<EntityStore> store, @Nonnull Vector3d pos) {
        LOGGER.atInfo().log("[GardienPierre] spawnNPC Golem_Crystal_Earth — pos=" + pos);
        try {
            var pair = NPCPlugin.get().spawnNPC(store, "Golem_Crystal_Earth", null, pos, new Vector3f(0f, 0f, 0f));
            if (pair == null) {
                LOGGER.atWarning().log("[GardienPierre] spawnNPC retourné null — vérifier le role name 'Golem_Crystal_Earth'");
                return;
            }
            Ref<EntityStore> golemRef = pair.left();
            EntityStatMap statMap = store.getComponent(golemRef, EntityStatMap.getComponentType());
            if (statMap == null) {
                LOGGER.atWarning().log("[GardienPierre] EntityStatMap null sur Golem_Crystal_Earth");
                return;
            }
            int healthIdx = DefaultEntityStatTypes.getHealth();
            statMap.putModifier(healthIdx, "guardian_hp_bonus",
                    new StaticModifier(Modifier.ModifierTarget.MAX, StaticModifier.CalculationType.ADDITIVE, 160f));
            statMap.maximizeStatValue(healthIdx);
            LOGGER.atInfo().log("[GardienPierre] Golem_Crystal_Earth spawned OK pos=" + pos);
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("[GardienPierre] spawnNPC ERREUR pos=" + pos);
        }
    }
}
