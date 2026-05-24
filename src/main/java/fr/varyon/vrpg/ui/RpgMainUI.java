package fr.varyon.vrpg.ui;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.ui.Anchor;
import com.hypixel.hytale.server.core.ui.PatchStyle;
import com.hypixel.hytale.server.core.ui.Value;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import fr.varyon.vrpg.VaryonRpgPlugin;
import fr.varyon.vrpg.config.VrpgConfig;
import fr.varyon.vrpg.rpg.PlayerAccount;
import fr.varyon.vrpg.rpg.Profession;
import fr.varyon.vrpg.rpg.ProfessionManager;
import fr.varyon.vrpg.rpg.ProfessionProgress;
import fr.varyon.vrpg.rpg.TalentSoundNodes;
import fr.varyon.vrpg.rpg.LeaderboardEntry;
import fr.varyon.vrpg.rpg.XpBoost;
import fr.varyon.vrpg.rpg.XpCurve;

import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.Universe;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.logging.Logger;

public final class RpgMainUI extends InteractiveCustomUIPage<RpgMainUI.Data> {

    private static final Logger LOG = Logger.getLogger(RpgMainUI.class.getName());

    @FunctionalInterface
    private interface EdgeBuilderFn {
        int build(@Nonnull UICommandBuilder ui, int seg, int[][] slotLt);
    }

    private static final class SkillTreeDef {
        final String[][] nodes;
        final int[][] slotLt;
        final int[][] parentGroups;
        final int[] maxRanks;
        final EdgeBuilderFn edgeBuilder;
        @Nullable final String[][] nodeStatValues;

        SkillTreeDef(String[][] nodes, int[][] slotLt, int[][] parentGroups,
                     int[] maxRanks, EdgeBuilderFn edgeBuilder,
                     @Nullable String[][] nodeStatValues) {
            this.nodes = nodes;
            this.slotLt = slotLt;
            this.parentGroups = parentGroups;
            this.maxRanks = maxRanks;
            this.edgeBuilder = edgeBuilder;
            this.nodeStatValues = nodeStatValues;
        }
    }

    private static final int[][] SKILL_TREE_PARENT_GROUPS = new int[][] {
        {}, // 0
        {}, // 1
        {0, 1}, // 2
        {2}, // 3
        {2}, // 4
        {2}, // 5
        {3}, // 6
        {4}, // 7
        {5}, // 8
        {6, 7, 8}, // 9
        {9}, // 10
        {9}, // 11
        {2}, // 12
        {2}, // 13
        {9}, // 14
        {9}, // 15
    };

    private static final String ICON_BASE = "Pages/VaryonRpg/Icons/";
    private static final String TALENT_SOUND_ICON_ON = "Elements/Sound.png";
    private static final String TALENT_SOUND_ICON_OFF = "Elements/No_Sound.png";

    private static final String NODE_FILL = "#1A1F29FF";
    private static final String NODE_BORDER = "#4E576DFF";
    private static final String NODE_BORDER_SELECTION = "#B0B8C8FF";
    private static final String NODE_BORDER_ALLOCATED = "#31C677FF";

    private static final String NODE_VEIL = "#14182166";

    private static final PatchStyle NODE_FILL_STYLE =
        new PatchStyle().setColor(Value.of(NODE_FILL));

    private static final PatchStyle NODE_VEIL_STYLE =
        new PatchStyle().setColor(Value.of(NODE_VEIL));

    private static final PatchStyle CARD_BG_ACTIVE_STYLE =
        new PatchStyle(Value.of("Elements/HudPanelActive.png"), Value.of(10));
    private static final PatchStyle CARD_BG_INACTIVE_STYLE =
        new PatchStyle(Value.of("Elements/HudPanel.png"), Value.of(10));

    private static final String[] LEGACY_STATIC_EDGE_IDS = {
        "#SkillTreeEdgeRootStem",
        "#SkillTreeEdgeRootBranch",
        "#SkillTreeEdgeRootToNode6",
        "#SkillTreeEdgeRootToNode7",
        "#SkillTreeEdgeRootToNode1",
        "#SkillTreeEdgeRootToNode2",
        "#SkillTreeEdgeNode6ToNode9",
        "#SkillTreeEdgeNode2ToNode8",
    };

    private static final int RAIL = 4;
    private static final int STEM_DOWN_FROM_PARENT = 12;

    /** Edge segment slots in CharacterSkillTreePanel.ui (#SkillTreeEdgeSeg0 ..). */
    private static final int SKILL_TREE_EDGE_SEGMENTS = 48;

    private static final int ICON_SIZE = 40;
    private static final int SLOT = Math.round(76 * 0.8f);
    private static final int ICON_INSET = (SLOT - ICON_SIZE) / 2;
    private static final int FILL_INSET = 3;
    private static final int FILL_SIZE = SLOT - 2 * FILL_INSET;
    private static final int MAX_RANK_PER_NODE = 5;
    private static final int[] NODE_MAX_RANKS = {
        5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, // nodes 0–11
        1, 1, 1, 1                             // nodes 12–15
    };
    private static final int SKILL_POINTS_BUDGET = 35;

    private static final int PROFESSION_ACTIVE_SLOTS = 2;
    private static final int PROFESSION_BASE_COUNT = 4;


    private static final String[] PROFESSION_NAMES = {
        "Mineur",
        "Fermier",
        "Forestier",
        "Chasseur",
        "Forgeron",
        "Alchimiste",
        "Artisan",
        "Cuisinier",
    };

    /** Demo levels only — replace with persisted progression later. */
    private static final int[] PROFESSION_DEMO_LEVELS = {
        12,
        14,
        16,
        10,
        5,
        5,
        8,
        3,
    };

    /** Catalog indices shown in the active slots (Mineur + Fermier in demo). */
    private static final int[] ACTIVE_PROFESSION_INDICES = {0, 1};

    /** Specialized row maps to prerequisite base catalog index and required level. */
    private static final int[][] SPECIALIZED_PREREQ_BASE_AND_LEVEL = {
        {0, 15},
        {1, 15},
        {2, 15},
        {3, 15},
    };

    private static final int RANK_LABEL_W = 44;
    private static final int RANK_LABEL_H = 14;
    private static final int RANK_LABEL_GAP_TOP = -1;
    private static final int RANK_LABEL_SHIFT_RIGHT = (48 * SLOT + 38) / 76;

    private static final int[][] SLOT_LT = {
        {118, 32},  // 0
        {298, 32},  // 1
        {208, 125}, // 2
        {58,  218}, // 3
        {208, 218}, // 4
        {358, 218}, // 5
        {58,  309}, // 6
        {208, 309}, // 7
        {358, 309}, // 8
        {208, 402}, // 9
        {118, 495}, // 10
        {298, 495}, // 11
        {58,  125}, // 12 — gauche de 2
        {358, 125}, // 13 — droite de 2
        {58,  402}, // 14 — gauche de 9
        {358, 402}, // 15 — droite de 9
    };

    private static final String[][] TREE_NODES = {
        {"0",  "Poches Pleines",           "Passif", "Un vrai mineur ne repart jamais avec un seul caillou.",                    "Chance de doubler les ressources obtenues en minant.",                             "Jobs_Icons/Ore_Loot.png"},
        {"1",  "Front Poussiereux",        "Passif", "Chaque coup de pioche laisse une marque. Certaines deviennent du savoir.", "Augmente l’expérience gagnée en minant.",                                          "Jobs_Icons/Xp_Boost.png"},
        {"2",  "Minerai Fantomatique",      "Passif", "Au fond des galeries, certains minerais brillent d’une lueur qui n’appartient pas à ce monde.", "Chance d’obtenir un Minerai Fantomatique en récoltant.", "Jobs_Icons/Ore_Corrupted.png"},
        {"3",  "Pioche de Vétéran",        "Passif", "Les outils bien entretenus survivent aux mineurs.",                       "Réduit les pertes de durabilité de votre pioche.",                                 "Jobs_Icons/Pickaxe_Durability.png"},
        {"4",  "Minerai Immortel",         "Passif", "Certaines veines refusent simplement de disparaître.",                    "Chance qu’un minerai réapparaîsse immédiatement après récolte.",                  "Jobs_Icons/Ore_Respawn.png"},
        {"5",  "C-C-Combo",                "Passif", "Plus tu frappes vite, plus la montagne te récompense.",                   "Enchainer les minerais rapport de l'XP et du minerai bonus par combo (Max 8 combo)",    "Jobs_Icons/Combo_Mining.png"},
        {"6",  "Incassable !",             "Passif", "Ta pioche a vu pire.",                                                    "Chaque coup a une chance de gagner un point de durabilité plutôt que d’en perdre un.", "Jobs_Icons/Unbreakable.png"},
        {"7",  "Briseur de Roche",         "Passif", "Terre et pierre ne sont plus qu’un simple obstacle.",                    "Chance que les coups sur la roche ne consomment pas la durabilité de votre pioche.", "Jobs_Icons/Pickaxe_Durability_Stone.png"},
        {"8",  "Chant de la Veine",        "Actif",  "Une frappe parfaite suffit à réveiller tout le filon.",                  "Permet de miner instantanément toute une veine de minerai.",                      "Jobs_Icons/Vein_Sing.png"},
        {"9",  "Gardien de Pierre",        "Passif", "Sous certaines montagnes sommeillent encore les anciens protecteurs.",    "Chance d’invoquer un Gardien Minéral laissant un objet légendaire.",              "Jobs_Icons/Keeper_Miner.png"},
        {"10", "Œil du Prospecteur",       "Passif", "Les cristaux rares brillent différemment pour ceux qui savent regarder.", "Détecte les gemmes rares à proximité.",                                          "Jobs_Icons/Radar_Gem.png"},
        {"11", "Wagon Express",            "Actif",  "Tous les tunnels finissent par mener quelque part.",                      "Débloque une commande pour retourner instantanément à la surface.",               "Jobs_Icons/Extract_Drill.png"},
        {"12", "Œil de Taupe",             "Passif", "Dans les profondeurs, la lumière finit toujours par suivre les anciens.", "Équipe un casque de mineur diffusant une lumière permanente autour de vous.",     "Jobs_Icons/Miner_Helmet.png"},
        {"13", "Besace du Foreur",         "Passif", "Même la mort n’ose pas fouiller dans ce sac.",                            "Les minerais placés dans votre sac de mineur sont conservés après votre mort.",  "Jobs_Icons/Bag_Rock.png"},
        {"14", "Deux pour le Prix d’un",   "Passif", "Un coup de pioche rentable, enfin.",                                      "Casser un bloc de roche casse également le bloc de roche en dessous.",            "Jobs_Icons/Multi_Mining.png"},
        {"15", "Diplomatie Minière",       "Actif",  "Quand la roche refuse de bouger, il existe d’autres arguments.",         "Permet de déclencher une explosion contrôlée pour terraformer rapidement la zone.", "Jobs_Icons/Rock_Explosion.png"},
    };

    private static final String[][] MINEUR_NODE_STAT_VALUES = {
        {"5% loot",        "10% loot",        "15% loot",        "20% loot",        "25% loot"},           // 0
        {"5% XP",          "10% XP",          "15% XP",          "20% XP",          "25% XP"},             // 1
        {"1% minerai",     "1.5% minerai",    "2% minerai",      "2.5% minerai",    "3% minerai"},          // 2
        {"7% durabilité",  "14% durabilité",  "21% durabilité",  "28% durabilité",  "35% durabilité"},      // 3
        {"4% repop",       "8% repop",        "12% repop",       "16% repop",       "20% repop"},           // 4
        {"1% / combo (max +8%)", "1.5% / combo (max +12%)", "2% / combo (max +16%)", "2.5% / combo (max +20%)", "3% / combo (max +24%)"}, // 5
        {"5% gain dura",   "10% gain dura",   "15% gain dura",   "20% gain dura",   "25% gain dura"},       // 6
        {"15% durabilité", "30% durabilité",  "45% durabilité",  "60% durabilité",  "75% durabilité"},      // 7
        {"4min 20s recharge", "3min 20s recharge", "2min 20s recharge", "1min 40s recharge", "1min recharge"},  // 8
        {"5% invocation",  "10% invocation", "15% invocation",  "20% invocation", "25% invocation"},
        {"12 blocs",       "18 blocs",        "24 blocs",        "30 blocs",        "36 blocs"},            // 10
        {"3h recharge",    "2h30 recharge",   "2h recharge",     "1h30 recharge",   "1h recharge"},         // 11
        {"Lumière permanente activée"},    // 12
        {"Minerais conservés à la mort"}, // 13
        {"Zone 2×2 débloquée"},           // 14
        {"Explosion contrôlée débloquée"} // 15
    };

    private static final String[][] BASE_TREE_NODES = Arrays.copyOfRange(TREE_NODES, 0, 12);
    private static final int[][] BASE_TREE_SLOT_LT = Arrays.copyOfRange(SLOT_LT, 0, 12);
    private static final int[][] BASE_TREE_PARENT_GROUPS = Arrays.copyOfRange(SKILL_TREE_PARENT_GROUPS, 0, 12);
    private static final int[] BASE_TREE_MAX_RANKS = Arrays.copyOfRange(NODE_MAX_RANKS, 0, 12);

