package fr.varyon.vrpg.profession.chasseur;

import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.SystemGroup;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.entity.component.HeadRotation;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageEventSystem;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageModule;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathSystems;
import com.hypixel.hytale.server.core.modules.entity.item.ItemComponent;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.modules.entitystats.modifier.Modifier;
import com.hypixel.hytale.server.core.modules.entitystats.modifier.StaticModifier;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.NPCPlugin;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import fr.varyon.vrpg.config.VrpgConfig;
import fr.varyon.vrpg.rpg.PlayerAccount;
import fr.varyon.vrpg.rpg.Profession;
import fr.varyon.vrpg.rpg.ProfessionManager;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ChasseurKillSystem {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final Random RANDOM = new Random();

    private static final String PREDATEUR_ALPHA_NPC_ID = "Rex_Caven";

    private final ProfessionManager professionManager;
    private final ChasseurComboTracker comboTracker;
    private final ConcurrentHashMap<Integer, Ref<EntityStore>> lastAttackerByVictim = new ConcurrentHashMap<>();

    public ChasseurKillSystem(@Nonnull ProfessionManager professionManager,
                               @Nonnull ChasseurComboTracker comboTracker) {
        this.professionManager = professionManager;
        this.comboTracker = comboTracker;
    }

    public final class AttackTagger extends DamageEventSystem {

        @Override
        public SystemGroup<EntityStore> getGroup() {
            return DamageModule.get().getFilterDamageGroup();
        }

        @Override
        public Query<EntityStore> getQuery() {
            return NPCEntity.getComponentType();
        }

        @Override
        public void handle(int index, @Nonnull ArchetypeChunk<EntityStore> chunk,
                           @Nonnull Store<EntityStore> store,
                           @Nonnull CommandBuffer<EntityStore> commandBuffer,
                           @Nonnull Damage damage) {
            if (damage.isCancelled() || damage.getAmount() <= 0f) return;
            Damage.Source source = damage.getSource();
            if (!(source instanceof Damage.EntitySource entitySource)) return;
            Ref<EntityStore> attackerRef = entitySource.getRef();
            if (attackerRef == null || !attackerRef.isValid()) return;

            Player player = store.getComponent(attackerRef, Player.getComponentType());
            if (player == null) player = commandBuffer.getComponent(attackerRef, Player.getComponentType());
            if (player == null) return;

            Ref<EntityStore> victimRef = chunk.getReferenceTo(index);
            lastAttackerByVictim.put(System.identityHashCode(victimRef), attackerRef);

            PlayerRef playerRef = player.getPlayerRef();
            if (playerRef == null) return;
            UUID uuid = playerRef.getUuid();

            PlayerAccount acc = professionManager.getAccount(uuid);
            if (acc == null || !acc.isActive(Profession.CHASSEUR)) return;

            int kitRank = acc.getTalentRank(Profession.CHASSEUR, "3");
            if (kitRank <= 0) return;

            try {
                Inventory inventory = player.getInventory();
                if (inventory == null) return;
                byte slot = inventory.getActiveHotbarSlot();
                ItemStack held = inventory.getHotbar().getItemStack((short) slot);
                if (held == null || held.getMaxDurability() <= 0) return;
                String heldId = held.getItemId();
                if (!ChasseurXpTable.isHuntingWeapon(heldId)) return;
                if (RANDOM.nextDouble() >= kitRank * 0.15) {
                    inventory.getHotbar().setItemStackForSlot((short) slot, held.withIncreasedDurability(-1.0));
                }
            } catch (Exception ignored) {}
        }
    }

    public final class DropOnDeath extends DeathSystems.OnDeathSystem {

        @Override
        public Query<EntityStore> getQuery() {
            return NPCEntity.getComponentType();
        }

        @Override
        @SuppressWarnings({"rawtypes", "unchecked"})
        public void onComponentAdded(@Nonnull Ref ref, @Nonnull DeathComponent death,
                                     @Nonnull Store store, @Nonnull CommandBuffer commandBuffer) {
            try {
                int victimId = System.identityHashCode(ref);
                Ref<EntityStore> attackerRef = lastAttackerByVictim.remove(victimId);

                boolean dbg = VrpgConfig.isDebugTalents();

                Player killer = resolveKiller(store, commandBuffer, attackerRef, death);
                if (killer == null) return;

                PlayerRef playerRef = killer.getPlayerRef();
                if (playerRef == null) return;
                UUID uuid = playerRef.getUuid();

                PlayerAccount acc = professionManager.getAccount(uuid);
                if (acc == null || !acc.isActive(Profession.CHASSEUR)) return;

                TransformComponent transform = (TransformComponent) store.getComponent(ref, TransformComponent.getComponentType());
                Vector3d pos = transform != null ? transform.getPosition().clone().add(0.0, 0.5, 0.0) : null;
                HeadRotation headRot = pos != null ? (HeadRotation) store.getComponent(ref, HeadRotation.getComponentType()) : null;
                Vector3f rot = headRot != null ? headRot.getRotation().clone() : new Vector3f(0f, 0f, 0f);

                int comboRank = acc.getTalentRank(Profession.CHASSEUR, "5");
                int comboCount = comboTracker.onKill(uuid);
                double comboPercent = comboRank > 0 ? (0.01 + (comboRank - 1) * 0.005) : 0.0;
                double comboBonus = comboRank > 0 ? comboCount * comboPercent : 0.0;
                if (dbg && comboRank > 0) LOGGER.atInfo().log("[ChasseurKill] N5 ChasseFrenetique combo="
                    + comboCount + " bonus=" + String.format("%.3f%%", comboBonus * 100));

                int xpRank = acc.getTalentRank(Profession.CHASSEUR, "1");
                double xpMult = 1.0 + xpRank * 0.05 + comboBonus;
                double finalXp = ChasseurXpTable.BASE_KILL_XP * xpMult;
                if (dbg) LOGGER.atInfo().log("[ChasseurKill] XP +" + finalXp
                    + " (base=" + ChasseurXpTable.BASE_KILL_XP + " × " + String.format("%.3f", xpMult) + ")"
                    + (xpRank > 0 ? " [N1 InstinctSauvage rank=" + xpRank + "]" : ""));
                professionManager.addXp(uuid, Profession.CHASSEUR, finalXp, playerRef);

                int essenceRank = acc.getTalentRank(Profession.CHASSEUR, "2");
                if (essenceRank > 0 && pos != null) {
                    double essenceChance = 0.01 + (essenceRank - 1) * 0.005;
                    if (RANDOM.nextDouble() < essenceChance) {
                        if (dbg) LOGGER.atInfo().log("[ChasseurKill] N2 MarqueduPredateur PROC");
                        try {
                            Holder[] drops = ItemComponent.generateItemDrops(store, List.of(new ItemStack(ChasseurXpTable.ESSENCE_ITEM_ID, 1)), pos, rot);
                            commandBuffer.addEntities(drops, AddReason.SPAWN);
                        } catch (Exception e) {
                            LOGGER.atWarning().withCause(e).log("[ChasseurKill] N2 essence ERREUR");
                        }
                    }
                }

                int alphaRank = acc.getTalentRank(Profession.CHASSEUR, "11");
                if (alphaRank > 0 && pos != null) {
                    double alphaChance = alphaRank * 0.005;
                    if (RANDOM.nextDouble() < alphaChance) {
                        if (dbg) LOGGER.atInfo().log("[ChasseurKill] N11 PredateurAlpha PROC — spawning " + PREDATEUR_ALPHA_NPC_ID);
                        spawnRexCaven(store, pos);
                    }
                }
            } catch (Exception e) {
                LOGGER.atWarning().withCause(e).log("[ChasseurKill] DropOnDeath error");
            }
        }

        @SuppressWarnings({"rawtypes", "unchecked"})
        private Player resolveKiller(@Nonnull Store store, @Nonnull CommandBuffer commandBuffer,
                                     Ref<EntityStore> lastAttacker, @Nonnull DeathComponent death) {
            Damage deathInfo = death.getDeathInfo();
            if (deathInfo != null && deathInfo.getSource() instanceof Damage.EntitySource es) {
                Ref<EntityStore> killerRef = es.getRef();
                if (killerRef != null && killerRef.isValid()) {
                    Player p = (Player) store.getComponent(killerRef, Player.getComponentType());
                    if (p == null) p = (Player) commandBuffer.getComponent(killerRef, Player.getComponentType());
                    if (p != null) return p;
                }
            }
            if (lastAttacker != null && lastAttacker.isValid()) {
                Player p = (Player) store.getComponent(lastAttacker, Player.getComponentType());
                if (p == null) p = (Player) commandBuffer.getComponent(lastAttacker, Player.getComponentType());
                return p;
            }
            return null;
        }
    }

    private static void spawnRexCaven(@Nonnull Store<EntityStore> store, @Nonnull Vector3d pos) {
        LOGGER.atInfo().log("[PredateurAlpha] spawnNPC " + PREDATEUR_ALPHA_NPC_ID + " — pos=" + pos);
        try {
            var pair = NPCPlugin.get().spawnNPC(store, PREDATEUR_ALPHA_NPC_ID, null, pos, new Vector3f(0f, 0f, 0f));
            if (pair == null) {
                LOGGER.atWarning().log("[PredateurAlpha] spawnNPC retourné null — vérifier le role name '" + PREDATEUR_ALPHA_NPC_ID + "'");
                return;
            }
            Ref<EntityStore> rexRef = pair.left();
            EntityStatMap statMap = store.getComponent(rexRef, EntityStatMap.getComponentType());
            if (statMap == null) {
                LOGGER.atWarning().log("[PredateurAlpha] EntityStatMap null sur " + PREDATEUR_ALPHA_NPC_ID);
                return;
            }
            int healthIdx = DefaultEntityStatTypes.getHealth();
            statMap.putModifier(healthIdx, "predateur_alpha_hp",
                new StaticModifier(Modifier.ModifierTarget.MAX, StaticModifier.CalculationType.MULTIPLICATIVE, 2.0f));
            statMap.maximizeStatValue(healthIdx);
            LOGGER.atInfo().log("[PredateurAlpha] " + PREDATEUR_ALPHA_NPC_ID + " spawned OK pos=" + pos);
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("[PredateurAlpha] spawnNPC ERREUR pos=" + pos);
        }
    }
}
