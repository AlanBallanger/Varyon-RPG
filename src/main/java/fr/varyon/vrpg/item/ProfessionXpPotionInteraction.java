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

public final class ProfessionXpPotionInteraction extends SimpleInstantInteraction {

    public static final String TYPE_NAME = "vrpg_miner_xp_potion";

    private record Reward(@Nonnull Profession profession, double xp) {}

    private static final Map<String, Reward> REWARDS = buildRewards();

    public static final BuilderCodec<ProfessionXpPotionInteraction> CODEC =
        BuilderCodec.builder(
            ProfessionXpPotionInteraction.class,
            ProfessionXpPotionInteraction::new,
            SimpleInstantInteraction.CODEC
        ).build();

    private static Map<String, Reward> buildRewards() {
        Map<String, Reward> map = new HashMap<>();
        registerTier(map, "Varyon_Miner_Xp_Potion", Profession.MINEUR);
        registerTier(map, "Varyon_Fermier_Xp_Potion", Profession.FERMIER);
        registerTier(map, "Varyon_Forestier_Xp_Potion", Profession.FORESTIER);
        registerTier(map, "Varyon_Chasseur_Xp_Potion", Profession.CHASSEUR);
        return Map.copyOf(map);
    }

    private static void registerTier(Map<String, Reward> map, String prefix, Profession profession) {
        map.put(prefix + "_Lesser", new Reward(profession, 100));
        map.put(prefix + "_Medium", new Reward(profession, 500));
        map.put(prefix + "_Large", new Reward(profession, 2500));
        map.put(prefix + "_Gigantic", new Reward(profession, 10000));
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

        Reward reward = REWARDS.get(held.getItemId());
        if (reward == null) {
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
        manager.addXp(playerRef.getUuid(), reward.profession(), reward.xp(), playerRef);
        interactionContext.getState().state = InteractionState.Finished;
    }
}
