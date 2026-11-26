package com.golemlogix.blockentity;

import com.golemlogix.blocks.PatternProviderBlock;
import com.golemlogix.blocks.ProviderOutput;
import com.golemlogix.blocks.SaturatorPartBlock;
import com.golemlogix.data.PatternData;
import com.golemlogix.init.ModBlockEntities;
import com.golemlogix.init.ModDataComponents;
import com.golemlogix.items.FluidGhostItem;
import com.golemlogix.system.CryoSource;
import com.golemlogix.system.GolemNetwork;
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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Pattern Provider: holds up to 9 encoded punch cards, dispatches ingredients atomically into adjacent machines,
 * and accepts machine item/fluid returns straight back into the network.
 */
public class PatternProviderBlockEntity extends BlockEntity {
    public static final int SLOTS = 9;
    public static final int RETURN_SLOTS = 3;
    public static final int RETURN_TANKS = 3, RETURN_TANK_MB = 16_000;
    private static final int RETURN_RETRY = 5;

    private long nextFlush = 0;

    private final OwnedContainer container = new OwnedContainer(SLOTS, this,
            s -> s.has(ModDataComponents.PATTERN.get())) {
        @Override
        public void setChanged() {
            super.setChanged();
            patterns = null;
            if (level == null || !level.isClientSide()) {
                GolemNetwork.patternsChanged();
            }
        }
    };
    private final OwnedContainer returns = new OwnedContainer(RETURN_SLOTS, this, s -> true);
    private final FluidStack[] returnFluids = {FluidStack.EMPTY, FluidStack.EMPTY, FluidStack.EMPTY};
    /** Return tanks accept any fluid except Liquid Cryo-Source, stacking into matching tanks before empty ones. */
    private final IFluidHandler fluidReturns = new IFluidHandler() {
        @Override
        public int getTanks() {
            return RETURN_TANKS;
        }

        @Override
        public FluidStack getFluidInTank(int tank) {
            return tank >= 0 && tank < RETURN_TANKS ? returnFluids[tank] : FluidStack.EMPTY;
        }

        @Override
        public int getTankCapacity(int tank) {
            return tank >= 0 && tank < RETURN_TANKS ? RETURN_TANK_MB : 0;
        }

        @Override
        public boolean isFluidValid(int tank, FluidStack stack) {
            return tank >= 0 && tank < RETURN_TANKS && !CryoSource.is(stack);
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            if (resource.isEmpty() || CryoSource.is(resource)) {
                return 0;
            }
            int remaining = resource.getAmount();
            int inserted = 0;
            for (int pass = 0; pass < 2 && remaining > 0; pass++) {
                for (int i = 0; i < RETURN_TANKS && remaining > 0; i++) {
                    boolean match = !returnFluids[i].isEmpty()
                            && FluidStack.isSameFluidSameComponents(returnFluids[i], resource);
                    if (pass == 0 ? !match : !returnFluids[i].isEmpty()) {
                        continue;
                    }
                    int room = RETURN_TANK_MB - returnFluids[i].getAmount();
                    if (room <= 0) {
                        continue;
                    }
                    int n = Math.min(remaining, room);
                    if (action.execute()) {
                        returnFluids[i] = resource.copyWithAmount(returnFluids[i].getAmount() + n);
                    }
                    inserted += n;
                    remaining -= n;
                }
            }
            if (inserted > 0 && action.execute()) {
                setChanged();
            }
            return inserted;
        }

        @Override
        public FluidStack drain(FluidStack resource, FluidAction action) {
            return FluidStack.EMPTY;
        }

        @Override
        public FluidStack drain(int maxDrain, FluidAction action) {
            return FluidStack.EMPTY;
        }
    };
    private final IItemHandler automation = new IItemHandler() {
        @Override
        public int getSlots() {
            return RETURN_SLOTS;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return slot >= 0 && slot < RETURN_SLOTS ? returns.getItem(slot) : ItemStack.EMPTY;
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (slot < 0 || slot >= RETURN_SLOTS || stack.isEmpty()) {
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
            return slot >= 0 && slot < RETURN_SLOTS ? 64 : 0;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return slot >= 0 && slot < RETURN_SLOTS;
        }
    };
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

    public IFluidHandler fluidReturns() {
        return fluidReturns;
    }

    public FluidStack returnFluid(int i) {
        return returnFluids[i];
    }

    public IItemHandler automation() {
        return automation;
    }

    public boolean isBlocking() {
        return blocking;
    }

    public void setBlocking(boolean blocking) {
        this.blocking = blocking;
        setChanged();
    }

    public ProviderOutput output() {
        BlockState s = getBlockState();
        return s.hasProperty(PatternProviderBlock.OUTPUT) ? s.getValue(PatternProviderBlock.OUTPUT) : ProviderOutput.ALL;
    }

    public void setOutput(ProviderOutput out) {
        if (level != null && !level.isClientSide() && getBlockState().hasProperty(PatternProviderBlock.OUTPUT) && out != output()) {
            level.setBlock(worldPosition, getBlockState().setValue(PatternProviderBlock.OUTPUT, out), 3);
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
                    setOutput(ProviderOutput.byId(value));
                }
            }

            @Override
            public int getCount() {
                return 3;
            }
        };
    }

    public boolean redstoneAllows(boolean consume) {
        if (level == null) {
            return true;
        }
        boolean installed = upgrades.has(UpgradeType.REDSTONE);
        return redstone.allows(installed, installed && level.hasNeighborSignal(worldPosition), consume);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, PatternProviderBlockEntity be) {
        BlockEntities.pollRedstone(level, pos, be.upgrades.has(UpgradeType.REDSTONE), be.redstone);
        long now = level.getGameTime();
        if (now >= be.nextFlush && !be.flushReturns(level, pos)) {
            be.nextFlush = now + RETURN_RETRY;
        }
    }

    private boolean hasPendingReturns() {
        if (!returns.isEmpty()) {
            return true;
        }
        for (int i = 0; i < RETURN_TANKS; i++) {
            if (!returnFluids[i].isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private boolean flushReturns(Level level, BlockPos pos) {
        if (!hasPendingReturns()) {
            return true;
        }
        GolemNetwork net = GolemNetwork.get(level, pos);
        if (!net.isOnline() || !net.isDeviceActive(pos)) {
            return false;
        }
        boolean moved = false;
        for (int i = 0; i < RETURN_SLOTS; i++) {
            ItemStack in = returns.getItem(i);
            if (in.isEmpty()) {
                continue;
            }
            ItemStack leftover = net.insertFromMachine(in.copy());
            if (leftover.getCount() != in.getCount()) {
                returns.setItem(i, leftover);
                moved = true;
            }
        }
        for (int i = 0; i < RETURN_TANKS; i++) {
            FluidStack f = returnFluids[i];
            if (!f.isEmpty()) {
                int n = net.insertFluidFromMachine(f.copy(), null);
                if (n > 0) {
                    returnFluids[i] = f.copyWithAmount(Math.max(0, f.getAmount() - n));
                    setChanged();
                    moved = true;
                }
            }
        }
        return moved;
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

    private static boolean isMachineBlock(Block block) {
        // never push into network devices, except assembled Mana Saturator parts
        return !(block instanceof GolemNode) || block instanceof SaturatorPartBlock;
    }

    /** Whether a valid target machine stands on a configured output side. */
    public boolean hasMachine() {
        if (level == null) {
            return false;
        }
        for (Direction dir : BlockEntities.directions(output().direction())) {
            BlockPos target = worldPosition.relative(dir);
            if (!level.isLoaded(target) || !isMachineBlock(level.getBlockState(target).getBlock())) {
                continue;
            }
            if (level.getCapability(Capabilities.ItemHandler.BLOCK, target, dir.getOpposite()) != null
                    || level.getCapability(Capabilities.FluidHandler.BLOCK, target, dir.getOpposite()) != null) {
                return true;
            }
        }
        return false;
    }

    @Nullable
    public List<ItemStack> tryPush(PatternData pattern) {
        return tryPush(pattern, pattern.inputs());
    }

    /** Unloads items and fluids for one execution into an adjacent machine (round-robin across enabled sides). */
    public List<ItemStack> tryPush(PatternData pattern, List<ItemStack> stacks) {
        if (level == null || level.isClientSide()) {
            return null;
        }
        List<ItemStack> inputs = new ArrayList<>();
        List<FluidStack> fluids = new ArrayList<>();
        for (ItemStack s : stacks) {
            if (s.isEmpty()) {
                continue;
            }
            FluidStack f = FluidGhostItem.fluid(s);
            if (f.isEmpty()) {
                inputs.add(s);
            } else {
                mergeFluid(fluids, f);
            }
        }

        Direction[] dirs = BlockEntities.directions(output().direction());
        for (int k = 0; k < dirs.length; k++) {
            Direction dir = dirs[(lastDirection + 1 + k) % dirs.length];
            BlockPos target = worldPosition.relative(dir);
            if (!level.isLoaded(target) || !isMachineBlock(level.getBlockState(target).getBlock())) {
                continue;
            }
            IItemHandler handler = inputs.isEmpty() ? null
                    : level.getCapability(Capabilities.ItemHandler.BLOCK, target, dir.getOpposite());
            if (!inputs.isEmpty() && (handler == null || !canAcceptAll(handler, inputs))) {
                continue;
            }
            IFluidHandler tanks = fluids.isEmpty() ? null
                    : level.getCapability(Capabilities.FluidHandler.BLOCK, target, dir.getOpposite());
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
            for (FluidStack f : fluids) {
                int filled = tanks.fill(f.copy(), IFluidHandler.FluidAction.EXECUTE);
                if (filled < f.getAmount()) {
                    rejected.add(FluidGhostItem.of(f.copyWithAmount(f.getAmount() - filled)));
                }
            }
            lastDirection = (lastDirection + 1 + k) % dirs.length;
            return rejected;
        }
        return null;
    }

    /** Every distinct fluid fits together: each passes fill(SIMULATE) and has its own tank capacity (not sharing one empty tank). */
    private static boolean canAcceptFluids(IFluidHandler tanks, List<FluidStack> fluids) {
        for (FluidStack f : fluids) {
            if (tanks.fill(f.copy(), IFluidHandler.FluidAction.SIMULATE) < f.getAmount()) {
                return false;
            }
        }
        int tankCount = tanks.getTanks();
        if (fluids.size() <= 1 || tankCount <= 0) {
            return true;
        }
        boolean[] usedEmpty = new boolean[tankCount];
        for (int idx = 0; idx < fluids.size(); idx++) {
            FluidStack f = fluids.get(idx);
            long room = 0;
            for (int t = 0; t < tankCount; t++) {
                FluidStack cur = tanks.getFluidInTank(t);
                if (!cur.isEmpty() && FluidStack.isSameFluidSameComponents(cur, f)) {
                    room += Math.max(0, tanks.getTankCapacity(t) - cur.getAmount());
                }
            }
            // Prefer empty tanks specific to this fluid before claiming generic ones
            for (int pass = 0; pass < 2 && room < f.getAmount(); pass++) {
                for (int t = 0; t < tankCount && room < f.getAmount(); t++) {
                    if (usedEmpty[t] || !tanks.getFluidInTank(t).isEmpty() || !tanks.isFluidValid(t, f)) {
                        continue;
                    }
                    int cap = tanks.getTankCapacity(t);
                    if (cap <= 0) {
                        continue;
                    }
                    if (pass == 0) {
                        boolean acceptsOther = false;
                        for (int j = idx + 1; j < fluids.size(); j++) {
                            if (tanks.isFluidValid(t, fluids.get(j))) {
                                acceptsOther = true;
                                break;
                            }
                        }
                        if (acceptsOther) {
                            continue;
                        }
                    }
                    usedEmpty[t] = true;
                    room += cap;
                }
            }
            if (room < f.getAmount()) {
                return false;
            }
        }
        return true;
    }

    private static void mergeFluid(List<FluidStack> list, FluidStack f) {
        for (int i = 0; i < list.size(); i++) {
            if (FluidStack.isSameFluidSameComponents(list.get(i), f)) {
                list.set(i, list.get(i).copyWithAmount(list.get(i).getAmount() + f.getAmount()));
                return;
            }
        }
        list.add(f.copy());
    }

    /** All ingredients fit together across the handler's slots (tracks virtual slot occupancy across multiple stacks). */
    private static boolean canAcceptAll(IItemHandler handler, List<ItemStack> inputs) {
        if (inputs.size() == 1) {
            return ItemHandlerHelper.insertItem(handler, inputs.get(0).copy(), true).isEmpty();
        }
        int slots = handler.getSlots();
        if (slots <= 0) {
            return false;
        }
        ItemStack[] claimed = new ItemStack[slots];
        int[] room = new int[slots];
        for (ItemStack in : inputs) {
            int left = in.getCount();
            for (int s = 0; s < slots && left > 0; s++) {
                if (claimed[s] != null) {
                    if (ItemStack.isSameItemSameComponents(claimed[s], in) && room[s] > 0) {
                        int put = Math.min(left, room[s]);
                        room[s] -= put;
                        left -= put;
                    }
                    continue;
                }
                int slotLimit = handler.getSlotLimit(s);
                if (slotLimit <= 0) {
                    continue;
                }
                int probeCap = Math.min(slotLimit, Math.max(left, in.getMaxStackSize()));
                ItemStack rest = handler.insertItem(s, in.copyWithCount(probeCap), true);
                int free = probeCap - rest.getCount();
                if (free <= 0) {
                    continue;
                }
                int put = Math.min(left, free);
                claimed[s] = in;
                room[s] = free - put;
                left -= put;
            }
            if (left > 0) {
                return false;
            }
        }
        return true;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("Patterns", container.createTag(registries));
        tag.put("Returns", returns.createTag(registries));
        BlockEntities.saveFluidSlots(tag, "ReturnFluids", returnFluids, registries);
        tag.put("Upgrades", upgrades.createTag(registries));
        tag.putBoolean("Blocking", blocking);
        redstone.save(tag);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        container.fromTag(tag.getList("Patterns", Tag.TAG_COMPOUND), registries);
        returns.fromTag(tag.getList("Returns", Tag.TAG_COMPOUND), registries);
        BlockEntities.loadFluidSlots(tag, "ReturnFluids", returnFluids, registries);
        upgrades.fromTag(tag.getList("Upgrades", Tag.TAG_COMPOUND), registries);
        blocking = tag.getBoolean("Blocking");
        redstone.load(tag);
        patterns = null;
    }
}
