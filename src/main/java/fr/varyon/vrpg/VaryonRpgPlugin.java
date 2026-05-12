package fr.varyon.vrpg;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import fr.varyon.vrpg.commands.VpaCommand;

import javax.annotation.Nonnull;

public final class VaryonRpgPlugin extends JavaPlugin {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static volatile VaryonRpgPlugin instance;

    public VaryonRpgPlugin(@Nonnull JavaPluginInit init) {
        super(init);
    }

    public static VaryonRpgPlugin getInstance() {
        return instance;
    }

    @Override
    protected void setup() {
        try {
            getCommandRegistry().registerCommand(new VpaCommand(this));
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("[VaryonRPG] enregistrement commandes");
        }
    }

    @Override
    protected void start() {
        instance = this;
        LOGGER.atInfo().log("[VaryonRPG] démarré v1.0.0");
    }

    @Override
    protected void shutdown() {
        instance = null;
    }
}
