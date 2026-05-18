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
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.server.core.command.system.CommandManager;
import com.hypixel.hytale.server.core.console.ConsoleSender;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.item.ItemComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import fr.varyon.vrpg.config.VrpgConfig;
import fr.varyon.vrpg.rpg.PlayerAccount;
import fr.varyon.vrpg.rpg.Profession;
import fr.varyon.vrpg.rpg.ProfessionManager;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class FarmerFKeyHarvestSystem extends EntityTickingSystem<EntityStore> {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final int CHECK_INTERVAL = 10;
    private static final int SCAN_RADIUS = 3;
    private static final long MANUAL_BREAK_TTL_MS = 3000L;
    private static final Random RANDOM = new Random();
    private static final double[] ETERNAL_SEED_CHANCES = {0.005, 0.0075, 0.01, 0.0125, 0.015};
    private static final double[] SNACK_CHANCES = {0.01, 0.0125, 0.015, 0.0175, 0.02};

    private static final Map<String, Long> MANUAL_BREAKS = new ConcurrentHashMap<>();

    private final ProfessionManager professionManager;
    private final FarmerComboTracker comboTracker;
    private final ComponentType<EntityStore, PlayerRef> playerRefType = PlayerRef.getComponentType();
    private final Map<UUID, Integer> tickCounters = new ConcurrentHashMap<>();
    private final Map<UUID, Map<String, String>> previousBlocks = new ConcurrentHashMap<>();

    public FarmerFKeyHarvestSystem(@Nonnull ProfessionManager professionManager,
                                   @Nonnull FarmerComboTracker comboTracker) {
        this.professionManager = professionManager;
        this.comboTracker = comboTracker;
    }

    public static void markManualBreak(int x, int y, int z) {
        long now = System.currentTimeMillis();
        MANUAL_BREAKS.put(x + "," + y + "," + z, now);
        MANUAL_BREAKS.put(x + "," + (y + 1) + "," + z, now);
    }

    public void removePlayer(@Nonnull UUID uuid) {
        tickCounters.remove(uuid);
        previousBlocks.remove(uuid);
    }

    @Nullable
    @Override
    public Query<EntityStore> getQuery() {
        return playerRefType;
    }

    @Override
    public void tick(float deltaTime, int index,
                     ArchetypeChunk<EntityStore> chunk,
                     Store<EntityStore> store,
                     CommandBuffer<EntityStore> commandBuffer) {

        PlayerRef playerRef = chunk.getComponent(index, playerRefType);
        if (playerRef == null) return;

        UUID uuid = playerRef.getUuid();
        int tc = tickCounters.merge(uuid, 1, Integer::sum);
        if (tc % CHECK_INTERVAL != 0) return;

        PlayerAccount acc = professionManager.getAccount(uuid);
        if (acc == null || !acc.isActive(Profession.FERMIER)) return;

        boolean dbg = VrpgConfig.isDebugTalents();

        try {
            Ref<EntityStore> ref = chunk.getReferenceTo(index);
            TransformComponent tcmp = store.getComponent(ref, TransformComponent.getComponentType());
            if (tcmp == null) return;
            Vector3d playerPos = tcmp.getPosition();

            Player player = null;
            try {
                player = playerRef.getComponent(Player.getComponentType());
            } catch (Exception ignored) {}
            if (player == null) return;
            World world = player.getWorld();
            if (world == null) return;

            int px = (int) playerPos.x, py = (int) playerPos.y, pz = (int) playerPos.z;

            long now = System.currentTimeMillis();
            MANUAL_BREAKS.entrySet().removeIf(e -> now - e.getValue() > MANUAL_BREAK_TTL_MS);

            Map<String, String> current = new HashMap<>();
            for (int x = px - SCAN_RADIUS; x <= px + SCAN_RADIUS; x++) {
                for (int y = py - SCAN_RADIUS; y <= py + SCAN_RADIUS; y++) {
                    for (int z = pz - SCAN_RADIUS; z <= pz + SCAN_RADIUS; z++) {
                        try {
                            String rawBid = String.valueOf(world.getBlockType(x, y, z).getId());
                            String bid = rawBid.startsWith("hytale:") ? rawBid.substring(7) : rawBid;
                            if (bid.startsWith("*")) bid = bid.substring(1);
                            if (bid.toLowerCase().contains("plant_crop_")) {
                                current.put(x + "," + y + "," + z, bid);
                            }
                        } catch (Exception ignored) {}
                    }
                }
            }

            Map<String, String> previous = previousBlocks.get(uuid);
            previousBlocks.put(uuid, current);
            if (previous == null) return;

            int xpRank = acc.getTalentRank(Profession.FERMIER, "1");
            int comboRank = acc.getTalentRank(Profession.FERMIER, "6");
            int lootRank = acc.getTalentRank(Profession.FERMIER, "0");
            int grainRank = acc.getTalentRank(Profession.FERMIER, "5");
            int snackRank = acc.getTalentRank(Profession.FERMIER, "9");

            List<String[]> harvested = new ArrayList<>();
            for (Map.Entry<String, String> entry : previous.entrySet()) {
                String key = entry.getKey();
                String prevBid = entry.getValue();
                String prevLower = prevBid.toLowerCase();

                if (!prevLower.contains("stagefinal")) continue;
                if (prevLower.contains("eternal")) continue;
                if (MANUAL_BREAKS.containsKey(key)) continue;

                String curBid = current.get(key);
                if (curBid == null || !curBid.equals(prevBid)) {
                    MANUAL_BREAKS.put(key, System.currentTimeMillis());
                    harvested.add(new String[]{key, prevBid});
                }
            }

            if (harvested.isEmpty()) return;

            String dbgId = dbg ? "[" + uuid.toString().substring(0, 8) + "|Faucille] " : null;
            if (dbg) LOGGER.atInfo().log(dbgId + "détectée — " + harvested.size() + " culture(s)");

            for (String[] h : harvested) {
                String key = h[0];
                String prevBid = h[1];

                String[] parts = key.split(",");
                int bx = Integer.parseInt(parts[0]);
                int by = Integer.parseInt(parts[1]);
                int bz = Integer.parseInt(parts[2]);
                Vector3d blockCenter = new Vector3d(bx + 0.5, by + 0.5, bz + 0.5);

                int comboCount = comboTracker.onCropHarvested(uuid);
                double comboPercent = comboRank > 0 ? (0.01 + (comboRank - 1) * 0.005) : 0.0;
                double comboBonus = comboRank > 0 ? comboCount * comboPercent : 0.0;
                if (dbg && comboRank > 0) LOGGER.atInfo().log(dbgId + "N6 CCombo combo=" + comboCount
                    + " bonus=" + String.format("%.3f%%", comboBonus * 100));

                double xpMult = 1.0 + xpRank * 0.05 + comboBonus;
                long finalXp = Math.round(FarmerXpTable.BASE_HARVEST_XP * xpMult);
                if (dbg) LOGGER.atInfo().log(dbgId + "XP +" + finalXp
                    + " (base=" + FarmerXpTable.BASE_HARVEST_XP + " × " + String.format("%.3f", xpMult) + ")"
                    + (xpRank > 0 ? " [N1 MainsTerreuses rank=" + xpRank + "]" : ""));
                professionManager.addXp(uuid, Profession.FERMIER, finalXp);

                if (lootRank > 0 && RANDOM.nextDouble() < lootRank * 0.05) {
                    String extraItemId = FarmerXpTable.resolveCropItemId(prevBid);
                    if (dbg) LOGGER.atInfo().log(dbgId + "N0 PaniersTropPleins PROC — item=" + extraItemId);
                    if (extraItemId != null) {
                        try {
                            dropItemAtBlock(commandBuffer, extraItemId, blockCenter);
                        } catch (Exception e) {
                            LOGGER.atWarning().withCause(e).log(dbgId + "N0 PaniersTropPleins drop ERREUR");
                        }
                    }
                }

                if (comboRank > 0 && comboBonus > 0 && RANDOM.nextDouble() < comboBonus) {
                    String comboItemId = FarmerXpTable.resolveCropItemId(prevBid);
                    if (dbg) LOGGER.atInfo().log(dbgId + "N6 CCombo loot PROC — item=" + comboItemId);
                    if (comboItemId != null) {
                        try {
                            dropItemAtBlock(commandBuffer, comboItemId, blockCenter);
                        } catch (Exception e) {
                            LOGGER.atWarning().withCause(e).log(dbgId + "N6 CCombo loot drop ERREUR");
                        }
                    }
                }

                if (grainRank > 0) {
                    double chance = ETERNAL_SEED_CHANCES[Math.min(grainRank, ETERNAL_SEED_CHANCES.length) - 1];
                    if (RANDOM.nextDouble() < chance) {
                        String eternalSeedId = FarmerXpTable.resolveEternalSeedId(prevBid);
                        if (dbg) LOGGER.atInfo().log(dbgId + "N5 GrainsSansFin PROC — item=" + eternalSeedId);
                        if (eternalSeedId != null) {
                            try {
                                dropItemAtBlock(commandBuffer, eternalSeedId, blockCenter);
                            } catch (Exception e) {
                                LOGGER.atWarning().withCause(e).log(dbgId + "N5 GrainsSansFin drop ERREUR");
                            }
                        }
                    }
                }

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
        } catch (Exception ignored) {}
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
