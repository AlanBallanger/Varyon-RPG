package fr.varyon.vrpg.profession.forestier;

public final class ForestierXpTable {

    public static final long BASE_LOG_XP = 1L;
    public static final long BASE_FORAGE_XP = 1L;
    public static final String ESSENCE_ITEM_ID = "Forestier_Essence";

    private ForestierXpTable() {}

    private static String normalize(String rawId) {
        if (rawId == null) return null;
        String s = rawId;
        if (s.startsWith("hytale:")) s = s.substring(7);
        if (s.startsWith("*")) s = s.substring(1);
        return s;
    }

    public static boolean isLog(String rawId) {
        String s = normalize(rawId);
        if (s == null || s.isEmpty()) return false;
        String lower = s.toLowerCase();
        return lower.startsWith("wood_") && (lower.contains("_log") || lower.contains("_trunk") || lower.contains("_root"));
    }

    public static boolean isForageBlock(String rawId) {
        String s = normalize(rawId);
        if (s == null || s.isEmpty()) return false;
        String lower = s.toLowerCase();
        return lower.startsWith("plant_flower_") || lower.startsWith("plant_mushroom_");
    }

    public static boolean isFishItem(String rawId) {
        String s = normalize(rawId);
        if (s == null || s.isEmpty()) return false;
        String lower = s.toLowerCase();
        return lower.startsWith("food_fish_") || lower.startsWith("food_wildfish_");
    }

    public static String resolveLogItemId(String rawId) {
        String s = normalize(rawId);
        if (s == null || s.isEmpty()) return null;
        String[] parts = s.split("_");
        if (parts.length >= 3) return parts[0] + "_" + parts[1] + "_" + parts[2];
        return s;
    }

    public static String resolveForageItemId(String rawId) {
        String s = normalize(rawId);
        if (s == null || s.isEmpty()) return null;
        int idx = s.indexOf("_Block");
        if (idx < 0) idx = s.toLowerCase().indexOf("_block");
        if (idx >= 0) return s.substring(0, idx);
        return s;
    }
}
