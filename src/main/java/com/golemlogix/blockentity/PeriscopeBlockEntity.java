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
 * Periscope: the priority of the external storage it connects, and a Redstone Module that can switch that storage on and off.
 */
public class PeriscopeBlockEntity extends BlockEntity {
    private int priority = 0;
    private final UpgradeContainer upgrades = new UpgradeContainer(UpgradeSpec.REDSTONE_ONLY, this);
    private final RedstoneControl redstone = new RedstoneControl();

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

    /** Window data: 0 = priority, 1 = redstone mode. */
    public ContainerData menuData() {
        return new ContainerData() {
            @Override
            public int get(int index) {
                return index == 0 ? priority : redstone.mode().ordinal();
            }

            @Override
            public void set(int index, int value) {
                if (index == 0) {
                    setPriority(value);
                } else {
                    RedstoneMode m = RedstoneMode.byId(value);
                    redstone.setMode(m == RedstoneMode.PULSE ? RedstoneMode.IGNORE : m);
                    setChanged();
                }
            }

            @Override
            public int getCount() {
                return 2;
            }
        };
    }

    protected void saveTag(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("Priority", priority);
        tag.put("Upgrades", upgrades.createTag(registries));
        redstone.save(tag);
    }

    protected void loadTag(CompoundTag tag, HolderLookup.Provider registries) {
        priority = tag.getIntOr("Priority", 0);
        upgrades.fromTag(tag.getList("Upgrades", Tag.TAG_COMPOUND), registries);
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
}
