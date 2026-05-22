package fr.varyon.vrpg.profession.forestier;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.protocol.SoundCategory;
import com.hypixel.hytale.server.core.asset.type.soundevent.config.SoundEvent;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.modules.entitystats.modifier.Modifier;
import com.hypixel.hytale.server.core.modules.entitystats.modifier.StaticModifier;
import com.hypixel.hytale.server.core.universe.world.SoundUtil;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.NPCPlugin;
import fr.varyon.vrpg.audio.TalentProcSounds;

import javax.annotation.Nonnull;

public final class ForestierGuardianSpawner {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    public static final long SPAWN_DELAY_MS = 500L;
    private static final String GUARDIAN_NPC_ID = "Wolf_Black";

    private ForestierGuardianSpawner() {}

    public static void scheduleSpawn(@Nonnull GuardianWoodManager manager,
                                     @Nonnull World world,
                                     @Nonnull Vector3d pos,
                                     boolean playSound) {
        final World w = world;
        final double x = pos.x;
        final double y = pos.y;
        final double z = pos.z;
        manager.queueDelayedRepop(() -> w.execute(() -> {
            try {
                if (playSound) {
                    playSpawnSound(w, x, y, z);
                }
                Store<EntityStore> entityStore = w.getEntityStore().getStore();
                spawnWolfBlack(entityStore, new Vector3d(x, y, z));
            } catch (Exception e) {
                LOGGER.atWarning().withCause(e).log("[GardienSylvestre] spawn schedule ERREUR");
            }
        }), SPAWN_DELAY_MS);
    }

    public static void spawnWolfBlack(@Nonnull Store<EntityStore> store, @Nonnull Vector3d pos) {
        LOGGER.atInfo().log("[GardienSylvestre] spawnNPC Wolf_Black — pos=" + pos);
        try {
            var pair = NPCPlugin.get().spawnNPC(store, GUARDIAN_NPC_ID, null, pos, new Vector3f(0f, 0f, 0f));
            if (pair == null) {
                LOGGER.atWarning().log("[GardienSylvestre] spawnNPC retourné null — vérifier le role name 'Wolf_Black'");
                return;
            }
            Ref<EntityStore> wolfRef = pair.left();
            EntityStatMap statMap = store.getComponent(wolfRef, EntityStatMap.getComponentType());
            if (statMap == null) {
                LOGGER.atWarning().log("[GardienSylvestre] EntityStatMap null sur Wolf_Black");
                return;
            }
            int healthIdx = DefaultEntityStatTypes.getHealth();
            statMap.putModifier(healthIdx, "guardian_wood_hp",
                new StaticModifier(Modifier.ModifierTarget.MAX, StaticModifier.CalculationType.MULTIPLICATIVE, 2.0f));
            statMap.maximizeStatValue(healthIdx);
            LOGGER.atInfo().log("[GardienSylvestre] Wolf_Black spawned OK pos=" + pos);
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("[GardienSylvestre] spawnNPC ERREUR pos=" + pos);
        }
    }

    private static void playSpawnSound(@Nonnull World world, double x, double y, double z) {
        try {
            int idx = SoundEvent.getAssetMap().getIndex(TalentProcSounds.GARDIEN_FORESTIER_SOUND_ID);
            if (idx <= 0) return;
            Store<EntityStore> entityStore = world.getEntityStore().getStore();
            SoundUtil.playSoundEvent3d(idx, SoundCategory.SFX, x, y, z, entityStore);
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("[GardienSylvestre] son ERREUR");
        }
    }
}
