package fr.varyon.vrpg.restriction;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.item.config.CraftingRecipe;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.ecs.CraftRecipeEvent;
import com.hypixel.hytale.server.core.inventory.MaterialQuantity;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import fr.varyon.vrpg.config.VrpgConfig;
import fr.varyon.vrpg.rpg.PlayerAccount;
import fr.varyon.vrpg.rpg.Profession;
import fr.varyon.vrpg.rpg.ProfessionManager;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.awt.Color;
import java.util.Map;

public final class TalentItemRestrictionSystem extends EntityEventSystem<EntityStore, CraftRecipeEvent.Pre> {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private record Rule(Profession profession, String nodeId, String message) {}

    private static final Map<String, Rule> RESTRICTIONS = Map.of(
        "Varyon_Miners_Helmet",      new Rule(Profession.MINEUR,  "12", "Casque du Mineur — talent Mineur (nœud 12 : Œil de Taupe) requis."),
        "SanAndreaP_Sprinkler_Iron", new Rule(Profession.FERMIER, "2",  "Arroseur — talent Fermier (nœud 2 : Maître Arroseur) requis.")
    );

    private final ProfessionManager professionManager;
    private final ComponentType<EntityStore, PlayerRef> playerRefType = PlayerRef.getComponentType();

    public TalentItemRestrictionSystem(@Nonnull ProfessionManager professionManager) {
        super(CraftRecipeEvent.Pre.class);
        this.professionManager = professionManager;
    }

    public static boolean isAllowed(@Nonnull String itemId, @Nullable PlayerAccount acc) {
        Rule rule = RESTRICTIONS.get(itemId);
        if (rule == null) return true;
        return acc != null && acc.isActive(rule.profession()) && acc.getTalentRank(rule.profession(), rule.nodeId()) > 0;
    }

    @Nullable
    public static String getMessage(@Nonnull String itemId) {
        Rule rule = RESTRICTIONS.get(itemId);
        return rule != null ? rule.message() : null;
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

        if (VrpgConfig.isDebugTalents()) LOGGER.atInfo().log("[TalentRestrict] craft outputId=" + outputId);
        if (!RESTRICTIONS.containsKey(outputId)) return;

        PlayerRef playerRef = archetypeChunk.getComponent(index, playerRefType);
        if (playerRef == null) {
            event.setCancelled(true);
            return;
        }

        PlayerAccount acc = professionManager.getAccount(playerRef.getUuid());
        if (!isAllowed(outputId, acc)) {
            event.setCancelled(true);
            try {
                Player player = playerRef.getComponent(Player.getComponentType());
                if (player != null) {
                    player.sendMessage(Message.raw(getMessage(outputId)).color(new Color(200, 50, 50)));
                }
            } catch (Exception ignored) {}
        }
    }
}
