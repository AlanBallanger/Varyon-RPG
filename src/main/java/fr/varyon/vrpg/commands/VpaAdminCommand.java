package fr.varyon.vrpg.commands;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.CommandSender;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractAsyncCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import fr.varyon.vrpg.VaryonRpgPlugin;
import fr.varyon.vrpg.rpg.PlayerAccount;
import fr.varyon.vrpg.rpg.Profession;
import fr.varyon.vrpg.rpg.ProfessionManager;
import fr.varyon.vrpg.rpg.ProfessionProgress;
import org.checkerframework.checker.nullness.compatqual.NonNullDecl;

import java.awt.Color;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class VpaAdminCommand extends AbstractAsyncCommand {

    public VpaAdminCommand() {
        super("vpa", "Varyon RPG — administration");
        this.addAliases("rpgadmin");

        this.addSubCommand(new SetLevelSub());
        this.addSubCommand(new AddXpSub());
        this.addSubCommand(new ResetSub());
        this.addSubCommand(new SaveSub());
    }

    @NonNullDecl
    @Override
    protected CompletableFuture<Void> executeAsync(CommandContext ctx) {
        ctx.sendMessage(Message.raw("=== Varyon RPG Admin ===").color(new Color(255, 215, 0)));
        ctx.sendMessage(Message.raw("  /vpa setlevel <player> <profession> <level>").color(Color.GRAY));
        ctx.sendMessage(Message.raw("  /vpa addxp <player> <profession> <amount>").color(Color.GRAY));
        ctx.sendMessage(Message.raw("  /vpa reset <player> <profession|all>").color(Color.GRAY));
        ctx.sendMessage(Message.raw("  /vpa save").color(Color.GRAY));
        return CompletableFuture.completedFuture(null);
    }

    private static ProfessionManager mgr() {
        return VaryonRpgPlugin.getInstance().getProfessionManager();
    }

    private static UUID resolveTarget(CommandSender sender, String rawName) {
        String name = rawName.trim();
        String lower = name.toLowerCase(Locale.ROOT);
        for (PlayerRef pr : Universe.get().getPlayers()) {
            if (pr.getUsername().equalsIgnoreCase(name)) return pr.getUuid();
        }
        for (PlayerRef pr : Universe.get().getPlayers()) {
            if (pr.getUsername().toLowerCase(Locale.ROOT).startsWith(lower)) return pr.getUuid();
        }
        ProfessionManager m = mgr();
        if (m == null) return null;
        List<UUID> dbHits = m.getStorage().findUuidsByName(name).join();
        if (dbHits.size() == 1) return dbHits.get(0);
        if (dbHits.size() > 1) {
            sender.sendMessage(Message.raw("Plusieurs comptes correspondent à \"" + name + "\".").color(Color.RED));
            return null;
        }
        sender.sendMessage(Message.raw("Joueur introuvable (en ligne ou en BDD) : " + name).color(Color.RED));
        return null;
    }

    private static Profession resolveProfession(CommandSender sender, String id) {
        Profession p = Profession.fromId(id.toLowerCase(Locale.ROOT));
        if (p == null) {
            sender.sendMessage(Message.raw("Métier inconnu : " + id +
                ". Valides : mineur, fermier, forestier, chasseur, forgeron, alchimiste, architecte, cuisinier.")
                .color(Color.RED));
        }
        return p;
    }

    private static class SetLevelSub extends AbstractAsyncCommand {
        private final RequiredArg<String> playerArg;
        private final RequiredArg<String> professionArg;
        private final RequiredArg<String> levelArg;

        SetLevelSub() {
            super("setlevel", "Set a player's level for a profession");
            this.playerArg = withRequiredArg("player", "Player name", ArgTypes.STRING);
            this.professionArg = withRequiredArg("profession", "Profession id", ArgTypes.STRING);
            this.levelArg = withRequiredArg("level", "Level (1-30)", ArgTypes.STRING);
        }

        @NonNullDecl
        @Override
        protected CompletableFuture<Void> executeAsync(CommandContext ctx) {
            CommandSender sender = ctx.sender();
            String playerName = playerArg.get(ctx);
            String professionId = professionArg.get(ctx);
            String levelStr = levelArg.get(ctx);
            int level;
            try {
                level = Integer.parseInt(levelStr);
            } catch (NumberFormatException e) {
                sender.sendMessage(Message.raw("Niveau invalide : " + levelStr).color(Color.RED));
                return CompletableFuture.completedFuture(null);
            }

            return CompletableFuture.runAsync(() -> {
                ProfessionManager m = mgr();
                if (m == null) return;
                UUID target = resolveTarget(sender, playerName);
                if (target == null) return;
                Profession p = resolveProfession(sender, professionId);
                if (p == null) return;

                m.ensureAccount(target, null);
                m.setLevel(target, p, level);
                sender.sendMessage(Message.join(
                    Message.raw("Set ").color(Color.GREEN),
                    Message.raw(p.getDisplayName()).color(Color.WHITE),
                    Message.raw(" -> niveau ").color(Color.GRAY),
                    Message.raw(String.valueOf(level)).color(new Color(50, 205, 50)),
                    Message.raw(" pour ").color(Color.GRAY),
                    Message.raw(playerName).color(Color.WHITE)
                ));
            });
        }
    }

    private static class AddXpSub extends AbstractAsyncCommand {
        private final RequiredArg<String> playerArg;
        private final RequiredArg<String> professionArg;
        private final RequiredArg<String> amountArg;

        AddXpSub() {
            super("addxp", "Add XP to a profession for a player");
            this.playerArg = withRequiredArg("player", "Player name", ArgTypes.STRING);
            this.professionArg = withRequiredArg("profession", "Profession id", ArgTypes.STRING);
            this.amountArg = withRequiredArg("amount", "XP amount", ArgTypes.STRING);
        }

        @NonNullDecl
        @Override
        protected CompletableFuture<Void> executeAsync(CommandContext ctx) {
            CommandSender sender = ctx.sender();
            String playerName = playerArg.get(ctx);
            String professionId = professionArg.get(ctx);
            String amountStr = amountArg.get(ctx);
            long amount;
            try {
                amount = Long.parseLong(amountStr);
            } catch (NumberFormatException e) {
                sender.sendMessage(Message.raw("Quantité invalide : " + amountStr).color(Color.RED));
                return CompletableFuture.completedFuture(null);
            }

            return CompletableFuture.runAsync(() -> {
                ProfessionManager m = mgr();
                if (m == null) return;
                UUID target = resolveTarget(sender, playerName);
                if (target == null) return;
                Profession p = resolveProfession(sender, professionId);
                if (p == null) return;

                m.ensureAccount(target, null);
                int lvUp = m.addXp(target, p, amount);
                PlayerAccount acc = m.getAccount(target);
                ProfessionProgress prog = acc == null ? null : acc.getProgress(p);
                int lvl = prog == null ? -1 : prog.getLevel();
                long xp = prog == null ? -1L : prog.getXpInLevel();
                sender.sendMessage(Message.join(
                    Message.raw("+").color(new Color(50, 205, 50)),
                    Message.raw(String.valueOf(amount)).color(Color.WHITE),
                    Message.raw(" XP ").color(Color.GRAY),
                    Message.raw(p.getDisplayName()).color(Color.WHITE),
                    Message.raw(" pour ").color(Color.GRAY),
                    Message.raw(playerName).color(Color.WHITE),
                    Message.raw(" (Nv " + lvl + ", " + xp + " XP)").color(Color.GRAY),
                    Message.raw(lvUp > 0 ? "  [+" + lvUp + " niveau(x)]" : "").color(new Color(255, 215, 0))
                ));
            });
        }
    }

    private static class ResetSub extends AbstractAsyncCommand {
        private final RequiredArg<String> playerArg;
        private final RequiredArg<String> professionArg;

        ResetSub() {
            super("reset", "Reset a profession (or all) for a player");
            this.playerArg = withRequiredArg("player", "Player name", ArgTypes.STRING);
            this.professionArg = withRequiredArg("profession", "Profession id or 'all'", ArgTypes.STRING);
        }

        @NonNullDecl
        @Override
        protected CompletableFuture<Void> executeAsync(CommandContext ctx) {
            CommandSender sender = ctx.sender();
            String playerName = playerArg.get(ctx);
            String selector = professionArg.get(ctx);

            return CompletableFuture.runAsync(() -> {
                ProfessionManager m = mgr();
                if (m == null) return;
                UUID target = resolveTarget(sender, playerName);
                if (target == null) return;

                m.ensureAccount(target, null);
                if (selector == null || selector.equalsIgnoreCase("all")) {
                    m.resetAccount(target);
                    sender.sendMessage(Message.raw("Compte RPG de " + playerName + " entièrement réinitialisé.")
                        .color(new Color(50, 205, 50)));
                    return;
                }
                Profession p = resolveProfession(sender, selector);
                if (p == null) return;
                m.resetProfession(target, p);
                sender.sendMessage(Message.raw(p.getDisplayName() + " réinitialisé pour " + playerName + ".")
                    .color(new Color(50, 205, 50)));
            });
        }
    }

    private static class SaveSub extends AbstractAsyncCommand {
        SaveSub() {
            super("save", "Force save de toutes les données RPG");
        }

        @NonNullDecl
        @Override
        protected CompletableFuture<Void> executeAsync(CommandContext ctx) {
            ProfessionManager m = mgr();
            if (m == null) {
                ctx.sendMessage(Message.raw("ProfessionManager indisponible.").color(Color.RED));
                return CompletableFuture.completedFuture(null);
            }
            return CompletableFuture.runAsync(() -> {
                m.forceSave();
                ctx.sendMessage(Message.raw("✓ Données RPG sauvegardées.").color(new Color(50, 205, 50)));
            });
        }
    }
}
