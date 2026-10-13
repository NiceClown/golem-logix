package com.golemlogix.gametest;

import com.golemlogix.GolemLogixMod;
import com.golemlogix.blockentity.BusBlockEntity;
import com.golemlogix.blockentity.CopperDriveBayBlockEntity;
import com.golemlogix.blockentity.CryoCalculatorBlockEntity;
import com.golemlogix.blockentity.PatternProviderBlockEntity;
import com.golemlogix.blocks.AtlantBlock;
import com.golemlogix.blocks.CopperDriveBayBlock;
import com.golemlogix.crafting.CraftingPlan;
import com.golemlogix.crafting.CraftingPlanner;
import com.golemlogix.data.PatternData;
import com.golemlogix.init.ModBlocks;
import com.golemlogix.init.ModDataComponents;
import com.golemlogix.init.ModItems;
import com.golemlogix.menu.PatternEncoderMenu;
import com.golemlogix.menu.TerminalMenu;
import com.golemlogix.network.TerminalActionPayload;
import com.golemlogix.network.TerminalActionPayload.Action;
import com.golemlogix.recipe.ChronometerUpgradeRecipe;
import com.golemlogix.system.AtlasBlueprint;
import com.golemlogix.system.AtlasStructure;
import com.golemlogix.system.DiskStorage;
import com.golemlogix.system.DiskStore;
import com.golemlogix.system.GolemNetwork;
import com.golemlogix.system.ItemKey;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * Проверки логики мода внутри настоящего сервера: многоблок, сеть, диски, шины, автокрафт.
 * Запуск: gradlew runGameTestServer
 *
 * Раскладка: Решётка Атланта 3x3x4 с основанием в ORIGIN (ядро наверху в центре), Креативный Сосуд под центральной муфтой.
 * Наружу сеть выходит через муфты основания: BAY, CPU, PROV, TERM, BUS_IN, BUS_OUT — точки рядом с угловыми муфтами.
 */
@GameTestHolder(GolemLogixMod.MOD_ID)
@PrefixGameTestTemplate(false)
public class GolemGameTests {
    private static final String T = "empty";

    private static final BlockPos ORIGIN = new BlockPos(5, 1, 5);
    private static final BlockPos CORE = ORIGIN.offset(0, 3, 0);
    private static final BlockPos JAR = ORIGIN.below();
    private static final BlockPos BAY = new BlockPos(7, 1, 4);
    private static final BlockPos CPU = new BlockPos(4, 1, 3);
    private static final BlockPos PROV = new BlockPos(7, 1, 6);
    private static final BlockPos TERM = new BlockPos(3, 1, 6);
    private static final BlockPos BUS_IN = new BlockPos(3, 1, 4);
    private static final BlockPos BUS_OUT = new BlockPos(6, 1, 3);

    // ------------------------------------------------------------ помощники

    private static void put(GameTestHelper helper, BlockPos rel, Block block) {
        helper.setBlock(rel, block);
    }

