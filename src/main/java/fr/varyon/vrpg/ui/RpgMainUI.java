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

import javax.annotation.Nonnull;
import java.util.Arrays;

public final class RpgMainUI extends InteractiveCustomUIPage<RpgMainUI.Data> {

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

    private static final String ICON_BASE = "Pages/FubsysRpg/Icons/";

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
    private static final int STEM_DOWN_FROM_PARENT = 15;
    private static final int STEM_DOWN_TO_CHILD = 13;
    private static final int STEM_COLUMN = 30;

    private static final int SLOT = 76;
    private static final int ICON_INSET = 18;
    private static final int FILL_INSET = 3;
    private static final int FILL_SIZE = 70;
    private static final int ICON_SIZE = 40;
    private static final int MAX_RANK_PER_NODE = 5;
    private static final int SKILL_POINTS_BUDGET = 35;
    private static final int PROFESSION_CARD_SLOTS = 4;

    private static final class ProfessionCardData {

        final String label;
        final String iconFile;
        final int level;
        final int xpTowardNext;
        final int xpForNextLevel;
        final int talentsUnlocked;

        ProfessionCardData(String label,
                           String iconFile,
                           int level,
                           int xpTowardNext,
                           int xpForNextLevel,
                           int talentsUnlocked) {
            this.label = label;
            this.iconFile = iconFile;
            this.level = level;
            this.xpTowardNext = xpTowardNext;
            this.xpForNextLevel = xpForNextLevel;
            this.talentsUnlocked = talentsUnlocked;
        }
    }

    private static final ProfessionCardData[] PROFESSION_DEMO_ROWS = {
        new ProfessionCardData("Chasseur", "Heavy_Swing_Icon.png", 20,
            3020, 6510, 3),
        new ProfessionCardData("Mineur", "Brutal_Charge_Icon.png", 22,
            4800, 8200, 8),
        new ProfessionCardData("Forgeron", "Warrior_Oath_Icon.png", 15,
            1200, 4000, 5),
    };

    private static final int RANK_LABEL_W = 44;
    private static final int RANK_LABEL_H = 14;
    private static final int RANK_LABEL_GAP_TOP = -1;
    private static final int RANK_LABEL_SHIFT_RIGHT = 48;

    private static final int[][] SLOT_LT = {
        {264, 32},
        {444, 32},
        {354, 140},
        {204, 248},
        {354, 248},
        {504, 248},
        {204, 354},
        {354, 354},
        {504, 354},
        {354, 462},
        {264, 570},
        {444, 570},
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
    private String activeTab = "skills";
    private int selectedNode = 2;
    private int hoveredNode = -1;
    private final int[] skillRanks = new int[TREE_NODES.length];

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

    private void populateCharacterProfessions(@Nonnull UICommandBuilder uiBuilder,
                                              @Nonnull UIEventBuilder eventBuilder) {
        uiBuilder.set("#ProfessionSectionSubtitle.TextSpans",
            Message.raw("Gérez vos métiers et talents."));
        uiBuilder.set("#ProfessionSectionSubtitle.Visible", true);

        int shown = Math.min(PROFESSION_DEMO_ROWS.length, PROFESSION_CARD_SLOTS);
        for (int i = 0; i < PROFESSION_CARD_SLOTS; i++) {
            boolean visible = i < shown;
            uiBuilder.set("#ProfessionCard" + i + ".Visible", visible);
            if (!visible) {
                continue;
            }
            ProfessionCardData row = PROFESSION_DEMO_ROWS[i];
            PatchStyle iconStyle = new PatchStyle()
                .setTexturePath(Value.of(ICON_BASE + row.iconFile));
            uiBuilder.setObject("#ProfessionCard" + i + "Icon.Background", iconStyle);
            uiBuilder.set("#ProfessionCard" + i + "Name.TextSpans", Message.raw(row.label));
            uiBuilder.set("#ProfessionCard" + i + "Level.TextSpans",
                Message.raw("Nv." + row.level));
            uiBuilder.set("#ProfessionCard" + i + "XpText.TextSpans",
                Message.raw(row.xpTowardNext + " / " + row.xpForNextLevel + " XP"));
            uiBuilder.set("#ProfessionCard" + i + "Talents.TextSpans",
                Message.raw("Talents débloqués : " + row.talentsUnlocked));
            double frac = row.xpForNextLevel <= 0
                ? 0.0
                : Math.min(1.0, (double) row.xpTowardNext / (double) row.xpForNextLevel);
            float xpFrac = (float) frac;
            uiBuilder.set("#ProfessionCard" + i + "Xp.Value", xpFrac);
            eventBuilder.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#ProfessionCard" + i + "Reconvert",
                EventData.of("Action", "professionReconvert")
                    .append("ProfessionId", Integer.toString(i)),
                false
            );
        }
    }

    private void populateSketchSkillTree(@Nonnull UICommandBuilder uiBuilder,
                                         @Nonnull UIEventBuilder eventBuilder) {
        int invested = 0;
        for (int r : skillRanks) {
            invested += r;
        }
        int remainingPoints = Math.max(0, SKILL_POINTS_BUDGET - invested);
        uiBuilder.set("#SkillTreePointsValue.TextSpans",
            Message.raw("Points restants : " + remainingPoints));

        for (String legacyId : LEGACY_STATIC_EDGE_IDS) {
            uiBuilder.set(legacyId + ".Visible", false);
        }
        hideEdgeSegmentRange(uiBuilder, 0, 256);

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

        hideEdgeSegmentRange(uiBuilder, seg, 256);

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

        for (int n = TREE_NODES.length; n <= 63; n++) {
            uiBuilder.set("#SkillTreeNode" + n + "Slot.Visible", false);
            uiBuilder.set("#SkillTreeNode" + n + ".Visible", false);
        }

        uiBuilder.set("#SkillTreeAttribuerButton.Visible", true);
        uiBuilder.set("#SkillTreeAttribuerButton.Disabled",
            invested >= SKILL_POINTS_BUDGET
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
        showEdge(ui, seg++, vx(cxMid), barBot, RAIL, STEM_DOWN_TO_CHILD);
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
        showEdge(ui, seg++, vx(cxL), barBot, RAIL, STEM_DOWN_TO_CHILD);
        showEdge(ui, seg++, vx(cxM), barBot, RAIL, STEM_DOWN_TO_CHILD);
        showEdge(ui, seg++, vx(cxR), barBot, RAIL, STEM_DOWN_TO_CHILD);
        return seg;
    }

    private static int layoutVerticalConnector(@Nonnull UICommandBuilder ui,
                                               int seg,
                                               int cx,
                                               int yFrom,
                                               int yTo) {
        showEdge(ui, seg++, vx(cx), yFrom, RAIL, STEM_COLUMN);
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
        showEdge(ui, seg++, vx(cxMid), barBot, RAIL, STEM_DOWN_TO_CHILD);
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
        showEdge(ui, seg++, vx(cxL), barBot, RAIL, STEM_DOWN_TO_CHILD);
        showEdge(ui, seg++, vx(cxR), barBot, RAIL, STEM_DOWN_TO_CHILD);
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
            int spent = 0;
            for (int r : skillRanks) {
                spent += r;
            }
            if (spent < SKILL_POINTS_BUDGET
                && skillRanks[selectedNode] < MAX_RANK_PER_NODE
                && skillTreeParentsAllowSelectedAllocation(skillRanks)) {
                skillRanks[selectedNode]++;
            }
            rebuild();
        } else if ("resetSkills".equals(data.action)) {
            Arrays.fill(skillRanks, 0);
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
