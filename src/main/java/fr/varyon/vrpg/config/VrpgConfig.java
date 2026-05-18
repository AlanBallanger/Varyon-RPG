package fr.varyon.vrpg.config;

import com.hypixel.hytale.logger.HytaleLogger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class VrpgConfig {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private static boolean debugTalents = false;
    private static boolean debugProfessions = false;

    private VrpgConfig() {}

    public static boolean isDebugTalents() {
        return debugTalents;
    }

    public static boolean isDebugProfessions() {
        return debugProfessions;
    }

    public static void load(Path dataDir) {
        Path configFile = dataDir.resolve("config.toml");
        if (!Files.exists(configFile)) {
            writeDefaults(configFile);
        }
        try {
            List<String> lines = Files.readAllLines(configFile);
            Map<String, String> values = parseToml(lines);
            debugTalents = parseBoolean(values.getOrDefault("debug_talents", "false"));
            debugProfessions = parseBoolean(values.getOrDefault("debug_professions", "false"));
            LOGGER.atInfo().log("[VaryonRPG] Config chargée — debug_talents=" + debugTalents
                + " debug_professions=" + debugProfessions);
        } catch (IOException e) {
            LOGGER.atWarning().withCause(e).log("[VaryonRPG] Impossible de lire config.toml, valeurs par défaut utilisées");
        }
    }

    private static void writeDefaults(Path path) {
        String content =
            "# Varyon RPG — Configuration\n" +
            "\n" +
            "# Active les logs de debug pour chaque declenchement de talent Mineur\n" +
            "# Utile pour diagnostiquer les talents qui ne fonctionnent pas\n" +
            "debug_talents = false\n" +
            "\n" +
            "# Active les logs de debug pour les jauges XP des metiers (ProfessionActiveCard / ProfessionCatalogCard)\n" +
            "# Utile pour diagnostiquer l'affichage des barres de progression\n" +
            "debug_professions = false\n";
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, content);
        } catch (IOException e) {
            LOGGER.atWarning().withCause(e).log("[VaryonRPG] Impossible d'écrire config.toml");
        }
    }

    private static Map<String, String> parseToml(List<String> lines) {
        Map<String, String> result = new HashMap<>();
        for (String raw : lines) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#") || line.startsWith("[")) continue;
            int eq = line.indexOf('=');
            if (eq < 0) continue;
            String key = line.substring(0, eq).trim();
            String val = line.substring(eq + 1).trim();
            int commentIdx = val.indexOf(" #");
            if (commentIdx >= 0) val = val.substring(0, commentIdx).trim();
            if (!key.isEmpty()) result.put(key, val);
        }
        return result;
    }

    private static boolean parseBoolean(String value) {
        return "true".equalsIgnoreCase(value);
    }
}
