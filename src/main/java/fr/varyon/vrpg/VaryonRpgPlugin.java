package fr.varyon.vrpg;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.entity.Entity;
import com.hypixel.hytale.server.core.event.events.player.AddPlayerToWorldEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerInteractEvent;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import fr.varyon.vrpg.commands.VpaBlastCommand;
import fr.varyon.vrpg.config.VrpgConfig;
import fr.varyon.vrpg.commands.VpaCommand;
import fr.varyon.vrpg.commands.VpaAdminCommand;
import fr.varyon.vrpg.commands.VpaSurfaceCommand;
import fr.varyon.vrpg.profession.fermier.FarmerAnimalDropSystem;
import fr.varyon.vrpg.profession.fermier.FarmerBlockBreakSystem;
import fr.varyon.vrpg.profession.fermier.FarmerComboTracker;
import fr.varyon.vrpg.profession.fermier.FarmerPlaceCropSystem;
import fr.varyon.vrpg.profession.fermier.FarmerFKeyHarvestSystem;
import fr.varyon.vrpg.profession.fermier.GuardianCropManager;
import fr.varyon.vrpg.profession.fermier.GuardianCropTickSystem;
import fr.varyon.vrpg.profession.mineur.BagCraftRestrictionSystem;
import fr.varyon.vrpg.profession.mineur.ExplosionTalentSystem;
import fr.varyon.vrpg.profession.mineur.MinerComboTracker;
import fr.varyon.vrpg.profession.mineur.VeinCooldownTracker;
import fr.varyon.vrpg.profession.mineur.GuardianOreTickSystem;
import fr.varyon.vrpg.profession.mineur.GuardianStoneManager;
import fr.varyon.vrpg.profession.mineur.MinerBlockBreakSystem;
import fr.varyon.vrpg.profession.mineur.MinerGuardianOreHardnessSystem;
import fr.varyon.vrpg.profession.mineur.MiningHelmet;
import fr.varyon.vrpg.profession.mineur.MiningHelmetTickSystem;
import fr.varyon.vrpg.rpg.PlayerAccount;
import fr.varyon.vrpg.rpg.ProfessionManager;

import javax.annotation.Nonnull;

public final class VaryonRpgPlugin extends JavaPlugin {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static volatile VaryonRpgPlugin instance;

    private ProfessionManager professionManager;
    private MiningHelmet miningHelmet;
    private GuardianStoneManager guardianManager;
    private MinerComboTracker comboTracker;
    private FarmerComboTracker farmerComboTracker;
    private FarmerAnimalDropSystem farmerAnimalDropSystem;
    private FarmerFKeyHarvestSystem farmerFKeyHarvestSystem;
    private GuardianCropManager guardianCropManager;
    private VeinCooldownTracker veinCooldownTracker;
    private VpaSurfaceCommand surfaceCommand;
    private VpaBlastCommand blastCommand;
    private ExplosionTalentSystem explosionTalentSystem;

    public VaryonRpgPlugin(@Nonnull JavaPluginInit init) {
        super(init);
    }

    public static VaryonRpgPlugin getInstance() {
        return instance;
    }

    public ProfessionManager getProfessionManager() {
        return professionManager;
    }

    public java.nio.file.Path getPluginDataDirectory() {
        return getDataDirectory();
    }