    private static final SkillTreeDef BASE_TREE = new SkillTreeDef(
        BASE_TREE_NODES, BASE_TREE_SLOT_LT, BASE_TREE_PARENT_GROUPS, BASE_TREE_MAX_RANKS,
        (ui, seg, lt) -> {
            seg = layoutMergeTwoToOne(ui, seg, cx(lt[0]), bot(lt[0]), cx(lt[1]), bot(lt[1]), cx(lt[2]), top(lt[2]));
            seg = layoutSplitOneToThree(ui, seg, cx(lt[2]), bot(lt[2]), cx(lt[3]), cx(lt[4]), cx(lt[5]), top(lt[3]));
            seg = layoutVerticalConnector(ui, seg, cx(lt[3]), bot(lt[3]), top(lt[6]));
            seg = layoutVerticalConnector(ui, seg, cx(lt[4]), bot(lt[4]), top(lt[7]));
            seg = layoutVerticalConnector(ui, seg, cx(lt[5]), bot(lt[5]), top(lt[8]));
            seg = layoutMergeThreeToOne(ui, seg, cx(lt[6]), bot(lt[6]), cx(lt[7]), bot(lt[7]), cx(lt[8]), bot(lt[8]), cx(lt[9]), top(lt[9]));
            seg = layoutSplitOneToTwo(ui, seg, cx(lt[9]), bot(lt[9]), cx(lt[10]), cx(lt[11]), top(lt[10]));
            return seg;
        },
        null
    );

    private static final SkillTreeDef MINEUR_TREE = new SkillTreeDef(
        TREE_NODES, SLOT_LT, SKILL_TREE_PARENT_GROUPS, NODE_MAX_RANKS,
        (ui, seg, lt) -> {
            seg = layoutMergeTwoToOne(ui, seg, cx(lt[0]), bot(lt[0]), cx(lt[1]), bot(lt[1]), cx(lt[2]), top(lt[2]));
            seg = layoutSplitOneToThree(ui, seg, cx(lt[2]), bot(lt[2]), cx(lt[3]), cx(lt[4]), cx(lt[5]), top(lt[3]));
            seg = layoutVerticalConnector(ui, seg, cx(lt[3]), bot(lt[3]), top(lt[6]));
            seg = layoutVerticalConnector(ui, seg, cx(lt[4]), bot(lt[4]), top(lt[7]));
            seg = layoutVerticalConnector(ui, seg, cx(lt[5]), bot(lt[5]), top(lt[8]));
            seg = layoutMergeThreeToOne(ui, seg, cx(lt[6]), bot(lt[6]), cx(lt[7]), bot(lt[7]), cx(lt[8]), bot(lt[8]), cx(lt[9]), top(lt[9]));
            seg = layoutSplitOneToTwo(ui, seg, cx(lt[9]), bot(lt[9]), cx(lt[10]), cx(lt[11]), top(lt[10]));
            seg = layoutHorizontalSiblings(ui, seg, cx(lt[2]), top(lt[2]) + SLOT / 2, cx(lt[12]), cx(lt[13]));
            seg = layoutHorizontalSiblings(ui, seg, cx(lt[9]), top(lt[9]) + SLOT / 2, cx(lt[14]), cx(lt[15]));
            return seg;
        },
        MINEUR_NODE_STAT_VALUES
    );

    private static final String[][] FERMIER_TREE_NODES = {
        {"0",  "Paniers Trop Pleins",    "Passif", "Les champs donnent davantage à ceux qui savent les écouter.",                     "Chance de doubler les récoltes obtenues.",                                        "Jobs_Icons/Crops_Loot.png"},
        {"1",  "Mains Terreuses",        "Passif", "Plus tes bottes sont sales, plus tu progresses.",                                  "Augmente l'expérience gagnée en récoltant.",                                      "Jobs_Icons/Xp_Boost.png"},
        {"2",  "Maître Arroseur",        "Passif", "Un vrai fermier hydrate ses cultures avec style.",                                  "Débloque les arroseurs automatiques.",                                            "Jobs_Icons/Sprinkler.png"},
        {"3",  "Graines Fantomatiques",  "Passif", "Ces semences translucides ne se révèlent qu'aux mains qui ont vraiment travaillé la terre.", "Chance d'obtenir des Graines Fantomatiques lors des récoltes.",         "Jobs_Icons/Seed_Corrupted.png"},
        {"4",  "Bras Long",              "Passif", "Pourquoi marcher jusqu'au champ quand le champ est déjà à portée ?",           "Plante sur 5 blocs de long.",                                                     "Jobs_Icons/Multiple_Crop.png"},
        {"5",  "Grains Sans Fin",        "Passif", "Le stock de graines devient un concept théorique.",                                "Chance d'obtenir une Graine Éternelle en récoltant n'importe quelle culture.",    "Jobs_Icons/Eternal_Seed_Plus.png"},
        {"6",  "C-C-Combo",              "Passif", "Plus tu récoltes vite, plus les champs te récompensent.",                         "Enchainer les récoltes rapporte de l'XP et du loot bonus par combo (Max 10 combo).", "Jobs_Icons/Combo_Harvsting.png"},
        {"7",  "Seigneur de l'Étable",  "Passif", "Même les bêtes savent reconnaître un maître.",                                  "Augmente les ressources obtenues sur l'élevage.",                                 "Jobs_Icons/Farm_Animals_Loot.png"},
        {"8",  "Faucille Éternelle",    "Passif", "Elle coupe encore. Toujours.",                                                      "Réduit l'usure de votre faucille.",                                               "Jobs_Icons/Sickle_Durability.png"},
        {"9",  "Casse-Croûte Fermier",  "Passif", "Un bon champ nourrit toujours son maître.",                                        "Récolter une culture a une chance de restaurer votre faim ou votre soif.",        "Jobs_Icons/Feed_Hydrate.png"},
        {"10", "Crop Circles",           "Passif", "Les champs commencent à s'organiser sans toi.",                                    "Débloque les semeurs de graines automatiques.",                                   "Jobs_Icons/Crop_Dispenser.png"},
        {"11", "Gardiens des Champs",    "Passif", "Les récoltes les plus riches attirent parfois d'anciens protecteurs.",             "Chance d'invoquer un Gardien des Champs laissant un objet légendaire.",          "Jobs_Icons/Scarecrow.png"},
        {"12", "Terre Nourricière",      "Passif", "La bonne terre, ça s'entretient avant de se mériter.",                          "Débloque des fertilisants de haute qualité boostant la vitesse de croissance des cultures.", "Jobs_Icons/Fertilizer.png"},
        {"16", "Besace du Paysan",       "Passif", "Un bon paysan ne rentre jamais les mains vides — ni sans un sac qui suit.",       "Un sac renforcé permettant de transporter des récoltes sans les perdre à la mort.", "Jobs_Icons/Bag_Seeds.png"},
    };

    private static final int[][] FERMIER_SLOT_LT = {
        {272, 32},
        {452, 32},
        {212, 125},
        {362, 125},
        {512, 125},
        {362, 218},
        {512, 218},
        {362, 309},
        {512, 309},
        {362, 402},
        {272, 495},
        {452, 495},
        {212, 218},
        {212, 309},
    };

    private static final int[][] FERMIER_PARENT_GROUPS = {
        {},
        {},
        {0, 1},
        {0, 1},
        {0, 1},
        {3},
        {4},
        {5},
        {6},
        {7, 8},
        {9},
        {9},
        {2},
        {12},
    };

    private static final int[] FERMIER_MAX_RANKS = {5, 5, 5, 5, 1, 5, 5, 5, 5, 5, 1, 5, 4, 1};

    private static final String[][] FERMIER_NODE_STAT_VALUES = {
        {"5% loot",              "10% loot",             "15% loot",             "20% loot",             "25% loot"},
        {"5% XP",                "10% XP",               "15% XP",               "20% XP",               "25% XP"},
        {"Arroseur en cuivre",   "Arroseur en fer",      "Arroseur en thorium",  "Arroseur en cobalt",   "Arroseur en adamantite"},
        {"1% graines",           "1.5% graines",         "2% graines",           "2.5% graines",         "3% graines"},
        {"Replantation 5 blocs activée"},
        {"0.5% graine éternelle",  "0.75% graine éternelle", "1% graine éternelle",   "1.25% graine éternelle", "1.5% graine éternelle"},
        {"1% / combo (max +10%)", "1.5% / combo (max +15%)", "2% / combo (max +20%)", "2.5% / combo (max +25%)", "3% / combo (max +30%)"},
        {"5% élevage",           "10% élevage",          "15% élevage",          "20% élevage",          "25% élevage"},
        {"15% durabilité",       "30% durabilité",       "45% durabilité",       "60% durabilité",       "75% durabilité"},
        {"1% de chance",         "1.25% de chance",      "1.5% de chance",       "1.75% de chance",      "2% de chance"},
        {"Replantation auto arroseurs activée"},
        {"5% invocation",      "10% invocation",       "15% invocation",       "20% invocation",       "25% invocation"},
        {"Fertilisant Chaux débloqué", "Fertilisant Osseux débloqué", "Fertilisant Coquillage débloqué", "Fertilisant Élite débloqué"},
        {"Besace du Paysan activée"},
    };

    private static final SkillTreeDef FERMIER_TREE = new SkillTreeDef(
        FERMIER_TREE_NODES, FERMIER_SLOT_LT, FERMIER_PARENT_GROUPS, FERMIER_MAX_RANKS,
        (ui, seg, lt) -> {
            seg = layoutFanTwoToThree(ui, seg, cx(lt[0]), bot(lt[0]), cx(lt[1]), bot(lt[1]),
                cx(lt[2]), cx(lt[3]), cx(lt[4]), top(lt[2]));
            seg = layoutVerticalConnector(ui, seg, cx(lt[2]), bot(lt[2]), top(lt[12]));
            seg = layoutVerticalConnector(ui, seg, cx(lt[3]), bot(lt[3]), top(lt[5]));
            seg = layoutVerticalConnector(ui, seg, cx(lt[4]), bot(lt[4]), top(lt[6]));
            seg = layoutVerticalConnector(ui, seg, cx(lt[5]), bot(lt[5]), top(lt[7]));
            seg = layoutVerticalConnector(ui, seg, cx(lt[6]), bot(lt[6]), top(lt[8]));
            seg = layoutMergeTwoToOne(ui, seg, cx(lt[7]), bot(lt[7]), cx(lt[8]), bot(lt[8]), cx(lt[9]), top(lt[9]));
            seg = layoutSplitOneToTwo(ui, seg, cx(lt[9]), bot(lt[9]), cx(lt[10]), cx(lt[11]), top(lt[10]));
            seg = layoutVerticalConnector(ui, seg, cx(lt[12]), bot(lt[12]), top(lt[13]));
            return seg;
        },
        FERMIER_NODE_STAT_VALUES
    );

    private static final String[][] FORESTIER_TREE_NODES = {
        {"0",  "Bûches Bien Lourdes",       "Passif", "Un arbre vide, c'est juste du mobilier.",                           "Chance de doubler les ressources obtenues en coupant des arbres.",                                           "Jobs_Icons/Logs_Loot.png"},
        {"1",  "Mains Écorchées",           "Passif", "L'expérience pousse rarement sans échardes.",                       "Augmente l'expérience gagnée en coupant des arbres et en récoltant dans la nature.",                        "Jobs_Icons/Xp_Boost.png"},
        {"2",  "Bûches Fantomatiques",       "Passif", "Certains arbres anciens laissent derrière eux plus que du bois.",    "Chance d'obtenir une Bûche Fantomatique en coupant des arbres.",                                              "Jobs_Icons/Log_Corrupted.png"},
        {"3",  "Hache du Survivant",        "Passif", "Elle coupe encore. Toujours.",                                      "Réduit l'usure de votre hache.",                                                                              "Jobs_Icons/Hatchet_Durability.png"},
        {"4",  "Cueilleur des Sous-Bois",   "Passif", "Les meilleures trouvailles poussent loin des chemins.",             "Augmente les ressources obtenues sur les fleurs et champignons.",                                             "Jobs_Icons/Mushroom_Loot.png"},
        {"5",  "C-C-Combo",                  "Passif", "Quand le rythme part, la forêt suit.",                             "Couper plusieurs arbres rapidement déclenche un combo augmentant les gains.",                                  "Jobs_Icons/Combo_Logging.png"},
        {"6",  "Rôdeur Sylvestre",          "Passif", "La forêt finit toujours par reconnaître les siens.",                "Augmente votre vitesse dans les forêts et réduit les dégâts de chute.",                                       "Jobs_Icons/Forest_Runner.png"},
        {"7",  "Pêche Miraculeuse",         "Passif", "Même les poissons veulent finir dans ton sac.",                     "Augmente les loot en pêchant.",                                                                               "Jobs_Icons/Fishs_Loot.png"},
        {"8",  "Yeux de Hibou",             "Passif", "La nuit appartient à ceux qui voient encore.",                      "Améliore votre vision nocturne dans les forêts.",                                                             "Jobs_Icons/Night_Vision.png"},
        {"9",  "Équipement Tridimensionnel","Actif",  "Le sol devient optionnel.",                                         "Débloque un grappin forestier permettant de se déplacer rapidement entre les arbres.",                         "Jobs_Icons/Grappling_Hook.png"},
        {"10", "Gardien Sylvestre",         "Passif", "Les forêts anciennes n'abandonnent jamais leurs protecteurs.",      "Chance d'invoquer un Gardien Sylvestre laissant un objet légendaire à sa mort.",                              "Jobs_Icons/Wolf.png"},
        {"12", "Retour aux Racines",        "Passif", "Chaque arbre tombé mérite un héritier.",                            "Replante automatiquement un arbre après l'avoir coupé.",                                                      "Jobs_Icons/Tree_Regrowth.png"},
        {"13", "Besace du Forestier",       "Passif", "Le bois coupé ne se perd pas avec le souffle.",                     "Un sac renforcé permettant de transporter du bois sans le perdre à la mort.",                                 "Jobs_Icons/Bag_Wood.png"},
        {"14", "Poumons de Loutre",         "Passif", "Tu passes plus de temps sous l'eau qu'au sec.",                     "Augmente le temps de respiration sous l'eau. Rang max : durée doublée (+100%).",                              "Jobs_Icons/Water_Breathing.png"},
        {"15", "Lit de Fortune",            "Passif", "Même les rôdeurs doivent dormir un jour.",                          "Les lits d'appoint restaurent davantage de vie et d'énergie.",                                               "Jobs_Icons/Bed_Regen.png"},
    };

