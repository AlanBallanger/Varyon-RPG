package fr.varyon.vrpg.profession.chasseur;

import java.util.Set;

public final class ChasseurXpTable {

    public static double BASE_KILL_XP = 1.0;
    public static double MIN_KILL_XP = 0.5;
    public static java.util.Map<String, String> MOB_TIERS = new java.util.HashMap<>();
    public static java.util.Map<String, Double> TIER_WEIGHTS = new java.util.HashMap<>();

    public static double getXp(String roleId) {
        if (roleId == null) return MIN_KILL_XP;
        String tier = MOB_TIERS.get(roleId.toLowerCase());
        if (tier == null) return MIN_KILL_XP;
        double weight = TIER_WEIGHTS.getOrDefault(tier.toLowerCase(), 0.0);
        return Math.max(MIN_KILL_XP, weight * BASE_KILL_XP);
    }
    public static final String ESSENCE_ITEM_ID = "Ingredient_Hide_Corrupted";

    public static final Set<String> MEAT_HIDE_FEATHER = Set.of(
        "Food_Wildmeat_Raw",
        "Ingredient_Hide_Light",
        "Ingredient_Hide_Medium",
        "Ingredient_Hide_Heavy",
        "Ingredient_Hide_Corrupted",
        "Ingredient_Feathers_Light"
    );

    public static final Set<String> EXOTIC_DROPS = Set.of(
        "Ingredient_Chitin_Sturdy",
        "Ingredient_Sac_Venom",
        "Ingredient_Bone_Fragment"
    );

    public static boolean isMeatHideFeather(String itemId) {
        return itemId != null && MEAT_HIDE_FEATHER.contains(itemId);
    }

    public static boolean isExoticDrop(String itemId) {
        return itemId != null && EXOTIC_DROPS.contains(itemId);
    }

    public static boolean isHuntingDrop(String itemId) {
        return isMeatHideFeather(itemId) || isExoticDrop(itemId);
    }

    public static boolean isHuntingWeapon(String itemId) {
        if (itemId == null) return false;
        String lower = itemId.toLowerCase();
        return lower.contains("bow") || lower.contains("crossbow") || lower.contains("dagger");
    }

    private ChasseurXpTable() {}
}
