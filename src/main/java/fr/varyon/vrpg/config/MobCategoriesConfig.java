package fr.varyon.vrpg.config;

import com.hypixel.hytale.logger.HytaleLogger;
import fr.varyon.vrpg.profession.chasseur.ChasseurXpTable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class MobCategoriesConfig {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private MobCategoriesConfig() {}

    public static void load(Path dataDir) {
        Path file = dataDir.resolve("mob_categories.toml");
        if (!Files.exists(file)) {
            writeDefaults(file);
        }
        try {
            List<String> lines = Files.readAllLines(file);
            Map<String, Map<String, String>> sections = XpTableConfig.parseSections(lines);

            Map<String, String> mobTiers = new HashMap<>();
            for (Map.Entry<String, String> e : sections.getOrDefault("mobs_categories", java.util.Collections.emptyMap()).entrySet()) {
                String val = e.getValue();
                if (val.startsWith("\"") && val.endsWith("\"")) val = val.substring(1, val.length() - 1);
                mobTiers.put(e.getKey(), val);
            }
            ChasseurXpTable.MOB_TIERS = mobTiers;

            LOGGER.atInfo().log("[VaryonRPG] mob_categories.toml chargé — " + mobTiers.size() + " mobs");
        } catch (IOException e) {
            LOGGER.atWarning().withCause(e).log("[VaryonRPG] Impossible de lire mob_categories.toml, valeurs par défaut utilisées");
        }
    }

    private static void writeDefaults(Path path) {
        String content =
            "# Varyon RPG — Catégories de mobs pour le Chasseur\n" +
            "# role_id = \"tier\"  — tiers disponibles : neutral, minor, moderate, major, hardened, elite, master, champion, boss\n" +
            "# Les poids par tier sont définis dans xp_tables.toml [tier_weights]\n" +
            "\n" +
            "[mobs_categories]\n" +
            "# MoreNPC\n" +
            "bramblekin                       = \"major\"\n" +
            "bramblekin_berserker             = \"major\"\n" +
            "bramblekin_fighter               = \"major\"\n" +
            "bramblekin_shaman                = \"major\"\n" +
            "grung_hopling                    = \"major\"\n" +
            "grung_hopling_archer             = \"major\"\n" +
            "grung_hopling_settled            = \"major\"\n" +
            "saurian_hunter                   = \"major\"\n" +
            "saurian_rogue                    = \"major\"\n" +
            "saurian_warrior                  = \"major\"\n" +
            "slothian_monk                    = \"major\"\n" +
            "slothian_scout                   = \"major\"\n" +
            "slothian_warrior                 = \"major\"\n" +
            "tuluk_fisherman                  = \"major\"\n" +
            "tuluk_king                       = \"major\"\n" +
            "tuluk_merchant                   = \"major\"\n" +
            "tuluk_pink                       = \"major\"\n" +
            "endgame_saurian_hunter           = \"major\"\n" +
            "endgame_saurian_rogue            = \"major\"\n" +
            "endgame_saurian_warrior          = \"major\"\n" +
            "grung_elder                      = \"major\"\n" +
            "# aquatic\n" +
            "bluegill                         = \"neutral\"\n" +
            "catfish                          = \"neutral\"\n" +
            "clownfish                        = \"neutral\"\n" +
            "eel_moray                        = \"neutral\"\n" +
            "frog                             = \"neutral\"\n" +
            "frog_blue                        = \"neutral\"\n" +
            "frog_green                       = \"neutral\"\n" +
            "frog_orange                      = \"neutral\"\n" +
            "jellyfish_blue                   = \"neutral\"\n" +
            "jellyfish_cyan                   = \"neutral\"\n" +
            "jellyfish_green                  = \"neutral\"\n" +
            "jellyfish_man_of_war             = \"neutral\"\n" +
            "jellyfish_red                    = \"neutral\"\n" +
            "jellyfish_yellow                 = \"neutral\"\n" +
            "lobster                          = \"neutral\"\n" +
            "minnow                           = \"neutral\"\n" +
            "pike                             = \"neutral\"\n" +
            "piranha                          = \"neutral\"\n" +
            "piranha_black                    = \"neutral\"\n" +
            "pufferfish                       = \"neutral\"\n" +
            "salmon                           = \"neutral\"\n" +
            "shellfish_lava                   = \"neutral\"\n" +
            "tang_blue                        = \"neutral\"\n" +
            "tang_chevron                     = \"neutral\"\n" +
            "tang_lemon_peel                  = \"neutral\"\n" +
            "tang_sailfin                     = \"neutral\"\n" +
            "temple_frog_blue                 = \"neutral\"\n" +
            "temple_frog_green                = \"neutral\"\n" +
            "temple_frog_orange               = \"neutral\"\n" +
            "trout_rainbow                    = \"neutral\"\n" +
            "whale                            = \"neutral\"\n" +
            "whale_humpback                   = \"neutral\"\n" +
            "crab                             = \"minor\"\n" +
            "trilobite                        = \"minor\"\n" +
            "trilobite_black                  = \"minor\"\n" +
            "frostgill                        = \"major\"\n" +
            "snapjaw                          = \"major\"\n" +
            "shark                            = \"major\"\n" +
            "shark_hammerhead                 = \"major\"\n" +
            "# birds\n" +
            "bird                             = \"neutral\"\n" +
            "bluebird                         = \"neutral\"\n" +
            "chicken                          = \"neutral\"\n" +
            "chicken_chick                    = \"neutral\"\n" +
            "chicken_desert                   = \"neutral\"\n" +
            "chicken_desert_chick             = \"neutral\"\n" +
            "crow                             = \"neutral\"\n" +
            "duck                             = \"neutral\"\n" +
            "finch                            = \"neutral\"\n" +
            "finch_green                      = \"neutral\"\n" +
            "flamingo                         = \"neutral\"\n" +
            "hawk                             = \"neutral\"\n" +
            "owl                              = \"neutral\"\n" +
            "owl_brown                        = \"neutral\"\n" +
            "owl_snow                         = \"neutral\"\n" +
            "parrot                           = \"neutral\"\n" +
            "penguin                          = \"neutral\"\n" +
            "pigeon                           = \"neutral\"\n" +
            "raven                            = \"neutral\"\n" +
            "skrill                           = \"neutral\"\n" +
            "skrill_chick                     = \"neutral\"\n" +
            "sparrow                          = \"neutral\"\n" +
            "temple_bluebird                  = \"neutral\"\n" +
            "temple_duck                      = \"neutral\"\n" +
            "temple_finch_green               = \"neutral\"\n" +
            "temple_owl_brown                 = \"neutral\"\n" +
            "tetrabird                        = \"neutral\"\n" +
            "turkey                           = \"neutral\"\n" +
            "turkey_chick                     = \"neutral\"\n" +
            "woodpecker                       = \"neutral\"\n" +
            "vulture                          = \"minor\"\n" +
            "# dinosaur\n" +
            "archaeopteryx                    = \"neutral\"\n" +
            "raptor                           = \"major\"\n" +
            "raptor_cave                      = \"major\"\n" +
            "snapdragon                       = \"major\"\n" +
            "trillodon                        = \"major\"\n" +
            "rex_cave                         = \"master\"\n" +
            "pterodactyl                      = \"neutral\"\n" +
            "# dragon\n" +
            "dragon                           = \"boss\"\n" +
            "dragon_fire                      = \"boss\"\n" +
            "dragon_frost                     = \"boss\"\n" +
            "dragon_storm                     = \"boss\"\n" +
            "endgame_fire_dragon              = \"champion\"\n" +
            "fire_dragon                      = \"champion\"\n" +
            "# feran\n" +
            "feran                            = \"neutral\"\n" +
            "feran_burrower                   = \"neutral\"\n" +
            "feran_civilian                   = \"neutral\"\n" +
            "feran_cub                        = \"neutral\"\n" +
            "feran_longtooth                  = \"neutral\"\n" +
            "feran_sharptooth                 = \"neutral\"\n" +
            "feran_windwalker                 = \"neutral\"\n" +
            "temple_feran                     = \"neutral\"\n" +
            "temple_feran_longtooth           = \"neutral\"\n" +
            "# fire\n" +
            "snail_magma                      = \"minor\"\n" +
            "spark_living                     = \"minor\"\n" +
            "golem_firesteel                  = \"elite\"\n" +
            "slug_magma                       = \"major\"\n" +
            "endgame_ghoul                    = \"elite\"\n" +
            "ghoul                            = \"elite\"\n" +
            "toad_rhino_magma                 = \"major\"\n" +
            "wraith                           = \"elite\"\n" +
            "endgame_onyxium_encounter        = \"champion\"\n" +
            "shadow_knight                    = \"champion\"\n" +
            "kf_fire_queen                    = \"champion\"\n" +
            "# goblin\n" +
            "goblin                           = \"major\"\n" +
            "goblin_hermit                    = \"major\"\n" +
            "goblin_lobber                    = \"major\"\n" +
            "goblin_lobber_patrol             = \"major\"\n" +
            "goblin_miner                     = \"major\"\n" +
            "goblin_miner_patrol              = \"major\"\n" +
            "goblin_scavenger                 = \"major\"\n" +
            "goblin_scavenger_battleaxe       = \"major\"\n" +
            "goblin_scavenger_sword           = \"major\"\n" +
            "goblin_scrapper                  = \"major\"\n" +
            "goblin_scrapper_patrol           = \"major\"\n" +
            "goblin_thief                     = \"major\"\n" +
            "goblin_thief_patrol              = \"major\"\n" +
            "goblin_ogre                      = \"major\"\n" +
            "goblin_ogre_tutorial             = \"major\"\n" +
            "goblin_duke                      = \"boss\"\n" +
            "# golem\n" +
            "golem_crystal_earth              = \"elite\"\n" +
            "golem_crystal_flame              = \"elite\"\n" +
            "golem_crystal_frost              = \"elite\"\n" +
            "golem_crystal_sand               = \"elite\"\n" +
            "golem_crystal_thunder            = \"elite\"\n" +
            "golem_guardian_void              = \"boss\"\n" +
            "# hedera\n" +
            "hedera                           = \"master\"\n" +
            "# insects\n" +
            "hatworm                          = \"neutral\"\n" +
            "larva_silk                       = \"minor\"\n" +
            "scorpion                         = \"major\"\n" +
            "# klops\n" +
            "klops_gentleman                  = \"major\"\n" +
            "klops_merchant                   = \"major\"\n" +
            "klops_merchant_patrol            = \"major\"\n" +
            "klops_merchant_wandering         = \"major\"\n" +
            "klops_miner                      = \"major\"\n" +
            "klops_miner_patrol               = \"major\"\n" +
            "temple_klops                     = \"major\"\n" +
            "temple_klops_merchant            = \"major\"\n" +
            "# kweebec\n" +
            "kweebec_elder                    = \"neutral\"\n" +
            "kweebec_merchant                 = \"neutral\"\n" +
            "kweebec_prisoner                 = \"neutral\"\n" +
            "kweebec_razorleaf                = \"neutral\"\n" +
            "kweebec_razorleaf_patrol         = \"neutral\"\n" +
            "kweebec_rootling                 = \"neutral\"\n" +
            "kweebec_sapling                  = \"neutral\"\n" +
            "kweebec_sapling_orange           = \"neutral\"\n" +
            "kweebec_sapling_pink             = \"neutral\"\n" +
            "kweebec_seedling                 = \"neutral\"\n" +
            "kweebec_sproutling               = \"neutral\"\n" +
            "kweebec_sproutling_patrol        = \"neutral\"\n" +
            "temple_kweebec                   = \"neutral\"\n" +
            "temple_kweebec_elder             = \"neutral\"\n" +
            "temple_kweebec_merchant          = \"neutral\"\n" +
            "temple_kweebec_razorleaf         = \"neutral\"\n" +
            "temple_kweebec_razorleaf_patrol  = \"neutral\"\n" +
            "temple_kweebec_razorleaf_patrol1 = \"neutral\"\n" +
            "temple_kweebec_razorleaf_patrol2 = \"neutral\"\n" +
            "temple_kweebec_razorleaf_patrol3 = \"neutral\"\n" +
            "temple_kweebec_razorleaf_patrol4 = \"neutral\"\n" +
            "temple_kweebec_razorleaf_patrol5 = \"neutral\"\n" +
            "temple_kweebec_rootling_static   = \"neutral\"\n" +
            "temple_kweebec_seedling          = \"neutral\"\n" +
            "temple_kweebec_seedling_static   = \"neutral\"\n" +
            "temple_kweebec_static            = \"neutral\"\n" +
            "# mammals\n" +
            "antelope                         = \"neutral\"\n" +
            "bat                              = \"neutral\"\n" +
            "bat_ice                          = \"neutral\"\n" +
            "bison                            = \"neutral\"\n" +
            "bison_calf                       = \"neutral\"\n" +
            "boar                             = \"neutral\"\n" +
            "boar_piglet                      = \"neutral\"\n" +
            "bunny                            = \"neutral\"\n" +
            "camel                            = \"neutral\"\n" +
            "camel_calf                       = \"neutral\"\n" +
            "cow                              = \"neutral\"\n" +
            "cow_calf                         = \"neutral\"\n" +
            "deer                             = \"neutral\"\n" +
            "deer_doe                         = \"neutral\"\n" +
            "deer_hind                        = \"neutral\"\n" +
            "deer_stag                        = \"neutral\"\n" +
            "donkey                           = \"neutral\"\n" +
            "endgame_rat_frost                = \"minor\"\n" +
            "fox                              = \"neutral\"\n" +
            "goat                             = \"neutral\"\n" +
            "goat_kid                         = \"neutral\"\n" +
            "grooble                          = \"neutral\"\n" +
            "horse                            = \"neutral\"\n" +
            "horse_foal                       = \"neutral\"\n" +
            "meerkat                          = \"neutral\"\n" +
            "mosshorn                         = \"major\"\n" +
            "mosshorn_plain                   = \"major\"\n" +
            "mouflon                          = \"neutral\"\n" +
            "mouflon_lamb                     = \"neutral\"\n" +
            "mouse                            = \"neutral\"\n" +
            "pig                              = \"neutral\"\n" +
            "pig_piglet                       = \"neutral\"\n" +
            "pig_wild                         = \"neutral\"\n" +
            "pig_wild_piglet                  = \"neutral\"\n" +
            "rabbit                           = \"neutral\"\n" +
            "ram                              = \"neutral\"\n" +
            "ram_lamb                         = \"neutral\"\n" +
            "sheep                            = \"neutral\"\n" +
            "sheep_lamb                       = \"neutral\"\n" +
            "squirrel                         = \"neutral\"\n" +
            "temple_bunny                     = \"neutral\"\n" +
            "temple_deer_doe                  = \"neutral\"\n" +
            "temple_deer_stag                 = \"neutral\"\n" +
            "temple_mithril_guard             = \"neutral\"\n" +
            "temple_squirrel                  = \"neutral\"\n" +
            "moose_bull                       = \"neutral\"\n" +
            "moose_cow                        = \"neutral\"\n" +
            "warthog                          = \"neutral\"\n" +
            "warthog_piglet                   = \"neutral\"\n" +
            "molerat                          = \"minor\"\n" +
            "rat                              = \"minor\"\n" +
            "armadillo                        = \"major\"\n" +
            "endgame_spirit_frost             = \"hardened\"\n" +
            "endgame_spirit_root              = \"hardened\"\n" +
            "# outlander\n" +
            "outlander                        = \"major\"\n" +
            "outlander_archer                 = \"major\"\n" +
            "outlander_berserker              = \"major\"\n" +
            "outlander_chief                  = \"major\"\n" +
            "outlander_cultist                = \"major\"\n" +
            "outlander_hunter                 = \"major\"\n" +
            "outlander_mage                   = \"major\"\n" +
            "outlander_marauder               = \"major\"\n" +
            "outlander_peon                   = \"major\"\n" +
            "outlander_priest                 = \"major\"\n" +
            "outlander_shaman                 = \"major\"\n" +
            "outlander_sorcerer               = \"major\"\n" +
            "outlander_stalker                = \"major\"\n" +
            "outlander_warrior                = \"major\"\n" +
            "outlander_brute                  = \"major\"\n" +
            "# predators\n" +
            "snail_frost                      = \"minor\"\n" +
            "wolf                             = \"major\"\n" +
            "bear_grizzly                     = \"hardened\"\n" +
            "bear_polar                       = \"hardened\"\n" +
            "emberwulf                        = \"elite\"\n" +
            "hyena                            = \"major\"\n" +
            "leopard_snow                     = \"major\"\n" +
            "moose                            = \"major\"\n" +
            "tiger                            = \"hardened\"\n" +
            "tiger_sabertooth                 = \"hardened\"\n" +
            "hound_bleached                   = \"major\"\n" +
            "yeti                             = \"elite\"\n" +
            "werewolf                         = \"master\"\n" +
            "# reptiles\n" +
            "gecko                            = \"neutral\"\n" +
            "cactee                           = \"minor\"\n" +
            "endgame_toad_frost               = \"major\"\n" +
            "snake_cobra                      = \"minor\"\n" +
            "snake_marsh                      = \"minor\"\n" +
            "snake_rattle                     = \"minor\"\n" +
            "tortoise                         = \"minor\"\n" +
            "lizard_sand                      = \"major\"\n" +
            "toad_minion                      = \"major\"\n" +
            "crocodile                        = \"major\"\n" +
            "toad_rhino                       = \"major\"\n" +
            "# scarak\n" +
            "scarak                           = \"major\"\n" +
            "scarak_defender                  = \"major\"\n" +
            "scarak_defender_patrol           = \"major\"\n" +
            "scarak_fighter                   = \"major\"\n" +
            "scarak_fighter_patrol            = \"major\"\n" +
            "scarak_fighter_royal_guard       = \"major\"\n" +
            "scarak_louse                     = \"major\"\n" +
            "scarak_queen_boss                = \"major\"\n" +
            "scarak_seeker                    = \"major\"\n" +
            "scarak_seeker_patrol             = \"major\"\n" +
            "dungeon_scarak_defender          = \"major\"\n" +
            "dungeon_scarak_defender_patrol   = \"major\"\n" +
            "dungeon_scarak_fighter           = \"major\"\n" +
            "dungeon_scarak_fighter_patrol    = \"major\"\n" +
            "dungeon_scarak_louse             = \"major\"\n" +
            "dungeon_scarak_seeker            = \"major\"\n" +
            "dungeon_scarak_seeker_patrol     = \"major\"\n" +
            "scarak_broodmother               = \"major\"\n" +
            "dungeon_scarak_broodmother       = \"major\"\n" +
            "dungeon_scarak_broodmother_young = \"major\"\n" +
            "# skeleton\n" +
            "horse_skeleton                   = \"major\"\n" +
            "horse_skeleton_armored           = \"major\"\n" +
            "skeleton                         = \"major\"\n" +
            "skeleton_archer                  = \"major\"\n" +
            "skeleton_archer_patrol           = \"major\"\n" +
            "skeleton_archer_wander           = \"major\"\n" +
            "skeleton_archmage                = \"major\"\n" +
            "skeleton_archmage_patrol         = \"major\"\n" +
            "skeleton_archmage_wander         = \"major\"\n" +
            "skeleton_fighter                 = \"major\"\n" +
            "skeleton_fighter_patrol          = \"major\"\n" +
            "skeleton_fighter_wander          = \"major\"\n" +
            "skeleton_frost_archer            = \"major\"\n" +
            "skeleton_frost_archer_patrol     = \"major\"\n" +
            "skeleton_frost_archer_wander     = \"major\"\n" +
            "skeleton_frost_archmage          = \"major\"\n" +
            "skeleton_frost_archmage_patrol   = \"major\"\n" +
            "skeleton_frost_archmage_wander   = \"major\"\n" +
            "skeleton_frost_fighter           = \"major\"\n" +
            "skeleton_frost_fighter_patrol    = \"major\"\n" +
            "skeleton_frost_fighter_wander    = \"major\"\n" +
            "skeleton_frost_knight            = \"major\"\n" +
            "skeleton_frost_knight_patrol     = \"major\"\n" +
            "skeleton_frost_knight_wander     = \"major\"\n" +
            "skeleton_frost_mage              = \"major\"\n" +
            "skeleton_frost_mage_patrol       = \"major\"\n" +
            "skeleton_frost_mage_wander       = \"major\"\n" +
            "skeleton_frost_ranger            = \"major\"\n" +
            "skeleton_frost_ranger_patrol     = \"major\"\n" +
            "skeleton_frost_ranger_wander     = \"major\"\n" +
            "skeleton_frost_scout             = \"major\"\n" +
            "skeleton_frost_scout_patrol      = \"major\"\n" +
            "skeleton_frost_scout_wander      = \"major\"\n" +
            "skeleton_frost_soldier           = \"major\"\n" +
            "skeleton_frost_soldier_patrol    = \"major\"\n" +
            "skeleton_frost_soldier_wander    = \"major\"\n" +
            "skeleton_incandescent_fighter         = \"major\"\n" +
            "skeleton_incandescent_fighter_patrol  = \"major\"\n" +
            "skeleton_incandescent_fighter_wander  = \"major\"\n" +
            "skeleton_incandescent_footman         = \"major\"\n" +
            "skeleton_incandescent_footman_patrol  = \"major\"\n" +
            "skeleton_incandescent_footman_wander  = \"major\"\n" +
            "skeleton_incandescent_head       = \"major\"\n" +
            "skeleton_incandescent_mage       = \"major\"\n" +
            "skeleton_incandescent_mage_patrol = \"major\"\n" +
            "skeleton_incandescent_mage_wander = \"major\"\n" +
            "skeleton_knight                  = \"major\"\n" +
            "skeleton_knight_patrol           = \"major\"\n" +
            "skeleton_knight_wander           = \"major\"\n" +
            "skeleton_mage                    = \"major\"\n" +
            "skeleton_mage_patrol             = \"major\"\n" +
            "skeleton_mage_wander             = \"major\"\n" +
            "skeleton_pirate                  = \"major\"\n" +
            "skeleton_pirate_captain          = \"major\"\n" +
            "skeleton_pirate_captain_patrol   = \"major\"\n" +
            "skeleton_pirate_captain_wander   = \"major\"\n" +
            "skeleton_pirate_gunner           = \"major\"\n" +
            "skeleton_pirate_gunner_patrol    = \"major\"\n" +
            "skeleton_pirate_gunner_wander    = \"major\"\n" +
            "skeleton_pirate_striker          = \"major\"\n" +
            "skeleton_pirate_striker_patrol   = \"major\"\n" +
            "skeleton_pirate_striker_wander   = \"major\"\n" +
            "skeleton_ranger                  = \"major\"\n" +
            "skeleton_ranger_patrol           = \"major\"\n" +
            "skeleton_ranger_wander           = \"major\"\n" +
            "skeleton_sand_archer             = \"major\"\n" +
            "skeleton_sand_archer_patrol      = \"major\"\n" +
            "skeleton_sand_archer_wander      = \"major\"\n" +
            "skeleton_sand_archmage           = \"major\"\n" +
            "skeleton_sand_archmage_patrol    = \"major\"\n" +
            "skeleton_sand_archmage_wander    = \"major\"\n" +
            "skeleton_sand_assassin           = \"major\"\n" +
            "skeleton_sand_assassin_patrol    = \"major\"\n" +
            "skeleton_sand_assassin_wander    = \"major\"\n" +
            "skeleton_sand_guard              = \"major\"\n" +
            "skeleton_sand_guard_patrol       = \"major\"\n" +
            "skeleton_sand_guard_wander       = \"major\"\n" +
            "skeleton_sand_mage               = \"major\"\n" +
            "skeleton_sand_mage_patrol        = \"major\"\n" +
            "skeleton_sand_mage_wander        = \"major\"\n" +
            "skeleton_sand_ranger             = \"major\"\n" +
            "skeleton_sand_ranger_patrol      = \"major\"\n" +
            "skeleton_sand_ranger_wander      = \"major\"\n" +
            "skeleton_sand_scout              = \"major\"\n" +
            "skeleton_sand_scout_patrol       = \"major\"\n" +
            "skeleton_sand_scout_wander       = \"major\"\n" +
            "skeleton_sand_soldier            = \"major\"\n" +
            "skeleton_sand_soldier_patrol     = \"major\"\n" +
            "skeleton_sand_soldier_wander     = \"major\"\n" +
            "skeleton_scout                   = \"major\"\n" +
            "skeleton_scout_patrol            = \"major\"\n" +
            "skeleton_scout_wander            = \"major\"\n" +
            "skeleton_soldier                 = \"major\"\n" +
            "skeleton_soldier_patrol          = \"major\"\n" +
            "skeleton_soldier_wander          = \"major\"\n" +
            "dungeon_skeleton_sand_archer     = \"major\"\n" +
            "dungeon_skeleton_sand_assassin   = \"major\"\n" +
            "dungeon_skeleton_sand_mage       = \"major\"\n" +
            "dungeon_skeleton_sand_soldier    = \"major\"\n" +
            "skeleton_burnt                   = \"major\"\n" +
            "skeleton_burnt_alchemist         = \"major\"\n" +
            "skeleton_burnt_alchemist_patrol  = \"major\"\n" +
            "skeleton_burnt_alchemist_wander  = \"major\"\n" +
            "skeleton_burnt_archer            = \"major\"\n" +
            "skeleton_burnt_archer_patrol     = \"major\"\n" +
            "skeleton_burnt_archer_wander     = \"major\"\n" +
            "skeleton_burnt_gunner            = \"major\"\n" +
            "skeleton_burnt_gunner_patrol     = \"major\"\n" +
            "skeleton_burnt_gunner_wander     = \"major\"\n" +
            "skeleton_burnt_knight            = \"major\"\n" +
            "skeleton_burnt_knight_patrol     = \"major\"\n" +
            "skeleton_burnt_knight_wander     = \"major\"\n" +
            "skeleton_burnt_lancer            = \"major\"\n" +
            "skeleton_burnt_lancer_patrol     = \"major\"\n" +
            "skeleton_burnt_lancer_wander     = \"major\"\n" +
            "skeleton_burnt_praetorian        = \"major\"\n" +
            "skeleton_burnt_praetorian_patrol = \"major\"\n" +
            "skeleton_burnt_praetorian_wander = \"major\"\n" +
            "skeleton_burnt_soldier           = \"major\"\n" +
            "skeleton_burnt_soldier_patrol    = \"major\"\n" +
            "skeleton_burnt_soldier_wander    = \"major\"\n" +
            "skeleton_burnt_wizard            = \"major\"\n" +
            "skeleton_burnt_wizard_patrol     = \"major\"\n" +
            "skeleton_burnt_wizard_wander     = \"major\"\n" +
            "# spider\n" +
            "spider                           = \"major\"\n" +
            "spider_cave                      = \"major\"\n" +
            "# trork\n" +
            "trork                            = \"major\"\n" +
            "trork_archer                     = \"major\"\n" +
            "trork_berserker                  = \"major\"\n" +
            "trork_bomber                     = \"major\"\n" +
            "trork_brawler                    = \"major\"\n" +
            "trork_chief                      = \"major\"\n" +
            "trork_chieftain                  = \"major\"\n" +
            "trork_doctor_witch               = \"major\"\n" +
            "trork_elite                      = \"major\"\n" +
            "trork_guard                      = \"major\"\n" +
            "trork_hunter                     = \"major\"\n" +
            "trork_mauler                     = \"major\"\n" +
            "trork_rider                      = \"major\"\n" +
            "trork_scout                      = \"major\"\n" +
            "trork_sentry                     = \"major\"\n" +
            "trork_sentry_patrol              = \"major\"\n" +
            "trork_shaman                     = \"major\"\n" +
            "trork_unarmed                    = \"major\"\n" +
            "trork_warlord                    = \"major\"\n" +
            "trork_warrior                    = \"major\"\n" +
            "trork_warrior_patrol             = \"major\"\n" +
            "# void\n" +
            "crawler_void                     = \"moderate\"\n" +
            "fen_stalker                      = \"moderate\"\n" +
            "larva_void                       = \"minor\"\n" +
            "eye_void                         = \"major\"\n" +
            "spectre_void                     = \"hardened\"\n" +
            "spirit_ember                     = \"hardened\"\n" +
            "spirit_frost                     = \"hardened\"\n" +
            "spirit_root                      = \"hardened\"\n" +
            "spawn_void                       = \"major\"\n" +
            "spirit_thunder                   = \"elite\"\n" +
            "# zombie\n" +
            "chicken_undead                   = \"moderate\"\n" +
            "pig_undead                       = \"moderate\"\n" +
            "cow_undead                       = \"major\"\n" +
            "zombie                           = \"hardened\"\n" +
            "zombie_frost                     = \"hardened\"\n" +
            "zombie_sand                      = \"hardened\"\n" +
            "zombie_burnt                     = \"major\"\n" +
            "endgame_zombie_aberrant          = \"champion\"\n" +
            "zombie_aberrant                  = \"champion\"\n" +
            "zombie_aberrant_big              = \"champion\"\n" +
            "zombie_aberrant_small            = \"champion\"\n";
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, content);
        } catch (IOException e) {
            LOGGER.atWarning().withCause(e).log("[VaryonRPG] Impossible d'écrire mob_categories.toml");
        }
    }
}
