package com.golemlogix.blockentity;

import com.golemlogix.blocks.GolemPlaneBlock;
import com.golemlogix.config.GolemConfig;
import com.golemlogix.init.ModBlockEntities;
import com.golemlogix.system.GolemNetwork;
import com.golemlogix.system.ItemKey;
import com.golemlogix.system.NbtBridge;
import com.golemlogix.upgrade.RedstoneControl;
import com.golemlogix.upgrade.RedstoneMode;
import com.golemlogix.upgrade.UpgradeContainer;
import com.golemlogix.upgrade.UpgradeSpec;
import com.golemlogix.upgrade.UpgradeType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
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

import java.util.ArrayList;
import java.util.List;

/**
 * Formation Bus: the copper golem fused into it takes a block named in the filter out of the network and pushes it out of the
 * mouth onto the free spot in front. The push takes as long as the bus's interval (at least two ticks), so the golem's hands keep
 * pace with four Compressed Co-Processors too; the block is set down when the push ends, and if the spot is taken meanwhile the
 * golem holds it until it is free again. Upgrades as on the Export Bus: speed, an Autocraft Module (it orders the block when the
 * network has none), a Redstone Module, a Fuzzy Card.
 */
public class FormationBusBlockEntity extends BlockEntity implements GolemPlaneBlock.Waking {
    public static final int FILTER_SLOTS = 9;
    private static final double[] INTERVAL_MULT = {1.0, 0.75, 0.5, 0.25, 0.125};
    private static final int CRAFT_REQUEST_COOLDOWN = 40;
    private static final int MAX_IDLE_TICKS = 20, MAX_IDLE_STEPS = 5;

    private final OwnedContainer filters = new OwnedContainer(FILTER_SLOTS, this, s -> true);
    private final UpgradeContainer upgrades = new UpgradeContainer(UpgradeSpec.EXPORT_BUS, this);
    private final RedstoneControl redstone = new RedstoneControl();

    /** The block the golem is pushing out (already taken out of the network), when the push began and how long it lasts. */
    private ItemStack carrying = ItemStack.EMPTY;
    private long pushStart;
    private int pushTicks = 2;
    private int idleSteps;
    private long sleepUntil;
    private long lastCraftRequest = -1000;

