package com.golemlogix.blockentity;

import com.golemlogix.data.PatternData;
import com.golemlogix.init.ModBlockEntities;
import com.golemlogix.init.ModDataComponents;
import com.golemlogix.system.GolemNode;
import com.golemlogix.upgrade.RedstoneControl;
import com.golemlogix.upgrade.RedstoneMode;
import com.golemlogix.upgrade.UpgradeContainer;
import com.golemlogix.upgrade.UpgradeSpec;
import com.golemlogix.upgrade.UpgradeType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Pattern Provider: holds up to 9 encoded punch cards and unloads ingredients
 * into adjacent machines (any neighbour that is not a network node).
 * Wait-for-result mode: the next portion is not loaded until the job has received the output of the previous one.
 * A Redstone Module stops feeding on a signal (or feeds one portion per pulse).
 * Return slots: what the machines hand back (their auto-output puts results here first) or a player puts in goes into the
 * network, so a machine fed by a provider needs no Import Bus and no channel of its own.
 */
public class PatternProviderBlockEntity extends BlockEntity {
    public static final int SLOTS = 9;
    public static final int RETURN_SLOTS = 3;
    /** How often the return slots are emptied into the network (ticks). */
    private static final int RETURN_INTERVAL = 5;

    private final OwnedContainer container = new OwnedContainer(SLOTS, this,
            s -> s.has(ModDataComponents.PATTERN.get())) {
        @Override
        public void setChanged() {
            super.setChanged();
            patterns = null;
            com.golemlogix.system.GolemNetwork.patternsChanged();
        }
    };
    private final OwnedContainer returns = new OwnedContainer(RETURN_SLOTS, this, s -> true);
    private final UpgradeContainer upgrades = new UpgradeContainer(UpgradeSpec.REDSTONE_ONLY, this);
    private final RedstoneControl redstone = new RedstoneControl();

    private List<PatternData> patterns;
    private int lastDirection = 0;
    private boolean blocking = false;