    /** Строит решётку целиком; expander/quantum — сколько боковых ячеек занять расширителями (до 8). */
    private static void buildArray(GameTestHelper helper, int expanders, int quantum) {
        int slot = 0;
        for (int dy = 0; dy <= 3; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    BlockPos p = ORIGIN.offset(dx, dy, dz);
                    boolean corner = dx != 0 && dz != 0;
                    boolean center = dx == 0 && dz == 0;
                    switch (dy) {
                        case 0 -> put(helper, p, corner || center ? ModBlocks.CABLE_BUSHING.get() : ModBlocks.ATLAS_CONDUIT.get());
                        case 1, 2 -> {
                            if (corner) {
                                BlockState atl = ModBlocks.ATLAS_ATLANT.get().defaultBlockState()
                                        .setValue(AtlantBlock.HALF, dy == 1 ? DoubleBlockHalf.LOWER : DoubleBlockHalf.UPPER);
                                helper.setBlock(p, atl);
                            } else if (center) {
                                put(helper, p, ModBlocks.ATLAS_CONDUIT.get());
                            } else {
                                Block b = ModBlocks.ATLAS_CASING.get();
                                if (slot < expanders) {
                                    b = ModBlocks.CHANNEL_EXPANDER.get();
                                } else if (slot < expanders + quantum) {
                                    b = ModBlocks.QUANTUM_CHANNEL_EXPANDER.get();
                                }
                                slot++;
                                put(helper, p, b);
                            }
                        }
                        default -> put(helper, p, center ? ModBlocks.ATLAS_CORE.get() : ModBlocks.ATLAS_CASING.get());
                    }
                }
            }
        }
    }

    private static void powered(GameTestHelper helper) {
        put(helper, JAR, ModBlocks.CREATIVE_SOURCE_JAR.get());
    }

    /** Шкаф 2x2: главная часть в rel, лицом на юг — остальные части справа (на восток) и выше. */
    private static void putBay(GameTestHelper helper, BlockPos rel) {
        CopperDriveBayBlock.place(helper.getLevel(), helper.absolutePos(rel), Direction.SOUTH);
    }

    /** Полная сеть: решётка + сосуд + шкаф с диском 1k. */
    private static BlockPos baseNetwork(GameTestHelper helper) {
        buildArray(helper, 0, 0);
        powered(helper);
        putBay(helper, BAY);
        CopperDriveBayBlockEntity be = (CopperDriveBayBlockEntity) helper.getBlockEntity(BAY);
        be.getContainer().setItem(0, new ItemStack(ModItems.COPPER_DISK_1K.get()));
        return BAY;
    }

    /**
     * Игрок для проверок окон и кликов: FakePlayer NeoForge в начале площадки. «Манекен» GameTest входит в список игроков, и моды
     * вроде Curios шлют ему пакеты, которые его соединение не принимает; FakePlayer пакеты молча глотает.
     */
    private static ServerPlayer testPlayer(GameTestHelper helper) {
        var profile = new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "golem-logix-test");
        ServerPlayer player = net.neoforged.neoforge.common.util.FakePlayerFactory.get(helper.getLevel(), profile);
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        player.moveTo(origin.getX() + 0.5, origin.getY() + 1, origin.getZ() + 0.5, 0f, 0f);
        return player;
    }

    private static GolemNetwork net(GameTestHelper helper, BlockPos rel) {
        return GolemNetwork.scan(helper.getLevel(), helper.absolutePos(rel));
    }

    // ----------------------------------------------------------- многоблок

    @GameTest(template = T, timeoutTicks = 100)
    public static void arrayStructure(GameTestHelper helper) {
        buildArray(helper, 3, 2);
        helper.runAfterDelay(2, () -> {
            var level = helper.getLevel();
            AtlasStructure.Result r = AtlasStructure.check(level, helper.absolutePos(CORE));
            helper.assertTrue(r.valid(), "complete array must be valid, missing " + r.missingPos());
            helper.assertTrue(r.expanders() == 3 && r.quantumExpanders() == 2, "expanders counted: " + r.expanders() + "/" + r.quantumExpanders());

            // убрали корпус -> решётка не валидна, подсказка указывает на дыру
            BlockPos hole = ORIGIN.offset(1, 3, 0);
            helper.setBlock(hole, Blocks.AIR);
            AtlasStructure.Result broken = AtlasStructure.check(level, helper.absolutePos(CORE));
            helper.assertTrue(!broken.valid(), "hole in the crown");
            helper.assertTrue(helper.absolutePos(hole).equals(broken.missingPos()), "hint points at the hole");
            helper.assertTrue(broken.expected() == ModBlocks.ATLAS_CASING.get(), "hint names the casing");
            put(helper, hole, ModBlocks.ATLAS_CASING.get());

            // Атлант нужной половиной на нужном уровне
            BlockPos corner = ORIGIN.offset(1, 2, 1);
            helper.setBlock(corner, ModBlocks.ATLAS_ATLANT.get().defaultBlockState().setValue(AtlantBlock.HALF, DoubleBlockHalf.LOWER));
            helper.assertTrue(!AtlasStructure.check(level, helper.absolutePos(CORE)).valid(), "wrong atlant half is rejected");
            // подмена половины разрушает пару, как у двери; ставим обе заново
            helper.setBlock(corner.below(), ModBlocks.ATLAS_ATLANT.get().defaultBlockState().setValue(AtlantBlock.HALF, DoubleBlockHalf.LOWER));
            helper.setBlock(corner, ModBlocks.ATLAS_ATLANT.get().defaultBlockState().setValue(AtlantBlock.HALF, DoubleBlockHalf.UPPER));
            helper.assertTrue(AtlasStructure.check(level, helper.absolutePos(CORE)).valid(), "fixed again");

            // расширитель вне боковых ячеек (на углу) не годится
            helper.setBlock(ORIGIN.offset(1, 1, 1), ModBlocks.CHANNEL_EXPANDER.get().defaultBlockState());
            helper.assertTrue(!AtlasStructure.check(level, helper.absolutePos(CORE)).valid(), "expander cannot replace an atlant");
            helper.succeed();
        });
    }

    @GameTest(template = T, timeoutTicks = 100)
    public static void arrayCapacity(GameTestHelper helper) {
        buildArray(helper, 0, 0);
        powered(helper);
        helper.runAfterDelay(3, () -> {
            GolemNetwork n = net(helper, ORIGIN);
            helper.assertTrue(n.structure().valid(), "valid structure");
            helper.assertTrue(n.lineCapacity() == 64 && n.channelCapacity() == 320, "base array: 5 lines x 64 = 320, got " + n.channelCapacity());
            helper.assertTrue(n.isOnline(), "online");

            buildArray(helper, 2, 1);
            GolemNetwork n2 = net(helper, ORIGIN);
            helper.assertTrue(n2.lineCapacity() == 64 + 2 * 16 + 32, "line 128, got " + n2.lineCapacity());
            helper.assertTrue(n2.channelCapacity() == 5 * 128, "total 640, got " + n2.channelCapacity());

            buildArray(helper, 8, 0);
            helper.assertTrue(net(helper, ORIGIN).channelCapacity() == 5 * 192, "8 basic expanders: 960");
            buildArray(helper, 0, 8);
            helper.assertTrue(net(helper, ORIGIN).channelCapacity() == 5 * 320, "8 quantum expanders: 1600");
            helper.succeed();
        });
    }

    @GameTest(template = T, timeoutTicks = 100)
    public static void bareTowerStart(GameTestHelper helper) {
        // «башня» без решётки: ядро, две шины, центральная муфта — 8 каналов на старте
        put(helper, CORE, ModBlocks.ATLAS_CORE.get());
        put(helper, ORIGIN.offset(0, 2, 0), ModBlocks.ATLAS_CONDUIT.get());
        put(helper, ORIGIN.offset(0, 1, 0), ModBlocks.ATLAS_CONDUIT.get());
        put(helper, ORIGIN, ModBlocks.CABLE_BUSHING.get());
        powered(helper);
        for (int i = 1; i <= 9; i++) {
            put(helper, ORIGIN.east(i), ModBlocks.ANALOG_TERMINAL.get());
        }
        helper.runAfterDelay(5, () -> {
            GolemNetwork n = net(helper, ORIGIN);
            helper.assertTrue(!n.structure().valid(), "no array yet");
            helper.assertTrue(n.channelCapacity() == GolemNetwork.BARE_CORE_CHANNELS, "bare capacity " + n.channelCapacity());
            helper.assertTrue(n.channelsUsed() == 8, "8 of 9 terminals work, got " + n.channelsUsed());
            helper.assertTrue(!n.isDeviceActive(helper.absolutePos(ORIGIN.east(9))), "9th terminal inactive");
            helper.assertTrue(n.isOnline(), "powered");
            helper.succeed();
        });
    }

    @GameTest(template = T, timeoutTicks = 100)
    public static void storageNeedsChannel(GameTestHelper helper) {
        // башня на 8 каналов: 8 терминалов заняли все, шкаф девятым остался без канала — хранилищем он не считается
        put(helper, CORE, ModBlocks.ATLAS_CORE.get());
        put(helper, ORIGIN.offset(0, 2, 0), ModBlocks.ATLAS_CONDUIT.get());
        put(helper, ORIGIN.offset(0, 1, 0), ModBlocks.ATLAS_CONDUIT.get());
        put(helper, ORIGIN, ModBlocks.CABLE_BUSHING.get());
        powered(helper);
        for (int i = 1; i <= 8; i++) {
            put(helper, ORIGIN.east(i), ModBlocks.ANALOG_TERMINAL.get());
        }
        BlockPos bay = ORIGIN.east(9);
        putBay(helper, bay);
        ((CopperDriveBayBlockEntity) helper.getBlockEntity(bay)).getContainer().setItem(0, new ItemStack(ModItems.COPPER_DISK_1K.get()));
        helper.runAfterDelay(5, () -> {
            GolemNetwork n = net(helper, ORIGIN);
            helper.assertTrue(n.isOnline(), "powered");
            helper.assertTrue(!n.isDeviceActive(helper.absolutePos(bay)), "the cabinet got no channel");
            helper.assertTrue(n.itemCapacity() == 0, "a cabinet without a channel adds no capacity, got " + n.itemCapacity());
            helper.assertTrue(n.insertItem(new ItemStack(Items.DIAMOND, 10), false).getCount() == 10, "nothing is stored without a channel");
            // убрали один терминал -> канал освободился, шкаф заработал
            helper.setBlock(ORIGIN.east(8), ModBlocks.BASIC_SNOW_PIPE.get());
            GolemNetwork n2 = net(helper, ORIGIN);
            helper.assertTrue(n2.isDeviceActive(helper.absolutePos(bay)), "the cabinet works once a channel is free");
            helper.assertTrue(n2.insertItem(new ItemStack(Items.DIAMOND, 10), false).isEmpty(), "and stores items");
            helper.succeed();
        });
    }

    @GameTest(template = T, timeoutTicks = 40)
    public static void pipesAndBusesAreSteps(GameTestHelper helper) {
        // трубы и шины — ступеньки: столкновения не выше половины блока (на них заходят без прыжка), рамка выделения прежняя
        BlockPos dense = new BlockPos(1, 1, 2), basic = dense.east(), bus = basic.east(), lone = new BlockPos(1, 1, 5);
        put(helper, dense, ModBlocks.DENSE_FROST_PIPE.get());
        put(helper, basic, ModBlocks.BASIC_SNOW_PIPE.get());
        helper.setBlock(bus, ModBlocks.VACUUM_IMPORT_BUS.get().defaultBlockState().setValue(DirectionalBlock.FACING, Direction.EAST));
        helper.setBlock(lone, ModBlocks.INJECTOR_EXPORT_BUS.get().defaultBlockState().setValue(DirectionalBlock.FACING, Direction.EAST));
        helper.runAfterDelay(3, () -> {
            for (BlockPos rel : new BlockPos[]{dense, basic, bus, lone}) {
                BlockPos abs = helper.absolutePos(rel);
                BlockState st = helper.getLevel().getBlockState(abs);
                var collision = st.getCollisionShape(helper.getLevel(), abs);
                var outline = st.getShape(helper.getLevel(), abs);
                helper.assertTrue(!collision.isEmpty(), st.getBlock() + " has a step to stand on");
                helper.assertTrue(collision.max(Direction.Axis.Y) <= 0.5 + 1e-6, st.getBlock() + " collision top " + collision.max(Direction.Axis.Y));
                helper.assertTrue(outline.max(Direction.Axis.Y) > 0.5, st.getBlock() + " outline keeps its full height");
            }
            // шина без соседей сзади — в передней половине блока (сзади лишь короткий разъём)
            var shape = helper.getLevel().getBlockState(helper.absolutePos(lone)).getShape(helper.getLevel(), helper.absolutePos(lone));
            helper.assertTrue(shape.min(Direction.Axis.X) >= 0.42, "lone bus fits in the front half, starts at x=" + shape.min(Direction.Axis.X));
            helper.succeed();
        });
    }

    @GameTest(template = T, timeoutTicks = 100)
    public static void onlyDevicesWithChannelLookActive(GameTestHelper helper) {
        // башня на 8 каналов и 10 терминалов: девятый и десятый не должны «гореть» (раньше флаг получали все узлы)
        put(helper, CORE, ModBlocks.ATLAS_CORE.get());
        put(helper, ORIGIN.offset(0, 2, 0), ModBlocks.ATLAS_CONDUIT.get());
        put(helper, ORIGIN.offset(0, 1, 0), ModBlocks.ATLAS_CONDUIT.get());
        put(helper, ORIGIN, ModBlocks.CABLE_BUSHING.get());
        powered(helper);
        for (int i = 1; i <= 10; i++) {
            put(helper, ORIGIN.east(i), ModBlocks.ANALOG_TERMINAL.get());
        }
        helper.runAfterDelay(5, () -> {
            var core = (com.golemlogix.blockentity.AtlasCoreBlockEntity) helper.getBlockEntity(CORE);
            core.refresh(helper.getLevel(), helper.absolutePos(CORE));
            int lit = 0;
            for (int i = 1; i <= 10; i++) {
                if (helper.getLevel().getBlockState(helper.absolutePos(ORIGIN.east(i))).getValue(com.golemlogix.blocks.GolemProperties.ACTIVE)) {
                    lit++;
                }
            }
            helper.assertTrue(lit == 8, "only the 8 terminals with a channel look active, lit " + lit);
            helper.succeed();
        });
    }

    @GameTest(template = T, timeoutTicks = 100)
    public static void pipePlugSeparatesLines(GameTestHelper helper) {
        buildArray(helper, 0, 0);
        powered(helper);
        // линия A: муфта-угол (4,1,4) -> труба (3,1,4) -> терминал; линия B касается A трубой (3,1,3) и ведёт к своему терминалу
        BlockPos a = new BlockPos(3, 1, 4), b = new BlockPos(3, 1, 3);
        put(helper, a, ModBlocks.BASIC_SNOW_PIPE.get());
        put(helper, new BlockPos(2, 1, 4), ModBlocks.ANALOG_TERMINAL.get());
        put(helper, b, ModBlocks.BASIC_SNOW_PIPE.get());
        put(helper, new BlockPos(3, 1, 2), ModBlocks.BASIC_SNOW_PIPE.get());
        BlockPos t2 = new BlockPos(3, 1, 1);
        put(helper, t2, ModBlocks.ANALOG_TERMINAL.get());
        helper.runAfterDelay(5, () -> {
            helper.assertTrue(net(helper, ORIGIN).isDeviceActive(helper.absolutePos(t2)), "line B joins line A without a plug");
            com.golemlogix.items.PipePlugItem.toggle(helper.getLevel(), helper.absolutePos(a), Direction.NORTH, true);
            helper.assertTrue(!net(helper, ORIGIN).contains(helper.absolutePos(t2)), "the plug splits the lines");
            var sa = helper.getLevel().getBlockState(helper.absolutePos(a));
            var sb = helper.getLevel().getBlockState(helper.absolutePos(b));
            helper.assertTrue(com.golemlogix.blocks.SnowPipeBlock.link(sa, Direction.NORTH) == com.golemlogix.blocks.SnowPipeBlock.Link.NONE
                    && com.golemlogix.blocks.SnowPipeBlock.link(sb, Direction.SOUTH) == com.golemlogix.blocks.SnowPipeBlock.Link.NONE, "both pipes show a closed end");
            com.golemlogix.items.PipePlugItem.toggle(helper.getLevel(), helper.absolutePos(a), Direction.NORTH, false);
            helper.assertTrue(net(helper, ORIGIN).isDeviceActive(helper.absolutePos(t2)), "removing the plug joins them again");
            helper.succeed();
        });
    }

    @GameTest(template = T, timeoutTicks = 100)
    public static void expanderFaceIsAnExit(GameTestHelper helper) {
        // расширитель в стене (4,2,5): к его наружной грани — плотная труба и три терминала
        buildArray(helper, 1, 0);
        powered(helper);
        BlockPos expander = ORIGIN.offset(-1, 1, 0);
        put(helper, expander.west(), ModBlocks.DENSE_FROST_PIPE.get());
        put(helper, expander.west(2), ModBlocks.ANALOG_TERMINAL.get());
        put(helper, expander.west().north(), ModBlocks.ANALOG_TERMINAL.get());
        put(helper, expander.west().south(), ModBlocks.ANALOG_TERMINAL.get());
        helper.runAfterDelay(5, () -> {
            GolemNetwork n = net(helper, ORIGIN);
            helper.assertTrue(n.exitCapacity(helper.absolutePos(expander)) == 64, "expander exit is 64, got " + n.exitCapacity(helper.absolutePos(expander)));
            helper.assertTrue(n.isDeviceActive(helper.absolutePos(expander.west(2))), "a terminal behind the expander works");
            helper.assertTrue(n.loadAt(helper.absolutePos(expander)) >= 3, "three channels go out of the expander, got " + n.loadAt(helper.absolutePos(expander)));
            helper.succeed();
        });
    }

    @GameTest(template = T, timeoutTicks = 100)
    public static void conduitsStayInside(GameTestHelper helper) {
        // шина не соединяется с внешними блоками, труба рядом с ней не подключается
        buildArray(helper, 0, 0);
        powered(helper);
        BlockPos outside = ORIGIN.offset(2, 0, 0); // рядом с боковой шиной основания
        put(helper, outside, ModBlocks.ANALOG_TERMINAL.get());
        helper.runAfterDelay(5, () -> {
            GolemNetwork n = net(helper, ORIGIN);
            helper.assertTrue(n.channelsUsed() == 0, "terminal touching a conduit is not connected, used " + n.channelsUsed());
            helper.assertTrue(!n.isDeviceActive(helper.absolutePos(outside)), "not part of the network");
            // тот же терминал рядом с муфтой подключается
            helper.setBlock(outside, Blocks.AIR);
            put(helper, ORIGIN.offset(2, 0, 1), ModBlocks.ANALOG_TERMINAL.get());
            GolemNetwork n2 = net(helper, ORIGIN);
            helper.assertTrue(n2.channelsUsed() == 1, "terminal next to a bushing works, used " + n2.channelsUsed());
            helper.succeed();
        });
    }

    @GameTest(template = T, timeoutTicks = 100)
    public static void atlantPlacesAsDoubleBlock(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 1, 2);
        var level = helper.getLevel();
        BlockState lower = ModBlocks.ATLAS_ATLANT.get().defaultBlockState();
        helper.setBlock(pos, lower);
        helper.setBlock(pos.above(), lower.setValue(AtlantBlock.HALF, DoubleBlockHalf.UPPER));
        helper.runAfterDelay(2, () -> {
            helper.assertTrue(helper.getBlockState(pos.above()).is(ModBlocks.ATLAS_ATLANT.get()), "upper exists");
            // убрали верхнюю половину -> нижняя исчезает сама
            helper.setBlock(pos.above(), Blocks.AIR);
            helper.runAfterDelay(2, () -> {
                helper.assertTrue(helper.getBlockState(pos).isAir(), "the lower half goes with the upper one");
                helper.succeed();
            });
        });
    }

    // ------------------------------------------------------- хранение и сеть

    @GameTest(template = T, timeoutTicks = 200)
    public static void storageRoundTrip(GameTestHelper helper) {
        BlockPos bay = baseNetwork(helper);
        helper.runAfterDelay(5, () -> {
            GolemNetwork n = net(helper, bay);
            helper.assertTrue(n.hasCore(), "core not found");
            helper.assertTrue(n.isOnline(), "network must be powered by the creative jar");
            helper.assertTrue(n.isDeviceActive(helper.absolutePos(bay)), "bay has a channel");

            ItemStack rest = n.insertItem(new ItemStack(Items.DIAMOND, 10), false);
            helper.assertTrue(rest.isEmpty(), "10 diamonds must fit");
            helper.assertTrue(n.itemCountOf(ItemKey.of(new ItemStack(Items.DIAMOND))) == 10, "expected 10 diamonds");

            ItemStack got = n.extractItem(new ItemStack(Items.DIAMOND), 4, false);
            helper.assertTrue(got.getCount() == 4, "extracted 4");
            helper.assertTrue(n.itemCountOf(ItemKey.of(new ItemStack(Items.DIAMOND))) == 6, "6 diamonds left");

            CopperDriveBayBlockEntity be = (CopperDriveBayBlockEntity) helper.getBlockEntity(bay);
            helper.assertTrue(DiskStorage.storedItems(be.getDisks().get(0)) == 6, "disk STORED must be 6");

            ItemStack overflow = n.insertItem(new ItemStack(Items.STONE, 64), false);
            for (int i = 0; i < 20; i++) {
                overflow = n.insertItem(overflow.isEmpty() ? new ItemStack(Items.STONE, 64) : overflow, false);
            }
            helper.assertTrue(DiskStorage.storedItems(be.getDisks().get(0)) == 1000, "disk must be completely full");

            ItemStack sim = n.insertItem(new ItemStack(Items.DIAMOND, 5), true);
            helper.assertTrue(sim.getCount() == 5, "full network accepts nothing");
            helper.succeed();
        });
    }

    @GameTest(template = T, timeoutTicks = 200)
    public static void lineLimit(GameTestHelper helper) {
        buildArray(helper, 0, 0);
        powered(helper);
        // два плотных трубопровода от одной угловой муфты (6,1,6): на восток и на юг, за каждым плита из 48 терминалов
        put(helper, new BlockPos(7, 1, 6), ModBlocks.DENSE_FROST_PIPE.get());
        for (int x = 8; x <= 11; x++) for (int y = 1; y <= 4; y++) for (int z = 6; z <= 8; z++) put(helper, new BlockPos(x, y, z), ModBlocks.ANALOG_TERMINAL.get());
        put(helper, new BlockPos(6, 1, 7), ModBlocks.DENSE_FROST_PIPE.get());
        put(helper, new BlockPos(6, 1, 8), ModBlocks.DENSE_FROST_PIPE.get());
        for (int x = 4; x <= 7; x++) for (int y = 1; y <= 4; y++) for (int z = 9; z <= 11; z++) put(helper, new BlockPos(x, y, z), ModBlocks.ANALOG_TERMINAL.get());
        helper.runAfterDelay(5, () -> {
            GolemNetwork n = net(helper, ORIGIN);
            helper.assertTrue(n.lineCapacity() == 64, "line 64");
            helper.assertTrue(n.channelsUsed() == 64, "one bushing line passes only 64 channels, got " + n.channelsUsed());

            // два расширителя поднимают линию до 96 — через ту же муфту проходят все 96 устройств
            buildArray(helper, 2, 0);
            GolemNetwork n2 = net(helper, ORIGIN);
            helper.assertTrue(n2.lineCapacity() == 96, "line 96, got " + n2.lineCapacity());
            helper.assertTrue(n2.channelsUsed() == 96, "all 96 terminals work now, got " + n2.channelsUsed());
            helper.succeed();
        });
    }

    @GameTest(template = T, timeoutTicks = 200)
    public static void pipeLimit(GameTestHelper helper) {
        buildArray(helper, 0, 0);
        powered(helper);
        put(helper, new BlockPos(7, 1, 6), ModBlocks.BASIC_SNOW_PIPE.get());
        for (int x = 8; x <= 11; x++) for (int y = 1; y <= 4; y++) for (int z = 6; z <= 8; z++) put(helper, new BlockPos(x, y, z), ModBlocks.ANALOG_TERMINAL.get());
        helper.runAfterDelay(5, () -> {
            GolemNetwork n = net(helper, ORIGIN);
            helper.assertTrue(n.channelsUsed() == 16, "basic pipe passes only 16 channels, got " + n.channelsUsed());
            helper.setBlock(new BlockPos(7, 1, 6), ModBlocks.DENSE_FROST_PIPE.get().defaultBlockState());
            GolemNetwork n2 = net(helper, ORIGIN);
            helper.assertTrue(n2.channelsUsed() == 48, "dense pipe passes all 48, got " + n2.channelsUsed());
            helper.succeed();
        });
    }

    @GameTest(template = T, timeoutTicks = 300)
    public static void busesMoveItems(GameTestHelper helper) {
        BlockPos bay = baseNetwork(helper);
        // импорт: шина на муфте смотрит на запад в сундук (2,1,4)
        helper.setBlock(BUS_IN, ModBlocks.VACUUM_IMPORT_BUS.get().defaultBlockState().setValue(DirectionalBlock.FACING, Direction.WEST));
        BlockPos chest = new BlockPos(2, 1, 4);
        helper.setBlock(chest, Blocks.CHEST);
        Container c = (Container) helper.getBlockEntity(chest);
        c.setItem(0, new ItemStack(Items.COBBLESTONE, 20));

        // экспорт: шина смотрит на север в другой сундук (6,1,2)
        helper.setBlock(BUS_OUT, ModBlocks.INJECTOR_EXPORT_BUS.get().defaultBlockState().setValue(DirectionalBlock.FACING, Direction.NORTH));
        BusBlockEntity bus = (BusBlockEntity) helper.getBlockEntity(BUS_OUT);
        bus.getFilters().setItem(0, new ItemStack(Items.COBBLESTONE));
        BlockPos chest2 = new BlockPos(6, 1, 2);
        helper.setBlock(chest2, Blocks.CHEST);
        Container c2 = (Container) helper.getBlockEntity(chest2);

        helper.runAfterDelay(120, () -> {
            helper.assertTrue(c.isEmpty(), "import bus must empty the first chest");
            int moved = 0;
            for (int i = 0; i < c2.getContainerSize(); i++) {
                moved += c2.getItem(i).getCount();
            }
            helper.assertTrue(moved == 20, "export bus must deliver all 20 cobblestone, got " + moved);
            GolemNetwork n = net(helper, bay);
            helper.assertTrue(n.itemCountOf(ItemKey.of(new ItemStack(Items.COBBLESTONE))) == 0, "nothing left in network");
            helper.succeed();
        });
    }

    @GameTest(template = T, timeoutTicks = 300)
    public static void interfaceKeepsStockAndAccepts(GameTestHelper helper) {
        BlockPos bay = baseNetwork(helper);
        helper.setBlock(BUS_OUT, ModBlocks.GOLEM_INTERFACE.get().defaultBlockState());
        var be = (com.golemlogix.blockentity.InterfaceBlockEntity) helper.getBlockEntity(BUS_OUT);
        be.getConfig().setItem(0, new ItemStack(Items.IRON_INGOT, 10));
        helper.runAfterDelay(2, () -> net(helper, bay).insertItem(new ItemStack(Items.IRON_INGOT, 32), false));
        // автоматика (как воронка или труба другого мода) видит интерфейс с любой стороны
        var handler = helper.getLevel().getCapability(net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK,
                helper.absolutePos(BUS_OUT), Direction.UP);
        helper.assertTrue(handler != null, "interface exposes an item handler");
        helper.assertTrue(handler.insertItem(0, new ItemStack(Items.DIRT), true).getCount() == 1, "stock slots refuse insertion");
        helper.assertTrue(handler.insertItem(9, new ItemStack(Items.GOLD_INGOT, 5), false).isEmpty(), "intake accepts anything");
        helper.runAfterDelay(100, () -> {
            GolemNetwork n = net(helper, bay);
            helper.assertTrue(be.getStock().getItem(0).getCount() == 10, "keeps 10 iron in stock, has " + be.getStock().getItem(0));
            helper.assertTrue(n.itemCountOf(ItemKey.of(new ItemStack(Items.IRON_INGOT))) == 22, "22 iron left in network");
            helper.assertTrue(n.itemCountOf(ItemKey.of(new ItemStack(Items.GOLD_INGOT))) == 5, "intake went into the network");
            helper.assertTrue(be.getInbox().isEmpty(), "intake is empty");
            helper.assertTrue(handler.extractItem(9, 1, true).isEmpty(), "intake cannot be pulled back out");
            ItemStack taken = handler.extractItem(0, 4, false);
            helper.assertTrue(taken.getCount() == 4, "automation takes from stock");
            // меньше держать — лишнее уходит обратно в сеть
            be.getConfig().setItem(0, new ItemStack(Items.IRON_INGOT, 2));
            helper.runAfterDelay(40, () -> {
                helper.assertTrue(be.getStock().getItem(0).getCount() == 2, "excess returned, stock " + be.getStock().getItem(0));
                helper.assertTrue(net(helper, bay).itemCountOf(ItemKey.of(new ItemStack(Items.IRON_INGOT))) == 26, "network got the excess back");
                helper.succeed();
            });
        });
    }

    /**
     * Окно интерфейса: предмет в курсоре ставит призрака (сам предмет остаётся у игрока), количество задаётся числом и
     * ограничено стопкой, клики пустой рукой и «колёсные» клики ничего не вынимают, Shift-клик очищает; жидкость —
     * в mB, смена режима переводит единицы; шифт-клик из инвентаря кладёт в ряд приёма, и предмет уходит в сеть.
     */
    @GameTest(template = T, timeoutTicks = 300)
    public static void interfaceMenuAmounts(GameTestHelper helper) {
        BlockPos bay = baseNetwork(helper);
        helper.setBlock(BUS_OUT, ModBlocks.GOLEM_INTERFACE.get().defaultBlockState());
        var be = (com.golemlogix.blockentity.InterfaceBlockEntity) helper.getBlockEntity(BUS_OUT);
        ServerPlayer player = testPlayer(helper);
        var menu = new com.golemlogix.menu.InterfaceMenu(77, player.getInventory(), be.getConfig(), be.getStock(), be.getInbox(),
                be.getUpgrades(), be.menuData(), be);
        menu.setCarried(new ItemStack(Items.IRON_INGOT, 5));
        menu.clicked(0, 0, net.minecraft.world.inventory.ClickType.PICKUP, player);
        helper.assertTrue(be.getConfig().getItem(0).is(Items.IRON_INGOT) && be.getConfig().getItem(0).getCount() == 1, "ghost is one item");
        helper.assertTrue(menu.getCarried().getCount() == 5, "the carried stack is not consumed");
        helper.assertTrue(be.amount(0) == 5 && menu.amount(0) == 5, "amount comes from the carried stack, got " + be.amount(0));
        menu.setAmount(0, 40);
        helper.assertTrue(menu.amount(0) == 40, "amount set by number, got " + menu.amount(0));
        menu.setAmount(0, 999);
        helper.assertTrue(be.amount(0) == 64, "item amount is capped by the stack size, got " + be.amount(0));
        menu.setCarried(ItemStack.EMPTY);
        for (var type : new net.minecraft.world.inventory.ClickType[]{net.minecraft.world.inventory.ClickType.PICKUP,
                net.minecraft.world.inventory.ClickType.SWAP, net.minecraft.world.inventory.ClickType.CLONE,
                net.minecraft.world.inventory.ClickType.THROW, net.minecraft.world.inventory.ClickType.QUICK_CRAFT}) {
            menu.clicked(0, 0, type, player);
        }
        helper.assertTrue(menu.getCarried().isEmpty() && player.getInventory().isEmpty(), "nothing can be pulled out of a ghost slot");
        helper.assertTrue(be.getConfig().getItem(0).is(Items.IRON_INGOT) && be.amount(0) == 64, "stray clicks leave the slot alone");
        menu.setAmount(0, 6);
        // жидкость из JEI: призрак-ведро, по умолчанию ведро (1000 mB), дальше — в mB
        menu.setGhost(player, 1, new ItemStack(Items.WATER_BUCKET));
        helper.assertTrue(menu.isFluidSlot(1) && be.amount(1) == 1000, "fluid ghost keeps 1000 mB, got " + be.amount(1));
        menu.setAmount(1, 5500);
        helper.assertTrue(be.amount(1) == 5500, "fluid amount in mB, got " + be.amount(1));
        menu.clickMenuButton(player, com.golemlogix.menu.InterfaceMenu.BUTTON_MODE + 1);
        helper.assertTrue(!menu.isFluidSlot(1) && be.amount(1) == 1, "item mode keeps whole buckets, got " + be.amount(1));
        menu.clickMenuButton(player, com.golemlogix.menu.InterfaceMenu.BUTTON_MODE + 1);
        helper.assertTrue(menu.isFluidSlot(1) && be.amount(1) == 1000, "back to mB, got " + be.amount(1));
        menu.clicked(1, 0, net.minecraft.world.inventory.ClickType.QUICK_MOVE, player);
        helper.assertTrue(be.getConfig().getItem(1).isEmpty(), "shift-click clears the slot");
        // ряд приёма: шифт-клик из инвентаря
        player.getInventory().setItem(0, new ItemStack(Items.GOLD_INGOT, 7));
        int hotbar0 = -1;
        for (int k = 0; k < menu.slots.size(); k++) {
            if (menu.slots.get(k).container == player.getInventory() && menu.slots.get(k).getContainerSlot() == 0) {
                hotbar0 = k;
            }
        }
        menu.quickMoveStack(player, hotbar0);
        helper.assertTrue(player.getInventory().getItem(0).isEmpty(), "shift-click moves the stack out of the inventory");
        helper.assertTrue(be.getInbox().countItem(Items.GOLD_INGOT) == 7, "into the intake row");
        helper.assertTrue(menu.getSlot(2 * com.golemlogix.blockentity.InterfaceBlockEntity.SLOTS).mayPlace(new ItemStack(Items.DIRT)),
                "intake slots accept manual placement");
        helper.runAfterDelay(2, () -> net(helper, bay).insertItem(new ItemStack(Items.IRON_INGOT, 32), false));
        helper.runAfterDelay(120, () -> {
            GolemNetwork n = net(helper, bay);
            helper.assertTrue(n.itemCountOf(ItemKey.of(new ItemStack(Items.GOLD_INGOT))) == 7, "manual intake went into the network");
            helper.assertTrue(be.getStock().getItem(0).getCount() == 6, "keeps the configured 6 iron, has " + be.getStock().getItem(0));
            helper.succeed();
        });
    }

    /** Поставщик с выбранной стороной подачи кормит только машину с этой стороны; кнопка окна перебирает стороны. */
    @GameTest(template = T, timeoutTicks = 60)
    public static void providerOutputSide(GameTestHelper helper) {
        BlockPos prov = new BlockPos(3, 1, 3);
        helper.setBlock(prov, ModBlocks.PATTERN_PROVIDER.get().defaultBlockState());
        helper.setBlock(prov.east(), Blocks.CHEST);
        helper.setBlock(prov.west(), Blocks.CHEST);
        PatternProviderBlockEntity provider = (PatternProviderBlockEntity) helper.getBlockEntity(prov);
        PatternData furnace = new PatternData(false, List.of(new ItemStack(Items.RAW_IRON)), List.of(new ItemStack(Items.IRON_INGOT)));
        ServerPlayer player = testPlayer(helper);
        var menu = new com.golemlogix.menu.PatternProviderMenu(21, player.getInventory(), provider.getContainer(), provider.getUpgrades(),
                provider.menuData());
        helper.assertTrue(provider.output() == com.golemlogix.blocks.ProviderOutput.ALL, "all sides by default");
        // все -> вниз -> вверх -> север -> юг -> запад
        for (int i = 0; i < 5; i++) {
            menu.clickMenuButton(player, com.golemlogix.menu.PatternProviderMenu.BUTTON_OUTPUT);
        }
        helper.assertTrue(provider.output() == com.golemlogix.blocks.ProviderOutput.WEST, "the button walks the sides, got " + provider.output());
        for (int i = 0; i < 3; i++) {
            helper.assertTrue(provider.tryPush(furnace) != null, "pushed into the west chest");
        }
        var west = (net.minecraft.world.Container) helper.getBlockEntity(prov.west());
        var east = (net.minecraft.world.Container) helper.getBlockEntity(prov.east());
        helper.assertTrue(west.countItem(Items.RAW_IRON) == 3 && east.countItem(Items.RAW_IRON) == 0,
                "only the west machine is fed: west " + west.countItem(Items.RAW_IRON) + ", east " + east.countItem(Items.RAW_IRON));
        helper.setBlock(prov.west(), Blocks.AIR);
        helper.assertTrue(provider.tryPush(furnace) == null, "nothing on the chosen side — no push even though the east chest is free");
        helper.succeed();
    }

    /** Дисковый шкаф: пока его окно открыто, дверца витрины распахнута (считаются все смотрящие). */
    @GameTest(template = T, timeoutTicks = 60)
    public static void driveBayDoorFollowsWindow(GameTestHelper helper) {
        putBay(helper, BAY);
        CopperDriveBayBlockEntity bay = (CopperDriveBayBlockEntity) helper.getBlockEntity(BAY);
        ServerPlayer a = testPlayer(helper), b = testPlayer(helper);
        var m1 = new com.golemlogix.menu.DriveBayMenu(11, a.getInventory(), bay.getContainer(), bay.priorityData());
        var m2 = new com.golemlogix.menu.DriveBayMenu(12, b.getInventory(), bay.getContainer(), bay.priorityData());
        helper.assertTrue(bay.viewers() == 2, "two players look into the bay, got " + bay.viewers());
        m1.removed(a);
        helper.assertTrue(bay.viewers() == 1, "the door stays open while someone still looks");
        m2.removed(b);
        helper.assertTrue(bay.viewers() == 0, "and closes after the last one");
        helper.succeed();
    }

    /** Крио-Мейнфрейм лицом на север с хозяином в ctl: стекло, корпуса сзади и атланты по бокам. */
    private static void buildMainframe(GameTestHelper helper, BlockPos ctl) {
        helper.setBlock(ctl, ModBlocks.CRYO_MAINFRAME.get().defaultBlockState()
                .setValue(com.golemlogix.blocks.CryoMainframeBlock.FACING, Direction.NORTH));
        BlockPos abs = helper.absolutePos(ctl);
        // атланты смотрят на восток: при сборке их направление не важно
        for (int i = 0; i < com.golemlogix.system.MainframeStructure.CELLS.length; i++) {
            BlockPos p = com.golemlogix.system.MainframeStructure.cellPos(abs, Direction.NORTH, i);
            helper.getLevel().setBlock(p, com.golemlogix.system.MainframeStructure.originalState(i, Direction.EAST), 3);
        }
    }

    /**
     * Крио-Мейнфрейм: собирается из стекла, корпусов и двух атлантов; два калькулятора внутри дают четыре ядра, Сжатый
     * Сопроцессор считается за два (9 шагов за цикл на ядро); весь шкаф занимает один канал; заказ идёт на ядро шкафа;
     * окно распахивает двери; сломанная часть разбирает шкаф и возвращает остальные блоки.
     */
    @GameTest(template = T, timeoutTicks = 400)
    public static void mainframeAssemblesAndCrafts(GameTestHelper helper) {
        BlockPos bay = baseNetwork(helper);
        BlockPos ctl = new BlockPos(5, 1, 1);
        helper.setBlock(PROV, ModBlocks.PATTERN_PROVIDER.get().defaultBlockState());
        PatternProviderBlockEntity provider = (PatternProviderBlockEntity) helper.getBlockEntity(PROV);
        provider.getContainer().setItem(0, card(craftingPattern(new ItemStack(Items.OAK_PLANKS, 4), new ItemStack(Items.OAK_LOG))));
        int[] channelsBefore = new int[1];
        helper.runAfterDelay(5, () -> {
            channelsBefore[0] = net(helper, bay).channelsUsed();
            // труба от угловой муфты решётки к заднему ряду шкафа
            put(helper, new BlockPos(4, 1, 3), ModBlocks.BASIC_SNOW_PIPE.get());
            buildMainframe(helper, ctl);
        });
        helper.runAfterDelay(35, () -> {
            helper.assertTrue(helper.getBlockState(ctl).getValue(com.golemlogix.blocks.CryoMainframeBlock.FORMED), "the mainframe assembles");
            helper.assertTrue(helper.getBlockState(new BlockPos(4, 1, 1)).is(ModBlocks.MAINFRAME_PART.get()), "glass became a part");
            helper.assertTrue(helper.getBlockState(new BlockPos(3, 1, 1)).is(ModBlocks.MAINFRAME_PART.get())
                    && helper.getBlockState(new BlockPos(7, 2, 1)).is(ModBlocks.MAINFRAME_PART.get()), "the atlants are built into the cabinet");
            var mf = (com.golemlogix.blockentity.CryoMainframeBlockEntity) helper.getBlockEntity(ctl);
            mf.getCalculators().setItem(0, new ItemStack(ModBlocks.CRYO_CALCULATOR.get()));
            mf.getCalculators().setItem(5, new ItemStack(ModBlocks.CRYO_CALCULATOR.get()));
            mf.getUpgrades().setItem(0, new ItemStack(ModItems.COMPRESSED_COPROCESSOR.get()));
            helper.assertTrue(mf.cores().size() == 4, "two calculators give four cores, got " + mf.cores().size());
            int expected = CryoCalculatorBlockEntity.BASE_WORKERS + CryoCalculatorBlockEntity.WORKERS_PER_COMPRESSED * 2;
            helper.assertTrue(mf.workersPerCore() == expected, "a co-processor counts twice: " + mf.workersPerCore() + " != " + expected);
            GolemNetwork n = net(helper, bay);
            helper.assertTrue(n.channelsUsed() == channelsBefore[0] + 1, "the whole cabinet takes one channel: "
                    + channelsBefore[0] + " -> " + n.channelsUsed());
            helper.assertTrue(n.cpus().size() == 4, "the network sees four cores, got " + n.cpus().size());
            n.insertItem(new ItemStack(Items.OAK_LOG, 4), false);
            var started = n.startCrafting(ItemKey.of(new ItemStack(Items.OAK_PLANKS)), 16);
            helper.assertTrue(started.type() == GolemNetwork.StartType.STARTED, "crafting starts on a mainframe core: " + started.type());
            helper.assertTrue(mf.busyCores() == 1, "one core is busy");
            // окно: двери открываются, пока смотрят
            ServerPlayer player = testPlayer(helper);
            var menu = new com.golemlogix.menu.MainframeMenu(91, player.getInventory(), mf, mf.getCalculators(), mf.getUpgrades(),
                    mf.menuData(), net.minecraft.world.inventory.ContainerLevelAccess.NULL);
            helper.assertTrue(mf.viewers() == 1, "the doors open while the window is open");
            helper.assertTrue(!menu.getSlot(1).mayPlace(new ItemStack(Items.DIRT)), "shelves take only calculators");
            menu.removed(player);
            helper.assertTrue(mf.viewers() == 0, "and close with it");
        });
        helper.runAfterDelay(140, () -> {
            GolemNetwork n = net(helper, bay);
            helper.assertTrue(n.itemCountOf(ItemKey.of(new ItemStack(Items.OAK_PLANKS))) == 16, "the core crafted 16 planks, got "
                    + n.itemCountOf(ItemKey.of(new ItemStack(Items.OAK_PLANKS))));
            var mf = (com.golemlogix.blockentity.CryoMainframeBlockEntity) helper.getBlockEntity(ctl);
            helper.assertTrue(mf.busyCores() == 0, "the core is free again");
            // сломали стекло — шкаф разобран, остальные части снова стекло и корпуса
            helper.getLevel().destroyBlock(helper.absolutePos(new BlockPos(4, 1, 1)), true);
            helper.assertTrue(!helper.getBlockState(ctl).getValue(com.golemlogix.blocks.CryoMainframeBlock.FORMED), "disassembled");
            helper.assertTrue(helper.getBlockState(new BlockPos(6, 1, 1)).is(Blocks.GLASS), "glass is back");
            helper.assertTrue(helper.getBlockState(new BlockPos(5, 1, 2)).is(ModBlocks.ATLAS_CASING.get()), "casing is back");
            helper.assertTrue(mf.cores().size() == 4, "the calculators stay inside");
            helper.assertTrue(net(helper, bay).freeCpu() == null, "an unassembled cabinet takes no orders");
            BlockState lower = helper.getBlockState(new BlockPos(3, 1, 1)), upper = helper.getBlockState(new BlockPos(3, 2, 1));
            helper.assertTrue(lower.is(ModBlocks.ATLAS_ATLANT.get()) && lower.getValue(AtlantBlock.HALF) == DoubleBlockHalf.LOWER
                    && upper.is(ModBlocks.ATLAS_ATLANT.get()) && upper.getValue(AtlantBlock.HALF) == DoubleBlockHalf.UPPER,
                    "both halves of the atlant are back");
            // стекло на место — шкаф снова собирается
            helper.setBlock(new BlockPos(4, 1, 1), Blocks.GLASS);
        });
        helper.runAfterDelay(175, () -> {
            helper.assertTrue(helper.getBlockState(ctl).getValue(com.golemlogix.blocks.CryoMainframeBlock.FORMED), "reassembled");
            helper.killAllEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class);
            // сломали верхнюю половину встроенного атланта: выпадает один атлант, нижняя половина исчезает
            helper.getLevel().destroyBlock(helper.absolutePos(new BlockPos(7, 2, 1)), true);
            helper.assertTrue(!helper.getBlockState(ctl).getValue(com.golemlogix.blocks.CryoMainframeBlock.FORMED), "disassembled again");
            helper.assertTrue(helper.getBlockState(new BlockPos(7, 1, 1)).isAir(), "the other half of that atlant is gone");
            helper.assertTrue(helper.getBlockState(new BlockPos(3, 2, 1)).is(ModBlocks.ATLAS_ATLANT.get()), "the other atlant is back whole");
            int atlants = 0;
            for (var e : helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                    new net.minecraft.world.phys.AABB(helper.absolutePos(new BlockPos(7, 1, 1))).inflate(3))) {
                if (e.getItem().is(ModBlocks.ATLAS_ATLANT.get().asItem())) {
                    atlants += e.getItem().getCount();
                }
            }
            helper.assertTrue(atlants == 1, "exactly one atlant drops, got " + atlants);
            helper.succeed();
        });
    }

    private static Block statue(String prefix) {
        return net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(
                net.minecraft.resources.ResourceLocation.withDefaultNamespace(prefix + "copper_golem_statue"));
    }

    /** Картотека перфокарт лицом на север с хозяином в ctl: полки, медь сзади, постаменты и статуи (справа — окисленная). */
    private static void buildCatalog(GameTestHelper helper, BlockPos ctl) {
        helper.setBlock(ctl, ModBlocks.CARD_CATALOG.get().defaultBlockState()
                .setValue(com.golemlogix.blocks.CardCatalogBlock.FACING, Direction.NORTH));
        BlockPos abs = helper.absolutePos(ctl);
        for (int i = 0; i < com.golemlogix.system.CatalogStructure.CELLS.length; i++) {
            BlockPos p = com.golemlogix.system.CatalogStructure.cellPos(abs, Direction.NORTH, i);
            Block b = com.golemlogix.system.CatalogStructure.isShelf(i) ? Blocks.BOOKSHELF
                    : com.golemlogix.system.CatalogStructure.isStatueCell(i) ? statue(i == 14 ? "oxidized_" : "")
                    : i == 5 ? Blocks.WAXED_WEATHERED_COPPER : Blocks.COPPER_BLOCK;
            helper.getLevel().setBlock(p, b.defaultBlockState(), 3);
        }
    }

    /**
     * Картотека перфокарт: собирается из полок, меди и двух статуй медных големов; без поставщиков в ней нет мест, каждый
     * поставщик даёт 27; принимает только верстачные перфокарты; забрать поставщиков, чьи места заняты, нельзя; поиск
     * находит карту; её шаблон виден сети (шкаф — один канал), калькулятор крафтит по нему без поставщика; поломка части
     * разбирает шкаф и возвращает прежние блоки (статуя — той же окисленности).
     */
    @GameTest(template = T, timeoutTicks = 300)
    public static void catalogStoresCraftingCards(GameTestHelper helper) {
        helper.assertTrue(statue("") != Blocks.AIR && statue("oxidized_") != Blocks.AIR, "copper golem statues are registered");
        BlockPos bay = baseNetwork(helper);
        BlockPos ctl = new BlockPos(5, 1, 1);
        int[] channelsBefore = new int[1];
        helper.runAfterDelay(5, () -> {
            helper.setBlock(new BlockPos(6, 1, 3), ModBlocks.CRYO_CALCULATOR.get().defaultBlockState());
            channelsBefore[0] = net(helper, bay).channelsUsed();
            put(helper, new BlockPos(4, 1, 3), ModBlocks.BASIC_SNOW_PIPE.get());
            buildCatalog(helper, ctl);
        });
        ItemStack planks = new ItemStack(Items.OAK_PLANKS);
        helper.runAfterDelay(35, () -> {
            helper.assertTrue(helper.getBlockState(ctl).getValue(com.golemlogix.blocks.CardCatalogBlock.FORMED), "the catalog assembles");
            helper.assertTrue(helper.getBlockState(new BlockPos(4, 2, 1)).is(ModBlocks.CATALOG_PART.get())
                    && helper.getBlockState(new BlockPos(7, 2, 1)).is(ModBlocks.CATALOG_PART.get()), "shelves and statues became parts");
            var cat = (com.golemlogix.blockentity.CardCatalogBlockEntity) helper.getBlockEntity(ctl);
            helper.assertTrue(cat.golemOxidation(1) == 3 && cat.golemOxidation(0) == 0, "golems take the statues' oxidation");
            ServerPlayer player = testPlayer(helper);
            var menu = new com.golemlogix.menu.CardCatalogMenu(93, player.getInventory(), cat, net.minecraft.world.inventory.ContainerLevelAccess.NULL);
            ItemStack plankCard = card(craftingPattern(new ItemStack(Items.OAK_PLANKS, 4), new ItemStack(Items.OAK_LOG)));
            helper.assertTrue(cat.capacity() == 0 && !menu.getSlot(0).mayPlace(plankCard), "no providers — no places");
            cat.getProviders().setItem(0, new ItemStack(ModBlocks.PATTERN_PROVIDER.get(), 2));
            helper.assertTrue(cat.capacity() == 54, "two providers give 54 places, got " + cat.capacity());
            helper.assertTrue(menu.getSlot(0).mayPlace(plankCard), "now a crafting card fits");
            PatternData machine = new PatternData(false, List.of(new ItemStack(Items.IRON_ORE)), List.of(new ItemStack(Items.IRON_INGOT)));
            helper.assertTrue(!menu.getSlot(0).mayPlace(card(machine)), "machine patterns stay with providers at machines");
            // карта в дальнее место — двух поставщиков забрать уже нельзя, третий — можно
            cat.getCards().setItem(30, plankCard);
            var provSlot = (com.golemlogix.menu.CardCatalogMenu.ProviderSlot) menu.getSlot(menu.providerSlot());
            helper.assertTrue(provSlot.removable() == 0 && !provSlot.mayPickup(player), "occupied places keep their providers");
            cat.getProviders().setItem(0, new ItemStack(ModBlocks.PATTERN_PROVIDER.get(), 3));
            helper.assertTrue(provSlot.removable() == 1, "the third provider is free to take, removable " + provSlot.removable());
            // шифт-клик картой из инвентаря — в первое свободное место
            player.getInventory().setItem(0, card(craftingPattern(new ItemStack(Items.STICK, 4), new ItemStack(Items.OAK_PLANKS),
                    ItemStack.EMPTY, ItemStack.EMPTY, new ItemStack(Items.OAK_PLANKS))));
            int invSlot = -1;
            for (int i = 0; i < menu.slots.size(); i++) {
                if (menu.slots.get(i).container == player.getInventory() && menu.slots.get(i).getContainerSlot() == 0) {
                    invSlot = i;
                }
            }
            menu.quickMoveStack(player, invSlot);
            helper.assertTrue(!cat.getCards().getItem(0).isEmpty(), "shift-click puts the card into the first free place");
            // поиск: гряда показывает только найденные карты
            menu.view(0, "plank");
            helper.assertTrue(ItemStack.matches(menu.getSlot(0).getItem(), plankCard) && menu.getSlot(1).getItem().isEmpty(),
                    "the search shows the planks card first");
            menu.view(0, "");
            menu.removed(player);
            GolemNetwork n = net(helper, bay);
            helper.assertTrue(n.channelsUsed() == channelsBefore[0] + 1, "the whole cabinet takes one channel: "
                    + channelsBefore[0] + " -> " + n.channelsUsed());
            helper.assertTrue(n.canCraft(ItemKey.of(planks)) && n.canCraft(ItemKey.of(new ItemStack(Items.STICK))),
                    "the network knows the catalog patterns");
            n.insertItem(new ItemStack(Items.OAK_LOG, 4), false);
            var started = n.startCrafting(ItemKey.of(planks), 16);
            helper.assertTrue(started.type() == GolemNetwork.StartType.STARTED, "crafting from a catalog card starts: " + started.type());
        });
        helper.runAfterDelay(160, () -> {
            GolemNetwork n = net(helper, bay);
            helper.assertTrue(n.itemCountOf(ItemKey.of(planks)) == 16, "the calculator crafted 16 planks, got " + n.itemCountOf(ItemKey.of(planks)));
            // сломали полку — шкаф разобран, остальные блоки на месте, шаблоны пропали из сети
            helper.getLevel().destroyBlock(helper.absolutePos(new BlockPos(4, 1, 1)), true);
            helper.assertTrue(!helper.getBlockState(ctl).getValue(com.golemlogix.blocks.CardCatalogBlock.FORMED), "disassembled");
            helper.assertTrue(helper.getBlockState(new BlockPos(6, 2, 1)).is(Blocks.BOOKSHELF), "shelves are back");
            helper.assertTrue(helper.getBlockState(new BlockPos(7, 2, 1)).is(statue("oxidized_")), "the oxidized statue is back");
            helper.assertTrue(helper.getBlockState(new BlockPos(4, 1, 2)).is(Blocks.WAXED_WEATHERED_COPPER), "waxed copper is back as it was");
            helper.assertTrue(!net(helper, bay).canCraft(ItemKey.of(planks)), "an unassembled catalog gives no patterns");
            var cat = (com.golemlogix.blockentity.CardCatalogBlockEntity) helper.getBlockEntity(ctl);
            helper.assertTrue(cat.cardCount() == 2, "the cards stay inside");
            helper.succeed();
        });
    }

    /**
     * Крио-концентратор схем: показывает поставщиков сети по категориям (верстачные — «Верстак», остальные — по машине рядом),
     * ячейки окна — настоящие ячейки поставщиков; шифт-клик кладёт машинную карту к машине; поиск оставляет нужную категорию.
     */
    @GameTest(template = T, timeoutTicks = 100)
    public static void concentratorGroupsProviders(GameTestHelper helper) {
        baseNetwork(helper);
        BlockPos craftProv = new BlockPos(4, 1, 7), conc = new BlockPos(6, 1, 7);
        helper.setBlock(PROV, ModBlocks.PATTERN_PROVIDER.get().defaultBlockState());
        helper.setBlock(PROV.east(), Blocks.FURNACE);
        helper.setBlock(craftProv, ModBlocks.PATTERN_PROVIDER.get().defaultBlockState());
        helper.setBlock(conc, ModBlocks.SCHEMATIC_CONCENTRATOR.get().defaultBlockState());
        PatternProviderBlockEntity furnaceProv = (PatternProviderBlockEntity) helper.getBlockEntity(PROV);
        PatternProviderBlockEntity crafting = (PatternProviderBlockEntity) helper.getBlockEntity(craftProv);
        ItemStack smelt = card(new PatternData(false, List.of(new ItemStack(Items.IRON_ORE)), List.of(new ItemStack(Items.IRON_INGOT))));
        ItemStack planks = card(craftingPattern(new ItemStack(Items.OAK_PLANKS, 4), new ItemStack(Items.OAK_LOG)));
        furnaceProv.getContainer().setItem(0, smelt.copy());
        crafting.getContainer().setItem(0, planks.copy());
        helper.runAfterDelay(5, () -> {
            ServerPlayer player = testPlayer(helper);
            var menu = new com.golemlogix.menu.ConcentratorMenu(94, player.getInventory(), helper.getLevel(), helper.absolutePos(conc));
            helper.assertTrue(menu.totalRows() == 4, "two categories with one provider each, rows " + menu.totalRows());
            helper.assertTrue(!menu.getSlot(0).isActive() && menu.getSlot(9).isActive(), "a header row has no slots, a provider row has nine");
            helper.assertTrue(ItemStack.matches(menu.getSlot(9).getItem(), planks), "crafting cards come first");
            helper.assertTrue(ItemStack.matches(menu.getSlot(27).getItem(), smelt), "then the furnace provider");
            // машинная карта шифт-кликом — к машине, а не в верстачного поставщика
            ItemStack blast = card(new PatternData(false, List.of(new ItemStack(Items.GOLD_ORE)), List.of(new ItemStack(Items.GOLD_INGOT))));
            player.getInventory().setItem(0, blast.copy());
            int invSlot = -1;
            for (int i = 0; i < menu.slots.size(); i++) {
                if (menu.slots.get(i).container == player.getInventory() && menu.slots.get(i).getContainerSlot() == 0) {
                    invSlot = i;
                }
            }
            menu.quickMoveStack(player, invSlot);
            helper.assertTrue(ItemStack.matches(furnaceProv.getContainer().getItem(1), blast), "the smelting card went to the furnace provider");
            // карту можно вынуть прямо из окна
            ItemStack taken = menu.getSlot(9).remove(1);
            helper.assertTrue(ItemStack.matches(taken, planks) && crafting.getContainer().getItem(0).isEmpty(), "taking a card empties the provider");
            crafting.getContainer().setItem(0, planks.copy());
            menu.view(0, "ingot");
            helper.assertTrue(menu.totalRows() == 2 && ItemStack.matches(menu.getSlot(9).getItem(), smelt), "search keeps only the furnace row");
            var be = (com.golemlogix.blockentity.SchematicConcentratorBlockEntity) helper.getBlockEntity(conc);
            helper.assertTrue(be.viewers() == 1, "the hologram opens with the window");
            menu.removed(player);
            helper.assertTrue(be.viewers() == 0, "and closes with it");
            helper.succeed();
        });
    }

    @GameTest(template = T, timeoutTicks = 400)
    public static void interfaceCraftsMissingStock(GameTestHelper helper) {
        BlockPos bay = baseNetwork(helper);
        helper.setBlock(CPU, ModBlocks.CRYO_CALCULATOR.get().defaultBlockState());
        helper.setBlock(PROV, ModBlocks.PATTERN_PROVIDER.get().defaultBlockState());
        PatternProviderBlockEntity provider = (PatternProviderBlockEntity) helper.getBlockEntity(PROV);
        provider.getContainer().setItem(0, card(craftingPattern(new ItemStack(Items.OAK_PLANKS, 4), new ItemStack(Items.OAK_LOG))));
        helper.setBlock(BUS_OUT, ModBlocks.GOLEM_INTERFACE.get().defaultBlockState());
        var be = (com.golemlogix.blockentity.InterfaceBlockEntity) helper.getBlockEntity(BUS_OUT);
        be.getConfig().setItem(0, new ItemStack(Items.OAK_PLANKS, 8));
        be.getUpgrades().setItem(0, new ItemStack(ModItems.AUTOCRAFT_MODULE.get()));
        helper.runAfterDelay(2, () -> net(helper, bay).insertItem(new ItemStack(Items.OAK_LOG, 4), false));
        helper.runAfterDelay(300, () -> {
            helper.assertTrue(be.getStock().getItem(0).getCount() == 8, "interface ordered and stocked 8 planks, has " + be.getStock().getItem(0));
            helper.succeed();
        });
    }

    private static ItemStack card(PatternData data) {
        return com.golemlogix.items.CopperPunchCardItem.written(ModItems.COPPER_PUNCH_CARD.get(), data);
    }

    private static PatternData craftingPattern(ItemStack output, ItemStack... slots) {
        List<ItemStack> grid = new ArrayList<>();
        for (int i = 0; i < 9; i++) {
            grid.add(i < slots.length ? slots[i] : ItemStack.EMPTY);
        }
        return new PatternData(true, grid, List.of(output));
    }

    @GameTest(template = T, timeoutTicks = 400)
    public static void autocraftTree(GameTestHelper helper) {
        BlockPos bay = baseNetwork(helper);
        helper.setBlock(CPU, ModBlocks.CRYO_CALCULATOR.get().defaultBlockState());
        helper.setBlock(PROV, ModBlocks.PATTERN_PROVIDER.get().defaultBlockState());
        PatternProviderBlockEntity provider = (PatternProviderBlockEntity) helper.getBlockEntity(PROV);
        ItemStack plank = new ItemStack(Items.OAK_PLANKS);
        provider.getContainer().setItem(0, card(craftingPattern(new ItemStack(Items.OAK_PLANKS, 4), new ItemStack(Items.OAK_LOG))));
        provider.getContainer().setItem(1, card(craftingPattern(new ItemStack(Items.STICK, 4),
                plank.copy(), ItemStack.EMPTY, ItemStack.EMPTY, plank.copy())));

        helper.runAfterDelay(5, () -> {
            GolemNetwork n = net(helper, bay);
            n.insertItem(new ItemStack(Items.OAK_LOG, 1), false);

            ItemKey sticks = ItemKey.of(new ItemStack(Items.STICK));
            helper.assertTrue(n.canCraft(sticks), "sticks must be craftable");
            CraftingPlan plan = CraftingPlanner.plan(n, sticks, 8);
            helper.assertTrue(plan.missing().isEmpty(), "plan has missing items: " + plan.missing());
            helper.assertTrue(plan.steps().size() == 2, "two patterns expected, got " + plan.steps().size());
            helper.assertTrue(plan.totalExecutions() == 3, "1 planks craft + 2 stick crafts, got " + plan.totalExecutions());

            CraftingPlan big = CraftingPlanner.plan(n, sticks, 100);
            helper.assertTrue(!big.missing().isEmpty(), "100 sticks need more logs");

            GolemNetwork.StartResult r = n.startCrafting(sticks, 8);
            helper.assertTrue(r.type() == GolemNetwork.StartType.STARTED, "start result " + r.type());
            helper.assertTrue(n.itemCountOf(ItemKey.of(new ItemStack(Items.OAK_LOG))) == 0, "log must be reserved");
        });

        helper.runAfterDelay(150, () -> {
            GolemNetwork n = net(helper, bay);
            long sticksNow = n.itemCountOf(ItemKey.of(new ItemStack(Items.STICK)));
            helper.assertTrue(sticksNow == 8, "expected 8 sticks, got " + sticksNow);
            helper.assertTrue(n.itemCountOf(ItemKey.of(new ItemStack(Items.OAK_PLANKS))) == 0, "no planks left");
            CryoCalculatorBlockEntity cpu = (CryoCalculatorBlockEntity) helper.getBlockEntity(CPU);
            helper.assertTrue(cpu.isIdle(), "calculator must be idle again");
            helper.succeed();
        });
    }

    @GameTest(template = T, timeoutTicks = 400)
    public static void autocraftProcessing(GameTestHelper helper) {
        BlockPos bay = baseNetwork(helper);
        helper.setBlock(CPU, ModBlocks.CRYO_CALCULATOR.get().defaultBlockState());
        helper.setBlock(PROV, ModBlocks.PATTERN_PROVIDER.get().defaultBlockState());
        // «машина» — сундук за поставщиком
        BlockPos machine = PROV.east();
        helper.setBlock(machine, Blocks.CHEST);
        Container machineInv = (Container) helper.getBlockEntity(machine);
        PatternProviderBlockEntity provider = (PatternProviderBlockEntity) helper.getBlockEntity(PROV);
        provider.getContainer().setItem(0, card(new PatternData(false,
                List.of(new ItemStack(Items.RAW_IRON)), List.of(new ItemStack(Items.IRON_INGOT)))));

        helper.runAfterDelay(5, () -> {
            GolemNetwork n = net(helper, bay);
            n.insertItem(new ItemStack(Items.RAW_IRON, 3), false);
            GolemNetwork.StartResult r = n.startCrafting(ItemKey.of(new ItemStack(Items.IRON_INGOT)), 3);
            helper.assertTrue(r.type() == GolemNetwork.StartType.STARTED, "start result " + r.type());
        });

        helper.runAfterDelay(60, () -> {
            int ore = 0;
            for (int i = 0; i < machineInv.getContainerSize(); i++) {
                ore += machineInv.getItem(i).getCount();
            }
            helper.assertTrue(ore == 3, "machine must receive 3 raw iron, got " + ore);
            machineInv.clearContent();
            GolemNetwork n = net(helper, bay);
            ItemStack rest = n.insertFromMachine(new ItemStack(Items.IRON_INGOT, 3));
            helper.assertTrue(rest.isEmpty(), "job absorbs the outputs");
        });

        helper.runAfterDelay(120, () -> {
            GolemNetwork n = net(helper, bay);
            long ingots = n.itemCountOf(ItemKey.of(new ItemStack(Items.IRON_INGOT)));
            helper.assertTrue(ingots == 3, "expected 3 ingots delivered to storage, got " + ingots);
            CryoCalculatorBlockEntity cpu = (CryoCalculatorBlockEntity) helper.getBlockEntity(CPU);
            helper.assertTrue(cpu.isIdle(), "calculator idle after delivery");
            helper.succeed();
        });
    }

    @GameTest(template = T, timeoutTicks = 100)
    public static void networkNeedsSource(GameTestHelper helper) {
        buildArray(helper, 0, 0);
        putBay(helper, BAY);
        helper.runAfterDelay(5, () -> {
            GolemNetwork n = net(helper, ORIGIN);
            helper.assertTrue(n.hasCore(), "core");
            if (!com.golemlogix.system.SourceHelper.sourceRequired()) {
                // без Ars Nouveau сеть работает даром
                helper.assertTrue(n.isOnline(), "without Ars Nouveau the network needs no Source");
                helper.succeed();
                return;
            }
            helper.assertTrue(!n.isOnline(), "no source => offline");
            helper.assertTrue("message.golem_logix.no_power".equals(n.accessError(null)), "access error");
            // Резонатор питает только хронометры, не сеть
            put(helper, JAR, ModBlocks.ETERNAL_FROST_RESONATOR.get());
            helper.runAfterDelay(25, () -> {
                GolemNetwork n2 = net(helper, ORIGIN);
                helper.assertTrue(n2.hasResonator() && !n2.isOnline(), "the resonator does not power the network");
                put(helper, JAR, ModBlocks.CREATIVE_SOURCE_JAR.get());
                helper.runAfterDelay(25, () -> {
                    helper.assertTrue(net(helper, ORIGIN).isOnline(), "a Source jar does");
                    helper.succeed();
                });
            });
        });
    }

    @GameTest(template = T, timeoutTicks = 100)
    public static void formationOrientsAtlants(GameTestHelper helper) {
        buildArray(helper, 0, 0);
        powered(helper);
        helper.runAfterDelay(5, () -> {
            BlockState northCorner = helper.getBlockState(ORIGIN.offset(1, 1, -1));
            BlockState southCorner = helper.getBlockState(ORIGIN.offset(-1, 2, 1));
            helper.assertTrue(northCorner.getValue(AtlantBlock.FACING) == Direction.NORTH, "northern atlants look north");
            helper.assertTrue(southCorner.getValue(AtlantBlock.FACING) == Direction.SOUTH, "southern atlants look south");
            helper.succeed();
        });
    }

    // --------------------------------------------------------------- диски

    @GameTest(template = T, timeoutTicks = 200)
    public static void fluidDisks(GameTestHelper helper) {
        BlockPos bay = baseNetwork(helper);
        CopperDriveBayBlockEntity be = (CopperDriveBayBlockEntity) helper.getBlockEntity(bay);
        be.getContainer().setItem(1, new ItemStack(ModItems.FLUID_DISK_4K.get()));
        helper.runAfterDelay(5, () -> {
            GolemNetwork n = net(helper, bay);
            helper.assertTrue(n.fluidCapacity() == 4000, "fluid capacity " + n.fluidCapacity());
            helper.assertTrue(n.insertFluid(new FluidStack(Fluids.WATER, 3000), false) == 3000, "3000 mB fit");
            helper.assertTrue(n.insertFluid(new FluidStack(Fluids.LAVA, 5000), false) == 1000, "only 1000 mB left");
            helper.assertTrue(n.listFluids().size() == 2, "water and lava");
            FluidStack got = n.extractFluid(new FluidStack(Fluids.WATER, 1), 1200, false);
            helper.assertTrue(got.getAmount() == 1200, "extracted water " + got.getAmount());
            helper.assertTrue(DiskStorage.storedItems(be.getDisks().get(1)) == 2800, "disk stored " + DiskStorage.storedItems(be.getDisks().get(1)));
            helper.assertTrue(DiskStorage.insertFluid(be.getDisks().get(0), new FluidStack(Fluids.WATER, 10), false) == 0, "item disk rejects fluids");
            helper.succeed();
        });
    }

    @GameTest(template = T, timeoutTicks = 200)
    public static void interfaceAcceptsFluids(GameTestHelper helper) {
        BlockPos bay = baseNetwork(helper);
        CopperDriveBayBlockEntity drive = (CopperDriveBayBlockEntity) helper.getBlockEntity(bay);
        drive.getContainer().setItem(1, new ItemStack(ModItems.FLUID_DISK_4K.get()));
        helper.setBlock(BUS_OUT, ModBlocks.GOLEM_INTERFACE.get().defaultBlockState());
        var fluids = helper.getLevel().getCapability(net.neoforged.neoforge.capabilities.Capabilities.FluidHandler.BLOCK,
                helper.absolutePos(BUS_OUT), Direction.WEST);
        helper.assertTrue(fluids != null, "interface exposes a fluid handler");
        helper.assertTrue(fluids.fill(new FluidStack(Fluids.WATER, 2500), net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE) == 2500,
                "interface takes 2500 mB");
        helper.assertTrue(fluids.drain(100, net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE).isEmpty(), "cannot be drained back");
        helper.runAfterDelay(120, () -> {
            GolemNetwork n = net(helper, bay);
            FluidStack got = n.extractFluid(new FluidStack(Fluids.WATER, 1), 4000, true);
            helper.assertTrue(got.getAmount() == 2500, "all water went into the network, got " + got.getAmount());
            helper.succeed();
        });
    }

    @GameTest(template = T, timeoutTicks = 300)
    public static void interfaceStocksFluidPerSlot(GameTestHelper helper) {
        BlockPos bay = baseNetwork(helper);
        CopperDriveBayBlockEntity drive = (CopperDriveBayBlockEntity) helper.getBlockEntity(bay);
        drive.getContainer().setItem(1, new ItemStack(ModItems.FLUID_DISK_16K.get()));
        helper.setBlock(BUS_OUT, ModBlocks.GOLEM_INTERFACE.get().defaultBlockState());
        var be = (com.golemlogix.blockentity.InterfaceBlockEntity) helper.getBlockEntity(BUS_OUT);
        // ячейка 0: три ведра воды в режиме «жидкость» (по умолчанию); ячейка 1: ведро воды в режиме «предмет»
        be.getConfig().setItem(0, new ItemStack(Items.WATER_BUCKET, 3));
        be.getConfig().setItem(1, new ItemStack(Items.WATER_BUCKET, 1));
        be.toggleMode(1);
        helper.runAfterDelay(2, () -> {
            GolemNetwork n = net(helper, bay);
            n.insertFluid(new FluidStack(Fluids.WATER, 8000), false);
            n.insertItem(new ItemStack(Items.WATER_BUCKET), false);
        });
        helper.runAfterDelay(150, () -> {
            helper.assertTrue(be.fluidStock(0).getAmount() == 3000, "keeps 3000 mB of water, has " + be.fluidStock(0).getAmount());
            helper.assertTrue(be.getStock().getItem(0).isEmpty(), "fluid slot keeps no buckets");
            helper.assertTrue(be.getStock().getItem(1).is(Items.WATER_BUCKET), "item-mode slot keeps the bucket itself");
            var fluids = helper.getLevel().getCapability(net.neoforged.neoforge.capabilities.Capabilities.FluidHandler.BLOCK,
                    helper.absolutePos(BUS_OUT), Direction.UP);
            FluidStack out = fluids.drain(1500, net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE);
            helper.assertTrue(out.getAmount() == 1500 && out.is(Fluids.WATER), "pipes drain the stocked water, got " + out.getAmount());
            helper.succeed();
        });
    }

    /**
     * Жидкости чужого мода (Mekanism, если загружен: gradle -Pmekanism): его вёдра и его бак в ячейке настройки распознаются как
     * жидкость, интерфейс набирает её из сети и отдаёт трубам. Без Mekanism тест проходит пустым.
     */
    @GameTest(template = T, timeoutTicks = 300)
    public static void interfaceModdedFluids(GameTestHelper helper) {
        if (!net.neoforged.fml.ModList.get().isLoaded("mekanism")) {
            helper.succeed();
            return;
        }
        var items = net.minecraft.core.registries.BuiltInRegistries.ITEM;
        var fluidsReg = net.minecraft.core.registries.BuiltInRegistries.FLUID;
        ItemStack brineBucket = new ItemStack(items.get(net.minecraft.resources.ResourceLocation.parse("mekanism:brine_bucket")), 2);
        ItemStack heavyBucket = new ItemStack(items.get(net.minecraft.resources.ResourceLocation.parse("mekanism:heavy_water_bucket")), 1);
        // переносной бак Mekanism, залитый серной кислотой через его же ёмкость-предмет
        ItemStack tank = new ItemStack(items.get(net.minecraft.resources.ResourceLocation.parse("mekanism:basic_fluid_tank")));
        var acid = fluidsReg.get(net.minecraft.resources.ResourceLocation.parse("mekanism:sulfuric_acid"));
        var tankCap = tank.getCapability(net.neoforged.neoforge.capabilities.Capabilities.FluidHandler.ITEM);
        helper.assertTrue(tankCap != null, "Mekanism tank item exposes a fluid handler");
        // Mekanism заливает в бак-предмет не больше ведра за раз — порциями
        int filled = 0;
        for (int k = 0; k < 8 && filled < 4000; k++) {
            filled += tankCap.fill(new FluidStack(acid, 4000 - filled), net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE);
        }
        ItemStack filledTank = tankCap.getContainer();
        helper.assertTrue(filled == 4000, "tank item took 4000 mB of acid, took " + filled);

        var brine = com.golemlogix.blockentity.InterfaceBlockEntity.containedFluid(brineBucket);
        var heavy = com.golemlogix.blockentity.InterfaceBlockEntity.containedFluid(heavyBucket);
        helper.assertTrue(!brine.isEmpty() && brine.getFluid().builtInRegistryHolder().key().location().getPath().contains("brine"), "brine bucket -> " + brine.getFluid());
        helper.assertTrue(!heavy.isEmpty(), "heavy water bucket -> " + heavy.getFluid());
        helper.assertTrue(com.golemlogix.blockentity.InterfaceBlockEntity.containedFluid(filledTank).is(acid), "filled tank -> acid");

        BlockPos bay = baseNetwork(helper);
        CopperDriveBayBlockEntity drive = (CopperDriveBayBlockEntity) helper.getBlockEntity(bay);
        drive.getContainer().setItem(1, new ItemStack(ModItems.FLUID_DISK_64K.get()));
        helper.setBlock(BUS_OUT, ModBlocks.GOLEM_INTERFACE.get().defaultBlockState());
        var be = (com.golemlogix.blockentity.InterfaceBlockEntity) helper.getBlockEntity(BUS_OUT);
        be.getConfig().setItem(0, brineBucket);
        be.getConfig().setItem(1, heavyBucket);
        be.getConfig().setItem(2, filledTank.copyWithCount(1));
        helper.runAfterDelay(2, () -> {
            GolemNetwork n = net(helper, bay);
            n.insertFluid(brine.copyWithAmount(5000), false);
            n.insertFluid(heavy.copyWithAmount(5000), false);
            n.insertFluid(new FluidStack(acid, 5000), false);
        });
        helper.runAfterDelay(200, () -> {
            helper.assertTrue(be.fluidStock(0).getAmount() == 2000, "2 buckets of brine kept, has " + be.fluidStock(0).getAmount());
            helper.assertTrue(be.fluidStock(1).getAmount() == 1000, "1 bucket of heavy water kept, has " + be.fluidStock(1).getAmount());
            // одна штука в ячейке = 1000 mB, хоть бак и вмещает больше
            helper.assertTrue(be.fluidStock(2).getAmount() == 1000 && be.fluidStock(2).is(acid), "acid by the tank kept, has " + be.fluidStock(2).getAmount());
            var cap = helper.getLevel().getCapability(net.neoforged.neoforge.capabilities.Capabilities.FluidHandler.BLOCK,
                    helper.absolutePos(BUS_OUT), Direction.NORTH);
            FluidStack out = cap.drain(brine.copyWithAmount(700), net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE);
            helper.assertTrue(out.getAmount() == 700, "pipes drain Mekanism brine, got " + out.getAmount());
            helper.succeed();
        });
    }

    @GameTest(template = T, timeoutTicks = 100)
    public static void diskStoreRoundTrip(GameTestHelper helper) {
        var level = helper.getLevel();
        ItemStack itemDisk = new ItemStack(ModItems.COPPER_DISK_4K.get());
        ItemStack fluidDisk = new ItemStack(ModItems.FLUID_DISK_4K.get());
        ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);
        sword.setDamageValue(7);
        helper.assertTrue(DiskStorage.insertItem(itemDisk, new ItemStack(Items.COBBLESTONE, 500), false) == 500, "500 cobble");
        helper.assertTrue(DiskStorage.insertItem(itemDisk, sword, false) == 1, "component-carrying stack stored separately");
        helper.assertTrue(DiskStorage.insertFluid(fluidDisk, new FluidStack(Fluids.WATER, 1234), false) == 1234, "water");
        java.util.UUID itemId = itemDisk.get(ModDataComponents.DISK_ID.get());
        java.util.UUID fluidId = fluidDisk.get(ModDataComponents.DISK_ID.get());
        helper.assertTrue(itemId != null && fluidId != null && !itemId.equals(fluidId), "disks get unique ids");
        helper.assertTrue(itemDisk.get(ModDataComponents.STORED.get()) == 501, "STORED mirrors the content");

        DiskStore store = DiskStore.get(level.getServer());
        CompoundTag tag = store.save(new CompoundTag(), level.registryAccess());
        DiskStore loaded = DiskStore.load(tag, level.registryAccess());
        DiskStore.Disk a = loaded.getOrNull(itemId);
        helper.assertTrue(a != null && a.storedItems == 501, "items survive the save");
        helper.assertTrue(a.items.get(ItemKey.of(new ItemStack(Items.COBBLESTONE))) == 500, "cobblestone count");
        helper.assertTrue(a.items.get(ItemKey.of(sword)) == 1, "damaged sword is a separate type");
        DiskStore.Disk f = loaded.getOrNull(fluidId);
        helper.assertTrue(f != null && f.storedFluids == 1234 && f.fluids.get(0).getAmount() == 1234, "fluids survive the save");

        ItemStack moved = itemDisk.copy();
        helper.assertTrue(DiskStorage.extractItem(moved, new ItemStack(Items.COBBLESTONE), 100, false) == 100, "extract via the copy");
        helper.assertTrue(DiskStorage.storedItems(itemDisk) == 401, "copy and original share the same storage");
        helper.assertTrue(DiskStorage.extractItem(moved, new ItemStack(Items.COBBLESTONE), 1000, false) == 400, "rest of cobble");
        helper.assertTrue(DiskStorage.extractItem(moved, sword, 5, false) == 1, "sword back");
        helper.assertTrue(DiskStorage.storedItems(itemDisk) == 0 && moved.get(ModDataComponents.STORED.get()) == null,
                "empty disk drops its STORED counter");
        helper.succeed();
    }

    // ------------------------------------------------------------- меню

    /**
     * Бегунок в трубах движется без скачков: в каждый момент его рисует ровно одна труба, соседние положения рядом, на
     * развороте он стоит на месте и смотрит в одну сторону, сдаёт ровно тот груз, что нёс, и уходит с тем, что получил.
     * Г-образная линия с ответвлением и вертикальным тупиком, а также одиночная труба между двумя устройствами.
     */
    @GameTest(template = T, timeoutTicks = 20)
    public static void pipeRunnerMovesContinuously(GameTestHelper helper) {
        List<BlockPos> line = new ArrayList<>();
        for (int x = 1; x <= 6; x++) {
            line.add(new BlockPos(x, 1, 1));
        }
        line.add(new BlockPos(4, 1, 2));
        line.add(new BlockPos(4, 1, 3));
        line.add(new BlockPos(6, 2, 1));
        line.add(new BlockPos(6, 3, 1));
        for (BlockPos p : line) {
            helper.setBlock(p, ModBlocks.BASIC_SNOW_PIPE.get());
        }
        BlockPos lonely = new BlockPos(2, 1, 6);
        helper.setBlock(lonely.west(), ModBlocks.ANALOG_TERMINAL.get());
        helper.setBlock(lonely.east(), ModBlocks.ANALOG_TERMINAL.get());
        helper.setBlock(lonely, ModBlocks.BASIC_SNOW_PIPE.get());
        // setBlock ставит трубу без расчёта связей: досчитываем их по соседям, как при обычной установке
        List<BlockPos> all = new ArrayList<>(line);
        all.add(lonely);
        for (BlockPos p : all) {
            BlockPos abs = helper.absolutePos(p);
            helper.getLevel().setBlock(abs, net.minecraft.world.level.block.Block.updateFromNeighbourShapes(
                    helper.getLevel().getBlockState(abs), helper.getLevel(), abs), 3);
        }
        helper.runAfterDelay(3, () -> {
            checkRunner(helper, line, 26 * 40);
            checkRunner(helper, List.of(lonely), 60 * 6);
            helper.succeed();
        });
    }

    private static void checkRunner(GameTestHelper helper, List<BlockPos> pipes, int ticks) {
        var level = helper.getLevel();
        net.minecraft.world.phys.Vec3 last = null;
        com.golemlogix.client.PipeRunners.Spot lastSpot = null;
        long base = 1_234_567L;
        for (int k = 0; k <= ticks * 4; k++) {
            long time = base + k / 4;
            float partial = (k % 4) / 4f;
            com.golemlogix.client.PipeRunners.Spot found = null;
            BlockPos where = null;
            int count = 0;
            for (BlockPos rel : pipes) {
                BlockPos abs = helper.absolutePos(rel);
                var spot = com.golemlogix.client.PipeRunners.locate(level, abs, level.getBlockState(abs), time, partial);
                if (spot != null) {
                    count++;
                    found = spot;
                    where = abs;
                }
            }
            helper.assertTrue(count == 1, "exactly one pipe draws the runner at tick " + k / 4f + ", got " + count);
            net.minecraft.world.phys.Vec3 at = new net.minecraft.world.phys.Vec3(where.getX() + 0.5 + found.dx(),
                    where.getY() + 0.5 + found.dy(), where.getZ() + 0.5 + found.dz());
            if (last != null) {
                helper.assertTrue(at.distanceTo(last) < 0.05, "the runner jumps " + at.distanceTo(last) + " blocks at tick " + k / 4f);
                if (found.isTurning() && !lastSpot.isTurning()) {
                    helper.assertTrue(found.incomingCargo() == lastSpot.outgoingCargo(), "hands over the cargo it carried");
                }
                if (!found.isTurning() && lastSpot.isTurning()) {
                    helper.assertTrue(found.outgoingCargo() == lastSpot.outgoingCargo(), "leaves with the cargo it got");
                }
                if (found.isTurning() && lastSpot.isTurning() && found.turnProgress() > lastSpot.turnProgress()) {
                    helper.assertTrue(found.heading() == lastSpot.heading(), "faces one way during a turn");
                }
            }
            last = at;
            lastSpot = found;
        }
    }

    /** Планировщик заказа: создаваемое, взятое из сети и недостающее — по строкам, недостающее первым. */
    @GameTest(template = T, timeoutTicks = 40)
    public static void craftPlannerRows(GameTestHelper helper) {
        BlockPos bay = baseNetwork(helper);
        helper.setBlock(PROV, ModBlocks.PATTERN_PROVIDER.get().defaultBlockState());
        PatternProviderBlockEntity provider = (PatternProviderBlockEntity) helper.getBlockEntity(PROV);
        provider.getContainer().setItem(0, card(craftingPattern(new ItemStack(Items.OAK_PLANKS, 4), new ItemStack(Items.OAK_LOG))));
        provider.getContainer().setItem(1, card(craftingPattern(new ItemStack(Items.STICK, 4), new ItemStack(Items.OAK_PLANKS),
                ItemStack.EMPTY, ItemStack.EMPTY, new ItemStack(Items.OAK_PLANKS))));
        helper.runAfterDelay(3, () -> {
            GolemNetwork n = net(helper, bay);
            n.insertItem(new ItemStack(Items.OAK_LOG), false);
            ItemKey stick = ItemKey.of(new ItemStack(Items.STICK)), plank = ItemKey.of(new ItemStack(Items.OAK_PLANKS)),
                    log = ItemKey.of(new ItemStack(Items.OAK_LOG));
            var ok = TerminalMenu.planEntries(com.golemlogix.crafting.CraftingPlanner.plan(n, stick, 8));
            java.util.Map<ItemKey, com.golemlogix.network.CraftPlanPayload.Entry> rows = new java.util.HashMap<>();
            for (var e : ok) {
                rows.put(ItemKey.of(e.stack()), e);
            }
            helper.assertTrue(rows.get(stick).craft() == 8 && rows.get(plank).craft() == 4 && rows.get(log).stored() == 1
                    && rows.get(log).missing() == 0, "8 sticks: craft 8 sticks and 4 planks from the one log");
            var short_ = TerminalMenu.planEntries(com.golemlogix.crafting.CraftingPlanner.plan(n, stick, 40));
            var first = short_.get(0);
            helper.assertTrue(ItemKey.of(first.stack()).equals(log) && first.missing() == 4 && first.stored() == 1,
                    "40 sticks: the log row comes first with 4 missing, got " + first);
            helper.succeed();
        });
    }

    /**
     * Насыщатель до сборки — наковальня: Кузнечный молот за четыре удара делает из блока железа одно ядро, каждый удар
     * списывает заряд молота (если Source требуется); разряженный молот не бьёт; вручную куются только три базовые пластины.
     */
    @GameTest(template = T, timeoutTicks = 120)
    public static void saturatorAnvilForgesByHand(GameTestHelper helper) {
        BlockPos at = new BlockPos(2, 1, 2);
        helper.setBlock(at, ModBlocks.MANA_SATURATOR.get().defaultBlockState());
        ServerPlayer player = testPlayer(helper);
        var sat = (com.golemlogix.blockentity.ManaSaturatorBlockEntity) helper.getBlockEntity(at);
        for (ItemStack only : new ItemStack[]{new ItemStack(ModItems.IMPLODED_WITHER_PLASMA.get()),
                new ItemStack(ModBlocks.COOLING_COPROCESSOR.get())}) {
            helper.assertTrue(!com.golemlogix.blockentity.ManaSaturatorBlockEntity.isManualInput(only), only + " needs the machine");
        }
        boolean source = com.golemlogix.system.SourceHelper.sourceRequired();
        ItemStack hammer = new ItemStack(ModItems.FORGE_HAMMER.get());
        int hits = com.golemlogix.blockentity.ManaSaturatorBlockEntity.MANUAL_HITS;
        int cost = com.golemlogix.items.ForgeHammerItem.STRIKE_COST;
        com.golemlogix.items.ForgeHammerItem.setCharge(hammer, cost * hits + 50);
        sat.placeOnAnvil(new ItemStack(Items.IRON_BLOCK));
        for (int k = 0; k < hits; k++) {
            int strike = k;
            helper.runAfterDelay(2 + k * 8, () -> {
                var r = sat.strike(helper.getLevel(), helper.absolutePos(at), player, hammer);
                helper.assertTrue(r == (strike == hits - 1 ? com.golemlogix.blockentity.ManaSaturatorBlockEntity.Strike.DONE
                        : com.golemlogix.blockentity.ManaSaturatorBlockEntity.Strike.HIT), "strike " + strike + " -> " + r);
            });
        }
        helper.runAfterDelay(4 + hits * 8, () -> {
            helper.assertTrue(sat.anvilItem().is(ModItems.FLATTENED_IRON_CORE.get()) && sat.anvilItem().getCount() == 1,
                    "one flattened iron core by hand, got " + sat.anvilItem());
            helper.assertTrue(!source || com.golemlogix.items.ForgeHammerItem.getCharge(hammer) == 50,
                    "each strike spends the hammer's charge, left " + com.golemlogix.items.ForgeHammerItem.getCharge(hammer));
            helper.assertTrue(!helper.getBlockState(at).getValue(com.golemlogix.blocks.ManaSaturatorBlock.FORMED), "still an anvil");
            // разряженный молот (когда Source нужен) не бьёт
            sat.takeFromAnvil();
            sat.placeOnAnvil(new ItemStack(Items.PACKED_ICE));
        });
        helper.runAfterDelay(14 + hits * 8, () -> {
            var r = sat.strike(helper.getLevel(), helper.absolutePos(at), player, hammer);
            helper.assertTrue(r == (source ? com.golemlogix.blockentity.ManaSaturatorBlockEntity.Strike.NO_SOURCE
                    : com.golemlogix.blockentity.ManaSaturatorBlockEntity.Strike.HIT), "an empty hammer -> " + r);
            helper.succeed();
        });
    }
    /**
     * Хронометр, через который открыт терминал, нельзя вынуть из его ячейки (клик, Shift, Q, обмен цифрой или F со второй
     * рукой) — иначе окно закрылось бы; остальные предметы инвентаря двигаются как обычно.
     */
    @GameTest(template = T, timeoutTicks = 40)
    public static void chronometerSlotIsLocked(GameTestHelper helper) {
        ServerPlayer player = testPlayer(helper);
        BlockPos anchor = helper.absolutePos(CORE);
        var inv = player.getInventory();
        inv.setItem(0, new ItemStack(ModItems.PORTABLE_CHRONOMETER.get()));
        inv.setItem(1, new ItemStack(Items.DIRT, 5));
        TerminalMenu menu = new TerminalMenu(79, inv, helper.getLevel(), anchor, null, null, true, true, 0, 0);
        int locked = menu.lockedMenuSlot();
        helper.assertTrue(locked >= 0, "the chronometer's hotbar slot is locked in the menu");
        int dirtSlot = -1, other = -1;
        for (int i = 0; i < menu.slots.size(); i++) {
            var s = menu.slots.get(i);
            if (s.container == inv && s.getContainerSlot() == 1) {
                dirtSlot = i;
            }
            if (s.container == inv && s.getContainerSlot() == 9) {
                other = i;
            }
        }
        for (var type : new net.minecraft.world.inventory.ClickType[]{net.minecraft.world.inventory.ClickType.PICKUP,
                net.minecraft.world.inventory.ClickType.QUICK_MOVE, net.minecraft.world.inventory.ClickType.THROW,
                net.minecraft.world.inventory.ClickType.PICKUP_ALL}) {
            menu.clicked(locked, 0, type, player);
            helper.assertTrue(inv.getItem(0).is(ModItems.PORTABLE_CHRONOMETER.get()) && menu.getCarried().isEmpty(),
                    "the chronometer stays put after " + type);
        }
        menu.clicked(other, 0, net.minecraft.world.inventory.ClickType.SWAP, player);
        helper.assertTrue(inv.getItem(0).is(ModItems.PORTABLE_CHRONOMETER.get()), "the hotbar key of its slot does not swap it out");
        menu.clicked(dirtSlot, 0, net.minecraft.world.inventory.ClickType.PICKUP, player);
        helper.assertTrue(menu.getCarried().is(Items.DIRT), "other items still move");
        menu.clicked(locked, 0, net.minecraft.world.inventory.ClickType.PICKUP, player);
        helper.assertTrue(menu.getCarried().is(Items.DIRT) && inv.getItem(0).is(ModItems.PORTABLE_CHRONOMETER.get()),
                "nothing can be dropped onto the chronometer either");
        menu.setCarried(ItemStack.EMPTY);
        // открыт со второй руки: обмен клавишей F (кнопка 40) не трогает её
        inv.setItem(0, ItemStack.EMPTY);
        inv.setItem(1, new ItemStack(Items.DIRT, 5));
        inv.offhand.set(0, new ItemStack(ModItems.PORTABLE_CHRONOMETER.get()));
        TerminalMenu off = new TerminalMenu(80, inv, helper.getLevel(), anchor, null, null, true, true, 0, net.minecraft.world.entity.player.Inventory.SLOT_OFFHAND);
        off.clicked(dirtSlot, net.minecraft.world.entity.player.Inventory.SLOT_OFFHAND, net.minecraft.world.inventory.ClickType.SWAP, player);
        helper.assertTrue(inv.offhand.get(0).is(ModItems.PORTABLE_CHRONOMETER.get()) && inv.getItem(1).is(Items.DIRT),
                "the offhand chronometer is not swapped away");
        helper.succeed();
    }

    @GameTest(template = T, timeoutTicks = 200)
    public static void terminalMenuActions(GameTestHelper helper) {
        BlockPos bay = baseNetwork(helper);
        helper.setBlock(TERM, ModBlocks.ANALOG_TERMINAL.get().defaultBlockState());
        ServerPlayer player = testPlayer(helper);
        helper.runAfterDelay(5, () -> {
            GolemNetwork n = net(helper, bay);
            n.insertItem(new ItemStack(Items.IRON_INGOT, 100), false);
            ItemKey iron = ItemKey.of(new ItemStack(Items.IRON_INGOT));
            BlockPos abs = helper.absolutePos(TERM);
            TerminalMenu menu = new TerminalMenu(77, player.getInventory(), helper.getLevel(), abs, abs,
                    ModBlocks.ANALOG_TERMINAL.get(), false, false, 0);
            ItemStack key = new ItemStack(Items.IRON_INGOT);

            menu.handleAction(player, new TerminalActionPayload(77, Action.TAKE_STACK, key, FluidStack.EMPTY));
            helper.assertTrue(menu.getCarried().getCount() == 64, "carried 64, got " + menu.getCarried().getCount());
            helper.assertTrue(n.itemCountOf(iron) == 36, "36 left in network");

            menu.handleAction(player, new TerminalActionPayload(77, Action.INSERT_ONE, ItemStack.EMPTY, FluidStack.EMPTY));
            helper.assertTrue(menu.getCarried().getCount() == 63, "carried 63");
            helper.assertTrue(n.itemCountOf(iron) == 37, "37 in network");

            menu.handleAction(player, new TerminalActionPayload(77, Action.INSERT_ALL, ItemStack.EMPTY, FluidStack.EMPTY));
            helper.assertTrue(menu.getCarried().isEmpty(), "carried empty after insert all");
            helper.assertTrue(n.itemCountOf(iron) == 100, "all 100 back");

            menu.handleAction(player, new TerminalActionPayload(77, Action.TAKE_HALF, key, FluidStack.EMPTY));
            helper.assertTrue(menu.getCarried().getCount() == 32, "half stack = 32, got " + menu.getCarried().getCount());
            menu.handleAction(player, new TerminalActionPayload(77, Action.TAKE_ONE, key, FluidStack.EMPTY));
            helper.assertTrue(menu.getCarried().getCount() == 33, "plus one");
            menu.handleAction(player, new TerminalActionPayload(77, Action.TAKE_STACK, new ItemStack(Items.DIAMOND), FluidStack.EMPTY));
            helper.assertTrue(menu.getCarried().is(Items.IRON_INGOT), "still iron");

            menu.setCarried(ItemStack.EMPTY);
            menu.handleAction(player, new TerminalActionPayload(77, Action.TO_INVENTORY, key, FluidStack.EMPTY));
            int inInv = 0;
            for (ItemStack s : player.getInventory().items) {
                if (s.is(Items.IRON_INGOT)) {
                    inInv += s.getCount();
                }
            }
            helper.assertTrue(inInv == 64, "64 moved to inventory, got " + inInv);

            int slot = menu.slots.size() - 1;
            for (int i = 0; i < menu.slots.size(); i++) {
                if (menu.slots.get(i).getItem().is(Items.IRON_INGOT)) {
                    slot = i;
                    break;
                }
            }
            menu.quickMoveStack(player, slot);
            helper.assertTrue(n.itemCountOf(iron) > 36, "quick move inserted into the network");
            helper.succeed();
        });
    }

    @GameTest(template = T, timeoutTicks = 200)
    public static void craftingTerminalGrid(GameTestHelper helper) {
        BlockPos bay = baseNetwork(helper);
        helper.setBlock(TERM, ModBlocks.CRAFTING_TERMINAL.get().defaultBlockState());
        ServerPlayer player = testPlayer(helper);
        helper.runAfterDelay(5, () -> {
            GolemNetwork n = net(helper, bay);
            n.insertItem(new ItemStack(Items.OAK_PLANKS, 10), false);
            BlockPos abs = helper.absolutePos(TERM);
            TerminalMenu menu = new TerminalMenu(78, player.getInventory(), helper.getLevel(), abs, abs,
                    ModBlocks.CRAFTING_TERMINAL.get(), true, false, 0);
            menu.getSlot(1).set(new ItemStack(Items.OAK_PLANKS));
            menu.getSlot(4).set(new ItemStack(Items.OAK_PLANKS));
            helper.assertTrue(menu.getSlot(0).getItem().is(Items.STICK), "result is sticks");
            helper.assertTrue(menu.getSlot(0).getItem().getCount() == 4, "4 sticks");

            menu.getSlot(0).onTake(player, menu.getSlot(0).getItem());
            helper.assertTrue(menu.getSlot(1).getItem().is(Items.OAK_PLANKS), "grid refilled from the network (top)");
            helper.assertTrue(menu.getSlot(4).getItem().is(Items.OAK_PLANKS), "grid refilled from the network (bottom)");
            helper.assertTrue(n.itemCountOf(ItemKey.of(new ItemStack(Items.OAK_PLANKS))) == 8, "two planks pulled from the network");

            menu.clickMenuButton(player, 0);
            helper.assertTrue(menu.getSlot(1).getItem().isEmpty(), "grid cleared");
            helper.assertTrue(n.itemCountOf(ItemKey.of(new ItemStack(Items.OAK_PLANKS))) == 10, "planks returned");
            helper.succeed();
        });
    }

    @GameTest(template = T, timeoutTicks = 100)
    public static void patternEncoder(GameTestHelper helper) {
        ServerPlayer player = testPlayer(helper);
        // кодировщик хранит содержимое у себя: окно открываем на его блок-сущности
        BlockPos encPos = new BlockPos(2, 1, 2);
        helper.setBlock(encPos, ModBlocks.PATTERN_ENCODER.get());
        var encoder = (com.golemlogix.blockentity.PatternEncoderBlockEntity) helper.getBlockEntity(encPos);
        PatternEncoderMenu menu = new PatternEncoderMenu(79, player.getInventory(), encoder.getItems(), encoder.data(), ContainerLevelAccess.NULL);
        menu.getContainer().setItem(0, new ItemStack(Items.OAK_PLANKS));
        menu.getContainer().setItem(3, new ItemStack(Items.OAK_PLANKS));
        menu.getContainer().setItem(PatternEncoderMenu.CARD_IN, new ItemStack(ModItems.COPPER_PUNCH_CARD.get(), 3));
        helper.assertTrue(menu.getContainer().getItem(PatternEncoderMenu.OUT_START).is(Items.STICK), "recipe preview");
        menu.clickMenuButton(player, PatternEncoderMenu.BUTTON_ENCODE);
        ItemStack encoded = menu.getContainer().getItem(PatternEncoderMenu.CARD_OUT);
        helper.assertTrue(!encoded.isEmpty(), "card encoded");
        PatternData data = encoded.get(ModDataComponents.PATTERN.get());
        helper.assertTrue(data != null && data.crafting() && data.primaryOutput().is(Items.STICK), "pattern content");
        helper.assertTrue(menu.getContainer().getItem(PatternEncoderMenu.CARD_IN).getCount() == 2, "one blank consumed");
        // чистые карты — стопкой до 64, записанная — штучная
        helper.assertTrue(new ItemStack(ModItems.COPPER_PUNCH_CARD.get()).getMaxStackSize() == 64, "blank cards stack to 64");
        helper.assertTrue(encoded.getMaxStackSize() == 1, "an encoded card does not stack");

        // правка: записанная карта, положенная в ячейку готовой карты, открывает свой рецепт; «Выбить» перезаписывает её
        ItemStack card = menu.getContainer().removeItemNoUpdate(PatternEncoderMenu.CARD_OUT);
        menu.clickMenuButton(player, PatternEncoderMenu.BUTTON_CLEAR);
        menu.getSlot(PatternEncoderMenu.CARD_OUT).setByPlayer(card, ItemStack.EMPTY);
        helper.assertTrue(menu.getContainer().getItem(0).is(Items.OAK_PLANKS), "the card's recipe is back in the grid");
        menu.getContainer().setItem(3, ItemStack.EMPTY);
        menu.getContainer().setItem(0, new ItemStack(Items.OAK_LOG));
        menu.clickMenuButton(player, PatternEncoderMenu.BUTTON_ENCODE);
        PatternData edited = menu.getContainer().getItem(PatternEncoderMenu.CARD_OUT).get(ModDataComponents.PATTERN.get());
        helper.assertTrue(edited != null && edited.primaryOutput().is(Items.OAK_PLANKS), "the same card now makes planks");
        helper.assertTrue(menu.getContainer().getItem(PatternEncoderMenu.CARD_IN).getCount() == 2, "no blank spent on a rewrite");
        // закрыли окно — чистые карты остались в кодировщике
        menu.removed(player);
        helper.assertTrue(encoder.getItems().getItem(PatternEncoderMenu.CARD_IN).getCount() == 2, "blank cards stay in the encoder");
        menu = new PatternEncoderMenu(80, player.getInventory(), encoder.getItems(), encoder.data(), ContainerLevelAccess.NULL);

        // Shift+ПКМ с записанной картой в руке стирает её
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, menu.getContainer().getItem(PatternEncoderMenu.CARD_OUT).copy());
        player.setShiftKeyDown(true);
        player.getMainHandItem().use(helper.getLevel(), player, net.minecraft.world.InteractionHand.MAIN_HAND);
        player.setShiftKeyDown(false);
        helper.assertTrue(!com.golemlogix.items.CopperPunchCardItem.hasPattern(player.getMainHandItem())
                && player.getMainHandItem().is(ModItems.COPPER_PUNCH_CARD.get()), "shift+use clears the card");

        menu.clickMenuButton(player, PatternEncoderMenu.BUTTON_CLEAR);
        menu.getContainer().setItem(PatternEncoderMenu.CARD_OUT, ItemStack.EMPTY);
        menu.clickMenuButton(player, PatternEncoderMenu.BUTTON_TOGGLE);
        helper.assertTrue(!menu.isCraftingMode(), "processing mode");
        menu.getContainer().setItem(0, new ItemStack(Items.RAW_IRON, 2));
        menu.getContainer().setItem(PatternEncoderMenu.OUT_START, new ItemStack(Items.IRON_INGOT, 2));
        menu.clickMenuButton(player, PatternEncoderMenu.BUTTON_ENCODE);
        PatternData p2 = menu.getContainer().getItem(PatternEncoderMenu.CARD_OUT).get(ModDataComponents.PATTERN.get());
        helper.assertTrue(p2 != null && !p2.crafting() && p2.inputs().get(0).getCount() == 2, "processing pattern keeps counts");

        menu.clickMenuButton(player, PatternEncoderMenu.BUTTON_CLEAR);
        menu.getContainer().setItem(PatternEncoderMenu.CARD_OUT, ItemStack.EMPTY);
        menu.clickMenuButton(player, PatternEncoderMenu.BUTTON_ENCODE);
        helper.assertTrue(menu.getContainer().getItem(PatternEncoderMenu.CARD_OUT).isEmpty(), "empty pattern rejected");
        helper.succeed();
    }

    @GameTest(template = T, timeoutTicks = 200)
    public static void jobPersistence(GameTestHelper helper) {
        BlockPos bay = baseNetwork(helper);
        helper.setBlock(CPU, ModBlocks.CRYO_CALCULATOR.get().defaultBlockState());
        helper.setBlock(PROV, ModBlocks.PATTERN_PROVIDER.get().defaultBlockState());
        PatternProviderBlockEntity provider = (PatternProviderBlockEntity) helper.getBlockEntity(PROV);
        provider.getContainer().setItem(0, card(new PatternData(false,
                List.of(new ItemStack(Items.RAW_IRON, 2)), List.of(new ItemStack(Items.IRON_INGOT)))));
        helper.runAfterDelay(5, () -> {
            GolemNetwork n = net(helper, bay);
            n.insertItem(new ItemStack(Items.RAW_IRON, 8), false);
            GolemNetwork.StartResult r = n.startCrafting(ItemKey.of(new ItemStack(Items.IRON_INGOT)), 4);
            helper.assertTrue(r.type() == GolemNetwork.StartType.STARTED, "start " + r.type());
            CryoCalculatorBlockEntity cpu = (CryoCalculatorBlockEntity) helper.getBlockEntity(CPU);
            CompoundTag tag = cpu.saveWithoutMetadata(helper.getLevel().registryAccess());
            CryoCalculatorBlockEntity copy = new CryoCalculatorBlockEntity(cpu.getBlockPos(), cpu.getBlockState());
            copy.loadWithComponents(tag, helper.getLevel().registryAccess());
            helper.assertTrue(copy.getJob() != null, "job survives a save/load round trip");
            helper.assertTrue(copy.getJob().totalExecutions() == 4, "total " + copy.getJob().totalExecutions());
            helper.assertTrue(copy.getJob().target().equals(ItemKey.of(new ItemStack(Items.IRON_INGOT))), "target");
            helper.assertTrue(copy.getJob().inventory().equals(cpu.getJob().inventory()), "reserved inventory");
            helper.succeed();
        });
    }

    // ------------------------------------------------------------ данные

    @GameTest(template = T, timeoutTicks = 100)
    public static void dataIsComplete(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        String[] recipes = {"analog_terminal", "atlas_core", "atlas_casing", "atlas_conduit", "atlas_atlant", "channel_expander",
                "quantum_channel_expander", "basic_snow_pipe", "cable_bushing", "cooling_coprocessor",
                "copper_disk_1k", "copper_disk_4k", "copper_disk_16k", "copper_disk_64k", "copper_disk_256k",
                "copper_drive_bay", "copper_punch_card", "crafting_terminal", "cryo_calculator", "dense_frost_pipe",
                "eternal_frost_resonator", "fluid_disk_1k", "fluid_disk_4k", "fluid_disk_16k", "fluid_disk_64k",
                "fluid_disk_256k", "guide_book", "injector_export_bus", "mana_saturator", "pattern_encoder",
                "pattern_provider", "periscope_storage_bus", "portable_chronometer", "vacuum_import_bus", "chronometer_upgrade"};
        for (String r : recipes) {
            ResourceLocation id = ResourceLocation.fromNamespaceAndPath(GolemLogixMod.MOD_ID, r);
            helper.assertTrue(server.getRecipeManager().byKey(id).isPresent(), "missing recipe " + r);
        }
        String[] blocks = {"creative_source_jar", "mana_saturator", "atlas_core", "atlas_casing", "atlas_conduit", "atlas_atlant",
                "channel_expander", "quantum_channel_expander", "cable_bushing", "basic_snow_pipe",
                "dense_frost_pipe", "copper_drive_bay", "analog_terminal", "crafting_terminal", "pattern_encoder",
                "pattern_provider", "cryo_calculator", "cooling_coprocessor", "vacuum_import_bus", "injector_export_bus",
                "periscope_storage_bus", "eternal_frost_resonator"};
        for (String b : blocks) {
            ResourceKey<LootTable> key = ResourceKey.create(Registries.LOOT_TABLE,
                    ResourceLocation.fromNamespaceAndPath(GolemLogixMod.MOD_ID, "blocks/" + b));
            helper.assertTrue(server.reloadableRegistries().getLootTable(key) != LootTable.EMPTY, "missing loot table " + b);
        }
        helper.succeed();
    }

    @GameTest(template = T, timeoutTicks = 100)
    public static void chronometerUpgrade(GameTestHelper helper) {
        var level = helper.getLevel();
        var recipe = new ChronometerUpgradeRecipe(CraftingBookCategory.EQUIPMENT);
        ItemStack chrono = new ItemStack(ModItems.PORTABLE_CHRONOMETER.get());
        chrono.set(ModDataComponents.BOUND_CORE.get(), GlobalPos.of(level.dimension(), BlockPos.ZERO));

        CraftingInput step1 = CraftingInput.of(3, 1, List.of(chrono, new ItemStack(Items.ENDER_EYE), new ItemStack(ModItems.FLATTENED_COPPER_MATRIX.get())));
        helper.assertTrue(recipe.matches(step1, level), "tier 1 upgrade matches");
        ItemStack t1 = recipe.assemble(step1, level.registryAccess());
        helper.assertTrue(t1.get(ModDataComponents.WIRELESS_TIER.get()) == 1, "tier 1");
        helper.assertTrue(t1.get(ModDataComponents.BOUND_CORE.get()) != null, "binding survives the upgrade");

        CraftingInput skip = CraftingInput.of(2, 1, List.of(chrono, new ItemStack(ModItems.INTERDIMENSIONAL_OCCULT_MATRIX.get())));
        helper.assertTrue(!recipe.matches(skip, level), "cannot skip the dimensional tier");

        CraftingInput step2 = CraftingInput.of(2, 1, List.of(t1, new ItemStack(ModItems.INTERDIMENSIONAL_OCCULT_MATRIX.get())));
        helper.assertTrue(recipe.matches(step2, level), "tier 2 upgrade matches");
        helper.assertTrue(recipe.assemble(step2, level.registryAccess()).get(ModDataComponents.WIRELESS_TIER.get()) == 2, "tier 2");
        helper.succeed();
    }

    // ------------------------------------------------------------- книга

    private static void checkItem(GameTestHelper helper, String spec, String where) {
        String id = spec.contains("*") ? spec.substring(0, spec.indexOf('*')) : spec;
        ResourceLocation rl = ResourceLocation.tryParse(id);
        helper.assertTrue(rl != null && net.minecraft.core.registries.BuiltInRegistries.ITEM.containsKey(rl), "unknown item '" + id + "' in " + where);
    }

    @GameTest(template = T, timeoutTicks = 100)
    public static void bookDataIsValid(GameTestHelper helper) throws Exception {
        var server = helper.getLevel().getServer();
        java.util.Map<String, String> signatures = new java.util.HashMap<>();
        for (String lang : new String[]{"ru_ru", "en_us"}) {
            StringBuilder sig = new StringBuilder();
            try (var in = GolemGameTests.class.getResourceAsStream("/assets/golem_logix/book/" + lang + ".json")) {
                helper.assertTrue(in != null, "book file " + lang);
                com.google.gson.JsonObject root = com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
                java.util.Set<String> ids = new java.util.HashSet<>();
                for (var ce : root.getAsJsonArray("categories")) {
                    for (var ee : ce.getAsJsonObject().getAsJsonArray("entries")) {
                        String id = ee.getAsJsonObject().get("id").getAsString();
                        helper.assertTrue(ids.add(id), "duplicate entry id " + id);
                    }
                }
                for (var ce : root.getAsJsonArray("categories")) {
                    checkItem(helper, ce.getAsJsonObject().get("icon").getAsString(), "category icon");
                    for (var ee : ce.getAsJsonObject().getAsJsonArray("entries")) {
                        var e = ee.getAsJsonObject();
                        String id = e.get("id").getAsString();
                        checkItem(helper, e.get("icon").getAsString(), id);
                        if (e.has("parents")) {
                            for (var p : e.getAsJsonArray("parents")) {
                                helper.assertTrue(ids.contains(p.getAsString()), "entry " + id + " has unknown parent " + p.getAsString());
                            }
                        }
                        helper.assertTrue(e.getAsJsonArray("pages").size() > 0, "entry " + id + " has pages");
                        String secret = e.has("secret") ? e.get("secret").getAsString() : "";
                        helper.assertTrue(secret.isEmpty() || secret.equals("partial") || secret.equals("full"), "entry " + id + ": bad secret " + secret);
                        sig.append(ce.getAsJsonObject().get("id").getAsString()).append("/").append(id).append("@").append(e.get("x")).append(",").append(e.get("y"))
                                .append(e.has("parents") ? e.get("parents").toString() : "[]").append(e.has("gold") ? "G" : "").append(secret).append(":");
                        for (var pe : e.getAsJsonArray("pages")) {
                            var page = pe.getAsJsonObject();
                            String type = page.get("type").getAsString();
                            String when = page.has("when") ? page.get("when").getAsString() : "";
                            helper.assertTrue(java.util.List.of("", "wither", "dimension", "!wither", "!dimension", "both", "one").contains(when),
                                    "entry " + id + ": bad page condition " + when);
                            sig.append(type).append(page.has("recipe") ? page.get("recipe").getAsString() : "")
                                    .append(page.has("texture") ? page.get("texture").getAsString() : "")
                                    .append(page.has("layer") ? page.get("layer").getAsString() : "")
                                    .append(page.has("variant") ? page.get("variant").getAsString() : "")
                                    .append(page.has("station") ? page.get("station").getAsString() : "")
                                    .append(page.has("inputs") ? page.get("inputs").toString() : "")
                                    .append(page.has("outputs") ? page.get("outputs").toString() : "")
                                    .append(page.has("grid") ? page.get("grid").toString() + page.get("result").getAsString() : "")
                                    .append(when.isEmpty() ? "" : "?" + when).append(",");
                            if (type.equals("tasks")) {
                                helper.assertTrue(page.has("tasks") && page.getAsJsonArray("tasks").size() > 0, "entry " + id + ": tasks page without tasks");
                                for (var t : page.getAsJsonArray("tasks")) {
                                    sig.append(t.getAsJsonObject().get("id").getAsString()).append("+");
                                }
                            }
                            if (page.has("recipe")) {
                                ResourceLocation rid = ResourceLocation.parse(page.get("recipe").getAsString());
                                helper.assertTrue(server.getRecipeManager().byKey(rid).isPresent(), "entry " + id + ": unknown recipe " + rid);
                            }
                            if (page.has("grid")) {
                                for (var g : page.getAsJsonArray("grid")) {
                                    if (!g.getAsString().isEmpty()) {
                                        checkItem(helper, g.getAsString(), id + " grid");
                                    }
                                }
                                checkItem(helper, page.get("result").getAsString(), id + " result");
                            }
                            for (String key : new String[]{"inputs", "outputs"}) {
                                if (page.has(key)) {
                                    for (var s : page.getAsJsonArray(key)) {
                                        checkItem(helper, s.getAsString(), id + " " + key);
                                    }
                                }
                            }
                            for (String key : new String[]{"station", "item"}) {
                                if (page.has(key)) {
                                    checkItem(helper, page.get(key).getAsString(), id + " " + key);
                                }
                            }
                            if (type.equals("image")) {
                                String tex = "/assets/golem_logix/textures/gui/book/" + page.get("texture").getAsString() + ".png";
                                helper.assertTrue(GolemGameTests.class.getResource(tex) != null, "missing image " + tex);
                            }
                            if (type.equals("blueprint")) {
                                int layer = page.get("layer").getAsInt();
                                helper.assertTrue(layer >= 0 && layer <= 3, "blueprint layer");
                            }
                            helper.assertTrue(page.has("recipe") || page.has("grid") || !type.equals("crafting"), "crafting page needs a recipe or grid");
                        }
                        sig.append(";");
                    }
                }
                signatures.put(lang, sig.toString());
            }
        }
        helper.assertTrue(signatures.get("ru_ru").equals(signatures.get("en_us")), "ru and en books must have the same structure");
        helper.succeed();
    }

    // ------------------------------------------------- шкаф, муфта, насыщатель, терминал

    private static BlockPos[] bayCells() {
        return new BlockPos[]{BAY, BAY.east(), BAY.above(), BAY.east().above()};
    }

    @GameTest(template = T, timeoutTicks = 100)
    public static void bayHasTenSlots(GameTestHelper helper) {
        putBay(helper, BAY);
        CopperDriveBayBlockEntity be = (CopperDriveBayBlockEntity) helper.getBlockEntity(BAY);
        helper.assertTrue(CopperDriveBayBlockEntity.SLOTS == 10, "cabinet must have 10 slots");
        helper.assertTrue(be.getContainer().getContainerSize() == 10 && be.getDisks().size() == 10, "container size 10");
        var menu = new com.golemlogix.menu.DriveBayMenu(0, testPlayer(helper).getInventory(), be.getContainer(), be.priorityData());
        helper.assertTrue(menu.slots.stream().filter(s -> s.container == be.getContainer()).count() == 10, "menu shows 10 cassette slots");
        helper.succeed();
    }

    /** Шкаф занимает 2x2 клетки: части, направление, блок-сущность только в главной. */
    @GameTest(template = T, timeoutTicks = 100)
    public static void cabinetIsTwoByTwo(GameTestHelper helper) {
        putBay(helper, BAY);
        BlockPos[] cells = bayCells();
        CopperDriveBayBlock.Part[] parts = CopperDriveBayBlock.Part.values();
        for (int i = 0; i < 4; i++) {
            BlockState s = helper.getBlockState(cells[i]);
            helper.assertTrue(s.is(ModBlocks.COPPER_DRIVE_BAY.get()), "cell " + i + " belongs to the cabinet");
            helper.assertTrue(s.getValue(CopperDriveBayBlock.PART) == parts[i], "part of cell " + i);
            helper.assertTrue(s.getValue(CopperDriveBayBlock.FACING) == Direction.SOUTH, "facing of cell " + i);
            helper.assertTrue((helper.getLevel().getBlockEntity(helper.absolutePos(cells[i])) instanceof CopperDriveBayBlockEntity) == (i == 0),
                    "only the master part has a block entity, cell " + i);
            BlockPos m = CopperDriveBayBlock.masterOf(helper.absolutePos(cells[i]), s);
            helper.assertTrue(m.equals(helper.absolutePos(BAY)), "master is found from part " + i);
        }
        helper.succeed();
    }

    /** Любая из четырёх частей ломается — исчезает весь шкаф, предмет выпадает один раз, диски не пропадают. */
    @GameTest(template = T, timeoutTicks = 200)
    public static void cabinetBreaksAsOne(GameTestHelper helper) {
        BlockPos[] cells = bayCells();
        for (int victim = 0; victim < 4; victim++) {
            putBay(helper, BAY);
            ((CopperDriveBayBlockEntity) helper.getBlockEntity(BAY)).getContainer().setItem(3, new ItemStack(ModItems.COPPER_DISK_1K.get()));
            helper.getLevel().destroyBlock(helper.absolutePos(cells[victim]), true);
            for (BlockPos c : cells) {
                helper.assertTrue(helper.getBlockState(c).isAir(), "cell " + c + " must vanish after breaking part " + victim);
            }
            helper.assertItemEntityCountIs(ModBlocks.COPPER_DRIVE_BAY.get().asItem(), BAY, 3.0, 1);
            helper.assertItemEntityCountIs(ModItems.COPPER_DISK_1K.get(), BAY, 3.0, 1);
            helper.killAllEntities();
        }
        helper.succeed();
    }

    /** Предметом игрока: ставится целиком там, где хватает места, и не ставится, если хоть одна клетка занята. */
    @GameTest(template = T, timeoutTicks = 100)
    public static void cabinetPlacementByPlayer(GameTestHelper helper) {
        ServerPlayer player = testPlayer(helper);
        player.setYRot(180f);   // смотрит на север: шкаф обернётся лицом на юг
        helper.setBlock(BAY.below(), Blocks.STONE);
        helper.setBlock(BAY.east().above(), Blocks.STONE);
        net.minecraft.world.phys.BlockHitResult hit = new net.minecraft.world.phys.BlockHitResult(
                net.minecraft.world.phys.Vec3.atCenterOf(helper.absolutePos(BAY.below())).add(0, 0.5, 0), Direction.UP, helper.absolutePos(BAY.below()), false);
        ItemStack stack = new ItemStack(ModBlocks.COPPER_DRIVE_BAY.get().asItem());
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, stack);
        var blocked = stack.useOn(new net.minecraft.world.item.context.UseOnContext(player, net.minecraft.world.InteractionHand.MAIN_HAND, hit));
        helper.assertTrue(!blocked.consumesAction(), "no room for the upper right part: the cabinet must not be placed");
        helper.assertTrue(helper.getBlockState(BAY).isAir(), "nothing was placed");

        helper.setBlock(BAY.east().above(), Blocks.AIR);
        var placed = stack.useOn(new net.minecraft.world.item.context.UseOnContext(player, net.minecraft.world.InteractionHand.MAIN_HAND, hit));
        helper.assertTrue(placed.consumesAction(), "placed when there is room");
        for (BlockPos c : bayCells()) {
            helper.assertTrue(helper.getBlockState(c).is(ModBlocks.COPPER_DRIVE_BAY.get()), "part placed at " + c);
            helper.assertTrue(helper.getBlockState(c).getValue(CopperDriveBayBlock.FACING) == Direction.SOUTH, "faces the player");
        }
        helper.succeed();
    }

    /** Шкаф — одно устройство сети (один канал), состояние active разлито на все части. */
    @GameTest(template = T, timeoutTicks = 200)
    public static void cabinetCountsOnce(GameTestHelper helper) {
        BlockPos bay = baseNetwork(helper);
        helper.runAfterDelay(25, () -> {
            GolemNetwork n = net(helper, bay);
            helper.assertTrue(n.deviceCount() == 1, "one cabinet is one device, got " + n.deviceCount());
            helper.assertTrue(n.channelsUsed() == 1, "one channel, got " + n.channelsUsed());
            helper.assertTrue(n.isDeviceActive(helper.absolutePos(bay)), "the master part has the channel");
            for (BlockPos c : bayCells()) {
                helper.assertTrue(helper.getBlockState(c).getValue(com.golemlogix.blocks.GolemProperties.ACTIVE), "part " + c + " is active");
            }
            helper.succeed();
        });
    }

    /** Фильтры шины сохраняют свои ячейки (центральная ячейка не уезжает в первую при перезагрузке мира). */
    @GameTest(template = T, timeoutTicks = 100)
    public static void busFilterKeepsSlots(GameTestHelper helper) {
        BlockPos at = new BlockPos(2, 1, 2);
        helper.setBlock(at, ModBlocks.VACUUM_IMPORT_BUS.get());
        var registries = helper.getLevel().registryAccess();
        BusBlockEntity be = (BusBlockEntity) helper.getBlockEntity(at);
        be.getFilters().setItem(4, new ItemStack(Items.DIAMOND));
        be.getFilters().setItem(8, new ItemStack(Items.GOLD_INGOT));
        CompoundTag saved = be.saveWithFullMetadata(registries);
        BusBlockEntity fresh = new BusBlockEntity(helper.absolutePos(at), helper.getBlockState(at));
        fresh.loadWithComponents(saved, registries);
        helper.assertTrue(fresh.getFilters().getItem(4).is(Items.DIAMOND), "centre filter slot is kept");
        helper.assertTrue(fresh.getFilters().getItem(8).is(Items.GOLD_INGOT), "last filter slot is kept");
        helper.assertTrue(fresh.getFilters().getItem(0).isEmpty(), "first slot stays empty");
        helper.succeed();
    }

    /** Каждый блок, которому нужен инструмент, можно добыть киркой (иначе он «не дропался» бы никогда), и у каждого блока есть дроп и модель. */
    @GameTest(template = T, timeoutTicks = 100)
    public static void everyBlockIsHarvestable(GameTestHelper helper) {
        for (var entry : ModBlocks.BLOCKS.getEntries()) {
            Block block = entry.get();
            BlockState state = block.defaultBlockState();
            String id = entry.getId().getPath();
            if (state.requiresCorrectToolForDrops()) {
                helper.assertTrue(state.is(net.minecraft.tags.BlockTags.MINEABLE_WITH_PICKAXE), id + " needs a tool but is not in mineable/pickaxe");
            }
            helper.assertTrue(GolemGameTests.class.getResource("/assets/golem_logix/blockstates/" + id + ".json") != null, id + " has a blockstate");
            if (block instanceof com.golemlogix.blocks.SaturatorPartBlock || block instanceof com.golemlogix.blocks.MainframePartBlock
                    || block instanceof com.golemlogix.blocks.CatalogPartBlock) {
                continue; // блок-часть собранного многоблока: предмета, дропа и модели предмета у неё нет
            }
            helper.assertTrue(block.asItem() != Items.AIR, id + " has an item");
            var lootId = block.getLootTable();
            helper.assertTrue(lootId != net.minecraft.world.level.storage.loot.BuiltInLootTables.EMPTY, id + " has a loot table");
            helper.assertTrue(helper.getLevel().getServer().reloadableRegistries().getLootTable(lootId) != LootTable.EMPTY, id + " loot table exists in data");
            helper.assertTrue(GolemGameTests.class.getResource("/assets/golem_logix/blockstates/" + id + ".json") != null, id + " has a blockstate");
            helper.assertTrue(GolemGameTests.class.getResource("/assets/golem_logix/models/item/" + id + ".json") != null, id + " has an item model");
        }
        helper.succeed();
    }

    /** Труба стыкуется с трубой той же толщины «стеклом», с другой толщиной и с устройствами — латунным рукавом. */
    @GameTest(template = T, timeoutTicks = 100)
    public static void pipeLinksByKind(GameTestHelper helper) {
        BlockPos p = new BlockPos(2, 1, 2);
        put(helper, p, ModBlocks.BASIC_SNOW_PIPE.get());
        put(helper, p.east(), ModBlocks.BASIC_SNOW_PIPE.get());
        put(helper, p.west(), ModBlocks.DENSE_FROST_PIPE.get());
        put(helper, p.north(), ModBlocks.ANALOG_TERMINAL.get());
        BlockState s = helper.getBlockState(p);
        helper.assertTrue(com.golemlogix.blocks.SnowPipeBlock.link(s, Direction.EAST) == com.golemlogix.blocks.SnowPipeBlock.Link.PIPE, "same kind: plain glass");
        helper.assertTrue(com.golemlogix.blocks.SnowPipeBlock.link(s, Direction.WEST) == com.golemlogix.blocks.SnowPipeBlock.Link.DEVICE, "thin into thick: sleeve");
        helper.assertTrue(com.golemlogix.blocks.SnowPipeBlock.link(helper.getBlockState(p.west()), Direction.EAST) == com.golemlogix.blocks.SnowPipeBlock.Link.REDUCER,
                "thick towards thin: reducer washer closes the gap");
        helper.assertTrue(com.golemlogix.blocks.SnowPipeBlock.link(s, Direction.NORTH) == com.golemlogix.blocks.SnowPipeBlock.Link.DEVICE, "device: sleeve");
        helper.assertTrue(com.golemlogix.blocks.SnowPipeBlock.link(s, Direction.SOUTH) == com.golemlogix.blocks.SnowPipeBlock.Link.NONE, "nothing: end");
        put(helper, new BlockPos(4, 1, 6), ModBlocks.DENSE_FROST_PIPE.get());
        putBay(helper, new BlockPos(5, 1, 6));
        helper.assertTrue(com.golemlogix.blocks.SnowPipeBlock.link(helper.getBlockState(new BlockPos(4, 1, 6)), Direction.EAST)
                == com.golemlogix.blocks.SnowPipeBlock.Link.DEVICE, "a pipe meets the cabinet with a sleeve");
        helper.succeed();
    }

    /** Диски в шкафу не «сжимаются» к началу при сохранении, старый формат без номеров ячеек тоже читается. */
    @GameTest(template = T, timeoutTicks = 100)
    public static void bayKeepsSlotPositions(GameTestHelper helper) {
        putBay(helper, BAY);
        var registries = helper.getLevel().registryAccess();
        CopperDriveBayBlockEntity be = (CopperDriveBayBlockEntity) helper.getBlockEntity(BAY);
        be.getContainer().setItem(0, new ItemStack(ModItems.COPPER_DISK_1K.get()));
        be.getContainer().setItem(4, new ItemStack(ModItems.FLUID_DISK_4K.get()));
        be.getContainer().setItem(9, new ItemStack(ModItems.COPPER_DISK_256K.get()));
        CompoundTag saved = be.saveWithFullMetadata(registries);
        CopperDriveBayBlockEntity fresh = new CopperDriveBayBlockEntity(helper.absolutePos(BAY), helper.getBlockState(BAY));
        fresh.loadWithComponents(saved, registries);
        for (int i = 0; i < 10; i++) {
            net.minecraft.world.item.Item expect = i == 0 ? ModItems.COPPER_DISK_1K.get() : i == 4 ? ModItems.FLUID_DISK_4K.get()
                    : i == 9 ? ModItems.COPPER_DISK_256K.get() : Items.AIR;
            helper.assertTrue(fresh.getDisks().get(i).getItem() == expect, "slot " + i + " keeps its disk, found " + fresh.getDisks().get(i));
        }
        // старый формат: записи без Slot идут подряд с первой ячейки
        net.minecraft.nbt.ListTag old = new net.minecraft.nbt.ListTag();
        old.add(new ItemStack(ModItems.COPPER_DISK_4K.get()).save(registries));
        old.add(new ItemStack(ModItems.COPPER_DISK_16K.get()).save(registries));
        CompoundTag legacy = new CompoundTag();
        legacy.put("Disks", old);
        CopperDriveBayBlockEntity oldBe = new CopperDriveBayBlockEntity(helper.absolutePos(BAY), helper.getBlockState(BAY));
        oldBe.loadWithComponents(legacy, registries);
        helper.assertTrue(oldBe.getDisks().get(0).is(ModItems.COPPER_DISK_4K.get()) && oldBe.getDisks().get(1).is(ModItems.COPPER_DISK_16K.get()),
                "legacy list is read in order");
        helper.succeed();
    }

    @GameTest(template = T, timeoutTicks = 200)
    public static void bushingReportsLoad(GameTestHelper helper) {
        buildArray(helper, 0, 0);
        powered(helper);
        BlockPos bushing = new BlockPos(4, 1, 6);
        put(helper, TERM, ModBlocks.ANALOG_TERMINAL.get());
        put(helper, new BlockPos(3, 2, 6), ModBlocks.CRAFTING_TERMINAL.get());
        helper.runAfterDelay(25, () -> {
            GolemNetwork n = net(helper, ORIGIN);
            helper.assertTrue(n.loadAt(helper.absolutePos(bushing)) == 2, "bushing line carries both terminals, got " + n.loadAt(helper.absolutePos(bushing)));
            helper.assertTrue(n.loadAt(helper.absolutePos(new BlockPos(6, 1, 4))) == 0, "other lines are idle");
            // терминалы включились вместе с сетью (анимация экрана)
            helper.assertTrue(helper.getBlockState(TERM).getValue(com.golemlogix.blocks.GolemProperties.ACTIVE), "terminal must become active");
            helper.assertTrue(helper.getBlockEntity(TERM) instanceof com.golemlogix.blockentity.TerminalBlockEntity, "terminal has a block entity");
            helper.succeed();
        });
    }

    // ------------------------------------------------------------ Насыщатель Маны (многоблок 3x3x2)

    /** Ставит все блоки машины вокруг хозяина (controllerRel), фасадом в сторону facing; хозяин с нужным FACING. */
    private static void buildSaturator(GameTestHelper helper, BlockPos controllerRel, Direction facing) {
        var level = helper.getLevel();
        BlockPos at = helper.absolutePos(controllerRel);
        for (int i = 0; i < com.golemlogix.system.SaturatorStructure.CELLS; i++) {
            BlockState s = com.golemlogix.system.SaturatorStructure.required(i).defaultBlockState();
            if (i == com.golemlogix.system.SaturatorStructure.CONTROLLER) {
                s = s.setValue(com.golemlogix.blocks.ManaSaturatorBlock.FACING, facing);
            }
            level.setBlock(com.golemlogix.system.SaturatorStructure.cellPos(at, facing, i), s, 3);
        }
    }

    private static BlockPos satCell(GameTestHelper helper, BlockPos controllerRel, Direction facing, int index) {
        return com.golemlogix.system.SaturatorStructure.cellPos(helper.absolutePos(controllerRel), facing, index);
    }

    @GameTest(template = T, timeoutTicks = 300)
    public static void saturatorFillsFromNetwork(GameTestHelper helper) {
        buildArray(helper, 0, 0);
        powered(helper);
        // машина лицом на север: «центральный ящик» (9,1,9), труба сети подходит к нему спереди (9,1,8) от угловой муфты
        BlockPos near = new BlockPos(9, 3, 9);
        for (int x = 7; x <= 9; x++) {
            put(helper, new BlockPos(x, 1, 6), ModBlocks.BASIC_SNOW_PIPE.get());
        }
        put(helper, new BlockPos(9, 1, 7), ModBlocks.BASIC_SNOW_PIPE.get());
        put(helper, new BlockPos(9, 1, 8), ModBlocks.BASIC_SNOW_PIPE.get());
        // отводы к остальным разъёмам: слева (восток), справа (запад), сзади (юг), снизу; и труба на крыше — к ней разъёма нет
        BlockPos leftStub = new BlockPos(11, 1, 10), rightStub = new BlockPos(7, 1, 10), backStub = new BlockPos(9, 1, 11),
                bottomStub = new BlockPos(9, 0, 9), roofStub = new BlockPos(10, 4, 9);
        for (BlockPos stub : new BlockPos[]{leftStub, rightStub, backStub, bottomStub, roofStub}) {
            put(helper, stub, ModBlocks.BASIC_SNOW_PIPE.get());
        }
        buildSaturator(helper, near, Direction.NORTH);
        BlockPos far = new BlockPos(2, 3, 9);     // сам по себе, источников нет
        buildSaturator(helper, far, Direction.NORTH);
        helper.runAfterDelay(100, () -> {
            helper.assertTrue(helper.getBlockState(near).getValue(com.golemlogix.blocks.ManaSaturatorBlock.FORMED), "networked saturator forms by itself");
            var a = (com.golemlogix.blockentity.ManaSaturatorBlockEntity) helper.getBlockEntity(near);
            var b = (com.golemlogix.blockentity.ManaSaturatorBlockEntity) helper.getBlockEntity(far);
            helper.assertTrue(a.getSource() >= 300, "networked saturator must fill from the network, got " + a.getSource());
            if (com.golemlogix.system.SourceHelper.sourceRequired()) {
                helper.assertTrue(b.getSource() == 0, "isolated saturator stays empty, got " + b.getSource());
            } else {
                helper.assertTrue(b.getSource() > 0, "without Ars Nouveau the saturator fills for free, got " + b.getSource());
            }
            // труба видит только «центральный ящик»: рукав у порта есть, у крыла рядом — нет
            var pipe = helper.getBlockState(new BlockPos(9, 1, 8));
            helper.assertTrue(com.golemlogix.blocks.SnowPipeBlock.link(pipe, Direction.SOUTH) == com.golemlogix.blocks.SnowPipeBlock.Link.DEVICE, "pipe attaches to the port");
            put(helper, new BlockPos(11, 1, 9), ModBlocks.BASIC_SNOW_PIPE.get());
            helper.assertTrue(com.golemlogix.blocks.SnowPipeBlock.link(helper.getBlockState(new BlockPos(11, 1, 9)), Direction.WEST) == com.golemlogix.blocks.SnowPipeBlock.Link.NONE,
                    "a pipe beside a wing cell does not attach");
            var device = com.golemlogix.blocks.SnowPipeBlock.Link.DEVICE;
            helper.assertTrue(com.golemlogix.blocks.SnowPipeBlock.link(helper.getBlockState(leftStub), Direction.WEST) == device, "left side port");
            helper.assertTrue(com.golemlogix.blocks.SnowPipeBlock.link(helper.getBlockState(rightStub), Direction.EAST) == device, "right side port");
            helper.assertTrue(com.golemlogix.blocks.SnowPipeBlock.link(helper.getBlockState(backStub), Direction.NORTH) == device, "back port");
            helper.assertTrue(com.golemlogix.blocks.SnowPipeBlock.link(helper.getBlockState(bottomStub), Direction.UP) == device, "bottom port");
            helper.assertTrue(com.golemlogix.blocks.SnowPipeBlock.link(helper.getBlockState(roofStub), Direction.DOWN) == com.golemlogix.blocks.SnowPipeBlock.Link.NONE,
                    "no port on the roof");
            helper.succeed();
        });
    }

    @GameTest(template = T, timeoutTicks = 200)
    public static void saturatorFormsAndRestores(GameTestHelper helper) {
        BlockPos ctl = new BlockPos(5, 3, 5);
        buildSaturator(helper, ctl, Direction.WEST);
        var level = helper.getLevel();
        BlockPos abs = helper.absolutePos(ctl);
        // перед сборкой всё стоит как обычные блоки
        helper.assertTrue(com.golemlogix.system.SaturatorStructure.check(level, abs, Direction.WEST).valid(), "complete machine passes the check");
        helper.runAfterDelay(40, () -> {
            helper.assertTrue(helper.getBlockState(ctl).getValue(com.golemlogix.blocks.ManaSaturatorBlock.FORMED), "machine forms");
            for (int i = 0; i < com.golemlogix.system.SaturatorStructure.CELLS; i++) {
                if (i == com.golemlogix.system.SaturatorStructure.CONTROLLER) {
                    continue;
                }
                BlockPos p = com.golemlogix.system.SaturatorStructure.cellPos(abs, Direction.WEST, i);
                BlockState s = level.getBlockState(p);
                helper.assertTrue(s.getBlock() instanceof com.golemlogix.blocks.SaturatorPartBlock, "cell " + i + " became a part");
                helper.assertTrue(s.getValue(com.golemlogix.blocks.SaturatorPartBlock.CELL) == i, "cell index " + i);
                var be = (com.golemlogix.blockentity.SaturatorPartBlockEntity) level.getBlockEntity(p);
                helper.assertTrue(be.getOriginal().is(com.golemlogix.system.SaturatorStructure.required(i)), "remembers the original block of cell " + i);
            }
            // добыча части отдаёт прежний блок, а не часть
            BlockPos rodPos = com.golemlogix.system.SaturatorStructure.cellPos(abs, Direction.WEST, com.golemlogix.system.SaturatorStructure.ROD);
            var drops = Block.getDrops(level.getBlockState(rodPos), level, rodPos, level.getBlockEntity(rodPos));
            helper.assertTrue(drops.size() == 1 && drops.get(0).is(Items.END_ROD), "a part drops its original block");
            // сломали любую часть -> остальное вернулось в обычные блоки, хозяин снова «несобранный»
            BlockPos wing = com.golemlogix.system.SaturatorStructure.cellPos(abs, Direction.WEST, com.golemlogix.system.SaturatorStructure.index(0, 1, 1));
            level.destroyBlock(wing, false);
            helper.assertTrue(!helper.getBlockState(ctl).getValue(com.golemlogix.blocks.ManaSaturatorBlock.FORMED), "machine unforms");
            for (int i = 0; i < com.golemlogix.system.SaturatorStructure.CELLS; i++) {
                BlockPos p = com.golemlogix.system.SaturatorStructure.cellPos(abs, Direction.WEST, i);
                if (p.equals(wing)) {
                    continue;
                }
                helper.assertTrue(level.getBlockState(p).is(com.golemlogix.system.SaturatorStructure.required(i)), "cell " + i + " is an ordinary block again");
            }
            helper.assertTrue(level.getBlockState(wing).isAir(), "the broken cell stays empty");
            helper.succeed();
        });
    }

    @GameTest(template = T, timeoutTicks = 420)
    public static void saturatorCrafts(GameTestHelper helper) {
        BlockPos ctl = new BlockPos(5, 3, 5);
        buildSaturator(helper, ctl, Direction.NORTH);
        // банка Source рядом с левым нижним углом машины
        put(helper, new BlockPos(7, 1, 5), ModBlocks.CREATIVE_SOURCE_JAR.get());
        helper.runAfterDelay(30, () -> {
            var sat = (com.golemlogix.blockentity.ManaSaturatorBlockEntity) helper.getBlockEntity(ctl);
            helper.assertTrue(helper.getBlockState(ctl).getValue(com.golemlogix.blocks.ManaSaturatorBlock.FORMED), "forms");
            // ПКМ любой частью (боковой корпус) с блоком в руке кладёт блок на наковальню
            var player = testPlayer(helper);
            ItemStack iron = new ItemStack(Items.IRON_BLOCK, 2);
            BlockPos partPos = com.golemlogix.system.SaturatorStructure.cellPos(helper.absolutePos(ctl), Direction.NORTH, com.golemlogix.system.SaturatorStructure.index(0, 0, 0));
            var hit = new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(partPos), Direction.NORTH, partPos, false);
            var result = helper.getLevel().getBlockState(partPos).useItemOn(iron, helper.getLevel(), player, net.minecraft.world.InteractionHand.MAIN_HAND, hit);
            helper.assertTrue(result.consumesAction(), "a part forwards the click to the head");
            helper.assertTrue(sat.inputDeck().is(Items.IRON_BLOCK), "the block lies on the left deck, waiting for the carrier");
            helper.assertTrue(iron.getCount() == 1, "one block was taken from the hand");
        });
        helper.runAfterDelay(380, () -> {
            var sat = (com.golemlogix.blockentity.ManaSaturatorBlockEntity) helper.getBlockEntity(ctl);
            helper.assertTrue(sat.outputDeck().is(ModItems.FLATTENED_IRON_CORE.get()) && sat.outputDeck().getCount() == 2,
                    "iron block becomes two flattened cores on the right deck, got " + sat.outputDeck());
            helper.succeed();
        });
    }
    /** Настоящая банка Source из Ars Nouveau питает сеть и расходуется (только с -PtestMods; без Ars Nouveau проверять нечего). */
    @GameTest(template = T, timeoutTicks = 200)
    public static void arsSourceJarPowersNetwork(GameTestHelper helper) {
        ResourceLocation jarId = ResourceLocation.fromNamespaceAndPath("ars_nouveau", "source_jar");
        if (!com.golemlogix.system.SourceHelper.arsNouveauLoaded() || !net.minecraft.core.registries.BuiltInRegistries.BLOCK.containsKey(jarId)) {
            helper.succeed();
            return;
        }
        buildArray(helper, 0, 0);
        helper.setBlock(JAR, net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(jarId));
        var supplier = com.golemlogix.system.SourceHelper.asSupplier(helper.getBlockEntity(JAR));
        helper.assertTrue(supplier != null, "an Ars Nouveau jar is a Source supplier");
        helper.runAfterDelay(5, () -> {
            helper.assertTrue(!net(helper, ORIGIN).isOnline(), "an empty jar does not power the network");
            // наполняем банку так же, как это делает Креативный Сосуд
            helper.setBlock(JAR.below(), ModBlocks.CREATIVE_SOURCE_JAR.get());
            helper.runAfterDelay(30, () -> {
                helper.setBlock(JAR.below(), Blocks.STONE);
                int full = supplier.available();
                helper.assertTrue(full > 1000, "the creative jar fills the Ars jar, got " + full);
                helper.runAfterDelay(50, () -> {
                    helper.assertTrue(net(helper, ORIGIN).isOnline(), "a filled Ars jar powers the network");
                    helper.assertTrue(supplier.available() < full, "the core drains the jar: " + supplier.available() + " < " + full);
                    helper.succeed();
                });
            });
        });
    }

    /** Автоматика через любую часть насыщателя: кладётся только заготовка, забирается только готовый результат. */
    @GameTest(template = T, timeoutTicks = 440)
    public static void saturatorAutomation(GameTestHelper helper) {
        BlockPos ctl = new BlockPos(5, 3, 5);
        buildSaturator(helper, ctl, Direction.NORTH);
        put(helper, new BlockPos(7, 1, 5), ModBlocks.CREATIVE_SOURCE_JAR.get());
        BlockPos side = com.golemlogix.system.SaturatorStructure.cellPos(helper.absolutePos(ctl), Direction.NORTH,
                com.golemlogix.system.SaturatorStructure.index(2, 1, 1));
        helper.runAfterDelay(30, () -> {
            var h = helper.getLevel().getCapability(net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK, side, null);
            helper.assertTrue(h != null, "a saturator part exposes the anvil to automation");
            helper.assertTrue(h.insertItem(0, new ItemStack(Items.DIRT), false).getCount() == 1, "dirt is rejected");
            helper.assertTrue(h.insertItem(1, new ItemStack(Items.IRON_BLOCK), false).getCount() == 1, "the output deck takes nothing");
            helper.assertTrue(h.insertItem(0, new ItemStack(Items.IRON_BLOCK, 3), false).isEmpty(), "three iron blocks queue on the left deck");
            helper.assertTrue(h.extractItem(0, 64, false).isEmpty(), "the blank cannot be pulled back out");
        });
        helper.runAfterDelay(400, () -> {
            var h = helper.getLevel().getCapability(net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK, side, null);
            ItemStack out = h.extractItem(1, 64, false);
            // конвейер: подносчик приносит следующую заготовку, пока кузнец куёт, — за это время готовы все три
            helper.assertTrue(out.is(ModItems.FLATTENED_IRON_CORE.get()) && out.getCount() == 6, "six flattened cores come out, got " + out);
            helper.assertTrue(h.getStackInSlot(0).isEmpty(), "the queue is used up");
            helper.succeed();
        });
    }

    // ------------------------------------------------------- ритуал наковальни

    /** Голем без ИИ с заданным здоровьем и наковальня в четырёх блоках над ним: она падает на землю сквозь голема. */
    @SuppressWarnings("unchecked")
    private static net.minecraft.world.entity.Mob ritual(GameTestHelper helper, net.minecraft.world.entity.EntityType<?> type, BlockPos at, float health) {
        var golem = helper.spawnWithNoFreeWill((net.minecraft.world.entity.EntityType<net.minecraft.world.entity.Mob>) type, at);
        if (health > 0) {
            golem.setHealth(health);
        }
        helper.setBlock(at.above(4), Blocks.ANVIL);
        return golem;
    }

    private static void assertCrushed(GameTestHelper helper, net.minecraft.world.entity.Mob golem, BlockPos at, net.minecraft.world.item.Item plate) {
        helper.succeedWhen(() -> {
            helper.assertItemEntityPresent(plate, at, 2.0);
            helper.assertTrue(golem.isRemoved(), "the golem is flattened (removed)");
        });
    }

    @GameTest(template = T, timeoutTicks = 100)
    public static void anvilRitualIronGolem(GameTestHelper helper) {
        BlockPos at = new BlockPos(4, 1, 4);
        assertCrushed(helper, ritual(helper, net.minecraft.world.entity.EntityType.IRON_GOLEM, at, 3f), at, ModItems.FLATTENED_IRON_CORE.get());
    }

    @GameTest(template = T, timeoutTicks = 100)
    public static void anvilRitualSnowGolem(GameTestHelper helper) {
        BlockPos at = new BlockPos(4, 1, 4);
        assertCrushed(helper, ritual(helper, net.minecraft.world.entity.EntityType.SNOW_GOLEM, at, 3f), at, ModItems.FLATTENED_CRYO_SUBSTRATE.get());
    }

    /** Медный голем — из Copper Age Backport (minecraft:copper_golem), обязательной зависимости мода. */
    @GameTest(template = T, timeoutTicks = 100)
    public static void anvilRitualCopperGolem(GameTestHelper helper) {
        var id = ResourceLocation.withDefaultNamespace("copper_golem");
        helper.assertTrue(net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.containsKey(id), "Copper Age Backport must register minecraft:copper_golem");
        BlockPos at = new BlockPos(4, 1, 4);
        var type = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.get(id);
        assertCrushed(helper, ritual(helper, type, at, 3f), at, ModItems.FLATTENED_COPPER_MATRIX.get());
    }

    @GameTest(template = T, timeoutTicks = 100)
    public static void anvilRitualNeedsWoundedGolem(GameTestHelper helper) {
        BlockPos at = new BlockPos(4, 1, 4);
        var golem = ritual(helper, net.minecraft.world.entity.EntityType.IRON_GOLEM, at, 0f);
        helper.runAfterDelay(40, () -> {
            helper.assertItemEntityNotPresent(ModItems.FLATTENED_IRON_CORE.get(), at, 3.0);
            helper.assertTrue(golem.isAlive() && golem.getHealth() < golem.getMaxHealth(), "a healthy golem only takes the hit");
            helper.succeed();
        });
    }

    // ------------------------------------------------- перископ, жидкости, приоритеты

    /** Тестовый «бак»: у Магнетита нет жидкостной ёмкости, поэтому даём её через capability, привязав бак к позиции. */
    @net.neoforged.fml.common.EventBusSubscriber(modid = GolemLogixMod.MOD_ID, bus = net.neoforged.fml.common.EventBusSubscriber.Bus.MOD)
    public static final class TestCaps {
        static final java.util.Map<BlockPos, net.neoforged.neoforge.fluids.capability.templates.FluidTank> TANKS = new java.util.HashMap<>();

        static net.neoforged.neoforge.fluids.capability.templates.FluidTank tank(BlockPos abs) {
            return TANKS.computeIfAbsent(abs.immutable(), p -> new net.neoforged.neoforge.fluids.capability.templates.FluidTank(10000));
        }

        @net.neoforged.bus.api.SubscribeEvent
        public static void register(net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent event) {
            event.registerBlock(net.neoforged.neoforge.capabilities.Capabilities.FluidHandler.BLOCK,
                    (level, pos, state, be, side) -> tank(pos), Blocks.LODESTONE);
        }
    }

    private static void periscope(GameTestHelper helper, BlockPos at, Direction facing) {
        helper.setBlock(at, ModBlocks.PERISCOPE_STORAGE_BUS.get().defaultBlockState()
                .setValue(com.golemlogix.blocks.PeriscopeStorageBusBlock.FACING, facing));
    }

    @GameTest(template = T, timeoutTicks = 200)
    public static void periscopeSeesTanks(GameTestHelper helper) {
        buildArray(helper, 0, 0);
        powered(helper);
        BlockPos tankRel = new BlockPos(8, 1, 6);
        periscope(helper, new BlockPos(7, 1, 6), Direction.EAST);
        helper.setBlock(tankRel, Blocks.LODESTONE);
        var tank = TestCaps.tank(helper.absolutePos(tankRel));
        tank.setFluid(new FluidStack(Fluids.WATER, 4000));
        helper.runAfterDelay(20, () -> {
            GolemNetwork n = net(helper, ORIGIN);
            helper.assertTrue(n.isOnline(), "powered");
            long water = 0;
            for (FluidStack f : n.listFluids()) {
                if (f.getFluid() == Fluids.WATER) {
                    water += f.getAmount();
                }
            }
            helper.assertTrue(water == 4000, "periscope must list the tank's 4000 mB of water, got " + water);
            helper.assertTrue(n.insertFluid(new FluidStack(Fluids.WATER, 500), false) == 500, "500 mB must be poured into the tank");
            helper.assertTrue(tank.getFluidAmount() == 4500, "tank now holds 4500, got " + tank.getFluidAmount());
            FluidStack got = n.extractFluid(new FluidStack(Fluids.WATER, 1), 1500, false);
            helper.assertTrue(got.getAmount() == 1500 && tank.getFluidAmount() == 3000, "extracted 1500 from the tank");
            helper.succeed();
        });
    }

    @GameTest(template = T, timeoutTicks = 300)
    public static void storagePriority(GameTestHelper helper) {
        buildArray(helper, 0, 0);
        powered(helper);
        BlockPos chestA = new BlockPos(8, 1, 6), chestB = new BlockPos(4, 1, 8);
        helper.setBlock(chestA, Blocks.CHEST);
        helper.setBlock(chestB, Blocks.CHEST);
        periscope(helper, new BlockPos(7, 1, 6), Direction.EAST);
        periscope(helper, new BlockPos(4, 1, 7), Direction.SOUTH);
        var pa = (com.golemlogix.blockentity.PeriscopeBlockEntity) helper.getBlockEntity(new BlockPos(7, 1, 6));
        var pb = (com.golemlogix.blockentity.PeriscopeBlockEntity) helper.getBlockEntity(new BlockPos(4, 1, 7));
        pb.setPriority(5);
        helper.runAfterDelay(20, () -> {
            GolemNetwork n = net(helper, ORIGIN);
            helper.assertTrue(n.insertItem(new ItemStack(Items.STONE, 10), false).isEmpty(), "stone fits");
            var a = (net.minecraft.world.level.block.entity.ChestBlockEntity) helper.getBlockEntity(chestA);
            var b = (net.minecraft.world.level.block.entity.ChestBlockEntity) helper.getBlockEntity(chestB);
            helper.assertTrue(count(a, Items.STONE) == 0 && count(b, Items.STONE) == 10, "the higher priority chest B gets the stone first");
            pa.setPriority(10);
            GolemNetwork n2 = net(helper, ORIGIN);
            helper.assertTrue(n2.insertItem(new ItemStack(Items.DIRT, 10), false).isEmpty(), "dirt fits");
            helper.assertTrue(count(a, Items.DIRT) == 10 && count(b, Items.DIRT) == 0, "after raising A above B, A gets the dirt");
            // изымаем из наименее приоритетного хранилища первым
            helper.assertTrue(n2.extractItem(new ItemStack(Items.STONE), 4, false).getCount() == 4 && count(b, Items.STONE) == 6, "extraction works");
            helper.succeed();
        });
    }

    private static int count(net.minecraft.world.Container c, net.minecraft.world.item.Item item) {
        int n = 0;
        for (int i = 0; i < c.getContainerSize(); i++) {
            if (c.getItem(i).is(item)) {
                n += c.getItem(i).getCount();
            }
        }
        return n;
    }

    @GameTest(template = T, timeoutTicks = 200)
    public static void busPriorityRank(GameTestHelper helper) {
        buildArray(helper, 0, 0);
        powered(helper);
        BlockPos a = new BlockPos(3, 1, 6), b = new BlockPos(4, 1, 7);
        put(helper, a, ModBlocks.VACUUM_IMPORT_BUS.get());
        put(helper, b, ModBlocks.INJECTOR_EXPORT_BUS.get());
        var ba = (BusBlockEntity) helper.getBlockEntity(a);
        var bb = (BusBlockEntity) helper.getBlockEntity(b);
        bb.setPriority(7);
        helper.runAfterDelay(10, () -> {
            GolemNetwork n = net(helper, ORIGIN);
            helper.assertTrue(n.busRank(bb) == 0 && n.busRank(ba) == 1, "higher priority bus goes first");
            ba.setPriority(50);
            helper.assertTrue(net(helper, ORIGIN).busRank(ba) == 0, "now the first bus leads");
            helper.succeed();
        });
    }
    // ------------------------------------------------- автокрафт: цепочки и граничные случаи

    private static final BlockPos MACHINE_OUT = new BlockPos(2, 1, 4);

    private static void autocraftRig(GameTestHelper helper) {
        helper.setBlock(CPU, ModBlocks.CRYO_CALCULATOR.get().defaultBlockState());
        helper.setBlock(PROV, ModBlocks.PATTERN_PROVIDER.get().defaultBlockState());
        helper.setBlock(PROV.east(), Blocks.CHEST);              // «вход машины»
        helper.setBlock(BUS_IN, ModBlocks.VACUUM_IMPORT_BUS.get().defaultBlockState().setValue(DirectionalBlock.FACING, Direction.WEST));
        helper.setBlock(MACHINE_OUT, Blocks.CHEST);              // «выход машины», его забирает шина импорта
    }

    /**
     * Автокрафт через Насыщатель Маны: шаблон «железный блок -> 2 ядра», Поставщик вплотную к части машины сам кладёт блок на
     * наковальню, шина импорта у другой части возвращает пластины задаче.
     */
    @GameTest(template = T, timeoutTicks = 700)
    public static void autocraftThroughSaturator(GameTestHelper helper) {
        BlockPos bay = baseNetwork(helper);
        // от угловой муфты (6,1,6): трубы на восток и к Поставщику; калькулятор у трубы
        for (int x = 7; x <= 9; x++) {
            put(helper, new BlockPos(x, 1, 6), ModBlocks.BASIC_SNOW_PIPE.get());
        }
        put(helper, new BlockPos(9, 1, 7), ModBlocks.BASIC_SNOW_PIPE.get());
        helper.setBlock(new BlockPos(7, 1, 7), ModBlocks.CRYO_CALCULATOR.get().defaultBlockState());
        BlockPos prov = new BlockPos(9, 1, 8);
        helper.setBlock(prov, ModBlocks.PATTERN_PROVIDER.get().defaultBlockState());
        put(helper, new BlockPos(10, 1, 8), ModBlocks.BASIC_SNOW_PIPE.get());
        BlockPos bus = new BlockPos(11, 1, 8);
        helper.setBlock(bus, ModBlocks.VACUUM_IMPORT_BUS.get().defaultBlockState().setValue(DirectionalBlock.FACING, Direction.SOUTH));
        // машина лицом на юг: клетки x 9..11, y 1..3, z 9..10; Поставщик и шина касаются задних нижних клеток
        BlockPos ctl = new BlockPos(10, 3, 10);
        buildSaturator(helper, ctl, Direction.SOUTH);
        put(helper, new BlockPos(12, 1, 10), ModBlocks.CREATIVE_SOURCE_JAR.get());
        PatternProviderBlockEntity provider = (PatternProviderBlockEntity) helper.getBlockEntity(prov);
        provider.getContainer().setItem(0, card(new PatternData(false,
                List.of(new ItemStack(Items.IRON_BLOCK)), List.of(new ItemStack(ModItems.FLATTENED_IRON_CORE.get(), 2)))));
        ItemKey core = ItemKey.of(new ItemStack(ModItems.FLATTENED_IRON_CORE.get()));
        helper.runAfterDelay(5, () -> {
            GolemNetwork n = net(helper, bay);
            n.insertItem(new ItemStack(Items.IRON_BLOCK, 1), false);
            helper.assertTrue(n.canCraft(core), "the saturator pattern is known to the network");
            var started = n.startCrafting(core, 2);
            helper.assertTrue(started.type() == GolemNetwork.StartType.STARTED, "started: " + started.type());
        });
        helper.runAfterDelay(650, () -> {
            GolemNetwork n = net(helper, bay);
            helper.assertTrue(n.itemCountOf(core) == 2, "two cores arrived through the import bus, got " + n.itemCountOf(core));
            helper.assertTrue(n.itemCountOf(ItemKey.of(new ItemStack(Items.IRON_BLOCK))) == 0, "the iron block was used");
            var sat = (com.golemlogix.blockentity.ManaSaturatorBlockEntity) helper.getBlockEntity(ctl);
            helper.assertTrue(sat.getHeldItem().isEmpty() && sat.outputDeck().isEmpty(), "the anvil and the output deck are empty again");
            helper.succeed();
        });
    }

    /** Сырое железо -> слиток (машина, 9 раз) -> железный блок (верстак 3x3): весь путь через шину импорта. */
    @GameTest(template = T, timeoutTicks = 600)
    public static void autocraftMachineThenCrafting(GameTestHelper helper) {
        BlockPos bay = baseNetwork(helper);
        autocraftRig(helper);
        PatternProviderBlockEntity provider = (PatternProviderBlockEntity) helper.getBlockEntity(PROV);
        provider.getContainer().setItem(0, card(new PatternData(false,
                List.of(new ItemStack(Items.RAW_IRON)), List.of(new ItemStack(Items.IRON_INGOT)))));
        ItemStack ingot = new ItemStack(Items.IRON_INGOT);
        provider.getContainer().setItem(1, card(craftingPattern(new ItemStack(Items.IRON_BLOCK),
                ingot.copy(), ingot.copy(), ingot.copy(), ingot.copy(), ingot.copy(), ingot.copy(), ingot.copy(), ingot.copy(), ingot.copy())));
        Container machineIn = (Container) helper.getBlockEntity(PROV.east());
        Container machineOut = (Container) helper.getBlockEntity(MACHINE_OUT);

        helper.runAfterDelay(5, () -> {
            GolemNetwork n = net(helper, bay);
            n.insertItem(new ItemStack(Items.RAW_IRON, 9), false);
            ItemKey block = ItemKey.of(new ItemStack(Items.IRON_BLOCK));
            CraftingPlan plan = CraftingPlanner.plan(n, block, 1);
            helper.assertTrue(plan.missing().isEmpty() && plan.steps().size() == 2 && plan.totalExecutions() == 10,
                    "plan: " + plan.steps().size() + " steps, " + plan.totalExecutions() + " executions, missing " + plan.missing());
            helper.assertTrue(plan.steps().get(0).pattern().crafting() == false, "the machine step goes first");
            helper.assertTrue(n.startCrafting(block, 1).type() == GolemNetwork.StartType.STARTED, "started");
        });
        helper.runAfterDelay(120, () -> {
            int raw = 0;
            for (int i = 0; i < machineIn.getContainerSize(); i++) {
                raw += machineIn.getItem(i).getCount();
            }
            helper.assertTrue(raw == 9, "machine received all 9 raw iron, got " + raw);
            machineIn.clearContent();
            machineOut.setItem(0, new ItemStack(Items.IRON_INGOT, 9)); // «машина» отработала
        });
        helper.runAfterDelay(300, () -> {
            GolemNetwork n = net(helper, bay);
            helper.assertTrue(machineOut.isEmpty(), "import bus took the ingots out of the machine");
            helper.assertTrue(n.itemCountOf(ItemKey.of(new ItemStack(Items.IRON_BLOCK))) == 1, "iron block delivered, got "
                    + n.itemCountOf(ItemKey.of(new ItemStack(Items.IRON_BLOCK))));
            helper.assertTrue(n.itemCountOf(ItemKey.of(new ItemStack(Items.IRON_INGOT))) == 0, "no ingots left over");
            helper.assertTrue(((CryoCalculatorBlockEntity) helper.getBlockEntity(CPU)).isIdle(), "calculator idle");
            helper.succeed();
        });
    }

    /** Хранилище забито доверху: выход машины всё равно принимается, а готовое доставляется, как только появится место. */
    @GameTest(template = T, timeoutTicks = 500)
    public static void autocraftWithFullStorage(GameTestHelper helper) {
        BlockPos bay = baseNetwork(helper);
        autocraftRig(helper);
        PatternProviderBlockEntity provider = (PatternProviderBlockEntity) helper.getBlockEntity(PROV);
        provider.getContainer().setItem(0, card(new PatternData(false,
                List.of(new ItemStack(Items.RAW_IRON)), List.of(new ItemStack(Items.IRON_INGOT)))));
        Container machineIn = (Container) helper.getBlockEntity(PROV.east());
        Container machineOut = (Container) helper.getBlockEntity(MACHINE_OUT);

        helper.runAfterDelay(5, () -> {
            GolemNetwork n = net(helper, bay);
            n.insertItem(new ItemStack(Items.RAW_IRON, 1), false);
            helper.assertTrue(n.startCrafting(ItemKey.of(new ItemStack(Items.IRON_INGOT)), 1).type() == GolemNetwork.StartType.STARTED, "started");
            // забиваем диск до отказа
            for (int i = 0; i < 20; i++) {
                n.insertItem(new ItemStack(Items.STONE, 64), false);
            }
        });
        helper.runAfterDelay(60, () -> {
            helper.assertTrue(!machineIn.isEmpty(), "raw iron went to the machine");
            machineOut.setItem(0, new ItemStack(Items.IRON_INGOT));
        });
        helper.runAfterDelay(120, () -> {
            helper.assertTrue(machineOut.isEmpty(), "the bus must take the ingot although the storage is full");
            GolemNetwork n = net(helper, bay);
            helper.assertTrue(n.itemCountOf(ItemKey.of(new ItemStack(Items.IRON_INGOT))) == 0, "ingot waits inside the job");
            helper.assertTrue(!((CryoCalculatorBlockEntity) helper.getBlockEntity(CPU)).isIdle(), "job still delivering");
            n.extractItem(new ItemStack(Items.STONE), 64, false);       // освободили место
        });
        helper.runAfterDelay(200, () -> {
            GolemNetwork n = net(helper, bay);
            helper.assertTrue(n.itemCountOf(ItemKey.of(new ItemStack(Items.IRON_INGOT))) == 1, "ingot delivered once there is room");
            helper.assertTrue(((CryoCalculatorBlockEntity) helper.getBlockEntity(CPU)).isIdle(), "calculator idle");
            helper.succeed();
        });
    }
    @GameTest(template = T, timeoutTicks = 100)
    public static void blueprintMatchesArray(GameTestHelper helper) {
        buildArray(helper, 3, 2);
        helper.runAfterDelay(2, () -> {
            var cells = AtlasBlueprint.cells(AtlasBlueprint.Variant.FULL);
            helper.assertTrue(cells.size() == 36, "full blueprint has 36 cells, got " + cells.size());
            for (AtlasBlueprint.Cell c : cells) {
                BlockState actual = helper.getBlockState(ORIGIN.offset(c.dx(), c.dy(), c.dz()));
                helper.assertTrue(c.matches(actual), "cell " + c.dx() + "," + c.dy() + "," + c.dz() + " must match the built array, found " + actual.getBlock());
            }
            var need = AtlasBlueprint.requirements(AtlasBlueprint.Variant.FULL);
            helper.assertTrue(need.get(ModBlocks.ATLAS_CORE.get().asItem()) == 1, "1 core");
            helper.assertTrue(need.get(ModBlocks.CABLE_BUSHING.get().asItem()) == 5, "5 bushings");
            helper.assertTrue(need.get(ModBlocks.ATLAS_CONDUIT.get().asItem()) == 6, "6 conduits");
            helper.assertTrue(need.get(ModBlocks.ATLAS_ATLANT.get().asItem()) == 4, "4 atlants (one item per two blocks)");
            helper.assertTrue(need.get(ModBlocks.ATLAS_CASING.get().asItem()) == 16, "16 casings");
            var tower = AtlasBlueprint.requirements(AtlasBlueprint.Variant.TOWER);
            helper.assertTrue(tower.size() == 3 && tower.get(ModBlocks.ATLAS_CONDUIT.get().asItem()) == 2, "tower: core, 2 conduits, bushing");

            // неверная половина Атланта и пустая клетка не совпадают с чертежом
            AtlasBlueprint.Cell upper = cells.stream().filter(c -> c.dy() == 2 && c.dx() == 1 && c.dz() == 1).findFirst().get();
            helper.assertTrue(!upper.matches(ModBlocks.ATLAS_ATLANT.get().defaultBlockState().setValue(AtlantBlock.HALF, DoubleBlockHalf.LOWER)), "wrong half");
            helper.assertTrue(!upper.matches(Blocks.AIR.defaultBlockState()), "air does not match");
            helper.succeed();
        });
    }

    /** Чертёж Насыщателя совпадает с тем, что требует сборка: 18 клеток, набор предметов, поворот по фасаду, части считаются верными. */
    @GameTest(template = T, timeoutTicks = 100)
    public static void saturatorBlueprint(GameTestHelper helper) {
        var need = AtlasBlueprint.requirements(AtlasBlueprint.Variant.SATURATOR);
        helper.assertTrue(need.get(ModBlocks.MANA_SATURATOR.get().asItem()) == 1, "1 saturator head");
        helper.assertTrue(need.get(Items.END_ROD) == 1, "1 end rod");
        helper.assertTrue(need.get(ModBlocks.DENSE_FROST_PIPE.get().asItem()) == 1, "1 dense pipe");
        helper.assertTrue(need.get(ModBlocks.ATLAS_CASING.get().asItem()) == 15, "15 casings");
        for (Direction facing : new Direction[]{Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST}) {
            var cells = com.golemlogix.system.SaturatorBlueprint.cells(facing);
            helper.assertTrue(cells.size() == 18, "18 cells for " + facing);
            BlockPos at = new BlockPos(6, 3, 6);
            buildSaturator(helper, at, facing);
            // чертёж привязан к «центральному ящику»: он на два блока ниже хозяина
            BlockPos port = at.below(2).relative(com.golemlogix.system.SaturatorStructure.right(facing), 0);
            for (AtlasBlueprint.Cell c : cells) {
                BlockState actual = helper.getBlockState(port.offset(c.dx(), c.dy(), c.dz()));
                helper.assertTrue(c.matches(actual), "blueprint cell " + c.dx() + "," + c.dy() + "," + c.dz() + " facing " + facing + " found " + actual.getBlock());
            }
            helper.assertTrue(!cells.get(0).matches(Blocks.AIR.defaultBlockState()), "air does not match");
            helper.assertTrue(cells.get(0).matches(ModBlocks.SATURATOR_PART.get().defaultBlockState()), "a formed part matches");
        }
        helper.succeed();
    }

    // ------------------------------------------------- апгрейды, редстоун, ожидание, прекрафт

    private static int count(Container c) {
        int n = 0;
        for (int i = 0; i < c.getContainerSize(); i++) {
            n += c.getItem(i).getCount();
        }
        return n;
    }

    /**
     * Шина не гоняет предметы по кругу: сундук, который читает перископ, для «своей» шины не хранилище. Пока другого хранилища
     * нет, импорт ничего не берёт, а экспорт ничего не перекладывает (иначе предметы съезжали бы в первую ячейку и шина щёлкала бы
     * без конца). Предметы из настоящего диска экспорт в этот сундук доставляет как обычно.
     */
    @GameTest(template = T, timeoutTicks = 300)
    public static void busSkipsItsOwnContainer(GameTestHelper helper) {
        buildArray(helper, 0, 0);
        powered(helper);
        // импорт (3,1,4) -> сундук (2,1,4); тот же сундук читает перископ (2,1,5), трубы от муфты (4,1,6)
        helper.setBlock(BUS_IN, ModBlocks.VACUUM_IMPORT_BUS.get().defaultBlockState().setValue(DirectionalBlock.FACING, Direction.WEST));
        BlockPos chest = new BlockPos(2, 1, 4);
        helper.setBlock(chest, Blocks.CHEST);
        put(helper, new BlockPos(3, 1, 6), ModBlocks.BASIC_SNOW_PIPE.get());
        put(helper, new BlockPos(3, 1, 5), ModBlocks.BASIC_SNOW_PIPE.get());
        helper.setBlock(new BlockPos(2, 1, 5), ModBlocks.PERISCOPE_STORAGE_BUS.get().defaultBlockState().setValue(DirectionalBlock.FACING, Direction.NORTH));
        Container c = (Container) helper.getBlockEntity(chest);
        c.setItem(5, new ItemStack(Items.COBBLESTONE, 20));
        ItemKey cobble = ItemKey.of(new ItemStack(Items.COBBLESTONE));
        helper.runAfterDelay(60, () -> {
            helper.assertTrue(net(helper, ORIGIN).itemCountOf(cobble) == 20, "the periscope shows the chest");
            helper.assertTrue(c.getItem(5).getCount() == 20 && c.getItem(0).isEmpty(), "the import bus leaves its own chest alone");
            // на месте импорта — экспорт с булыжником в фильтре
            helper.setBlock(BUS_IN, ModBlocks.INJECTOR_EXPORT_BUS.get().defaultBlockState().setValue(DirectionalBlock.FACING, Direction.WEST));
            ((BusBlockEntity) helper.getBlockEntity(BUS_IN)).getFilters().setItem(0, new ItemStack(Items.COBBLESTONE));
        });
        helper.runAfterDelay(120, () -> {
            helper.assertTrue(c.getItem(5).getCount() == 20 && c.getItem(0).isEmpty(), "the export bus does not refill a chest from itself");
            putBay(helper, BAY);
            ((CopperDriveBayBlockEntity) helper.getBlockEntity(BAY)).getContainer().setItem(0, new ItemStack(ModItems.COPPER_DISK_1K.get()));
            helper.assertTrue(net(helper, ORIGIN).insertItem(new ItemStack(Items.COBBLESTONE, 5), false).isEmpty(), "5 more go to the disk");
        });
        helper.runAfterDelay(220, () -> {
            CopperDriveBayBlockEntity be = (CopperDriveBayBlockEntity) helper.getBlockEntity(BAY);
            helper.assertTrue(DiskStorage.storedItems(be.getDisks().get(0)) == 0, "the export bus delivered the disk's cobblestone");
            helper.assertTrue(count(c) == 25 && c.getItem(5).getCount() == 25, "all 25 in the chest's stack, got " + count(c));
            helper.succeed();
        });
    }

    /** Шесть ячеек апгрейдов: до четырёх ускорителей, Модуль Автокрафта только у экспорта, по одному модулю; ускорители сохраняются. */
    @GameTest(template = T, timeoutTicks = 100)
    public static void busUpgradeSlots(GameTestHelper helper) {
        BlockPos at = new BlockPos(2, 1, 2);
        helper.setBlock(at, ModBlocks.VACUUM_IMPORT_BUS.get().defaultBlockState());
        BusBlockEntity in = (BusBlockEntity) helper.getBlockEntity(at);
        var up = in.getUpgrades();
        helper.assertTrue(up.getContainerSize() == 6 && up.getMaxStackSize() == 1, "six single-item upgrade slots");
        int baseInterval = in.interval();
        int baseAmount = in.itemsPerOperation();
        ItemStack speed = new ItemStack(ModItems.COMPRESSED_COPROCESSOR.get());
        for (int i = 0; i < 4; i++) {
            helper.assertTrue(up.canPlaceItem(i, speed), "speed upgrade " + (i + 1) + " fits");
            up.setItem(i, speed.copy());
        }
        helper.assertTrue(!up.canPlaceItem(4, speed), "a fifth speed upgrade is rejected");
        helper.assertTrue(!up.canPlaceItem(4, new ItemStack(ModItems.AUTOCRAFT_MODULE.get())), "the import bus takes no autocraft module");
        helper.assertTrue(!up.canPlaceItem(4, new ItemStack(Items.DIAMOND)), "not an upgrade");
        helper.assertTrue(up.canPlaceItem(4, new ItemStack(ModItems.REDSTONE_MODULE.get())), "a redstone module fits");
        up.setItem(4, new ItemStack(ModItems.REDSTONE_MODULE.get()));
        helper.assertTrue(!up.canPlaceItem(5, new ItemStack(ModItems.REDSTONE_MODULE.get())), "only one redstone module");
        helper.assertTrue(in.itemsPerOperation() == baseAmount * 8, "four upgrades move 8x as much, got " + in.itemsPerOperation());
        helper.assertTrue(in.interval() < baseInterval && in.interval() >= 1, "four upgrades work more often: " + in.interval() + " < " + baseInterval);

        BlockPos at2 = new BlockPos(4, 1, 2);
        helper.setBlock(at2, ModBlocks.INJECTOR_EXPORT_BUS.get().defaultBlockState());
        var up2 = ((BusBlockEntity) helper.getBlockEntity(at2)).getUpgrades();
        helper.assertTrue(up2.canPlaceItem(0, new ItemStack(ModItems.AUTOCRAFT_MODULE.get())), "the export bus takes the autocraft module");
        up2.setItem(0, new ItemStack(ModItems.AUTOCRAFT_MODULE.get()));
        helper.assertTrue(!up2.canPlaceItem(1, new ItemStack(ModItems.AUTOCRAFT_MODULE.get())), "only one autocraft module");

        BlockPos at3 = new BlockPos(6, 1, 2);
        helper.setBlock(at3, ModBlocks.PERISCOPE_STORAGE_BUS.get().defaultBlockState());
        var up3 = ((com.golemlogix.blockentity.PeriscopeBlockEntity) helper.getBlockEntity(at3)).getUpgrades();
        helper.assertTrue(up3.getContainerSize() == 1 && !up3.canPlaceItem(0, speed)
                && up3.canPlaceItem(0, new ItemStack(ModItems.REDSTONE_MODULE.get())), "the periscope takes only the redstone module");

        var registries = helper.getLevel().registryAccess();
        CompoundTag saved = in.saveWithFullMetadata(registries);
        BusBlockEntity fresh = new BusBlockEntity(helper.absolutePos(at), helper.getBlockState(at));
        fresh.loadWithComponents(saved, registries);
        helper.assertTrue(fresh.getUpgrades().count(com.golemlogix.upgrade.UpgradeType.SPEED) == 4
                && fresh.getUpgrades().has(com.golemlogix.upgrade.UpgradeType.REDSTONE), "upgrades are saved with the bus");
        helper.succeed();
    }

    /** Крио-Калькулятор: до четырёх Сжатых Сопроцессоров в ячейках, каждый +4 шага за такт; вместе с приставленными складывается. */
    @GameTest(template = T, timeoutTicks = 100)
    public static void calculatorCompressedCoprocessors(GameTestHelper helper) {
        BlockPos at = new BlockPos(4, 1, 4);
        helper.setBlock(at, ModBlocks.CRYO_CALCULATOR.get().defaultBlockState());
        helper.setBlock(at.east(), ModBlocks.COOLING_COPROCESSOR.get().defaultBlockState());
        CryoCalculatorBlockEntity cpu = (CryoCalculatorBlockEntity) helper.getBlockEntity(at);
        var up = cpu.getUpgrades();
        ItemStack speed = new ItemStack(ModItems.COMPRESSED_COPROCESSOR.get());
        helper.assertTrue(up.getContainerSize() == 5, "five upgrade slots");
        for (int i = 0; i < 4; i++) {
            helper.assertTrue(up.canPlaceItem(i, speed), "compressed co-processor " + (i + 1) + " fits");
            up.setItem(i, speed.copy());
        }
        helper.assertTrue(!up.canPlaceItem(4, speed), "a fifth one does not");
        helper.assertTrue(!up.canPlaceItem(4, new ItemStack(ModItems.AUTOCRAFT_MODULE.get())), "no autocraft module in a calculator");
        helper.assertTrue(up.canPlaceItem(4, new ItemStack(ModItems.REDSTONE_MODULE.get())), "the redstone module fits");
        helper.assertTrue(cpu.getCompressed() == 4, "four counted");
        helper.runAfterDelay(25, () -> {
            int expected = CryoCalculatorBlockEntity.BASE_WORKERS + CryoCalculatorBlockEntity.WORKERS_PER_COPROCESSOR
                    + 4 * CryoCalculatorBlockEntity.WORKERS_PER_COMPRESSED;
            helper.assertTrue(cpu.getCoprocessors() == 1, "the adjacent co-processor is counted");
            helper.assertTrue(cpu.getWorkers() == expected, "steps per cycle " + cpu.getWorkers() + " == " + expected);
            helper.succeed();
        });
    }

    /** Режимы Модуля Редстоуна — как у AE2/RS: с сигналом, без сигнала, одно действие на импульс; без модуля сигнал не важен. */
    @GameTest(template = T, timeoutTicks = 100)
    public static void redstoneModes(GameTestHelper helper) {
        var rc = new com.golemlogix.upgrade.RedstoneControl();
        helper.assertTrue(rc.allows(false, false, false) && rc.allows(false, true, true), "without the module the signal does not matter");
        rc.setMode(com.golemlogix.upgrade.RedstoneMode.HIGH);
        helper.assertTrue(!rc.allows(true, false, false) && rc.allows(true, true, false), "HIGH: only with a signal");
        rc.setMode(com.golemlogix.upgrade.RedstoneMode.LOW);
        helper.assertTrue(rc.allows(true, false, false) && !rc.allows(true, true, false), "LOW: only without a signal");
        rc.setMode(com.golemlogix.upgrade.RedstoneMode.IGNORE);
        helper.assertTrue(rc.allows(true, true, false) && rc.allows(true, false, false), "IGNORE: always");
        rc.setMode(com.golemlogix.upgrade.RedstoneMode.PULSE);
        rc.observe(false);
        helper.assertTrue(!rc.allows(true, false, false), "PULSE: nothing before the first edge");
        rc.observe(true);
        helper.assertTrue(rc.allows(true, true, false), "a rising edge arms one action");
        helper.assertTrue(rc.allows(true, true, true), "the action spends it");
        helper.assertTrue(!rc.allows(true, true, false), "a steady signal gives no second action");
        rc.observe(false);
        rc.observe(true);
        helper.assertTrue(rc.allows(true, true, true), "the next edge gives the next action");
        helper.assertTrue(com.golemlogix.upgrade.RedstoneMode.LOW.next(false) == com.golemlogix.upgrade.RedstoneMode.IGNORE,
                "storage modes skip PULSE");

        // шина импорта с модулем в режиме «с сигналом» стоит без сигнала и работает с ним
        BlockPos bay = baseNetwork(helper);
        helper.setBlock(BUS_IN, ModBlocks.VACUUM_IMPORT_BUS.get().defaultBlockState().setValue(DirectionalBlock.FACING, Direction.WEST));
        BusBlockEntity bus = (BusBlockEntity) helper.getBlockEntity(BUS_IN);
        bus.getUpgrades().setItem(0, new ItemStack(ModItems.REDSTONE_MODULE.get()));
        BlockPos chest = new BlockPos(2, 1, 4);
        helper.setBlock(chest, Blocks.CHEST);
        Container c = (Container) helper.getBlockEntity(chest);
        c.setItem(0, new ItemStack(Items.COBBLESTONE, 8));
        helper.runAfterDelay(40, () -> {
            helper.assertTrue(count(c) == 8, "no signal: the bus waits");
            helper.setBlock(BUS_IN.north(), Blocks.REDSTONE_BLOCK);
        });
        helper.runAfterDelay(90, () -> {
            helper.assertTrue(c.isEmpty(), "with a signal the bus works");
            helper.assertTrue(net(helper, bay).itemCountOf(ItemKey.of(new ItemStack(Items.COBBLESTONE))) == 8, "into the network");
            helper.succeed();
        });
    }

    /** Режим ожидания Поставщика: следующая порция не грузится в машину, пока результат прошлой не вернулся в задачу. */
    @GameTest(template = T, timeoutTicks = 500)
    public static void providerWaitsForResult(GameTestHelper helper) {
        BlockPos bay = baseNetwork(helper);
        autocraftRig(helper);
        PatternProviderBlockEntity provider = (PatternProviderBlockEntity) helper.getBlockEntity(PROV);
        provider.getContainer().setItem(0, card(new PatternData(false,
                List.of(new ItemStack(Items.RAW_IRON)), List.of(new ItemStack(Items.IRON_INGOT)))));
        provider.setBlocking(true);
        Container machineIn = (Container) helper.getBlockEntity(PROV.east());
        Container machineOut = (Container) helper.getBlockEntity(MACHINE_OUT);
        ItemKey ingot = ItemKey.of(new ItemStack(Items.IRON_INGOT));
        helper.runAfterDelay(5, () -> {
            GolemNetwork n = net(helper, bay);
            n.insertItem(new ItemStack(Items.RAW_IRON, 2), false);
            helper.assertTrue(n.startCrafting(ingot, 2).type() == GolemNetwork.StartType.STARTED, "started");
        });
        helper.runAfterDelay(80, () -> {
            helper.assertTrue(count(machineIn) == 1, "while waiting only the first batch is loaded, got " + count(machineIn));
            helper.assertTrue(net(helper, bay).providerBusy(helper.absolutePos(PROV)), "the provider waits for the result");
            machineIn.clearContent();
            machineOut.setItem(0, new ItemStack(Items.IRON_INGOT));    // «машина» отработала первую порцию
        });
        helper.runAfterDelay(170, () -> {
            helper.assertTrue(machineOut.isEmpty(), "the bus returned the first ingot");
            helper.assertTrue(count(machineIn) == 1, "the result came back, so the second batch is loaded, got " + count(machineIn));
            machineIn.clearContent();
            machineOut.setItem(0, new ItemStack(Items.IRON_INGOT));
        });
        helper.runAfterDelay(280, () -> {
            GolemNetwork n = net(helper, bay);
            helper.assertTrue(n.itemCountOf(ingot) == 2, "both ingots delivered, got " + n.itemCountOf(ingot));
            helper.assertTrue(((CryoCalculatorBlockEntity) helper.getBlockEntity(CPU)).isIdle(), "calculator idle");
            helper.succeed();
        });
    }

    /** Прекрафт (Ctrl+«+» в JEI): одна задача сразу на несколько предметов. */
    @GameTest(template = T, timeoutTicks = 400)
    public static void precraftSeveralTargets(GameTestHelper helper) {
        BlockPos bay = baseNetwork(helper);
        helper.setBlock(CPU, ModBlocks.CRYO_CALCULATOR.get().defaultBlockState());
        helper.setBlock(PROV, ModBlocks.PATTERN_PROVIDER.get().defaultBlockState());
        PatternProviderBlockEntity provider = (PatternProviderBlockEntity) helper.getBlockEntity(PROV);
        ItemStack plank = new ItemStack(Items.OAK_PLANKS);
        provider.getContainer().setItem(0, card(craftingPattern(new ItemStack(Items.OAK_PLANKS, 4), new ItemStack(Items.OAK_LOG))));
        provider.getContainer().setItem(1, card(craftingPattern(new ItemStack(Items.STICK, 4),
                plank.copy(), ItemStack.EMPTY, ItemStack.EMPTY, plank.copy())));
        ItemKey sticks = ItemKey.of(new ItemStack(Items.STICK));
        ItemKey planks = ItemKey.of(plank);
        helper.runAfterDelay(5, () -> {
            GolemNetwork n = net(helper, bay);
            n.insertItem(new ItemStack(Items.OAK_LOG, 2), false);
            java.util.Map<ItemKey, Long> targets = new java.util.LinkedHashMap<>();
            targets.put(sticks, 4L);
            targets.put(planks, 4L);
            CraftingPlan plan = CraftingPlanner.plan(n, targets);
            helper.assertTrue(plan.missing().isEmpty(), "two logs are enough for 4 sticks and 4 planks, missing " + plan.missing());
            helper.assertTrue(plan.totalExecutions() == 3, "2 planks crafts + 1 sticks craft, got " + plan.totalExecutions());
            targets.put(sticks, 40L);
            helper.assertTrue(!CraftingPlanner.plan(n, targets).missing().isEmpty(), "40 sticks need more logs");
            targets.put(sticks, 4L);
            GolemNetwork.StartResult r = n.startCrafting(targets, null, null);
            helper.assertTrue(r.type() == GolemNetwork.StartType.STARTED, "one job for both: " + r.type());
            helper.assertTrue(n.freeCpu() == null, "the only calculator took it");
        });
        helper.runAfterDelay(220, () -> {
            GolemNetwork n = net(helper, bay);
            helper.assertTrue(n.itemCountOf(sticks) == 4, "4 sticks, got " + n.itemCountOf(sticks));
            helper.assertTrue(n.itemCountOf(planks) >= 4, "at least 4 planks, got " + n.itemCountOf(planks));
            helper.assertTrue(((CryoCalculatorBlockEntity) helper.getBlockEntity(CPU)).isIdle(), "calculator idle");
            helper.succeed();
        });
    }

    // ------------------------------------------------- насыщатель: охлаждение и редстоун

    private static BlockHitResult hitTop(BlockPos abs) {
        return new BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(abs).add(0, 0.5, 0), Direction.UP, abs, false);
    }

    /** Сопроцессоры на решётках крыши: только сверху, по одному на решётку, ускоряют пресс, снимаются с Shift и не теряются при разборе. */
    @GameTest(template = T, timeoutTicks = 200)
    public static void saturatorCoolers(GameTestHelper helper) {
        BlockPos ctl = new BlockPos(5, 3, 5);
        buildSaturator(helper, ctl, Direction.NORTH);
        int[] vents = com.golemlogix.system.SaturatorStructure.VENTS;
        helper.runAfterDelay(30, () -> {
            var level = helper.getLevel();
            helper.assertTrue(helper.getBlockState(ctl).getValue(com.golemlogix.blocks.ManaSaturatorBlock.FORMED), "forms");
            var player = testPlayer(helper);
            ItemStack coolers = new ItemStack(ModBlocks.COOLING_COPROCESSOR.get(), 6);
            BlockPos first = satCell(helper, ctl, Direction.NORTH, vents[0]);
            level.getBlockState(first).useItemOn(coolers, level, player, net.minecraft.world.InteractionHand.MAIN_HAND,
                    new BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(first), Direction.NORTH, first, false));
            helper.assertTrue(!level.getBlockState(first).getValue(com.golemlogix.blocks.SaturatorPartBlock.COOLER) && coolers.getCount() == 6,
                    "a cooler is not mounted from the side");
            for (int v : vents) {
                BlockPos p = satCell(helper, ctl, Direction.NORTH, v);
                var r = level.getBlockState(p).useItemOn(coolers, level, player, net.minecraft.world.InteractionHand.MAIN_HAND, hitTop(p));
                helper.assertTrue(r.consumesAction() && level.getBlockState(p).getValue(com.golemlogix.blocks.SaturatorPartBlock.COOLER),
                        "a cooler sits on vent " + v);
            }
            helper.assertTrue(coolers.getCount() == 1, "five coolers used, " + coolers.getCount() + " left");
            level.getBlockState(first).useItemOn(coolers, level, player, net.minecraft.world.InteractionHand.MAIN_HAND, hitTop(first));
            helper.assertTrue(coolers.getCount() == 1, "a busy vent takes no second cooler");
        });
        helper.runAfterDelay(70, () -> {
            var level = helper.getLevel();
            var sat = (com.golemlogix.blockentity.ManaSaturatorBlockEntity) helper.getBlockEntity(ctl);
            helper.assertTrue(sat.getCoolers() == 5, "the machine counted its coolers, got " + sat.getCoolers());
            helper.assertTrue(sat.speed() == com.golemlogix.blockentity.ManaSaturatorBlockEntity.BASE_SPEED
                    + 5 * com.golemlogix.blockentity.ManaSaturatorBlockEntity.SPEED_PER_COOLER, "five coolers speed it up");
            var player = testPlayer(helper);
            player.setShiftKeyDown(true);
            BlockPos p = satCell(helper, ctl, Direction.NORTH, vents[2]);
            level.getBlockState(p).useWithoutItem(level, player, hitTop(p));
            helper.assertTrue(!level.getBlockState(p).getValue(com.golemlogix.blocks.SaturatorPartBlock.COOLER), "Shift+click takes a cooler off");
            helper.assertTrue(player.getInventory().countItem(ModBlocks.COOLING_COPROCESSOR.get().asItem()) == 1, "and gives it back");
            // разбор машины: четыре оставшихся сопроцессора выпадают предметами
            level.destroyBlock(satCell(helper, ctl, Direction.NORTH, com.golemlogix.system.SaturatorStructure.ROD), false);
            var box = new net.minecraft.world.phys.AABB(helper.absolutePos(ctl)).inflate(3);
            int dropped = level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, box,
                    e -> e.getItem().is(ModBlocks.COOLING_COPROCESSOR.get().asItem())).stream().mapToInt(e -> e.getItem().getCount()).sum();
            helper.assertTrue(dropped == 4, "the other four coolers drop, got " + dropped);
            helper.succeed();
        });
    }

    /** Модуль Редстоуна в Насыщателе: ставится ПКМ, повторный ПКМ меняет режим; в режиме «с сигналом» без сигнала голем ждёт. */
    @GameTest(template = T, timeoutTicks = 400)
    public static void saturatorRedstoneModule(GameTestHelper helper) {
        BlockPos ctl = new BlockPos(5, 3, 5);
        buildSaturator(helper, ctl, Direction.NORTH);
        put(helper, new BlockPos(7, 1, 5), ModBlocks.CREATIVE_SOURCE_JAR.get());
        helper.runAfterDelay(30, () -> {
            var level = helper.getLevel();
            var sat = (com.golemlogix.blockentity.ManaSaturatorBlockEntity) helper.getBlockEntity(ctl);
            var player = testPlayer(helper);
            BlockPos abs = helper.absolutePos(ctl);
            var hit = new BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(abs), Direction.NORTH, abs, false);
            ItemStack module = new ItemStack(ModItems.REDSTONE_MODULE.get(), 2);
            level.getBlockState(abs).useItemOn(module, level, player, net.minecraft.world.InteractionHand.MAIN_HAND, hit);
            helper.assertTrue(sat.hasRedstoneModule() && module.getCount() == 1, "the module goes into the machine");
            helper.assertTrue(sat.redstoneMode() == com.golemlogix.upgrade.RedstoneMode.HIGH, "default mode: with a signal");
            for (int i = 0; i < 4; i++) {
                level.getBlockState(abs).useItemOn(module, level, player, net.minecraft.world.InteractionHand.MAIN_HAND, hit);
            }
            helper.assertTrue(sat.redstoneMode() == com.golemlogix.upgrade.RedstoneMode.HIGH && module.getCount() == 1,
                    "four clicks go round the modes and spend nothing");
            ItemStack iron = new ItemStack(Items.IRON_BLOCK);
            level.getBlockState(abs).useItemOn(iron, level, player, net.minecraft.world.InteractionHand.MAIN_HAND, hit);
            helper.assertTrue(sat.inputDeck().is(Items.IRON_BLOCK), "the blank waits on the left deck");
        });
        helper.runAfterDelay(170, () -> {
            var sat = (com.golemlogix.blockentity.ManaSaturatorBlockEntity) helper.getBlockEntity(ctl);
            helper.assertTrue(sat.getHeldItem().is(Items.IRON_BLOCK) && !sat.isCrafting(), "without a signal the smith waits");
            helper.setBlock(new BlockPos(3, 1, 5), Blocks.REDSTONE_BLOCK);
        });
        helper.runAfterDelay(340, () -> {
            var sat = (com.golemlogix.blockentity.ManaSaturatorBlockEntity) helper.getBlockEntity(ctl);
            helper.assertTrue(sat.outputDeck().is(ModItems.FLATTENED_IRON_CORE.get()), "with a signal it forges, got " + sat.outputDeck());
            var player = testPlayer(helper);
            player.setShiftKeyDown(true);
            BlockPos abs = helper.absolutePos(ctl);
            helper.getLevel().getBlockState(abs).useWithoutItem(helper.getLevel(), player,
                    new BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(abs), Direction.NORTH, abs, false));
            helper.assertTrue(!sat.hasRedstoneModule(), "Shift+click with an empty hand takes the module out");
            helper.assertTrue(player.getInventory().countItem(ModItems.REDSTONE_MODULE.get()) == 1, "and gives it back");
            helper.succeed();
        });
    }

    /** Резонатор не питает сеть и не наполняет банки; но хронометр сети с резонатором всегда полон. */
    @GameTest(template = T, timeoutTicks = 200)
    public static void resonatorOnlyFeedsChronometers(GameTestHelper helper) {
        buildArray(helper, 0, 0);
        BlockPos res = new BlockPos(3, 1, 6);     // вплотную к угловой муфте (4,1,6)
        put(helper, res, ModBlocks.ETERNAL_FROST_RESONATOR.get());
        ResourceLocation jarId = ResourceLocation.fromNamespaceAndPath("ars_nouveau", "source_jar");
        boolean ars = com.golemlogix.system.SourceHelper.arsNouveauLoaded() && net.minecraft.core.registries.BuiltInRegistries.BLOCK.containsKey(jarId);
        BlockPos arsJar = new BlockPos(2, 1, 6);  // банка Ars Nouveau рядом с резонатором
        if (ars) {
            helper.setBlock(arsJar, net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(jarId));
        }
        helper.runAfterDelay(60, () -> {
            helper.assertTrue(com.golemlogix.system.SourceHelper.asSupplier(helper.getBlockEntity(res)) == null, "the resonator is not a Source supplier");
            GolemNetwork n = net(helper, ORIGIN);
            helper.assertTrue(n.hasResonator(), "the network sees its resonator");
            if (com.golemlogix.system.SourceHelper.sourceRequired()) {
                helper.assertTrue(!n.isOnline(), "a resonator alone does not power the network");
            }
            if (ars) {
                var supplier = com.golemlogix.system.SourceHelper.asSupplier(helper.getBlockEntity(arsJar));
                helper.assertTrue(supplier != null && supplier.available() == 0, "the resonator does not fill Ars Nouveau jars");
            }
            powered(helper);
        });
        helper.runAfterDelay(100, () -> {
            var level = helper.getLevel();
            helper.assertTrue(net(helper, ORIGIN).isOnline(), "the jar powers the network");
            var player = testPlayer(helper);
            ItemStack chrono = new ItemStack(ModItems.PORTABLE_CHRONOMETER.get());
            chrono.set(ModDataComponents.BOUND_CORE.get(), GlobalPos.of(level.dimension(), helper.absolutePos(CORE)));
            player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, chrono);
            chrono.use(level, player, net.minecraft.world.InteractionHand.MAIN_HAND);
            if (com.golemlogix.system.SourceHelper.sourceRequired()) {
                helper.assertTrue(com.golemlogix.items.PortableChronometerItem.getCharge(chrono) == com.golemlogix.items.PortableChronometerItem.MAX_CHARGE,
                        "the resonator fills the chronometer, got " + com.golemlogix.items.PortableChronometerItem.getCharge(chrono));
                helper.assertTrue(Boolean.TRUE.equals(chrono.get(ModDataComponents.RESONATOR_LINK.get())), "the chronometer knows about the resonator");
            }
            player.closeContainer();
            // без резонатора хронометр заряжается от банки Source
            ItemStack plain = new ItemStack(ModItems.PORTABLE_CHRONOMETER.get());
            player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, plain);
            BlockPos jar = helper.absolutePos(JAR);
            plain.useOn(new net.minecraft.world.item.context.UseOnContext(player, net.minecraft.world.InteractionHand.MAIN_HAND, hitTop(jar)));
            if (com.golemlogix.system.SourceHelper.sourceRequired()) {
                helper.assertTrue(com.golemlogix.items.PortableChronometerItem.getCharge(plain) == com.golemlogix.items.PortableChronometerItem.MAX_CHARGE,
                        "a creative jar charges the chronometer to the top");
            }
            helper.succeed();
        });
    }
}