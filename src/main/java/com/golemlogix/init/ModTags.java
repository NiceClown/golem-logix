package com.golemlogix.init;

import com.golemlogix.GolemLogixMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Block;

/** Tags for modpacks: which mobs count as copper golems and which blocks as their statues (any mod may provide them). */
public final class ModTags {
    public static final TagKey<EntityType<?>> COPPER_GOLEMS =
            TagKey.create(Registries.ENTITY_TYPE, Identifier.fromNamespaceAndPath(GolemLogixMod.MOD_ID, "copper_golems"));
    public static final TagKey<Block> COPPER_GOLEM_STATUES =
            TagKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath(GolemLogixMod.MOD_ID, "copper_golem_statues"));
    /** Blood the Cryo-Distillation Tower distils into Source (Essentia Vitae from Neo Vitae; modpacks may add their own). */
    public static final TagKey<net.minecraft.world.level.material.Fluid> CRYO_DISTILLABLE =
            TagKey.create(Registries.FLUID, Identifier.fromNamespaceAndPath(GolemLogixMod.MOD_ID, "cryo_distillable"));

    private ModTags() {
    }
}
