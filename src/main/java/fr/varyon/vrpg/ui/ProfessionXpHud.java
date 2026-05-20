package fr.varyon.vrpg.ui;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.hud.CustomUIHud;
import com.hypixel.hytale.server.core.ui.PatchStyle;
import com.hypixel.hytale.server.core.ui.Value;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import fr.varyon.vrpg.VaryonRpgPlugin;
import fr.varyon.vrpg.rpg.PlayerAccount;
import fr.varyon.vrpg.rpg.Profession;
import fr.varyon.vrpg.rpg.ProfessionProgress;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ProfessionXpHud extends CustomUIHud {

    private static final ConcurrentHashMap<UUID, ProfessionXpHud> INSTANCES = new ConcurrentHashMap<>();

    private static final PatchStyle TRANSPARENT = new PatchStyle().setColor(Value.of("#00000000"));

    private static final Map<String, String> JOB_ICON_TEXTURES = Map.ofEntries(
        Map.entry("mineur",     "Icons/Mineur.png"),
        Map.entry("fermier",    "Icons/Fermier.png"),
        Map.entry("forestier",  "Icons/Forestier.png"),
        Map.entry("chasseur",   "Icons/Chasseur.png"),
        Map.entry("forgeron",   "Icons/Forgeron.png"),
        Map.entry("alchimiste", "Icons/Alchimiste.png"),
        Map.entry("artisan",    "Icons/Architecte.png"),
        Map.entry("cuisinier",  "Icons/Cuisinier.png")
    );

    private boolean built = false;
    private boolean hidden = false;
    @Nullable private String lastJob1Id;
    @Nullable private String lastJob2Id;

    public ProfessionXpHud(@Nonnull PlayerRef playerRef) {
        super(playerRef);
    }

    @Nonnull
    public static ProfessionXpHud getOrCreate(@Nonnull Player player, @Nonnull PlayerRef playerRef) {
        UUID uuid = playerRef.getUuid();
        ProfessionXpHud existing = INSTANCES.get(uuid);
        if (existing != null) return existing;

        ProfessionXpHud hud = new ProfessionXpHud(playerRef);
        ProfessionXpHud race = INSTANCES.putIfAbsent(uuid, hud);
        if (race != null) return race;

        player.getHudManager().setCustomHud(playerRef, hud);
        return hud;
    }

    @Nullable
    public static ProfessionXpHud get(@Nonnull UUID uuid) {
        return INSTANCES.get(uuid);
    }

    public static void refreshIfPresent(@Nonnull UUID uuid) {
        ProfessionXpHud hud = INSTANCES.get(uuid);
        if (hud != null) hud.refresh();
    }

    public static void cleanup(@Nonnull UUID uuid) {
        INSTANCES.remove(uuid);
    }

    @Override
    protected void build(@Nonnull UICommandBuilder builder) {
        lastJob1Id = null;
        lastJob2Id = null;
        builder.append("VRpgProfessionXp.ui");
        built = true;
        applyBars(builder);
    }

    public void refresh() {
        if (!built) return;
        UICommandBuilder builder = new UICommandBuilder();
        applyBars(builder);
        update(false, builder);
    }

    public void setHidden(boolean hidden) {
        if (this.hidden == hidden) return;
        this.hidden = hidden;
        if (!built) return;
        UICommandBuilder builder = new UICommandBuilder();
        applyBars(builder);
        update(false, builder);
    }

    private void applyBars(@Nonnull UICommandBuilder builder) {
        if (hidden) {
            builder.setObject("#XPPanel.Background", TRANSPARENT);
            builder.set("#HudBorder.Visible", false);
            builder.set("#JobSeparator.Visible", false);
            hideSlot(builder, 1);
            hideSlot(builder, 2);
            return;
        }

        builder.set("#HudBorder.Visible", true);
        builder.set("#JobSeparator.Visible", true);

        VaryonRpgPlugin plugin = VaryonRpgPlugin.getInstance();
        if (plugin == null) {
            hideSlot(builder, 1);
            hideSlot(builder, 2);
            return;
        }

        PlayerAccount acc = plugin.getProfessionManager().getOrLoad(getPlayerRef().getUuid());
        Profession[] active = { acc.getActiveSlot0(), acc.getActiveSlot1() };

        for (int i = 0; i < 2; i++) {
            applySlot(builder, i + 1, active[i], acc);
        }
    }

    private void applySlot(@Nonnull UICommandBuilder builder, int slot,
                           @Nullable Profession profession, @Nonnull PlayerAccount acc) {
        if (profession == null) {
            hideSlot(builder, slot);
            if (slot == 1) lastJob1Id = null;
            else lastJob2Id = null;
            return;
        }

        builder.set("#Job" + slot + "Row.Visible", true);
        builder.set("#Job" + slot + "ProgBar.Visible", true);

        ProfessionProgress prog = acc.getProgress(profession);
        String levelText = "Nv." + prog.getLevel();
        if (acc.availableTalentPoints(profession) > 0) {
            levelText += " *";
        }

        builder.set("#Job" + slot + "Level.TextSpans", Message.raw(levelText));
        builder.set("#Job" + slot + "Name.TextSpans",
            Message.raw(profession.getDisplayName()));

        if (prog.isMaxLevel()) {
            builder.set("#Job" + slot + "XPText.TextSpans", Message.raw("MAX"));
            builder.set("#Job" + slot + "ProgBarFill.Value", 1.0);
        } else {
            builder.set("#Job" + slot + "XPText.TextSpans",
                Message.raw(prog.getXpInLevel() + "/" + prog.getXpToNextLevel()));
            double ratio = prog.getXpToNextLevel() > 0L
                ? Math.min(1.0, (double) prog.getXpInLevel() / prog.getXpToNextLevel())
                : 1.0;
            builder.set("#Job" + slot + "ProgBarFill.Value", ratio);
        }

        String jobId = profession.getId();
        String prevId = slot == 1 ? lastJob1Id : lastJob2Id;
        if (!jobId.equals(prevId)) {
            String iconPath = JOB_ICON_TEXTURES.getOrDefault(jobId, "Icons/Mineur.png");
            builder.setObject("#Job" + slot + "Icon.Background",
                new PatchStyle().setTexturePath(Value.of(iconPath)));
            if (slot == 1) lastJob1Id = jobId;
            else lastJob2Id = jobId;
        }
    }

    private void hideSlot(@Nonnull UICommandBuilder builder, int slot) {
        builder.set("#Job" + slot + "Row.Visible", false);
        builder.set("#Job" + slot + "ProgBar.Visible", false);
        builder.set("#Job" + slot + "Level.TextSpans", Message.raw(" "));
        builder.set("#Job" + slot + "Name.TextSpans", Message.raw(" "));
        builder.set("#Job" + slot + "XPText.TextSpans", Message.raw(" "));
        builder.set("#Job" + slot + "ProgBarFill.Value", 0.0);
        builder.setObject("#Job" + slot + "Icon.Background", TRANSPARENT);
    }
}
