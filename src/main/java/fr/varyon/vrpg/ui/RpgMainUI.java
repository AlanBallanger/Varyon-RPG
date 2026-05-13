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

    private static final String ICON_BASE = "Pages/FubsysRpg/Icons/";

    private static final String NODE_FILL = "#1A1F29FF";
    private static final String NODE_BORDER = "#4E576DFF";
    private static final String NODE_BORDER_SELECTION = "#FFFFFFFF";
    private static final String NODE_BORDER_ALLOCATED = "#31C677FF";

    private static final String NODE_VEIL = "#14182140";

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
    private static final int SLOT = 76;
    private static final int BTN_PAD = 6;
    private static final int ICON_INSET = 18;
    private static final int FILL_INSET = 3;
    private static final int FILL_SIZE = 70;
    private static final int ICON_SIZE = 40;
    private static final int BTN_SIZE = 64;
    private static final int MAX_RANK_PER_NODE = 5;

    private static final int RANK_LABEL_W = 44;
    private static final int RANK_LABEL_H = 14;
    private static final int RANK_LABEL_GAP_TOP = 2;
    private static final int RANK_LABEL_SHIFT_RIGHT = 48;

    private static final int[][] SLOT_LT = {
        {264, 32},
        {444, 32},
        {354, 130},
        {204, 228},
        {354, 228},
        {504, 228},
        {204, 326},
        {354, 326},
        {504, 326},
        {354, 424},
        {264, 522},
        {444, 522},
    };

    private static final String[][] TREE_NODES = {
        {"0", "Warrior", "Passive", "Sommet gauche.", "Vue arbre prototype.", "AbilityIconSword_40.png"},
        {"1", "Weapon Training", "Passive", "Sommet droit.", "Icônes provisoires.", "Weapon_Training_Icon.png"},
        {"2", "Defense Training", "Passive", "Fusion des deux sommets.", "Placeholder.", "Defense_Training_Icon.png"},
        {"3", "Precision Training", "Passive", "Colonne gauche — niveau 1.", "Placeholder.", "Precision_Training_Icon.png"},
        {"4", "Vigor Training", "Passive", "Colonne centre — niveau 1.", "Placeholder.", "Vigor_Training_Icon.png"},
        {"5", "Warcry", "Active", "Colonne droite — niveau 1.", "Placeholder.", "Warcry_Icon.png"},
        {"6", "Heavy Swing", "Active", "Colonne gauche — niveau 2.", "Placeholder.", "Heavy_Swing_Icon.png"},
        {"7", "Brutal Charge", "Active", "Colonne centre — niveau 2.", "Placeholder.", "Brutal_Charge_Icon.png"},
        {"8", "Warrior Oath", "Passive", "Colonne droite — niveau 2.", "Placeholder.", "Warrior_Oath_Icon.png"},
        {"9", "Guarded Strike", "Active", "Convergence des trois colonnes.", "Placeholder.", "Guarded_Strike_Icon.png"},
        {"10", "Second Wind", "Passive", "Sortie gauche.", "Icône dupliquée / libre.", "Second_Wind_Icon.png"},
        {"11", "Battle Footing", "Passive", "Sortie droite.", "Icône dupliquée / libre.", "Battle_Footing_Icon.png"},
    };

    private final PlayerRef playerRef;
    private String activeTab = "skills";
    private int selectedNode = 2;
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
        uiBuilder.set("#LoadoutTabContent.Visible", "loadout".equals(activeTab));

        uiBuilder.set("#TabCharacterUnderline.Visible", "character".equals(activeTab));
        uiBuilder.set("#TabSkillsUnderline.Visible", "skills".equals(activeTab));
        uiBuilder.set("#TabLoadoutUnderline.Visible", "loadout".equals(activeTab));

        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#TabCharacterButton",
            EventData.of("Action", "tab").append("Tab", "character"), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#TabSkillsButton",
            EventData.of("Action", "tab").append("Tab", "skills"), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#TabLoadoutButton",
            EventData.of("Action", "tab").append("Tab", "loadout"), false);

        if ("skills".equals(activeTab)) {
            populateSketchSkillTree(uiBuilder, eventBuilder);
        }
    }

    private void populateSketchSkillTree(@Nonnull UICommandBuilder uiBuilder,
                                         @Nonnull UIEventBuilder eventBuilder) {
        int invested = 0;
        for (int r : skillRanks) {
            invested += r;
        }
        uiBuilder.set("#SkillTreePointsValue.TextSpans",
            Message.raw("Points investis: " + invested));

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

        String selectedId = TREE_NODES[selectedNode][0];

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
            boolean nodeSelected = id.equals(selectedId);
            String borderRgb;
            if (allocated >= 1) {
                borderRgb = NODE_BORDER_ALLOCATED;
            } else if (nodeSelected) {
                borderRgb = NODE_BORDER_SELECTION;
            } else {
                borderRgb = NODE_BORDER;
            }
            PatchStyle borderStyle = new PatchStyle().setColor(Value.of(borderRgb));

            uiBuilder.set("#SkillTreeNode" + id + "Slot.Visible", true);
            uiBuilder.setObject("#SkillTreeNode" + id + "Slot.Background", borderStyle);

            uiBuilder.set("#SkillTreeNode" + id + "Unlocked.Visible", true);
            uiBuilder.setObject("#SkillTreeNode" + id + "Unlocked.Background", NODE_FILL_STYLE);

            uiBuilder.set("#SkillTreeNode" + id + "Icon.Visible", true);
            uiBuilder.setObject("#SkillTreeNode" + id + "Icon.Background", iconStyle);

            uiBuilder.set("#SkillTreeNode" + id + "RankText.Visible", true);
            uiBuilder.set("#SkillTreeNode" + id + "RankText.TextSpans",
                Message.raw(allocated + "/" + MAX_RANK_PER_NODE));

            uiBuilder.set("#SkillTreeNode" + id + "Veil.Visible",
                !nodeSelected && allocated == 0);
            uiBuilder.setObject("#SkillTreeNode" + id + "Veil.Background", NODE_VEIL_STYLE);

            uiBuilder.set("#SkillTreeNode" + id + ".Visible", true);
            eventBuilder.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#SkillTreeNode" + id,
                EventData.of("Action", "skill").append("Node", id),
                false
            );
        }

        for (int n = TREE_NODES.length; n <= 63; n++) {
            uiBuilder.set("#SkillTreeNode" + n + "Slot.Visible", false);
            uiBuilder.set("#SkillTreeNode" + n + ".Visible", false);
        }

        String[] sel = TREE_NODES[selectedNode];
        uiBuilder.set("#SkillTreeSelectedTitle.TextSpans", Message.raw(sel[1]));
        uiBuilder.set("#SkillTreeSelectedStatus.TextSpans", Message.raw(sel[2]));
        uiBuilder.set("#SkillTreeSelectedDescriptionLine0.Visible", true);
        uiBuilder.set("#SkillTreeSelectedDescriptionLine0.TextSpans", Message.raw(sel[3]));
        uiBuilder.set("#SkillTreeSelectedDescriptionLine1.Visible", true);
        uiBuilder.set("#SkillTreeSelectedDescriptionLine1.TextSpans", Message.raw(sel[4]));

        uiBuilder.set("#SkillTreeAttribuerButton.Visible", true);
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
            slotLeft + BTN_PAD, slotTop + BTN_PAD, BTN_SIZE, BTN_SIZE);
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
        int yH = elbowY(Math.min(yBotA, yBotB), yTopMid);

        showEdge(ui, seg++, vx(cxA), yBotA, RAIL, yH - yBotA);
        showEdge(ui, seg++, vx(cxB), yBotB, RAIL, yH - yBotB);
        int barL = vx(Math.min(cxA, cxB));
        showEdge(ui, seg++, barL, yH - 2, vx(Math.max(cxA, cxB)) - barL + RAIL, RAIL);
        showEdge(ui, seg++, vx(cxMid), yH + 2, RAIL, yTopMid - (yH + 2));
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
        int ySplit = elbowY(yBotP, yTopChildRow);

        showEdge(ui, seg++, vx(cxP), yBotP, RAIL, ySplit - yBotP);
        int barL = vx(Math.min(cxL, Math.min(cxM, cxR)));
        showEdge(ui, seg++, barL, ySplit - 2,
            vx(Math.max(cxL, Math.max(cxM, cxR))) - barL + RAIL, RAIL);
        showEdge(ui, seg++, vx(cxL), ySplit + 2, RAIL, yTopChildRow - (ySplit + 2));
        showEdge(ui, seg++, vx(cxM), ySplit + 2, RAIL, yTopChildRow - (ySplit + 2));
        showEdge(ui, seg++, vx(cxR), ySplit + 2, RAIL, yTopChildRow - (ySplit + 2));
        return seg;
    }

    private static int layoutVerticalConnector(@Nonnull UICommandBuilder ui,
                                               int seg,
                                               int cx,
                                               int yFrom,
                                               int yTo) {
        showEdge(ui, seg++, vx(cx), yFrom, RAIL, yTo - yFrom);
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
        int bottomMin = Math.min(yBotA, Math.min(yBotB, yBotC));
        int yH = elbowY(bottomMin, yTopMid);

        showEdge(ui, seg++, vx(cxA), yBotA, RAIL, yH - yBotA);
        showEdge(ui, seg++, vx(cxB), yBotB, RAIL, yH - yBotB);
        showEdge(ui, seg++, vx(cxC), yBotC, RAIL, yH - yBotC);
        int barL = vx(Math.min(cxA, Math.min(cxB, cxC)));
        showEdge(ui, seg++, barL, yH - 2,
            vx(Math.max(cxA, Math.max(cxB, cxC))) - barL + RAIL, RAIL);
        showEdge(ui, seg++, vx(cxMid), yH + 2, RAIL, yTopMid - (yH + 2));
        return seg;
    }

    private static int layoutSplitOneToTwo(@Nonnull UICommandBuilder ui,
                                          int seg,
                                          int cxP,
                                          int yBotP,
                                          int cxL,
                                          int cxR,
                                          int yTopChildRow) {
        int yH = elbowY(yBotP, yTopChildRow);

        showEdge(ui, seg++, vx(cxP), yBotP, RAIL, yH - yBotP);
        int barL = vx(Math.min(cxL, cxR));
        showEdge(ui, seg++, barL, yH - 2, vx(Math.max(cxL, cxR)) - barL + RAIL, RAIL);
        showEdge(ui, seg++, vx(cxL), yH + 2, RAIL, yTopChildRow - (yH + 2));
        showEdge(ui, seg++, vx(cxR), yH + 2, RAIL, yTopChildRow - (yH + 2));
        return seg;
    }

    private static int elbowY(int yBottomRail, int yTopChildRow) {
        int mid = yBottomRail + (yTopChildRow - yBottomRail) / 2;
        int lo = yBottomRail + RAIL + 4;
        int hi = yTopChildRow - RAIL - 6;
        if (hi <= lo) {
            return (lo + hi) >>> 1;
        }
        return Math.min(Math.max(mid, lo), hi);
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
            rebuild();
        } else if ("skill".equals(data.action) && data.node != null) {
            for (int i = 0; i < TREE_NODES.length; i++) {
                if (TREE_NODES[i][0].equals(data.node)) {
                    selectedNode = i;
                    break;
                }
            }
            rebuild();
        } else if ("allocate".equals(data.action)) {
            if (skillRanks[selectedNode] < MAX_RANK_PER_NODE) {
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
                .build();

        private String action;
        private String tab;
        private String node;

        public Data() {}
    }
}
