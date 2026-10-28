package com.golemlogix.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Common mod settings (golem_logix-common.toml).
 */
public final class GolemConfig {
    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.BooleanValue REQUIRE_SOURCE;
    public static final ModConfigSpec.IntValue CORE_SOURCE_PER_SECOND;
    public static final ModConfigSpec.DoubleValue CHANNEL_SOURCE_PER_SECOND;
    public static final ModConfigSpec.IntValue CRYO_SOURCE_COST;
    public static final ModConfigSpec.IntValue TELEGRAPH_SOURCE_PER_SECOND;
    public static final ModConfigSpec.IntValue LINE_CHANNELS;
    public static final ModConfigSpec.IntValue EXPANDER_BONUS;
    public static final ModConfigSpec.IntValue QUANTUM_BONUS;
    public static final ModConfigSpec.IntValue EXPANDER_EXIT;
    public static final ModConfigSpec.IntValue QUANTUM_EXIT;
    public static final ModConfigSpec.IntValue DEVICE_CHANNELS;
    public static final ModConfigSpec.IntValue CRAFT_STEP_SOURCE;
    public static final ModConfigSpec.IntValue MAINFRAME_CORES_PER_CALCULATOR;
    public static final ModConfigSpec.IntValue MAINFRAME_COPROCESSOR_MULTIPLIER;
    public static final ModConfigSpec.IntValue CATALOG_SLOTS_PER_PROVIDER;
    public static final ModConfigSpec.IntValue BUS_ITEMS_PER_OPERATION;
    public static final ModConfigSpec.IntValue BUS_FLUID_PER_OPERATION;
    public static final ModConfigSpec.IntValue BUS_INTERVAL_TICKS;
    public static final ModConfigSpec.IntValue LOCAL_WIRELESS_RANGE;
    public static final ModConfigSpec.IntValue CRYO_JAR_CAPACITY;
    public static final ModConfigSpec.IntValue DISTILL_RATE;
    public static final ModConfigSpec.DoubleValue DISTILL_SOURCE_PER_MB;
    public static final ModConfigSpec.IntValue DISTILL_COPROCESSOR_BONUS;
    public static final ModConfigSpec.IntValue MIXER_SNOW_WATER;
    public static final ModConfigSpec.IntValue MIXER_SNOW_CRYO;
    public static final ModConfigSpec.IntValue MIXER_SNOW_TICKS;
    public static final ModConfigSpec.IntValue MIXER_ICE_WATER;
    public static final ModConfigSpec.IntValue MIXER_ICE_CRYO;
    public static final ModConfigSpec.IntValue MIXER_ICE_TICKS;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();

        b.push("network");
        REQUIRE_SOURCE = b.comment("If false the network and the Mana Saturator work without any Source (creative-like).",
                        "Without Ars Nouveau and without a blood mod for the Cryo-Distillation Tower (Neo Vitae) Source is never required:",
                        "there would be nowhere to get it from.")
                .define("requireSource", true);
        CORE_SOURCE_PER_SECOND = b.comment("Source drained every second by the Atlas Core itself.")
                .defineInRange("coreSourcePerSecond", 1, 0, 100000);
        CHANNEL_SOURCE_PER_SECOND = b.comment("Extra Source drained every second per used channel.")
                .defineInRange("channelSourcePerSecond", 0.25D, 0.0D, 1000.0D);
        CRYO_SOURCE_COST = b.comment("Source the network spends to make 1 mB of Liquid Cryo-Source pumped out by automation.")
                .defineInRange("cryoSourceCost", 1, 0, 1000);
        TELEGRAPH_SOURCE_PER_SECOND = b.comment("Source the Atlas Core spends every second on each working Cryo-Source Telegraph link.")
                .defineInRange("telegraphSourcePerSecond", 2, 0, 100000);
        b.pop();

        b.push("array");
        LINE_CHANNELS = b.comment("Channels of ONE Cable Bushing line in a completed Atlas Array (5 bushings = 5 lines).")
                .defineInRange("lineChannels", 64, 1, 4096);
        EXPANDER_BONUS = b.comment("Extra channels added to EVERY line by each Channel Expander built into the array wall.")
                .defineInRange("expanderBonus", 16, 0, 4096);
        QUANTUM_BONUS = b.comment("Extra channels added to EVERY line by each Quantum Channel Expander.")
                .defineInRange("quantumExpanderBonus", 32, 0, 4096);
        EXPANDER_EXIT = b.comment("Channels a Channel Expander passes out of its outer face (pipes attach to it like to a bushing).")
                .defineInRange("expanderExitChannels", 64, 0, 4096);
        QUANTUM_EXIT = b.comment("Channels a Quantum Channel Expander passes out of its outer face.")
                .defineInRange("quantumExpanderExitChannels", 128, 0, 4096);
        DEVICE_CHANNELS = b.comment("Channels a device (terminal, cabinet, bus, calculator...) passes on to the devices behind it, its own",
                        "channel included. 16 = like a basic pipe, as AE2 devices carry as much as a normal cable.")
                .defineInRange("deviceChannels", 16, 1, 4096);
        b.pop();

