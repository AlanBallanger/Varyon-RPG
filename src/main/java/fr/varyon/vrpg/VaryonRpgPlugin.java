package fr.varyon.vrpg;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.player.AddPlayerToWorldEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import fr.varyon.vrpg.commands.VpaCommand;
import fr.varyon.vrpg.commands.VpaAdminCommand;
import fr.varyon.vrpg.rpg.ProfessionManager;

import javax.annotation.Nonnull;
import java.util.logging.Level;

public final class VaryonRpgPlugin extends JavaPlugin {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static volatile VaryonRpgPlugin instance;

    private ProfessionManager professionManager;

    public VaryonRpgPlugin(@Nonnull JavaPluginInit init) {
        super(init);
    }

    public static VaryonRpgPlugin getInstance() {
        return instance;
    }

    public ProfessionManager getProfessionManager() {
        return professionManager;
    }

    @Override
    protected void setup() {
        instance = this;

        try {
            this.professionManager = new ProfessionManager(getDataDirectory());
        } catch (Exception e) {
            LOGGER.atSevere().withCause(e).log("[VaryonRPG] Failed to initialize ProfessionManager");
        }

        try {
            getCommandRegistry().registerCommand(new VpaCommand(this));
            getCommandRegistry().registerCommand(new VpaAdminCommand());
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("[VaryonRPG] enregistrement commandes");
        }

        try {
            getEventRegistry().registerGlobal(AddPlayerToWorldEvent.class, event -> {
                Player player = event.getHolder().getComponent(Player.getComponentType());
                PlayerRef ref = event.getHolder().getComponent(PlayerRef.getComponentType());
                if (player == null || ref == null || professionManager == null) return;
                professionManager.ensureAccount(ref.getUuid(), ref.getUsername());
            });
            getEventRegistry().registerGlobal(PlayerDisconnectEvent.class, event -> {
                PlayerRef ref = event.getPlayerRef();
                if (ref != null && professionManager != null) {
                    professionManager.onPlayerDisconnect(ref.getUuid());
                }
            });
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("[VaryonRPG] enregistrement events joueur");
        }
    }

    @Override
    protected void start() {
        LOGGER.atInfo().log("[VaryonRPG] démarré v1.0.0");
    }

    @Override
    protected void shutdown() {
        if (professionManager != null) {
            try {
                professionManager.shutdown();
            } catch (Exception e) {
                LOGGER.atSevere().withCause(e).log("[VaryonRPG] shutdown ProfessionManager");
            }
        }
        instance = null;
    }
}
