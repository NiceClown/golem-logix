package com.golemlogix.gametest;

import com.golemlogix.GolemLogixMod;
import com.golemlogix.blockentity.BusBlockEntity;
import com.golemlogix.blockentity.CardCatalogBlockEntity;
import com.golemlogix.blockentity.CopperDriveBayBlockEntity;
import com.golemlogix.blockentity.CopperTankBlockEntity;
import com.golemlogix.blockentity.CryoMixerBlockEntity;
import com.golemlogix.blockentity.InterfaceBlockEntity;
import com.golemlogix.blockentity.PatternEncoderBlockEntity;
import com.golemlogix.blockentity.SecurityTerminalBlockEntity;
import com.golemlogix.init.ModBlocks;
import com.golemlogix.init.ModItems;
import com.golemlogix.items.FluidGhostItem;
import com.golemlogix.items.IdentityPunchCardItem;
import com.golemlogix.menu.BusMenu;
import com.golemlogix.menu.CardCatalogMenu;
import com.golemlogix.menu.CryoMixerMenu;
import com.golemlogix.menu.GolemMenu;
import com.golemlogix.menu.InterfaceMenu;
import com.golemlogix.menu.PatternEncoderMenu;
import com.golemlogix.menu.SecurityTerminalMenu;
import com.golemlogix.menu.TerminalMenu;
import com.golemlogix.network.CraftDonePayload;
import com.golemlogix.network.CraftPlanPayload;
import com.golemlogix.network.MonitorPayload;
import com.golemlogix.network.PrecraftPayload;
import com.golemlogix.network.RecipeFillPayload;
import com.golemlogix.network.TerminalActionPayload;
import com.golemlogix.network.TerminalInfoPayload;
import com.golemlogix.network.TerminalListPayload;
import com.golemlogix.security.SecurityPermission;
import com.golemlogix.system.DiskStorage;
import com.golemlogix.system.GolemNetwork;
import com.golemlogix.system.ItemKey;
import com.mojang.authlib.GameProfile;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.FunctionGameTestInstance;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.network.Connection;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.gametest.GameTestHooks;
import net.neoforged.neoforge.network.connection.ConnectionType;
import net.neoforged.neoforge.registries.RegisterEvent;
import net.neoforged.neoforge.transfer.fluid.FluidResource;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import static com.golemlogix.gametest.GolemGameTests.*;

/**
 * Regression GameTests for menu security, slot/ghost handling, and payload codec bounds.
 */
@EventBusSubscriber(modid = GolemLogixMod.MOD_ID)
public final class MenusRegressionTests {
    private record Entry(String name, Consumer<GameTestHelper> body, int maxTicks) {
    }

    private static final List<Entry> TESTS = List.of(
            new Entry("menusterminalsecurityinsertbypass", MenusRegressionTests::terminalSecurityInsertBypass, 120),
            new Entry("menussecurityterminalmenuownercheck", MenusRegressionTests::securityTerminalMenuOwnerCheck, 80),
            new Entry("menuscodecboundsrejectmalformed", MenusRegressionTests::codecBoundsRejectMalformed, 40),
            new Entry("menusslotandghostsafety", MenusRegressionTests::slotAndGhostSafety, 120),
            new Entry("menusterminalcachefreshness", MenusRegressionTests::terminalCacheFreshness, 120),
            new Entry("menusterminalclientindex", MenusRegressionTests::terminalClientIndex, 40),
            new Entry("menusterminalinfoliverefresh", MenusRegressionTests::terminalInfoLiveRefresh, 120)
    );

    private MenusRegressionTests() {
    }

    @SubscribeEvent
    public static void onRegisterFunctions(RegisterEvent event) {
        if (!GameTestHooks.isGametestEnabled()) {
            return;
        }
        event.register(Registries.TEST_FUNCTION, helper -> {
            for (Entry t : TESTS) {
                helper.register(id(t.name()), t.body());
            }
        });
    }

    @SubscribeEvent
    public static void onRegisterTests(RegisterGameTestsEvent event) {
        if (!GameTestHooks.isGametestEnabled()) {
            return;
        }
        Holder<TestEnvironmentDefinition<?>> env = event.registerEnvironment(
                id("menus_regression"), new TestEnvironmentDefinition.AllOf(List.of()));
        for (Entry t : TESTS) {
            ResourceKey<Consumer<GameTestHelper>> fn = ResourceKey.create(Registries.TEST_FUNCTION, id(t.name()));
            event.registerTest(id(t.name()), new FunctionGameTestInstance(fn, new TestData<>(env, id("empty"), t.maxTicks(), 0, true)));
        }
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(GolemLogixMod.MOD_ID, path);
    }

