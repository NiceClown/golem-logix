package com.golemlogix.blockentity;

import com.golemlogix.blocks.GolemPlaneBlock;
import com.golemlogix.config.GolemConfig;
import com.golemlogix.init.ModBlockEntities;
import com.golemlogix.system.Fuzzy;
import com.golemlogix.system.GolemNetwork;
import com.golemlogix.system.ItemKey;
import com.golemlogix.upgrade.RedstoneControl;
import com.golemlogix.upgrade.RedstoneMode;
import com.golemlogix.upgrade.UpgradeContainer;
import com.golemlogix.upgrade.UpgradeSpec;
import com.golemlogix.upgrade.UpgradeType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.DirectionalPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashSet;
import java.util.Set;

/**
 * Formation Bus: the copper golem fused into it pulls a filtered block from the network and pushes it onto the free spot
 * in front over one bus interval (at least 2 ticks). Supports in-order, round-robin, and random slot ordering, plus speed,
 * autocraft, redstone, and fuzzy upgrades.
 */
public class FormationBusBlockEntity extends BlockEntity implements GolemPlaneBlock.Waking {
    public static final int FILTER_SLOTS = 9;
    /** Filter slot traversal modes shared with {@link BusBlockEntity}. */
    public static final int IN_ORDER = 0, ROUND_ROBIN = 1, RANDOM = 2;

    private static final double[] INTERVAL_MULT = {1.0, 0.75, 0.5, 0.25, 0.125};
    private static final long[] ORDER_BATCHES = {8L, 1L};
    private static final int CRAFT_REQUEST_COOLDOWN = 40;
    private static final int MAX_IDLE_TICKS = 20, MAX_IDLE_STEPS = 5;

    private final OwnedContainer filters = new OwnedContainer(FILTER_SLOTS, this, s -> true);
    private final UpgradeContainer upgrades = new UpgradeContainer(UpgradeSpec.FORMATION_BUS, this);
    private final RedstoneControl redstone = new RedstoneControl();
    private final int[] slotOrder = new int[FILTER_SLOTS];

    /** Block currently being pushed out (already extracted from the network), push start tick and duration. */
    private ItemStack carrying = ItemStack.EMPTY;
    private long pushStart;
    private int pushTicks = 2;
    private int idleSteps;
    private long sleepUntil;
    private long sleepEpoch;
    private long lastCraftRequest = -1000;
    private int order = IN_ORDER;
    private int next;
    private boolean replaceFluids = true;

