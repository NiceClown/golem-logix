package com.golemlogix.init;

import com.golemlogix.GolemLogixMod;
import com.golemlogix.secret.ChronometerDimensionTrigger;
import net.minecraft.advancements.CriterionTrigger;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public final class ModTriggers {
    public static final DeferredRegister<CriterionTrigger<?>> TRIGGERS =
            DeferredRegister.create(Registries.TRIGGER_TYPE, GolemLogixMod.MOD_ID);

    /** Хронометр открыт (или открывался) в чужом для его ядра измерении. */
    public static final Supplier<ChronometerDimensionTrigger> CHRONOMETER_DIMENSION =
            TRIGGERS.register("chronometer_dimension", ChronometerDimensionTrigger::new);

    private ModTriggers() {
    }
}
