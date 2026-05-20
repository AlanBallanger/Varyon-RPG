package fr.varyon.vrpg.profession.mineur;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.item.config.CraftingRecipe;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.ecs.CraftRecipeEvent;
import com.hypixel.hytale.server.core.inventory.MaterialQuantity;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import fr.varyon.vrpg.rpg.PlayerAccount;
import fr.varyon.vrpg.rpg.Profession;
import fr.varyon.vrpg.rpg.ProfessionManager;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.awt.Color;
import java.util.Set;

public final class BagCraftRestrictionSystem extends EntityEventSystem<EntityStore, CraftRecipeEvent.Pre> {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    public static final Set<String> ORE_BAG_IDS = Set.of(
        "Bag_Ore_Lesser",
        "NoCube_Bag_Ore", "NoCube_Bag_Ore_Lesser", "NoCube_Bag_Ore_Greater"
    );

    public static final Set<String> CROP_BAG_IDS = Set.of(
        "Bag_Crop_Lesser",
        "NoCube_Bag_Plant", "NoCube_Bag_Plant_Lesser", "NoCube_Bag_Plant_Greater"
    );

    public static final Set<String> WOOD_BAG_IDS = Set.of(
        "Bag_Wood_Lesser",
        "NoCube_Bag_Wood", "NoCube_Bag_Wood_Lesser", "NoCube_Bag_Wood_Greater"
    );

    private final ProfessionManager professionManager;
    private final ComponentType<EntityStore, PlayerRef> playerRefType = PlayerRef.getComponentType();

    public BagCraftRestrictionSystem(@Nonnull ProfessionManager professionManager) {
        super(CraftRecipeEvent.Pre.class);
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
            @Nonnull CraftRecipeEvent.Pre event) {

        CraftingRecipe recipe = event.getCraftedRecipe();
        MaterialQuantity output = recipe.getPrimaryOutput();
        if (output == null) return;
        String outputId = output.getItemId();
        if (outputId == null) return;

        boolean isOreBag = ORE_BAG_IDS.contains(outputId);
        boolean isCropBag = CROP_BAG_IDS.contains(outputId);
        boolean isWoodBag = WOOD_BAG_IDS.contains(outputId);
        if (!isOreBag && !isCropBag && !isWoodBag) return;

        PlayerRef playerRef = archetypeChunk.getComponent(index, playerRefType);
        if (playerRef == null) {
            LOGGER.atWarning().log("[BagRestrict] BLOCKED outputId=" + outputId + " — playerRef null");
            event.setCancelled(true);
            return;
        }

        String playerName = playerRef.getUsername() != null ? playerRef.getUsername() : playerRef.getUuid().toString().substring(0, 8);
        Player player = null;
        try { player = playerRef.getComponent(Player.getComponentType()); } catch (Exception ignored) {}
        boolean hasPermStar = player != null && player.hasPermission("*");

        PlayerAccount acc = professionManager.getAccount(playerRef.getUuid());
        boolean allowed;
        String message;
        if (isOreBag) {
            allowed = acc != null && acc.isActive(Profession.MINEUR) && acc.getTalentRank(Profession.MINEUR, "13") > 0;
            message = "Besace du Foreur — talent Mineur (nœud 13) requis.";
        } else if (isWoodBag) {
            allowed = acc != null && acc.isActive(Profession.FORESTIER) && acc.getTalentRank(Profession.FORESTIER, "13") > 0;
            message = "Besace du Forestier — talent Forestier (nœud 13) requis.";
        } else {
            allowed = acc != null && acc.isActive(Profession.FERMIER) && acc.getTalentRank(Profession.FERMIER, "16") > 0;
            message = "Besace du Paysan — talent Fermier (nœud 16) requis.";
        }

        LOGGER.atInfo().log("[BagRestrict] craft player=" + playerName
            + " outputId=" + outputId
            + " hasPerm*=" + hasPermStar
            + " acc=" + (acc != null ? "ok" : "null")
            + " allowed=" + allowed
            + " cancelled=" + !allowed);

        if (!allowed) {
            event.setCancelled(true);
            try {
                if (player != null) {
                    player.sendMessage(Message.raw(message).color(new Color(200, 50, 50)));
                }
            } catch (Exception ignored) {}
        }
    }
}
