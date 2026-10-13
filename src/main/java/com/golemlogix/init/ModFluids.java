package com.golemlogix.init;

import com.golemlogix.GolemLogixMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.BaseFlowingFluid;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * Liquid Cryo-Source: the network's own fluid. It is not stored anywhere; the network makes it from Source when automation
 * pumps it out (export bus, pattern provider, interface). It has no bucket and no block on purpose: it cannot be scooped up by hand.
 */
public final class ModFluids {
    public static final DeferredRegister<FluidType> FLUID_TYPES = DeferredRegister.create(NeoForgeRegistries.Keys.FLUID_TYPES, GolemLogixMod.MOD_ID);
    public static final DeferredRegister<Fluid> FLUIDS = DeferredRegister.create(Registries.FLUID, GolemLogixMod.MOD_ID);

    public static final DeferredHolder<FluidType, FluidType> CRYO_SOURCE_TYPE = FLUID_TYPES.register("cryo_source",
            () -> new FluidType(FluidType.Properties.create()
                    .descriptionId("fluid_type.golem_logix.cryo_source")
                    .lightLevel(10)
                    .density(800)
                    .viscosity(600)
                    .temperature(160)
                    .canConvertToSource(false)
                    .canPushEntity(false)
                    .canSwim(false)
                    .canDrown(false)));

    public static final DeferredHolder<Fluid, BaseFlowingFluid.Source> CRYO_SOURCE = FLUIDS.register("cryo_source",
            () -> new BaseFlowingFluid.Source(properties()));

    public static final DeferredHolder<Fluid, BaseFlowingFluid.Flowing> CRYO_SOURCE_FLOWING = FLUIDS.register("flowing_cryo_source",
            () -> new BaseFlowingFluid.Flowing(properties()));

    /** No bucket and no block: the fluid only exists inside tanks and machines. */
    private static BaseFlowingFluid.Properties properties() {
        return new BaseFlowingFluid.Properties(CRYO_SOURCE_TYPE, CRYO_SOURCE, CRYO_SOURCE_FLOWING);
    }

    private ModFluids() {
    }
}
