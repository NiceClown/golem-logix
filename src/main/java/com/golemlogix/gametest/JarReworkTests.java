package com.golemlogix.gametest;

import com.golemlogix.GolemLogixMod;
import com.golemlogix.blockentity.AtlasCoreBlockEntity;
import com.golemlogix.blockentity.ChannelCounterBlockEntity;
import com.golemlogix.blockentity.CryoJarBlockEntity;
import com.golemlogix.blockentity.DistillationTowerBlockEntity;
import com.golemlogix.blocks.AtlantBlock;
import com.golemlogix.blocks.CryoJarBlock;
import com.golemlogix.blocks.DistillationTowerBlock;
import com.golemlogix.blocks.GolemProperties;
import com.golemlogix.blocks.JarForm;
import com.golemlogix.config.GolemConfig;
import com.golemlogix.init.ModBlocks;
import com.golemlogix.init.ModTags;
import com.golemlogix.items.PipePlugItem;
import com.golemlogix.system.CryoSource;
import com.golemlogix.system.GolemNetwork;
import com.golemlogix.system.JarMerge;
import com.golemlogix.system.SourceDiscount;
import com.golemlogix.system.SourceHelper;
import com.golemlogix.system.Transfers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.FunctionGameTestInstance;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.gametest.GameTestHooks;
import net.neoforged.neoforge.registries.RegisterEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * End-to-end NeoForge GameTests for the Giga Jar (-7% network Source consumption, non-stacking)
 * and Antigravity Reactor (+25% network channel capacity, non-stacking) rework.
 */
@EventBusSubscriber(modid = GolemLogixMod.MOD_ID)
public final class JarReworkTests {
    private static final BlockPos ORIGIN = new BlockPos(5, 1, 5);
    private static final BlockPos CORE = ORIGIN.above(3);

    private record Test(String name, Consumer<GameTestHelper> body, int maxTicks) {
    }

    private static final List<Test> TESTS = List.of(
            new Test("jar_rework_giga_discount_non_stacking_and_fractional", JarReworkTests::gigaDiscountNonStackingAndFractional, 200),
            new Test("jar_rework_giga_no_distillation_bonus", JarReworkTests::gigaNoDistillationBonus, 200),
            new Test("jar_rework_reactor_64_to_80_and_immediate_recalc", JarReworkTests::reactor64To80AndImmediateRecalc, 160),
            new Test("jar_rework_reactor_128_to_160_and_all_tiers", JarReworkTests::reactor128To160AndAllTiers, 160),
            new Test("jar_rework_tier_independence_and_coexistence", JarReworkTests::tierIndependenceAndCoexistence, 160),
            new Test("jar_rework_pipe_plug_toggles_reactor_bonus", JarReworkTests::pipePlugTogglesReactorBonus, 100)
    );

    private JarReworkTests() {
    }

    @SubscribeEvent
    public static void onRegisterFunctions(RegisterEvent event) {
        if (!GameTestHooks.isGametestEnabled()) {
            return;
        }
        event.register(Registries.TEST_FUNCTION, helper -> {
            for (Test t : TESTS) {
                helper.register(id(t.name()), t.body());
            }
        });
    }

    @SubscribeEvent
    public static void onRegisterGameTests(RegisterGameTestsEvent event) {
        if (!GameTestHooks.isGametestEnabled()) {
            return;
        }
        Holder<TestEnvironmentDefinition<?>> env = event.registerEnvironment(
                id("jar_rework"), new TestEnvironmentDefinition.AllOf(List.of()));
        for (Test t : TESTS) {
            ResourceKey<Consumer<GameTestHelper>> fn = ResourceKey.create(Registries.TEST_FUNCTION, id(t.name()));
            event.registerTest(id(t.name()), new FunctionGameTestInstance(fn, new TestData<>(env, id("empty"), t.maxTicks(), 0, true)));
        }
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(GolemLogixMod.MOD_ID, path);
    }

