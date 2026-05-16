package fr.varyon.vrpg.profession.mineur;

import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.ParticleUtil;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import fr.varyon.vrpg.rpg.PlayerAccount;
import fr.varyon.vrpg.rpg.Profession;
import fr.varyon.vrpg.rpg.ProfessionManager;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class MiningHelmet {

    public static final int DETECTION_RADIUS = 12;
    private static final long SCAN_COOLDOWN_MS = 10_000;

    private final Map<UUID, Long> lastScanTimes = new HashMap<>();
    private final ProfessionManager professionManager;

    public MiningHelmet(ProfessionManager professionManager) {
        this.professionManager = professionManager;
    }

    public int scanForOres(Player player, PlayerRef playerRef, UUID uuid,
                            Store<EntityStore> store, Ref<EntityStore> ref) {
        Long lastScan = lastScanTimes.get(uuid);
        long now = System.currentTimeMillis();
        if (lastScan != null && (now - lastScan) < SCAN_COOLDOWN_MS) return 0;

        PlayerAccount acc = professionManager.getAccount(uuid);
        if (acc == null) return 0;
        if (acc.getTalentRank(Profession.MINEUR, "10") <= 0) return 0;

        lastScanTimes.put(uuid, now);

        World world = player.getWorld();
        if (world == null) return 0;

        int oresFound = 0;
        try {
            TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
            if (transform == null) return 0;
            Vector3d playerPos = transform.getPosition();
            int px = (int) playerPos.x;
            int py = (int) playerPos.y;
            int pz = (int) playerPos.z;

            for (int x = px - DETECTION_RADIUS; x <= px + DETECTION_RADIUS; x++) {
                for (int y = py - DETECTION_RADIUS; y <= py + DETECTION_RADIUS; y++) {
                    for (int z = pz - DETECTION_RADIUS; z <= pz + DETECTION_RADIUS; z++) {
                        String blockId = world.getBlockType(x, y, z).getId();
                        if (isOre(blockId)) {
                            ParticleUtil.spawnParticleEffect(getParticleForOre(blockId),
                                new Vector3d(x + 0.5, y + 0.5, z + 0.5), store);
                            oresFound++;
                        }
                    }
                }
            }
        } catch (Exception ignored) {}

        return oresFound;
    }

    private boolean isOre(String blockId) {
        if (blockId == null || blockId.equals("Empty")) return false;
        return blockId.startsWith("Ore_") || blockId.contains("Crystal");
    }

    private String getParticleForOre(String blockId) {
        if (blockId.contains("Gold")) return "Varyon_OreGlow_Gold";
        if (blockId.contains("Iron")) return "Varyon_OreGlow_Iron";
        if (blockId.contains("Copper")) return "Varyon_OreGlow_Copper";
        if (blockId.contains("Cobalt")) return "Varyon_OreGlow_Cobalt";
        if (blockId.contains("Adamantite")) return "Varyon_OreGlow_Rare";
        if (blockId.contains("Mithril")) return "Varyon_OreGlow_Rare";
        if (blockId.contains("Crystal")) return "Varyon_OreGlow_Crystal";
        return "Varyon_OreReveal";
    }

    public void removePlayer(UUID uuid) {
        lastScanTimes.remove(uuid);
    }
}
