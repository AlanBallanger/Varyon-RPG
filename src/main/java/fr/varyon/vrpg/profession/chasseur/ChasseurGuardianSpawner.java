package fr.varyon.vrpg.profession.chasseur;

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

public final class ChasseurGuardianSpawner {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    public static final long SPAWN_DELAY_MS = 500L;
    private static final String PREDATEUR_ALPHA_NPC_ID = "Rex_Caven";

    private ChasseurGuardianSpawner() {}

    public static void scheduleSpawn(@Nonnull ChasseurGuardianManager manager,
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
                spawnRexCaven(entityStore, new Vector3d(x, y, z));
            } catch (Exception e) {
                LOGGER.atWarning().withCause(e).log("[PredateurAlpha] spawn schedule ERREUR");
            }
        }), SPAWN_DELAY_MS);
    }

    public static void spawnRexCaven(@Nonnull Store<EntityStore> store, @Nonnull Vector3d pos) {
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

    private static void playSpawnSound(@Nonnull World world, double x, double y, double z) {
        try {
            int idx = SoundEvent.getAssetMap().getIndex(TalentProcSounds.GARDIEN_CHASSEUR_SOUND_ID);
            if (idx <= 0) return;
            Store<EntityStore> entityStore = world.getEntityStore().getStore();
            SoundUtil.playSoundEvent3d(idx, SoundCategory.SFX, x, y, z, entityStore);
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("[PredateurAlpha] son ERREUR");
        }
    }
}
