package fr.varyon.vrpg;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.entity.Entity;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.event.events.player.AddPlayerToWorldEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerInteractEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerReadyEvent;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
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
import fr.varyon.vrpg.restriction.TalentItemPlaceRestrictionSystem;
import fr.varyon.vrpg.restriction.TalentItemRestrictionSystem;
import fr.varyon.vrpg.profession.fermier.FarmerAnimalDropSystem;
import fr.varyon.vrpg.profession.fermier.FarmerBlockBreakSystem;
import fr.varyon.vrpg.profession.fermier.FarmerComboTracker;
import fr.varyon.vrpg.profession.fermier.FarmerPlaceCropSystem;
import fr.varyon.vrpg.profession.fermier.FarmerPickupHarvestSystem;
import fr.varyon.vrpg.profession.fermier.GuardianCropManager;
import fr.varyon.vrpg.profession.fermier.GuardianCropTickSystem;
import fr.varyon.vrpg.profession.forestier.ForestierBlockBreakSystem;
import fr.varyon.vrpg.profession.forestier.ForestierComboTracker;
import fr.varyon.vrpg.profession.forestier.ForestierPickupSystem;
import fr.varyon.vrpg.profession.forestier.PoumonLoutreTickSystem;
import fr.varyon.vrpg.profession.forestier.RodeurSylvestreTickSystem;
import fr.varyon.vrpg.profession.forestier.RodeurSylvestreFallSystem;
import fr.varyon.vrpg.profession.chasseur.ChasseurComboTracker;
import fr.varyon.vrpg.profession.chasseur.ChasseurGuardianManager;
import fr.varyon.vrpg.profession.chasseur.ChasseurGuardianTickSystem;
import fr.varyon.vrpg.profession.chasseur.ChasseurKillSystem;
import fr.varyon.vrpg.profession.chasseur.ChasseurPickupSystem;
import fr.varyon.vrpg.profession.chasseur.RodeurDunesTickSystem;
import fr.varyon.vrpg.profession.chasseur.RodeurDunesFallSystem;
import fr.varyon.vrpg.profession.chasseur.SecondSouffleTickSystem;
import fr.varyon.vrpg.profession.chasseur.YeuxLynxTickSystem;
import fr.varyon.vrpg.profession.forestier.GuardianWoodManager;
import fr.varyon.vrpg.profession.forestier.GuardianWoodTickSystem;
import fr.varyon.vrpg.profession.forestier.LitDeFortuneTickSystem;
import fr.varyon.vrpg.profession.forestier.YeuxHibouTickSystem;
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
import fr.varyon.vrpg.item.ProfessionXpPotionInteraction;
import fr.varyon.vrpg.ui.ProfessionXpHud;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;

