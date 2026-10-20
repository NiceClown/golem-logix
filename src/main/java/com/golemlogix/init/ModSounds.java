package com.golemlogix.init;

import com.golemlogix.GolemLogixMod;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUND_EVENTS =
            DeferredRegister.create(BuiltInRegistries.SOUND_EVENT, GolemLogixMod.MOD_ID);

    public static final Supplier<SoundEvent> ANVIL_CRUSH = registerSound("anvil_crush");
    public static final Supplier<SoundEvent> TUBE_SCURRY = registerSound("tube_scurry");
    public static final Supplier<SoundEvent> RELAY_CLICK = registerSound("relay_click");
    public static final Supplier<SoundEvent> VACUUM_HUM = registerSound("vacuum_hum");
    public static final Supplier<SoundEvent> VACUUM_SUCTION = registerSound("vacuum_suction");
    public static final Supplier<SoundEvent> PNEUMATIC_INJECT = registerSound("pneumatic_inject");

    private static Supplier<SoundEvent> registerSound(String name) {
        return SOUND_EVENTS.register(name, () -> SoundEvent.createVariableRangeEvent(
                Identifier.fromNamespaceAndPath(GolemLogixMod.MOD_ID, name)));
    }
}
