package fr.varyon.vrpg.restriction;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.ecs.PlaceBlockEvent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import fr.varyon.vrpg.rpg.PlayerAccount;
import fr.varyon.vrpg.rpg.ProfessionManager;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.awt.Color;

public final class TalentItemPlaceRestrictionSystem extends EntityEventSystem<EntityStore, PlaceBlockEvent> {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private final ProfessionManager professionManager;
    private final ComponentType<EntityStore, PlayerRef> playerRefType = PlayerRef.getComponentType();

    public TalentItemPlaceRestrictionSystem(@Nonnull ProfessionManager professionManager) {
        super(PlaceBlockEvent.class);
        this.professionManager = professionManager;
    }

    @Override
    @Nullable
    public Query<EntityStore> getQuery() {
        return playerRefType;
    }

    @Override
    public void handle(int index,
            @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
            @Nonnull Store<EntityStore> store,
            @Nonnull CommandBuffer<EntityStore> commandBuffer,
            @Nonnull PlaceBlockEvent event) {

        ItemStack item = event.getItemInHand();
        if (item == null) return;
        String itemId = item.getItemId();
        if (itemId == null) return;
        if (TalentItemRestrictionSystem.getMessage(itemId) == null) return;

        PlayerRef playerRef = archetypeChunk.getComponent(index, playerRefType);
        if (playerRef == null) {
            LOGGER.atWarning().log("[PlaceRestrict] BLOCKED itemId=" + itemId + " — playerRef null");
            event.setCancelled(true);
            return;
        }

        String playerName = playerRef.getUsername() != null ? playerRef.getUsername() : playerRef.getUuid().toString().substring(0, 8);
        PlayerAccount acc = professionManager.getAccount(playerRef.getUuid());
        boolean allowed = TalentItemRestrictionSystem.isAllowed(itemId, acc);

        LOGGER.atInfo().log("[PlaceRestrict] place player=" + playerName
            + " itemId=" + itemId
            + " acc=" + (acc != null ? "ok" : "null")
            + " allowed=" + allowed);

        if (!allowed) {
            event.setCancelled(true);
            try {
                Player player = playerRef.getComponent(Player.getComponentType());
                if (player != null) {
                    player.sendMessage(Message.raw(TalentItemRestrictionSystem.getMessage(itemId, "poser"))
                        .color(new Color(200, 50, 50)));
                }
            } catch (Exception ignored) {}
        }
    }
}
