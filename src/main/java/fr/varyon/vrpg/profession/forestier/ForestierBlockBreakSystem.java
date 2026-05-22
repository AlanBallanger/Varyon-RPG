package fr.varyon.vrpg.profession.forestier;

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
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public final class ForestierBlockBreakSystem extends EntityEventSystem<EntityStore, BreakBlockEvent> {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final Random RANDOM = new Random();

    private static final int MAX_TREE_BLOCKS = 256;
    private static final int TREE_REACH = 12;
    private static final int[][] TREE_DIRS_26 = buildTreeDirs();

    private static final ScheduledExecutorService REPLANT_SCHEDULER =
        Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "VaryonRPG-Replant");
            t.setDaemon(true);
            return t;
        });

    private static int[][] buildTreeDirs() {
        int[][] dirs = new int[26][3];
        int i = 0;
        for (int dx = -1; dx <= 1; dx++)
            for (int dy = -1; dy <= 1; dy++)
                for (int dz = -1; dz <= 1; dz++)
                    if (dx != 0 || dy != 0 || dz != 0)
                        dirs[i++] = new int[]{dx, dy, dz};
        return dirs;
    }

    private final ProfessionManager professionManager;
    private final ForestierComboTracker comboTracker;
    private final GuardianWoodManager guardianWoodManager;
    private final ComponentType<EntityStore, PlayerRef> playerRefType = PlayerRef.getComponentType();

    public ForestierBlockBreakSystem(@Nonnull ProfessionManager professionManager,
                                     @Nonnull ForestierComboTracker comboTracker,
                                     @Nonnull GuardianWoodManager guardianWoodManager) {
        super(BreakBlockEvent.class);
        this.professionManager = professionManager;
        this.comboTracker = comboTracker;
        this.guardianWoodManager = guardianWoodManager;
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

        boolean isLog    = ForestierXpTable.isLog(rawId);
        boolean isForage = ForestierXpTable.isForageBlock(rawId);
        if (!isLog && !isForage) return;

        PlayerRef playerRef = archetypeChunk.getComponent(index, playerRefType);
        if (playerRef == null) return;
        UUID uuid = playerRef.getUuid();

        PlayerAccount acc = professionManager.getAccount(uuid);
        if (acc == null || !acc.isActive(Profession.FORESTIER)) return;

        boolean dbg = VrpgConfig.isDebugTalents();
        String dbgId = dbg ? "[" + uuid.toString().substring(0, 8) + "|" + rawId + "] " : null;

        Vector3d blockCenter = null;
        if (event.getTargetBlock() != null) {
            blockCenter = new Vector3d(
                event.getTargetBlock().x + 0.5,
                event.getTargetBlock().y + 0.5,
                event.getTargetBlock().z + 0.5
            );
        }

        Ref<EntityStore> entityRef = null;

        if (isLog) {
            // Node 5 ÔÇö C-C-Combo : XP et loot bonus par combo (1%/combo au rang 1, +0.5% par rang)
            int comboRank = acc.getTalentRank(Profession.FORESTIER, "5");
            int comboCount = comboTracker.onLogChopped(uuid);
            double comboPercent = comboRank > 0 ? (0.01 + (comboRank - 1) * 0.005) : 0.0;
            double comboBonus = comboRank > 0 ? comboCount * comboPercent : 0.0;
            if (dbg && comboRank > 0) LOGGER.atInfo().log(dbgId + "N5 CCombo combo=" + comboCount
                + " bonus=" + String.format("%.3f%%", comboBonus * 100));

            if (comboRank > 0 && comboCount > 0 && blockCenter != null) {
                try {
                    if (entityRef == null) entityRef = archetypeChunk.getReferenceTo(index);
                    if (entityRef != null && entityRef.isValid()) {
                        TalentProcSounds.playCombo(acc, Profession.FORESTIER, comboRank, comboCount,
                            playerRef, entityRef, commandBuffer, blockCenter);
                    }
                } catch (Exception ignored) {}
            }

            // Node 1 ÔÇö Mains ├ëcorch├®es : +5% XP par rang
            int xpRank = acc.getTalentRank(Profession.FORESTIER, "1");
            double xpMult = 1.0 + xpRank * 0.05 + comboBonus;
            double finalXp = ForestierXpTable.BASE_LOG_XP * xpMult;
            if (dbg) LOGGER.atInfo().log(dbgId + "XP +" + finalXp
                + " (base=" + ForestierXpTable.BASE_LOG_XP + " ├ù " + String.format("%.3f", xpMult) + ")"
                + (xpRank > 0 ? " [N1 MainsEcorchees rank=" + xpRank + "]" : ""));
            professionManager.addXp(uuid, Profession.FORESTIER, finalXp, playerRef);

            // Node 0 ÔÇö B├╗ches Bien Lourdes : chance de doubler les b├╗ches (5% par rang, max 25%)
            int lootRank = acc.getTalentRank(Profession.FORESTIER, "0");
            if (lootRank > 0 && blockCenter != null && RANDOM.nextDouble() < lootRank * 0.05) {
                String logItemId = ForestierXpTable.resolveLogItemId(rawId);
                if (dbg) LOGGER.atInfo().log(dbgId + "N0 BuchesBienLourdes PROC ÔÇö item=" + logItemId);
                if (logItemId != null) {
                    try {
                        if (entityRef == null) entityRef = archetypeChunk.getReferenceTo(index);
                        if (entityRef != null && entityRef.isValid()) {
                            TalentProcSounds.playLootDouble(acc, Profession.FORESTIER, playerRef, entityRef, commandBuffer, blockCenter);
                            dropItemAtBlock(commandBuffer, logItemId, blockCenter);
                        }
                    } catch (Exception e) {
                        LOGGER.atWarning().withCause(e).log(dbgId + "N0 drop ERREUR item=" + logItemId);
                    }
                }
            }

            // Node 5 ÔÇö C-C-Combo loot : drop bonus proportionnel au combo
            if (comboRank > 0 && comboCount > 0 && blockCenter != null && RANDOM.nextDouble() < comboBonus) {
                String logItemId = ForestierXpTable.resolveLogItemId(rawId);
                if (dbg) LOGGER.atInfo().log(dbgId + "N5 CCombo loot PROC ÔÇö item=" + logItemId);
                if (logItemId != null) {
                    try {
                        if (entityRef == null) entityRef = archetypeChunk.getReferenceTo(index);
                        if (entityRef != null && entityRef.isValid()) {
                            dropItemAtBlock(commandBuffer, logItemId, blockCenter);
                        }
                    } catch (Exception e) {
                        LOGGER.atWarning().withCause(e).log(dbgId + "N5 CCombo loot ERREUR");
                    }
                }
            }

            // Node 2 ÔÇö S├¿ve Primordiale : chance d'obtenir une Essence de Forestier (1% + 0.5%/rang)
            int essenceRank = acc.getTalentRank(Profession.FORESTIER, "2");
            if (essenceRank > 0 && blockCenter != null) {
                double essenceChance = 0.01 + (essenceRank - 1) * 0.005;
                if (RANDOM.nextDouble() < essenceChance) {
                    if (dbg) LOGGER.atInfo().log(dbgId + "N2 SevePrimordiale PROC");
                    try {
                        if (entityRef == null) entityRef = archetypeChunk.getReferenceTo(index);
                        if (entityRef != null && entityRef.isValid()) {
                            TalentProcSounds.playFantomatique(acc, Profession.FORESTIER, playerRef, entityRef, commandBuffer, blockCenter);
                            dropItemAtBlock(commandBuffer, ForestierXpTable.ESSENCE_ITEM_ID, blockCenter);
                        }
                    } catch (Exception e) {
                        LOGGER.atWarning().withCause(e).log(dbgId + "N2 essence ERREUR");
                    }
                }
            }

            // Node 3 ÔÇö Hache du Survivant : annule la durabilit├® (15% par rang, max 75%)
            int hacheRank = acc.getTalentRank(Profession.FORESTIER, "3");
            if (hacheRank > 0) {
                try {
                    Player player = playerRef.getComponent(Player.getComponentType());
                    Inventory inventory = player != null ? player.getInventory() : null;
                    if (inventory != null) {
                        byte slot = inventory.getActiveHotbarSlot();
                        ItemStack held = inventory.getHotbar().getItemStack((short) slot);
                        String heldId = held != null ? held.getItemId() : null;
                        boolean isHatchet = heldId != null && heldId.toLowerCase().contains("hatchet");
                        if (isHatchet && held.getMaxDurability() > 0) {
                            boolean proc = RANDOM.nextDouble() < hacheRank * 0.15;
                            if (dbg) LOGGER.atInfo().log(dbgId + "N3 HacheDuSurvivant rank=" + hacheRank + " proc=" + proc);
                            if (!proc) {
                                inventory.getHotbar().setItemStackForSlot((short) slot,
                                    held.withIncreasedDurability(-1.0));
                            }
                        }
                    }
                } catch (Exception ignored) {}
            }

            // Node 10 — Gardien Sylvestre : chance d'invoquer un Wolf_Black (5% par rang)
            int gardienRank = acc.getTalentRank(Profession.FORESTIER, "10");
            if (gardienRank > 0 && event.getTargetBlock() != null && RANDOM.nextDouble() < gardienRank * 0.05) {
                int bx = event.getTargetBlock().x;
                int by = event.getTargetBlock().y;
                int bz = event.getTargetBlock().z;
                if (dbg) LOGGER.atInfo().log(dbgId + "N10 GardienSylvestre PROC — pos(" + bx + "," + by + "," + bz + ")");
                try {
                    Player player = playerRef.getComponent(Player.getComponentType());
                    World world = player != null ? player.getWorld() : null;
                    if (world != null) {
                        boolean soundOn = acc.isTalentSoundEnabled(Profession.FORESTIER, "10");
                        ForestierGuardianSpawner.scheduleSpawn(guardianWoodManager, world,
                            new Vector3d(bx + 0.5, by, bz + 0.5), soundOn);
                    }
                } catch (Exception e) {
                    LOGGER.atWarning().withCause(e).log(dbgId + "N10 GardienSylvestre schedule ERREUR");
                }
            }

            // Node 12 — Retour aux Racines : abat l'arbre quand la coupe sectionne le tronc du sol
            int racinRank = acc.getTalentRank(Profession.FORESTIER, "12");
            if (racinRank > 0 && event.getTargetBlock() != null) {
                try {
                    Player racinPlayer = playerRef.getComponent(Player.getComponentType());
                    World world = racinPlayer != null ? racinPlayer.getWorld() : null;
                    if (world != null) {
                        int bx = (int) event.getTargetBlock().x;
                        int by = (int) event.getTargetBlock().y;
                        int bz = (int) event.getTargetBlock().z;
                        String treeType = extractTreeType(rawId);
                        if (treeType != null) {
                            long brokenKey = packTreePos(bx, by, bz);
                            Map<Long, String> treeBlocks = findTreeBlocks(world, bx, by, bz, treeType);
                            // Trouver les blocs ancrés au sol (log/trunk avec sol solide directement en dessous)
                            Set<Long> baseAnchors = new HashSet<>();
                            for (Map.Entry<Long, String> entry : treeBlocks.entrySet()) {
                                long key = entry.getKey();
                                if (key == brokenKey) continue;
                                int[] p = unpackTreePos(key);
                                String bid = entry.getValue();
                                if (!ForestierXpTable.isLog(bid)) continue;
                                String bidLower = bid.toLowerCase();
                                if (!bidLower.contains("_log") && !bidLower.contains("_trunk")) continue;
                                try {
                                    BlockType below = world.getBlockType(p[0], p[1] - 1, p[2]);
                                    if (below == null) continue;
                                    String belowId = String.valueOf(below.getId()).toLowerCase();
                                    if (belowId.equals("empty") || belowId.equals("null")) continue;
                                    if (belowId.contains("_" + treeType.toLowerCase())) continue;
                                    baseAnchors.add(key);
                                } catch (Exception ignored) {}
                            }
                            if (dbg) LOGGER.atInfo().log(dbgId + "N12 baseAnchors=" + baseAnchors.size() + " treeBlocks=" + treeBlocks.size());
                            // Depuis les ancrages, peut-on atteindre un bloc au-dessus de by sans passer par brokenKey ?
                            boolean stillConnected = isUpperReachableFromAnchors(treeBlocks, baseAnchors, brokenKey, by);
                            if (stillConnected) {
                                if (dbg) LOGGER.atInfo().log(dbgId + "N12 ignoré — arbre encore relié au sol");
                            } else {
                                if (dbg) LOGGER.atInfo().log(dbgId + "N12 arbre sectionné — abattage de " + treeBlocks.size() + " blocs");
                                try {
                                    if (entityRef == null) entityRef = archetypeChunk.getReferenceTo(index);
                                    if (entityRef != null && entityRef.isValid()) {
                                        Vector3d soundPos = new Vector3d(bx + 0.5, by + 0.5, bz + 0.5);
                                        TalentProcSounds.playTalent(acc, Profession.FORESTIER, "12",
                                            TalentProcSounds.TREE_REGROWTH_SOUND_ID,
                                            playerRef, entityRef, commandBuffer, soundPos);
                                    }
                                } catch (Exception ignored) {}
                                Map<Long, int[]> lowestByXZ = new HashMap<>();
                                int extraLogs = 0;
                                for (Map.Entry<Long, String> entry : treeBlocks.entrySet()) {
                                    long posKey = entry.getKey();
                                    if (posKey == brokenKey) continue;
                                    int[] pos = unpackTreePos(posKey);
                                    String bid = entry.getValue();
                                    world.setBlock(pos[0], pos[1], pos[2], "Empty");
                                    if (ForestierXpTable.isLog(bid) || bid.toLowerCase().contains("_root")) {
                                        String logItemId = ForestierXpTable.resolveLogItemId(bid);
                                        if (logItemId != null) {
                                            Vector3d dropPos = new Vector3d(pos[0] + 0.5, pos[1] + 0.5, pos[2] + 0.5);
                                            try {
                                                if (entityRef == null) entityRef = archetypeChunk.getReferenceTo(index);
                                                if (entityRef != null && entityRef.isValid()) {
                                                    dropItemAtBlock(commandBuffer, logItemId, dropPos);
                                                }
                                            } catch (Exception ignored) {}
                                        }
                                        extraLogs++;
                                    }
                                    long xzKey = ((long) pos[0]) << 32 | ((long) pos[2] & 0xFFFFFFFFL);
                                    int[] existing = lowestByXZ.get(xzKey);
                                    if (existing == null || pos[1] < existing[1]) {
                                        lowestByXZ.put(xzKey, pos);
                                    }
                                }
                                if (extraLogs > 0) {
                                    professionManager.addXp(uuid, Profession.FORESTIER, ForestierXpTable.BASE_LOG_XP * xpMult * extraLogs, playerRef);
                                }
                                if (!lowestByXZ.isEmpty()) {
                                    final Collection<int[]> finalLowest = new ArrayList<>(lowestByXZ.values());
                                    final String finalSaplingId = "Plant_Sapling_" + treeType;
                                    if (dbg) LOGGER.atInfo().log(dbgId + "N12 replant sapling=" + finalSaplingId + " (délai 500ms)");
                                    REPLANT_SCHEDULER.schedule(
                                        () -> world.execute(() -> fillAndReplant(world, finalLowest, finalSaplingId)),
                                        500, TimeUnit.MILLISECONDS
                                    );
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    LOGGER.atWarning().withCause(e).log("[N12 RetourAuxRacines] ERREUR rawId=" + rawId);
                }
            }
        }

        if (isForage) {
            // Node 1 ÔÇö Mains ├ëcorch├®es : +5% XP par rang (s'applique aussi aux r├®coltes en nature)
            int xpRank = acc.getTalentRank(Profession.FORESTIER, "1");
            double xpMult = 1.0 + xpRank * 0.05;
            double finalXp = ForestierXpTable.BASE_FORAGE_XP * xpMult;
            if (dbg) LOGGER.atInfo().log(dbgId + "XP forage +" + finalXp
                + " (base=" + ForestierXpTable.BASE_FORAGE_XP + " ├ù " + String.format("%.3f", xpMult) + ")");
            professionManager.addXp(uuid, Profession.FORESTIER, finalXp, playerRef);

            // Node 4 ÔÇö Cueilleur des Sous-Bois : chance de doubler la r├®colte (5% par rang, max 25%)
            int forageRank = acc.getTalentRank(Profession.FORESTIER, "4");
            if (forageRank > 0 && blockCenter != null && RANDOM.nextDouble() < forageRank * 0.05) {
                String forageItemId = ForestierXpTable.resolveForageItemId(rawId);
                if (dbg) LOGGER.atInfo().log(dbgId + "N4 CueilleurSousBois PROC ÔÇö item=" + forageItemId);
                if (forageItemId != null) {
                    try {
                        if (entityRef == null) entityRef = archetypeChunk.getReferenceTo(index);
                        if (entityRef != null && entityRef.isValid()) {
                            dropItemAtBlock(commandBuffer, forageItemId, blockCenter);
                        }
                    } catch (Exception e) {
                        LOGGER.atWarning().withCause(e).log(dbgId + "N4 drop ERREUR item=" + forageItemId);
                    }
                }
            }
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

    /**
     * Depuis les blocs ancrés au sol (baseAnchors), vérifie si on peut atteindre
     * un bloc à y > cutY en traversant treeBlocks sans passer par brokenKey.
     * Retourne true si l'arbre est encore relié au sol depuis la canopée.
     */
    private static boolean isUpperReachableFromAnchors(Map<Long, String> treeBlocks,
                                                        Set<Long> baseAnchors,
                                                        long brokenKey,
                                                        int cutY) {
        if (baseAnchors.isEmpty()) return false;
        Set<Long> visited = new HashSet<>(baseAnchors.size() + 1);
        visited.add(brokenKey);
        Queue<int[]> queue = new ArrayDeque<>();
        for (long src : baseAnchors) {
            if (visited.add(src)) {
                int[] p = unpackTreePos(src);
                if (p[1] > cutY) return true;
                queue.add(p);
            }
        }
        while (!queue.isEmpty()) {
            int[] pos = queue.poll();
            for (int[] d : TREE_DIRS_26) {
                int nx = pos[0] + d[0], ny = pos[1] + d[1], nz = pos[2] + d[2];
                long nKey = packTreePos(nx, ny, nz);
                if (visited.contains(nKey)) continue;
                if (!treeBlocks.containsKey(nKey)) continue;
                visited.add(nKey);
                if (ny > cutY) return true;
                queue.add(new int[]{nx, ny, nz});
            }
        }
        return false;
    }

    private static String extractTreeType(String rawId) {
        String s = rawId;
        if (s.startsWith("hytale:")) s = s.substring(7);
        if (s.startsWith("*")) s = s.substring(1);
        String[] parts = s.split("_");
        return parts.length >= 2 ? parts[1] : null;
    }

    private static Map<Long, String> findTreeBlocks(World world, int sx, int sy, int sz, String treeType) {
        Map<Long, String> result = new HashMap<>();
        Set<Long> visited = new HashSet<>();
        Queue<int[]> queue = new ArrayDeque<>();
        String searchKey = "_" + treeType;
        long startKey = packTreePos(sx, sy, sz);
        visited.add(startKey);
        result.put(startKey, "");
        queue.add(new int[]{sx, sy, sz});
        while (!queue.isEmpty() && result.size() < MAX_TREE_BLOCKS) {
            int[] pos = queue.poll();
            for (int[] d : TREE_DIRS_26) {
                int nx = pos[0] + d[0], ny = pos[1] + d[1], nz = pos[2] + d[2];
                if (Math.abs(nx - sx) > TREE_REACH || Math.abs(nz - sz) > TREE_REACH) continue;
                long key = packTreePos(nx, ny, nz);
                if (visited.contains(key)) continue;
                visited.add(key);
                try {
                    BlockType bt = world.getBlockType(nx, ny, nz);
                    if (bt == null) continue;
                    String bid = String.valueOf(bt.getId());
                    if (!bid.contains(searchKey)) continue;
                    result.put(key, bid);
                    if (!bid.toLowerCase().contains("leaves")) {
                        queue.add(new int[]{nx, ny, nz});
                    }
                } catch (Exception ignored) {}
            }
        }
        return result;
    }

    private static void fillAndReplant(World world, Collection<int[]> lowestPositions, String saplingId) {
        Set<Long> holePositions = new HashSet<>();
        String soilBlockId = "Soil_Dirt";
        int highestY = Integer.MIN_VALUE;
        int[] highestPos = null;
        for (int[] lowestPos : lowestPositions) {
            for (int i = 0; i < 10; i++) {
                int cx = lowestPos[0], cy = lowestPos[1] + i, cz = lowestPos[2];
                List<int[]> airNeighbours = new ArrayList<>();
                for (int[] d : new int[][]{{1,0,0},{-1,0,0},{0,0,1},{0,0,-1},{1,0,1},{1,0,-1},{-1,0,1},{-1,0,-1}}) {
                    try {
                        BlockType nb = world.getBlockType(cx + d[0], cy, cz + d[2]);
                        if (nb == null) continue;
                        String nbId = String.valueOf(nb.getId());
                        if ("null".equals(nbId)) nbId = "Empty";
                        if (nbId.equals("Empty") || nbId.startsWith("Plant_")) {
                            airNeighbours.add(new int[]{cx + d[0], cy, cz + d[2]});
                        } else if (soilBlockId.equals("Soil_Dirt") && nbId.startsWith("Soil_")) {
                            soilBlockId = nbId;
                        }
                    } catch (Exception ignored) {}
                }
                if (airNeighbours.size() > 5) break;
                try {
                    BlockType bt = world.getBlockType(cx, cy, cz);
                    String bid = bt != null ? String.valueOf(bt.getId()) : "Empty";
                    if ("null".equals(bid)) bid = "Empty";
                    if (bid.equals("Empty") || bid.startsWith("Plant_")) {
                        holePositions.add(packTreePos(cx, cy, cz));
                        for (int[] ap : airNeighbours) holePositions.add(packTreePos(ap[0], ap[1], ap[2]));
                        if (cy > highestY) {
                            highestY = cy;
                            highestPos = new int[]{cx, cy, cz};
                        }
                    }
                } catch (Exception ignored) {}
            }
        }
        for (long key : holePositions) {
            int[] pos = unpackTreePos(key);
            try { world.setBlock(pos[0], pos[1], pos[2], soilBlockId); } catch (Exception ignored) {}
        }
        if (highestPos != null && !saplingId.isEmpty()) {
            try { world.setBlock(highestPos[0], highestY + 1, highestPos[2], saplingId); } catch (Exception ignored) {}
        }
    }

    private static long packTreePos(int x, int y, int z) {
        return (((long)(x + 1048576)) << 42) | (((long)(y + 1048576)) << 21) | ((long)(z + 1048576));
    }

    private static int[] unpackTreePos(long key) {
        int x = (int)((key >> 42) & 0x1FFFFFL) - 1048576;
        int y = (int)((key >> 21) & 0x1FFFFFL) - 1048576;
        int z = (int)(key & 0x1FFFFFL) - 1048576;
        return new int[]{x, y, z};
    }
}
