package fr.varyon.vrpg.profession.mineur;

import com.hypixel.hytale.server.core.entity.ExplosionConfig;
import com.hypixel.hytale.server.core.asset.type.item.config.ItemTool;
import com.hypixel.hytale.server.core.asset.type.item.config.ItemToolSpec;

final class MinerExplosionConfig extends ExplosionConfig {

    MinerExplosionConfig() {
        this.damageEntities = true;
        this.damageBlocks = true;
        this.blockDamageRadius = 5;
        this.blockDamageFalloff = 1f;
        this.entityDamageRadius = 5f;
        this.entityDamage = 20f;
        this.entityDamageFalloff = 1f;
        this.blockDropChance = 0.4f;
        this.itemTool = new ItemTool(new ItemToolSpec[]{
            new ItemToolSpec("SoftBlocks",     2f, 0),
            new ItemToolSpec("Soils",          2f, 0),
            new ItemToolSpec("Woods",          2f, 0),
            new ItemToolSpec("Rocks",          2f, 0),
            new ItemToolSpec("Benches",        2f, 0),
            new ItemToolSpec("OreCopper",      1f, 0),
            new ItemToolSpec("OreIron",        1f, 0),
            new ItemToolSpec("OreSilver",      1f, 0),
            new ItemToolSpec("OreGold",        1f, 0),
            new ItemToolSpec("OreThorium",     1f, 0),
            new ItemToolSpec("OreCobalt",      1f, 0),
            new ItemToolSpec("OreAdamantite",  1f, 0),
            new ItemToolSpec("OreMithril",     1f, 0),
            new ItemToolSpec("VolcanicRocks",  0.001f, 0),
        }, 1f, new ItemTool.DurabilityLossBlockTypes[0]);
    }
}
