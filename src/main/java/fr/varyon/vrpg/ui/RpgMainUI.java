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

public final class RpgMainUI extends InteractiveCustomUIPage<RpgMainUI.Data> {

    private static final String ICON_BASE = "Pages/FubsysRpg/Icons/";

    private static final String NODE_FILL = "#1A1F29FF";
    private static final String NODE_BORDER = "#252934FF";
    private static final String NODE_BORDER_SELECTED = "#31C677FF";

    private static final PatchStyle NODE_FILL_STYLE =
        new PatchStyle().setColor(Value.of(NODE_FILL));

    // { nodeId, name, type, description line 1, description line 2, icon filename }
    private static final String[][] WARRIOR_NODES = {
        { "0", "Warrior",            "Passive", "Foundation of all combat arts.",       "All skills available to the Warrior class.", "AbilityIconSword_40.png" },
        { "6", "Weapon Training",    "Passive", "Increases base weapon damage.",         "Unlocks advanced weapon techniques.",        "Weapon_Training_Icon.png" },
        { "7", "Defense Training",   "Passive", "Increases armor effectiveness.",        "Reduces incoming physical damage.",          "Defense_Training_Icon.png" },
        { "1", "Precision Training", "Passive", "Improves critical strike chance.",      "Boosts accuracy against moving targets.",    "Precision_Training_Icon.png" },
        { "2", "Vigor Training",     "Passive", "Increases maximum health.",             "Improves health regeneration rate.",         "Vigor_Training_Icon.png" },
        { "9", "Warcry",             "Active",  "Lets out a powerful battle cry.",       "Boosts nearby allies' attack speed.",        "Warcry_Icon.png" },
        { "8", "Heavy Swing",        "Active",  "A wide sweeping blow.",                 "Deals damage to all enemies in an arc.",     "Heavy_Swing_Icon.png" },
        { "5", "Brutal Charge",      "Active",  "Charge forward, knocking back foes.",   "Deals bonus damage on first hit.",           "Brutal_Charge_Icon.png" },
        { "4", "Warrior Oath",       "Passive", "Sworn to protect allies in battle.",    "Grants a damage absorption shield.",         "Warrior_Oath_Icon.png" },
        { "3", "Guarded Strike",     "Active",  "Attack while maintaining guard stance.", "Blocks the next incoming hit.",             "Guarded_Strike_Icon.png" },
    };

    private static final String[] STATIC_EDGES = {
        "#SkillTreeEdgeRootStem",
        "#SkillTreeEdgeRootBranch",
        "#SkillTreeEdgeRootToNode6",
        "#SkillTreeEdgeRootToNode7",
        "#SkillTreeEdgeRootToNode1",
        "#SkillTreeEdgeRootToNode2",
        "#SkillTreeEdgeNode6ToNode9",
        "#SkillTreeEdgeNode2ToNode8",
    };

