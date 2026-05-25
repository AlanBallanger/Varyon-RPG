package fr.varyon.vrpg.config;

import com.hypixel.hytale.logger.HytaleLogger;
import fr.varyon.vrpg.profession.chasseur.ChasseurXpTable;
import fr.varyon.vrpg.profession.fermier.FarmerXpTable;
import fr.varyon.vrpg.profession.forestier.ForestierXpTable;
import fr.varyon.vrpg.profession.mineur.MinerXpTable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class XpTableConfig {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private static Map<String, Long> minerXp = Collections.emptyMap();

    private XpTableConfig() {}

    public static Map<String, Long> getMinerXp() { return minerXp; }

    public static void load(Path dataDir) {
        Path file = dataDir.resolve("xp_tables.toml");
        if (!Files.exists(file)) {
            writeDefaults(file);
        }
        try {
            List<String> lines = Files.readAllLines(file);
            Map<String, Map<String, String>> sections = parseSections(lines);

            // [mineur]
            Map<String, Long> miner = new HashMap<>();
            for (Map.Entry<String, String> e : sections.getOrDefault("mineur", Collections.emptyMap()).entrySet()) {
                miner.put(e.getKey(), parseLong(e.getValue(), 0L));
            }
            minerXp = Collections.unmodifiableMap(miner);

            // [fermier]
            Map<String, Double> cropXp = new HashMap<>();
            for (Map.Entry<String, String> e : sections.getOrDefault("fermier", Collections.emptyMap()).entrySet()) {
                cropXp.put(e.getKey(), parseDouble(e.getValue(), 1.0));
            }
            FarmerXpTable.CROP_XP = cropXp;

            // [forestier]
            Map<String, Double> logXp = new HashMap<>();
            for (Map.Entry<String, String> e : sections.getOrDefault("forestier", Collections.emptyMap()).entrySet()) {
                logXp.put(e.getKey(), parseDouble(e.getValue(), 1.0));
            }
            ForestierXpTable.LOG_XP = logXp;

            // [tier_weights]
            Map<String, Double> tierWeights = new HashMap<>();
            for (Map.Entry<String, String> e : sections.getOrDefault("tier_weights", Collections.emptyMap()).entrySet()) {
                tierWeights.put(e.getKey(), parseDouble(e.getValue(), 0.0));
            }
            ChasseurXpTable.TIER_WEIGHTS = tierWeights;

            LOGGER.atInfo().log("[VaryonRPG] xp_tables.toml chargé — "
                + minerXp.size() + " entrées mineur"
                + ", fermier=" + FarmerXpTable.CROP_XP.size() + " crops"
                + ", forestier=" + ForestierXpTable.LOG_XP.size() + " bois"
                + ", tiers=" + ChasseurXpTable.TIER_WEIGHTS.size());
        } catch (IOException e) {
            LOGGER.atWarning().withCause(e).log("[VaryonRPG] Impossible de lire xp_tables.toml, valeurs par défaut utilisées");
        }
    }

    static Map<String, Map<String, String>> parseSections(List<String> lines) {
        Map<String, Map<String, String>> result = new HashMap<>();
        String currentSection = "";
        for (String raw : lines) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            if (line.startsWith("[") && line.endsWith("]")) {
                currentSection = line.substring(1, line.length() - 1).trim().toLowerCase();
                result.putIfAbsent(currentSection, new HashMap<>());
                continue;
            }
            int eq = line.indexOf('=');
            if (eq < 0) continue;
            String key = line.substring(0, eq).trim();
            String val = line.substring(eq + 1).trim();
            int commentIdx = val.indexOf(" #");
            if (commentIdx >= 0) val = val.substring(0, commentIdx).trim();
            if (!key.isEmpty() && !currentSection.isEmpty()) {
                result.get(currentSection).put(key, val);
            }
        }
        return result;
    }

    static long parseLong(String value, long fallback) {
        try { return Long.parseLong(value); } catch (NumberFormatException e) { return fallback; }
    }

    static double parseDouble(String value, double fallback) {
        try { return Double.parseDouble(value); } catch (NumberFormatException e) { return fallback; }
    }

    private static void writeDefaults(Path path) {
        String content =
            "# Varyon RPG — Tables XP des métiers\n" +
            "# Modifiable sans recompiler. Rechargement au prochain démarrage du serveur.\n" +
            "\n" +
            "[mineur]\n" +
            "ore_copper          = 1.5\n" +
            "ore_iron            = 1.6\n" +
            "ore_gold            = 1.8\n" +
            "ore_silver          = 1.8\n" +
            "ore_thorium         = 2.1\n" +
            "ore_cobalt          = 2.5\n" +
            "ore_adamantite      = 3.0\n" +
            "ore_mithril         = 0.5\n" +
            "ore_onyxium         = 0.5\n" +
            "ore_prisma          = 0.5\n" +
            "rock_crystal_blue   = 0.1\n" +
            "rock_crystal_cyan   = 0.1\n" +
            "rock_crystal_green  = 0.1\n" +
            "rock_crystal_pink   = 0.1\n" +
            "rock_crystal_purple = 0.1\n" +
            "rock_crystal_red    = 0.1\n" +
            "rock_crystal_white  = 0.1\n" +
            "rock_crystal_yellow = 0.1\n" +
            "rock_gem_emerald    = 10.0\n" +
            "rock_gem_ruby       = 15.0\n" +
            "rock_gem_sapphire   = 18.0\n" +
            "rock_gem_topaz      = 22.0\n" +
            "rock_gem_diamond    = 30.0\n" +
            "rock_gem_voidstone  = 30.0\n" +
            "rock_gem_zephyr     = 30.0\n" +
            "rock                = 0.1\n" +
            "dirt                = 0.1\n" +
            "\n" +
            "[fermier]\n" +
            "sunflower    = 0.3\n" +
            "wheat        = 0.3\n" +
            "lettuce      = 0.3\n" +
            "carrot       = 0.35\n" +
            "corn         = 0.35\n" +
            "cauliflower  = 0.4\n" +
            "turnip       = 0.4\n" +
            "aubergine    = 0.45\n" +
            "pumpkin      = 0.45\n" +
            "tomato       = 0.5\n" +
            "chilli       = 0.5\n" +
            "cotton       = 0.55\n" +
            "rice         = 0.55\n" +
            "onion        = 0.6\n" +
            "potato       = 0.6\n" +
            "\n" +
            "[forestier]\n" +
            "wood_amber        = 1.0\n" +
            "wood_ash          = 1.0\n" +
            "wood_aspen        = 1.0\n" +
            "wood_azure        = 1.0\n" +
            "wood_bamboo       = 1.0\n" +
            "wood_banyan       = 1.0\n" +
            "wood_beech        = 1.0\n" +
            "wood_birch        = 1.0\n" +
            "wood_blackwood    = 1.0\n" +
            "wood_bottletree   = 1.0\n" +
            "wood_burnt        = 1.0\n" +
            "wood_camphor      = 1.0\n" +
            "wood_cedar        = 1.0\n" +
            "wood_crystal      = 1.0\n" +
            "wood_darkwood     = 1.0\n" +
            "wood_deadwood     = 1.0\n" +
            "wood_dry          = 1.0\n" +
            "wood_drywood      = 1.0\n" +
            "wood_fig          = 1.0\n" +
            "wood_fir          = 1.0\n" +
            "wood_fire         = 1.0\n" +
            "wood_gnarled      = 1.0\n" +
            "wood_goldenwood   = 1.0\n" +
            "wood_greenwood    = 1.0\n" +
            "wood_gumboab      = 1.0\n" +
            "wood_hardwood     = 1.0\n" +
            "wood_ice          = 1.0\n" +
            "wood_jungle       = 1.0\n" +
            "wood_lightwood    = 1.0\n" +
            "wood_maple        = 1.0\n" +
            "wood_oak          = 1.0\n" +
            "wood_palm         = 1.0\n" +
            "wood_palo         = 1.0\n" +
            "wood_petrified    = 1.0\n" +
            "wood_poisoned     = 1.0\n" +
            "wood_redwood      = 1.0\n" +
            "wood_sallow       = 1.0\n" +
            "wood_softwood     = 1.0\n" +
            "wood_spiral       = 1.0\n" +
            "wood_stormbark    = 1.0\n" +
            "wood_tropicalwood = 1.0\n" +
            "wood_village      = 1.0\n" +
            "wood_windwillow   = 1.0\n" +
            "wood_wisteria     = 1.0\n" +
            "wood_corrupted    = 1.0\n" +
            "plant_flower      = 1.0\n" +
            "plant_mushroom    = 1.0\n" +
            "\n" +
            "[tier_weights]\n" +
            "neutral  = 1\n" +
            "minor    = 2\n" +
            "moderate = 4\n" +
            "major    = 8\n" +
            "hardened = 10\n" +
            "elite    = 15\n" +
            "master   = 20\n" +
            "champion = 30\n" +
            "boss     = 70\n";
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, content);
        } catch (IOException e) {
            LOGGER.atWarning().withCause(e).log("[VaryonRPG] Impossible d'écrire xp_tables.toml");
        }
    }
}
