package com.golemlogix.blockentity;

import com.golemlogix.init.ModBlockEntities;
import com.golemlogix.menu.Priority;
import com.golemlogix.upgrade.RedstoneControl;
import com.golemlogix.upgrade.RedstoneMode;
import com.golemlogix.upgrade.UpgradeContainer;
import com.golemlogix.upgrade.UpgradeSpec;
import com.golemlogix.upgrade.UpgradeType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Periscope: the external storage it connects, as a storage bus of AE2 or RS - its priority, its access (both ways, insert only,
 * extract only: an insert-only storage shows nothing in the terminal), a filter of what may go in and out of it (an allow list, or
 * a deny list; fuzzy with a Fuzzy Card; a bucket stands for its fluid; empty: everything) and a Redstone Module that can switch it
 * on and off.
 */
public class PeriscopeBlockEntity extends BlockEntity {
    public static final int FILTER_SLOTS = 9;
    /** Access modes. */
    public static final int BOTH = 0, INSERT_ONLY = 1, EXTRACT_ONLY = 2;
    private int priority = 0;
    private final UpgradeContainer upgrades = new UpgradeContainer(UpgradeSpec.PERISCOPE, this);
    private final RedstoneControl redstone = new RedstoneControl();
    private final OwnedContainer filters = new OwnedContainer(FILTER_SLOTS, this, s -> true);
    private int access = BOTH;
    private boolean denyList;

    public PeriscopeBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.PERISCOPE.get(), pos, state);
    }

    public int getPriority() {
        return priority;
    }

    public void setPriority(int value) {
        this.priority = Mth.clamp(value, -Priority.MAX, Priority.MAX);
        setChanged();
        // the network remembers the storages behind periscopes with their priority
        if (level != null && !level.isClientSide()) {
            com.golemlogix.system.GolemNetwork.clearCache();
        }
    }

    public UpgradeContainer getUpgrades() {
        return upgrades;
    }

    public OwnedContainer getFilters() {
        return filters;
    }

    private static net.neoforged.neoforge.fluids.FluidStack fluidOf(net.minecraft.world.item.ItemStack ghost) {
        net.neoforged.neoforge.fluids.FluidStack f = com.golemlogix.items.FluidGhostItem.fluid(ghost);
        return f.isEmpty() ? net.neoforged.neoforge.fluids.FluidUtil.getFluidContained(ghost).orElse(net.neoforged.neoforge.fluids.FluidStack.EMPTY) : f;
    }

    /** The item passes the filter: an allow list lets only what it names through, a deny list everything else; no item in it, all. */
    public boolean passes(net.minecraft.world.item.ItemStack stack) {
        boolean fuzzy = upgrades.has(UpgradeType.FUZZY), any = false;
        for (int i = 0; i < FILTER_SLOTS; i++) {
            net.minecraft.world.item.ItemStack f = filters.getItem(i);
            if (f.isEmpty() || !fluidOf(f).isEmpty()) {
                continue;
            }
            any = true;
            if (com.golemlogix.system.Fuzzy.matches(f, stack, fuzzy)) {
                return !denyList;
            }
        }
        return !any || denyList;
    }

    /** The same for a fluid, by the buckets (fluid ghosts) in the filter. */
    public boolean passes(net.neoforged.neoforge.fluids.FluidStack fluid) {
        boolean any = false;
        for (int i = 0; i < FILTER_SLOTS; i++) {
            net.neoforged.neoforge.fluids.FluidStack f = fluidOf(filters.getItem(i));
            if (f.isEmpty()) {
                continue;
            }
            any = true;
            if (net.neoforged.neoforge.fluids.FluidStack.isSameFluid(f, fluid)) {
                return !denyList;
            }
        }
        return !any || denyList;
    }

    /** The network may put this into the storage. */
    public boolean mayInsert(net.minecraft.world.item.ItemStack stack) {
        return access != EXTRACT_ONLY && passes(stack);
    }

    /** The network sees this in the storage and may take it out. */
    public boolean mayExtract(net.minecraft.world.item.ItemStack stack) {
        return access != INSERT_ONLY && passes(stack);
    }

    public boolean mayInsert(net.neoforged.neoforge.fluids.FluidStack fluid) {
        return access != EXTRACT_ONLY && passes(fluid);
    }

    public boolean mayExtract(net.neoforged.neoforge.fluids.FluidStack fluid) {
        return access != INSERT_ONLY && passes(fluid);
    }

    /** The handlers of the storage in front (server), looked up once and kept until the block there changes. */
    public com.golemlogix.system.TargetCaps targetCaps(net.minecraft.server.level.ServerLevel level, BlockPos target, net.minecraft.core.Direction face) {
        caps = com.golemlogix.system.TargetCaps.of(caps, level, target, face, () -> !isRemoved(), () -> {
        });
        return caps;
    }

    @org.jetbrains.annotations.Nullable
    private com.golemlogix.system.TargetCaps caps;

    /** Whether the storage is connected now (with a Redstone Module, by signal; pulse mode makes no sense here). */
    public boolean storageEnabled() {
        if (level == null || !upgrades.has(UpgradeType.REDSTONE)) {
            return true;
        }
        boolean powered = level.hasNeighborSignal(worldPosition);
        return switch (redstone.mode()) {
            case HIGH -> powered;
            case LOW -> !powered;
            default -> true;
        };
    }

    /** Window data: 0 = priority, 1 = redstone mode, 2 = access (BOTH, INSERT_ONLY, EXTRACT_ONLY), 3 = deny list. */
    public ContainerData menuData() {
        return new ContainerData() {
            @Override
            public int get(int index) {
                return switch (index) {
                    case 0 -> priority;
                    case 1 -> redstone.mode().ordinal();
                    case 2 -> access;
                    default -> denyList ? 1 : 0;
                };
            }

            @Override
            public void set(int index, int value) {
                if (index == 0) {
                    setPriority(value);
                } else if (index == 1) {
                    RedstoneMode m = RedstoneMode.byId(value);
                    redstone.setMode(m == RedstoneMode.PULSE ? RedstoneMode.IGNORE : m);
                    setChanged();
                } else if (index == 2) {
                    access = Math.floorMod(value, 3);
                    setChanged();
                } else {
                    denyList = value != 0;
                    setChanged();
                }
            }

            @Override
            public int getCount() {
                return 4;
            }
        };
    }

    protected void saveTag(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("Priority", priority);
        tag.put("Upgrades", upgrades.createTag(registries));
        tag.put("Filters", filters.createTag(registries));
        tag.putInt("Access", access);
        tag.putBoolean("DenyList", denyList);
        redstone.save(tag);
    }

    protected void loadTag(CompoundTag tag, HolderLookup.Provider registries) {
        priority = tag.getIntOr("Priority", 0);
        upgrades.fromTag(tag.getListOrEmpty("Upgrades"), registries);
        filters.fromTag(tag.getListOrEmpty("Filters"), registries);
        access = tag.getIntOr("Access", BOTH);
        denyList = tag.getBooleanOr("DenyList", false);
        redstone.load(tag);
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
