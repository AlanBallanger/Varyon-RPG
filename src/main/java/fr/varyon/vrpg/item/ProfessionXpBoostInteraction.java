package fr.varyon.vrpg.item;

import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.InteractionState;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInstantInteraction;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import fr.varyon.vrpg.VaryonRpgPlugin;
import fr.varyon.vrpg.rpg.Profession;
import fr.varyon.vrpg.rpg.ProfessionManager;

import javax.annotation.Nonnull;
import java.util.HashMap;
import java.util.Map;

public final class ProfessionXpBoostInteraction extends SimpleInstantInteraction {

    public static final String TYPE_NAME = "vrpg_xp_boost_potion";

    private record BoostDef(@Nonnull Profession profession, int tier, double bonus, long durationMs) {}

    private static final long DURATION_LONG  = 30L * 60L * 1000L;
    private static final long DURATION_SHORT = 10L * 60L * 1000L;

    private static final Map<String, BoostDef> BOOSTS = buildBoosts();

    public static final BuilderCodec<ProfessionXpBoostInteraction> CODEC =
        BuilderCodec.builder(
            ProfessionXpBoostInteraction.class,
            ProfessionXpBoostInteraction::new,
            SimpleInstantInteraction.CODEC
        ).build();

    private static Map<String, BoostDef> buildBoosts() {
        Map<String, BoostDef> map = new HashMap<>();
        registerTiers(map, "Varyon_Miner_Xp_Boost",     Profession.MINEUR,    DURATION_LONG);
        registerTiers(map, "Varyon_Chasseur_Xp_Boost",  Profession.CHASSEUR,  DURATION_LONG);
        registerTiers(map, "Varyon_Fermier_Xp_Boost",   Profession.FERMIER,   DURATION_SHORT);
        registerTiers(map, "Varyon_Forestier_Xp_Boost", Profession.FORESTIER, DURATION_SHORT);
        return Map.copyOf(map);
    }

    private static void registerTiers(Map<String, BoostDef> map, String prefix, Profession profession, long durationMs) {
        map.put(prefix + "_Lesser",   new BoostDef(profession, 1, 0.25, durationMs));
        map.put(prefix + "_Medium",   new BoostDef(profession, 2, 0.50, durationMs));
        map.put(prefix + "_Large",    new BoostDef(profession, 3, 1.00, durationMs));
        map.put(prefix + "_Gigantic", new BoostDef(profession, 4, 2.00, durationMs));
    }

    @Override
    protected void simulateFirstRun(
            @Nonnull InteractionType interactionType,
            @Nonnull InteractionContext interactionContext,
            @Nonnull CooldownHandler cooldownHandler) {
        interactionContext.getState().state = InteractionState.Finished;
    }

    @Override
    protected void firstRun(
            @Nonnull InteractionType interactionType,
            @Nonnull InteractionContext interactionContext,
            @Nonnull CooldownHandler cooldownHandler) {

        CommandBuffer<EntityStore> commandBuffer = interactionContext.getCommandBuffer();
        if (commandBuffer == null) {
            interactionContext.getState().state = InteractionState.Failed;
            return;
        }

        Ref<EntityStore> ref = interactionContext.getEntity();
        Player player = commandBuffer.getComponent(ref, Player.getComponentType());
        if (player == null) {
            interactionContext.getState().state = InteractionState.Failed;
            return;
        }

        Store<EntityStore> store = commandBuffer.getExternalData().getStore();
        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        if (playerRef == null || !playerRef.isValid()) {
            interactionContext.getState().state = InteractionState.Failed;
            return;
        }

        ItemStack held = interactionContext.getHeldItem();
        if (held == null) {
            interactionContext.getState().state = InteractionState.Failed;
            return;
        }

        BoostDef def = BOOSTS.get(held.getItemId());
        if (def == null) {
            interactionContext.getState().state = InteractionState.Failed;
            return;
        }

        VaryonRpgPlugin plugin = VaryonRpgPlugin.getInstance();
        ProfessionManager manager = plugin == null ? null : plugin.getProfessionManager();
        if (manager == null) {
            interactionContext.getState().state = InteractionState.Failed;
            return;
        }

        manager.ensureAccount(playerRef.getUuid(), playerRef.getUsername());
        manager.applyXpBoost(playerRef.getUuid(), def.profession(), def.tier(), def.bonus(), def.durationMs());
        interactionContext.getState().state = InteractionState.Finished;
    }
}
