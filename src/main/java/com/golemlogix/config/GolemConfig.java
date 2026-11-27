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
    public static final ModConfigSpec.IntValue BUS_MAX_MILLIS_PER_TICK;
    public static final ModConfigSpec.IntValue CRAFT_MAX_MILLIS_PER_TICK;
    public static final ModConfigSpec.IntValue LOCAL_WIRELESS_RANGE;
    public static final ModConfigSpec.IntValue LOCAL_TELEGRAPH_RANGE;
    public static final ModConfigSpec.IntValue CRYO_JAR_CAPACITY;
    public static final ModConfigSpec.IntValue CRYO_JAR_GIGA_BONUS;
    public static final ModConfigSpec.IntValue CRYO_JAR_SUPER_BONUS;
    public static final ModConfigSpec.IntValue GIGA_SOURCE_DISCOUNT;
    public static final ModConfigSpec.IntValue REACTOR_CHANNEL_BONUS;
    public static final ModConfigSpec.IntValue DISTILL_RATE;
    public static final ModConfigSpec.DoubleValue DISTILL_SOURCE_PER_MB;
    public static final ModConfigSpec.IntValue DISTILL_COPROCESSOR_BONUS;
    public static final ModConfigSpec.IntValue MIXER_SNOW_WATER;
    public static final ModConfigSpec.IntValue MIXER_SNOW_CRYO;
    public static final ModConfigSpec.IntValue MIXER_SNOW_TICKS;
    public static final ModConfigSpec.IntValue MIXER_ICE_WATER;
    public static final ModConfigSpec.IntValue MIXER_ICE_CRYO;
    public static final ModConfigSpec.IntValue MIXER_ICE_TICKS;
    public static final ModConfigSpec.BooleanValue TYPEWRITER_DING;
    public static final ModConfigSpec.BooleanValue MONITOR_BLOCKED_TICK;
    public static final ModConfigSpec.DoubleValue VIS_CONDENSER_DRAIN_PER_SEC;
    public static final ModConfigSpec.DoubleValue VIS_CONDENSER_SOURCE_PER_VIS;
    public static final ModConfigSpec.IntValue VIS_CONDENSER_HYSTERESIS_PERCENT;
    public static final ModConfigSpec.IntValue VIS_CONDENSER_HYSTERESIS_EXACT;
    public static final ModConfigSpec.IntValue VIS_CONDENSER_LOW_VIS_PERCENT;
    public static final ModConfigSpec.DoubleValue VIS_CONDENSER_LOW_VIS_FLUX;
    public static final ModConfigSpec.ConfigValue<String> CAULDRON_PRIMARY_ASPECT;
    public static final ModConfigSpec.ConfigValue<String> CAULDRON_COOLANT_ASPECT;
    public static final ModConfigSpec.IntValue CAULDRON_PRIMARY_COST;
    public static final ModConfigSpec.IntValue CAULDRON_COOLANT_COST;
    public static final ModConfigSpec.IntValue CAULDRON_SOURCE_PER_REACTION;
    public static final ModConfigSpec.DoubleValue CAULDRON_BASE_SPEED;
    public static final ModConfigSpec.DoubleValue CAULDRON_COPROCESSOR_BONUS;
    public static final ModConfigSpec.IntValue CAULDRON_ICE_CHARGE;
    public static final ModConfigSpec.IntValue CAULDRON_PACKED_ICE_CHARGE;
    public static final ModConfigSpec.IntValue CAULDRON_BLUE_ICE_CHARGE;
    public static final ModConfigSpec.IntValue CAULDRON_BOIL_GRACE_TICKS;
    public static final ModConfigSpec.IntValue CAULDRON_BOIL_INTERVAL_TICKS;
    public static final ModConfigSpec.DoubleValue CAULDRON_BOIL_FLUX;

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
        BUS_MAX_MILLIS_PER_TICK = b.comment("How long all buses together may work in one tick (milliseconds, of the 50 a tick has; 0: no limit).",
                        "Over it, buses wait for the next tick and catch up afterwards. /golemlogix_buses shows what they take.")
                .defineInRange("busMaxMillisPerTick", 10, 0, 50);
        CRAFT_MAX_MILLIS_PER_TICK = b.comment("How long all autocrafting jobs together may work in one tick (milliseconds, of the 50 a tick has; 0: no limit).",
                        "Over it, a job goes on in its next cycle.")
                .defineInRange("craftMaxMillisPerTick", 10, 0, 50);
        b.pop();

        b.push("wireless");
        LOCAL_WIRELESS_RANGE = b.comment("Range in blocks of a local-tier Portable Chronometer.")
                .defineInRange("localWirelessRange", 64, 1, 30000000);
        LOCAL_TELEGRAPH_RANGE = b.comment("Range in blocks of a local-tier Cryo-Source Telegraph link.")
                .defineInRange("localTelegraphRange", 200, 1, 30000000);
        b.pop();

        b.push("distillation");
        CRYO_JAR_CAPACITY = b.comment("Source a Cryo-Source Jar holds.")
                .defineInRange("cryoJarCapacity", 10000, 100, 10000000);
        CRYO_JAR_GIGA_BONUS = b.comment("Extra Source four jars joined into a Giga Jar hold (percent of what the four hold alone).")
                .defineInRange("cryoJarGigaBonus", 10, 0, 1000);
        CRYO_JAR_SUPER_BONUS = b.comment("Extra Source nine jars joined into an Antigravity Reactor hold (percent of what the nine hold alone).")
                .defineInRange("cryoJarSuperBonus", 20, 0, 1000);
        GIGA_SOURCE_DISCOUNT = b.comment("Source consumption discount (percent) across a network with at least one Giga Jar (applies once per network, non-stacking).")
                .defineInRange("gigaSourceDiscount", 7, 0, 100);
        REACTOR_CHANNEL_BONUS = b.comment("Extra channel capacity (percent) across a network with at least one Antigravity Reactor (applies once per network, non-stacking).")
                .defineInRange("reactorChannelBonus", 25, 0, 1000);
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

        b.push("sound");
        TYPEWRITER_DING = b.comment("Play a typewriter-carriage bell ding when a Cryo Calculator completes a job of >= 16 executions or an order placed by a player.")
                .define("typewriterDing", true);
        MONITOR_BLOCKED_TICK = b.comment("Recipe Monitor plays a soft tick while a crafting job is blocked.")
                .define("monitorBlockedTick", true);
        b.pop();

        b.push("thaumaturge");
        VIS_CONDENSER_DRAIN_PER_SEC = b.comment("Vis drained per second from the chunk aura by the Vis Condenser.")
                .defineInRange("visCondenserDrainPerSec", 1.0D, 0.05D, 100.0D);
        VIS_CONDENSER_SOURCE_PER_VIS = b.comment("Cryo-Source generated by the Vis Condenser per 1 Vis drained.")
                .defineInRange("visCondenserSourcePerVis", 15.0D, 0.1D, 1000.0D);
        VIS_CONDENSER_HYSTERESIS_PERCENT = b.comment("Extra percent of chunk base Vis required above the percent floor before the Vis Condenser resumes.")
                .defineInRange("visCondenserHysteresisPercent", 5, 0, 50);
        VIS_CONDENSER_HYSTERESIS_EXACT = b.comment("Extra Vis required above the exact Vis floor before the Vis Condenser resumes.")
                .defineInRange("visCondenserHysteresisExact", 5, 0, 200);
        VIS_CONDENSER_LOW_VIS_PERCENT = b.comment("Percent of chunk base Vis at or below which draining Vis with the Vis Condenser emits Flux.")
                .defineInRange("visCondenserLowVisPercent", 10, 0, 50);
        VIS_CONDENSER_LOW_VIS_FLUX = b.comment("Flux released per 1 Vis drained when the Vis Condenser operates at or below the low-Vis threshold.")
                .defineInRange("visCondenserLowVisFlux", 0.05D, 0.0D, 5.0D);
        CAULDRON_PRIMARY_ASPECT = b.comment("Aspect ID consumed on the left nozzle of the Essentia Cryo-Cauldron.")
                .define("cauldronPrimaryAspect", "thaumaturge:auram");
        CAULDRON_COOLANT_ASPECT = b.comment("Aspect ID consumed on the right nozzle of the Essentia Cryo-Cauldron.")
                .define("cauldronCoolantAspect", "thaumaturge:gelum");
        CAULDRON_PRIMARY_COST = b.comment("Units of primary aspect (Auram) consumed per Essentia Cryo-Cauldron reaction.")
                .defineInRange("cauldronPrimaryCost", 1, 1, 64);
        CAULDRON_COOLANT_COST = b.comment("Units of coolant aspect (Gelum) consumed per Essentia Cryo-Cauldron reaction.")
                .defineInRange("cauldronCoolantCost", 2, 1, 64);
        CAULDRON_SOURCE_PER_REACTION = b.comment("Cryo-Source produced per Essentia Cryo-Cauldron reaction.")
                .defineInRange("cauldronSourcePerReaction", 160, 1, 10000);
        CAULDRON_BASE_SPEED = b.comment("Base Essentia Cryo-Cauldron reactions per second without Compressed Co-Processors.")
                .defineInRange("cauldronBaseSpeed", 0.25D, 0.05D, 50.0D);
        CAULDRON_COPROCESSOR_BONUS = b.comment("Extra reactions per second per Compressed Co-Processor in the Essentia Cryo-Cauldron.")
                .defineInRange("cauldronCoprocessorBonus", 0.125D, 0.0D, 50.0D);
        CAULDRON_ICE_CHARGE = b.comment("Source cooled by one normal Ice block under the Essentia Cryo-Cauldron before melting to Water.")
                .defineInRange("cauldronIceCharge", 1000, 10, 1000000);
        CAULDRON_PACKED_ICE_CHARGE = b.comment("Source cooled by one Packed Ice block under the Essentia Cryo-Cauldron before degrading to Ice.")
                .defineInRange("cauldronPackedIceCharge", 3000, 10, 1000000);
        CAULDRON_BLUE_ICE_CHARGE = b.comment("Source cooled by one Blue Ice block under the Essentia Cryo-Cauldron before degrading to Packed Ice.")
                .defineInRange("cauldronBlueIceCharge", 9000, 10, 1000000);
        CAULDRON_BOIL_GRACE_TICKS = b.comment("Grace ticks before uncooled or Gelum-starved Auram starts boiling off into Flux.")
                .defineInRange("cauldronBoilGraceTicks", 40, 0, 1200);
        CAULDRON_BOIL_INTERVAL_TICKS = b.comment("Ticks between Flux boil-off events once the grace period expires.")
                .defineInRange("cauldronBoilIntervalTicks", 40, 5, 1200);
        CAULDRON_BOIL_FLUX = b.comment("Flux released into the chunk aura per Essentia Cryo-Cauldron boil-off tick.")
                .defineInRange("cauldronBoilFlux", 0.5D, 0.0D, 20.0D);
        b.pop();

        SPEC = b.build();
    }

    private GolemConfig() {
    }
}
