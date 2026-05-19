package fr.varyon.vrpg.ui;

import com.hypixel.hytale.server.core.entity.entities.player.hud.CustomUIHud;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.ui.ItemGridSlot;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public final class XpNotifHud extends CustomUIHud {

    private static final int MAX_SLOTS = 4;
    private static final long SLOT_DURATION_MS = 4000L;

    private final long[] slotExpiresAt = new long[MAX_SLOTS];
    private final ScheduledExecutorService scheduler;

    public XpNotifHud(@Nonnull PlayerRef playerRef, @Nonnull ScheduledExecutorService scheduler) {
        super(playerRef);
        this.scheduler = scheduler;
    }

    @Override
    protected void build(@Nonnull UICommandBuilder builder) {
        builder.append("Hud/VRpgXpNotif.ui");
    }

    public void showBurst(@Nonnull String xpText, @Nonnull String iconItemId) {
        int slot = findFreeSlot();
        long expiresAt = System.currentTimeMillis() + SLOT_DURATION_MS;
        slotExpiresAt[slot] = expiresAt;

        UICommandBuilder cmd = new UICommandBuilder();
        cmd.set("#VRpgXp" + slot + ".Visible", true);
        cmd.set("#VRpgXpAmount" + slot + ".Text", xpText);
        cmd.set("#VRpgXpIcon" + slot + ".Slots", List.of(new ItemGridSlot(new ItemStack(iconItemId, 1))));
        this.update(false, cmd);

        final int finalSlot = slot;
        final long finalExpires = expiresAt;
        scheduler.schedule(() -> {
            if (slotExpiresAt[finalSlot] == finalExpires) {
                clearSlot(finalSlot);
            }
        }, SLOT_DURATION_MS, TimeUnit.MILLISECONDS);
    }

    private void clearSlot(int slot) {
        slotExpiresAt[slot] = 0L;
        UICommandBuilder cmd = new UICommandBuilder();
        cmd.set("#VRpgXp" + slot + ".Visible", false);
        this.update(false, cmd);
    }

    private int findFreeSlot() {
        long now = System.currentTimeMillis();
        long oldestTime = Long.MAX_VALUE;
        int oldestSlot = 0;
        for (int i = 0; i < MAX_SLOTS; i++) {
            if (slotExpiresAt[i] <= now) return i;
            if (slotExpiresAt[i] < oldestTime) {
                oldestTime = slotExpiresAt[i];
                oldestSlot = i;
            }
        }
        return oldestSlot;
    }
}
