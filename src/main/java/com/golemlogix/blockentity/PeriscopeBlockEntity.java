package com.golemlogix.blockentity;

import com.golemlogix.init.ModBlockEntities;
import com.golemlogix.menu.Priority;
import com.golemlogix.system.Fuzzy;
import com.golemlogix.system.GolemNetwork;
import com.golemlogix.system.TargetCaps;
import com.golemlogix.upgrade.RedstoneControl;
import com.golemlogix.upgrade.RedstoneMode;
import com.golemlogix.upgrade.UpgradeContainer;
import com.golemlogix.upgrade.UpgradeSpec;
import com.golemlogix.upgrade.UpgradeType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

/**
 * Periscope Storage Bus: attaches an external container or tank to the network with configurable priority,
 * access direction (both / insert-only / extract-only), allow/deny + fuzzy filters, and redstone gating.
 */
public class PeriscopeBlockEntity extends BlockEntity {
    public static final int FILTER_SLOTS = 9;
    public static final int BOTH = 0, INSERT_ONLY = 1, EXTRACT_ONLY = 2;

    private int priority = 0;
    private final UpgradeContainer upgrades = new UpgradeContainer(UpgradeSpec.PERISCOPE, this, this::invalidateNetwork);
    private final RedstoneControl redstone = new RedstoneControl();
    private final OwnedContainer filters = new OwnedContainer(FILTER_SLOTS, this, s -> true);
    private int access = BOTH;
    private boolean denyList;
    private byte lastStorageEnabled = -1;
    @Nullable
    private TargetCaps caps;

    public PeriscopeBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.PERISCOPE.get(), pos, state);
    }

    public int getPriority() {
        return priority;
    }

    public void setPriority(int value) {
        this.priority = Mth.clamp(value, -Priority.MAX, Priority.MAX);
        setChanged();
        invalidateNetwork();
    }

    private void invalidateNetwork() {
        if (level != null && !level.isClientSide()) {
            storageEnabled();
            GolemNetwork.clearCache();
        }
    }

    public UpgradeContainer getUpgrades() {
        return upgrades;
    }

    public OwnedContainer getFilters() {
        return filters;
    }

    public boolean passes(ItemStack stack) {
        boolean fuzzy = upgrades.has(UpgradeType.FUZZY), any = false;
        for (int i = 0; i < FILTER_SLOTS; i++) {
            ItemStack f = filters.getItem(i);
            if (f.isEmpty() || !BlockEntities.ghostFluid(f).isEmpty()) {
                continue;
            }
            any = true;
            if (Fuzzy.matches(f, stack, fuzzy)) {
                return !denyList;
            }
        }
        return !any || denyList;
    }

    public boolean passes(FluidStack fluid) {
        boolean fuzzy = upgrades.has(UpgradeType.FUZZY), any = false;
        for (int i = 0; i < FILTER_SLOTS; i++) {
            FluidStack f = BlockEntities.ghostFluid(filters.getItem(i));
            if (f.isEmpty()) {
                continue;
            }
            any = true;
            boolean match = fuzzy ? FluidStack.isSameFluid(f, fluid) : FluidStack.isSameFluidSameComponents(f, fluid);
            if (match) {
                return !denyList;
            }
        }
        return !any || denyList;
    }

    public boolean mayInsert(ItemStack stack) {
        return access != EXTRACT_ONLY && passes(stack);
    }

    public boolean mayExtract(ItemStack stack) {
        return access != INSERT_ONLY && passes(stack);
    }

    public boolean mayInsert(FluidStack fluid) {
        return access != EXTRACT_ONLY && passes(fluid);
    }

    public boolean mayExtract(FluidStack fluid) {
        return access != INSERT_ONLY && passes(fluid);
    }

    public TargetCaps targetCaps(ServerLevel level, BlockPos target, Direction face) {
        caps = TargetCaps.of(caps, level, target, face, () -> !isRemoved(), () -> {
        });
        return caps;
    }

    public boolean storageEnabled() {
        boolean enabled = computeStorageEnabled();
        if (level != null && !level.isClientSide()) {
            lastStorageEnabled = (byte) (enabled ? 1 : 0);
        }
        return enabled;
    }

    private boolean computeStorageEnabled() {
        if (level == null || !upgrades.has(UpgradeType.REDSTONE)) {
            return true;
        }
        RedstoneMode mode = redstone.mode();
        if (mode != RedstoneMode.HIGH && mode != RedstoneMode.LOW) {
            return true;
        }
        boolean powered = level.hasNeighborSignal(worldPosition);
        return mode == RedstoneMode.HIGH ? powered : !powered;
    }

    /** Updates cached redstone-gating state and returns true only when {@link #storageEnabled()} changed. */
    public boolean checkStorageEnabledChanged() {
        if (level == null || !upgrades.has(UpgradeType.REDSTONE)) {
            return false;
        }
        byte now = (byte) (computeStorageEnabled() ? 1 : 0);
        if (now == lastStorageEnabled) {
            return false;
        }
        lastStorageEnabled = now;
        return true;
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
                    invalidateNetwork();
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
        access = Math.floorMod(tag.getIntOr("Access", BOTH), 3);
        denyList = tag.getBooleanOr("DenyList", false);
        redstone.load(tag);
        lastStorageEnabled = -1;
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
