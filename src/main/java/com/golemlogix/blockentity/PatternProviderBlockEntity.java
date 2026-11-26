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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.TransferPreconditions;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.fluid.FluidStacksResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.VanillaContainerWrapper;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

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
    /** Return tanks accept any fluid except Liquid Cryo-Source, stacking into matching tanks before empty ones. */
    private final FluidStacksResourceHandler returnFluids = new FluidStacksResourceHandler(RETURN_TANKS, RETURN_TANK_MB) {
        @Override
        public boolean isValid(int index, FluidResource resource) {
            return !CryoSource.is(resource.toStack(1));
        }

        @Override
        public int insert(FluidResource resource, int amount, TransactionContext tx) {
            TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
            int inserted = 0;
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
    private final ResourceHandler<ItemResource> itemResources = Transfers.insertOnly(VanillaContainerWrapper.of(returns));
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

    public ResourceHandler<FluidResource> fluidResources() {
        return fluidResources;
    }

    public FluidStack returnFluid(int i) {
        return Transfers.getFluid(returnFluids, i);
    }

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
            if (returnFluids.getAmountAsInt(i) > 0) {
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
            if (level.getCapability(Capabilities.Item.BLOCK, target, dir.getOpposite()) != null
                    || level.getCapability(Capabilities.Fluid.BLOCK, target, dir.getOpposite()) != null) {
                return true;
            }
        }
        return false;
    }

    @Nullable
    public List<ItemStack> tryPush(PatternData pattern) {
        return tryPush(pattern, pattern.inputs());
    }

    /** Atomically inserts items and fluids for one execution into an adjacent machine (round-robin across enabled sides). */
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
            ResourceHandler<ItemResource> handler = inputs.isEmpty() ? null
                    : level.getCapability(Capabilities.Item.BLOCK, target, dir.getOpposite());
            if (!inputs.isEmpty() && handler == null) {
                continue;
            }
            ResourceHandler<FluidResource> tanks = fluids.isEmpty() ? null
                    : level.getCapability(Capabilities.Fluid.BLOCK, target, dir.getOpposite());
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
        BlockEntities.saveFluidSlots(tag, "ReturnFluids", returnFluids, registries);
        tag.put("Upgrades", upgrades.createTag(registries));
        tag.putBoolean("Blocking", blocking);
        redstone.save(tag);
    }

    protected void loadTag(CompoundTag tag, HolderLookup.Provider registries) {
        container.fromTag(tag.getListOrEmpty("Patterns"), registries);
        returns.fromTag(tag.getListOrEmpty("Returns"), registries);
        BlockEntities.loadFluidSlots(tag, "ReturnFluids", returnFluids, registries);
        upgrades.fromTag(tag.getListOrEmpty("Upgrades"), registries);
        blocking = tag.getBooleanOr("Blocking", false);
        redstone.load(tag);
        patterns = null;
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        BlockEntities.save(this, output, this::saveTag);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        BlockEntities.load(input, this::loadTag);
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        BlockEntities.onPreRemove(this, pos, state);
    }
}
