package fr.varyon.vrpg.commands;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.server.core.command.system.CommandSender;
import com.hypixel.hytale.server.core.command.system.ParseResult;
import com.hypixel.hytale.server.core.command.system.ParserContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractCommandCollection;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import fr.varyon.vrpg.VaryonRpgPlugin;
import fr.varyon.vrpg.ui.RpgMainUI;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.concurrent.CompletableFuture;

public final class VpaCommand extends AbstractCommandCollection {

    @SuppressWarnings("unused")
    public VpaCommand(@Nonnull VaryonRpgPlugin plugin) {
        super("vpp", "Varyon RPG — Panneau principal");
        setPermissionGroup(GameMode.Creative);
    }

    @Override
    @Nullable
    public CompletableFuture<Void> acceptCall(@Nonnull CommandSender sender,
                                              @Nonnull ParserContext parserContext,
                                              @Nonnull ParseResult parseResult) {
        if (sender instanceof Player player && hasPermission(sender)) {
            openPanel(player);
            return CompletableFuture.completedFuture(null);
        }
        return super.acceptCall(sender, parserContext, parseResult);
    }

    public static void openPanel(@Nonnull Player player) {
        PlayerRef playerRef = player.getPlayerRef();
        if (playerRef == null) {
            return;
        }
        Ref<EntityStore> ref = playerRef.getReference();
        Store<EntityStore> store = ref.getStore();
        player.getPageManager().openCustomPage(ref, store, new RpgMainUI(playerRef));
    }
}