    private static final int[][] FORESTIER_SLOT_LT = {
        {272, 32},
        {452, 32},
        {362, 125},
        {212, 218},
        {362, 218},
        {512, 218},
        {272, 309},
        {452, 309},
        {362, 402},
        {272, 495},
        {452, 495},
        {212, 125},
        {512, 125},
        {212, 402},
        {512, 402},
    };

    private static final int[][] FORESTIER_PARENT_GROUPS = {
        {},
        {},
        {0, 1},
        {2},
        {2},
        {2},
        {3, 4},
        {4, 5},
        {6, 7},
        {8},
        {8},
        {2},
        {2},
        {8},
        {8},
    };

    private static final int[] FORESTIER_MAX_RANKS = {5, 5, 5, 5, 5, 5, 5, 5, 5, 3, 5, 1, 1, 5, 5};

    private static final String[][] FORESTIER_NODE_STAT_VALUES = {
        {"5% loot",              "10% loot",              "15% loot",              "20% loot",              "25% loot"},
        {"5% XP",                "10% XP",                "15% XP",                "20% XP",                "25% XP"},
        {"1% bûche",             "1.5% bûche",            "2% bûche",              "2.5% bûche",            "3% bûche"},
        {"15% durabilité",       "30% durabilité",        "45% durabilité",        "60% durabilité",        "75% durabilité"},
        {"5% fleurs/champi",     "10% fleurs/champi",     "15% fleurs/champi",     "20% fleurs/champi",     "25% fleurs/champi"},
        {"1% / combo (max +10%)", "1.5% / combo (max +15%)", "2% / combo (max +20%)", "2.5% / combo (max +25%)", "3% / combo (max +30%)"},
        {"10% vit / 10% chute",  "15% vit / 15% chute",  "20% vit / 20% chute",  "25% vit / 25% chute",  "30% vit / 30% chute"},
        {"5% loot rare",         "10% loot rare",         "15% loot rare",         "20% loot rare",         "25% loot rare"},
        {"Vision faible",        "Vision modérée",        "Vision renforcée",      "Vision avancée",        "Vision parfaite"},
        {"Grappin Fer, Émeraude, Diamant, Rubis, Saphir, Topaze, Zéphyr débloqués", "Grappin Thorium & Cobalt débloqués", "Grappin Adamantite débloqué"},
        {"5% invocation",      "10% invocation",        "15% invocation",        "20% invocation",         "25% invocation"},
        {"Replantation auto activée"},
        {"Déracinage total activé"},
        {"+20% respiration",     "+40% respiration",      "+60% respiration",      "+80% respiration",      "+100% respiration"},
        {"5% récupération",      "10% récupération",      "15% récupération",      "20% récupération",      "25% récupération"},
    };

    private static final SkillTreeDef FORESTIER_TREE = new SkillTreeDef(
        FORESTIER_TREE_NODES, FORESTIER_SLOT_LT, FORESTIER_PARENT_GROUPS, FORESTIER_MAX_RANKS,
        (ui, seg, lt) -> {
            seg = layoutMergeTwoToOne(ui, seg, cx(lt[0]), bot(lt[0]), cx(lt[1]), bot(lt[1]), cx(lt[2]), top(lt[2]));
            seg = layoutSplitOneToThree(ui, seg, cx(lt[2]), bot(lt[2]), cx(lt[3]), cx(lt[4]), cx(lt[5]), top(lt[3]));
            seg = layoutFanThreeToTwo(ui, seg, cx(lt[3]), bot(lt[3]), cx(lt[4]), bot(lt[4]), cx(lt[5]), bot(lt[5]),
                cx(lt[6]), cx(lt[7]), top(lt[6]));
            seg = layoutMergeTwoToOne(ui, seg, cx(lt[6]), bot(lt[6]), cx(lt[7]), bot(lt[7]), cx(lt[8]), top(lt[8]));
            seg = layoutSplitOneToTwo(ui, seg, cx(lt[8]), bot(lt[8]), cx(lt[9]), cx(lt[10]), top(lt[9]));
            seg = layoutHorizontalSiblings(ui, seg, cx(lt[2]), top(lt[2]) + SLOT / 2, cx(lt[11]), cx(lt[12]));
            seg = layoutHorizontalSiblings(ui, seg, cx(lt[8]), top(lt[8]) + SLOT / 2, cx(lt[13]), cx(lt[14]));
            return seg;
        },
        FORESTIER_NODE_STAT_VALUES
    );

    private static final String[][] CHASSEUR_TREE_NODES = {
        {"0",  "Mains du Boucher",     "Passif", "Un vrai chasseur sait où couper.",                                          "Augmente les ressources obtenues sur la viande, le cuir et les plumes.",                        "Jobs_Icons/Leather_Loot.png"},
        {"1",  "Instinct Sauvage",     "Passif", "Plus la traque dure, plus le prédateur apprend.",                           "Augmente l'expérience gagnée en chassant.",                                                      "Jobs_Icons/Xp_Boost.png"},
        {"2",  "Cuir Fantomatique",    "Passif", "Les créatures les plus rares laissent parfois une dépouille translucide.", "Chance d'obtenir du Cuir Fantomatique sur les créatures.",                                       "Jobs_Icons/Hide_Corrupted.png"},
        {"3",  "Kit Renforcé",         "Passif", "Un bon arc encaisse autant que son porteur.",                               "Réduit l'usure de vos armes de chasse.",                                                         "Jobs_Icons/Hunter_Weapon_Durability.png"},
        {"4",  "Dépouilleur",          "Passif", "Les meilleures prises ne se laissent jamais partir sans récompense.",       "Chance de doubler les ressources obtenues sur les créatures.",                                   "Jobs_Icons/Monster_Loot.png"},
        {"5",  "Chasse Frénétique",    "Passif", "Quand la poursuite commence, difficile de s'arrêter.",                     "Éliminer plusieurs créatures rapidement déclenche un combo augmentant les gains.",               "Jobs_Icons/Combo_Killing.png"},
        {"6",  "Rôdeur des Dunes",     "Passif", "Le désert finit toujours par respecter ceux qui le traversent.",           "Augmente votre vitesse dans les zones désertiques et réduit les dégâts de chute.",               "Jobs_Icons/Forest_Runner.png"},
        {"7",  "Matériaux Exotiques",  "Passif", "Les créatures rares laissent rarement des matériaux ordinaires.",          "Augmente les chances d'obtenir de la chitine, du venin, des os et de la laine.",                 "Jobs_Icons/Exotic_Loot.png"},
        {"8",  "Second Souffle",       "Passif", "Un chasseur fatigué devient une proie.",                                    "Augmente votre endurance maximale.",                                                              "Jobs_Icons/Stamina_Max.png"},
        {"9",  "Yeux de Lynx",         "Passif", "La nuit cache les faibles, pas les chasseurs.",                            "Améliore votre vision nocturne.",                                                                 "Jobs_Icons/Night_Vision.png"},
        {"10", "Dompteur de Monstres", "Actif",  "Certaines créatures préfèrent obéir plutôt que mourir.",                   "Permet d'apprivoiser certaines créatures agressives.",                                           "Jobs_Icons/Tame_Tool.png"},
        {"11", "Prédateur Alpha",      "Passif", "Même les monstres savent reconnaître le sommet de la chaîne alimentaire.", "Chance d'invoquer un Prédateur Alpha laissant un objet légendaire à sa mort.",                  "Jobs_Icons/Rex.png"},
        {"12", "Chasseur_12",          "Passif", "À définir.",                                                                "À définir.",                                                                                      ""},
        {"13", "Bourse du Traqueur",   "Passif", "Un bon chasseur garde toujours ses trophées près de lui.",                 "Les ressources placées dans votre sac de chasse sont conservées après votre mort.",             "Jobs_Icons/Bag_Meat.png"},
        {"14", "Chasseur_14",          "Passif", "À définir.",                                                                "À définir.",                                                                                      ""},
        {"15", "Chasseur_15",          "Passif", "À définir.",                                                                "À définir.",                                                                                      ""},
    };

    private static final int[][] CHASSEUR_SLOT_LT = {
        {272, 32},
        {452, 32},
        {362, 125},
        {212, 218},
        {362, 218},
        {512, 218},
        {212, 309},
        {362, 309},
        {512, 309},
        {362, 402},
        {272, 495},
        {452, 495},
        {212, 125},
        {512, 125},
        {212, 402},
        {512, 402},
    };

    private static final int[][] CHASSEUR_PARENT_GROUPS = {
        {},
        {},
        {0, 1},
        {2},
        {2},
        {2},
        {3},
        {4},
        {5},
        {6, 7, 8},
        {9},
        {9},
        {2},
        {2},
        {9},
        {9},
    };

    private static final int[] CHASSEUR_MAX_RANKS = {5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 1, 5, 1, 1, 1, 1};

    private static final String[][] CHASSEUR_NODE_STAT_VALUES = {
        {"5% viande/cuir/plumes",      "10% viande/cuir/plumes",      "15% viande/cuir/plumes",      "20% viande/cuir/plumes",      "25% viande/cuir/plumes"},
        {"5% XP",                      "10% XP",                      "15% XP",                      "20% XP",                      "25% XP"},
        {"1% cuir",                    "1.5% cuir",                   "2% cuir",                     "2.5% cuir",                   "3% cuir"},
        {"15% durabilité",             "30% durabilité",              "45% durabilité",              "60% durabilité",              "75% durabilité"},
        {"5% loot",                    "10% loot",                    "15% loot",                    "20% loot",                    "25% loot"},
        {"5% XP & loot",               "10% XP & loot",               "15% XP & loot",               "20% XP & loot",               "25% XP & loot"},
        {"4% vit / 10% chute",         "8% vit / 20% chute",          "12% vit / 30% chute",         "16% vit / 40% chute",         "20% vit / 50% chute"},
        {"5% exotiques",               "10% exotiques",               "15% exotiques",               "20% exotiques",               "25% exotiques"},
        {"5% endurance",               "10% endurance",               "15% endurance",               "20% endurance",               "25% endurance"},
        {"Vision faible",              "Vision modérée",              "Vision renforcée",            "Vision avancée",              "Vision parfaite"},
        {"Apprivoisement activé"},
        {"5% invocation",            "10% invocation",              "15% invocation",              "20% invocation",              "25% invocation"},
        {"À définir"},
        {"Bourse de chasse activée"},
        {"À définir"},
        {"À définir"},
    };

    private static final SkillTreeDef CHASSEUR_TREE = new SkillTreeDef(
        CHASSEUR_TREE_NODES, CHASSEUR_SLOT_LT, CHASSEUR_PARENT_GROUPS, CHASSEUR_MAX_RANKS,
        (ui, seg, lt) -> {
            seg = layoutMergeTwoToOne(ui, seg, cx(lt[0]), bot(lt[0]), cx(lt[1]), bot(lt[1]), cx(lt[2]), top(lt[2]));
            seg = layoutSplitOneToThree(ui, seg, cx(lt[2]), bot(lt[2]), cx(lt[3]), cx(lt[4]), cx(lt[5]), top(lt[3]));
            seg = layoutVerticalConnector(ui, seg, cx(lt[3]), bot(lt[3]), top(lt[6]));
            seg = layoutVerticalConnector(ui, seg, cx(lt[4]), bot(lt[4]), top(lt[7]));
            seg = layoutVerticalConnector(ui, seg, cx(lt[5]), bot(lt[5]), top(lt[8]));
            seg = layoutMergeThreeToOne(ui, seg, cx(lt[6]), bot(lt[6]), cx(lt[7]), bot(lt[7]), cx(lt[8]), bot(lt[8]), cx(lt[9]), top(lt[9]));
            seg = layoutSplitOneToTwo(ui, seg, cx(lt[9]), bot(lt[9]), cx(lt[10]), cx(lt[11]), top(lt[10]));
            seg = layoutHorizontalSiblings(ui, seg, cx(lt[2]), top(lt[2]) + SLOT / 2, cx(lt[12]), cx(lt[13]));
            seg = layoutHorizontalSiblings(ui, seg, cx(lt[9]), top(lt[9]) + SLOT / 2, cx(lt[14]), cx(lt[15]));
            return seg;
        },
        CHASSEUR_NODE_STAT_VALUES
    );

