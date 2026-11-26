package com.golemlogix.blockentity;

import com.golemlogix.data.PatternData;
import com.golemlogix.init.ModBlockEntities;
import com.golemlogix.init.ModDataComponents;
import com.golemlogix.system.GolemNode;
import com.golemlogix.system.NbtBridge;
import com.golemlogix.system.Transfers;
import com.golemlogix.upgrade.RedstoneControl;
import com.golemlogix.upgrade.RedstoneMode;
import com.golemlogix.upgrade.UpgradeContainer;
import com.golemlogix.upgrade.UpgradeSpec;
import com.golemlogix.upgrade.UpgradeType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.fluid.FluidStacksResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.VanillaContainerWrapper;
import net.neoforged.neoforge.transfer.transaction.Transaction;

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
    /** Return tanks for fluids (a machine's fluid result), each this many mB. */
    public static final int RETURN_TANKS = 3, RETURN_TANK_MB = 16_000;
    /** When the network took nothing (it is full or offline), how long before the return slots try again (ticks). */
    private static final int RETURN_RETRY = 5;
    private static final Direction[] ALL_DIRECTIONS = Direction.values();
    private long nextFlush = 0;

    private final OwnedContainer container = new OwnedContainer(SLOTS, this,
            s -> s.has(ModDataComponents.PATTERN.get())) {
        @Override
        public void setChanged() {
            super.setChanged();
            patterns = null;
            if (level == null || !level.isClientSide()) {
                com.golemlogix.system.GolemNetwork.patternsChanged();
            }
        }
    };
    private final OwnedContainer returns = new OwnedContainer(RETURN_SLOTS, this, s -> true);
    /** The return tanks from any side: anything goes in (but Liquid Cryo-Source, which is never stored), nothing comes out. */
    private final FluidStacksResourceHandler returnFluids = new FluidStacksResourceHandler(RETURN_TANKS, RETURN_TANK_MB) {
        @Override
        public boolean isValid(int index, FluidResource resource) {
            return !com.golemlogix.system.CryoSource.is(resource.toStack(1));
        }

        @Override
        public int insert(FluidResource resource, int amount, net.neoforged.neoforge.transfer.transaction.TransactionContext tx) {
            net.neoforged.neoforge.transfer.TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
            int inserted = 0;
            // stack into a tank already holding this fluid first, then into empty tanks
            for (int pass = 0; pass < 2 && inserted < amount; pass++) {
                for (int i = 0; i < size() && inserted < amount; i++) {
                    if ((pass == 0) == (getAmountAsInt(i) > 0 && getResource(i).equals(resource))) {
                        inserted += insert(i, resource, amount - inserted, tx);
                    }
                }
            }
            return inserted;
        }

        @Override
        protected void onContentsChanged(int index, FluidStack previousContents) {
            setChanged();
        }
    };
    private final ResourceHandler<ItemResource> itemResources =
            Transfers.insertOnly(VanillaContainerWrapper.of(returns));
    private final ResourceHandler<FluidResource> fluidResources = Transfers.insertOnly(returnFluids);
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

    /** The return tanks for pipes and machines that push out fluids. */
    public ResourceHandler<FluidResource> fluidResources() {
        return fluidResources;
    }

    /** What the return tanks hold now (the window shows it). */
    public FluidStack returnFluid(int i) {
        return Transfers.getFluid(returnFluids, i);
    }

    /** The return slots for pipes, hoppers and the machines' auto-output. */
    public ResourceHandler<ItemResource> itemResources() {
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
        if (level != null && !level.isClientSide() && getBlockState().hasProperty(com.golemlogix.blocks.PatternProviderBlock.OUTPUT)
                && out != output()) {
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
        // what comes back goes on at once: a machine must not wait for the network
        long now = level.getGameTime();
        if (now >= be.nextFlush && !be.flushReturns(level, pos)) {
            be.nextFlush = now + RETURN_RETRY;
        }
    }

    /**
     * Return slots and tanks -> network: first to the jobs waiting for these, the rest into storage; what does not fit stays.
     * False when there was something to hand over and the network took none of it.
     */
    private boolean flushReturns(Level level, BlockPos pos) {
        boolean any = false;
        for (int i = 0; i < RETURN_SLOTS && !any; i++) {
            any = !returns.getItem(i).isEmpty();
        }
        for (int i = 0; i < RETURN_TANKS && !any; i++) {
            any = returnFluids.getAmountAsInt(i) > 0;
        }
        if (!any) {
            return true;
        }
        com.golemlogix.system.GolemNetwork net = com.golemlogix.system.GolemNetwork.get(level, pos);
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
            FluidStack f = returnFluid(i);
            if (!f.isEmpty()) {
                int n = net.insertFluidFromMachine(f.copy(), null);
                if (n > 0) {
                    int left = Math.max(0, f.getAmount() - n);
                    returnFluids.set(i, left == 0 ? FluidResource.EMPTY : FluidResource.of(f), left);
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

    /** Whether a machine stands on a side it feeds (a block with an item or fluid inventory that is not a network device). */
    public boolean hasMachine() {
        if (level == null) {
            return false;
        }
        Direction only = output().direction();
        for (Direction dir : only == null ? ALL_DIRECTIONS : new Direction[]{only}) {
            BlockPos target = worldPosition.relative(dir);
            if (!level.isLoaded(target)) {
                continue;
            }
            net.minecraft.world.level.block.Block block = level.getBlockState(target).getBlock();
            if (block instanceof GolemNode && !(block instanceof com.golemlogix.blocks.SaturatorPartBlock)) {
                continue;
            }
            if (level.getCapability(Capabilities.Item.BLOCK, target, dir.getOpposite()) != null
                    || level.getCapability(Capabilities.Fluid.BLOCK, target, dir.getOpposite()) != null) {
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
        List<FluidStack> fluids = new ArrayList<>();
        for (ItemStack s : stacks) {
            if (s.isEmpty()) {
                continue;
            }
            FluidStack f = com.golemlogix.items.FluidGhostItem.fluid(s);
            if (f.isEmpty()) {
                inputs.add(s);
            } else {
                mergeFluid(fluids, f);
            }
        }

        // a side is selected: only into it (when machines stand side by side you can see which one the provider feeds)
        Direction only = output().direction();
        Direction[] dirs = only == null ? ALL_DIRECTIONS : new Direction[]{only};
        for (int k = 0; k < dirs.length; k++) {
            Direction dir = dirs[(lastDirection + 1 + k) % dirs.length];
            BlockPos target = worldPosition.relative(dir);
            // never unload into network devices; the exception is an assembled Mana Saturator (its parts are network nodes too)
            net.minecraft.world.level.block.Block block = level.isLoaded(target) ? level.getBlockState(target).getBlock() : null;
            if (block == null || (block instanceof GolemNode && !(block instanceof com.golemlogix.blocks.SaturatorPartBlock))) {
                continue;
            }
            // items and fluids go into the same machine side atomically in one transaction: only if it takes all of them
            ResourceHandler<ItemResource> handler = inputs.isEmpty() ? null : level.getCapability(Capabilities.Item.BLOCK, target, dir.getOpposite());
            if (!inputs.isEmpty() && handler == null) {
                continue;
            }
            ResourceHandler<FluidResource> tanks = fluids.isEmpty() ? null : level.getCapability(Capabilities.Fluid.BLOCK, target, dir.getOpposite());
            if (!fluids.isEmpty() && tanks == null) {
                continue;
            }
            try (Transaction tx = Transaction.openRoot()) {
                boolean ok = true;
                for (ItemStack in : inputs) {
                    if (handler.insert(ItemResource.of(in), in.getCount(), tx) < in.getCount()) {
                        ok = false;
                        break;
                    }
                }
                if (ok) {
                    for (FluidStack f : fluids) {
                        if (tanks.insert(FluidResource.of(f), f.getAmount(), tx) < f.getAmount()) {
                            ok = false;
                            break;
                        }
                    }
                }
                if (ok) {
                    tx.commit();
                    lastDirection = (lastDirection + 1 + k) % dirs.length;
                    return List.of();
                }
            }
        }
        return null;
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

    protected void saveTag(CompoundTag tag, HolderLookup.Provider registries) {
        tag.put("Patterns", container.createTag(registries));
        tag.put("Returns", returns.createTag(registries));
        net.minecraft.nbt.ListTag fl = new net.minecraft.nbt.ListTag();
        for (int i = 0; i < RETURN_TANKS; i++) {
            FluidStack f = returnFluid(i);
            if (!f.isEmpty()) {
                CompoundTag e = (CompoundTag) NbtBridge.saveFluid(f, registries);
                e.putByte("Slot", (byte) i);
                fl.add(e);
            }
        }
        tag.put("ReturnFluids", fl);
        tag.put("Upgrades", upgrades.createTag(registries));
        tag.putBoolean("Blocking", blocking);
        redstone.save(tag);
    }

    protected void loadTag(CompoundTag tag, HolderLookup.Provider registries) {
        container.fromTag(tag.getListOrEmpty("Patterns"), registries);
        returns.fromTag(tag.getListOrEmpty("Returns"), registries);
        for (int i = 0; i < RETURN_TANKS; i++) {
            returnFluids.set(i, FluidResource.EMPTY, 0);
        }
        net.minecraft.nbt.ListTag fl = tag.getListOrEmpty("ReturnFluids");
        for (int k = 0; k < fl.size(); k++) {
            CompoundTag e = fl.getCompoundOrEmpty(k);
            int i = e.getByteOr("Slot", (byte) 0);
            if (i >= 0 && i < RETURN_TANKS) {
                FluidStack f = NbtBridge.loadFluid(registries, e).orElse(FluidStack.EMPTY);
                returnFluids.set(i, FluidResource.of(f), f.getAmount());
            }
        }
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
