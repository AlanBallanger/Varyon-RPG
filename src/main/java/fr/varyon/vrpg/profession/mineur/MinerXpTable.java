package fr.varyon.vrpg.profession.mineur;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public final class MinerXpTable {

    private static final int SCALE = 10;

    public static final Map<String, Long> XP;

    static {
        Map<String, Long> m = new HashMap<>();
        m.put("ore_adamantite",      2L * SCALE);
        m.put("ore_cobalt",          2L * SCALE);
        m.put("ore_copper",          1L * SCALE);
        m.put("ore_gold",            1L * SCALE);
        m.put("ore_iron",            1L * SCALE);
        m.put("ore_mithril",         3L * SCALE);
        m.put("ore_onyxium",         3L * SCALE);
        m.put("ore_prisma",          3L * SCALE);
        m.put("ore_silver",          1L * SCALE);
        m.put("ore_thorium",         1L * SCALE);
        m.put("rock_crystal_blue",   3L);
        m.put("rock_crystal_cyan",   3L);
        m.put("rock_crystal_green",  3L);
        m.put("rock_crystal_pink",   3L);
        m.put("rock_crystal_purple", 3L);
        m.put("rock_crystal_red",    3L);
        m.put("rock_crystal_white",  3L);
        m.put("rock_crystal_yellow", 3L);
        m.put("rock_gem_diamond",   25L * SCALE);
        m.put("rock_gem_emerald",    8L * SCALE);
        m.put("rock_gem_ruby",      12L * SCALE);
        m.put("rock_gem_sapphire",  12L * SCALE);
        m.put("rock_gem_topaz",     12L * SCALE);
        m.put("rock_gem_voidstone", 25L * SCALE);
        m.put("rock_gem_zephyr",    25L * SCALE);
        XP = Collections.unmodifiableMap(m);
    }

    private MinerXpTable() {}

    /**
     * Normalise un block ID brut du jeu vers la clé de la table XP.
     * Ex: "Ore_Iron_Stone" → "ore_iron"
     *     "Rock_Crystal_Blue" → "rock_crystal_blue"
     *     "Rock_Gem_Diamond_Stone" → "rock_gem_diamond"
     */
    public static String resolveXpKey(String rawId) {
        if (rawId == null || rawId.isEmpty()) return "";
        String id = rawId.startsWith("hytale:") ? rawId.substring(7) : rawId;
        String lower = id.toLowerCase();
        String[] parts = id.split("_");

        if (lower.startsWith("ore_") && parts.length >= 2) {
            return "ore_" + parts[1].toLowerCase();
        }
        if (lower.startsWith("rock_crystal_") && parts.length >= 3) {
            return "rock_crystal_" + parts[2].toLowerCase();
        }
        if (lower.startsWith("rock_gem_") && parts.length >= 3) {
            return "rock_gem_" + parts[2].toLowerCase();
        }
        return lower;
    }

    /**
     * Retourne l'item pur à dropper en bonus pour un bloc cassé.
     * Ex: "Ore_Iron_Stone" → "Ore_Iron"
     *     "Rock_Crystal_Blue" → "Rock_Crystal_Blue"
     *     "Rock_Gem_Diamond" → "Rock_Gem_Diamond"
     */
    public static String resolveOreItemId(String rawId) {
        if (rawId == null || rawId.isEmpty()) return null;
        String id = rawId.startsWith("hytale:") ? rawId.substring(7) : rawId;
        String lower = id.toLowerCase();
        String[] parts = id.split("_");

        if (lower.startsWith("ore_") && parts.length >= 2) {
            return parts[0] + "_" + parts[1];
        }
        if (lower.startsWith("rock_crystal_") && parts.length >= 3) {
            return parts[0] + "_" + parts[1] + "_" + parts[2];
        }
        if (lower.startsWith("rock_gem_") && parts.length >= 3) {
            return parts[0] + "_" + parts[1] + "_" + parts[2];
        }
        return null;
    }

    public static long getXp(String xpKey) {
        return XP.getOrDefault(xpKey, 0L);
    }
}
