package fr.varyon.vrpg.profession.fermier;

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
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.entity.component.HeadRotation;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageEventSystem;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageModule;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathSystems;
import com.hypixel.hytale.server.core.modules.entity.item.ItemComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import fr.varyon.vrpg.config.VrpgConfig;
import fr.varyon.vrpg.rpg.PlayerAccount;
import fr.varyon.vrpg.rpg.Profession;
import fr.varyon.vrpg.rpg.ProfessionManager;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class FarmerAnimalDropSystem {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final Random RANDOM = new Random();
    private static final long MILK_COOLDOWN_MS = 3000L;

    private final ProfessionManager professionManager;
    private final ConcurrentHashMap<Integer, Ref<EntityStore>> lastAttackerByVictim = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Long> lastMilkTime = new ConcurrentHashMap<>();

    public FarmerAnimalDropSystem(@Nonnull ProfessionManager professionManager) {
        this.professionManager = professionManager;
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

            PlayerRef playerRef = player.getPlayerRef();
            if (playerRef == null) return;
            PlayerAccount acc = professionManager.getAccount(playerRef.getUuid());
            if (acc == null || !acc.isActive(Profession.FERMIER)) return;

            NPCEntity npc = chunk.getComponent(index, NPCEntity.getComponentType());
            if (npc == null) return;
            String role = npc.getRoleName();
            String roleLower = role != null ? role.toLowerCase(Locale.ROOT) : null;
            boolean dbg = VrpgConfig.isDebugTalents();
            if (roleLower == null || FarmerAnimalTable.resolveDropItemContains(roleLower) == null) {
                if (dbg) LOGGER.atInfo().log("[FarmerAnimalDrop] AttackTagger — ignored npc role=" + role);
                return;
            }
            if (dbg) LOGGER.atInfo().log("[FarmerAnimalDrop] AttackTagger — tagged animal role=" + role);

            Ref<EntityStore> victimRef = chunk.getReferenceTo(index);
            lastAttackerByVictim.put(System.identityHashCode(victimRef), attackerRef);
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
                PlayerAccount acc = professionManager.getAccount(playerRef.getUuid());
                if (acc == null || !acc.isActive(Profession.FERMIER)) {
                    if (dbg) LOGGER.atInfo().log("[Fermier-DBG] SeigneurEtable — joueur non actif fermier"
                        + " active0=" + (acc == null ? "null" : acc.getActiveSlot0())
                        + " active1=" + (acc == null ? "null" : acc.getActiveSlot1()));
                    return;
                }

                NPCEntity npc = (NPCEntity) store.getComponent(ref, NPCEntity.getComponentType());
                if (npc == null) return;
                String role = npc.getRoleName();
                if (role == null) return;
                String roleLower = role.toLowerCase(Locale.ROOT);
                String dropItem = FarmerAnimalTable.resolveDropItemContains(roleLower);
                if (dropItem == null) {
                    if (dbg) LOGGER.atInfo().log("[FarmerAnimalDrop] DropOnDeath — no match role=" + role);
                    return;
                }

                professionManager.addXp(playerRef.getUuid(), Profession.FERMIER, FarmerAnimalTable.BASE_ANIMAL_KILL_XP, playerRef);
                if (dbg) LOGGER.atInfo().log("[FarmerAnimalDrop] DropOnDeath — XP +" + FarmerAnimalTable.BASE_ANIMAL_KILL_XP + " role=" + role);

                boolean isGuardian = FarmerAnimalTable.GUARDIAN_CROP_ROLE.equals(roleLower);
                if (!isGuardian) {
                    int rank = acc.getTalentRank(Profession.FERMIER, "7");
                    if (rank <= 0) return;
                    if (RANDOM.nextDouble() >= rank * 0.05) return;
                    if (dbg) LOGGER.atInfo().log("[FarmerAnimalDrop] N7 SeigneurEtable PROC — mob=" + role + " item=" + dropItem + " rank=" + rank);
                } else {
                    if (dbg) LOGGER.atInfo().log("[FarmerAnimalDrop] N11 GardienChamps tué — drop garanti item=" + dropItem);
                }

                TransformComponent transform = (TransformComponent) store.getComponent(ref, TransformComponent.getComponentType());
                if (transform == null) return;
                Vector3d pos = transform.getPosition().clone().add(0.0, 0.5, 0.0);
                HeadRotation headRot = (HeadRotation) store.getComponent(ref, HeadRotation.getComponentType());
                Vector3f rot = headRot != null ? headRot.getRotation().clone() : new Vector3f(0f, 0f, 0f);

                Holder[] drops = ItemComponent.generateItemDrops(store, List.of(new ItemStack(dropItem, 1)), pos, rot);
                commandBuffer.addEntities(drops, AddReason.SPAWN);

            } catch (Exception e) {
                LOGGER.atWarning().withCause(e).log("[FarmerAnimalDrop] DropOnDeath error");
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

    public void onMilkInteract(@Nonnull PlayerRef playerRef, @Nonnull String roleLower) {
        UUID uuid = playerRef.getUuid();
        if (!FarmerAnimalTable.isMilkableAnimal(roleLower)) return;
        long now = System.currentTimeMillis();
        Long last = lastMilkTime.get(uuid);
        if (last != null && now - last < MILK_COOLDOWN_MS) return;
        lastMilkTime.put(uuid, now);
        PlayerAccount acc = professionManager.getAccount(uuid);
        if (acc == null || !acc.isActive(Profession.FERMIER)) return;
        boolean dbg = VrpgConfig.isDebugTalents();
        professionManager.addXp(uuid, Profession.FERMIER, FarmerAnimalTable.BASE_MILK_XP, playerRef);
        if (dbg) LOGGER.atInfo().log("[FarmerAnimalDrop] Milk XP +" + FarmerAnimalTable.BASE_MILK_XP + " role=" + roleLower);
    }

    public void removePlayer(@Nonnull UUID uuid) {
        lastMilkTime.remove(uuid);
    }
}
