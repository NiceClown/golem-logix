package com.golemlogix.gametest;

import com.golemlogix.GolemLogixMod;
import com.golemlogix.blockentity.BusBlockEntity;
import com.golemlogix.blockentity.CardCatalogBlockEntity;
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
import com.golemlogix.security.SecurityPermission;
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
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
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
            new Entry("menusslotandghostsafety", MenusRegressionTests::slotAndGhostSafety, 120)
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
}
