package fr.varyon.vrpg.profession.mineur;

import fr.varyon.vrpg.config.XpTableConfig;

public final class MinerXpTable {

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
        return XpTableConfig.getMinerXp().getOrDefault(xpKey, 0L);
    }
}