    private final PlayerRef playerRef;
    private String activeTab = "character";
    private Profession classementFilter = Profession.MINEUR;
    private boolean classementAsc = false;
    private int selectedNode = 0;
    private int hoveredNode = -1;
    private int selectedBonusNode = -1;
    private int hoveredBonusNode = -1;
    private final int[] skillRanks = new int[32];
    private boolean isEditMode = false;
    private int[] pendingRanks = null;
    private int[] pendingBonusRanks = null;
    private String reconvertSourceId = null;
    private int talentTreeSlotIndex = 0;

    private int adminPlayerIndex = 0;
    private int adminProfIndex = 0;

    public RpgMainUI(@Nonnull PlayerRef playerRef) {
        super(playerRef, CustomPageLifetime.CanDismiss, Data.CODEC);
        this.playerRef = playerRef;
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref,
                      @Nonnull UICommandBuilder uiBuilder,
                      @Nonnull UIEventBuilder eventBuilder,
                      @Nonnull Store<EntityStore> store) {
        boolean isAdmin = isAdmin();

        uiBuilder.append("CharacterPage.ui");
        uiBuilder.append("#CharacterTabMount", "CharacterTabProfession.ui");
        uiBuilder.append("#SkillsTabMount", "CharacterTabSkills.ui");
        uiBuilder.append("#ArtisansTabMount", "CharacterTabArtisans.ui");
        uiBuilder.append("#ClassementTabMount", "CharacterTabClassement.ui");
        if (isAdmin) {
            uiBuilder.append("#AdminTabMount", "CharacterTabAdmin.ui");
        }

        uiBuilder.set("#CharacterTabContent.Visible", "character".equals(activeTab));
        uiBuilder.set("#SkillsTabContent.Visible", "skills".equals(activeTab));
        uiBuilder.set("#ArtisansTabContent.Visible", "artisans".equals(activeTab));
        uiBuilder.set("#ClassementTabContent.Visible", "classement".equals(activeTab));
        uiBuilder.set("#AdminTabContent.Visible", "admin".equals(activeTab));

        uiBuilder.set("#TabCharacterUnderline.Visible", "character".equals(activeTab));
        uiBuilder.set("#TabArtisansUnderline.Visible", "artisans".equals(activeTab));
        uiBuilder.set("#TabClassementUnderline.Visible", "classement".equals(activeTab));
        uiBuilder.set("#TabAdminUnderline.Visible", "admin".equals(activeTab));
        uiBuilder.set("#TabAdminButton.Visible", isAdmin);

        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#TabCharacterButton",
            EventData.of("Action", "tab").append("Tab", "character"), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#TabArtisansButton",
            EventData.of("Action", "tab").append("Tab", "artisans"), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#TabClassementButton",
            EventData.of("Action", "tab").append("Tab", "classement"), false);
        if (isAdmin) {
            eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#TabAdminButton",
                EventData.of("Action", "tab").append("Tab", "admin"), false);
        }

        if ("character".equals(activeTab)) {
            populateCharacterProfessions(uiBuilder, eventBuilder);
        } else if ("skills".equals(activeTab)) {
            populateSketchSkillTree(uiBuilder, eventBuilder);
        } else if ("classement".equals(activeTab)) {
            populateClassement(uiBuilder, eventBuilder);
        } else if ("admin".equals(activeTab) && isAdmin) {
            populateAdmin(uiBuilder, eventBuilder);
        }
    }

    private static final int PROFESSION_CATALOG_SLOTS = 8;

    private static final Profession[] CATALOG_ORDER = {
        Profession.MINEUR,
        Profession.FERMIER,
        Profession.FORESTIER,
        Profession.CHASSEUR,
        Profession.FORGERON,
        Profession.ALCHIMISTE,
        Profession.ARTISAN,
        Profession.CUISINIER,
    };

    private PlayerAccount currentAccount() {
        ProfessionManager m = VaryonRpgPlugin.getInstance().getProfessionManager();
        if (m == null) return null;
        m.ensureAccount(playerRef.getUuid(), playerRef.getUsername());
        return m.getAccount(playerRef.getUuid());
    }

    private boolean isAdmin() {
        try {
            List<com.hypixel.hytale.server.core.universe.PlayerRef> players = new ArrayList<>(Universe.get().getPlayers());
            for (com.hypixel.hytale.server.core.universe.PlayerRef pr : players) {
                if (pr.getUuid().equals(playerRef.getUuid())) {
                    com.hypixel.hytale.server.core.entity.entities.Player p =
                        pr.getReference().getStore().getComponent(
                            pr.getReference(), com.hypixel.hytale.server.core.entity.entities.Player.getComponentType());
                    return p != null && p.getGameMode() == GameMode.Creative;
                }
            }
        } catch (Exception ignored) {}
        return false;
    }

    private List<com.hypixel.hytale.server.core.universe.PlayerRef> getOnlinePlayers() {
        try {
            return new ArrayList<>(Universe.get().getPlayers());
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    private @Nullable com.hypixel.hytale.server.core.universe.PlayerRef adminTargetRef() {
        List<com.hypixel.hytale.server.core.universe.PlayerRef> players = getOnlinePlayers();
        if (players.isEmpty()) return null;
        adminPlayerIndex = Math.max(0, Math.min(adminPlayerIndex, players.size() - 1));
        return players.get(adminPlayerIndex);
    }

    private void populateAdmin(@Nonnull UICommandBuilder ui, @Nonnull UIEventBuilder ev) {
        List<com.hypixel.hytale.server.core.universe.PlayerRef> players = getOnlinePlayers();
        com.hypixel.hytale.server.core.universe.PlayerRef target = adminTargetRef();

        String targetName = target != null ? target.getUsername() : "—";
        ui.set("#AdminTargetName.TextSpans", Message.raw(targetName));
        ui.set("#AdminPlayerCount.TextSpans", Message.raw("(" + players.size() + " en ligne)"));

        ev.addEventBinding(CustomUIEventBindingType.Activating, "#AdminPlayerPrev",
            EventData.of("Action", "adminPlayerNav").append("Dir", "-1"), false);
        ev.addEventBinding(CustomUIEventBindingType.Activating, "#AdminPlayerNext",
            EventData.of("Action", "adminPlayerNav").append("Dir", "1"), false);
        ev.addEventBinding(CustomUIEventBindingType.Activating, "#AdminPlayerSelf",
            EventData.of("Action", "adminPlayerSelf"), false);

        Profession prof = CATALOG_ORDER[adminProfIndex];
        ui.set("#AdminProfName.TextSpans", Message.raw(prof.getDisplayName()));

        ProfessionManager mgr = VaryonRpgPlugin.getInstance().getProfessionManager();
        if (mgr != null && target != null) {
            mgr.ensureAccount(target.getUuid(), target.getUsername());
            PlayerAccount acc = mgr.getAccount(target.getUuid());
            if (acc != null) {
                ProfessionProgress prog = acc.getProgress(prof);
                ui.set("#AdminProfLevel.TextSpans", Message.raw(String.valueOf(prog.getLevel())));
                ui.set("#AdminProfXp.TextSpans", Message.raw(prog.getXpInLevel() + " / " + prog.getXpToNextLevel()));

                ui.set("#AdminStatsContainer.Visible", true);
                ui.clear("#AdminStatsContainer");
                for (int i = 0; i < CATALOG_ORDER.length; i++) {
                    Profession p = CATALOG_ORDER[i];
                    ProfessionProgress pp = acc.getProgress(p);
                    ui.append("#AdminStatsContainer", "CharacterTabAdminStatRow.ui");
                    ui.set("#AdminStatsContainer[" + i + "] #AdminStatRowName.TextSpans", Message.raw(p.getDisplayName()));
                    ui.set("#AdminStatsContainer[" + i + "] #AdminStatRowLevel.TextSpans",
                        Message.raw("Nv " + pp.getLevel()));
                }
            } else {
                ui.set("#AdminProfLevel.TextSpans", Message.raw("—"));
                ui.set("#AdminProfXp.TextSpans", Message.raw("—"));
            }
        } else {
            ui.set("#AdminProfLevel.TextSpans", Message.raw("—"));
            ui.set("#AdminProfXp.TextSpans", Message.raw("—"));
        }

        ev.addEventBinding(CustomUIEventBindingType.Activating, "#AdminProfPrev",
            EventData.of("Action", "adminProfNav").append("Dir", "-1"), false);
        ev.addEventBinding(CustomUIEventBindingType.Activating, "#AdminProfNext",
            EventData.of("Action", "adminProfNav").append("Dir", "1"), false);

        ev.addEventBinding(CustomUIEventBindingType.Activating, "#AdminXp100",
            EventData.of("Action", "adminXp").append("Amount", "100"), false);
        ev.addEventBinding(CustomUIEventBindingType.Activating, "#AdminXp1k",
            EventData.of("Action", "adminXp").append("Amount", "1000"), false);
        ev.addEventBinding(CustomUIEventBindingType.Activating, "#AdminXp5k",
            EventData.of("Action", "adminXp").append("Amount", "5000"), false);
        ev.addEventBinding(CustomUIEventBindingType.Activating, "#AdminXp10k",
            EventData.of("Action", "adminXp").append("Amount", "10000"), false);
        ev.addEventBinding(CustomUIEventBindingType.Activating, "#AdminXp50k",
            EventData.of("Action", "adminXp").append("Amount", "50000"), false);

        ev.addEventBinding(CustomUIEventBindingType.Activating, "#AdminLvlMinus5",
            EventData.of("Action", "adminLevel").append("Delta", "-5"), false);
        ev.addEventBinding(CustomUIEventBindingType.Activating, "#AdminLvlMinus1",
            EventData.of("Action", "adminLevel").append("Delta", "-1"), false);
        ev.addEventBinding(CustomUIEventBindingType.Activating, "#AdminLvlPlus1",
            EventData.of("Action", "adminLevel").append("Delta", "1"), false);
        ev.addEventBinding(CustomUIEventBindingType.Activating, "#AdminLvlPlus5",
            EventData.of("Action", "adminLevel").append("Delta", "5"), false);
        ev.addEventBinding(CustomUIEventBindingType.Activating, "#AdminLvlMax",
            EventData.of("Action", "adminLevel").append("Delta", "max"), false);

        ev.addEventBinding(CustomUIEventBindingType.Activating, "#AdminResetTalents",
            EventData.of("Action", "adminResetTalents"), false);
        ev.addEventBinding(CustomUIEventBindingType.Activating, "#AdminResetAll",
            EventData.of("Action", "adminResetAll"), false);

        ui.set("#AdminFeedback.Visible", false);
    }

    private void adminFeedback(@Nonnull UICommandBuilder ui, @Nonnull String msg) {
        ui.set("#AdminFeedback.TextSpans", Message.raw(msg));
        ui.set("#AdminFeedback.Visible", true);
    }

    private static void applyGaugeBar(@Nonnull UICommandBuilder ui,
                                      @Nonnull String fillId,
                                      long xpInLevel, long xpToNext) {
        double ratio = xpToNext > 0 ? Math.min(1.0, (double) xpInLevel / xpToNext) : 1.0;
        if (VrpgConfig.isDebugProfessions()) {
            LOG.info("[RPG-Gauge] " + fillId + " xpInLevel=" + xpInLevel
                + " xpToNext=" + xpToNext + " ratio=" + String.format("%.3f", ratio));
        }
        ui.set(fillId + ".Value", ratio);
    }

    private void populateCharacterProfessions(@Nonnull UICommandBuilder uiBuilder,
                                              @Nonnull UIEventBuilder eventBuilder) {
        PlayerAccount acc = currentAccount();
        Profession[] activeSlots = new Profession[2];
        if (acc != null) {
            activeSlots[0] = acc.getActiveSlot0();
            activeSlots[1] = acc.getActiveSlot1();
        }
        for (int i = 0; i < 2; i++) {
            String p = "#ProfessionActiveCard" + i;
            Profession active = activeSlots[i];
            if (active != null && acc != null) {
                ProfessionProgress prog = acc.getProgress(active);
                uiBuilder.set(p + ".Visible", true);
                uiBuilder.set(p + "Name.TextSpans", Message.raw(active.getDisplayName().toUpperCase(Locale.FRENCH)));
                uiBuilder.set(p + "Icon.ItemId", active.getIconItemId());
                String levelBadge = "Niveau " + prog.getLevel();
                if (acc.availableTalentPoints(active) > 0) levelBadge += " *";
                uiBuilder.set(p + "LevelBadge.TextSpans", Message.raw(levelBadge.toUpperCase(Locale.FRENCH)));
                if (prog.isMaxLevel()) {
                    uiBuilder.set(p + "LevelXp.TextSpans", Message.raw("MAX"));
                    uiBuilder.set(p + "ProgBarFill.Value", 1.0);
                } else {
                    uiBuilder.set(p + "LevelXp.TextSpans",
                        Message.raw(prog.getXpInLevel() + " / " + prog.getXpToNextLevel() + " XP"));
                    applyGaugeBar(uiBuilder, p + "ProgBarFill", prog.getXpInLevel(), prog.getXpToNextLevel());
                }
                uiBuilder.set(p + "Reconvert.Visible", true);
                uiBuilder.set(p + "ViewTalents.Visible", true);
                eventBuilder.addEventBinding(
                    CustomUIEventBindingType.Activating,
                    p + "Reconvert",
                    EventData.of("Action", "professionReconvert")
                        .append("ProfessionId", active.getId())
                        .append("Node", Integer.toString(i)),
                    false
                );
                eventBuilder.addEventBinding(
                    CustomUIEventBindingType.Activating,
                    p + "ViewTalents",
                    EventData.of("Action", "tab").append("Tab", "skills").append("TalentSlot", Integer.toString(i)),
                    false
                );
            } else {
                uiBuilder.set(p + ".Visible", false);
                uiBuilder.set(p + "Reconvert.Visible", false);
                uiBuilder.set(p + "ViewTalents.Visible", false);
            }
        }

        boolean selectMode = reconvertSourceId != null
            && Profession.fromId(reconvertSourceId) != null;
        if (!selectMode) reconvertSourceId = null;

        for (int i = 0; i < PROFESSION_CATALOG_SLOTS; i++) {
            String id = "#ProfessionCatalogCard" + i;
            Profession p = CATALOG_ORDER[i];
            uiBuilder.set(id + "Name.TextSpans", Message.raw(p.getDisplayName()));
            uiBuilder.set(id + "Icon.ItemId", p.getIconItemId());
            ProfessionProgress catProg = acc == null ? null : acc.getProgress(p);
            int level = catProg == null ? 1 : catProg.getLevel();
            boolean catMax = catProg != null && catProg.isMaxLevel();
            uiBuilder.set(id + "Level.TextSpans", Message.raw((catMax ? "MAX" : "Niveau " + level).toUpperCase(Locale.FRENCH)));
            if (catMax) {
                uiBuilder.set(id + "ProgBarFill.Value", 1.0);
            } else {
                long catXpInLevel = catProg == null ? 0L : catProg.getXpInLevel();
                long catXpToNext = catProg == null ? 0L : catProg.getXpToNextLevel();
                applyGaugeBar(uiBuilder, id + "ProgBarFill", catXpInLevel, catXpToNext);
            }

            boolean selectable = selectMode
                && (!p.isSpecialized() || (acc != null && acc.isUnlocked(p)))
                && p != activeSlots[0]
                && p != activeSlots[1];
            uiBuilder.set(id + "Select.Visible", selectable);
            if (selectable) {
                eventBuilder.addEventBinding(CustomUIEventBindingType.Activating,
                    id + "Select",
                    EventData.of("Action", "professionReconvertSelect")
                        .append("ProfessionId", p.getId()),
                    false);
            }

            boolean isActive = p == activeSlots[0] || p == activeSlots[1];
            uiBuilder.set(id + "Active.Visible", isActive);
            uiBuilder.set(id + "Inactive.Visible", !isActive);
            uiBuilder.setObject(id + ".Background", isActive ? CARD_BG_ACTIVE_STYLE : CARD_BG_INACTIVE_STYLE);

            if (p.isSpecialized()) {
                Profession parent = p.getPrereq();
                int need = p.getPrereqLevel();
                String parentName = parent == null ? "?" : parent.getDisplayName();
                boolean unlocked = acc != null && acc.isUnlocked(p);
                uiBuilder.set(id + "PrereqText.Visible", !unlocked);
                if (!unlocked) {
                    uiBuilder.set(id + "PrereqText.TextSpans",
                        Message.raw("Pr\u00e9requis : niveau " + need + " " + parentName));
                }
                uiBuilder.set(id + "Desc.Visible", unlocked);
                if (unlocked) {
                    uiBuilder.set(id + "Desc.TextSpans", Message.raw(p.getDescription()));
                }
            } else {
                uiBuilder.set(id + "PrereqText.Visible", false);
                uiBuilder.set(id + "Desc.Visible", true);
                uiBuilder.set(id + "Desc.TextSpans", Message.raw(p.getDescription()));
            }
        }

        for (int i = 0; i < BOOST_PROFESSION_ORDER.length; i++) {
            Profession bp = BOOST_PROFESSION_ORDER[i];
            String bid = "#BoostCard" + i;
            XpBoost boost = acc != null ? acc.getBoost(bp) : null;
            uiBuilder.set(bid + "Icon.ItemId", bp.getIconItemId());
            uiBuilder.set(bid + "Name.TextSpans", Message.raw(bp.getDisplayName()));
            uiBuilder.set(bid + "Info.Visible", boost != null);
            if (boost != null) {
                uiBuilder.set(bid + "Multiplier.TextSpans", Message.raw(formatBoostMultiplier(boost.getBonus())));
                uiBuilder.set(bid + "Timer.TextSpans", Message.raw(formatBoostTime(boost.getRemainingMs())));
            }
        }
    }

    private static final Profession[] BOOST_PROFESSION_ORDER = {
        Profession.MINEUR, Profession.FERMIER, Profession.FORESTIER, Profession.CHASSEUR
    };

    private static String formatBoostMultiplier(double bonus) {
        int pct = (int) Math.round(bonus * 100);
        return "+" + pct + "% XP";
    }

    private static String formatBoostTime(long ms) {
        long s = ms / 1000;
        long m = s / 60;
        long h = m / 60;
        if (h > 0) return h + "h " + (m % 60) + "min";
        if (m > 0) return m + "min " + (s % 60) + "s";
        return s + "s";
    }

    private void populateClassement(@Nonnull UICommandBuilder uiBuilder, @Nonnull UIEventBuilder eventBuilder) {
        for (Profession p : Profession.values()) {
            String capId = capitalize(p.getId());
            uiBuilder.set("#ClassementUnderline" + capId + ".Visible", p == classementFilter);
            eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#ClassementBtn" + capId,
                EventData.of("Action", "classementFilter").append("ProfessionId", p.getId()), false);
        }

        uiBuilder.set("#ClassementOrderLabel.Text", classementAsc ? "Ordre : ASC" : "Ordre : DESC");
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#ClassementOrderBtn",
            EventData.of("Action", "classementOrder"), false);

        ProfessionManager mgr = VaryonRpgPlugin.getInstance().getProfessionManager();
        List<LeaderboardEntry> entries = mgr != null ? mgr.getLeaderboard(classementFilter) : Collections.emptyList();
        if (classementAsc) {
            entries = new ArrayList<>(entries);
            Collections.reverse(entries);
        }

        uiBuilder.clear("#ClassementListContainer");
        for (int i = 0; i < entries.size(); i++) {
            LeaderboardEntry entry = entries.get(i);
            uiBuilder.append("#ClassementListContainer", "CharacterTabClassementEntry.ui");
            String eid = "#ClassementListContainer[" + i + "]";
            uiBuilder.set(eid + " #BgOdd.Visible", i % 2 == 1);
            if (i == 0) {
                uiBuilder.set(eid + " #Rank.Visible", false);
                uiBuilder.set(eid + " #RankGold.Text", "1");
                uiBuilder.set(eid + " #RankGold.Visible", true);
            } else if (i == 1) {
                uiBuilder.set(eid + " #Rank.Visible", false);
                uiBuilder.set(eid + " #RankSilver.Text", "2");
                uiBuilder.set(eid + " #RankSilver.Visible", true);
            } else if (i == 2) {
                uiBuilder.set(eid + " #Rank.Visible", false);
                uiBuilder.set(eid + " #RankBronze.Text", "3");
                uiBuilder.set(eid + " #RankBronze.Visible", true);
            } else {
                uiBuilder.set(eid + " #Rank.Text", String.valueOf(i + 1));
            }
            uiBuilder.set(eid + " #PlayerName.Text", entry.playerName());
            uiBuilder.set(eid + " #Level.Text", "Niv. " + entry.level());
            uiBuilder.set(eid + " #XpTotal.Text", formatXp(XpCurve.cumulativeXp(entry.level(), entry.xpInLevel())));
        }
    }

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private static String formatXp(long xp) {
        if (xp >= 1_000_000) return String.format("%.1fM", xp / 1_000_000.0);
        if (xp >= 1_000) return String.format("%.1fK", xp / 1_000.0);
        return String.valueOf(xp);
    }

    private void syncTalentTreeSlotIndex(@Nullable PlayerAccount acc) {
        if (acc == null) {
            talentTreeSlotIndex = 0;
            return;
        }
        Profession p0 = acc.getActiveSlot0();
        Profession p1 = acc.getActiveSlot1();
        if (talentTreeSlotIndex != 0 && talentTreeSlotIndex != 1) {
            talentTreeSlotIndex = 0;
        }
        if (talentTreeSlotIndex == 0 && p0 == null && p1 != null) {
            talentTreeSlotIndex = 1;
        } else if (talentTreeSlotIndex == 1 && p1 == null && p0 != null) {
            talentTreeSlotIndex = 0;
        }
    }

    private Profession currentTalentProfession() {
        PlayerAccount acc = currentAccount();
        syncTalentTreeSlotIndex(acc);
        if (acc == null) {
            return Profession.MINEUR;
        }
        Profession p = talentTreeSlotIndex == 0 ? acc.getActiveSlot0() : acc.getActiveSlot1();
        if (p != null) {
            return p;
        }
        Profession fallback = acc.getActiveSlot0() != null
            ? acc.getActiveSlot0()
            : acc.getActiveSlot1();
        return fallback != null ? fallback : Profession.MINEUR;
    }

    private SkillTreeDef currentSkillTree() {
        Profession p = currentTalentProfession();
        if (p == Profession.MINEUR)    return MINEUR_TREE;
        if (p == Profession.FERMIER)   return FERMIER_TREE;
        if (p == Profession.FORESTIER) return FORESTIER_TREE;
        if (p == Profession.CHASSEUR)  return CHASSEUR_TREE;
        return BASE_TREE;
    }

    private void loadSkillRanksFromAccount() {
        Arrays.fill(skillRanks, 0);
        PlayerAccount acc = currentAccount();
        if (acc == null) return;
        Profession prof = currentTalentProfession();
        SkillTreeDef tree = currentSkillTree();
        for (int i = 0; i < tree.nodes.length; i++) {
            skillRanks[i] = acc.getTalentRank(prof, tree.nodes[i][0]);
        }
    }

    private void populateSketchSkillTree(@Nonnull UICommandBuilder uiBuilder,
                                         @Nonnull UIEventBuilder eventBuilder) {
        PlayerAccount acc = currentAccount();
        syncTalentTreeSlotIndex(acc);
        SkillTreeDef tree = currentSkillTree();
        if (selectedNode >= tree.nodes.length) selectedNode = 0;
        loadSkillRanksFromAccount();
        Profession prof = currentTalentProfession();
        int remainingPoints = pendingRemainingPoints(acc, prof);
        uiBuilder.set("#SkillTreePointsValue.TextSpans",
            Message.raw("Points restants : " + remainingPoints + " (" + prof.getDisplayName() + ")"));

        eventBuilder.addEventBinding(
            CustomUIEventBindingType.Activating,
            "#SkillTreeBackButton",
            EventData.of("Action", "tab").append("Tab", "character"),
            false
        );

        int minX = Integer.MAX_VALUE;
        for (int[] lt : tree.slotLt) if (lt[0] < minX) minX = lt[0];
        int xOffset = 58 - minX;
        int[][] shiftedLt = new int[tree.slotLt.length][2];
        for (int i = 0; i < tree.slotLt.length; i++) {
            shiftedLt[i][0] = tree.slotLt[i][0] + xOffset;
            shiftedLt[i][1] = tree.slotLt[i][1];
        }

        for (String legacyId : LEGACY_STATIC_EDGE_IDS) {
            uiBuilder.set(legacyId + ".Visible", false);
        }
        hideEdgeSegmentRange(uiBuilder, 0, SKILL_TREE_EDGE_SEGMENTS);

        int seg = tree.edgeBuilder.build(uiBuilder, 0, shiftedLt);
        hideEdgeSegmentRange(uiBuilder, seg, SKILL_TREE_EDGE_SEGMENTS);

        for (int i = 0; i < tree.nodes.length; i++) {
            String[] node = tree.nodes[i];
            String id = node[0];
            int sl = shiftedLt[i][0];
            int st = shiftedLt[i][1];

            positionSkillSlot(uiBuilder, id, sl, st);
            positionSkillRank(uiBuilder, id, sl, st);

            String iconPath = node[5].contains("/") ? node[5] : ICON_BASE + node[5];
            PatchStyle iconStyle = new PatchStyle().setTexturePath(Value.of(iconPath));
            int allocated = currentRanks()[i];

            uiBuilder.set("#SkillTreeNode" + id + "Slot.Visible", true);

            uiBuilder.set("#SkillTreeNode" + id + "Unlocked.Visible", true);
            uiBuilder.setObject("#SkillTreeNode" + id + "Unlocked.Background", NODE_FILL_STYLE);

            uiBuilder.set("#SkillTreeNode" + id + "Icon.Visible", true);
            uiBuilder.setObject("#SkillTreeNode" + id + "Icon.Background", iconStyle);

            uiBuilder.set("#SkillTreeNode" + id + "RankText.Visible", true);
            uiBuilder.set("#SkillTreeNode" + id + "RankText.TextSpans",
                Message.raw(allocated + "/" + tree.maxRanks[i]));

            uiBuilder.set("#SkillTreeNode" + id + ".Visible", true);
            eventBuilder.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#SkillTreeNode" + id,
                EventData.of("Action", "skill").append("Node", id),
                false
            );
            eventBuilder.addEventBinding(
                CustomUIEventBindingType.RightClicking,
                "#SkillTreeNode" + id,
                EventData.of("Action", "skillRight").append("Node", id),
                false
            );
            eventBuilder.addEventBinding(
                CustomUIEventBindingType.MouseEntered,
                "#SkillTreeNode" + id,
                EventData.of("Action", "skillHover").append("Node", id),
                false
            );
        }

        for (String xId : new String[]{"12", "13", "14", "15", "16"}) {
            boolean active = false;
            for (String[] n : tree.nodes) { if (n[0].equals(xId)) { active = true; break; } }
            if (!active) {
            uiBuilder.set("#SkillTreeNode" + xId + "Slot.Visible", false);
            uiBuilder.set("#SkillTreeNode" + xId + "RankText.Visible", false);
            uiBuilder.set("#SkillTreeNode" + xId + ".Visible", false);
            }
        }

        populateBonusTree(uiBuilder, eventBuilder, acc, prof);

        applySkillTreeSelectionAndHoverChrome(uiBuilder);
        uiBuilder.set("#SkillTreeAttribuerButton.Visible", isEditMode);
        uiBuilder.set("#SkillTreeAttribuerButton.Disabled", false);
        uiBuilder.set("#SkillTreeResetButton.Visible", true);
        if (isEditMode) {
            eventBuilder.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#SkillTreeAttribuerButton",
                EventData.of("Action", "saveSkills"),
                false
            );
        }
        eventBuilder.addEventBinding(
            CustomUIEventBindingType.Activating,
            "#SkillTreeResetButton",
            EventData.of("Action", "resetSkills"),
            false
        );
        eventBuilder.addEventBinding(
            CustomUIEventBindingType.Activating,
            "#SkillTreeSoundToggle",
            EventData.of("Action", "toggleTalentSound"),
            false
        );
    }

    private static final String[] BONUS_NODE_IDS = {"bonus_0", "bonus_1", "bonus_2"};
    private static final String[] BONUS_NODE_LABELS = {"I", "II", "III"};
    private static final String[] BONUS_NODE_NAMES = {"Maîtrise I", "Maîtrise II", "Maîtrise III"};
    private static final String[] BONUS_NODE_DESCS = {
        "Première maîtrise du métier.",
        "Maîtrise approfondie du métier.",
        "Maîtrise ultime du métier."
    };

    private void populateBonusTree(@Nonnull UICommandBuilder ui,
                                   @Nonnull UIEventBuilder ev,
                                   @Nullable PlayerAccount acc,
                                   @Nonnull Profession prof) {
        int panelLeft = 550;
        Anchor bonusAnchor = new Anchor();
        bonusAnchor.setLeft(Value.of(panelLeft));
        bonusAnchor.setTop(Value.of(0));
        bonusAnchor.setBottom(Value.of(0));
        bonusAnchor.setWidth(Value.of(90));
        ui.setObject("#BonusTreePanel.Anchor", bonusAnchor);
        ui.set("#BonusTreePanel.Visible", true);
        boolean hasPoints = acc != null && acc.availableTalentPoints(prof) > 0;
        for (int i = 0; i < 3; i++) {
            int real = acc != null ? acc.getTalentRank(prof, BONUS_NODE_IDS[i]) : 0;
            int rank = (pendingBonusRanks != null) ? pendingBonusRanks[i] : real;
            boolean allocated = rank > 0;
            boolean prevAllocated = i == 0 || (acc != null && acc.getTalentRank(prof, BONUS_NODE_IDS[i - 1]) > 0);
            boolean canAllocate = !allocated && prevAllocated && hasPoints;

            ui.set("#BonusTreeNode" + i + "Container.Visible", true);
            ui.set("#BonusTreeNode" + i + "BorderAllocated.Visible", allocated);
            ui.set("#BonusTreeNode" + i + "RankText.Visible", true);
            ui.set("#BonusTreeNode" + i + "RankText.TextSpans", Message.raw(rank + "/1"));

            if (i < 2) {
                ui.set("#BonusTreeNode" + i + "Connector.Visible", true);
            }

            ev.addEventBinding(CustomUIEventBindingType.Activating,
                "#BonusTreeNode" + i,
                EventData.of("Action", "bonusClick").append("Index", String.valueOf(i)),
                false);
            ev.addEventBinding(CustomUIEventBindingType.MouseEntered,
                "#BonusTreeNode" + i,
                EventData.of("Action", "bonusHover").append("Index", String.valueOf(i)),
                false);
        }
    }

    private void applySkillTreeSelectionAndHoverChrome(@Nonnull UICommandBuilder uiBuilder) {
        SkillTreeDef tree = currentSkillTree();
        int effectiveSelected = (selectedNode >= 0 && selectedNode < tree.nodes.length) ? selectedNode : 0;
        int bonusPanel = hoveredBonusNode >= 0 ? hoveredBonusNode : selectedBonusNode;
        if (bonusPanel >= 0 && bonusPanel < BONUS_NODE_IDS.length) {
            PlayerAccount acc = currentAccount();
            Profession prof = currentTalentProfession();
            int realRank = acc != null ? acc.getTalentRank(prof, BONUS_NODE_IDS[bonusPanel]) : 0;
            int rank = (pendingBonusRanks != null) ? pendingBonusRanks[bonusPanel] : realRank;
            uiBuilder.set("#SkillTreeSelectedTitle.TextSpans", Message.raw(BONUS_NODE_NAMES[bonusPanel]));
            uiBuilder.set("#SkillTreeSelectedFlavor.TextSpans", Message.raw("« " + BONUS_NODE_DESCS[bonusPanel] + " »"));
            uiBuilder.set("#SkillTreeSelectedEffect.TextSpans", Message.raw(""));
            uiBuilder.setObject("#SkillSidebarIconImage.Background", new PatchStyle());
            uiBuilder.set("#SkillTreeCurrentRankValue.TextSpans", Message.raw(rank + "/1"));
            uiBuilder.set("#SkillTreeCurrentBonusRow.Visible", false);
            uiBuilder.set("#SkillTreeNextRankRow.Visible", false);
            uiBuilder.set("#SkillTreeTypePassif.Visible", true);
            uiBuilder.set("#SkillTreeTypeActif.Visible", false);
            uiBuilder.set("#SkillTreeTypeObjet.Visible", false);
            uiBuilder.set("#SkillTreeSoundToggle.Visible", false);
            String selectedId = tree.nodes[effectiveSelected][0];
            boolean hoverValid = hoveredNode >= 0 && hoveredNode < tree.nodes.length;
            String hoverId = hoverValid ? tree.nodes[hoveredNode][0] : null;
            int[] display = currentRanks();
            for (int i = 0; i < tree.nodes.length; i++) {
                String id = tree.nodes[i][0];
                int allocated = display[i];
                boolean nodeSelected = id.equals(selectedId);
                boolean nodeHovered = hoverId != null && id.equals(hoverId);
                String borderRgb = allocated >= 1 ? NODE_BORDER_ALLOCATED : NODE_BORDER;
                uiBuilder.setObject("#SkillTreeNode" + id + "Slot.Background", new PatchStyle().setColor(Value.of(borderRgb)));
                uiBuilder.set("#SkillTreeNode" + id + "Veil.Visible", !nodeSelected && !nodeHovered && allocated == 0);
                uiBuilder.setObject("#SkillTreeNode" + id + "Veil.Background", NODE_VEIL_STYLE);
            }
            return;
        }
        String selectedId = tree.nodes[effectiveSelected][0];
        boolean hoverValid = hoveredNode >= 0 && hoveredNode < tree.nodes.length;
        String hoverId = hoverValid ? tree.nodes[hoveredNode][0] : null;

        int[] display = currentRanks();
        for (int i = 0; i < tree.nodes.length; i++) {
            String id = tree.nodes[i][0];
            int allocated = display[i];
            boolean nodeSelected = id.equals(selectedId);
            boolean nodeHovered = hoverId != null && id.equals(hoverId);
            String borderRgb;
            if (nodeHovered) {
                borderRgb = NODE_BORDER_SELECTION;
            } else if (allocated >= 1) {
                borderRgb = NODE_BORDER_ALLOCATED;
            } else {
                borderRgb = NODE_BORDER;
            }
            PatchStyle borderStyle = new PatchStyle().setColor(Value.of(borderRgb));
            uiBuilder.setObject("#SkillTreeNode" + id + "Slot.Background", borderStyle);
            uiBuilder.set("#SkillTreeNode" + id + "Veil.Visible",
                !nodeSelected && !nodeHovered && allocated == 0);
            uiBuilder.setObject("#SkillTreeNode" + id + "Veil.Background", NODE_VEIL_STYLE);
        }

        int panelNode = hoverValid ? hoveredNode : effectiveSelected;
        String[] sel = tree.nodes[panelNode];
        int rank = display[panelNode];
        int maxRank = tree.maxRanks[panelNode];
        String[] stats = (tree.nodeStatValues != null && panelNode < tree.nodeStatValues.length)
            ? tree.nodeStatValues[panelNode] : null;

        uiBuilder.set("#SkillTreeSelectedTitle.TextSpans", Message.raw(sel[1]));
        uiBuilder.set("#SkillTreeSelectedFlavor.TextSpans", Message.raw("« " + sel[3] + " »"));
        uiBuilder.set("#SkillTreeSelectedEffect.TextSpans", Message.raw(sel[4]));

        String sidebarIconPath = sel[5].contains("/") ? sel[5] : ICON_BASE + sel[5];
        uiBuilder.setObject("#SkillSidebarIconImage.Background",
            new PatchStyle().setTexturePath(Value.of(sidebarIconPath)));

        uiBuilder.set("#SkillTreeCurrentRankValue.TextSpans",
            Message.raw(rank + "/" + maxRank));

        boolean hasCurrent = rank > 0 && stats != null;
        uiBuilder.set("#SkillTreeCurrentBonusRow.Visible", hasCurrent);
        if (hasCurrent) {
            uiBuilder.set("#SkillTreeCurrentBonusValue.TextSpans", Message.raw(stats[rank - 1]));
        }

        boolean hasNext = rank < maxRank && stats != null;
        uiBuilder.set("#SkillTreeNextRankRow.Visible", hasNext);
        if (hasNext) {
            uiBuilder.set("#SkillTreeNextRankValue.TextSpans", Message.raw(stats[rank]));
        }

        String type = sel[2];
        uiBuilder.set("#SkillTreeTypePassif.Visible", "Passif".equals(type));
        uiBuilder.set("#SkillTreeTypeActif.Visible",  "Actif".equals(type));
        uiBuilder.set("#SkillTreeTypeObjet.Visible",  "Objet".equals(type));

        applyTalentSoundToggle(uiBuilder, sel[0]);
    }

    private void applyTalentSoundToggle(@Nonnull UICommandBuilder uiBuilder,
                                        @Nonnull String panelNodeId) {
        Profession profession = currentTalentProfession();
        boolean show = TalentSoundNodes.hasSoundToggle(profession, panelNodeId);
        uiBuilder.set("#SkillTreeSoundToggle.Visible", show);
        if (!show) return;
        PlayerAccount acc = currentAccount();
        boolean soundOn = acc == null || acc.isTalentSoundEnabled(profession, panelNodeId);
        String icon = soundOn ? TALENT_SOUND_ICON_ON : TALENT_SOUND_ICON_OFF;
        uiBuilder.setObject("#SkillTreeSoundToggleIcon.Background",
            new PatchStyle().setTexturePath(Value.of(icon)));
    }

    private void sendSkillTreeHoverChromeUpdate() {
        UICommandBuilder cmd = new UICommandBuilder();
        applySkillTreeSelectionAndHoverChrome(cmd);
        this.sendUpdate(cmd, null, false);
    }

    private static int cx(int[] lt) {
        return lt[0] + SLOT / 2;
    }

    private static int top(int[] lt) {
        return lt[1];
    }

    private static int bot(int[] lt) {
        return lt[1] + SLOT;
    }

    private static void hideEdgeSegmentRange(@Nonnull UICommandBuilder ui,
                                            int fromInclusive,
                                            int toExclusive) {
        for (int i = fromInclusive; i < toExclusive; i++) {
            ui.set("#SkillTreeEdgeSeg" + i + ".Visible", false);
        }
    }

    private static void positionSkillSlot(@Nonnull UICommandBuilder ui,
                                          @Nonnull String id,
                                          int slotLeft,
                                          int slotTop) {
        setAnchor(ui, "#SkillTreeNode" + id + "Slot", slotLeft, slotTop, SLOT, SLOT);
        setAnchor(ui, "#SkillTreeNode" + id + "Unlocked",
            slotLeft + FILL_INSET, slotTop + FILL_INSET, FILL_SIZE, FILL_SIZE);
        setAnchor(ui, "#SkillTreeNode" + id + "Icon",
            slotLeft + 8, slotTop + 8, SLOT - 16, SLOT - 16);
        setAnchor(ui, "#SkillTreeNode" + id + "Veil",
            slotLeft + FILL_INSET, slotTop + FILL_INSET, FILL_SIZE, FILL_SIZE);
        setAnchor(ui, "#SkillTreeNode" + id,
            slotLeft, slotTop, SLOT, SLOT);
    }

    private static void positionSkillRank(@Nonnull UICommandBuilder ui,
                                          @Nonnull String id,
                                          int slotLeft,
                                          int slotTop) {
        setAnchor(ui, "#SkillTreeNode" + id + "RankText",
            slotLeft + RANK_LABEL_SHIFT_RIGHT,
            slotTop + SLOT + RANK_LABEL_GAP_TOP,
            RANK_LABEL_W, RANK_LABEL_H);
    }

    private static void setAnchor(@Nonnull UICommandBuilder ui,
                                  @Nonnull String elementId,
                                  int left,
                                  int top,
                                  int width,
                                  int height) {
        Anchor a = new Anchor();
        a.setLeft(Value.of(left));
        a.setTop(Value.of(top));
        a.setWidth(Value.of(width));
        a.setHeight(Value.of(height));
        ui.setObject(elementId + ".Anchor", a);
    }

    private static int layoutMergeTwoToOne(@Nonnull UICommandBuilder ui,
                                          int seg,
                                          int cxA,
                                          int yBotA,
                                          int cxB,
                                          int yBotB,
                                          int cxMid,
                                          int yTopMid) {
        int yBotMin = Math.min(yBotA, yBotB);
        int barTop = yBotMin + STEM_DOWN_FROM_PARENT;
        int barBot = barTop + RAIL;

        showEdge(ui, seg++, vx(cxA), yBotA, RAIL, STEM_DOWN_FROM_PARENT);
        showEdge(ui, seg++, vx(cxB), yBotB, RAIL, STEM_DOWN_FROM_PARENT);
        int barL = vx(Math.min(cxA, cxB));
        showEdge(ui, seg++, barL, barTop, vx(Math.max(cxA, cxB)) - barL + RAIL, RAIL);
        int stemToChild = yTopMid - barBot;
        if (stemToChild > 0) {
            showEdge(ui, seg++, vx(cxMid), barBot, RAIL, stemToChild);
        }
        return seg;
    }

    private static int layoutSplitOneToThree(@Nonnull UICommandBuilder ui,
                                              int seg,
                                              int cxP,
                                              int yBotP,
                                              int cxL,
                                              int cxM,
                                              int cxR,
                                              int yTopChildRow) {
        int barTop = yBotP + STEM_DOWN_FROM_PARENT;
        int barBot = barTop + RAIL;

        showEdge(ui, seg++, vx(cxP), yBotP, RAIL, STEM_DOWN_FROM_PARENT);
        int barL = vx(Math.min(cxL, Math.min(cxM, cxR)));
        showEdge(ui, seg++, barL, barTop,
            vx(Math.max(cxL, Math.max(cxM, cxR))) - barL + RAIL, RAIL);
        int stemToChild = yTopChildRow - barBot;
        if (stemToChild > 0) {
            showEdge(ui, seg++, vx(cxL), barBot, RAIL, stemToChild);
            showEdge(ui, seg++, vx(cxM), barBot, RAIL, stemToChild);
            showEdge(ui, seg++, vx(cxR), barBot, RAIL, stemToChild);
        }
        return seg;
    }

    private static int layoutVerticalConnector(@Nonnull UICommandBuilder ui,
                                               int seg,
                                               int cx,
                                               int yFrom,
                                               int yTo) {
        int h = yTo - yFrom;
        if (h > 0) {
            showEdge(ui, seg++, vx(cx), yFrom, RAIL, h);
        }
        return seg;
    }

    private static int layoutMergeThreeToOne(@Nonnull UICommandBuilder ui,
                                            int seg,
                                            int cxA,
                                            int yBotA,
                                            int cxB,
                                            int yBotB,
                                            int cxC,
                                            int yBotC,
                                            int cxMid,
                                            int yTopMid) {
        int yBotMin = Math.min(yBotA, Math.min(yBotB, yBotC));
        int barTop = yBotMin + STEM_DOWN_FROM_PARENT;
        int barBot = barTop + RAIL;

        showEdge(ui, seg++, vx(cxA), yBotA, RAIL, STEM_DOWN_FROM_PARENT);
        showEdge(ui, seg++, vx(cxB), yBotB, RAIL, STEM_DOWN_FROM_PARENT);
        showEdge(ui, seg++, vx(cxC), yBotC, RAIL, STEM_DOWN_FROM_PARENT);
        int barL = vx(Math.min(cxA, Math.min(cxB, cxC)));
        showEdge(ui, seg++, barL, barTop,
            vx(Math.max(cxA, Math.max(cxB, cxC))) - barL + RAIL, RAIL);
        int stemToChild = yTopMid - barBot;
        if (stemToChild > 0) {
            showEdge(ui, seg++, vx(cxMid), barBot, RAIL, stemToChild);
        }
        return seg;
    }

    private static int layoutHorizontalSiblings(@Nonnull UICommandBuilder ui,
                                               int seg,
                                               int cxCenter, int cyMid,
                                               int cxLeft, int cxRight) {
        int slotHalfL = SLOT / 2;
        int slotHalfR = SLOT - slotHalfL;
        int barY = cyMid - RAIL / 2;
        int leftBarX  = cxLeft  + slotHalfR;
        int leftBarW  = (cxCenter - slotHalfL) - leftBarX;
        int rightBarX = cxCenter + slotHalfR;
        int rightBarW = (cxRight  - slotHalfL) - rightBarX;
        showEdge(ui, seg++, leftBarX,  barY, leftBarW,  RAIL);
        showEdge(ui, seg++, rightBarX, barY, rightBarW, RAIL);
        return seg;
    }

    private static int layoutFanTwoToThree(@Nonnull UICommandBuilder ui, int seg,
                                           int cxA, int yBotA, int cxB, int yBotB,
                                           int cxL, int cxM, int cxR, int yTopChildRow) {
        int barTop = Math.min(yBotA, yBotB) + STEM_DOWN_FROM_PARENT;
        int barBot = barTop + RAIL;
        showEdge(ui, seg++, vx(cxA), yBotA, RAIL, STEM_DOWN_FROM_PARENT);
        showEdge(ui, seg++, vx(cxB), yBotB, RAIL, STEM_DOWN_FROM_PARENT);
        int barL = vx(cxL);
        showEdge(ui, seg++, barL, barTop, vx(cxR) - barL + RAIL, RAIL);
        int stemH = yTopChildRow - barBot;
        if (stemH > 0) {
            showEdge(ui, seg++, vx(cxL), barBot, RAIL, stemH);
            showEdge(ui, seg++, vx(cxM), barBot, RAIL, stemH);
            showEdge(ui, seg++, vx(cxR), barBot, RAIL, stemH);
        }
        return seg;
    }

    private static int layoutFanThreeToTwo(@Nonnull UICommandBuilder ui, int seg,
                                           int cxA, int yBotA, int cxB, int yBotB, int cxC, int yBotC,
                                           int cxL, int cxR, int yTopChildRow) {
        int barTop = Math.min(yBotA, Math.min(yBotB, yBotC)) + STEM_DOWN_FROM_PARENT;
        int barBot = barTop + RAIL;
        showEdge(ui, seg++, vx(cxA), yBotA, RAIL, STEM_DOWN_FROM_PARENT);
        showEdge(ui, seg++, vx(cxB), yBotB, RAIL, STEM_DOWN_FROM_PARENT);
        showEdge(ui, seg++, vx(cxC), yBotC, RAIL, STEM_DOWN_FROM_PARENT);
        int barL = vx(cxA);
        showEdge(ui, seg++, barL, barTop, vx(cxC) - barL + RAIL, RAIL);
        int stemH = yTopChildRow - barBot;
        if (stemH > 0) {
            showEdge(ui, seg++, vx(cxL), barBot, RAIL, stemH);
            showEdge(ui, seg++, vx(cxR), barBot, RAIL, stemH);
        }
        return seg;
    }

    private static int layoutSplitOneToTwo(@Nonnull UICommandBuilder ui,
                                          int seg,
                                          int cxP,
                                          int yBotP,
                                          int cxL,
                                          int cxR,
                                          int yTopChildRow) {
        int barTop = yBotP + STEM_DOWN_FROM_PARENT;
        int barBot = barTop + RAIL;

        showEdge(ui, seg++, vx(cxP), yBotP, RAIL, STEM_DOWN_FROM_PARENT);
        int barL = vx(Math.min(cxL, cxR));
        showEdge(ui, seg++, barL, barTop, vx(Math.max(cxL, cxR)) - barL + RAIL, RAIL);
        int stemToChild = yTopChildRow - barBot;
        if (stemToChild > 0) {
            showEdge(ui, seg++, vx(cxL), barBot, RAIL, stemToChild);
            showEdge(ui, seg++, vx(cxR), barBot, RAIL, stemToChild);
        }
        return seg;
    }

    private static int vx(int cxCenter) {
        return cxCenter - RAIL / 2;
    }

    private static void showEdge(@Nonnull UICommandBuilder ui,
                                int index,
                                int left,
                                int top,
                                int width,
                                int height) {
        if (width <= 0 || height <= 0) return;
        setAnchor(ui, "#SkillTreeEdgeSeg" + index, left, top, width, height);
        ui.set("#SkillTreeEdgeSeg" + index + ".Visible", true);
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref,
                                @Nonnull Store<EntityStore> store,
                                @Nonnull Data data) {
        super.handleDataEvent(ref, store, data);
        if (data.action == null) return;

        if ("classementFilter".equals(data.action) && data.professionId != null) {
            Profession p = Profession.fromId(data.professionId);
            if (p != null) {
                classementFilter = p;
                rebuild();
            }
            return;
        } else if ("classementOrder".equals(data.action)) {
            classementAsc = !classementAsc;
            rebuild();
            return;
        }

        if ("tab".equals(data.action) && data.tab != null) {
            activeTab = data.tab;
            if ("skills".equals(data.tab) && data.talentSlot != null) {
                talentTreeSlotIndex = "1".equals(data.talentSlot) ? 1 : 0;
            }
            hoveredNode = -1;
            selectedNode = 0;
            exitEditMode();
            rebuild();
        } else if ("talentSlotPick".equals(data.action) && data.talentSlot != null) {
            talentTreeSlotIndex = "1".equals(data.talentSlot) ? 1 : 0;
            selectedNode = 0;
            hoveredNode = -1;
            exitEditMode();
            rebuild();
        } else if ("skill".equals(data.action) && data.node != null) {
            SkillTreeDef tree = currentSkillTree();
            for (int i = 0; i < tree.nodes.length; i++) {
                if (tree.nodes[i][0].equals(data.node)) {
                    selectedNode = i;
                    hoveredNode = -1;
                    selectedBonusNode = -1;
                    hoveredBonusNode = -1;
                    enterEditMode();
                    tryPendingAdd(i, tree);
                    break;
                }
            }
            rebuild();
        } else if ("skillRight".equals(data.action) && data.node != null) {
            SkillTreeDef tree = currentSkillTree();
            for (int i = 0; i < tree.nodes.length; i++) {
                if (tree.nodes[i][0].equals(data.node)) {
                    selectedNode = i;
                    if (!isEditMode) {
                        PlayerAccount acc = currentAccount();
                        if (acc != null && acc.availableTalentPoints(currentTalentProfession()) > 0) {
                            enterEditMode();
                        }
                    } else {
                        tryPendingRemove(i);
                    }
                    break;
                }
            }
            rebuild();
        } else if ("skillHover".equals(data.action) && data.node != null) {
            SkillTreeDef tree = currentSkillTree();
            for (int i = 0; i < tree.nodes.length; i++) {
                if (tree.nodes[i][0].equals(data.node)) {
                    hoveredNode = i;
                    hoveredBonusNode = -1;
                    break;
                }
            }
            sendSkillTreeHoverChromeUpdate();
        } else if ("bonusClick".equals(data.action) && data.index != null) {
            int idx;
            try { idx = Integer.parseInt(data.index); } catch (NumberFormatException e) { return; }
            if (idx < 0 || idx >= BONUS_NODE_IDS.length) return;
            selectedBonusNode = idx;
            hoveredBonusNode = -1;
            selectedNode = -1;
            hoveredNode = -1;
            PlayerAccount acc = currentAccount();
            if (acc == null) { rebuild(); return; }
            Profession prof = currentTalentProfession();
            enterEditMode();
            if (pendingBonusRanks == null) { rebuild(); return; }
            if (idx > 0 && pendingBonusRanks[idx - 1] == 0) { rebuild(); return; }
            if (pendingBonusRanks[idx] >= 1) { rebuild(); return; }
            int pendingSpent = 0;
            if (pendingRanks != null) {
                for (int i = 0; i < pendingRanks.length; i++) pendingSpent += Math.max(0, pendingRanks[i] - skillRanks[i]);
            }
            for (int i = 0; i < BONUS_NODE_IDS.length; i++) {
                int real = acc.getTalentRank(prof, BONUS_NODE_IDS[i]);
                pendingSpent += Math.max(0, pendingBonusRanks[i] - real);
            }
            if (acc.availableTalentPoints(prof) - pendingSpent <= 0) { rebuild(); return; }
            pendingBonusRanks[idx] = 1;
            rebuild();
        } else if ("bonusHover".equals(data.action) && data.index != null) {
            try { hoveredBonusNode = Integer.parseInt(data.index); } catch (NumberFormatException e) { return; }
            sendSkillTreeHoverChromeUpdate();
        } else if ("toggleTalentSound".equals(data.action)) {
            Profession profession = currentTalentProfession();
            SkillTreeDef tree = currentSkillTree();
            int panelNode = hoveredNode >= 0 && hoveredNode < tree.nodes.length ? hoveredNode : selectedNode;
            String nodeId = tree.nodes[panelNode][0];
            if (TalentSoundNodes.hasSoundToggle(profession, nodeId)) {
                ProfessionManager mgr = VaryonRpgPlugin.getInstance().getProfessionManager();
                if (mgr != null) {
                    mgr.toggleTalentSound(playerRef.getUuid(), profession, nodeId);
                }
            }
            sendSkillTreeHoverChromeUpdate();
        } else if ("saveSkills".equals(data.action)) {
            if (isEditMode) {
                ProfessionManager mgr = VaryonRpgPlugin.getInstance().getProfessionManager();
                if (mgr != null) {
                    SkillTreeDef tree = currentSkillTree();
                    Profession prof = currentTalentProfession();
                    if (pendingRanks != null) {
                        for (int i = 0; i < tree.nodes.length; i++) {
                            int delta = pendingRanks[i] - skillRanks[i];
                            if (delta > 0) {
                                String nodeId = tree.nodes[i][0];
                                for (int d = 0; d < delta; d++) {
                                    mgr.allocateTalent(playerRef.getUuid(), prof, nodeId, tree.maxRanks[i]);
                                }
                            }
                        }
                    }
                    if (pendingBonusRanks != null) {
                        PlayerAccount acc = currentAccount();
                        for (int i = 0; i < BONUS_NODE_IDS.length; i++) {
                            int real = acc != null ? acc.getTalentRank(prof, BONUS_NODE_IDS[i]) : 0;
                            if (pendingBonusRanks[i] > real) {
                                mgr.allocateTalent(playerRef.getUuid(), prof, BONUS_NODE_IDS[i], 1);
                            }
                        }
                    }
                }
            }
            exitEditMode();
            rebuild();
        } else if ("resetSkills".equals(data.action)) {
            exitEditMode();
            ProfessionManager mgr = VaryonRpgPlugin.getInstance().getProfessionManager();
            if (mgr != null) {
                mgr.resetTalents(playerRef.getUuid(), currentTalentProfession());
            }
            Arrays.fill(skillRanks, 0);
            rebuild();
        } else if ("professionReconvert".equals(data.action) && data.professionId != null) {
            reconvertSourceId = data.professionId.equals(reconvertSourceId)
                ? null : data.professionId;
            rebuild();
        } else if ("professionReconvertSelect".equals(data.action) && data.professionId != null
                   && reconvertSourceId != null) {
            ProfessionManager mgr = VaryonRpgPlugin.getInstance().getProfessionManager();
            if (mgr != null) {
                Profession source = Profession.fromId(reconvertSourceId);
                Profession target = Profession.fromId(data.professionId);
                LOG.info("[RPG-Reconvert] select source=" + source + " target=" + target);
                if (source != null && target != null) {
                    PlayerAccount acc = mgr.getAccount(playerRef.getUuid());
                    if (acc != null) {
                        int slot = source == acc.getActiveSlot1() ? 1 : 0;
                        ProfessionManager.ReconvertResult result =
                            mgr.setActiveSlot(playerRef.getUuid(), slot, target);
                        LOG.info("[RPG-Reconvert] setActiveSlot slot=" + slot + " result=" + result
                            + " lastReconvertAt=" + acc.getLastReconvertAt());
                    }
                }
            }
            reconvertSourceId = null;
            rebuild();
        } else if ("adminPlayerNav".equals(data.action) && data.dir != null) {
            if (!isAdmin()) return;
            List<com.hypixel.hytale.server.core.universe.PlayerRef> players = getOnlinePlayers();
            if (!players.isEmpty()) {
                int dir = "1".equals(data.dir) ? 1 : -1;
                adminPlayerIndex = Math.floorMod(adminPlayerIndex + dir, players.size());
            }
            rebuild();
        } else if ("adminPlayerSelf".equals(data.action)) {
            if (!isAdmin()) return;
            List<com.hypixel.hytale.server.core.universe.PlayerRef> players = getOnlinePlayers();
            for (int i = 0; i < players.size(); i++) {
                if (players.get(i).getUuid().equals(playerRef.getUuid())) {
                    adminPlayerIndex = i;
                    break;
                }
            }
            rebuild();
        } else if ("adminProfNav".equals(data.action) && data.dir != null) {
            if (!isAdmin()) return;
            int dir = "1".equals(data.dir) ? 1 : -1;
            adminProfIndex = Math.floorMod(adminProfIndex + dir, CATALOG_ORDER.length);
            rebuild();
        } else if ("adminXp".equals(data.action) && data.amount != null) {
            if (!isAdmin()) return;
            com.hypixel.hytale.server.core.universe.PlayerRef target = adminTargetRef();
            if (target == null) return;
            ProfessionManager mgr = VaryonRpgPlugin.getInstance().getProfessionManager();
            if (mgr == null) return;
            Profession prof = CATALOG_ORDER[adminProfIndex];
            try {
                int amount = Integer.parseInt(data.amount);
                mgr.addXp(target.getUuid(), prof, amount);
            } catch (NumberFormatException ignored) {}
            rebuild();
        } else if ("adminLevel".equals(data.action) && data.delta != null) {
            if (!isAdmin()) return;
            com.hypixel.hytale.server.core.universe.PlayerRef target = adminTargetRef();
            if (target == null) return;
            ProfessionManager mgr = VaryonRpgPlugin.getInstance().getProfessionManager();
            if (mgr == null) return;
            Profession prof = CATALOG_ORDER[adminProfIndex];
            mgr.ensureAccount(target.getUuid(), target.getUsername());
            PlayerAccount acc = mgr.getAccount(target.getUuid());
            if (acc == null) return;
            int currentLevel = acc.getProgress(prof).getLevel();
            int newLevel;
            if ("max".equals(data.delta)) {
                newLevel = XpCurve.MAX_LEVEL;
            } else {
                try {
                    newLevel = Math.max(1, Math.min(XpCurve.MAX_LEVEL, currentLevel + Integer.parseInt(data.delta)));
                } catch (NumberFormatException ignored) { return; }
            }
            mgr.setLevel(target.getUuid(), prof, newLevel);
            rebuild();
        } else if ("adminResetTalents".equals(data.action)) {
            if (!isAdmin()) return;
            com.hypixel.hytale.server.core.universe.PlayerRef target = adminTargetRef();
            if (target == null) return;
            ProfessionManager mgr = VaryonRpgPlugin.getInstance().getProfessionManager();
            if (mgr == null) return;
            Profession prof = CATALOG_ORDER[adminProfIndex];
            mgr.resetTalents(target.getUuid(), prof);
            rebuild();
        } else if ("adminResetAll".equals(data.action)) {
            if (!isAdmin()) return;
            com.hypixel.hytale.server.core.universe.PlayerRef target = adminTargetRef();
            if (target == null) return;
            ProfessionManager mgr = VaryonRpgPlugin.getInstance().getProfessionManager();
            if (mgr == null) return;
            mgr.resetAccount(target.getUuid());
            rebuild();
        }
    }

    public static final class Data {
        public static final BuilderCodec<Data> CODEC =
            BuilderCodec.builder(Data.class, Data::new)
                .addField(new KeyedCodec<>("Action", Codec.STRING),
                    (d, v) -> d.action = v,
                    d -> d.action)
                .addField(new KeyedCodec<>("Tab", Codec.STRING),
                    (d, v) -> d.tab = v,
                    d -> d.tab)
                .addField(new KeyedCodec<>("Node", Codec.STRING),
                    (d, v) -> d.node = v,
                    d -> d.node)
                .addField(new KeyedCodec<>("ProfessionId", Codec.STRING),
                    (d, v) -> d.professionId = v,
                    d -> d.professionId)
                .addField(new KeyedCodec<>("TalentSlot", Codec.STRING),
                    (d, v) -> d.talentSlot = v,
                    d -> d.talentSlot)
                .addField(new KeyedCodec<>("Dir", Codec.STRING),
                    (d, v) -> d.dir = v,
                    d -> d.dir)
                .addField(new KeyedCodec<>("Amount", Codec.STRING),
                    (d, v) -> d.amount = v,
                    d -> d.amount)
                .addField(new KeyedCodec<>("Delta", Codec.STRING),
                    (d, v) -> d.delta = v,
                    d -> d.delta)
                .addField(new KeyedCodec<>("Index", Codec.STRING),
                    (d, v) -> d.index = v,
                    d -> d.index)
                .build();

        private String action;
        private String tab;
        private String node;
        private String professionId;
        private String talentSlot;
        private String dir;
        private String amount;
        private String delta;
        private String index;

        public Data() {}
    }

    private int[] currentRanks() {
        return (isEditMode && pendingRanks != null) ? pendingRanks : skillRanks;
    }

    private int pendingRemainingPoints(@Nullable PlayerAccount acc, @Nonnull Profession prof) {
        if (acc == null) return 0;
        int real = acc.availableTalentPoints(prof);
        if (!isEditMode || pendingRanks == null) return real;
        int spent = 0;
        for (int i = 0; i < pendingRanks.length; i++) {
            spent += Math.max(0, pendingRanks[i] - skillRanks[i]);
        }
        return real - spent;
    }

    private void enterEditMode() {
        if (isEditMode) return;
        isEditMode = true;
        pendingRanks = skillRanks.clone();
        PlayerAccount acc = currentAccount();
        Profession prof = currentTalentProfession();
        if (acc != null) {
            pendingBonusRanks = new int[BONUS_NODE_IDS.length];
            for (int i = 0; i < BONUS_NODE_IDS.length; i++) {
                pendingBonusRanks[i] = acc.getTalentRank(prof, BONUS_NODE_IDS[i]);
            }
        }
    }

    private void exitEditMode() {
        isEditMode = false;
        pendingRanks = null;
        pendingBonusRanks = null;
    }

    private boolean tryPendingAdd(int nodeIdx, @Nonnull SkillTreeDef tree) {
        if (pendingRanks == null) return false;
        if (pendingRanks[nodeIdx] >= tree.maxRanks[nodeIdx]) return false;
        if (!parentsAllow(pendingRanks, tree, nodeIdx)) return false;
        PlayerAccount acc = currentAccount();
        if (acc == null) return false;
        int pending = 0;
        for (int i = 0; i < pendingRanks.length; i++) pending += Math.max(0, pendingRanks[i] - skillRanks[i]);
        if (acc.availableTalentPoints(currentTalentProfession()) - pending <= 0) return false;
        pendingRanks[nodeIdx]++;
        return true;
    }

    private boolean tryPendingRemove(int nodeIdx) {
        if (pendingRanks == null) return false;
        if (pendingRanks[nodeIdx] <= skillRanks[nodeIdx]) return false;
        pendingRanks[nodeIdx]--;
        return true;
    }

    private boolean parentsAllow(@Nonnull int[] ranks, @Nonnull SkillTreeDef tree, int nodeIdx) {
        int[] parents = tree.parentGroups[nodeIdx];
        if (parents.length == 0) return true;
        for (int p : parents) {
            if (ranks[p] >= 1) return true;
        }
        return false;
    }
}