    public FormationBusBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.FORMATION_BUS.get(), pos, state);
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

    /** How far the push is (0 at the start, 1 when the block is set down), for the renderer. */
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

    /** Window data: 0 = priority (unused), 1 = redstone mode. */
    public ContainerData menuData() {
        return new ContainerData() {
            @Override
            public int get(int index) {
                return index == 0 ? 0 : redstone.mode().ordinal();
            }

            @Override
            public void set(int index, int value) {
                if (index == 1) {
                    redstone.setMode(RedstoneMode.byId(value));
                    setChanged();
                }
            }

            @Override
            public int getCount() {
                return 2;
            }
        };
    }

    public int interval() {
        int n = upgrades.count(UpgradeType.SPEED);
        return Math.max(1, (int) Math.round(GolemConfig.BUS_INTERVAL_TICKS.get() * INTERVAL_MULT[n]));
    }

    // ------------------------------------------------------------------ tick

    public static void serverTick(Level level, BlockPos pos, BlockState state, FormationBusBlockEntity be) {
        be.tick((ServerLevel) level, pos, state);
    }

    private void tick(ServerLevel level, BlockPos pos, BlockState state) {
        boolean hasRedstone = upgrades.has(UpgradeType.REDSTONE);
        boolean powered = hasRedstone && level.hasNeighborSignal(pos);
        if (hasRedstone) {
            redstone.observe(powered);
        }
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
        if (Math.floorMod(now + pos.asLong(), interval) != 0 || now < sleepUntil) {
            return;
        }
        GolemNetwork net = GolemNetwork.get(level, pos);
        if (!net.isOnline() || !net.isDeviceActive(pos) || !redstone.allows(hasRedstone, powered, false)) {
            return;
        }
        if (!level.isLoaded(target) || !level.getBlockState(target).canBeReplaced()) {
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
        wake();
        redstone.allows(hasRedstone, powered, true);
        sync();
    }

    private void sleep(long now, int interval) {
        idleSteps = Math.min(MAX_IDLE_STEPS, idleSteps + 1);
        sleepUntil = now + Math.max(interval, Math.min(MAX_IDLE_TICKS, interval << idleSteps));
    }

    /** One block named in the filter that can stand in front, out of the network; with an Autocraft Module, orders a missing one. */
    private ItemStack take(ServerLevel level, GolemNetwork net, BlockPos target, Direction facing) {
        boolean fuzzy = upgrades.has(UpgradeType.FUZZY);
        java.util.Set<ItemKey> netKeys = fuzzy ? net.listItems().keySet() : java.util.Set.of();
        ItemKey missing = null;
        for (int i = 0; i < FILTER_SLOTS; i++) {
            ItemStack filter = filters.getItem(i);
            if (filter.isEmpty()) {
                continue;
            }
            ItemStack want = com.golemlogix.system.Fuzzy.plain(filter).copyWithCount(1);
            List<ItemStack> wants = new ArrayList<>();
            wants.add(want);
            for (ItemKey k : com.golemlogix.system.Fuzzy.candidates(filter, netKeys, fuzzy)) {
                if (!k.matches(want)) {
                    wants.add(k.toStack(1));
                }
            }
            boolean any = false;
            for (ItemStack one : wants) {
                if (!(one.getItem() instanceof BlockItem) || !fits(level, target, facing, one)) {
                    continue;
                }
                if (net.extractItem(one, 1, true, target).isEmpty()) {
                    continue;
                }
                any = true;
                ItemStack real = net.extractItem(one, 1, false, target);
                if (!real.isEmpty()) {
                    return real;
                }
            }
            if (!any && missing == null && want.getItem() instanceof BlockItem) {
                missing = ItemKey.of(want);
            }
        }
        long now = level.getGameTime();
        if (missing != null && upgrades.has(UpgradeType.CRAFTING) && now - lastCraftRequest >= CRAFT_REQUEST_COOLDOWN
                && net.canCraft(missing) && !net.busCraftPending(worldPosition, missing)) {
            lastCraftRequest = now;
            java.util.Map<ItemKey, Long> one = new java.util.LinkedHashMap<>();
            one.put(missing, 1L);
            net.startCrafting(one, null, worldPosition);
        }
        return ItemStack.EMPTY;
    }

    private static DirectionalPlaceContext context(Level level, BlockPos target, Direction facing, ItemStack stack) {
        return new DirectionalPlaceContext(level, target, facing, stack, facing.getOpposite());
    }

    /** The block would stand in front: placeable there and able to survive. */
    private static boolean fits(Level level, BlockPos target, Direction facing, ItemStack stack) {
        BlockItem item = (BlockItem) stack.getItem();
        BlockState placed = item.getBlock().getStateForPlacement(context(level, target, facing, stack.copyWithCount(1)));
        return placed != null && placed.canSurvive(level, target);
    }

    /** The push is over: the block goes down in front; if the spot was taken it waits, if it cannot stand there it goes back. */
    private void finishPush(ServerLevel level, BlockPos target, Direction facing) {
        if (!level.getBlockState(target).canBeReplaced()) {
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

    /** What the golem holds goes back into the network, or drops if it cannot. */
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

    /** Broken or replaced: the block in the golem's hands goes back, the upgrades drop. */
    public void dropContents() {
        if (level == null) {
            return;
        }
        giveBack(level);
        net.minecraft.world.Containers.dropContents(level, worldPosition, upgrades);
    }

    private void sync() {
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 2);
        }
    }

    // ----------------------------------------------------------- persistence

    private void saveTag(CompoundTag tag, HolderLookup.Provider registries, boolean full) {
        if (full) {
            tag.put("Filters", filters.createTag(registries));
            tag.put("Upgrades", upgrades.createTag(registries));
            redstone.save(tag);
        }
        if (!carrying.isEmpty()) {
            tag.put("Carrying", NbtBridge.saveItem(carrying, registries));
        }
        tag.putLong("PushStart", pushStart);
        tag.putInt("PushTicks", pushTicks);
    }

    private void loadTag(CompoundTag tag, HolderLookup.Provider registries, boolean full) {
        if (full) {
            filters.fromTag(tag.getListOrEmpty("Filters"), registries);
            upgrades.fromTag(tag.getListOrEmpty("Upgrades"), registries);
            redstone.load(tag);
        }
        carrying = tag.contains("Carrying") ? NbtBridge.loadItem(registries, tag.getCompoundOrEmpty("Carrying")).orElse(ItemStack.EMPTY)
                : ItemStack.EMPTY;
        pushStart = tag.getLongOr("PushStart", 0L);
        pushTicks = Math.max(1, tag.getIntOr("PushTicks", 2));
    }

    @Override
    protected void saveAdditional(net.minecraft.world.level.storage.ValueOutput output) {
        super.saveAdditional(output);
        CompoundTag tag = new CompoundTag();
        saveTag(tag, NbtBridge.registries(this), true);
        NbtBridge.write(output, tag);
    }

    @Override
    protected void loadAdditional(net.minecraft.world.level.storage.ValueInput input) {
        super.loadAdditional(input);
        loadTag(NbtBridge.read(input), input.lookup(), true);
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
    public void handleUpdateTag(net.minecraft.world.level.storage.ValueInput in) {
        loadTag(NbtBridge.read(in), in.lookup(), false);
    }

    @Override
    public void onDataPacket(net.minecraft.network.Connection net, net.minecraft.world.level.storage.ValueInput in) {
        handleUpdateTag(in);
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (level != null && state.getBlock() instanceof com.golemlogix.blocks.RemovalAware r) {
            r.onRemoved(state, level, pos);
        }
    }
}