    private final PlayerRef playerRef;
    private String activeTab = "skills";
    private int selectedNode = 0;

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
            populateWarriorSkillTree(uiBuilder, eventBuilder);
        }
    }

    private void populateWarriorSkillTree(@Nonnull UICommandBuilder uiBuilder,
                                          @Nonnull UIEventBuilder eventBuilder) {
        uiBuilder.set("#SkillTreePointsValue.TextSpans", Message.raw("Classe : Warrior  |  Points : 0"));

        for (String edgeId : STATIC_EDGES) {
            uiBuilder.set(edgeId + ".Visible", true);
        }
        layoutWarriorTier4Edges(uiBuilder);

        String selectedId = WARRIOR_NODES[selectedNode][0];

        for (String[] node : WARRIOR_NODES) {
            String id = node[0];
            String iconPath = ICON_BASE + node[5];
            PatchStyle iconStyle = new PatchStyle().setTexturePath(Value.of(iconPath));
            PatchStyle borderStyle = new PatchStyle().setColor(Value.of(
                id.equals(selectedId) ? NODE_BORDER_SELECTED : NODE_BORDER));

            uiBuilder.set("#SkillTreeNode" + id + "Slot.Visible", true);
            uiBuilder.setObject("#SkillTreeNode" + id + "Slot.Background", borderStyle);

            uiBuilder.set("#SkillTreeNode" + id + "Unlocked.Visible", true);
            uiBuilder.setObject("#SkillTreeNode" + id + "Unlocked.Background", NODE_FILL_STYLE);

            uiBuilder.set("#SkillTreeNode" + id + "Icon.Visible", true);
            uiBuilder.setObject("#SkillTreeNode" + id + "Icon.Background", iconStyle);

            uiBuilder.set("#SkillTreeNode" + id + ".Visible", true);
            eventBuilder.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#SkillTreeNode" + id,
                EventData.of("Action", "skill").append("Node", id),
                false
            );
        }

        String[] sel = WARRIOR_NODES[selectedNode];
        uiBuilder.set("#SkillTreeSelectedTitle.TextSpans", Message.raw(sel[1]));
        uiBuilder.set("#SkillTreeSelectedStatus.TextSpans", Message.raw(sel[2]));
        uiBuilder.set("#SkillTreeSelectedDescriptionLine0.Visible", true);
        uiBuilder.set("#SkillTreeSelectedDescriptionLine0.TextSpans", Message.raw(sel[3]));
        uiBuilder.set("#SkillTreeSelectedDescriptionLine1.Visible", true);
        uiBuilder.set("#SkillTreeSelectedDescriptionLine1.TextSpans", Message.raw(sel[4]));
    }

    private static void layoutWarriorTier4Edges(@Nonnull UICommandBuilder uiBuilder) {
        final int rail = 4;
        final int yMidTier3 = 406;
        final int tier3Bottom = 264 + 76;
        final int tier2Bottom = 154 + 76;
        final int bottomRowSlotTop = 474;
        final int node4SlotTop = 544;

        final int cx9 = 114 + 38;
        final int cx5 = 184 + 38;
        final int cx8 = 594 + 38;
        final int cx3 = 524 + 38;
        final int cx7 = 274 + 38;
        final int cx1 = 434 + 38;
        final int cx4 = 354 + 38;

        final int bx9 = cx9 - 2;
        final int bx5 = cx5 - 2;
        final int bx8 = cx8 - 2;
        final int bx3 = cx3 - 2;
        final int bx7 = cx7 - 2;
        final int bx1 = cx1 - 2;
        final int bx4 = cx4 - 2;

        showEdge(uiBuilder, "#SkillTreeEdgeSeg0", bx9, tier3Bottom, rail, yMidTier3 - tier3Bottom);
        showEdge(uiBuilder, "#SkillTreeEdgeSeg1", Math.min(bx9, bx5), yMidTier3 - 2,
            Math.abs(cx5 - cx9) + rail, rail);
        showEdge(uiBuilder, "#SkillTreeEdgeSeg2", bx5, yMidTier3 + 2, rail,
            bottomRowSlotTop - (yMidTier3 + 2));

        showEdge(uiBuilder, "#SkillTreeEdgeSeg3", bx8, tier3Bottom, rail, yMidTier3 - tier3Bottom);
        showEdge(uiBuilder, "#SkillTreeEdgeSeg4", Math.min(bx8, bx3), yMidTier3 - 2,
            Math.abs(cx8 - cx3) + rail, rail);
        showEdge(uiBuilder, "#SkillTreeEdgeSeg5", bx3, yMidTier3 + 2, rail,
            bottomRowSlotTop - (yMidTier3 + 2));

        final int hubY = Math.min(Math.min(tier3Bottom + 120, bottomRowSlotTop - 140), tier2Bottom + 160);
        final int connectorTop = hubY - 2;
        final int trunkTop = hubY + 2;

        showEdge(uiBuilder, "#SkillTreeEdgeSeg6", bx7, tier2Bottom, rail, connectorTop - tier2Bottom);
        showEdge(uiBuilder, "#SkillTreeEdgeSeg7", bx1, tier2Bottom, rail, connectorTop - tier2Bottom);

        final int barLeft = Math.min(bx7, bx1);
        final int barRight = Math.max(bx7, bx1) + rail;
        showEdge(uiBuilder, "#SkillTreeEdgeSeg8", barLeft, hubY - 2, barRight - barLeft, rail);

        showEdge(uiBuilder, "#SkillTreeEdgeSeg9", bx4, trunkTop, rail, node4SlotTop - trunkTop);
    }

    private static void showEdge(@Nonnull UICommandBuilder uiBuilder,
                                 @Nonnull String elementId,
                                 int left,
                                 int top,
                                 int width,
                                 int height) {
        if (width <= 0 || height <= 0) return;
        Anchor a = new Anchor();
        a.setLeft(Value.of(left));
        a.setTop(Value.of(top));
        a.setWidth(Value.of(width));
        a.setHeight(Value.of(height));
        uiBuilder.setObject(elementId + ".Anchor", a);
        uiBuilder.set(elementId + ".Visible", true);
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
            for (int i = 0; i < WARRIOR_NODES.length; i++) {
                if (WARRIOR_NODES[i][0].equals(data.node)) {
                    selectedNode = i;
                    break;
                }
            }
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