    @Override
    protected void setup() {
        instance = this;

        VrpgConfig.load(getDataDirectory());

        try {
            this.professionManager = new ProfessionManager(getDataDirectory());
            this.miningHelmet = new MiningHelmet(professionManager);
            this.guardianManager = new GuardianStoneManager();
            this.comboTracker = new MinerComboTracker();
            this.farmerComboTracker = new FarmerComboTracker();
            this.farmerAnimalDropSystem = new FarmerAnimalDropSystem(professionManager);
            this.farmerFKeyHarvestSystem = new FarmerFKeyHarvestSystem(professionManager, farmerComboTracker);
            this.guardianCropManager = new GuardianCropManager();
            this.veinCooldownTracker = new VeinCooldownTracker();
            this.explosionTalentSystem = new ExplosionTalentSystem();
            this.surfaceCommand = new VpaSurfaceCommand();
            this.blastCommand = new VpaBlastCommand(explosionTalentSystem);
        } catch (Exception e) {
            LOGGER.atSevere().withCause(e).log("[VaryonRPG] Failed to initialize ProfessionManager");
        }

        try {
            getCommandRegistry().registerCommand(new VpaCommand(this));
            getCommandRegistry().registerCommand(new VpaAdminCommand());
            getCommandRegistry().registerCommand(surfaceCommand);
            getCommandRegistry().registerCommand(blastCommand);
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
            getEventRegistry().registerGlobal(PlayerInteractEvent.class, event -> {
                ItemStack held = event.getItemInHand();
                if (held == null) return;
                String itemId = held.getItemId();
                if (itemId == null) return;
                boolean isOreBag = BagCraftRestrictionSystem.ORE_BAG_IDS.contains(itemId);
                boolean isCropBag = BagCraftRestrictionSystem.CROP_BAG_IDS.contains(itemId);
                if (!isOreBag && !isCropBag) return;
                Player player = event.getPlayer();
                if (player == null) return;
                PlayerRef ref = player.getPlayerRef();
                if (ref == null || professionManager == null) { event.setCancelled(true); return; }
                PlayerAccount acc = professionManager.getAccount(ref.getUuid());
                boolean ok;
                String msg;
                if (isOreBag) {
                    ok = acc != null && acc.isActive(fr.varyon.vrpg.rpg.Profession.MINEUR)
                        && acc.getTalentRank(fr.varyon.vrpg.rpg.Profession.MINEUR, "13") > 0;
                    msg = "Besace du Foreur — talent Mineur (nœud 13) requis.";
                } else {
                    ok = acc != null && acc.isActive(fr.varyon.vrpg.rpg.Profession.FERMIER)
                        && acc.getTalentRank(fr.varyon.vrpg.rpg.Profession.FERMIER, "16") > 0;
                    msg = "Besace du Paysan — talent Fermier (nœud 16) requis.";
                }
                if (!ok) {
                    event.setCancelled(true);
                    player.sendMessage(com.hypixel.hytale.server.core.Message.raw(msg)
                        .color(new java.awt.Color(200, 50, 50)));
                }
            });
            getEventRegistry().registerGlobal(PlayerInteractEvent.class, event -> {
                InteractionType action = event.getActionType();
                if (action != InteractionType.Secondary && action != InteractionType.Use) return;
                Entity targetEnt = event.getTargetEntity();
                if (!(targetEnt instanceof NPCEntity npc)) return;
                String role = npc.getRoleName();
                if (role == null) return;
                String roleLower = role.toLowerCase(java.util.Locale.ROOT);
                Player player = event.getPlayer();
                if (player == null) return;
                PlayerRef milkRef = player.getPlayerRef();
                if (milkRef == null || farmerAnimalDropSystem == null) return;
                farmerAnimalDropSystem.onMilkInteract(milkRef.getUuid(), roleLower);
            });
            getEventRegistry().registerGlobal(PlayerDisconnectEvent.class, event -> {
                PlayerRef ref = event.getPlayerRef();
                if (ref != null && professionManager != null) {
                    professionManager.onPlayerDisconnect(ref.getUuid());
                    if (comboTracker != null) comboTracker.remove(ref.getUuid());
                    if (farmerComboTracker != null) farmerComboTracker.remove(ref.getUuid());
                    if (farmerFKeyHarvestSystem != null) farmerFKeyHarvestSystem.removePlayer(ref.getUuid());
                    if (farmerAnimalDropSystem != null) farmerAnimalDropSystem.removePlayer(ref.getUuid());
                    if (veinCooldownTracker != null) veinCooldownTracker.remove(ref.getUuid());
                    if (miningHelmet != null) miningHelmet.removePlayer(ref.getUuid());
                    if (surfaceCommand != null) surfaceCommand.clearCooldown(ref.getUuid());
                    if (blastCommand != null) blastCommand.clearCooldown(ref.getUuid());
                }
            });
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("[VaryonRPG] enregistrement events joueur");
        }
    }

    @Override
    protected void start() {
        LOGGER.atInfo().log("[VaryonRPG] démarré v1.0.0");

        try {
            getEntityStoreRegistry().registerSystem(new MinerGuardianOreHardnessSystem());
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("[VaryonRPG] register MinerGuardianOreHardnessSystem");
        }

        try {
            getEntityStoreRegistry().registerSystem(new MinerBlockBreakSystem(professionManager, guardianManager, comboTracker, veinCooldownTracker));
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("[VaryonRPG] register MinerBlockBreakSystem");
        }

        try {
            getEntityStoreRegistry().registerSystem(new MiningHelmetTickSystem(professionManager, miningHelmet));
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("[VaryonRPG] register MiningHelmetTickSystem");
        }

        try {
            getEntityStoreRegistry().registerSystem(new GuardianOreTickSystem(guardianManager));
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("[VaryonRPG] register GuardianOreTickSystem");
        }

        try {
            getEntityStoreRegistry().registerSystem(explosionTalentSystem);
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("[VaryonRPG] register ExplosionTalentSystem");
        }

        try {
            getEntityStoreRegistry().registerSystem(new FarmerBlockBreakSystem(professionManager, farmerComboTracker, guardianCropManager));
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("[VaryonRPG] register FarmerBlockBreakSystem");
        }

        try {
            getEntityStoreRegistry().registerSystem(new GuardianCropTickSystem(guardianCropManager));
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("[VaryonRPG] register GuardianCropTickSystem");
        }

        try {
            getEntityStoreRegistry().registerSystem(farmerFKeyHarvestSystem);
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("[VaryonRPG] register FarmerFKeyHarvestSystem");
        }

        try {
            getEntityStoreRegistry().registerSystem(new FarmerPlaceCropSystem(professionManager));
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("[VaryonRPG] register FarmerPlaceCropSystem");
        }

        try {
            getEntityStoreRegistry().registerSystem(new BagCraftRestrictionSystem(professionManager));
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("[VaryonRPG] register BagCraftRestrictionSystem");
        }

        try {
            getEntityStoreRegistry().registerSystem(farmerAnimalDropSystem.new AttackTagger());
            getEntityStoreRegistry().registerSystem(farmerAnimalDropSystem.new DropOnDeath());
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("[VaryonRPG] register FarmerAnimalDropSystem");
        }
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
        if (guardianManager != null) guardianManager.clear();
        if (guardianCropManager != null) guardianCropManager.clear();
        if (veinCooldownTracker != null) veinCooldownTracker.clear();
        if (comboTracker != null) comboTracker.clear();
        if (farmerComboTracker != null) farmerComboTracker.clear();
        instance = null;
    }
}
