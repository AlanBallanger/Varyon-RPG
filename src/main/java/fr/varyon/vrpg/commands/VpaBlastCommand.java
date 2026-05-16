package fr.varyon.vrpg.commands;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.CommandSender;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractAsyncCommand;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.TargetUtil;
import fr.varyon.vrpg.VaryonRpgPlugin;
import fr.varyon.vrpg.profession.mineur.ExplosionTalentSystem;
import fr.varyon.vrpg.rpg.PlayerAccount;
import fr.varyon.vrpg.rpg.Profession;
import fr.varyon.vrpg.rpg.ProfessionManager;
import org.checkerframework.checker.nullness.compatqual.NonNullDecl;

import java.awt.Color;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public final class VpaBlastCommand extends AbstractAsyncCommand {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final long COOLDOWN_MS = 5 * 60 * 1000L;

    private final ExplosionTalentSystem explosionSystem;
    private final Map<UUID, Long> lastUse = new ConcurrentHashMap<>();

    public VpaBlastCommand(ExplosionTalentSystem explosionSystem) {
        super("blast", "Diplomatie Minière — explosion contrôlée");
        this.addAliases("vpb");
        this.explosionSystem = explosionSystem;
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
        if (mgr == null) { LOGGER.atWarning().log("[Blast] mgr null"); return CompletableFuture.completedFuture(null); }

        PlayerRef playerRef = player.getPlayerRef();
        if (playerRef == null) { LOGGER.atWarning().log("[Blast] playerRef null"); return CompletableFuture.completedFuture(null); }
        UUID uuid = playerRef.getUuid();

        PlayerAccount acc = mgr.getAccount(uuid);
        if (acc == null || !acc.isActive(Profession.MINEUR)) {
            sender.sendMessage(Message.raw("Vous devez être Mineur actif pour utiliser Diplomatie Minière.").color(Color.RED));
            return CompletableFuture.completedFuture(null);
        }

        if (acc.getTalentRank(Profession.MINEUR, "15") <= 0) {
            sender.sendMessage(Message.raw("Talent non débloqué.").color(Color.RED));
            return CompletableFuture.completedFuture(null);
        }

        boolean isOp = player.hasPermission("*");
        long now = System.currentTimeMillis();
        if (!isOp) {
            Long lastUsed = lastUse.get(uuid);
            if (lastUsed != null) {
                long remaining = COOLDOWN_MS - (now - lastUsed);
                if (remaining > 0) {
                    long minutes = remaining / 60_000;
                    long seconds = (remaining % 60_000) / 1_000;
                    sender.sendMessage(Message.raw(
                        "Diplomatie Minière en rechargement — disponible dans " + minutes + "m " + seconds + "s.")
                        .color(new Color(255, 165, 0)));
                    return CompletableFuture.completedFuture(null);
                }
            }
        }

        World world = player.getWorld();
        if (world == null) {
            LOGGER.atWarning().log("[Blast] world null");
            sender.sendMessage(Message.raw("Monde invalide.").color(Color.RED));
            return CompletableFuture.completedFuture(null);
        }

        CompletableFuture<Void> done = new CompletableFuture<>();
        world.execute(() -> {
            try {
                Ref<EntityStore> ref = playerRef.getReference();
                if (ref == null || !ref.isValid()) {
                    LOGGER.atWarning().log("[Blast] ref null/invalid");
                    sender.sendMessage(Message.raw("Erreur lors de l'explosion.").color(Color.RED));
                    done.complete(null);
                    return;
                }
                Store<EntityStore> store = ref.getStore();

                Vector3i targetBlock = TargetUtil.getTargetBlock(ref, 10.0, store);
                if (targetBlock == null) {
                    sender.sendMessage(Message.raw("Aucune cible — visez un bloc.").color(Color.RED));
                    done.complete(null);
                    return;
                }
                Vector3d pos = new Vector3d(targetBlock.x + 0.5, targetBlock.y + 0.5, targetBlock.z + 0.5);
                lastUse.put(uuid, now);
                explosionSystem.queueExplosion(uuid, pos);
                sender.sendMessage(Message.raw("Diplomatie Minière !").color(new Color(255, 80, 0)));
                done.complete(null);
            } catch (Exception e) {
                LOGGER.atSevere().withCause(e).log("[Blast] erreur explosion");
                sender.sendMessage(Message.raw("Erreur lors de l'explosion.").color(Color.RED));
                done.complete(null);
            }
        });
        return done;
    }

    public void clearCooldown(UUID uuid) {
        lastUse.remove(uuid);
    }
}
