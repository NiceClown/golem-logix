package com.golemlogix.gametest;

import com.golemlogix.GolemLogixMod;
import com.golemlogix.blockentity.CopperDriveBayBlockEntity;
import com.golemlogix.blockentity.CryoCalculatorBlockEntity;
import com.golemlogix.blockentity.CryoJarBlockEntity;
import com.golemlogix.blockentity.DistillationTowerBlockEntity;
import com.golemlogix.blockentity.PatternProviderBlockEntity;
import com.golemlogix.blockentity.SecurityTerminalBlockEntity;
import com.golemlogix.blockentity.StockManometerBlockEntity;
import com.golemlogix.crafting.CraftingJob;
import com.golemlogix.data.PatternData;
import com.golemlogix.init.ModBlocks;
import com.golemlogix.init.ModItems;
import com.golemlogix.items.IdentityPunchCardItem;
import com.golemlogix.menu.TerminalMenu;
import com.golemlogix.network.CraftRequestPayload;
import com.golemlogix.network.TerminalActionPayload;
import com.golemlogix.security.SecurityPermission;
import com.golemlogix.system.AtlasStructure;
import com.golemlogix.system.GolemNetwork;
import com.golemlogix.system.ItemKey;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/**
 * Backport of main's 2.1.4 game tests: jars through the network and the tower, equipment on disks, order back-off, terminal deltas,
 * the crafting step cache, security, the Stock Manometer and the Atlas Array's layout checks.
 */
@GameTestHolder(GolemLogixMod.MOD_ID)
@PrefixGameTestTemplate(false)
public class GolemGameTestsBackport9 {
    private static final String T = "empty";

    /** Same points as in GolemGameTests (kept here so this class does not depend on their visibility). */
    private static final BlockPos ORIGIN = new BlockPos(5, 1, 5);
    private static final BlockPos CORE = ORIGIN.offset(0, 3, 0);
    private static final BlockPos BAY = new BlockPos(7, 1, 4);
    private static final BlockPos CPU = new BlockPos(4, 1, 3);
    private static final BlockPos PROV = new BlockPos(7, 1, 6);
    private static final BlockPos TERM = new BlockPos(3, 1, 6);
    private static final BlockPos BUS_OUT = new BlockPos(6, 1, 3);

    // ------------------------------------------------------------ helpers

    private static <X extends BlockEntity> X be(GameTestHelper helper, BlockPos rel, Class<X> type) {
        BlockEntity found = helper.getBlockEntity(rel);
        return type.cast(found);
    }

    /**
     * A real reload: the block entity is replaced by a new object that knows only what was saved, and the network is built again.
     * Loading a tag into the same object proves nothing, it still remembers everything.
     */
    private static <X extends BlockEntity> X reload(GameTestHelper helper, BlockPos rel, Class<X> type) {
        BlockPos abs = helper.absolutePos(rel);
        var level = helper.getLevel();
        var old = level.getBlockEntity(abs);
        CompoundTag tag = old.saveWithFullMetadata(level.registryAccess());
        var fresh = BlockEntity.loadStatic(abs, old.getBlockState(), tag, level.registryAccess());
        level.setBlockEntity(fresh);
        GolemNetwork.refresh(level, abs);
        return type.cast(fresh);
    }

    /** The stick and plank recipes on a provider, and a large storage. */
    private static BlockPos hugeRig(GameTestHelper helper) {
        BlockPos bay = GolemGameTests.baseNetwork(helper);
        CopperDriveBayBlockEntity drive = be(helper, bay, CopperDriveBayBlockEntity.class);
        for (int i = 0; i < 4; i++) {
            drive.getContainer().setItem(i, new ItemStack(ModItems.COPPER_DISK_256K.get()));
        }
        helper.setBlock(CPU, ModBlocks.CRYO_CALCULATOR.get().defaultBlockState());
        helper.setBlock(PROV, ModBlocks.PATTERN_PROVIDER.get().defaultBlockState());
        PatternProviderBlockEntity provider = be(helper, PROV, PatternProviderBlockEntity.class);
        provider.getContainer().setItem(0, GolemGameTests.card(GolemGameTests.craftingPattern(new ItemStack(Items.OAK_PLANKS, 4), new ItemStack(Items.OAK_LOG))));
        java.util.List<ItemStack> grid = new java.util.ArrayList<>();
        for (int i = 0; i < 9; i++) {
            grid.add(i == 0 || i == 3 ? new ItemStack(Items.OAK_PLANKS) : ItemStack.EMPTY);
        }
        provider.getContainer().setItem(1, GolemGameTests.card(new PatternData(true, grid, List.of(new ItemStack(Items.STICK, 4)))));
        return bay;
    }

    private static GolemNetwork net(GameTestHelper helper, BlockPos rel) {
        return GolemGameTests.net(helper, rel);
    }

    // ------------------------------------------------------------ jars and the tower

