package fr.varyon.vrpg.ui;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;

public final class RpgMainUI extends InteractiveCustomUIPage<RpgMainUI.Data> {

    private final PlayerRef playerRef;
    private String activeTab = "skills";

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
                .build();

        private String action;
        private String tab;

        public Data() {}
    }
}
