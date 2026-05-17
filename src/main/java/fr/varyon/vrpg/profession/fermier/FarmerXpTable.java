package fr.varyon.vrpg.profession.fermier;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public final class FarmerXpTable {

    public static final long BASE_HARVEST_XP = 1L;

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

    public static boolean isCrop(String rawId) {
        if (rawId == null || rawId.isEmpty()) return false;
        String lower = rawId.toLowerCase();
        if (!lower.startsWith("plant_crop_") || !lower.contains("_block")) return false;
        int blockIdx = lower.indexOf("_block");
        return CROP_PREFIXES.contains(lower.substring(0, blockIdx));
    }

    public static String resolveCropItemId(String rawId) {
        if (rawId == null || rawId.isEmpty()) return null;
        int idx = rawId.indexOf("_Block");
        if (idx < 0) idx = rawId.toLowerCase().indexOf("_block");
        if (idx < 0) return null;
        return rawId.substring(0, idx) + "_Item";
    }

    public static String resolveEternalSeedId(String rawId) {
        // "Plant_Crop_Wheat_Block" ou "Plant_Crop_Wheat_Block_Eternal" → "Plant_Seeds_Wheat_Eternal"
        if (rawId == null || !rawId.toLowerCase().startsWith("plant_crop_")) return null;
        int blockIdx = rawId.indexOf("_Block");
        if (blockIdx < 0) blockIdx = rawId.toLowerCase().indexOf("_block");
        if (blockIdx < 0) return null;
        String cropName = rawId.substring("Plant_Crop_".length(), blockIdx);
        return "Plant_Seeds_" + cropName + "_Eternal";
    }
}