    /** A Giga Jar beside the tower and on its network is one jar: the tower takes blood only for the room it really has. */
    @GameTest(template = T, timeoutTicks = 200)
    public static void towerCountsAJarOnce(GameTestHelper helper) {
        GolemGameTests.buildArray(helper, 0, 0);
        var level = helper.getLevel();
        for (int x = 3; x >= -2; x--) {
            GolemGameTests.put(helper, new BlockPos(x, 1, 6), ModBlocks.BASIC_SNOW_PIPE.get());
        }
        // the square touches the pipe line on its north side and the tower on its west side; the pipe runs on to the tower
        BlockPos jarAt = new BlockPos(-1, 1, 7);
        for (BlockPos p : List.of(jarAt, jarAt.east(), jarAt.south(), jarAt.east().south())) {
            com.golemlogix.blocks.CryoJarBlock.place(level, helper.absolutePos(p), Direction.SOUTH, ModBlocks.CRYO_SOURCE_JAR.get());
        }
        BlockPos base = new BlockPos(-2, 1, 7);
        com.golemlogix.blocks.DistillationTowerBlock.place(level, helper.absolutePos(base), Direction.SOUTH, ModBlocks.CRYO_DISTILLATION_TOWER.get());
        var blood = net.minecraft.core.registries.BuiltInRegistries.FLUID.getTag(com.golemlogix.init.ModTags.CRYO_DISTILLABLE);
        if (blood.isEmpty() || blood.get().size() == 0) {
            helper.succeed();
            return;
        }
        var fluid = blood.get().get(0).value();
        int[] room = new int[1];
        helper.runAfterDelay(10, () -> {
            var whole = be(helper, jarAt, CryoJarBlockEntity.class);
            helper.assertTrue(whole.form() == com.golemlogix.blocks.JarForm.GIGA, "the jars joined");
            room[0] = 200;
            whole.fill(whole.capacity() - room[0], false);
            // fast enough that one batch could fill the room twice over
            be(helper, base, DistillationTowerBlockEntity.class).getUpgrades()
                    .setItem(0, new ItemStack(ModItems.COMPRESSED_COPROCESSOR.get(), 28));
            be(helper, base, DistillationTowerBlockEntity.class).input()
                    .fill(new FluidStack(fluid, 1000), net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE);
        });
        helper.runAfterDelay(80, () -> {
            var tower = be(helper, base, DistillationTowerBlockEntity.class);
            var whole = be(helper, jarAt, CryoJarBlockEntity.class);
            double perMb = com.golemlogix.config.GolemConfig.DISTILL_SOURCE_PER_MB.get() * (1 + com.golemlogix.config.GolemConfig.GIGA_SOURCE_BONUS.get() / 100.0);
            // the core drinks a little meanwhile, which makes some more room: allow 15 Source for it, far less than the room
            int used = 1000 - tower.blood().getAmount();
            helper.assertTrue(used <= Math.ceil((room[0] + 15) / perMb) + 1, "blood was taken only for the room there was: " + used + " mB for " + room[0] + " Source of room");
            helper.assertTrue(whole.available() >= whole.capacity() - 15, "and the jar filled up: " + whole.available() + " of " + whole.capacity());
            helper.succeed();
        });
    }

    /** A tower touching the network pours into a Giga Jar anywhere on it, with the derrick's bonus; a lone jar there gets nothing. */
    @GameTest(template = T, timeoutTicks = 200)
    public static void towerFillsJarsThroughNetwork(GameTestHelper helper) {
        GolemGameTests.buildArray(helper, 0, 0);
        var level = helper.getLevel();
        // a line of pipes from the corner bushing (4,1,6) to the west, a tower at its far end, jars along it
        for (int x = 3; x >= -4; x--) {
            GolemGameTests.put(helper, new BlockPos(x, 1, 6), ModBlocks.BASIC_SNOW_PIPE.get());
        }
        BlockPos jarAt = new BlockPos(1, 1, 7), lone = new BlockPos(3, 1, 5);
        for (BlockPos p : List.of(jarAt, jarAt.east(), jarAt.south(), jarAt.east().south())) {
            com.golemlogix.blocks.CryoJarBlock.place(level, helper.absolutePos(p), Direction.SOUTH, ModBlocks.CRYO_SOURCE_JAR.get());
        }
        com.golemlogix.blocks.CryoJarBlock.place(level, helper.absolutePos(lone), Direction.SOUTH, ModBlocks.CRYO_SOURCE_JAR.get());
        BlockPos base = new BlockPos(-5, 1, 6);
        com.golemlogix.blocks.DistillationTowerBlock.place(level, helper.absolutePos(base), Direction.SOUTH, ModBlocks.CRYO_DISTILLATION_TOWER.get());
        var blood = net.minecraft.core.registries.BuiltInRegistries.FLUID.getTag(com.golemlogix.init.ModTags.CRYO_DISTILLABLE);
        if (blood.isEmpty() || blood.get().size() == 0) {
            helper.succeed();
            return;
        }
        var fluid = blood.get().get(0).value();
        helper.runAfterDelay(10, () -> {
            var tower = be(helper, base, DistillationTowerBlockEntity.class);
            tower.input().fill(new FluidStack(fluid, 100), net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE);
        });
        helper.runAfterDelay(120, () -> {
            var tower = be(helper, base, DistillationTowerBlockEntity.class);
            helper.assertTrue(tower.blood().isEmpty(), "the blood is distilled: " + tower.blood().getAmount());
            var whole = be(helper, jarAt, CryoJarBlockEntity.class);
            int expect = (int) Math.floor(100 * com.golemlogix.config.GolemConfig.DISTILL_SOURCE_PER_MB.get() * (1 + com.golemlogix.config.GolemConfig.GIGA_SOURCE_BONUS.get() / 100.0));
            // the core drinks some of it meanwhile, so the amount is checked loosely and the bonus on its own: the window's rate
            helper.assertTrue(whole.available() > expect - 60 && whole.available() <= expect, "the Giga Jar far away got it: " + whole.available() + " of " + expect);
            int shown = tower.menuData().get(2), plain = (int) Math.round(tower.rate() * com.golemlogix.config.GolemConfig.DISTILL_SOURCE_PER_MB.get() * 10);
            helper.assertTrue(shown > plain, "with the derrick's bonus: " + shown / 10.0 + " Source a second, without it " + plain / 10.0);
            helper.assertTrue(be(helper, lone, CryoJarBlockEntity.class).available() == 0, "the lone jar on the network did not");
            helper.succeed();
        });
    }