    public PatternProviderBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.PATTERN_PROVIDER.get(), pos, state);
    }

    public OwnedContainer getContainer() {
        return container;
    }

    public UpgradeContainer getUpgrades() {
        return upgrades;
    }

    public OwnedContainer getReturns() {
        return returns;
    }

    /**
     * Automation access from any side: only the return slots, and only to put in (anything; it goes into the network). The
     * punch cards are not reachable, and nothing can be taken back out, otherwise a hopper under the provider would pull it.
     */
    private final IItemHandler automation = new IItemHandler() {
        @Override
        public int getSlots() {
            return RETURN_SLOTS;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return returns.getItem(slot);
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (stack.isEmpty()) {
                return stack;
            }
            ItemStack cur = returns.getItem(slot);
            if (!cur.isEmpty() && !ItemStack.isSameItemSameComponents(cur, stack)) {
                return stack;
            }
            int room = Math.min(stack.getMaxStackSize(), 64) - cur.getCount();
            if (room <= 0) {
                return stack;
            }
            int n = Math.min(room, stack.getCount());
            if (!simulate) {
                if (cur.isEmpty()) {
                    returns.setItem(slot, stack.copyWithCount(n));
                } else {
                    cur.grow(n);
                    returns.setChanged();
                }
            }
            return n == stack.getCount() ? ItemStack.EMPTY : stack.copyWithCount(stack.getCount() - n);
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            return ItemStack.EMPTY;
        }

        @Override
        public int getSlotLimit(int slot) {
            return 64;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return true;
        }
    };

    private final net.neoforged.neoforge.transfer.ResourceHandler<net.neoforged.neoforge.transfer.item.ItemResource> itemResources =
            com.golemlogix.system.LegacyResources.offerItems(automation, () -> {
                ItemStack[] copy = new ItemStack[RETURN_SLOTS];
                for (int i = 0; i < RETURN_SLOTS; i++) {
                    copy[i] = returns.getItem(i).copy();
                }
                return copy;
            }, copy -> {
                for (int i = 0; i < RETURN_SLOTS; i++) {
                    returns.setItem(i, copy[i]);
                }
            });

    /** The return slots for pipes, hoppers and the machines' auto-output. */
    public net.neoforged.neoforge.transfer.ResourceHandler<net.neoforged.neoforge.transfer.item.ItemResource> itemResources() {
        return itemResources;
    }

    public boolean isBlocking() {
        return blocking;
    }

    public void setBlocking(boolean blocking) {
        this.blocking = blocking;
        setChanged();
    }

    /** Where it feeds ingredients (a block property: the arrows on the faces follow it too). */
    public com.golemlogix.blocks.ProviderOutput output() {
        BlockState s = getBlockState();
        return s.hasProperty(com.golemlogix.blocks.PatternProviderBlock.OUTPUT)
                ? s.getValue(com.golemlogix.blocks.PatternProviderBlock.OUTPUT) : com.golemlogix.blocks.ProviderOutput.ALL;
    }

    public void setOutput(com.golemlogix.blocks.ProviderOutput out) {
        if (level != null && !level.isClientSide() && getBlockState().hasProperty(com.golemlogix.blocks.PatternProviderBlock.OUTPUT)) {
            level.setBlock(worldPosition, getBlockState().setValue(com.golemlogix.blocks.PatternProviderBlock.OUTPUT, out), 3);
        }
    }

    /** Window data: 0 = wait mode (0/1), 1 = redstone mode, 2 = feeding side (ProviderOutput). */
    public ContainerData menuData() {
        return new ContainerData() {
            @Override
            public int get(int index) {
                return switch (index) {
                    case 0 -> blocking ? 1 : 0;
                    case 1 -> redstone.mode().ordinal();
                    default -> output().ordinal();
                };
            }

            @Override
            public void set(int index, int value) {
                if (index == 0) {
                    setBlocking(value != 0);
                } else if (index == 1) {
                    redstone.setMode(RedstoneMode.byId(value));
                    setChanged();
                } else {
                    setOutput(com.golemlogix.blocks.ProviderOutput.byId(value));
                }
            }

            @Override
            public int getCount() {
                return 3;
            }
        };
    }

    /** Whether redstone allows unloading a portion; consume = use up the pulse (the portion was really unloaded). */
    public boolean redstoneAllows(boolean consume) {
        if (level == null) {
            return true;
        }
        boolean installed = upgrades.has(UpgradeType.REDSTONE);
        return redstone.allows(installed, installed && level.hasNeighborSignal(worldPosition), consume);
    }

    /** Remembers the signal level every tick (a short pulse between calculator cycles is not lost) and empties the return slots. */
    public static void serverTick(Level level, BlockPos pos, BlockState state, PatternProviderBlockEntity be) {
        if (be.upgrades.has(UpgradeType.REDSTONE)) {
            be.redstone.observe(level.hasNeighborSignal(pos));
        }
        if (Math.floorMod(level.getGameTime() + pos.asLong(), RETURN_INTERVAL) == 0) {
            be.flushReturns(level, pos);
        }
    }

    /** Return slots -> network: first to the jobs waiting for these items, the rest into storage; what does not fit stays. */
    private void flushReturns(Level level, BlockPos pos) {
        boolean any = false;
        for (int i = 0; i < RETURN_SLOTS && !any; i++) {
            any = !returns.getItem(i).isEmpty();
        }
        if (!any) {
            return;
        }
        com.golemlogix.system.GolemNetwork net = com.golemlogix.system.GolemNetwork.get(level, pos);
        if (!net.isOnline() || !net.isDeviceActive(pos)) {
            return;
        }
        for (int i = 0; i < RETURN_SLOTS; i++) {
            ItemStack in = returns.getItem(i);
            if (in.isEmpty()) {
                continue;
            }
            ItemStack leftover = net.insertFromMachine(in.copy());
            if (leftover.getCount() != in.getCount()) {
                returns.setItem(i, leftover);
            }
        }
    }

    public List<PatternData> getPatterns() {
        if (patterns == null) {
            List<PatternData> list = new ArrayList<>();
            for (int i = 0; i < SLOTS; i++) {
                PatternData data = container.getItem(i).get(ModDataComponents.PATTERN.get());
                if (data != null && data.isValid()) {
                    list.add(data);
                }
            }
            patterns = list;
        }
        return patterns;
    }

    public boolean hasPattern(PatternData pattern) {
        return getPatterns().contains(pattern);
    }

    /** Whether a machine stands on a side it feeds (a block with an item or fluid inventory that is not a network device). */
    public boolean hasMachine() {
        if (level == null) {
            return false;
        }
        Direction only = output().direction();
        for (Direction dir : only == null ? Direction.values() : new Direction[]{only}) {
            BlockPos target = worldPosition.relative(dir);
            if (!level.isLoaded(target)) {
                continue;
            }
            net.minecraft.world.level.block.Block block = level.getBlockState(target).getBlock();
            if (block instanceof GolemNode && !(block instanceof com.golemlogix.blocks.SaturatorPartBlock)) {
                continue;
            }
            if (com.golemlogix.system.LegacyResources.items(level, target, dir.getOpposite()) != null
                    || com.golemlogix.system.LegacyResources.fluids(level, target, dir.getOpposite()) != null) {
                return true;
            }
        }
        return false;
    }

    /**
     * Tries to unload the ingredients of one pattern execution into a neighbouring machine.
     * Returns the list of what could not be accepted (usually empty), or null if the machine is not ready.
     */
    @Nullable
    public List<ItemStack> tryPush(PatternData pattern) {
        return tryPush(pattern, pattern.inputs());
    }

    /** The same with the items of this very execution (a fuzzy pattern sends what stands for the encoded items). */
    public List<ItemStack> tryPush(PatternData pattern, List<ItemStack> stacks) {
        if (level == null || level.isClientSide()) {
            return null;
        }
        List<ItemStack> inputs = new ArrayList<>();
        List<net.neoforged.neoforge.fluids.FluidStack> fluids = new ArrayList<>();
        for (ItemStack s : stacks) {
            if (s.isEmpty()) {
                continue;
            }
            net.neoforged.neoforge.fluids.FluidStack f = com.golemlogix.items.FluidGhostItem.fluid(s);
            if (f.isEmpty()) {
                inputs.add(s);
            } else {
                mergeFluid(fluids, f);
            }
        }

        // a side is selected: only into it (when machines stand side by side you can see which one the provider feeds)
        Direction only = output().direction();
        Direction[] dirs = only == null ? Direction.values() : new Direction[]{only};
        for (int k = 0; k < dirs.length; k++) {
            Direction dir = dirs[(lastDirection + 1 + k) % dirs.length];
            BlockPos target = worldPosition.relative(dir);
            // never unload into network devices; the exception is an assembled Mana Saturator (its parts are network nodes too)
            net.minecraft.world.level.block.Block block = level.isLoaded(target) ? level.getBlockState(target).getBlock() : null;
            if (block == null || (block instanceof GolemNode && !(block instanceof com.golemlogix.blocks.SaturatorPartBlock))) {
                continue;
            }
            // items and fluids go into the same machine side, and only if it takes all of them
            IItemHandler handler = inputs.isEmpty() ? null : com.golemlogix.system.LegacyResources.items(level, target, dir.getOpposite());
            if (!inputs.isEmpty() && (handler == null || !canAcceptAll(handler, inputs))) {
                continue;
            }
            net.neoforged.neoforge.fluids.capability.IFluidHandler tanks = fluids.isEmpty() ? null
                    : com.golemlogix.system.LegacyResources.fluids(level, target, dir.getOpposite());
            if (!fluids.isEmpty() && (tanks == null || !canAcceptFluids(tanks, fluids))) {
                continue;
            }
            List<ItemStack> rejected = new ArrayList<>();
            for (ItemStack in : inputs) {
                ItemStack rest = ItemHandlerHelper.insertItem(handler, in.copy(), false);
                if (!rest.isEmpty()) {
                    rejected.add(rest);
                }
            }
            for (net.neoforged.neoforge.fluids.FluidStack f : fluids) {
                int filled = tanks.fill(f.copy(), net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE);
                if (filled < f.getAmount()) {
                    rejected.add(com.golemlogix.items.FluidGhostItem.of(f.copyWithAmount(f.getAmount() - filled)));
                }
            }
            lastDirection = (lastDirection + 1 + k) % dirs.length;
            return rejected;
        }
        return null;
    }

    /** Every fluid fits whole (each checked on its own by simulation). */
    private static boolean canAcceptFluids(net.neoforged.neoforge.fluids.capability.IFluidHandler tanks,
                                           List<net.neoforged.neoforge.fluids.FluidStack> fluids) {
        for (net.neoforged.neoforge.fluids.FluidStack f : fluids) {
            if (tanks.fill(f.copy(), net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.SIMULATE) < f.getAmount()) {
                return false;
            }
        }
        return true;
    }

    private static void mergeFluid(List<net.neoforged.neoforge.fluids.FluidStack> list, net.neoforged.neoforge.fluids.FluidStack f) {
        for (int i = 0; i < list.size(); i++) {
            if (net.neoforged.neoforge.fluids.FluidStack.isSameFluidSameComponents(list.get(i), f)) {
                list.set(i, list.get(i).copyWithAmount(list.get(i).getAmount() + f.getAmount()));
                return;
            }
        }
        list.add(f.copy());
    }

    /** All ingredients will fit (each checked on its own, then a joint check on the real slots). */
    private static boolean canAcceptAll(IItemHandler handler, List<ItemStack> inputs) {
        // check every item by simulation; to be safe, identical ones are summed
        for (ItemStack in : inputs) {
            ItemStack rest = ItemHandlerHelper.insertItem(handler, in.copy(), true);
            if (!rest.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    protected void saveTag(CompoundTag tag, HolderLookup.Provider registries) {
        tag.put("Patterns", container.createTag(registries));
        tag.put("Returns", returns.createTag(registries));
        tag.put("Upgrades", upgrades.createTag(registries));
        tag.putBoolean("Blocking", blocking);
        redstone.save(tag);
    }

    protected void loadTag(CompoundTag tag, HolderLookup.Provider registries) {
        container.fromTag(tag.getListOrEmpty("Patterns"), registries);
        returns.fromTag(tag.getListOrEmpty("Returns"), registries);
        upgrades.fromTag(tag.getListOrEmpty("Upgrades"), registries);
        blocking = tag.getBooleanOr("Blocking", false);
        redstone.load(tag);
        patterns = null;
    }

    // ---- 1.21.6+: the game's ValueInput/ValueOutput over the CompoundTag code above

    @Override
    protected void saveAdditional(net.minecraft.world.level.storage.ValueOutput output) {
        super.saveAdditional(output);
        CompoundTag tag = new CompoundTag();
        saveTag(tag, com.golemlogix.system.NbtBridge.registries(this));
        com.golemlogix.system.NbtBridge.write(output, tag);
    }

    @Override
    protected void loadAdditional(net.minecraft.world.level.storage.ValueInput input) {
        super.loadAdditional(input);
        loadTag(com.golemlogix.system.NbtBridge.read(input), input.lookup());
    }

    /** Since 1.21.5 the block's removal side effects run from here, while this entity and its contents are still in place. */
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (level != null && state.getBlock() instanceof com.golemlogix.blocks.RemovalAware r) {
            r.onRemoved(state, level, pos);
        }
    }
}
