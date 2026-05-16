package fr.varyon.vrpg.profession.mineur;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.entity.ExplosionUtils;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.SoundUtil;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.asset.type.soundevent.config.SoundEvent;
import com.hypixel.hytale.protocol.SoundCategory;
import com.hypixel.hytale.logger.HytaleLogger;

import javax.annotation.Nullable;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ExplosionTalentSystem extends EntityTickingSystem<EntityStore> {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final MinerExplosionConfig CONFIG = new MinerExplosionConfig();
    private static final Damage.Source DAMAGE_SOURCE = new Damage.Source() {};
    private static final String BLAST_SOUND_ID = "SFX_Goblin_Lobber_Bomb_Death";
    private static int blastSoundIndex = 0;

    private final ConcurrentHashMap<UUID, Vector3d> pendingExplosions = new ConcurrentHashMap<>();
    private final ComponentType<EntityStore, PlayerRef> playerRefType = PlayerRef.getComponentType();

    public void queueExplosion(UUID uuid, Vector3d position) {
        pendingExplosions.put(uuid, position);
    }

    @Override
    public void tick(float deltaTime, int index,
                     ArchetypeChunk<EntityStore> chunk,
                     Store<EntityStore> store,
                     CommandBuffer<EntityStore> commandBuffer) {

        PlayerRef playerRef = chunk.getComponent(index, playerRefType);
        if (playerRef == null) return;

        UUID uuid = playerRef.getUuid();
        Vector3d pos = pendingExplosions.remove(uuid);
        if (pos == null) return;

        try {
            Player player = playerRef.getComponent(Player.getComponentType());
            if (player == null) return;
            World world = player.getWorld();
            if (world == null) return;

            Ref<EntityStore> ref = chunk.getReferenceTo(index);
            Store<ChunkStore> chunkStoreStore = world.getChunkStore().getStore();

            if (blastSoundIndex == 0) {
                blastSoundIndex = SoundEvent.getAssetMap().getIndex(BLAST_SOUND_ID);
            }
            LOGGER.atInfo().log("[Blast] performExplosion pos=" + pos.x + "," + pos.y + "," + pos.z);
            ExplosionUtils.performExplosion(DAMAGE_SOURCE, pos, CONFIG, ref, commandBuffer, chunkStoreStore);
            if (blastSoundIndex != 0) {
                SoundUtil.playSoundEvent3d(blastSoundIndex, SoundCategory.SFX, pos.x, pos.y, pos.z, commandBuffer);
            }
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("[Blast] performExplosion ERREUR pos=" + pos);
        }
    }

    @Nullable
    @Override
    public Query<EntityStore> getQuery() {
        return playerRefType;
    }
}