    /** A reactor on the network is one supplier, however many of its blocks touch it, and the network draws on it through any jar. */
    @GameTest(template = T, timeoutTicks = 100)
    public static void joinedJarFeedsNetwork(GameTestHelper helper) {
        GolemGameTests.buildArray(helper, 0, 0);
        var level = helper.getLevel();
        // against the corner bushing (4,1,6); the square goes away from the array
        BlockPos c = new BlockPos(2, 1, 5);
        for (BlockPos p : List.of(c, c.east(), c.south(), c.east().south())) {
            com.golemlogix.blocks.CryoJarBlock.place(level, helper.absolutePos(p), Direction.SOUTH, ModBlocks.CRYO_SOURCE_JAR.get());
        }
        helper.runAfterDelay(5, () -> {
            var whole = be(helper, c, CryoJarBlockEntity.class);
            helper.assertTrue(whole.form() == com.golemlogix.blocks.JarForm.GIGA, "the jars joined");
            whole.fill(30000, false);
        });
        helper.runAfterDelay(15, () -> {
            GolemNetwork n = net(helper, ORIGIN);
            helper.assertTrue(n.sources().size() == 1, "one supplier, got " + n.sources().size());
            helper.assertTrue(n.sources().get(0).available() > 29000, "with all of its Source: " + n.sources().get(0).available());
        });
        helper.runAfterDelay(80, () -> {
            helper.assertTrue(net(helper, ORIGIN).isOnline(), "the network runs on it");
            helper.succeed();
        });
    }

    // ------------------------------------------------------------ disks, orders, terminals

    /**
     * Equipment takes 4000 pieces of a disk; 1k and 4k disks always take one; things that do not count stack up as pieces. Old contents
     * over the limit stay, nothing more goes in.
     */
    @GameTest(template = T, timeoutTicks = 40)
    public static void gearOnDisks(GameTestHelper helper) {
        BlockPos bay = GolemGameTests.baseNetwork(helper);
        CopperDriveBayBlockEntity drive = be(helper, bay, CopperDriveBayBlockEntity.class);
        drive.getContainer().setItem(0, new ItemStack(ModItems.COPPER_DISK_16K.get()));
        ItemStack big = drive.getContainer().getItem(0);
        ItemStack worn = new ItemStack(Items.IRON_SWORD);
        worn.setDamageValue(5);
        helper.assertTrue(com.golemlogix.system.Gear.is(worn), "a worn sword is equipment");
        helper.assertTrue(!com.golemlogix.system.Gear.is(new ItemStack(Items.IRON_SWORD)), "a new one is not");
        helper.assertTrue(!com.golemlogix.system.Gear.is(new ItemStack(Items.WATER_BUCKET)) && !com.golemlogix.system.Gear.is(new ItemStack(Items.COBBLESTONE)), "a bucket and stone are not");
        ItemStack potion = new ItemStack(Items.POTION);
        potion.set(net.minecraft.core.component.DataComponents.POTION_CONTENTS, new net.minecraft.world.item.alchemy.PotionContents(net.minecraft.world.item.alchemy.Potions.WATER));
        helper.assertTrue(!com.golemlogix.system.Gear.is(potion), "a potion is not");
        ItemStack book = new ItemStack(Items.ENCHANTED_BOOK);
        book.enchant(helper.getLevel().registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT).getOrThrow(Enchantments.MENDING), 1);
        helper.assertTrue(!com.golemlogix.system.Gear.is(book), "an enchanted book is not");

