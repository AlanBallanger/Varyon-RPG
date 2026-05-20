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

    private record Rule(Profession profession, String nodeId, String talentName, int minRank) {}

    private static final Map<String, Rule> RESTRICTIONS = Map.ofEntries(
        Map.entry("Varyon_Miners_Helmet",            new Rule(Profession.MINEUR,    "12", "Œil de Taupe",       1)),
        Map.entry("SanAndreaP_Sprinkler_Iron",       new Rule(Profession.FERMIER,   "2",  "Maître Arroseur",    1)),
        Map.entry("SanAndreaP_Sprinkler_Thorium",    new Rule(Profession.FERMIER,   "2",  "Maître Arroseur",    2)),
        Map.entry("SanAndreaP_Sprinkler_Cobalt",     new Rule(Profession.FERMIER,   "2",  "Maître Arroseur",    3)),
        Map.entry("SanAndreaP_Sprinkler_Adamantite", new Rule(Profession.FERMIER,   "2",  "Maître Arroseur",    4)),
        Map.entry("SanAndreaP_Sprinkler_Mithril",    new Rule(Profession.FERMIER,   "2",  "Maître Arroseur",    5)),
        Map.entry("NoCube_Tool_Fertilizer_Lime",     new Rule(Profession.FERMIER,   "12", "Terre Nourricière",  1)),
        Map.entry("NoCube_Tool_Fertilizer_Bone",     new Rule(Profession.FERMIER,   "12", "Terre Nourricière",  2)),
        Map.entry("NoCube_Tool_Fertilizer_Seashell", new Rule(Profession.FERMIER,   "12", "Terre Nourricière",  3)),
        Map.entry("NoCube_Tool_Fertilizer_Elite",    new Rule(Profession.FERMIER,   "12", "Terre Nourricière",  4)),
        Map.entry("Bag_Crop_Lesser",                      new Rule(Profession.FERMIER,   "16", "Besace du Paysan",          1)),
        Map.entry("Bag_Ore_Lesser",                       new Rule(Profession.MINEUR,    "13", "Besace du Foreur",          1)),
        Map.entry("Bag_Potion_Lesser",                    new Rule(Profession.CHASSEUR,  "13", "Bourse du Traqueur",        1)),
        Map.entry("Bag_Wood_Lesser",                      new Rule(Profession.FORESTIER, "13", "Besace du Forestier",       1)),
        Map.entry("SanAndreaP_Sprinkler_Funnel",          new Rule(Profession.FERMIER,   "10", "Crop Circles",              1)),
        Map.entry("Miner_Drill_Kart",                     new Rule(Profession.MINEUR,    "11", "Wagon Express",             1)),
        Map.entry("Grappling_Hook_Iron",                  new Rule(Profession.FORESTIER, "9",  "Équipement Tridimensionnel", 1)),
        Map.entry("Grappling_Hook_Emerald",               new Rule(Profession.FORESTIER, "9",  "Équipement Tridimensionnel", 1)),
        Map.entry("Grappling_Hook_Diamond",               new Rule(Profession.FORESTIER, "9",  "Équipement Tridimensionnel", 1)),
        Map.entry("Grappling_Hook_Ruby",                  new Rule(Profession.FORESTIER, "9",  "Équipement Tridimensionnel", 1)),
        Map.entry("Grappling_Hook_Sapphire",              new Rule(Profession.FORESTIER, "9",  "Équipement Tridimensionnel", 1)),
        Map.entry("Grappling_Hook_Topaz",                 new Rule(Profession.FORESTIER, "9",  "Équipement Tridimensionnel", 1)),
        Map.entry("Grappling_Hook_Zephyr",                new Rule(Profession.FORESTIER, "9",  "Équipement Tridimensionnel", 1)),
        Map.entry("Grappling_Hook_Thorium",               new Rule(Profession.FORESTIER, "9",  "Équipement Tridimensionnel", 2)),
        Map.entry("Grappling_Hook_Cobalt",                new Rule(Profession.FORESTIER, "9",  "Équipement Tridimensionnel", 2)),
        Map.entry("Grappling_Hook_Adamantite",            new Rule(Profession.FORESTIER, "9",  "Équipement Tridimensionnel", 3))
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
        if (acc == null || !acc.isActive(rule.profession())) return false;
        return acc.getTalentRank(rule.profession(), rule.nodeId()) >= rule.minRank();
    }

    public static boolean hasRestriction(@Nonnull String itemId) {
        return RESTRICTIONS.containsKey(itemId);
    }

    @Nullable
    public static String getMessage(@Nonnull String itemId, @Nonnull String action) {
        Rule rule = RESTRICTIONS.get(itemId);
        if (rule == null) return null;
        return "Impossible de " + action + " cet objet : le talent de "
            + rule.profession().getDisplayName()
            + " «" + rule.talentName() + "»"
            + " lvl " + rule.minRank() + " est requis.";
    }

    @Nullable
    public static String getMessage(@Nonnull String itemId) {
        return getMessage(itemId, "utiliser");
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
            LOGGER.atWarning().log("[TalentRestrict] BLOCKED outputId=" + outputId + " — playerRef null");
            event.setCancelled(true);
            return;
        }

        String playerName = playerRef.getUsername() != null ? playerRef.getUsername() : playerRef.getUuid().toString().substring(0, 8);
        Player player = null;
        try { player = playerRef.getComponent(Player.getComponentType()); } catch (Exception ignored) {}
        boolean hasPermStar = player != null && player.hasPermission("*");

        PlayerAccount acc = professionManager.getAccount(playerRef.getUuid());
        boolean allowed = isAllowed(outputId, acc);

        LOGGER.atInfo().log("[TalentRestrict] craft player=" + playerName
            + " outputId=" + outputId
            + " hasPerm*=" + hasPermStar
            + " acc=" + (acc != null ? "ok" : "null")
            + " allowed=" + allowed
            + " cancelled=" + !allowed);

        if (!allowed) {
            event.setCancelled(true);
            try {
                if (player != null) {
                    player.sendMessage(Message.raw(getMessage(outputId, "crafter")).color(new Color(200, 50, 50)));
                }
            } catch (Exception ignored) {}
        }
    }
}
