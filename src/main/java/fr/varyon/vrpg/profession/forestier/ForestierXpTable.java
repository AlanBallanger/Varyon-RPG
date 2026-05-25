package fr.varyon.vrpg.profession.forestier;

public final class ForestierXpTable {

    public static long BASE_LOG_XP = 1L;
    public static long BASE_FORAGE_XP = 1L;
    public static java.util.Map<String, Double> LOG_XP = new java.util.HashMap<>();

    public static String resolveLogXpKey(String rawId) {
        String s = normalize(rawId);
        if (s == null || s.isEmpty()) return null;
        String lower = s.toLowerCase();
        if (!lower.startsWith("wood_")) return null;
        String[] parts = lower.split("_");
        return parts.length >= 2 ? "wood_" + parts[1] : null;
    }

    public static double getLogXp(String rawId) {
        String key = resolveLogXpKey(rawId);
        if (key == null) return BASE_LOG_XP;
        Double val = LOG_XP.get(key);
        return val != null ? val : BASE_LOG_XP;
    }

    public static double getForageXp(String rawId) {
        String s = normalize(rawId);
        if (s == null) return BASE_FORAGE_XP;
        String lower = s.toLowerCase();
        String key = lower.startsWith("plant_flower_") ? "plant_flower"
                   : lower.startsWith("plant_mushroom_") ? "plant_mushroom"
                   : null;
        if (key == null) return BASE_FORAGE_XP;
        Double val = LOG_XP.get(key);
        return val != null ? val : BASE_FORAGE_XP;
    }
    public static final String ESSENCE_ITEM_ID = "Log_Corrupted";

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
