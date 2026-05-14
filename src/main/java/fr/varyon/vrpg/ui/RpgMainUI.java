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
import fr.varyon.vrpg.rpg.PlayerAccount;
import fr.varyon.vrpg.rpg.Profession;
import fr.varyon.vrpg.rpg.ProfessionManager;
import fr.varyon.vrpg.rpg.ProfessionProgress;
import fr.varyon.vrpg.rpg.XpCurve;

import javax.annotation.Nonnull;
import java.util.Arrays;
import java.util.Locale;
import java.util.logging.Logger;

public final class RpgMainUI extends InteractiveCustomUIPage<RpgMainUI.Data> {

    private static final Logger LOG = Logger.getLogger(RpgMainUI.class.getName());

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
    };

    private static final String ICON_BASE = "Pages/VaryonRpg/Icons/";

    private static final String NODE_FILL = "#1A1F29FF";
    private static final String NODE_BORDER = "#4E576DFF";
    private static final String NODE_BORDER_SELECTION = "#FFFFFFFF";
    private static final String NODE_BORDER_ALLOCATED = "#31C677FF";

    private static final String NODE_VEIL = "#14182166";

    private static final PatchStyle NODE_FILL_STYLE =
        new PatchStyle().setColor(Value.of(NODE_FILL));

    private static final PatchStyle NODE_VEIL_STYLE =
        new PatchStyle().setColor(Value.of(NODE_VEIL));

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
    };

    private static final String[][] TREE_NODES = {
        {"0", "Guerrier", "Passif", "Sommet gauche.", "Prototype d’arbre.", "AbilityIconSword_40.png"},
        {"1", "Entraînement aux armes", "Passif", "Sommet droit.", "Icônes provisoires.", "Weapon_Training_Icon.png"},
        {"2", "Entraînement défensif", "Passif", "Fusion des deux sommets.", "Emplacement réservé.", "Defense_Training_Icon.png"},
        {"3", "Entraînement de précision", "Passif", "Colonne gauche — palier 1.", "Emplacement réservé.", "Precision_Training_Icon.png"},
        {"4", "Entraînement à la vigueur", "Passif", "Colonne centre — palier 1.", "Emplacement réservé.", "Vigor_Training_Icon.png"},
        {"5", "Cri de guerre", "Actif", "Colonne droite — palier 1.", "Emplacement réservé.", "Warcry_Icon.png"},
        {"6", "Coup puissant", "Actif", "Colonne gauche — palier 2.", "Emplacement réservé.", "Heavy_Swing_Icon.png"},
        {"7", "Charge brutale", "Actif", "Colonne centre — palier 2.", "Emplacement réservé.", "Brutal_Charge_Icon.png"},
        {"8", "Serment du guerrier", "Passif", "Colonne droite — palier 2.", "Emplacement réservé.", "Warrior_Oath_Icon.png"},
        {"9", "Frappe défensive", "Actif", "Convergence des trois colonnes.", "Emplacement réservé.", "Guarded_Strike_Icon.png"},
        {"10", "Second souffle", "Passif", "Sortie gauche.", "Icône libre à assigner.", "Second_Wind_Icon.png"},
        {"11", "Position de combat", "Passif", "Sortie droite.", "Icône libre à assigner.", "Battle_Footing_Icon.png"},
    };

    private final PlayerRef playerRef;
    private String activeTab = "character";
    private int selectedNode = 2;
    private int hoveredNode = -1;
    private final int[] skillRanks = new int[TREE_NODES.length];
    private String reconvertSourceId = null;

    public RpgMainUI(@Nonnull PlayerRef playerRef) {
        super(playerRef, CustomPageLifetime.CanDismiss, Data.CODEC);
        this.playerRef = playerRef;
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref,
                      @Nonnull UICommandBuilder uiBuilder,
                      @Nonnull UIEventBuilder eventBuilder,
                      @Nonnull Store<EntityStore> store) {
        uiBuilder.append("CharacterPage.ui");
        uiBuilder.append("#CharacterTabMount", "CharacterTabProfession.ui");
        uiBuilder.append("#SkillsTabMount", "CharacterTabSkills.ui");
        uiBuilder.append("#ArtisansTabMount", "CharacterTabArtisans.ui");
        uiBuilder.append("#ClassementTabMount", "CharacterTabClassement.ui");

        uiBuilder.set("#CharacterTabContent.Visible", "character".equals(activeTab));
        uiBuilder.set("#SkillsTabContent.Visible", "skills".equals(activeTab));
        uiBuilder.set("#ArtisansTabContent.Visible", "artisans".equals(activeTab));
        uiBuilder.set("#ClassementTabContent.Visible", "classement".equals(activeTab));

        uiBuilder.set("#TabCharacterUnderline.Visible", "character".equals(activeTab));
        uiBuilder.set("#TabSkillsUnderline.Visible", "skills".equals(activeTab));
        uiBuilder.set("#TabArtisansUnderline.Visible", "artisans".equals(activeTab));
        uiBuilder.set("#TabClassementUnderline.Visible", "classement".equals(activeTab));

        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#TabCharacterButton",
            EventData.of("Action", "tab").append("Tab", "character"), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#TabSkillsButton",
            EventData.of("Action", "tab").append("Tab", "skills"), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#TabArtisansButton",
            EventData.of("Action", "tab").append("Tab", "artisans"), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#TabClassementButton",
            EventData.of("Action", "tab").append("Tab", "classement"), false);

        if ("character".equals(activeTab)) {
            populateCharacterProfessions(uiBuilder, eventBuilder);
        } else if ("skills".equals(activeTab)) {
            populateSketchSkillTree(uiBuilder, eventBuilder);
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

    private static void applyGaugeBar(@Nonnull UICommandBuilder ui,
                                      @Nonnull String fillId,
                                      long xpInLevel, long xpToNext) {
        double ratio = xpToNext > 0 ? Math.min(1.0, (double) xpInLevel / xpToNext) : 1.0;
        LOG.info("[RPG-Gauge] " + fillId + " xpInLevel=" + xpInLevel
            + " xpToNext=" + xpToNext + " ratio=" + String.format("%.3f", ratio));
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
        int activeFilled = 0;
        for (Profession s : activeSlots) if (s != null) activeFilled++;

        uiBuilder.set("#ProfessionSectionSubtitle.TextSpans",
            Message.raw("M\u00e9tiers actifs (" + activeFilled + "/2)"));

        for (int i = 0; i < 2; i++) {
            String p = "#ProfessionActiveCard" + i;
            Profession active = activeSlots[i];
            if (active != null && acc != null) {
                ProfessionProgress prog = acc.getProgress(active);
                uiBuilder.set(p + ".Visible", true);
                uiBuilder.set(p + "Name.TextSpans", Message.raw(active.getDisplayName().toUpperCase(Locale.FRENCH)));
                uiBuilder.set(p + "Icon.ItemId", active.getIconItemId());
                uiBuilder.set(p + "Level.TextSpans",
                    Message.raw(("Niveau " + prog.getLevel()
                        + " \u2022 " + prog.getXpInLevel() + " / " + prog.getXpToNextLevel() + " XP")
                        .toUpperCase(Locale.FRENCH)));
                uiBuilder.set(p + "Reconvert.Visible", true);
                applyGaugeBar(uiBuilder,
                    p + "ProgBarFill",
                    prog.getXpInLevel(), prog.getXpToNextLevel());
                eventBuilder.addEventBinding(
                    CustomUIEventBindingType.Activating,
                    p + "Reconvert",
                    EventData.of("Action", "professionReconvert")
                        .append("ProfessionId", active.getId())
                        .append("Node", Integer.toString(i)),
                    false
                );
            } else {
                uiBuilder.set(p + ".Visible", false);
                uiBuilder.set(p + "Reconvert.Visible", false);
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
            uiBuilder.set(id + "Level.TextSpans", Message.raw(("Niveau " + level).toUpperCase(Locale.FRENCH)));
            long catXpInLevel = catProg == null ? 0L : catProg.getXpInLevel();
            long catXpToNext = catProg == null ? 0L : catProg.getXpToNextLevel();
            applyGaugeBar(uiBuilder, id + "ProgBarFill", catXpInLevel, catXpToNext);

            boolean selectable = selectMode
                && !p.isSpecialized()
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

            if (p.isSpecialized()) {
                Profession parent = p.getPrereq();
                int need = p.getPrereqLevel();
                String parentName = parent == null ? "?" : parent.getDisplayName();
                uiBuilder.set(id + "Prereq.Visible", true);
                uiBuilder.set(id + "Prereq.TextSpans",
                    Message.raw("Pr\u00e9requis : niveau " + need + " " + parentName));
                boolean unlocked = acc != null && acc.isUnlocked(p);
                uiBuilder.set(id + "Lock.Visible", !unlocked);
                if (!unlocked) {
                    uiBuilder.set(id + "Lock.TextSpans", Message.raw("Verrouill\u00e9"));
                }
            } else {
                uiBuilder.set(id + "Prereq.Visible", false);
                uiBuilder.set(id + "Lock.Visible", false);
            }
        }
    }

    private Profession currentTalentProfession() {
        PlayerAccount acc = currentAccount();
        if (acc == null) return Profession.MINEUR;
        Profession slot0 = acc.getActiveSlot0();
        return slot0 != null ? slot0 : Profession.MINEUR;
    }

    private void loadSkillRanksFromAccount() {
        PlayerAccount acc = currentAccount();
        if (acc == null) {
            Arrays.fill(skillRanks, 0);
            return;
        }
        Profession prof = currentTalentProfession();
        for (int i = 0; i < TREE_NODES.length; i++) {
            skillRanks[i] = acc.getTalentRank(prof, TREE_NODES[i][0]);
        }
    }

    private void populateSketchSkillTree(@Nonnull UICommandBuilder uiBuilder,
                                         @Nonnull UIEventBuilder eventBuilder) {
        loadSkillRanksFromAccount();
        PlayerAccount acc = currentAccount();
        Profession prof = currentTalentProfession();
        int invested = 0;
        for (int r : skillRanks) invested += r;
        int remainingPoints = acc == null
            ? Math.max(0, SKILL_POINTS_BUDGET - invested)
            : acc.availableTalentPoints(prof);
        uiBuilder.set("#SkillTreePointsValue.TextSpans",
            Message.raw("Points restants : " + remainingPoints + " (" + prof.getDisplayName() + ")"));

        for (String legacyId : LEGACY_STATIC_EDGE_IDS) {
            uiBuilder.set(legacyId + ".Visible", false);
        }
        hideEdgeSegmentRange(uiBuilder, 0, SKILL_TREE_EDGE_SEGMENTS);

        int seg = 0;

        seg = layoutMergeTwoToOne(uiBuilder, seg,
            cx(SLOT_LT[0]), bot(SLOT_LT[0]),
            cx(SLOT_LT[1]), bot(SLOT_LT[1]),
            cx(SLOT_LT[2]), top(SLOT_LT[2]));

        seg = layoutSplitOneToThree(uiBuilder, seg,
            cx(SLOT_LT[2]), bot(SLOT_LT[2]),
            cx(SLOT_LT[3]),
            cx(SLOT_LT[4]),
            cx(SLOT_LT[5]),
            top(SLOT_LT[3]));

        seg = layoutVerticalConnector(uiBuilder, seg,
            cx(SLOT_LT[3]), bot(SLOT_LT[3]), top(SLOT_LT[6]));
        seg = layoutVerticalConnector(uiBuilder, seg,
            cx(SLOT_LT[4]), bot(SLOT_LT[4]), top(SLOT_LT[7]));
        seg = layoutVerticalConnector(uiBuilder, seg,
            cx(SLOT_LT[5]), bot(SLOT_LT[5]), top(SLOT_LT[8]));

        seg = layoutMergeThreeToOne(uiBuilder, seg,
            cx(SLOT_LT[6]), bot(SLOT_LT[6]),
            cx(SLOT_LT[7]), bot(SLOT_LT[7]),
            cx(SLOT_LT[8]), bot(SLOT_LT[8]),
            cx(SLOT_LT[9]),
            top(SLOT_LT[9]));

        seg = layoutSplitOneToTwo(uiBuilder, seg,
            cx(SLOT_LT[9]), bot(SLOT_LT[9]),
            cx(SLOT_LT[10]),
            cx(SLOT_LT[11]),
            top(SLOT_LT[10]));

        hideEdgeSegmentRange(uiBuilder, seg, SKILL_TREE_EDGE_SEGMENTS);

        for (int i = 0; i < TREE_NODES.length; i++) {
            String[] node = TREE_NODES[i];
            String id = node[0];
            int sl = SLOT_LT[i][0];
            int st = SLOT_LT[i][1];

            positionSkillSlot(uiBuilder, id, sl, st);
            positionSkillRank(uiBuilder, id, sl, st);

            String iconPath = ICON_BASE + node[5];
            PatchStyle iconStyle = new PatchStyle().setTexturePath(Value.of(iconPath));
            int allocated = skillRanks[i];

            uiBuilder.set("#SkillTreeNode" + id + "Slot.Visible", true);

            uiBuilder.set("#SkillTreeNode" + id + "Unlocked.Visible", true);
            uiBuilder.setObject("#SkillTreeNode" + id + "Unlocked.Background", NODE_FILL_STYLE);

            uiBuilder.set("#SkillTreeNode" + id + "Icon.Visible", true);
            uiBuilder.setObject("#SkillTreeNode" + id + "Icon.Background", iconStyle);

            uiBuilder.set("#SkillTreeNode" + id + "RankText.Visible", true);
            uiBuilder.set("#SkillTreeNode" + id + "RankText.TextSpans",
                Message.raw(allocated + "/" + MAX_RANK_PER_NODE));

            uiBuilder.set("#SkillTreeNode" + id + ".Visible", true);
            eventBuilder.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#SkillTreeNode" + id,
                EventData.of("Action", "skill").append("Node", id),
                false
            );
            eventBuilder.addEventBinding(
                CustomUIEventBindingType.MouseEntered,
                "#SkillTreeNode" + id,
                EventData.of("Action", "skillHover").append("Node", id),
                false
            );
            eventBuilder.addEventBinding(
                CustomUIEventBindingType.MouseExited,
                "#SkillTreeNode" + id,
                EventData.of("Action", "skillHoverEnd").append("Node", id),
                false
            );
        }

        applySkillTreeSelectionAndHoverChrome(uiBuilder);
        uiBuilder.set("#SkillTreeAttribuerButton.Disabled",
            remainingPoints <= 0
                || !skillTreeParentsAllowSelectedAllocation(skillRanks)
                || skillRanks[selectedNode] >= MAX_RANK_PER_NODE);
        uiBuilder.set("#SkillTreeResetButton.Visible", true);
        eventBuilder.addEventBinding(
            CustomUIEventBindingType.Activating,
            "#SkillTreeAttribuerButton",
            EventData.of("Action", "allocate"),
            false
        );
        eventBuilder.addEventBinding(
            CustomUIEventBindingType.Activating,
            "#SkillTreeResetButton",
            EventData.of("Action", "resetSkills"),
            false
        );
    }

    private void applySkillTreeSelectionAndHoverChrome(@Nonnull UICommandBuilder uiBuilder) {
        String selectedId = TREE_NODES[selectedNode][0];
        String hoverId = hoveredNode >= 0 ? TREE_NODES[hoveredNode][0] : null;

        for (int i = 0; i < TREE_NODES.length; i++) {
            String id = TREE_NODES[i][0];
            int allocated = skillRanks[i];
            boolean nodeSelected = id.equals(selectedId);
            boolean nodeHovered = hoverId != null && id.equals(hoverId);
            String borderRgb;
            if (allocated >= 1) {
                borderRgb = NODE_BORDER_ALLOCATED;
            } else if (nodeSelected || nodeHovered) {
                borderRgb = NODE_BORDER_SELECTION;
            } else {
                borderRgb = NODE_BORDER;
            }
            PatchStyle borderStyle = new PatchStyle().setColor(Value.of(borderRgb));
            uiBuilder.setObject("#SkillTreeNode" + id + "Slot.Background", borderStyle);
            uiBuilder.set("#SkillTreeNode" + id + "Veil.Visible",
                !nodeSelected && !nodeHovered && allocated == 0);
            uiBuilder.setObject("#SkillTreeNode" + id + "Veil.Background", NODE_VEIL_STYLE);
        }

        int panelNode = hoveredNode >= 0 ? hoveredNode : selectedNode;
        String[] sel = TREE_NODES[panelNode];
        uiBuilder.set("#SkillTreeSelectedTitle.TextSpans", Message.raw(sel[1]));
        uiBuilder.set("#SkillTreeSelectedStatus.TextSpans", Message.raw(sel[2]));
        uiBuilder.set("#SkillTreeSelectedDescriptionLine0.Visible", true);
        uiBuilder.set("#SkillTreeSelectedDescriptionLine0.TextSpans", Message.raw(sel[3]));
        uiBuilder.set("#SkillTreeSelectedDescriptionLine1.Visible", true);
        uiBuilder.set("#SkillTreeSelectedDescriptionLine1.TextSpans", Message.raw(sel[4]));
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
            slotLeft + ICON_INSET, slotTop + ICON_INSET, ICON_SIZE, ICON_SIZE);
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

        if ("tab".equals(data.action) && data.tab != null) {
            activeTab = data.tab;
            hoveredNode = -1;
            rebuild();
        } else if ("skill".equals(data.action) && data.node != null) {
            for (int i = 0; i < TREE_NODES.length; i++) {
                if (TREE_NODES[i][0].equals(data.node)) {
                    selectedNode = i;
                    break;
                }
            }
            rebuild();
        } else if ("skillHover".equals(data.action) && data.node != null) {
            for (int i = 0; i < TREE_NODES.length; i++) {
                if (TREE_NODES[i][0].equals(data.node)) {
                    hoveredNode = i;
                    break;
                }
            }
            sendSkillTreeHoverChromeUpdate();
        } else if ("skillHoverEnd".equals(data.action) && data.node != null) {
            for (int i = 0; i < TREE_NODES.length; i++) {
                if (TREE_NODES[i][0].equals(data.node) && hoveredNode == i) {
                    hoveredNode = -1;
                    break;
                }
            }
            sendSkillTreeHoverChromeUpdate();
        } else if ("allocate".equals(data.action)) {
            loadSkillRanksFromAccount();
            if (skillRanks[selectedNode] < MAX_RANK_PER_NODE
                && skillTreeParentsAllowSelectedAllocation(skillRanks)) {
                ProfessionManager mgr = VaryonRpgPlugin.getInstance().getProfessionManager();
                if (mgr != null) {
                    Profession prof = currentTalentProfession();
                    String nodeId = TREE_NODES[selectedNode][0];
                    mgr.allocateTalent(playerRef.getUuid(), prof, nodeId, MAX_RANK_PER_NODE);
                }
            }
            rebuild();
        } else if ("resetSkills".equals(data.action)) {
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
                .build();

        private String action;
        private String tab;
        private String node;
        private String professionId;

        public Data() {}
    }

    private boolean skillTreeParentsAllowSelectedAllocation(@Nonnull int[] ranks) {
        int[] parents = SKILL_TREE_PARENT_GROUPS[selectedNode];
        if (parents.length == 0) {
            return true;
        }
        for (int p : parents) {
            if (ranks[p] >= 1) {
                return true;
            }
        }
        return false;
    }
}
