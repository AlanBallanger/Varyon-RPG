package fr.varyon.vrpg.audio;

import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.protocol.SoundCategory;
import com.hypixel.hytale.protocol.ToClientPacket;
import com.hypixel.hytale.protocol.packets.world.PlaySoundEvent2D;
import com.hypixel.hytale.server.core.asset.type.soundevent.config.SoundEvent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.SoundUtil;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import fr.varyon.vrpg.rpg.PlayerAccount;
import fr.varyon.vrpg.rpg.Profession;
import fr.varyon.vrpg.rpg.TalentSoundNodes;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class TalentProcSounds {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    public static final String LOOT_DOUBLE_SOUND_ID = "SFX_Vrpg_PochesPleines";
    public static final String FANTOMATIQUE_SOUND_ID = "SFX_Vrpg_MineraiFantomatique";
    public static final String IMMORTEL_SOUND_ID = "SFX_Vrpg_MineraiImmortel";
    public static final String MULTI_SEEDS_SOUND_ID = "SFX_Vrpg_MultiSeeds";
    public static final String REFEED_SOUND_ID = "SFX_Vrpg_Refeed";
    public static final String GARDIEN_SCARECROW_SOUND_ID = "SFX_Vrpg_GardienScarecrow";
    public static final String GARDIEN_FORESTIER_SOUND_ID = "SFX_Vrpg_GardienForestier";
    public static final String GARDIEN_CHASSEUR_SOUND_ID = "SFX_Vrpg_GardienChasseur";
    public static final String TREE_REGROWTH_SOUND_ID = "SFX_Vrpg_TreeRegrowth";
    public static final int MAX_COMBO_SOUND = 8;

    private TalentProcSounds() {}

    public static void playLootDouble(@Nonnull PlayerAccount acc,
                                      @Nonnull Profession profession,
                                      @Nonnull PlayerRef playerRef,
                                      @Nonnull Ref<EntityStore> ref,
                                      @Nonnull CommandBuffer<EntityStore> buffer,
                                      @Nonnull Vector3d at) {
        if (!acc.isTalentSoundEnabled(profession, TalentSoundNodes.lootDoubleNode(profession))) return;
        playProcSound(LOOT_DOUBLE_SOUND_ID, playerRef, ref, buffer, at);
    }

    public static void playFantomatique(@Nonnull PlayerAccount acc,
                                        @Nonnull Profession profession,
                                        @Nonnull PlayerRef playerRef,
                                        @Nonnull Ref<EntityStore> ref,
                                        @Nonnull CommandBuffer<EntityStore> buffer,
                                        @Nonnull Vector3d at) {
        if (!acc.isTalentSoundEnabled(profession, TalentSoundNodes.fantomatiqueNode(profession))) return;
        playProcSound(FANTOMATIQUE_SOUND_ID, playerRef, ref, buffer, at);
    }

    public static void playCombo(@Nonnull PlayerAccount acc,
                                 @Nonnull Profession profession,
                                 int comboRank,
                                 int comboCount,
                                 @Nonnull PlayerRef playerRef,
                                 @Nonnull Ref<EntityStore> ref,
                                 @Nonnull CommandBuffer<EntityStore> buffer,
                                 @Nonnull Vector3d at) {
        if (comboRank <= 0 || comboCount <= 0) return;
        if (!acc.isTalentSoundEnabled(profession, TalentSoundNodes.comboNode(profession))) return;
        int soundLevel = Math.min(comboCount, MAX_COMBO_SOUND);
        playProcSound("SFX_Vrpg_Combo_" + soundLevel, playerRef, ref, buffer, at);
    }

    public static void playTalent(@Nonnull PlayerAccount acc,
                                    @Nonnull Profession profession,
                                    @Nonnull String nodeId,
                                    @Nonnull String soundEventId,
                                    @Nonnull PlayerRef playerRef,
                                    @Nonnull Ref<EntityStore> ref,
                                    @Nonnull CommandBuffer<EntityStore> buffer,
                                    @Nonnull Vector3d at) {
        if (!acc.isTalentSoundEnabled(profession, nodeId)) return;
        playProcSound(soundEventId, playerRef, ref, buffer, at);
    }

    private static void playProcSound(@Nonnull String soundEventId,
                                      @Nonnull PlayerRef playerRef,
                                      @Nonnull Ref<EntityStore> ref,
                                      @Nonnull CommandBuffer<EntityStore> commandBuffer,
                                      @Nonnull Vector3d at) {
        try {
            int idx = SoundEvent.getAssetMap().getIndex(soundEventId);
            if (idx <= 0) return;
            playerRef.getPacketHandler().writeNoCache(
                    (ToClientPacket) new PlaySoundEvent2D(idx, SoundCategory.SFX, 1.0f, 1.0f));
            SoundUtil.playSoundEvent3d(idx, SoundCategory.SFX, at.x, at.y, at.z, commandBuffer);
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("[TalentProcSounds] son ERREUR id=" + soundEventId);
        }
    }
}