        for (int i = 1; i <= 4; i++) {
            ItemStack s = new ItemStack(Items.IRON_SWORD);
            s.setDamageValue(i);
            helper.assertTrue(com.golemlogix.system.DiskStorage.insertItem(big, s, false) == 1, "16k takes equipment number " + i);
        }
        helper.assertTrue(com.golemlogix.system.DiskStorage.storedItems(big) == 16000, "four take the whole disk");
        helper.assertTrue(com.golemlogix.system.DiskStorage.insertItem(big, worn, true) == 0, "a fifth does not fit");
        helper.assertTrue(com.golemlogix.system.DiskStorage.insertItem(big, new ItemStack(Items.COBBLESTONE), true) == 0, "nor a stone");
        ItemStack first = new ItemStack(Items.IRON_SWORD);
        first.setDamageValue(1);
        helper.assertTrue(com.golemlogix.system.DiskStorage.extractItem(big, first, 1, false) == 1, "one comes out");
        helper.assertTrue(com.golemlogix.system.DiskStorage.storedItems(big) == 12000, "and frees its 4000");

        for (var disk : List.of(ModItems.COPPER_DISK_1K.get(), ModItems.COPPER_DISK_4K.get())) {
            ItemStack small = new ItemStack(disk);
            helper.assertTrue(com.golemlogix.system.DiskStorage.insertItem(small, new ItemStack(Items.COBBLESTONE, 500), false) == 500, "stone first");
            helper.assertTrue(com.golemlogix.system.DiskStorage.insertItem(small, worn, false) == 1, "a small disk takes one, even partly filled");
            ItemStack other = new ItemStack(Items.IRON_SWORD);
            other.setDamageValue(9);
            helper.assertTrue(com.golemlogix.system.DiskStorage.insertItem(small, other, true) == 0, "but only one");
            helper.assertTrue(com.golemlogix.system.DiskStorage.insertItem(small, new ItemStack(Items.COBBLESTONE), true) == 0, "and is full then");
            helper.assertTrue(com.golemlogix.system.DiskStorage.extractItem(small, worn, 1, false) == 1, "it comes out");
            helper.assertTrue(com.golemlogix.system.DiskStorage.storedItems(small) == 500, "the stone stays");
        }
        helper.succeed();
    }

    /** A device whose order lacks ingredients does not plan again at once; the pause grows, and ingredients end it soon. */
    @GameTest(template = T, timeoutTicks = 200)
    public static void failedOrdersWait(GameTestHelper helper) {
        BlockPos bay = hugeRig(helper);
        ItemKey plank = ItemKey.of(new ItemStack(Items.OAK_PLANKS));
        BlockPos device = helper.absolutePos(BUS_OUT);
        java.util.Map<ItemKey, Long> order = java.util.Map.of(plank, 4L);
        helper.runAfterDelay(5, () -> {
            GolemNetwork n = net(helper, bay);
            var first = n.startCrafting(order, null, device);
            helper.assertTrue(first.type() == GolemNetwork.StartType.MISSING && first.plan() != null, "the first try plans and fails");
            var second = n.startCrafting(order, null, device);
            helper.assertTrue(second.type() == GolemNetwork.StartType.MISSING && second.plan() == null, "the next one does not plan");
            helper.assertTrue(n.startCrafting(order, null, null).plan() != null, "a player's order is never held back");
        });
        helper.runAfterDelay(30, () -> {
            GolemNetwork n = net(helper, bay);
            helper.assertTrue(n.startCrafting(order, null, device).plan() != null, "after a second it tries again");
            helper.assertTrue(n.startCrafting(order, null, device).plan() == null, "and waits again");
        });
        helper.runAfterDelay(45, () -> {
            helper.assertTrue(net(helper, bay).startCrafting(order, null, device).plan() == null, "now two seconds, not one");
            net(helper, bay).insertItem(new ItemStack(Items.OAK_LOG, 1), false);
        });
        helper.runAfterDelay(90, () -> {
            helper.assertTrue(net(helper, bay).startCrafting(order, null, device).type() == GolemNetwork.StartType.STARTED, "ingredients end the wait");
            helper.succeed();
        });
    }

    /** Terminal windows get only what changed, and end up with the same list as the network. */
    @GameTest(template = T, timeoutTicks = 40)
    public static void terminalSendsChanges(GameTestHelper helper) {
        hugeRig(helper);
        ServerPlayer player = GolemGameTests.testPlayer(helper);
        helper.setBlock(TERM, ModBlocks.ANALOG_TERMINAL.get().defaultBlockState());
        BlockPos abs = helper.absolutePos(TERM);
        TerminalMenu client = new TerminalMenu(90, player.getInventory(), helper.getLevel(), abs, abs, ModBlocks.ANALOG_TERMINAL.get(), false, false, 0);
        var stone = new ItemStack(Items.STONE);
        var dirt = new ItemStack(Items.DIRT);
        // the client side's merging, entry by entry
        client.clientReceiveList(new com.golemlogix.network.TerminalListPayload(90, true, List.of(
                new com.golemlogix.network.TerminalListPayload.Entry(stone, 5, false), new com.golemlogix.network.TerminalListPayload.Entry(dirt, 7, false))));
        client.clientReceiveList(new com.golemlogix.network.TerminalListPayload(90, false, List.of(new com.golemlogix.network.TerminalListPayload.Entry(stone, 9, false))));
        helper.assertTrue(client.clientEntries.size() == 2 && client.clientEntries.get(0).count() == 9, "a changed count replaces its entry");
        client.clientReceiveList(new com.golemlogix.network.TerminalListPayload(90, false, List.of(new com.golemlogix.network.TerminalListPayload.Entry(stone, 0, false))));
        helper.assertTrue(client.clientEntries.size() == 1 && client.clientEntries.get(0).key().is(Items.DIRT), "an empty entry goes");
        client.clientReceiveList(new com.golemlogix.network.TerminalListPayload(90, false, List.of(new com.golemlogix.network.TerminalListPayload.Entry(stone, 0, true))));
        helper.assertTrue(client.clientEntries.size() == 2, "what can be crafted stays listed with nothing stored");
        client.clientReceiveList(new com.golemlogix.network.TerminalListPayload(90, true, List.of()));
        helper.assertTrue(client.clientEntries.isEmpty(), "a reset clears it");
        helper.succeed();
    }

    /** A crafting step remembers the recipe's result for its cells, and only for those: other cells give their own. */
    @GameTest(template = T, timeoutTicks = 40)
    public static void craftingStepCacheFollowsTheCells(GameTestHelper helper) {
        var level = helper.getLevel();
        var step = new CraftingJob.StepState(GolemGameTests.craftingPattern(new ItemStack(Items.OAK_PLANKS, 4), new ItemStack(Items.OAK_LOG)), 4);
        List<ItemStack> oak = List.of(new ItemStack(Items.OAK_LOG));
        List<ItemStack> birch = List.of(new ItemStack(Items.BIRCH_LOG));
        List<ItemStack> nothing = List.of(new ItemStack(Items.DIRT));
        helper.assertTrue(step.craft(level, oak).get(0).is(Items.OAK_PLANKS), "oak logs make oak planks");
        helper.assertTrue(step.craft(level, oak).get(0).getCount() == 4, "and again, four of them");
        helper.assertTrue(step.craft(level, birch).get(0).is(Items.BIRCH_PLANKS), "birch logs make birch planks, not what oak gave");
        helper.assertTrue(step.craft(level, nothing) == null, "cells with no recipe make nothing");
        helper.assertTrue(step.craft(level, oak).get(0).is(Items.OAK_PLANKS), "and oak logs still work after that");
        helper.succeed();
    }

    // ------------------------------------------------------------ security

    /**
     * A player has a terminal window open and rights through a card; the card is taken out of the Security Terminal;
     * the player's further actions through that same open window (take, insert, order a craft) are refused.
     */
    @GameTest(template = T, timeoutTicks = 150)
    public static void securityRevokedWhileWindowOpen(GameTestHelper helper) {
        BlockPos bay = GolemGameTests.baseNetwork(helper);
        BlockPos secPos = BUS_OUT;
        helper.setBlock(secPos, ModBlocks.SECURITY_TERMINAL.get().defaultBlockState().setValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING, Direction.NORTH));
        SecurityTerminalBlockEntity sec = be(helper, secPos, SecurityTerminalBlockEntity.class);

        ServerPlayer owner = GolemGameTests.testPlayer(helper);
        sec.setOwner(owner);

        ServerPlayer player = net.neoforged.neoforge.common.util.FakePlayerFactory.get(helper.getLevel(),
                new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "player-open-window"));
        sec.getCards().setItem(0, IdentityPunchCardItem.of(player.getUUID(), "player-open-window",
                SecurityPermission.EXTRACT.mask | SecurityPermission.INSERT.mask | SecurityPermission.CRAFT.mask));

        helper.setBlock(CPU, ModBlocks.CRYO_CALCULATOR.get().defaultBlockState());
        helper.setBlock(PROV, ModBlocks.PATTERN_PROVIDER.get().defaultBlockState());
        PatternProviderBlockEntity provider = be(helper, PROV, PatternProviderBlockEntity.class);
        provider.getContainer().setItem(0, GolemGameTests.card(GolemGameTests.craftingPattern(new ItemStack(Items.OAK_PLANKS, 4), new ItemStack(Items.OAK_LOG))));

        BlockPos termPos = TERM;
        helper.setBlock(termPos, ModBlocks.ANALOG_TERMINAL.get().defaultBlockState());
        BlockPos absTerm = helper.absolutePos(termPos);

        helper.runAfterDelay(10, () -> {
            GolemNetwork n = net(helper, bay);
            n.insertItem(new ItemStack(Items.DIAMOND, 50), false);
            n.insertItem(new ItemStack(Items.OAK_LOG, 10), false);
            ItemKey diamond = ItemKey.of(new ItemStack(Items.DIAMOND));

            // Player opens terminal window
            TerminalMenu menu = new TerminalMenu(88, player.getInventory(), helper.getLevel(), absTerm, absTerm,
                    ModBlocks.ANALOG_TERMINAL.get(), false, false, 0);
            player.containerMenu = menu;

            // Positive control: actions succeed while card is in the Security Terminal
            menu.setCarried(new ItemStack(Items.GOLD_INGOT, 5));
            menu.handleAction(player, new TerminalActionPayload(88, TerminalActionPayload.Action.INSERT_ALL, ItemStack.EMPTY, FluidStack.EMPTY));
            helper.assertTrue(menu.getCarried().isEmpty(), "positive control: player with card can insert");
            helper.assertTrue(n.itemCountOf(ItemKey.of(new ItemStack(Items.GOLD_INGOT))) == 5, "positive control: gold stored");

            // Rights revoked: remove card from Security Terminal while window stays open
            sec.getCards().setItem(0, ItemStack.EMPTY);
            sec.setChanged();
            helper.assertTrue(!n.hasPermission(player, SecurityPermission.EXTRACT), "permissions revoked on network");

            // 1. Take action refused
            menu.handleAction(player, new TerminalActionPayload(88, TerminalActionPayload.Action.TAKE_STACK, new ItemStack(Items.DIAMOND), FluidStack.EMPTY));
            helper.assertTrue(menu.getCarried().isEmpty(), "TAKE_STACK refused after rights revoked");
            helper.assertTrue(n.itemCountOf(diamond) == 50, "diamonds untouched in storage");

            // 2. Insert action refused
            menu.setCarried(new ItemStack(Items.IRON_INGOT, 8));
            menu.handleAction(player, new TerminalActionPayload(88, TerminalActionPayload.Action.INSERT_ALL, ItemStack.EMPTY, FluidStack.EMPTY));
            helper.assertTrue(menu.getCarried().getCount() == 8, "INSERT_ALL refused after rights revoked");
            helper.assertTrue(n.itemCountOf(ItemKey.of(new ItemStack(Items.IRON_INGOT))) == 0, "iron not inserted");

            // 3. Craft action refused
            menu.handleCraftRequest(player, new CraftRequestPayload(88, new ItemStack(Items.OAK_PLANKS), 4, false, 1));
            CryoCalculatorBlockEntity cpu = be(helper, CPU, CryoCalculatorBlockEntity.class);
            helper.assertTrue(cpu.isIdle(), "craft order refused after rights revoked");

            // The same order goes through once the card is back, so it was the rights that stopped it
            sec.getCards().setItem(0, IdentityPunchCardItem.of(player.getUUID(), "player-open-window", SecurityPermission.CRAFT.mask));
            menu.handleCraftRequest(player, new CraftRequestPayload(88, new ItemStack(Items.OAK_PLANKS), 4, false, 1));
            helper.assertTrue(!cpu.isIdle(), "positive control: the same craft order is taken with the card back");

            helper.succeed();
        });
    }

    /**
     * The Security Terminal's owner and cards, with their rights, survive a save and load:
     * rights are still enforced afterwards.
     */
    @GameTest(template = T, timeoutTicks = 100)
    public static void securityPersistsAcrossReload(GameTestHelper helper) {
        BlockPos bay = GolemGameTests.baseNetwork(helper);
        BlockPos secPos = BUS_OUT;
        helper.setBlock(secPos, ModBlocks.SECURITY_TERMINAL.get().defaultBlockState().setValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING, Direction.NORTH));
        SecurityTerminalBlockEntity sec = be(helper, secPos, SecurityTerminalBlockEntity.class);

        ServerPlayer owner = GolemGameTests.testPlayer(helper);
        sec.setOwner(owner);

        ServerPlayer playerWithCard = net.neoforged.neoforge.common.util.FakePlayerFactory.get(helper.getLevel(),
                new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "player-card-persists"));
        sec.getCards().setItem(0, IdentityPunchCardItem.of(playerWithCard.getUUID(), "player-card-persists", SecurityPermission.EXTRACT.mask));

        ServerPlayer playerNoCard = net.neoforged.neoforge.common.util.FakePlayerFactory.get(helper.getLevel(),
                new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "player-nocard-persists"));

        helper.runAfterDelay(10, () -> {
            SecurityTerminalBlockEntity loaded = reload(helper, secPos, SecurityTerminalBlockEntity.class);

            helper.assertTrue(owner.getUUID().equals(loaded.getOwnerId()), "owner UUID survives reload");
            helper.assertTrue(owner.getScoreboardName().equals(loaded.getOwnerName()), "owner name survives reload");
            helper.assertTrue(!loaded.getCards().getItem(0).isEmpty(), "punch card survives reload");

            GolemNetwork n = net(helper, bay);
            helper.assertTrue(n.hasPermission(owner, SecurityPermission.BUILD), "owner keeps BUILD permission after reload");
            helper.assertTrue(n.hasPermission(owner, SecurityPermission.EXTRACT), "owner keeps EXTRACT permission after reload");
            helper.assertTrue(n.hasPermission(playerWithCard, SecurityPermission.EXTRACT), "cardholder keeps EXTRACT permission after reload");
            helper.assertTrue(!n.hasPermission(playerWithCard, SecurityPermission.BUILD), "cardholder still denied BUILD permission");
            helper.assertTrue(!n.hasPermission(playerWithCard, SecurityPermission.INSERT), "cardholder still denied INSERT permission");
            helper.assertTrue(!n.hasPermission(playerNoCard, SecurityPermission.EXTRACT), "cardless player still denied EXTRACT permission");
            helper.assertTrue(!n.hasPermission(playerNoCard, SecurityPermission.BUILD), "cardless player still denied BUILD permission");

            helper.succeed();
        });
    }

    // ------------------------------------------------------------ Stock Manometer

    /**
     * Stock Manometer mode "below" with hysteresis (guide book's logs example: 500 and 2000):
     * on at the first mark (<= 500), stays on up to the second, off above it (>= 2000),
     * on again only when falling back to the first mark (<= 500).
     */
    @GameTest(template = T, timeoutTicks = 120)
    public static void stockManometerBelowHysteresis(GameTestHelper helper) {
        BlockPos bay = GolemGameTests.baseNetwork(helper);
        be(helper, bay, CopperDriveBayBlockEntity.class).getContainer().setItem(0, new ItemStack(ModItems.COPPER_DISK_64K.get()));

        BlockPos manoPos = BUS_OUT;
        helper.setBlock(manoPos, ModBlocks.STOCK_MANOMETER.get().defaultBlockState().setValue(DirectionalBlock.FACING, Direction.NORTH));
        StockManometerBlockEntity mano = be(helper, manoPos, StockManometerBlockEntity.class);
        mano.getFilter().setItem(0, new ItemStack(Items.OAK_LOG));
        mano.setMode(StockManometerBlockEntity.MODE_BELOW);
        mano.setThreshold(500);
        mano.setHysteresis(2000);

        helper.runAfterDelay(15, () -> {
            helper.assertTrue(mano.getSignal() == 15, "0 <= 500: signal starts ON (15)");

            // Add 1000 logs: between 500 and 2000 -> signal stays ON (15)
            net(helper, bay).insertItem(new ItemStack(Items.OAK_LOG, 1000), false);

            helper.runAfterDelay(15, () -> {
                helper.assertTrue(mano.getSignal() == 15, "stock=1000 stays ON (15) between 500 and 2000");

                // Add 1500 logs (total = 2500): above 2000 -> signal turns OFF (0)
                net(helper, bay).insertItem(new ItemStack(Items.OAK_LOG, 1500), false);

                helper.runAfterDelay(15, () -> {
                    helper.assertTrue(mano.getSignal() == 0, "stock=2500 turns OFF (0) above 2000");

                    // Extract 1500 logs (total = 1000): between 500 and 2000 -> signal stays OFF (0)
                    net(helper, bay).extractItem(new ItemStack(Items.OAK_LOG), 1500, false);

                    helper.runAfterDelay(15, () -> {
                        helper.assertTrue(mano.getSignal() == 0, "stock=1000 stays OFF (0) while falling, until <= 500");

                        // Extract 600 logs (total = 400): at or below 500 -> signal turns ON (15) again
                        net(helper, bay).extractItem(new ItemStack(Items.OAK_LOG), 600, false);

                        helper.runAfterDelay(15, () -> {
                            helper.assertTrue(mano.getSignal() == 15, "stock=400 turns ON (15) again at <= 500");
                            helper.succeed();
                        });
                    });
                });
            });
        });
    }

    /**
     * Stock Manometer mode, both numbers and the filter survive a save and load,
     * and the signal behaves the same afterwards.
     */
    @GameTest(template = T, timeoutTicks = 100)
    public static void stockManometerSettingsSurviveReload(GameTestHelper helper) {
        BlockPos bay = GolemGameTests.baseNetwork(helper);
        BlockPos manoPos = BUS_OUT;
        helper.setBlock(manoPos, ModBlocks.STOCK_MANOMETER.get().defaultBlockState().setValue(DirectionalBlock.FACING, Direction.NORTH));
        StockManometerBlockEntity mano = be(helper, manoPos, StockManometerBlockEntity.class);
        mano.getFilter().setItem(0, new ItemStack(Items.DIAMOND));
        mano.setMode(StockManometerBlockEntity.MODE_BELOW);
        mano.setThreshold(50);
        mano.setHysteresis(200);

        // It first sees an empty network and latches on; 100 diamonds lie between the two marks, so it holds
        helper.runAfterDelay(15, () -> net(helper, bay).insertItem(new ItemStack(Items.DIAMOND, 100), false));
        helper.runAfterDelay(30, () -> {
            helper.assertTrue(mano.getSignal() == 15, "initial signal is 15 in below mode");

            StockManometerBlockEntity loaded = reload(helper, manoPos, StockManometerBlockEntity.class);

            helper.assertTrue(loaded.getMode() == StockManometerBlockEntity.MODE_BELOW, "mode preserved after reload");
            helper.assertTrue(loaded.getThreshold() == 50, "threshold preserved after reload");
            helper.assertTrue(loaded.getHysteresis() == 200, "hysteresis preserved after reload");
            helper.assertTrue(loaded.getFilter().getItem(0).is(Items.DIAMOND), "filter preserved after reload");
            helper.assertTrue(loaded.getSignal() == 15, "signal state preserved after reload");

            // Verify behavior continues identically:
            // Insert 150 diamonds (total = 250 > 200) -> turns OFF (0)
            net(helper, bay).insertItem(new ItemStack(Items.DIAMOND, 150), false);

            helper.runAfterDelay(15, () -> {
                helper.assertTrue(loaded.getSignal() == 0, "signal turns OFF (0) above hysteresis mark (250 > 200)");

                // Extract 170 diamonds (total = 80, between 50 and 200) -> stays OFF (0)
                net(helper, bay).extractItem(new ItemStack(Items.DIAMOND), 170, false);

                helper.runAfterDelay(15, () -> {
                    helper.assertTrue(loaded.getSignal() == 0, "signal stays OFF (0) in deadband (80)");

                    // Extract 50 diamonds (total = 30 <= 50) -> turns ON (15)
                    net(helper, bay).extractItem(new ItemStack(Items.DIAMOND), 50, false);

                    helper.runAfterDelay(15, () -> {
                        helper.assertTrue(loaded.getSignal() == 15, "signal turns ON (15) below threshold (30 <= 50)");
                        helper.succeed();
                    });
                });
            });
        });
    }

    // ------------------------------------------------------------ Atlas Array

    /**
     * An Atlas Array built wrongly (missing part, expander in crown, base, or corner) does not provide
     * array capacity; positive controls with basic and quantum expanders provide the book's stated capacity.
     */
    @GameTest(template = T, timeoutTicks = 120)
    public static void atlasRejectsBadLayouts(GameTestHelper helper) {
        // Positive Control 1: Correct layout with 4 basic + 4 quantum expanders = 1280 channels (256/line)
        GolemGameTests.buildArray(helper, 4, 4);
        GolemGameTests.powered(helper);
        GolemGameTests.putBay(helper, BAY);

        helper.runAfterDelay(5, () -> {
            var rOk1 = AtlasStructure.check(helper.getLevel(), helper.absolutePos(CORE));
            helper.assertTrue(rOk1.valid(), "4+4 expander array is valid multiblock");
            helper.assertTrue(rOk1.expanders() == 4 && rOk1.quantumExpanders() == 4, "expanders counted: 4+4");
            GolemNetwork net1 = net(helper, BAY);
            helper.assertTrue(net1.channelCapacity() == 1280, "4+4 expanders give 1280 channels, got " + net1.channelCapacity());
            helper.assertTrue(net1.lineCapacity() == 256, "4+4 expanders give 256 line capacity, got " + net1.lineCapacity());

            // Positive Control 2: Correct layout with 8 quantum expanders = 1600 channels (320/line)
            GolemGameTests.buildArray(helper, 0, 8);
            var rOk2 = AtlasStructure.check(helper.getLevel(), helper.absolutePos(CORE));
            helper.assertTrue(rOk2.valid(), "8 quantum expander array is valid multiblock");
            helper.assertTrue(rOk2.quantumExpanders() == 8, "8 quantum expanders counted");
            GolemNetwork net2 = net(helper, BAY);
            helper.assertTrue(net2.channelCapacity() == 1600, "8 quantum expanders give 1600 channels, got " + net2.channelCapacity());
            helper.assertTrue(net2.lineCapacity() == 320, "8 quantum expanders give 320 line capacity, got " + net2.lineCapacity());

            // Negative Control 1: Missing part (hole in crown)
            BlockPos crownPos = ORIGIN.offset(1, 3, 0);
            helper.setBlock(crownPos, Blocks.AIR);
            var rHole = AtlasStructure.check(helper.getLevel(), helper.absolutePos(CORE));
            helper.assertTrue(!rHole.valid(), "missing crown block invalidates array");
            helper.assertTrue(net(helper, BAY).channelCapacity() <= 16, "capacity drops when part missing");

            // Negative Control 2: Expander in crown (layer 3)
            helper.setBlock(crownPos, ModBlocks.CHANNEL_EXPANDER.get().defaultBlockState());
            var rCrownExp = AtlasStructure.check(helper.getLevel(), helper.absolutePos(CORE));
            helper.assertTrue(!rCrownExp.valid(), "expander in crown is rejected");
            helper.assertTrue(net(helper, BAY).channelCapacity() <= 16, "capacity drops with expander in crown");
            GolemGameTests.put(helper, crownPos, ModBlocks.ATLAS_CASING.get()); // restore

            // Negative Control 3: Expander in base (layer 0)
            BlockPos basePos = ORIGIN.offset(1, 0, 0);
            helper.setBlock(basePos, ModBlocks.CHANNEL_EXPANDER.get().defaultBlockState());
            var rBaseExp = AtlasStructure.check(helper.getLevel(), helper.absolutePos(CORE));
            helper.assertTrue(!rBaseExp.valid(), "expander in base is rejected");
            helper.assertTrue(net(helper, BAY).channelCapacity() <= 16, "capacity drops with expander in base");
            GolemGameTests.put(helper, basePos, ModBlocks.ATLAS_CONDUIT.get()); // restore

            // Negative Control 4: Expander in corner (layer 1 Atlant position)
            BlockPos cornerPos = ORIGIN.offset(1, 1, 1);
            helper.setBlock(cornerPos, ModBlocks.CHANNEL_EXPANDER.get().defaultBlockState());
            var rCornerExp = AtlasStructure.check(helper.getLevel(), helper.absolutePos(CORE));
            helper.assertTrue(!rCornerExp.valid(), "expander in corner is rejected");
            helper.assertTrue(net(helper, BAY).channelCapacity() <= 16, "capacity drops with expander in corner");

            helper.succeed();
        });
    }
}
