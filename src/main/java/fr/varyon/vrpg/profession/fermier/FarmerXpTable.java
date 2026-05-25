package fr.varyon.vrpg.profession.fermier;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public final class FarmerXpTable {

    /** @deprecated remplacé par getXp(rawId) — conservé pour compatibilité transitoire */
    @Deprecated
    public static double BASE_HARVEST_XP = 1.0;

    public static java.util.Map<String, Double> CROP_XP = new java.util.HashMap<>();

    private static final Set<String> CROP_PREFIXES;

    static {
        Set<String> s = new HashSet<>();
        s.add("plant_crop_aubergine");
        s.add("plant_crop_carrot");
        s.add("plant_crop_cauliflower");
        s.add("plant_crop_chilli");
        s.add("plant_crop_corn");
        s.add("plant_crop_cotton");
        s.add("plant_crop_lettuce");
        s.add("plant_crop_onion");
        s.add("plant_crop_potato");
        s.add("plant_crop_pumpkin");
        s.add("plant_crop_rice");
        s.add("plant_crop_tomato");
        s.add("plant_crop_turnip");
        s.add("plant_crop_wheat");
        CROP_PREFIXES = Collections.unmodifiableSet(s);
    }

    private FarmerXpTable() {}

    private static String normalize(String rawId) {
        if (rawId == null) return null;
        String s = rawId;
        if (s.startsWith("hytale:")) s = s.substring(7);
        if (s.startsWith("*")) s = s.substring(1);
        return s;
    }

    public static boolean isCrop(String rawId) {
        String id = normalize(rawId);
        if (id == null || id.isEmpty()) return false;
        String lower = id.toLowerCase();
        if (!lower.startsWith("plant_crop_") || !lower.contains("_block")) return false;
        int blockIdx = lower.indexOf("_block");
        return CROP_PREFIXES.contains(lower.substring(0, blockIdx));
    }

    public static String resolveCropItemId(String rawId) {
        String id = normalize(rawId);
        if (id == null || id.isEmpty()) return null;
        int idx = id.indexOf("_Block");
        if (idx < 0) idx = id.toLowerCase().indexOf("_block");
        if (idx < 0) return null;
        return id.substring(0, idx) + "_Item";
    }

    public static boolean isCropHarvestItem(String rawId) {
        String id = normalize(rawId);
        if (id == null || id.isEmpty()) return false;
        String lower = id.toLowerCase();
        if (!lower.startsWith("plant_crop_") || !lower.endsWith("_item")) return false;
        String withoutItem = lower.substring(0, lower.length() - "_item".length());
        return CROP_PREFIXES.contains(withoutItem);
    }

    public static String resolveEternalSeedFromItem(String rawId) {
        String id = normalize(rawId);
        if (!isCropHarvestItem(id)) return null;
        int itemIdx = id.lastIndexOf("_Item");
        if (itemIdx < 0) itemIdx = id.toLowerCase().lastIndexOf("_item");
        if (itemIdx < 0) return null;
        String cropName = id.substring("Plant_Crop_".length(), itemIdx);
        return "Plant_Seeds_" + cropName + "_Eternal";
    }

    public static String resolveEternalSeedId(String rawId) {
        String id = normalize(rawId);
        if (id == null || !id.toLowerCase().startsWith("plant_crop_")) return null;
        int blockIdx = id.indexOf("_Block");
        if (blockIdx < 0) blockIdx = id.toLowerCase().indexOf("_block");
        if (blockIdx < 0) return null;
        String cropName = id.substring("Plant_Crop_".length(), blockIdx);
        return "Plant_Seeds_" + cropName + "_Eternal";
    }

    public static String resolveCropKey(String rawId) {
        String id = normalize(rawId);
        if (id == null) return null;
        String lower = id.toLowerCase();
        if (!lower.startsWith("plant_crop_")) return null;
        String without = lower.substring("plant_crop_".length());
        int cut = without.indexOf('_');
        return cut >= 0 ? without.substring(0, cut) : without;
    }

    public static double getXp(String rawId) {
        String key = resolveCropKey(rawId);
        if (key == null) return BASE_HARVEST_XP;
        return CROP_XP.getOrDefault(key, BASE_HARVEST_XP);
    }
}