    private static void buildArray(GameTestHelper helper, int basicExpanders, int quantumExpanders) {
        int b = basicExpanders, q = quantumExpanders;
        for (int dy = 0; dy <= 3; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    BlockPos p = ORIGIN.offset(dx, dy, dz);
                    boolean corner = dx != 0 && dz != 0;
                    boolean center = dx == 0 && dz == 0;
                    if (dy == 0) {
                        helper.setBlock(p, (corner || center) ? ModBlocks.CABLE_BUSHING.get() : ModBlocks.ATLAS_CONDUIT.get());
                    } else if (dy == 1 || dy == 2) {
                        if (corner) {
                            helper.setBlock(p, ModBlocks.ATLAS_ATLANT.get().defaultBlockState()
                                    .setValue(AtlantBlock.HALF, dy == 1 ? DoubleBlockHalf.LOWER : DoubleBlockHalf.UPPER));
                        } else if (center) {
                            helper.setBlock(p, ModBlocks.ATLAS_CONDUIT.get());
                        } else if (b > 0) {
                            helper.setBlock(p, ModBlocks.CHANNEL_EXPANDER.get());
                            b--;
                        } else if (q > 0) {
                            helper.setBlock(p, ModBlocks.QUANTUM_CHANNEL_EXPANDER.get());
                            q--;
                        } else {
                            helper.setBlock(p, ModBlocks.ATLAS_CASING.get());
                        }
                    } else {
                        helper.setBlock(p, center ? ModBlocks.ATLAS_CORE.get() : ModBlocks.ATLAS_CASING.get());
                    }
                }
            }
        }
    }

    private static void placeJarSquare(GameTestHelper helper, BlockPos corner, int width) {
        var level = helper.getLevel();
        Block jar = ModBlocks.CRYO_SOURCE_JAR.get();
        for (int x = 0; x < width; x++) {
            for (int z = 0; z < width; z++) {
                CryoJarBlock.place(level, helper.absolutePos(corner.offset(x, 0, z)), Direction.SOUTH, jar);
            }
        }
    }

    /** Places a Dense Frost Pipe comb with 4 + 14*(len-1) terminals (each within 5 hops of the dense spine). */
    private static List<BlockPos> placeDenseComb(GameTestHelper helper, int x0, int z0, int dx, int dz, int len) {
        List<BlockPos> terminals = new ArrayList<>();
        for (int i = 0; i < len; i++) {
            BlockPos p = new BlockPos(x0 + dx * i, 1, z0 + dz * i);
            helper.setBlock(p, ModBlocks.DENSE_FROST_PIPE.get());
            for (int y = 2; y <= 5; y++) {
                BlockPos t = p.atY(y);
                helper.setBlock(t, ModBlocks.ANALOG_TERMINAL.get());
                terminals.add(t);
            }
            if (i == 0) {
                continue;
            }
            for (int side = -1; side <= 1; side += 2) {
                for (int y = 1; y <= 5; y++) {
                    BlockPos t = p.offset(dz * side, y - 1, dx * side);
                    helper.setBlock(t, ModBlocks.ANALOG_TERMINAL.get());
                    terminals.add(t);
                }
            }
        }
        return terminals;
    }

    private static int countLit(GameTestHelper helper, List<BlockPos> terminals) {
        int lit = 0;
        for (BlockPos rel : terminals) {
            if (helper.getBlockState(rel).getValue(GolemProperties.ACTIVE)) {
                lit++;
            }
        }
        return lit;
    }

    private static long totalAvailable(GolemNetwork net) {
        long sum = 0;
        for (SourceHelper.Supplier s : net.sources()) {
            sum += s.available();
        }
        return sum;
    }

    /**
     * 0 vs 1 vs 2 Giga Jars: -7% Source consumption discount applies once per network (non-stacking),
     * fractional accumulator preserves exact savings on 1/s upkeep, 2/step craft, and Cryo-Source synthesis,
     * storage stacks across multiple Giga Jars, and Giga Jars never grant channel boosts.
     */
    private static void gigaDiscountNonStackingAndFractional(GameTestHelper helper) {
        buildArray(helper, 0, 0);
        // Single jar touching corner bushing (4,1,6)
        BlockPos giga1 = new BlockPos(2, 1, 6); // east edge x=3 touches (4,1,6)
        CryoJarBlock.place(helper.getLevel(), helper.absolutePos(giga1.east()), Direction.SOUTH, ModBlocks.CRYO_SOURCE_JAR.get());
        CryoJarBlockEntity singleJar = helper.getBlockEntity(giga1.east(), CryoJarBlockEntity.class);
        singleJar.fill(5000, false);

        int discount = GolemConfig.GIGA_SOURCE_DISCOUNT.get();
        int expectedPer100 = 100 - discount;
        int expectedPer200 = 2 * (100 - discount);
        int expectedPer1000 = 10 * (100 - discount);

        helper.runAfterDelay(5, () -> {
            AtlasCoreBlockEntity core = helper.getBlockEntity(CORE, AtlasCoreBlockEntity.class);
            core.refresh(helper.getLevel(), helper.absolutePos(CORE));
            GolemNetwork n0 = GolemNetwork.get(helper.getLevel(), helper.absolutePos(ORIGIN));
            helper.assertTrue(n0.isOnline(), "network online with single jar");
            helper.assertTrue(!n0.hasGigaJar() && n0.gigaJarCount() == 0 && n0.sourceDiscountPercent() == 0,
                    "single jar must not grant Giga discount");
            helper.assertTrue(n0.lineCapacity() == 64 && n0.channelCapacity() == 320, "base channels without Reactor");

            if (SourceHelper.sourceRequired()) {
                long before = totalAvailable(n0);
                for (int i = 0; i < 100; i++) {
                    helper.assertTrue(n0.consumeSource(1), "single jar 1-cost drain " + i);
                }
                helper.assertTrue(before - totalAvailable(n0) == 100L,
                        "0 Giga Jars: 100 x 1 must drain 100, drained " + (before - totalAvailable(n0)));

                before = totalAvailable(n0);
                for (int i = 0; i < 100; i++) {
                    helper.assertTrue(n0.consumeSource(2), "single jar 2-cost drain " + i);
                }
                helper.assertTrue(before - totalAvailable(n0) == 200L,
                        "0 Giga Jars: 100 x 2 must drain 200, drained " + (before - totalAvailable(n0)));
            }

            // Complete Giga Jar #1 at (2,1,6)..(3,1,7)
            for (BlockPos p : List.of(giga1, giga1.south(), giga1.east().south())) {
                CryoJarBlock.place(helper.getLevel(), helper.absolutePos(p), Direction.SOUTH, ModBlocks.CRYO_SOURCE_JAR.get());
            }
        });

        helper.runAfterDelay(15, () -> {
            CryoJarBlockEntity whole1 = helper.getBlockEntity(giga1, CryoJarBlockEntity.class);
            helper.assertTrue(whole1.form() == JarForm.GIGA, "first Giga Jar formed");
            whole1.fill(30000, false);

            AtlasCoreBlockEntity core = helper.getBlockEntity(CORE, AtlasCoreBlockEntity.class);
            core.refresh(helper.getLevel(), helper.absolutePos(CORE));
            GolemNetwork n1 = GolemNetwork.get(helper.getLevel(), helper.absolutePos(ORIGIN));
            helper.assertTrue(n1.hasGigaJar() && n1.gigaJarCount() == 1 && !n1.hasReactor(), "1 Giga Jar detected");
            helper.assertTrue(n1.sourceDiscountPercent() == discount, "discount should be " + discount + ", got " + n1.sourceDiscountPercent());
            helper.assertTrue(n1.channelBonusPercent() == 0 && n1.lineCapacity() == 64 && n1.channelCapacity() == 320,
                    "Giga Jar must NOT boost channels: line=" + n1.lineCapacity() + ", total=" + n1.channelCapacity());
            helper.assertTrue(core.hasGigaJar() && !core.hasReactor() && core.getGigaDiscountPercent() == discount,
                    "core synced Giga Jar discount");

            if (SourceHelper.sourceRequired()) {
                core.sourceDiscount().reset();
                long before = totalAvailable(n1);
                for (int i = 0; i < 100; i++) {
                    helper.assertTrue(n1.consumeSource(1), "1 Giga Jar 1-cost drain " + i);
                }
                long drained1 = before - totalAvailable(n1);
                helper.assertTrue(drained1 == expectedPer100,
                        "1 Giga Jar: 100 x 1 must drain " + expectedPer100 + ", got " + drained1);

                before = totalAvailable(n1);
                for (int i = 0; i < 100; i++) {
                    helper.assertTrue(n1.consumeSource(2), "1 Giga Jar 2-cost drain " + i);
                }
                long drained2 = before - totalAvailable(n1);
                helper.assertTrue(drained2 == expectedPer200,
                        "1 Giga Jar: 100 x 2 must drain " + expectedPer200 + ", got " + drained2);

                // Liquid Cryo-Source synthesis also receives the -7% discount
                int cryoPerMb = GolemConfig.CRYO_SOURCE_COST.get();
                if (cryoPerMb > 0) {
                    before = totalAvailable(n1);
                    FluidStack got = n1.extractFluid(CryoSource.stack(1), 1000, false);
                    helper.assertTrue(got.getAmount() == 1000, "extracted 1000 mB Liquid Cryo-Source");
                    long drainedCryo = before - totalAvailable(n1);
                    long expectCryo = (long) expectedPer1000 * cryoPerMb;
                    helper.assertTrue(drainedCryo == expectCryo,
                            "1000 mB Cryo-Source must drain " + expectCryo + ", got " + drainedCryo);
                }
            }

            // Place a 2nd Giga Jar touching corner bushing (4,1,4) at (2,1,3)..(3,1,4)
            placeJarSquare(helper, new BlockPos(2, 1, 3), 2);
        });

        helper.runAfterDelay(25, () -> {
            BlockPos giga2 = new BlockPos(2, 1, 3);
            CryoJarBlockEntity whole1 = helper.getBlockEntity(giga1, CryoJarBlockEntity.class);
            CryoJarBlockEntity whole2 = helper.getBlockEntity(giga2, CryoJarBlockEntity.class);
            helper.assertTrue(whole2.form() == JarForm.GIGA, "second Giga Jar formed");
            whole1.fill(Integer.MAX_VALUE, false);
            whole2.fill(Integer.MAX_VALUE, false);

            AtlasCoreBlockEntity core = helper.getBlockEntity(CORE, AtlasCoreBlockEntity.class);
            core.refresh(helper.getLevel(), helper.absolutePos(CORE));
            GolemNetwork n2 = GolemNetwork.get(helper.getLevel(), helper.absolutePos(ORIGIN));
            helper.assertTrue(n2.gigaJarCount() == 2 && n2.sources().size() == 2,
                    "both Giga Jars connected as 2 storage suppliers, got count=" + n2.gigaJarCount() + " sources=" + n2.sources().size());
            long expectedCap = 2L * JarMerge.capacity(JarForm.GIGA);
            helper.assertTrue(totalAvailable(n2) >= expectedCap - 5,
                    "storage must stack across 2 Giga Jars: " + totalAvailable(n2) + " vs " + expectedCap);
            helper.assertTrue(n2.sourceDiscountPercent() == discount,
                    "2 Giga Jars must NOT stack discount: expected " + discount + ", got " + n2.sourceDiscountPercent());

            if (SourceHelper.sourceRequired()) {
                core.sourceDiscount().reset();
                long before = totalAvailable(n2);
                for (int i = 0; i < 100; i++) {
                    helper.assertTrue(n2.consumeSource(1), "2 Giga Jars 1-cost drain " + i);
                }
                long drained = before - totalAvailable(n2);
                helper.assertTrue(drained == expectedPer100,
                        "2 Giga Jars: 100 x 1 must STILL drain " + expectedPer100 + " (non-stacking), got " + drained);
            }
            helper.succeed();
        });
    }

    /** Cryo-Distillation Tower right beside a Giga Jar receives ZERO +7% distillation income bonus. */
    private static void gigaNoDistillationBonus(GameTestHelper helper) {
        BlockPos giga = new BlockPos(3, 1, 3);
        BlockPos towerPos = new BlockPos(2, 1, 3);
        placeJarSquare(helper, giga, 2);
        DistillationTowerBlock.place(helper.getLevel(), helper.absolutePos(towerPos), Direction.SOUTH,
                ModBlocks.CRYO_DISTILLATION_TOWER.get());

        var bloodTag = BuiltInRegistries.FLUID.get(ModTags.CRYO_DISTILLABLE);
        var fluid = bloodTag.flatMap(t -> t.stream().map(Holder::value)
                .filter(f -> f.isSource(f.defaultFluidState())).findFirst()).orElse(null);

        helper.runAfterDelay(5, () -> {
            CryoJarBlockEntity whole = helper.getBlockEntity(giga, CryoJarBlockEntity.class);
            helper.assertTrue(whole.form() == JarForm.GIGA, "Giga Jar formed beside tower");
            DistillationTowerBlockEntity tower = helper.getBlockEntity(towerPos, DistillationTowerBlockEntity.class);
            int expectedShown = (int) Math.round(tower.rate() * GolemConfig.DISTILL_SOURCE_PER_MB.get() * 10.0);
            helper.assertTrue(tower.menuData().get(2) == expectedShown,
                    "tower display rate must not include removed +7% bonus: " + tower.menuData().get(2) + " vs " + expectedShown);
            if (fluid != null) {
                Transfers.insertFluid(tower.inputResources(), new FluidStack(fluid, 100), false);
            }
        });

        helper.runAfterDelay(130, () -> {
            DistillationTowerBlockEntity tower = helper.getBlockEntity(towerPos, DistillationTowerBlockEntity.class);
            CryoJarBlockEntity whole = helper.getBlockEntity(giga, CryoJarBlockEntity.class);
            if (fluid != null) {
                helper.assertTrue(tower.blood().isEmpty(), "blood distilled");
                int expected = (int) Math.floor(100 * GolemConfig.DISTILL_SOURCE_PER_MB.get() + 1e-9);
                helper.assertTrue(whole.available() == expected,
                        "distilling 100 mB beside a Giga Jar must yield exact base " + expected + " Source, got " + whole.available());
            }
            helper.succeed();
        });
    }

    /**
     * 0 vs 1 vs 2 Antigravity Reactors on a base Atlas Array (64 -> 80 per line & Dense Frost Pipe, 320 -> 400 total):
     * - 88 devices behind ONE Dense Frost Pipe on ONE Cable Bushing: 64 active without Reactor, 80 active IMMEDIATELY upon forming Reactor!
     * - Forming via the far corner (not touching pipe) still recalculates immediately without waiting for core tick.
     * - 2nd Reactor stacks storage capacity without stacking the +25% channel bonus.
     * - Channel Counter updates to 80; breaking the Reactors immediately drops devices #65-#80 back offline.
     */
    private static void reactor64To80AndImmediateRecalc(GameTestHelper helper) {
        buildArray(helper, 0, 0);
        // Creative Source Jar under center bushing (5,0,5) so power is always on
        helper.setBlock(ORIGIN.below(), ModBlocks.CREATIVE_SOURCE_JAR.get());

        // Single Dense Frost Pipe spine of length 7 east of corner bushing (6,1,6): 4 + 6*14 = 88 terminals!
        BlockPos denseEntry = new BlockPos(7, 1, 6);
        List<BlockPos> terminals = placeDenseComb(helper, 7, 6, 1, 0, 7);

        // Channel Counter mounted south of the dense entry pipe (7,1,6) -> placed at (7,1,7) facing SOUTH
        BlockPos counterPos = new BlockPos(7, 1, 7);
        helper.setBlock(counterPos, ModBlocks.CHANNEL_COUNTER.get().defaultBlockState()
                .setValue(com.golemlogix.blocks.ChannelCounterBlock.FACING, Direction.SOUTH)
                .setValue(com.golemlogix.blocks.ChannelCounterBlock.MOUNT, com.golemlogix.blocks.ChannelCounterBlock.Mount.SIDE_DENSE));

        // 8 of 9 jars of Reactor #1 at (1,1,4)..(3,1,6) — only (3,1,6) & (3,1,4) touch bushings (4,1,6)/(4,1,4);
        // leave the FAR corner (1,1,4) empty for now so placing it forms the Reactor from a non-touching cell!
        BlockPos reactor1 = new BlockPos(1, 1, 4);
        for (int x = 0; x < 3; x++) {
            for (int z = 0; z < 3; z++) {
                if (x == 0 && z == 0) {
                    continue;
                }
                CryoJarBlock.place(helper.getLevel(), helper.absolutePos(reactor1.offset(x, 0, z)), Direction.SOUTH,
                        ModBlocks.CRYO_SOURCE_JAR.get());
            }
        }

        helper.runAfterDelay(5, () -> {
            AtlasCoreBlockEntity core = helper.getBlockEntity(CORE, AtlasCoreBlockEntity.class);
            core.refresh(helper.getLevel(), helper.absolutePos(CORE));
            GolemNetwork n0 = GolemNetwork.get(helper.getLevel(), helper.absolutePos(ORIGIN));
            helper.assertTrue(n0.isOnline(), "network online");
            helper.assertTrue(!n0.hasReactor() && n0.channelBonusPercent() == 0, "no Reactor yet");
            helper.assertTrue(n0.lineCapacity() == 64 && n0.channelCapacity() == 320,
                    "base array 64/320, got " + n0.lineCapacity() + "/" + n0.channelCapacity());
            helper.assertTrue(n0.capacityAt(helper.absolutePos(denseEntry)) == 64,
                    "base dense pipe cap 64, got " + n0.capacityAt(helper.absolutePos(denseEntry)));
            helper.assertTrue(n0.channelsUsed() == 64, "base 64 devices granted, got " + n0.channelsUsed());
            helper.assertTrue(countLit(helper, terminals) == 64, "64 terminals lit before Reactor, got " + countLit(helper, terminals));

            // Place the 9th jar at the FAR corner (1,1,4) to form Reactor #1
            CryoJarBlock.place(helper.getLevel(), helper.absolutePos(reactor1), Direction.SOUTH, ModBlocks.CRYO_SOURCE_JAR.get());
        });

        // Only 3 ticks later (BEFORE the next 20-tick core update!) — must ALREADY be recalculated and lit!
        helper.runAfterDelay(8, () -> {
            CryoJarBlockEntity r1 = helper.getBlockEntity(reactor1, CryoJarBlockEntity.class);
            helper.assertTrue(r1.form() == JarForm.SUPER, "Reactor #1 formed");
            GolemNetwork n1 = GolemNetwork.get(helper.getLevel(), helper.absolutePos(ORIGIN));
            helper.assertTrue(n1.hasReactor() && n1.reactorCount() == 1 && !n1.hasGigaJar(), "1 Reactor detected, no Giga Jar");
            helper.assertTrue(n1.sourceDiscountPercent() == 0, "Reactor must NOT grant Giga discount");
            helper.assertTrue(n1.lineCapacity() == 80 && n1.channelCapacity() == 400,
                    "Reactor boosts 64->80 / 320->400, got " + n1.lineCapacity() + "/" + n1.channelCapacity());
            helper.assertTrue(n1.capacityAt(helper.absolutePos(denseEntry)) == 80,
                    "Dense Frost Pipe scales 64->80 (zero bottleneck), got " + n1.capacityAt(helper.absolutePos(denseEntry)));
            helper.assertTrue(n1.channelsUsed() == 80, "80 of 88 devices online through single Dense Pipe, got " + n1.channelsUsed());
            helper.assertTrue(countLit(helper, terminals) == 80,
                    "devices #65-#80 must light up immediately, lit=" + countLit(helper, terminals));

            // Place a 2nd Antigravity Reactor connected to corner bushing (6,1,4) via north at (6,1,1)..(8,1,3)
            placeJarSquare(helper, new BlockPos(6, 1, 1), 3);
        });

        // Wait 25 ticks so Channel Counter ticks and 2nd Reactor forms
        helper.runAfterDelay(35, () -> {
            BlockPos reactor2 = new BlockPos(6, 1, 1);
            CryoJarBlockEntity r1 = helper.getBlockEntity(reactor1, CryoJarBlockEntity.class);
            CryoJarBlockEntity r2 = helper.getBlockEntity(reactor2, CryoJarBlockEntity.class);
            helper.assertTrue(r2.form() == JarForm.SUPER, "Reactor #2 formed");
            r1.fill(Integer.MAX_VALUE, false);
            r2.fill(Integer.MAX_VALUE, false);

            GolemNetwork n2 = GolemNetwork.get(helper.getLevel(), helper.absolutePos(ORIGIN));
            helper.assertTrue(n2.reactorCount() == 2, "2 Reactors connected, got " + n2.reactorCount());
            long jarStorage = (long) r1.available() + r2.available();
            helper.assertTrue(jarStorage == 2L * JarMerge.capacity(JarForm.SUPER),
                    "storage stacks across 2 Reactors: " + jarStorage);
            helper.assertTrue(n2.lineCapacity() == 80 && n2.channelCapacity() == 400 && n2.channelsUsed() == 80,
                    "2 Reactors must NOT stack channel bonus: line=" + n2.lineCapacity() + ", used=" + n2.channelsUsed());

            ChannelCounterBlockEntity counter = helper.getBlockEntity(counterPos, ChannelCounterBlockEntity.class);
            helper.assertTrue(counter.online() && counter.used() == 80 && counter.capacity() == 80,
                    "Channel Counter on Dense Pipe must read 80/80, got " + counter.used() + "/" + counter.capacity());

            // Break the middle jar of BOTH Reactors (non-touching cells!) -> both immediately unform into singles
            helper.getLevel().destroyBlock(helper.absolutePos(reactor1.offset(1, 0, 1)), false);
            helper.getLevel().destroyBlock(helper.absolutePos(reactor2.offset(1, 0, 1)), false);
        });

        // Immediately after breaking (2 ticks later, BEFORE 20-tick core tick!) -> back to 64!
        helper.runAfterDelay(37, () -> {
            GolemNetwork nAfter = GolemNetwork.get(helper.getLevel(), helper.absolutePos(ORIGIN));
            helper.assertTrue(!nAfter.hasReactor() && nAfter.reactorCount() == 0, "no Reactors remain");
            helper.assertTrue(nAfter.lineCapacity() == 64 && nAfter.channelCapacity() == 320,
                    "channels dropped back to 64/320, got " + nAfter.lineCapacity() + "/" + nAfter.channelCapacity());
            helper.assertTrue(nAfter.capacityAt(helper.absolutePos(denseEntry)) == 64,
                    "dense pipe dropped back to 64, got " + nAfter.capacityAt(helper.absolutePos(denseEntry)));
            helper.assertTrue(nAfter.channelsUsed() == 64, "64 devices online after breaking Reactors, got " + nAfter.channelsUsed());
            helper.assertTrue(countLit(helper, terminals) == 64,
                    "devices #65-#80 must go offline immediately, lit=" + countLit(helper, terminals));
            helper.succeed();
        });
    }

    /**
     * Tests 128 -> 160 (2 Basic + 1 Quantum Expander line & Quantum Expander exit),
     * 64 -> 80 (Basic Expander exit), 16 -> 20 (Basic Snow Pipe & Starter Tower), and 8 -> 10 (Bare Core).
     */
    private static void reactor128To160AndAllTiers(GameTestHelper helper) {
        // Array with 2 Basic Expanders + 1 Quantum Expander: base line = 64 + 32 + 32 = 128, total = 640
        buildArray(helper, 2, 1);
        helper.setBlock(ORIGIN.below(), ModBlocks.CREATIVE_SOURCE_JAR.get());

        // Two independent dense combs off corner bushing (6,1,6): 88 east + 88 south = 176 devices
        placeDenseComb(helper, 7, 6, 1, 0, 7);
        placeDenseComb(helper, 6, 7, 0, 1, 7);

        // Basic Snow Pipe branch off corner bushing (4,1,4) to the north: 22 terminals behind 1 basic pipe
        BlockPos basicPipe = new BlockPos(4, 1, 3);
        helper.setBlock(basicPipe, ModBlocks.BASIC_SNOW_PIPE.get());
        helper.setBlock(new BlockPos(4, 1, 2), ModBlocks.DENSE_FROST_PIPE.get());
        for (int i = 0; i < 2; i++) {
            BlockPos spine = new BlockPos(4, 1, 2 - i);
            helper.setBlock(spine, ModBlocks.DENSE_FROST_PIPE.get());
            for (int y = 2; y <= 5; y++) helper.setBlock(spine.atY(y), ModBlocks.ANALOG_TERMINAL.get());
            if (i > 0) {
                for (int side = -1; side <= 1; side += 2) {
                    for (int y = 1; y <= 5; y++) {
                        helper.setBlock(spine.offset(side, y - 1, 0), ModBlocks.ANALOG_TERMINAL.get());
                    }
                }
            }
        }
        // Extra 4 terminals on the dense pipe at (4,1,2) sides -> total 4 + 4 + 10 + 4 = 22 behind basicPipe
        helper.setBlock(new BlockPos(3, 1, 2), ModBlocks.ANALOG_TERMINAL.get());
        helper.setBlock(new BlockPos(5, 1, 2), ModBlocks.ANALOG_TERMINAL.get());
        helper.setBlock(new BlockPos(3, 2, 2), ModBlocks.ANALOG_TERMINAL.get());
        helper.setBlock(new BlockPos(5, 2, 2), ModBlocks.ANALOG_TERMINAL.get());

        // Locate the first basic expander and quantum expander placed by buildArray(2, 1)
        BlockPos basicExp = ORIGIN.offset(-1, 1, 0);
        BlockPos quantumExp = ORIGIN.offset(0, 1, 1);

        // Separate bare core at (1, 10, 1) and starter tower at (10, 10, 1)
        BlockPos bareCore = new BlockPos(1, 11, 1);
        helper.setBlock(bareCore, ModBlocks.ATLAS_CORE.get());
        helper.setBlock(bareCore.below(), ModBlocks.CABLE_BUSHING.get());
        helper.setBlock(bareCore.below(2), ModBlocks.CREATIVE_SOURCE_JAR.get());

        BlockPos towerBase = new BlockPos(10, 10, 1);
        helper.setBlock(towerBase, ModBlocks.CABLE_BUSHING.get());
        helper.setBlock(towerBase.above(), ModBlocks.ATLAS_CONDUIT.get());
        helper.setBlock(towerBase.above(2), ModBlocks.ATLAS_CONDUIT.get());
        helper.setBlock(towerBase.above(3), ModBlocks.ATLAS_CORE.get());
        helper.setBlock(towerBase.below(), ModBlocks.CREATIVE_SOURCE_JAR.get());

        helper.runAfterDelay(5, () -> {
            GolemNetwork n0 = GolemNetwork.refresh(helper.getLevel(), helper.absolutePos(ORIGIN));
            helper.assertTrue(n0.lineCapacity() == 128 && n0.channelCapacity() == 640,
                    "base 2B+1Q array: 128/640, got " + n0.lineCapacity() + "/" + n0.channelCapacity());
            helper.assertTrue(n0.loadAt(helper.absolutePos(new BlockPos(6, 1, 6))) == 128,
                    "128 devices active on 128-ch line, got " + n0.loadAt(helper.absolutePos(new BlockPos(6, 1, 6))));
            helper.assertTrue(n0.loadAt(helper.absolutePos(basicPipe)) == 16 && n0.capacityAt(helper.absolutePos(basicPipe)) == 16,
                    "basic pipe caps at 16 before Reactor, got " + n0.loadAt(helper.absolutePos(basicPipe)));
            helper.assertTrue(n0.exitCapacity(helper.absolutePos(basicExp)) == 64,
                    "basic expander exit 64, got " + n0.exitCapacity(helper.absolutePos(basicExp)));
            helper.assertTrue(n0.exitCapacity(helper.absolutePos(quantumExp)) == 128,
                    "quantum expander exit 128, got " + n0.exitCapacity(helper.absolutePos(quantumExp)));

            GolemNetwork bare0 = GolemNetwork.refresh(helper.getLevel(), helper.absolutePos(bareCore));
            GolemNetwork tower0 = GolemNetwork.refresh(helper.getLevel(), helper.absolutePos(towerBase));
            helper.assertTrue(bare0.channelCapacity() == 8, "bare core 8, got " + bare0.channelCapacity());
            helper.assertTrue(tower0.channelCapacity() == 16, "starter tower 16, got " + tower0.channelCapacity());

            // Connect a Reactor to each of the 3 networks
            placeJarSquare(helper, new BlockPos(1, 1, 6), 3);    // touches array bushing (4,1,6)
            placeJarSquare(helper, new BlockPos(2, 10, 1), 3);   // touches bare core bushing (1,10,1)
            placeJarSquare(helper, new BlockPos(11, 10, 1), 3);  // touches tower bushing (10,10,1)
        });

        helper.runAfterDelay(15, () -> {
            GolemNetwork n1 = GolemNetwork.get(helper.getLevel(), helper.absolutePos(ORIGIN));
            helper.assertTrue(n1.hasReactor(), "array sees Reactor");
            helper.assertTrue(n1.lineCapacity() == 160 && n1.channelCapacity() == 800,
                    "2B+1Q array with Reactor: 128->160 / 640->800, got " + n1.lineCapacity() + "/" + n1.channelCapacity());
            helper.assertTrue(n1.loadAt(helper.absolutePos(new BlockPos(6, 1, 6))) == 160,
                    "160 devices active on boosted line, got " + n1.loadAt(helper.absolutePos(new BlockPos(6, 1, 6))));
            helper.assertTrue(n1.loadAt(helper.absolutePos(basicPipe)) == 20 && n1.capacityAt(helper.absolutePos(basicPipe)) == 20,
                    "basic pipe scales 16->20, got " + n1.loadAt(helper.absolutePos(basicPipe)) + "/" + n1.capacityAt(helper.absolutePos(basicPipe)));
            helper.assertTrue(n1.exitCapacity(helper.absolutePos(basicExp)) == 80,
                    "basic expander exit scales 64->80, got " + n1.exitCapacity(helper.absolutePos(basicExp)));
            helper.assertTrue(n1.exitCapacity(helper.absolutePos(quantumExp)) == 160,
                    "quantum expander exit scales 128->160, got " + n1.exitCapacity(helper.absolutePos(quantumExp)));

            GolemNetwork bare1 = GolemNetwork.get(helper.getLevel(), helper.absolutePos(bareCore));
            GolemNetwork tower1 = GolemNetwork.get(helper.getLevel(), helper.absolutePos(towerBase));
            helper.assertTrue(bare1.channelCapacity() == 10 && bare1.lineCapacity() == 10,
                    "bare core scales 8->10, got " + bare1.channelCapacity());
            helper.assertTrue(tower1.channelCapacity() == 20 && tower1.lineCapacity() == 20,
                    "starter tower scales 16->20, got " + tower1.channelCapacity());
            helper.succeed();
        });
    }

    /**
     * Tier independence & coexistence:
     * - Reactor alone grants +25% channels and 0% Source discount.
     * - Reactor + Giga Jar together grant BOTH +25% channels AND -7% Source discount.
     * - Removing the Reactor leaves ONLY Giga Jar's -7% Source discount (channels return to 64/320).
     */
    private static void tierIndependenceAndCoexistence(GameTestHelper helper) {
        buildArray(helper, 0, 0);
        BlockPos reactorPos = new BlockPos(1, 1, 4); // touches corner bushing (4,1,4) & (4,1,6)
        BlockPos gigaPos = new BlockPos(7, 1, 5);    // touches corner bushing (6,1,6)
        placeJarSquare(helper, reactorPos, 3);

        int discount = GolemConfig.GIGA_SOURCE_DISCOUNT.get();
        int channelBonus = GolemConfig.REACTOR_CHANNEL_BONUS.get();
        int expectedDiscounted100 = 100 - discount;

        helper.runAfterDelay(5, () -> {
            CryoJarBlockEntity reactor = helper.getBlockEntity(reactorPos, CryoJarBlockEntity.class);
            helper.assertTrue(reactor.form() == JarForm.SUPER, "Reactor formed");
            reactor.fill(20000, false);

            AtlasCoreBlockEntity core = helper.getBlockEntity(CORE, AtlasCoreBlockEntity.class);
            core.refresh(helper.getLevel(), helper.absolutePos(CORE));
            GolemNetwork net = GolemNetwork.get(helper.getLevel(), helper.absolutePos(ORIGIN));
            helper.assertTrue(net.hasReactor() && !net.hasGigaJar(), "only Reactor connected");
            helper.assertTrue(net.channelBonusPercent() == channelBonus && net.lineCapacity() == 80 && net.channelCapacity() == 400,
                    "Reactor boosts channels to 80/400");
            helper.assertTrue(net.sourceDiscountPercent() == 0, "Reactor alone must NOT grant Giga discount");

            if (SourceHelper.sourceRequired()) {
                long before = totalAvailable(net);
                for (int i = 0; i < 100; i++) {
                    helper.assertTrue(net.consumeSource(1), "Reactor-only drain " + i);
                }
                helper.assertTrue(before - totalAvailable(net) == 100L,
                        "Reactor alone must drain full 100, got " + (before - totalAvailable(net)));
            }

            // Now add a Giga Jar alongside the Reactor
            placeJarSquare(helper, gigaPos, 2);
        });

        helper.runAfterDelay(15, () -> {
            CryoJarBlockEntity giga = helper.getBlockEntity(gigaPos, CryoJarBlockEntity.class);
            helper.assertTrue(giga.form() == JarForm.GIGA, "Giga Jar formed");
            giga.fill(10000, false);

            AtlasCoreBlockEntity core = helper.getBlockEntity(CORE, AtlasCoreBlockEntity.class);
            core.refresh(helper.getLevel(), helper.absolutePos(CORE));
            GolemNetwork net = GolemNetwork.get(helper.getLevel(), helper.absolutePos(ORIGIN));
            helper.assertTrue(net.hasReactor() && net.hasGigaJar(), "both Reactor and Giga Jar coexist");
            helper.assertTrue(net.channelBonusPercent() == channelBonus && net.lineCapacity() == 80 && net.channelCapacity() == 400,
                    "channels boosted to 80/400 during coexistence");
            helper.assertTrue(net.sourceDiscountPercent() == discount,
                    "Source discount active during coexistence: " + net.sourceDiscountPercent());
            helper.assertTrue(core.hasGigaJar() && core.hasReactor()
                            && core.getGigaDiscountPercent() == discount && core.getReactorBonusPercent() == channelBonus
                            && core.getChannelCapacity() == 400,
                    "AtlasCoreBlockEntity synced both bonuses");
            if (SourceHelper.sourceRequired()) {
                int baseCost = AtlasCoreBlockEntity.powerCost(net);
                String expectFmt = SourceDiscount.formatRate(SourceDiscount.effectiveCostHundredths(baseCost, discount));
                helper.assertTrue(core.formattedSourcePerSecond().equals(expectFmt),
                        "core formatted Source/s should be " + expectFmt + ", got " + core.formattedSourcePerSecond());
                core.sourceDiscount().reset();
                long before = totalAvailable(net);
                for (int i = 0; i < 100; i++) {
                    helper.assertTrue(net.consumeSource(1), "coexistence drain " + i);
                }
                helper.assertTrue(before - totalAvailable(net) == expectedDiscounted100,
                        "coexistence must drain " + expectedDiscounted100 + ", got " + (before - totalAvailable(net)));
            }

            // Break the Reactor center -> leaves only Giga Jar connected
            helper.getLevel().destroyBlock(helper.absolutePos(reactorPos.offset(1, 0, 1)), false);
        });

        helper.runAfterDelay(20, () -> {
            AtlasCoreBlockEntity core = helper.getBlockEntity(CORE, AtlasCoreBlockEntity.class);
            core.refresh(helper.getLevel(), helper.absolutePos(CORE));
            GolemNetwork net = GolemNetwork.get(helper.getLevel(), helper.absolutePos(ORIGIN));
            helper.assertTrue(!net.hasReactor() && net.hasGigaJar(), "only Giga Jar remains");
            helper.assertTrue(net.lineCapacity() == 64 && net.channelCapacity() == 320,
                    "channels back to 64/320 after Reactor removed");
            helper.assertTrue(net.sourceDiscountPercent() == discount, "Giga Jar discount still active");
            if (SourceHelper.sourceRequired()) {
                core.sourceDiscount().reset();
                long before = totalAvailable(net);
                for (int i = 0; i < 100; i++) {
                    helper.assertTrue(net.consumeSource(1), "post-reactor Giga drain " + i);
                }
                helper.assertTrue(before - totalAvailable(net) == expectedDiscounted100,
                        "Giga Jar alone must still drain " + expectedDiscounted100 + ", got " + (before - totalAvailable(net)));
            }
            helper.succeed();
        });
    }

    /** Plugging the pipe side facing a Reactor disconnects its channel bonus immediately; unplugging restores it. */
    private static void pipePlugTogglesReactorBonus(GameTestHelper helper) {
        buildArray(helper, 0, 0);
        helper.setBlock(ORIGIN.below(), ModBlocks.CREATIVE_SOURCE_JAR.get());
        BlockPos pipe = new BlockPos(3, 1, 6);
        helper.setBlock(pipe, ModBlocks.BASIC_SNOW_PIPE.get());
        placeJarSquare(helper, new BlockPos(0, 1, 5), 3); // east side x=2 touches pipe at (3,1,6)

        helper.runAfterDelay(5, () -> {
            GolemNetwork n1 = GolemNetwork.get(helper.getLevel(), helper.absolutePos(ORIGIN));
            helper.assertTrue(n1.hasReactor() && n1.lineCapacity() == 80 && n1.channelCapacity() == 400,
                    "Reactor connected through pipe gives 80/400");
            PipePlugItem.toggle(helper.getLevel(), helper.absolutePos(pipe), Direction.WEST, true);
        });

        helper.runAfterDelay(7, () -> {
            GolemNetwork n2 = GolemNetwork.get(helper.getLevel(), helper.absolutePos(ORIGIN));
            helper.assertTrue(!n2.hasReactor() && n2.lineCapacity() == 64 && n2.channelCapacity() == 320,
                    "plugging pipe side facing Reactor removes bonus immediately, got " + n2.lineCapacity());
            PipePlugItem.toggle(helper.getLevel(), helper.absolutePos(pipe), Direction.WEST, false);
        });

        helper.runAfterDelay(9, () -> {
            GolemNetwork n3 = GolemNetwork.get(helper.getLevel(), helper.absolutePos(ORIGIN));
            helper.assertTrue(n3.hasReactor() && n3.lineCapacity() == 80 && n3.channelCapacity() == 400,
                    "unplugging pipe side restores Reactor bonus immediately, got " + n3.lineCapacity());
            helper.succeed();
        });
    }
}