    private static ServerPlayer fakePlayer(GameTestHelper helper, String name) {
        return FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), name));
    }

    /**
     * A player without INSERT permission cannot sneak items into a secured network through shift-click,
     * Clear Grid button, JEI fillGrid, or closing the terminal; and wireless stillValid enforces security.
     */
    private static void terminalSecurityInsertBypass(GameTestHelper helper) {
        BlockPos bay = baseNetwork(helper);
        BlockPos secPos = BUS_OUT;
        helper.setBlock(secPos, ModBlocks.SECURITY_TERMINAL.get().defaultBlockState()
                .setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH));
        SecurityTerminalBlockEntity sec = helper.getBlockEntity(secPos, SecurityTerminalBlockEntity.class);
        ServerPlayer owner = testPlayer(helper);
        sec.setOwner(owner);

        ServerPlayer extractOnly = fakePlayer(helper, "menus-extract-only");
        sec.getCards().setItem(0, IdentityPunchCardItem.of(extractOnly.getUUID(), "menus-extract-only",
                SecurityPermission.EXTRACT.mask));
        ServerPlayer stranger = fakePlayer(helper, "menus-stranger");

        helper.setBlock(TERM, ModBlocks.CRAFTING_TERMINAL.get().defaultBlockState());
        BlockPos absTerm = helper.absolutePos(TERM);
        BlockPos absCore = helper.absolutePos(CORE);

        helper.runAfterDelay(10, () -> {
            GolemNetwork n = net(helper, bay);
            TerminalMenu menu = new TerminalMenu(91, extractOnly.getInventory(), helper.getLevel(), absTerm, absTerm,
                    ModBlocks.CRAFTING_TERMINAL.get(), true, false, 0);

            // 1. Shift-click from player inventory denied without INSERT
            extractOnly.getInventory().setItem(0, new ItemStack(Items.EMERALD, 4));
            int playerSlot0 = menu.slots.size() - 9; // hotbar slot 0
            menu.quickMoveStack(extractOnly, playerSlot0);
            helper.assertTrue(n.itemCountOf(ItemKey.of(new ItemStack(Items.EMERALD))) == 0,
                    "shift-click from inventory must not insert without INSERT permission");
            helper.assertTrue(extractOnly.getInventory().getItem(0).getCount() == 4,
                    "player inventory emerald untouched after denied shift-click");

            // 2. Shift-click from crafting grid does not insert into network
            menu.getSlot(1).set(new ItemStack(Items.GOLD_INGOT, 3));
            menu.quickMoveStack(extractOnly, 1);
            helper.assertTrue(n.itemCountOf(ItemKey.of(new ItemStack(Items.GOLD_INGOT))) == 0,
                    "shift-click from crafting grid must not insert without INSERT permission");

            // 3. Clear Grid button (id=0) denied without INSERT
            menu.getSlot(1).set(new ItemStack(Items.LAPIS_LAZULI, 2));
            menu.clickMenuButton(extractOnly, 0);
            helper.assertTrue(n.itemCountOf(ItemKey.of(new ItemStack(Items.LAPIS_LAZULI))) == 0,
                    "Clear Grid button must not insert without INSERT permission");

            // 4. JEI fillGrid returning grid items must not insert without INSERT
            menu.getSlot(1).set(new ItemStack(Items.REDSTONE, 5));
            menu.fillGrid(extractOnly, List.of(), false);
            helper.assertTrue(n.itemCountOf(ItemKey.of(new ItemStack(Items.REDSTONE))) == 0,
                    "fillGrid must not return grid items into network without INSERT permission");

            // 5. Closing terminal (removed) must not insert grid items without INSERT
            menu.getSlot(1).set(new ItemStack(Items.COPPER_INGOT, 6));
            menu.removed(extractOnly);
            helper.assertTrue(n.itemCountOf(ItemKey.of(new ItemStack(Items.COPPER_INGOT))) == 0,
                    "closing terminal must not insert grid items without INSERT permission");

            // 6. Wireless TerminalMenu.stillValid rejects stranger without permissions
            stranger.getInventory().setItem(0, new ItemStack(ModItems.PORTABLE_CHRONOMETER.get()));
            TerminalMenu wirelessStranger = new TerminalMenu(92, stranger.getInventory(), helper.getLevel(), absCore, null, null,
                    true, true, 2, 0);
            helper.assertTrue(!wirelessStranger.stillValid(stranger),
                    "wireless stillValid must reject unauthorized player when Security Terminal is present");

            // 7. Wireless fillGrid never pulls the locked Portable Chronometer out of lockedSlot
            owner.getInventory().setItem(0, new ItemStack(ModItems.PORTABLE_CHRONOMETER.get()));
            TerminalMenu wirelessOwner = new TerminalMenu(93, owner.getInventory(), helper.getLevel(), absCore, null, null,
                    true, true, 2, 0);
            wirelessOwner.fillGrid(owner, List.of(List.of(new ItemStack(ModItems.PORTABLE_CHRONOMETER.get()))), false);
            helper.assertTrue(owner.getInventory().getItem(0).is(ModItems.PORTABLE_CHRONOMETER.get()),
                    "fillGrid must not pull the locked Chronometer out of lockedSlot");

            helper.succeed();
        });
    }

    /**
     * SecurityTerminalMenu restricts stillValid and togglePermission to owner/OP, masks permission bits,
     * and refuses toggling unbound cards.
     */
    private static void securityTerminalMenuOwnerCheck(GameTestHelper helper) {
        BlockPos secPos = new BlockPos(2, 1, 2);
        helper.setBlock(secPos, ModBlocks.SECURITY_TERMINAL.get().defaultBlockState()
                .setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH));
        SecurityTerminalBlockEntity sec = helper.getBlockEntity(secPos, SecurityTerminalBlockEntity.class);
        BlockPos absPos = helper.absolutePos(secPos);

        ServerPlayer owner = testPlayer(helper);
        owner.setPos(absPos.getX() + 0.5, absPos.getY() + 0.5, absPos.getZ() + 0.5);
        sec.setOwner(owner);

        ServerPlayer stranger = fakePlayer(helper, "menus-sec-stranger");
        stranger.setPos(absPos.getX() + 0.5, absPos.getY() + 0.5, absPos.getZ() + 0.5);

        sec.getCards().setItem(0, IdentityPunchCardItem.of(stranger.getUUID(), "menus-sec-stranger", SecurityPermission.INSERT.mask));
        sec.getCards().setItem(1, new ItemStack(ModItems.IDENTITY_PUNCH_CARD.get())); // unbound

        SecurityTerminalMenu strangerMenu = new SecurityTerminalMenu(94, stranger.getInventory(),
                sec.getCards(), absPos, sec.getOwnerId(), sec.getOwnerName());
        helper.assertTrue(!strangerMenu.stillValid(stranger), "non-owner fails SecurityTerminalMenu.stillValid");
        strangerMenu.togglePermission(stranger, 0, SecurityPermission.BUILD.mask);
        helper.assertTrue(IdentityPunchCardItem.getPermissions(sec.getCards().getItem(0)) == SecurityPermission.INSERT.mask,
                "non-owner cannot toggle permissions");

        SecurityTerminalMenu ownerMenu = new SecurityTerminalMenu(95, owner.getInventory(),
                sec.getCards(), absPos, sec.getOwnerId(), sec.getOwnerName());
        helper.assertTrue(ownerMenu.stillValid(owner), "owner passes SecurityTerminalMenu.stillValid");
        // Out-of-range mask bits ignored
        ownerMenu.togglePermission(owner, 0, 0xFFFF0000);
        helper.assertTrue(IdentityPunchCardItem.getPermissions(sec.getCards().getItem(0)) == SecurityPermission.INSERT.mask,
                "out-of-range permMask bits are ignored");
        // Unbound card ignored
        ownerMenu.togglePermission(owner, 1, SecurityPermission.BUILD.mask);
        helper.assertTrue(!IdentityPunchCardItem.isBound(sec.getCards().getItem(1)),
                "unbound card remains unbound");
        // Valid toggle by owner succeeds
        ownerMenu.togglePermission(owner, 0, SecurityPermission.BUILD.mask);
        helper.assertTrue(IdentityPunchCardItem.getPermissions(sec.getCards().getItem(0))
                        == (SecurityPermission.INSERT.mask | SecurityPermission.BUILD.mask),
                "owner can toggle valid permission bit on bound card");

        helper.succeed();
    }

    /**
     * Codec decoders reject negative/oversized list lengths cleanly instead of throwing IAE or desyncing buffers,
     * and S2C encoders cap list lengths to match decoder bounds.
     */
    private static void codecBoundsRejectMalformed(GameTestHelper helper) {
        var reg = helper.getLevel().registryAccess();

        // PrecraftPayload rejects negative count
        RegistryFriendlyByteBuf negPrecraft = new RegistryFriendlyByteBuf(Unpooled.buffer(), reg, ConnectionType.NEOFORGE);
        negPrecraft.writeVarInt(1);
        negPrecraft.writeVarInt(-1);
        boolean threw = false;
        try {
            PrecraftPayload.STREAM_CODEC.decode(negPrecraft);
        } catch (DecoderException e) {
            threw = true;
        }
        helper.assertTrue(threw, "PrecraftPayload rejects negative item count");

        // RecipeFillPayload rejects oversized slot count
        RegistryFriendlyByteBuf bigFill = new RegistryFriendlyByteBuf(Unpooled.buffer(), reg, ConnectionType.NEOFORGE);
        bigFill.writeVarInt(1);
        bigFill.writeBoolean(true);
        bigFill.writeBoolean(false);
        bigFill.writeVarInt(RecipeFillPayload.MAX_SLOTS + 1);
        threw = false;
        try {
            RecipeFillPayload.STREAM_CODEC.decode(bigFill);
        } catch (DecoderException e) {
            threw = true;
        }
        helper.assertTrue(threw, "RecipeFillPayload rejects oversized slot count");

        // CraftDonePayload caps oversized list on encode and round-trips with zero unread bytes
        List<ItemStack> many = new ArrayList<>();
        for (int i = 0; i < CraftDonePayload.MAX_ITEMS + 4; i++) {
            many.add(new ItemStack(Items.IRON_INGOT, i + 1));
        }
        RegistryFriendlyByteBuf doneBuf = new RegistryFriendlyByteBuf(Unpooled.buffer(), reg, ConnectionType.NEOFORGE);
        CraftDonePayload.STREAM_CODEC.encode(doneBuf, new CraftDonePayload(many));
        CraftDonePayload decoded = CraftDonePayload.STREAM_CODEC.decode(doneBuf);
        helper.assertTrue(decoded.items().size() == CraftDonePayload.MAX_ITEMS && doneBuf.readableBytes() == 0,
                "CraftDonePayload caps encoded size and leaves no unread bytes");

        // CraftPlanPayload clamps negative status ordinal and rejects negative entry count
        RegistryFriendlyByteBuf planBuf = new RegistryFriendlyByteBuf(Unpooled.buffer(), reg, ConnectionType.NEOFORGE);
        planBuf.writeVarInt(1);
        planBuf.writeVarInt(1);
        planBuf.writeVarInt(-5);
        planBuf.writeVarInt(0);
        planBuf.writeVarLong(0);
        planBuf.writeVarInt(-1);
        threw = false;
        try {
            CraftPlanPayload.STREAM_CODEC.decode(planBuf);
        } catch (DecoderException e) {
            threw = true;
        }
        helper.assertTrue(threw, "CraftPlanPayload rejects negative entry count");

        // MonitorPayload caps oversized cpu list on encode and round-trips cleanly
        List<MonitorPayload.Cpu> cpus = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            cpus.add(new MonitorPayload.Cpu(BlockPos.ZERO, i, -1, 0, 0, 0, ItemStack.EMPTY, List.of(), List.of(), List.of()));
        }
        RegistryFriendlyByteBuf monBuf = new RegistryFriendlyByteBuf(Unpooled.buffer(), reg, ConnectionType.NEOFORGE);
        MonitorPayload.STREAM_CODEC.encode(monBuf, new MonitorPayload(1, "", cpus));
        MonitorPayload decodedMon = MonitorPayload.STREAM_CODEC.decode(monBuf);
        helper.assertTrue(decodedMon.cpus().size() == 3 && monBuf.readableBytes() == 0,
                "MonitorPayload round-trips cleanly");

        helper.succeed();
    }

    /**
     * Verifies slot & ghost fixes: CryoMixer take-only output tray, Interface empty slot clear,
     * Bus duplicate filter & ghost dupe guard, Encoder fluid clamping, and CardCatalog provider swap guard.
     */
    private static void slotAndGhostSafety(GameTestHelper helper) {
        ServerPlayer player = testPlayer(helper);

        // 1. CryoMixerMenu: shift-clicking snow blocks from player inventory must not merge into take-only output tray
        BlockPos mixerPos = new BlockPos(2, 1, 2);
        helper.setBlock(mixerPos, ModBlocks.CRYO_MIXER.get().defaultBlockState());
        CryoMixerBlockEntity mixer = helper.getBlockEntity(mixerPos, CryoMixerBlockEntity.class);
        mixer.getOutput().setItem(0, new ItemStack(Items.SNOW_BLOCK, 5));
        player.getInventory().setItem(0, new ItemStack(Items.SNOW_BLOCK, 10));
        CryoMixerMenu mixerMenu = new CryoMixerMenu(96, player.getInventory(), mixer.getOutput(),
                mixer.getUpgrades(), mixer.menuData(), mixer, ContainerLevelAccess.NULL);
        int mixerPlayerHotbar0 = mixerMenu.slots.size() - 9;
        mixerMenu.quickMoveStack(player, mixerPlayerHotbar0);
        helper.assertTrue(mixer.getOutput().getItem(0).getCount() == 5,
                "CryoMixer output tray must not accept items via shift-click merge");
        helper.assertTrue(player.getInventory().getItem(0).getCount() == 10,
                "player snow blocks remain untouched");

        // 2. InterfaceMenu: shift-clicking from inventory clears emptied player slot cleanly
        BlockPos ifacePos = new BlockPos(3, 1, 2);
        helper.setBlock(ifacePos, ModBlocks.GOLEM_INTERFACE.get().defaultBlockState());
        InterfaceBlockEntity iface = helper.getBlockEntity(ifacePos, InterfaceBlockEntity.class);
        player.getInventory().setItem(0, new ItemStack(Items.COBBLESTONE, 8));
        InterfaceMenu ifaceMenu = new InterfaceMenu(97, player.getInventory(), iface.getConfig(),
                iface.getStock(), iface.getInbox(), iface.getUpgrades(), iface.menuData(), iface);
        int ifaceHotbar0 = ifaceMenu.slots.size() - 9;
        ifaceMenu.quickMoveStack(player, ifaceHotbar0);
        helper.assertTrue(iface.getInbox().getItem(0).is(Items.COBBLESTONE) && iface.getInbox().getItem(0).getCount() == 8,
                "Interface quickMoveStack moves items to inbox");
        helper.assertTrue(player.getInventory().getItem(0) == ItemStack.EMPTY,
                "Interface quickMoveStack clears emptied player slot to ItemStack.EMPTY");

        // 3. BusMenu: no duplicate filter when earlier slot is empty, and direct quickMoveStack on ghost slot never dupes
        BlockPos busPos = new BlockPos(4, 1, 2);
        helper.setBlock(busPos, ModBlocks.INJECTOR_EXPORT_BUS.get().defaultBlockState()
                .setValue(DirectionalBlock.FACING, Direction.NORTH));
        BusBlockEntity bus = helper.getBlockEntity(busPos, BusBlockEntity.class);
        bus.getFilters().setItem(1, new ItemStack(Items.COAL));
        player.getInventory().clearContent();
        player.getInventory().setItem(0, new ItemStack(Items.COAL, 1));
        BusMenu busMenu = new BusMenu(98, player.getInventory(), bus.getFilters(), bus.getUpgrades(),
                BusMenu.KIND_EXPORT, bus.menuData());
        int busHotbar0 = busMenu.slots.size() - 9;
        busMenu.quickMoveStack(player, busHotbar0);
        helper.assertTrue(bus.getFilters().getItem(0).isEmpty() && bus.getFilters().getItem(1).is(Items.COAL),
                "BusMenu shift-click does not duplicate an item already present in a later filter slot");
        busMenu.quickMoveStack(player, 1); // direct shift-click on ghost slot 1
        helper.assertTrue(player.getInventory().countItem(Items.COAL) == 1,
                "direct quickMoveStack on ghost slot must not dupe real items into player inventory");

        // 4. PatternEncoderMenu: fluid ghost amount clamped in setCount and fillFromRecipe without overflow
        BlockPos encPos = new BlockPos(5, 1, 2);
        helper.setBlock(encPos, ModBlocks.PATTERN_ENCODER.get().defaultBlockState());
        PatternEncoderBlockEntity enc = helper.getBlockEntity(encPos, PatternEncoderBlockEntity.class);
        PatternEncoderMenu encMenu = new PatternEncoderMenu(99, player.getInventory(), enc.getItems(), enc.data(),
                ContainerLevelAccess.NULL);
        ItemStack waterGhost = FluidGhostItem.of(new FluidStack(Fluids.WATER, 1000));
        encMenu.fillFromRecipe(false, List.of(
                List.of(FluidGhostItem.of(new FluidStack(Fluids.WATER, Integer.MAX_VALUE))),
                List.of(waterGhost)), List.of(new ItemStack(Items.ICE)));
        helper.assertTrue(FluidGhostItem.amount(enc.getItems().getItem(0)) == GolemMenu.MAX_FLUID_GHOST,
                "fillFromRecipe clamps summed fluid mB without integer overflow");
        encMenu.setCount(0, Integer.MAX_VALUE);
        helper.assertTrue(FluidGhostItem.amount(enc.getItems().getItem(0)) == GolemMenu.MAX_FLUID_GHOST,
                "setCount caps fluid ghost mB to MAX_FLUID_GHOST");

        // 5. CardCatalogMenu: clicking providerSlot with renamed provider cannot drop below minProviders()
        BlockPos catPos = new BlockPos(6, 1, 2);
        helper.setBlock(catPos, ModBlocks.CARD_CATALOG.get().defaultBlockState());
        CardCatalogBlockEntity cat = helper.getBlockEntity(catPos, CardCatalogBlockEntity.class);
        cat.getProviders().setItem(0, new ItemStack(ModBlocks.PATTERN_PROVIDER.get(), 3));
        cat.getCards().setItem(CardCatalogBlockEntity.slotsPerProvider(), card(craftingPattern(new ItemStack(Items.STICK, 4), new ItemStack(Items.OAK_PLANKS))));
        helper.assertTrue(cat.minProviders() == 2, "catalog requires at least 2 providers");
        CardCatalogMenu catMenu = new CardCatalogMenu(100, player.getInventory(), cat, ContainerLevelAccess.NULL);
        ItemStack renamedProvider = new ItemStack(ModBlocks.PATTERN_PROVIDER.get(), 1);
        renamedProvider.set(DataComponents.CUSTOM_NAME, Component.literal("Renamed"));
        catMenu.setCarried(renamedProvider);
        catMenu.clicked(catMenu.providerSlot(), 0, ContainerInput.PICKUP, player);
        helper.assertTrue(cat.getProviders().getItem(0).getCount() == 3,
                "clicking providerSlot with a single renamed provider must not swap below minProviders()");

        helper.succeed();
    }

    /**
     * Proves TerminalMenu's cached GolemNetwork.get() sees same-tick topology, security, and bay-disk mutations:
     * placing a Security Terminal, toggling card permissions, inserting a pre-filled disk, and severing a pipe.
     */
    private static void terminalCacheFreshness(GameTestHelper helper) {
        BlockPos bayPos = baseNetwork(helper);
        put(helper, TERM, ModBlocks.BASIC_SNOW_PIPE.get());
        BlockPos farTerm = TERM.west();
        helper.setBlock(farTerm, ModBlocks.ANALOG_TERMINAL.get().defaultBlockState());

        ServerPlayer owner = testPlayer(helper);
        ServerPlayer stranger = fakePlayer(helper, "menus-cache-stranger");
        BlockPos absTerm = helper.absolutePos(farTerm);

        helper.runAfterDelay(10, () -> {
            GolemNetwork n = GolemNetwork.get(helper.getLevel(), absTerm);
            n.insertItem(new ItemStack(Items.IRON_INGOT, 16), false);

            TerminalMenu ownerMenu = new TerminalMenu(101, owner.getInventory(), helper.getLevel(), absTerm, absTerm,
                    ModBlocks.ANALOG_TERMINAL.get(), false, false, 0);
            TerminalMenu strangerMenu = new TerminalMenu(102, stranger.getInventory(), helper.getLevel(), absTerm, absTerm,
                    ModBlocks.ANALOG_TERMINAL.get(), false, false, 0);
            ownerMenu.broadcastChanges();
            TerminalActionPayload takeIron = new TerminalActionPayload(102,
                    TerminalActionPayload.Action.TAKE_ONE, new ItemStack(Items.IRON_INGOT), FluidStack.EMPTY);

            // Warm cache: unsecured network allows stranger to take 1 iron
            strangerMenu.handleAction(stranger, takeIron);
            helper.assertTrue(strangerMenu.getCarried().is(Items.IRON_INGOT) && strangerMenu.getCarried().getCount() == 1,
                    "unsecured terminal allows extract on warm cache");
            strangerMenu.setCarried(ItemStack.EMPTY);

            // 1. Placing a Security Terminal invalidates cache synchronously on the same tick
            helper.setBlock(BUS_OUT, ModBlocks.SECURITY_TERMINAL.get().defaultBlockState()
                    .setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH));
            SecurityTerminalBlockEntity sec = helper.getBlockEntity(BUS_OUT, SecurityTerminalBlockEntity.class);
            sec.setOwner(owner);
            strangerMenu.handleAction(stranger, takeIron);
            helper.assertTrue(strangerMenu.getCarried().isEmpty(),
                    "newly placed Security Terminal must immediately block unauthorized terminal extract");

            // 2. Granting & revoking EXTRACT permission on existing Security Terminal is read live
            sec.getCards().setItem(0, IdentityPunchCardItem.of(stranger.getUUID(), "menus-cache-stranger",
                    SecurityPermission.EXTRACT.mask));
            strangerMenu.handleAction(stranger, takeIron);
            helper.assertTrue(strangerMenu.getCarried().is(Items.IRON_INGOT) && strangerMenu.getCarried().getCount() == 1,
                    "adding EXTRACT card must immediately allow extract");
            strangerMenu.setCarried(ItemStack.EMPTY);

            sec.togglePermission(0, SecurityPermission.EXTRACT);
            strangerMenu.handleAction(stranger, takeIron);
            helper.assertTrue(strangerMenu.getCarried().isEmpty(),
                    "revoking EXTRACT permission must immediately block extract");

            // 3. Inserting a pre-filled disk into an existing Drive Bay is visible immediately on cached network
            CopperDriveBayBlockEntity bay = helper.getBlockEntity(bayPos, CopperDriveBayBlockEntity.class);
            ItemStack disk = new ItemStack(ModItems.COPPER_DISK_1K.get());
            DiskStorage.insertItem(disk, new ItemStack(Items.DIAMOND, 4), false);
            bay.getContainer().setItem(1, disk);
            ownerMenu.handleAction(owner, new TerminalActionPayload(101,
                    TerminalActionPayload.Action.TAKE_ONE, new ItemStack(Items.DIAMOND), FluidStack.EMPTY));
            helper.assertTrue(ownerMenu.getCarried().is(Items.DIAMOND) && ownerMenu.getCarried().getCount() == 1,
                    "disk inserted into existing bay must be immediately extractable through cached terminal");
            ownerMenu.setCarried(ItemStack.EMPTY);
            ownerMenu.broadcastChanges();

            // 4. Breaking the connecting pipe disconnects the terminal on the same tick
            helper.setBlock(TERM, Blocks.AIR.defaultBlockState());
            ownerMenu.handleAction(owner, new TerminalActionPayload(101,
                    TerminalActionPayload.Action.TAKE_ONE, new ItemStack(Items.IRON_INGOT), FluidStack.EMPTY));
            helper.assertTrue(ownerMenu.getCarried().isEmpty(),
                    "severed pipe must immediately disconnect terminal on same tick");
            ownerMenu.broadcastChanges();

            helper.succeed();
        });
    }

    /**
     * Verifies TerminalMenu.clientReceiveList and clientEntryOf across multi-chunk resets, updates,
     * interior swap-with-last removals, tail removals, craftable-only entries, and empty resets.
     */
    private static void terminalClientIndex(GameTestHelper helper) {
        ServerPlayer player = testPlayer(helper);
        TerminalMenu client = new TerminalMenu(103, player.getInventory(), null, null, null, null, false, false, 0);

        int chunkSize = TerminalListPayload.CHUNK;
        int total = chunkSize + 50;
        List<TerminalListPayload.Entry> chunk1 = new ArrayList<>(chunkSize);
        List<TerminalListPayload.Entry> chunk2 = new ArrayList<>(50);
        ItemStack first = null, middle = null, tail = null;
        for (int i = 0; i < total; i++) {
            ItemStack s = new ItemStack(Items.STONE, 1);
            s.set(DataComponents.CUSTOM_NAME, Component.literal("idx_" + i));
            if (i == 0) first = s;
            if (i == 500) middle = s;
            if (i == total - 1) tail = s;
            TerminalListPayload.Entry entry = new TerminalListPayload.Entry(s, i + 1, i % 10 == 0);
            (i < chunkSize ? chunk1 : chunk2).add(entry);
        }

        client.clientReceiveList(new TerminalListPayload(103, true, chunk1));
        client.clientReceiveList(new TerminalListPayload(103, false, chunk2));
        helper.assertTrue(client.clientEntries.size() == total, "multi-chunk reset populated all " + total + " entries");
        helper.assertTrue(client.clientEntryOf(first) != null && client.clientEntryOf(first).count() == 1 && client.clientEntryOf(first).craftable(),
                "clientEntryOf finds first entry");
        helper.assertTrue(client.clientEntryOf(middle) != null && client.clientEntryOf(middle).count() == 501,
                "clientEntryOf finds middle entry");
        helper.assertTrue(client.clientEntryOf(tail) != null && client.clientEntryOf(tail).count() == total,
                "clientEntryOf finds tail entry");

        // Update existing entry
        client.clientReceiveList(new TerminalListPayload(103, false, List.of(
                new TerminalListPayload.Entry(middle, 9999, true))));
        helper.assertTrue(client.clientEntries.size() == total && client.clientEntryOf(middle).count() == 9999 && client.clientEntryOf(middle).craftable(),
                "updating entry preserves size and updates value");

        // Interior removal swaps tail into middle's slot; tail must stay indexed and updatable/removable
        client.clientReceiveList(new TerminalListPayload(103, false, List.of(
                new TerminalListPayload.Entry(middle, 0, false))));
        helper.assertTrue(client.clientEntries.size() == total - 1 && client.clientEntryOf(middle) == null,
                "removed interior entry is absent");
        helper.assertTrue(client.clientEntryOf(tail) != null && client.clientEntryOf(tail).count() == total,
                "swapped tail entry remains indexed after interior removal");

        client.clientReceiveList(new TerminalListPayload(103, false, List.of(
                new TerminalListPayload.Entry(tail, 77, false))));
        helper.assertTrue(client.clientEntryOf(tail) != null && client.clientEntryOf(tail).count() == 77,
                "swapped entry can be updated cleanly");

        // Remove swapped entry and also remove the current tail entry
        ItemStack currentTail = client.clientEntries.get(client.clientEntries.size() - 1).key();
        client.clientReceiveList(new TerminalListPayload(103, false, List.of(
                new TerminalListPayload.Entry(currentTail, 0, false),
                new TerminalListPayload.Entry(tail, 0, false),
                new TerminalListPayload.Entry(new ItemStack(Items.NETHER_STAR), 0, false))));
        helper.assertTrue(client.clientEntries.size() == total - 3 && client.clientEntryOf(currentTail) == null && client.clientEntryOf(tail) == null,
                "tail removal, swapped removal, and missing-key removal all maintain consistent index");

        // Craftable-only entry (count=0, craftable=true) is kept
        ItemStack beacon = new ItemStack(Items.BEACON);
        client.clientReceiveList(new TerminalListPayload(103, false, List.of(
                new TerminalListPayload.Entry(beacon, 0, true))));
        helper.assertTrue(client.clientEntryOf(beacon) != null && client.clientEntryOf(beacon).count() == 0 && client.clientEntryOf(beacon).craftable(),
                "craftable-only entry stays indexed");

        // Reset clears all entries and index
        client.clientReceiveList(new TerminalListPayload(103, true, List.of()));
        helper.assertTrue(client.clientEntries.isEmpty() && client.clientEntryOf(first) == null && client.clientEntryOf(beacon) == null,
                "empty reset clears entries and index");

        helper.succeed();
    }

    private static List<TerminalInfoPayload> attachInfoRecorder(ServerPlayer player) {
        List<TerminalInfoPayload> received = new ArrayList<>();
        player.connection = new ServerGamePacketListenerImpl(
                player.level().getServer(),
                new Connection(PacketFlow.SERVERBOUND),
                player,
                CommonListenerCookie.createInitial(player.getGameProfile(), false)) {
            @Override
            public void send(Packet<?> packet) {
                if (packet instanceof ClientboundCustomPayloadPacket custom
                        && custom.payload() instanceof TerminalInfoPayload info) {
                    received.add(info);
                }
            }
        };
        return received;
    }

    private static int fluidAmount(TerminalInfoPayload info, net.minecraft.world.level.material.Fluid fluid) {
        for (FluidStack f : info.fluids()) {
            if (f.getFluid() == fluid) {
                return f.getAmount();
            }
        }
        return 0;
    }

    /**
     * Verifies TerminalSync suppresses duplicate TerminalInfoPayload packets when nothing changed, yet immediately
     * updates on the next broadcastChanges() after inserting an empty item disk, an empty or pre-filled fluid disk,
     * or externally filling/draining a fluid tank behind a Periscope Storage Bus.
     */
    private static void terminalInfoLiveRefresh(GameTestHelper helper) {
        BlockPos bayPos = baseNetwork(helper);
        helper.setBlock(TERM, ModBlocks.ANALOG_TERMINAL.get().defaultBlockState());
        helper.setBlock(BUS_OUT, ModBlocks.PERISCOPE_STORAGE_BUS.get().defaultBlockState()
                .setValue(DirectionalBlock.FACING, Direction.NORTH));
        BlockPos tankPos = BUS_OUT.north();
        helper.setBlock(tankPos, ModBlocks.COPPER_TANK.get().defaultBlockState());

        ServerPlayer player = fakePlayer(helper, "menus-info-recorder");
        List<TerminalInfoPayload> received = attachInfoRecorder(player);
        BlockPos absTerm = helper.absolutePos(TERM);
        int delay = (int) (10 - (helper.getLevel().getGameTime() % 10)) + 10;

        helper.runAfterDelay(delay, () -> {
            CopperDriveBayBlockEntity bay = helper.getBlockEntity(bayPos, CopperDriveBayBlockEntity.class);
            CopperTankBlockEntity tank = helper.getBlockEntity(tankPos, CopperTankBlockEntity.class);
            TerminalMenu menu = new TerminalMenu(104, player.getInventory(), helper.getLevel(), absTerm, absTerm,
                    ModBlocks.ANALOG_TERMINAL.get(), false, false, 0);

            // Initial sync sends TerminalInfoPayload with 1K item disk and no fluids
            menu.broadcastChanges();
            helper.assertTrue(received.size() == 1, "initial sync sends TerminalInfoPayload");
            long baseItemCap = received.get(0).itemsCapacity();
            helper.assertTrue(baseItemCap > 0 && received.get(0).fluidsCapacity() == 0 && received.get(0).fluids().isEmpty(),
                    "initial info reflects 1K item disk and empty fluids");

            // Unchanged sync suppresses redundant TerminalInfoPayload
            menu.broadcastChanges();
            helper.assertTrue(received.size() == 1, "unchanged sync must not resend TerminalInfoPayload");

            // 1. Inserting an empty item disk (networkEpoch unchanged, items unchanged) updates itemsCapacity immediately
            ItemStack emptyItemDisk = new ItemStack(ModItems.COPPER_DISK_16K.get());
            bay.getContainer().setItem(1, emptyItemDisk);
            menu.broadcastChanges();
            helper.assertTrue(received.size() == 2, "empty item disk insert must trigger updated TerminalInfoPayload");
            helper.assertTrue(received.get(1).itemsCapacity() == baseItemCap + DiskStorage.capacity(emptyItemDisk),
                    "itemsCapacity reflects newly inserted empty 16K disk");

            // 2. Inserting an empty fluid disk updates fluidsCapacity immediately
            ItemStack emptyFluidDisk = new ItemStack(ModItems.FLUID_DISK_16K.get());
            bay.getContainer().setItem(2, emptyFluidDisk);
            menu.broadcastChanges();
            helper.assertTrue(received.size() == 3, "empty fluid disk insert must trigger updated TerminalInfoPayload");
            long fluidDiskCap = DiskStorage.capacity(emptyFluidDisk);
            helper.assertTrue(received.get(2).fluidsCapacity() == fluidDiskCap && received.get(2).fluidsUsed() == 0,
                    "fluidsCapacity reflects newly inserted empty fluid disk");

            // 3. Inserting a pre-filled fluid disk updates fluidsUsed and fluids list immediately
            ItemStack filledFluidDisk = new ItemStack(ModItems.FLUID_DISK_16K.get());
            DiskStorage.insertFluid(filledFluidDisk, new FluidStack(Fluids.LAVA, 2000), false);
            bay.getContainer().setItem(3, filledFluidDisk);
            menu.broadcastChanges();
            helper.assertTrue(received.size() == 4, "filled fluid disk insert must trigger updated TerminalInfoPayload");
            helper.assertTrue(received.get(3).fluidsCapacity() == fluidDiskCap * 2
                            && received.get(3).fluidsUsed() == 2000
                            && fluidAmount(received.get(3), Fluids.LAVA) == 2000,
                    "filled fluid disk updates fluidsUsed, fluidsCapacity, and fluid list");

            // 4. Externally filling tank behind Periscope (bypassing GolemNetwork.insertFluid) updates fluids immediately
            tank.tank().set(0, FluidResource.of(Fluids.WATER), 3000);
            menu.broadcastChanges();
            helper.assertTrue(received.size() == 5, "external Periscope tank fill must trigger updated TerminalInfoPayload");
            helper.assertTrue(fluidAmount(received.get(4), Fluids.WATER) == 3000
                            && fluidAmount(received.get(4), Fluids.LAVA) == 2000,
                    "external Periscope tank water appears alongside disk lava");

            // 5. Externally draining tank behind Periscope removes water immediately
            tank.tank().set(0, FluidResource.EMPTY, 0);
            menu.broadcastChanges();
            helper.assertTrue(received.size() == 6, "external Periscope tank drain must trigger updated TerminalInfoPayload");
            helper.assertTrue(fluidAmount(received.get(5), Fluids.WATER) == 0
                            && fluidAmount(received.get(5), Fluids.LAVA) == 2000,
                    "drained Periscope tank fluid disappears while disk lava stays");

            helper.succeed();
        });
    }
}