import javax.annotation.Nonnull;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class VaryonRpgPlugin extends JavaPlugin {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static volatile VaryonRpgPlugin instance;

    private ProfessionManager professionManager;
    private MiningHelmet miningHelmet;
    private GuardianStoneManager guardianManager;
    private MinerComboTracker comboTracker;
    private FarmerComboTracker farmerComboTracker;
    private FarmerAnimalDropSystem farmerAnimalDropSystem;
    private FarmerPickupHarvestSystem farmerPickupHarvestSystem;
    private GuardianCropManager guardianCropManager;
    private VeinCooldownTracker veinCooldownTracker;
    private VpaSurfaceCommand surfaceCommand;
    private VpaBlastCommand blastCommand;
    private ExplosionTalentSystem explosionTalentSystem;
    private ForestierComboTracker forestierComboTracker;
    private GuardianWoodManager guardianWoodManager;
    private PoumonLoutreTickSystem poumonLoutreTickSystem;
    private RodeurSylvestreTickSystem rodeurSylvestreTickSystem;
    private ChasseurComboTracker chasseurComboTracker;
    private ChasseurGuardianManager chasseurGuardianManager;
    private ChasseurKillSystem chasseurKillSystem;
    private RodeurDunesTickSystem rodeurDunesTickSystem;
    private SecondSouffleTickSystem secondSouffleTickSystem;
    private YeuxLynxTickSystem yeuxLynxTickSystem;
    private YeuxHibouTickSystem yeuxHibouTickSystem;
    private LitDeFortuneTickSystem litDeFortuneTickSystem;

    private final ConcurrentHashMap<UUID, PlayerRef> pendingProfessionHudInit = new ConcurrentHashMap<>();

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
            getCodecRegistry(Interaction.CODEC)
                .register(ProfessionXpPotionInteraction.TYPE_NAME,
                    ProfessionXpPotionInteraction.class,
                    ProfessionXpPotionInteraction.CODEC);
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("[VaryonRPG] register ProfessionXpPotionInteraction");
        }

        try {
            this.professionManager = new ProfessionManager(getDataDirectory());
            this.miningHelmet = new MiningHelmet(professionManager);
            this.guardianManager = new GuardianStoneManager();
            this.comboTracker = new MinerComboTracker();
            this.farmerComboTracker = new FarmerComboTracker();
            this.farmerAnimalDropSystem = new FarmerAnimalDropSystem(professionManager);
            this.guardianCropManager = new GuardianCropManager();
            this.farmerPickupHarvestSystem = new FarmerPickupHarvestSystem(professionManager, farmerComboTracker, guardianCropManager);
            this.veinCooldownTracker = new VeinCooldownTracker();
            this.explosionTalentSystem = new ExplosionTalentSystem();
            this.forestierComboTracker = new ForestierComboTracker();
            this.guardianWoodManager = new GuardianWoodManager();
            this.poumonLoutreTickSystem = new PoumonLoutreTickSystem(professionManager);
            this.rodeurSylvestreTickSystem = new RodeurSylvestreTickSystem(professionManager);
            this.chasseurComboTracker = new ChasseurComboTracker();
            this.chasseurGuardianManager = new ChasseurGuardianManager();
            this.chasseurKillSystem = new ChasseurKillSystem(professionManager, chasseurComboTracker, chasseurGuardianManager);
            this.rodeurDunesTickSystem = new RodeurDunesTickSystem(professionManager);
            this.secondSouffleTickSystem = new SecondSouffleTickSystem(professionManager);
            this.yeuxLynxTickSystem = new YeuxLynxTickSystem(professionManager);
            this.yeuxHibouTickSystem = new YeuxHibouTickSystem(professionManager);
            this.litDeFortuneTickSystem = new LitDeFortuneTickSystem(professionManager);
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
                pendingProfessionHudInit.put(ref.getUuid(), ref);
            });
            getEventRegistry().registerGlobal(PlayerReadyEvent.class, event -> {
                Player player = event.getPlayer();
                if (player == null || professionManager == null) return;
                UUID uid = player.getUuid();
                PlayerRef connectRef = pendingProfessionHudInit.remove(uid);
                if (connectRef == null) return;

                @SuppressWarnings("rawtypes") Ref entityRef = event.getPlayerRef();
                if (entityRef == null) return;
                @SuppressWarnings("rawtypes") Store store = entityRef.getStore();
                if (store == null) return;
                World world = ((EntityStore) store.getExternalData()).getWorld();
                if (world == null) return;

                final Player readyPlayer = player;
                final PlayerRef readyRef = connectRef;
                world.execute(() -> {
                    try {
                        if (!readyRef.isValid()) return;
                        ProfessionXpHud.getOrCreate(readyPlayer, readyRef);
                    } catch (Exception e) {
                        LOGGER.atWarning().withCause(e).log("[VaryonRPG] init ProfessionXpHud");
                    }
                });
            });
            getEventRegistry().registerGlobal(PlayerInteractEvent.class, event -> {
                ItemStack held = event.getItemInHand();
                if (held == null) return;
                String itemId = held.getItemId();
                if (itemId == null) return;
                boolean isOreBag = BagCraftRestrictionSystem.ORE_BAG_IDS.contains(itemId);
                boolean isCropBag = BagCraftRestrictionSystem.CROP_BAG_IDS.contains(itemId);
                boolean isWoodBag = BagCraftRestrictionSystem.WOOD_BAG_IDS.contains(itemId);
                if (!isOreBag && !isCropBag && !isWoodBag) return;
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
                } else if (isWoodBag) {
                    ok = acc != null && acc.isActive(fr.varyon.vrpg.rpg.Profession.FORESTIER)
                        && acc.getTalentRank(fr.varyon.vrpg.rpg.Profession.FORESTIER, "13") > 0;
                    msg = "Besace du Forestier — talent Forestier (nœud 13) requis.";
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
                ItemStack heldRestrict = event.getItemInHand();
                if (heldRestrict != null) {
                    String restrictId = heldRestrict.getItemId();
                    if (restrictId != null && TalentItemRestrictionSystem.getMessage(restrictId) != null) {
                        Player restrictPlayer = event.getPlayer();
                        if (restrictPlayer != null) {
                            PlayerRef restrictRef = restrictPlayer.getPlayerRef();
                            PlayerAccount restrictAcc = restrictRef != null && professionManager != null
                                ? professionManager.getAccount(restrictRef.getUuid()) : null;
                            if (!TalentItemRestrictionSystem.isAllowed(restrictId, restrictAcc)) {
                                event.setCancelled(true);
                                restrictPlayer.sendMessage(com.hypixel.hytale.server.core.Message.raw(
                                    TalentItemRestrictionSystem.getMessage(restrictId))
                                    .color(new java.awt.Color(200, 50, 50)));
                                return;
                            }
                        }
                    }
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
                farmerAnimalDropSystem.onMilkInteract(milkRef, roleLower);
            });
            getEventRegistry().registerGlobal(PlayerDisconnectEvent.class, event -> {
                PlayerRef ref = event.getPlayerRef();
                if (ref != null) {
                    pendingProfessionHudInit.remove(ref.getUuid());
                    ProfessionXpHud.cleanup(ref.getUuid());
                }
                if (ref != null && professionManager != null) {
                    professionManager.onPlayerDisconnect(ref.getUuid());
                    if (comboTracker != null) comboTracker.remove(ref.getUuid());
                    if (farmerComboTracker != null) farmerComboTracker.remove(ref.getUuid());
                    if (forestierComboTracker != null) forestierComboTracker.remove(ref.getUuid());
                    if (poumonLoutreTickSystem != null) poumonLoutreTickSystem.removePlayer(ref.getUuid());
                    if (rodeurSylvestreTickSystem != null) rodeurSylvestreTickSystem.removePlayer(ref.getUuid());
                    if (yeuxLynxTickSystem != null) yeuxLynxTickSystem.removePlayer(ref.getUuid());
                    if (yeuxHibouTickSystem != null) yeuxHibouTickSystem.removePlayer(ref.getUuid());
                    if (litDeFortuneTickSystem != null) litDeFortuneTickSystem.removePlayer(ref.getUuid());
                    if (chasseurComboTracker != null) chasseurComboTracker.remove(ref.getUuid());
                    if (rodeurDunesTickSystem != null) rodeurDunesTickSystem.removePlayer(ref.getUuid());
                    if (secondSouffleTickSystem != null) secondSouffleTickSystem.removePlayer(ref.getUuid());
                    if (farmerPickupHarvestSystem != null) farmerPickupHarvestSystem.removePlayer(ref.getUuid());
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
            getEntityStoreRegistry().registerSystem(new ForestierBlockBreakSystem(professionManager, forestierComboTracker, guardianWoodManager));
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("[VaryonRPG] register ForestierBlockBreakSystem");
        }

        try {
            getEntityStoreRegistry().registerSystem(new ForestierPickupSystem(professionManager));
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("[VaryonRPG] register ForestierPickupSystem");
        }

        try {
            getEntityStoreRegistry().registerSystem(new GuardianWoodTickSystem(guardianWoodManager));
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("[VaryonRPG] register GuardianWoodTickSystem");
        }

        try {
            getEntityStoreRegistry().registerSystem(poumonLoutreTickSystem);
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("[VaryonRPG] register PoumonLoutreTickSystem");
        }

        try {
            getEntityStoreRegistry().registerSystem(rodeurSylvestreTickSystem);
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("[VaryonRPG] register RodeurSylvestreTickSystem");
        }

        try {
            getEntityStoreRegistry().registerSystem(new RodeurSylvestreFallSystem(rodeurSylvestreTickSystem));
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("[VaryonRPG] register RodeurSylvestreFallSystem");
        }

        try {
            getEntityStoreRegistry().registerSystem(yeuxLynxTickSystem);
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("[VaryonRPG] register YeuxLynxTickSystem");
        }

        try {
            getEntityStoreRegistry().registerSystem(yeuxHibouTickSystem);
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("[VaryonRPG] register YeuxHibouTickSystem");
        }

        try {
            getEntityStoreRegistry().registerSystem(litDeFortuneTickSystem);
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("[VaryonRPG] register LitDeFortuneTickSystem");
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
            getEntityStoreRegistry().registerSystem(farmerPickupHarvestSystem);
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("[VaryonRPG] register FarmerPickupHarvestSystem");
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
            getEntityStoreRegistry().registerSystem(new TalentItemRestrictionSystem(professionManager));
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("[VaryonRPG] register TalentItemRestrictionSystem");
        }

        try {
            getEntityStoreRegistry().registerSystem(new TalentItemPlaceRestrictionSystem(professionManager));
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("[VaryonRPG] register TalentItemPlaceRestrictionSystem");
        }

        try {
            getEntityStoreRegistry().registerSystem(farmerAnimalDropSystem.new AttackTagger());
            getEntityStoreRegistry().registerSystem(farmerAnimalDropSystem.new DropOnDeath());
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("[VaryonRPG] register FarmerAnimalDropSystem");
        }

        try {
            getEntityStoreRegistry().registerSystem(chasseurKillSystem.new AttackTagger());
            getEntityStoreRegistry().registerSystem(chasseurKillSystem.new DropOnDeath());
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("[VaryonRPG] register ChasseurKillSystem");
        }

        try {
            getEntityStoreRegistry().registerSystem(new ChasseurGuardianTickSystem(chasseurGuardianManager));
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("[VaryonRPG] register ChasseurGuardianTickSystem");
        }

        try {
            getEntityStoreRegistry().registerSystem(new ChasseurPickupSystem(professionManager, chasseurComboTracker));
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("[VaryonRPG] register ChasseurPickupSystem");
        }

        try {
            getEntityStoreRegistry().registerSystem(rodeurDunesTickSystem);
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("[VaryonRPG] register RodeurDunesTickSystem");
        }

        try {
            getEntityStoreRegistry().registerSystem(new RodeurDunesFallSystem(rodeurDunesTickSystem));
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("[VaryonRPG] register RodeurDunesFallSystem");
        }

        try {
            getEntityStoreRegistry().registerSystem(secondSouffleTickSystem);
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("[VaryonRPG] register SecondSouffleTickSystem");
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
        if (guardianWoodManager != null) guardianWoodManager.clear();
        if (chasseurGuardianManager != null) chasseurGuardianManager.clear();
        if (veinCooldownTracker != null) veinCooldownTracker.clear();
        if (comboTracker != null) comboTracker.clear();
        if (farmerComboTracker != null) farmerComboTracker.clear();
        if (forestierComboTracker != null) forestierComboTracker.clear();
        if (chasseurComboTracker != null) chasseurComboTracker.clear();
        instance = null;
    }
}
