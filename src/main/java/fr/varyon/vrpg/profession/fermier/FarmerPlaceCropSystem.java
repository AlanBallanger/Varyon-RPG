package fr.varyon.vrpg.profession.fermier;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.ecs.PlaceBlockEvent;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import fr.varyon.vrpg.config.VrpgConfig;
import fr.varyon.vrpg.rpg.PlayerAccount;
import fr.varyon.vrpg.rpg.Profession;
import fr.varyon.vrpg.rpg.ProfessionManager;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.UUID;

public final class FarmerPlaceCropSystem extends EntityEventSystem<EntityStore, PlaceBlockEvent> {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private final ProfessionManager professionManager;
    private final ComponentType<EntityStore, PlayerRef> playerRefType = PlayerRef.getComponentType();

    public FarmerPlaceCropSystem(@Nonnull ProfessionManager professionManager) {
        super(PlaceBlockEvent.class);
        this.professionManager = professionManager;
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
            @Nonnull PlaceBlockEvent event) {

        // Node 4 — Bras Long : plante sur 5 blocs de long
        ItemStack eventItem = event.getItemInHand();
        if (eventItem == null || eventItem.isEmpty() || !eventItem.isValid()) return;

        String seedId = eventItem.getItemId();
        if (seedId == null || !seedId.startsWith("Plant_Seeds_")) return;

        String cropBlockId = deriveCropBlockId(seedId);
        if (cropBlockId == null) return;

        PlayerRef playerRef = archetypeChunk.getComponent(index, playerRefType);
        if (playerRef == null) return;
        UUID uuid = playerRef.getUuid();

        boolean dbg = VrpgConfig.isDebugTalents();

        PlayerAccount acc = professionManager.getAccount(uuid);
        if (acc == null || !acc.isActive(Profession.FERMIER)) {
            if (dbg) LOGGER.atInfo().log("[Fermier-DBG] BrasLong — joueur non actif fermier seed=" + seedId
                + " active0=" + (acc == null ? "null" : acc.getActiveSlot0())
                + " active1=" + (acc == null ? "null" : acc.getActiveSlot1()));
            return;
        }
        if (acc.getTalentRank(Profession.FERMIER, "4") <= 0) return;

        Player player = null;
        Inventory inventory = null;
        try {
            player = playerRef.getComponent(Player.getComponentType());
            if (player != null) inventory = player.getInventory();
        } catch (Exception ignored) {}
        if (player == null || inventory == null) return;

        World world = player.getWorld();
        if (world == null) return;

        Vector3i target = event.getTargetBlock();
        if (target == null) return;
        int bx = target.x, by = target.y, bz = target.z;

        // Direction cardinal depuis le joueur vers le bloc posé
        Ref<EntityStore> ref = archetypeChunk.getReferenceTo(index);
        Vector3d playerPos = null;
        try {
            TransformComponent tc = store.getComponent(ref, TransformComponent.getComponentType());
            if (tc != null) playerPos = tc.getPosition();
        } catch (Exception ignored) {}
        if (playerPos == null) return;

        double dxf = (bx + 0.5) - playerPos.x;
        double dzf = (bz + 0.5) - playerPos.z;
        int dx, dz;
        if (Math.abs(dxf) >= Math.abs(dzf)) {
            dx = dxf >= 0 ? 1 : -1;
            dz = 0;
        } else {
            dx = 0;
            dz = dzf >= 0 ? 1 : -1;
        }

        // Graines disponibles pour l'extension (snapshot event = avant consommation, -1 pour le bloc initial)
        int available = Math.max(0, eventItem.getQuantity() - 1);
        if (available == 0) return;

        String dbgId = dbg ? "[" + uuid.toString().substring(0, 8) + "|BrasLong] " : null;

        int consumed = 0;
        for (int i = 1; i <= 4 && consumed < available; i++) {
            int ex = bx + dx * i;
            int ez = bz + dz * i;
            try {
                String belowId = String.valueOf(world.getBlockType(ex, by - 1, ez).getId()).toLowerCase();
                String atId   = String.valueOf(world.getBlockType(ex, by,     ez).getId()).toLowerCase();

                boolean validSoil = belowId.startsWith("soil_dirt_tilled") || belowId.contains("planter");
                boolean isEmpty   = "empty".equals(atId);
                if (!validSoil || !isEmpty) continue;

                world.setBlock(ex, by, ez, cropBlockId);
                consumed++;
                if (dbg) LOGGER.atInfo().log(dbgId + "posé " + cropBlockId + " en (" + ex + "," + by + "," + ez + ")");
            } catch (Exception ignored) {}
        }

        if (consumed > 0) {
            try {
                byte slot = inventory.getActiveHotbarSlot();
                ItemStack held = inventory.getHotbar().getItemStack((short) slot);
                if (held != null && held.isValid() && !held.isEmpty()) {
                    int remaining = held.getQuantity() - consumed;
                    ItemStack newStack = remaining <= 0 ? null : held.withQuantity(remaining);
                    inventory.getHotbar().setItemStackForSlot((short) slot, newStack);
                }
            } catch (Exception e) {
                LOGGER.atWarning().withCause(e).log("[BrasLong] erreur consommation graines");
            }
        }
    }

    private static String deriveCropBlockId(String seedId) {
        // "Plant_Seeds_Wheat"         → "Plant_Crop_Wheat_Block"
        // "Plant_Seeds_Wheat_Eternal" → "Plant_Crop_Wheat_Block_Eternal"
        String rest = seedId.substring("Plant_Seeds_".length());
        if (rest.isEmpty()) return null;
        if (rest.endsWith("_Eternal")) {
            String name = rest.substring(0, rest.length() - "_Eternal".length());
            return "Plant_Crop_" + name + "_Block_Eternal";
        }
        return "Plant_Crop_" + rest + "_Block";
    }
}
