package fr.varyon.vrpg.profession.mineur;

import com.hypixel.hytale.component.ArchetypeChunk;
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

    public static final Set<String> ORE_BAG_IDS = Set.of(
        "Bag_Ore_Lesser",
        "NoCube_Bag_Ore", "NoCube_Bag_Ore_Lesser", "NoCube_Bag_Ore_Greater"
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
        if (outputId == null || !ORE_BAG_IDS.contains(outputId)) return;

        PlayerRef playerRef = archetypeChunk.getComponent(index, playerRefType);
        if (playerRef == null) {
            event.setCancelled(true);
            return;
        }

        PlayerAccount acc = professionManager.getAccount(playerRef.getUuid());
        boolean allowed = acc != null
            && acc.isActive(Profession.MINEUR)
            && acc.getTalentRank(Profession.MINEUR, "13") > 0;

        if (!allowed) {
            event.setCancelled(true);
            try {
                Player player = playerRef.getComponent(Player.getComponentType());
                if (player != null) {
                    player.sendMessage(Message.raw(
                        "Besace du Foreur — talent Mineur (nœud 13) requis."
                    ).color(new Color(200, 50, 50)));
                }
            } catch (Exception ignored) {}
        }
    }
}
