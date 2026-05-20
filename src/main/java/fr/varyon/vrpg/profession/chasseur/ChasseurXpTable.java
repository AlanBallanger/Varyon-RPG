package fr.varyon.vrpg.profession.chasseur;

import java.util.Set;

public final class ChasseurXpTable {

    public static final double BASE_KILL_XP = 10.0;
    public static final String ESSENCE_ITEM_ID = "Chasseur_Essence";

    public static final Set<String> MEAT_HIDE_FEATHER = Set.of(
        "Food_Wildmeat_Raw",
        "Ingredient_Hide_Light",
        "Ingredient_Hide_Medium",
        "Ingredient_Hide_Heavy",
        "Ingredient_Hide_Ghost",
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