    public FormationBusBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.FORMATION_BUS.get(), pos, state);
    }

    public boolean replaceFluids() {
        return replaceFluids;
    }

    public void setReplaceFluids(boolean replaceFluids) {
        if (this.replaceFluids != replaceFluids) {
            this.replaceFluids = replaceFluids;
            setChanged();
        }
    }

    /** Whether a block can be placed into a target spot given fluid-replacement preference, replaceability, and emptiness of fluid. */
    public static boolean canPlaceInto(boolean replaceFluids, boolean canBeReplaced, boolean fluidEmpty) {
        return com.golemlogix.system.FormationBusMath.canPlaceInto(replaceFluids, canBeReplaced, fluidEmpty);
    }

    public boolean canPlaceInto(BlockState state) {
        return canPlaceInto(replaceFluids, state.canBeReplaced(), state.getFluidState().isEmpty());
    }

    public OwnedContainer getFilters() {
        return filters;
    }

    public UpgradeContainer getUpgrades() {
        return upgrades;
    }

    public ItemStack carrying() {
        return carrying;
    }

    /** Push animation progress (0 at start, 1 when placed) for the renderer. */
    public float push(float partialTick) {
        if (carrying.isEmpty() || level == null) {
            return 0f;
        }
        return Math.min(1f, (level.getGameTime() - pushStart + partialTick) / pushTicks);
    }

    @Override
    public void wake() {
        idleSteps = 0;
        sleepUntil = 0;
    }

    @Override
    public void setChanged() {
        super.setChanged();
        wake();
    }

    /** Window data: 0 = priority (unused), 1 = redstone mode, 2 = order (IN_ORDER, ROUND_ROBIN, RANDOM), 3 = replaceFluids (0/1). */
    public ContainerData menuData() {
        return new ContainerData() {
            @Override
            public int get(int index) {
                return switch (index) {
                    case 1 -> redstone.mode().ordinal();
                    case 2 -> order;
                    case 3 -> replaceFluids ? 1 : 0;
                    default -> 0;
                };
            }

            @Override
            public void set(int index, int value) {
                if (index == 1) {
                    redstone.setMode(RedstoneMode.byId(value));
                    setChanged();
                } else if (index == 2) {
                    order = Math.floorMod(value, 3);
                    next = 0;
                    setChanged();
                } else if (index == 3) {
                    setReplaceFluids(value != 0);
                }
            }

            @Override
            public int getCount() {
                return 4;
            }
        };
    }

    public int interval() {
        int n = Math.min(upgrades.count(UpgradeType.SPEED), INTERVAL_MULT.length - 1);
        return Math.max(1, (int) Math.round(GolemConfig.BUS_INTERVAL_TICKS.get() * INTERVAL_MULT[n]));
    }

    // ------------------------------------------------------------------ tick

    public static void serverTick(Level level, BlockPos pos, BlockState state, FormationBusBlockEntity be) {
        be.tick((ServerLevel) level, pos, state);
    }

    private void tick(ServerLevel level, BlockPos pos, BlockState state) {
        boolean hasRedstone = upgrades.has(UpgradeType.REDSTONE);
        boolean powered = BlockEntities.pollRedstone(level, pos, hasRedstone, redstone);
        long now = level.getGameTime();
        Direction facing = state.getValue(DirectionalBlock.FACING);
        BlockPos target = pos.relative(facing);
        if (!carrying.isEmpty()) {
            if (now >= pushStart + pushTicks && level.isLoaded(target)) {
                finishPush(level, target, facing);
            }
            return;
        }
        int interval = interval();
        if (Math.floorMod(now + pos.asLong(), interval) != 0 || (now < sleepUntil && sleepEpoch == GolemNetwork.storageEpoch())) {
            return;
        }
        GolemNetwork net = GolemNetwork.get(level, pos);
        if (!net.isOnline() || !net.isDeviceActive(pos) || !redstone.allows(hasRedstone, powered, false)) {
            return;
        }
        if (!level.isLoaded(target) || !canPlaceInto(level.getBlockState(target))) {
            sleep(now, interval);
            return;
        }
        ItemStack taken = take(level, net, target, facing);
        if (taken.isEmpty()) {
            sleep(now, interval);
            return;
        }
        carrying = taken;
        pushStart = now;
        pushTicks = Math.max(2, interval);
        setChanged();
        redstone.allows(hasRedstone, powered, true);
        sync();
    }

    private void sleep(long now, int interval) {
        idleSteps = Math.min(MAX_IDLE_STEPS, idleSteps + 1);
        sleepUntil = now + Math.max(interval, Math.min(MAX_IDLE_TICKS, interval << idleSteps));
        sleepEpoch = GolemNetwork.storageEpoch();
    }

    /** Extracts one placeable filtered block from the network; with Autocraft Module, orders missing blocks. */
    private ItemStack take(ServerLevel level, GolemNetwork net, BlockPos target, Direction facing) {
        boolean fuzzy = upgrades.has(UpgradeType.FUZZY);
        Set<ItemKey> netKeys = fuzzy ? net.listItemsShared().keySet() : Set.of();
        orderMissing(level, net);
        BlockEntities.fillSlotOrder(slotOrder, order, next, level.getRandom());
        for (int i : slotOrder) {
            ItemStack filter = filters.getItem(i);
            if (filter.isEmpty()) {
                continue;
            }
            ItemStack want = Fuzzy.plain(filter).copyWithCount(1);
            ItemStack real = tryExtractBlock(level, net, target, facing, want);
            if (real.isEmpty() && fuzzy) {
                for (ItemKey k : Fuzzy.candidates(filter, netKeys, true)) {
                    if (!k.matches(want)) {
                        real = tryExtractBlock(level, net, target, facing, k.toStack(1));
                        if (!real.isEmpty()) {
                            break;
                        }
                    }
                }
            }
            if (!real.isEmpty()) {
                next = (i + 1) % FILTER_SLOTS;
                return real;
            }
        }
        return ItemStack.EMPTY;
    }

    private static ItemStack tryExtractBlock(ServerLevel level, GolemNetwork net, BlockPos target, Direction facing, ItemStack one) {
        if (!(one.getItem() instanceof BlockItem) || !fits(level, target, facing, one)) {
            return ItemStack.EMPTY;
        }
        if (net.extractItem(one, 1, true, target).isEmpty()) {
            return ItemStack.EMPTY;
        }
        return net.extractItem(one, 1, false, target);
    }

    /** Orders missing filtered blocks (batch of 8 if craftable, else 1) while the golem continues placing others. */
    private void orderMissing(ServerLevel level, GolemNetwork net) {
        long now = level.getGameTime();
        if (!upgrades.has(UpgradeType.CRAFTING) || now - lastCraftRequest < CRAFT_REQUEST_COOLDOWN) {
            return;
        }
        Set<ItemKey> asked = null;
        for (int i = 0; i < FILTER_SLOTS; i++) {
            ItemStack filter = filters.getItem(i);
            if (filter.isEmpty()) {
                continue;
            }
            ItemStack want = Fuzzy.plain(filter).copyWithCount(1);
            if (!(want.getItem() instanceof BlockItem)) {
                continue;
            }
            ItemKey key = ItemKey.of(want);
            if (asked == null) {
                asked = new HashSet<>();
            }
            GlobalPos bus = GlobalPos.of(level.dimension(), worldPosition);
            if (!asked.add(key) || !net.extractItem(want, 1, true, null).isEmpty()
                    || !net.canCraft(key) || net.busCraftPending(bus, key)) {
                continue;
            }
            lastCraftRequest = now;
            for (long amount : ORDER_BATCHES) {
                GolemNetwork.StartType r = BlockEntities.requestCraft(net, bus, key, amount).type();
                if (r == GolemNetwork.StartType.STARTED || r == GolemNetwork.StartType.NO_CPU) {
                    break;
                }
            }
        }
    }

    private static DirectionalPlaceContext context(Level level, BlockPos target, Direction facing, ItemStack stack) {
        return new DirectionalPlaceContext(level, target, facing, stack, facing.getOpposite());
    }

    private static boolean fits(Level level, BlockPos target, Direction facing, ItemStack stack) {
        BlockItem item = (BlockItem) stack.getItem();
        BlockState placed = item.getBlock().getStateForPlacement(context(level, target, facing, stack.copyWithCount(1)));
        return placed != null && placed.canSurvive(level, target);
    }

    private void finishPush(ServerLevel level, BlockPos target, Direction facing) {
        if (!canPlaceInto(level.getBlockState(target))) {
            return;
        }
        ItemStack one = carrying.copyWithCount(1);
        boolean placed = carrying.getItem() instanceof BlockItem item
                && item.place(context(level, target, facing, one)).consumesAction();
        if (!placed) {
            giveBack(level);
        }
        carrying = ItemStack.EMPTY;
        setChanged();
        sync();
    }

    private void giveBack(Level level) {
        if (carrying.isEmpty()) {
            return;
        }
        ItemStack rest = GolemNetwork.get(level, worldPosition).insertItem(carrying, false);
        if (!rest.isEmpty()) {
            Block.popResource(level, worldPosition, rest);
        }
        carrying = ItemStack.EMPTY;
    }

    public void dropContents() {
        if (level == null || level.isClientSide()) {
            return;
        }
        giveBack(level);
        BlockEntities.dropContainers(level, worldPosition, upgrades);
    }

    private void sync() {
        BlockEntities.sendUpdate(this, 2);
    }

    // ----------------------------------------------------------- persistence

    private void saveTag(CompoundTag tag, HolderLookup.Provider registries, boolean full) {
        if (full) {
            tag.put("Filters", filters.createTag(registries));
            tag.put("Upgrades", upgrades.createTag(registries));
            redstone.save(tag);
            tag.putInt("Order", order);
            tag.putInt("Next", next);
            tag.putBoolean("ReplaceFluids", replaceFluids);
        }
        if (!carrying.isEmpty()) {
            tag.put("Carrying", carrying.save(registries));
        }
        tag.putLong("PushStart", pushStart);
        tag.putInt("PushTicks", pushTicks);
    }

    private void loadTag(CompoundTag tag, HolderLookup.Provider registries, boolean full) {
        if (full) {
            filters.fromTag(tag.getList("Filters", Tag.TAG_COMPOUND), registries);
            upgrades.fromTag(tag.getList("Upgrades", Tag.TAG_COMPOUND), registries);
            redstone.load(tag);
            order = Math.floorMod(tag.contains("Order") ? tag.getInt("Order") : IN_ORDER, 3);
            next = Math.floorMod(tag.getInt("Next"), FILTER_SLOTS);
            replaceFluids = !tag.contains("ReplaceFluids") || tag.getBoolean("ReplaceFluids");
        }
        carrying = tag.contains("Carrying")
                ? ItemStack.parseOptional(registries, tag.getCompound("Carrying"))
                : ItemStack.EMPTY;
        pushStart = tag.getLong("PushStart");
        pushTicks = Math.max(1, tag.contains("PushTicks") ? tag.getInt("PushTicks") : 2);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        saveTag(tag, registries, true);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        loadTag(tag, registries, true);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        saveTag(tag, registries, false);
        return tag;
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider registries) {
        loadTag(tag, registries, false);
    }

    @Override
    public void onDataPacket(Connection net, ClientboundBlockEntityDataPacket pkt, HolderLookup.Provider registries) {
        handleUpdateTag(pkt.getTag(), registries);
    }
}