        b.push("crafting");
        CRAFT_STEP_SOURCE = b.comment("Source spent by the network for every executed pattern step.")
                .defineInRange("craftStepSource", 2, 0, 100000);
        MAINFRAME_CORES_PER_CALCULATOR = b.comment("Independent crafting cores each Cryo Calculator gives inside a Cryo-Mainframe.")
                .defineInRange("mainframeCoresPerCalculator", 2, 1, 16);
        MAINFRAME_COPROCESSOR_MULTIPLIER = b.comment("A Compressed Co-processor inside a Cryo-Mainframe counts as this many (for every core).")
                .defineInRange("mainframeCoprocessorMultiplier", 2, 1, 16);
        CATALOG_SLOTS_PER_PROVIDER = b.comment("Punch card places each Pattern Provider stored in a Punch Card Catalog adds (up to 64 providers).")
                .defineInRange("catalogSlotsPerProvider", 27, 1, 27);
        b.pop();

        b.push("buses");
        BUS_ITEMS_PER_OPERATION = b.comment("Items moved by an import/export bus per operation.")
                .defineInRange("busItemsPerOperation", 16, 1, 4096);
        BUS_FLUID_PER_OPERATION = b.comment("Fluid (mB) moved by an import/export bus per operation.")
                .defineInRange("busFluidPerOperation", 1000, 1, 100000);
        BUS_INTERVAL_TICKS = b.comment("Ticks between bus operations.")
                .defineInRange("busIntervalTicks", 8, 1, 200);
        b.pop();

        b.push("wireless");
        LOCAL_WIRELESS_RANGE = b.comment("Range in blocks of a local-tier Portable Chronometer.")
                .defineInRange("localWirelessRange", 200, 1, 30000000);
        b.pop();

        b.push("distillation");
        CRYO_JAR_CAPACITY = b.comment("Source a Cryo-Source Jar holds.")
                .defineInRange("cryoJarCapacity", 10000, 100, 10000000);
        DISTILL_RATE = b.comment("Blood (mB) a Cryo-Distillation Tower distils every second.")
                .defineInRange("distillRate", 20, 1, 100000);
        DISTILL_COPROCESSOR_BONUS = b.comment("Extra blood (mB a second) each Compressed Co-Processor in the tower distils.")
                .defineInRange("distillCoprocessorBonus", 20, 0, 100000);
        DISTILL_SOURCE_PER_MB = b.comment("Source the tower makes from 1 mB of blood (Essentia Vitae, or any fluid in the tag",
                        "golem_logix:cryo_distillable).")
                .defineInRange("distillSourcePerMb", 1.5D, 0.001D, 1000.0D);
        b.pop();

        b.push("mixer");
        MIXER_SNOW_WATER = b.comment("Cryo-Mixer, snow mode (fast): water (mB) for one snow block.")
                .defineInRange("snowWater", 250, 1, 64000);
        MIXER_SNOW_CRYO = b.comment("Cryo-Mixer, snow mode: Liquid Cryo-Source (mB) for one snow block.")
                .defineInRange("snowCryo", 25, 0, 64000);
        MIXER_SNOW_TICKS = b.comment("Cryo-Mixer, snow mode: ticks for one snow block without Compressed Co-Processors",
                        "(each co-processor adds the base speed once more).")
                .defineInRange("snowTicks", 20, 1, 72000);
        MIXER_ICE_WATER = b.comment("Cryo-Mixer, ice mode (slow): water (mB) for one block of ice.")
                .defineInRange("iceWater", 1000, 1, 64000);
        MIXER_ICE_CRYO = b.comment("Cryo-Mixer, ice mode: Liquid Cryo-Source (mB) for one block of ice.")
                .defineInRange("iceCryo", 100, 0, 64000);
        MIXER_ICE_TICKS = b.comment("Cryo-Mixer, ice mode: ticks for one block of ice without Compressed Co-Processors.")
                .defineInRange("iceTicks", 100, 1, 72000);
        b.pop();

        SPEC = b.build();
    }

    private GolemConfig() {
    }
}
