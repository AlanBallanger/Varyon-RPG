package fr.varyon.vrpg.commands;

import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.modules.entity.teleport.Teleport;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.CommandSender;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractAsyncCommand;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.logger.HytaleLogger;
import fr.varyon.vrpg.VaryonRpgPlugin;
import fr.varyon.vrpg.rpg.PlayerAccount;
import fr.varyon.vrpg.rpg.Profession;
import fr.varyon.vrpg.rpg.ProfessionManager;
import org.checkerframework.checker.nullness.compatqual.NonNullDecl;

import java.awt.Color;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public final class VpaSurfaceCommand extends AbstractAsyncCommand {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final int MAX_SCAN_Y = 300;

    private static final long[] COOLDOWN_MS = {
        10_800_000L,
         9_000_000L,
         7_200_000L,
         5_400_000L,
         3_600_000L,
    };

    private final Map<UUID, Long> lastUse = new ConcurrentHashMap<>();

    public VpaSurfaceCommand() {
        super("surface", "Wagon Express — retour à la surface");
        this.addAliases("vps");
    }

    @NonNullDecl
    @Override
    protected CompletableFuture<Void> executeAsync(CommandContext ctx) {
        CommandSender sender = ctx.sender();
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Message.raw("Commande joueur uniquement.").color(Color.RED));
            return CompletableFuture.completedFuture(null);
        }

        ProfessionManager mgr = VaryonRpgPlugin.getInstance().getProfessionManager();
        if (mgr == null) { LOGGER.atWarning().log("[Surface] mgr null"); return CompletableFuture.completedFuture(null); }

        PlayerRef playerRef = player.getPlayerRef();
        if (playerRef == null) { LOGGER.atWarning().log("[Surface] playerRef null"); return CompletableFuture.completedFuture(null); }
        UUID uuid = playerRef.getUuid();

        PlayerAccount acc = mgr.getAccount(uuid);
        if (acc == null || !acc.isActive(Profession.MINEUR)) {
            sender.sendMessage(Message.raw("Vous devez être Mineur actif pour utiliser Wagon Express.").color(Color.RED));
            return CompletableFuture.completedFuture(null);
        }

        int rank = acc.getTalentRank(Profession.MINEUR, "11");
        if (rank <= 0) {
            sender.sendMessage(Message.raw("Talent non débloqué.").color(Color.RED));
            return CompletableFuture.completedFuture(null);
        }

        long cooldownMs = COOLDOWN_MS[Math.min(rank, COOLDOWN_MS.length) - 1];
        boolean isOp = player.hasPermission("*");
        long now = System.currentTimeMillis();
        if (!isOp) {
            Long lastUsed = lastUse.get(uuid);
            if (lastUsed != null) {
                long remaining = cooldownMs - (now - lastUsed);
                if (remaining > 0) {
                    long minutes = remaining / 60_000;
                    long seconds = (remaining % 60_000) / 1_000;
                    sender.sendMessage(Message.raw(
                        "Wagon Express en rechargement — disponible dans " + minutes + "m " + seconds + "s.")
                        .color(new Color(255, 165, 0)));
                    return CompletableFuture.completedFuture(null);
                }
            }
        }

        World world = player.getWorld();
        if (world == null) {
            LOGGER.atWarning().log("[Surface] world null");
            sender.sendMessage(Message.raw("Erreur lors de la téléportation.").color(Color.RED));
            return CompletableFuture.completedFuture(null);
        }

        CompletableFuture<Void> done = new CompletableFuture<>();
        world.execute(() -> {
            try {
                Ref<EntityStore> ref = playerRef.getReference();
                if (ref == null || !ref.isValid()) {
                    LOGGER.atWarning().log("[Surface] ref null/invalid");
                    sender.sendMessage(Message.raw("Erreur lors de la téléportation.").color(Color.RED));
                    done.complete(null);
                    return;
                }
                Store<EntityStore> store = ref.getStore();
                TransformComponent tc = store.getComponent(ref, TransformComponent.getComponentType());
                if (tc == null) {
                    LOGGER.atWarning().log("[Surface] TransformComponent null");
                    sender.sendMessage(Message.raw("Erreur lors de la téléportation.").color(Color.RED));
                    done.complete(null);
                    return;
                }

                Vector3d pos = tc.getPosition();

                int px = (int) pos.x;
                int py = (int) pos.y;
                int pz = (int) pos.z;

                int surfaceY = findSurface(world, px, py, pz);
                if (surfaceY < 0) {
                    sender.sendMessage(Message.raw("Aucune surface trouvée au-dessus de vous.").color(Color.RED));
                    done.complete(null);
                    return;
                }

                Teleport teleport = Teleport.createForPlayer(world, new Vector3d(pos.x, surfaceY, pos.z), new Vector3f(0, 0, 0));
                store.addComponent(ref, Teleport.getComponentType(), teleport);
                lastUse.put(uuid, now);
                sender.sendMessage(Message.raw("Wagon Express — arrivée en surface !").color(new Color(50, 205, 50)));
                done.complete(null);
            } catch (Exception e) {
                LOGGER.atSevere().withCause(e).log("[Surface] erreur téléportation");
                sender.sendMessage(Message.raw("Erreur lors de la téléportation.").color(Color.RED));
                done.complete(null);
            }
        });
        return done;
    }

    private int findSurface(World world, int px, int py, int pz) {
        for (int y = MAX_SCAN_Y; y > py; y--) {
            if (!isEmpty(world, px, y, pz)
                    && isEmpty(world, px, y + 1, pz)
                    && isEmpty(world, px, y + 2, pz)) {
                return y + 1;
            }
        }
        return -1;
    }

    private boolean isEmpty(World world, int x, int y, int z) {
        try {
            return "Empty".equals(world.getBlockType(x, y, z).getId());
        } catch (Exception e) {
            return true;
        }
    }

    public void clearCooldown(UUID uuid) {
        lastUse.remove(uuid);
    }
}
