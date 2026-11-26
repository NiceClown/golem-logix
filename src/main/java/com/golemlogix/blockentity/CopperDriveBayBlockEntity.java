package com.golemlogix.blockentity;

import com.golemlogix.init.ModBlockEntities;
import com.golemlogix.items.MagneticDiskItem;
import com.golemlogix.menu.Priority;
import com.golemlogix.system.DiskStorage;
import com.golemlogix.system.GolemNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Copper Drive Cabinet (2x2 multiblock): 10 slots (2 columns x 5 rows) for magnetic item and fluid storage disks.
 * Lives in the bottom-left master cell; syncs slot occupancy, fill levels, and door open state to tracking clients.
 */
public class CopperDriveBayBlockEntity extends BlockEntity {
    public static final int SLOTS = 10;
    public static final int EVENT_VIEWERS = ViewerTracker.EVENT_VIEWERS;
    /** Monotonic server counter bumped on physical disk insert, remove, or swap in any cabinet. */
    private static long diskChangeEpoch;

    static long diskChangeEpoch() {
        return diskChangeEpoch;
    }

    private final ViewerTracker viewerTracker = new ViewerTracker();

    private final OwnedContainer container = new OwnedContainer(SLOTS, this, DiskStorage::isDisk) {
        @Override
        public void setChanged() {
            super.setChanged();
            refreshVisuals();
            if (level != null && !level.isClientSide()) {
                AtlasCoreBlockEntity core = GolemNetwork.get(level, worldPosition).core();
                if (core != null) {
                    core.onDiskChanged();
                }
                diskChangeEpoch++;
                GolemNetwork.clearCache();
            }
        }

        @Override
        public void startOpen(Player player) {
            if (!player.isSpectator()) {
                viewerTracker.open(CopperDriveBayBlockEntity.this, () ->
                        level.playSound(null, worldPosition, SoundEvents.COPPER_DOOR_OPEN, SoundSource.BLOCKS, 0.6f, 1.25f));
            }
        }

        @Override
        public void stopOpen(Player player) {
            if (!player.isSpectator()) {
                viewerTracker.close(CopperDriveBayBlockEntity.this, () ->
                        level.playSound(null, worldPosition, SoundEvents.COPPER_DOOR_CLOSE, SoundSource.BLOCKS, 0.6f, 1.25f));
            }
        }
    };

    /** Client door opening 0..1. */
    public float door, doorPrev;
    /** Bit mask of occupied slots (synced to clients). */
    private int mask = 0;
    /** Quantized signature of visible disk states so redundant updates are skipped. */
    private int visualSig = 0;
    /** Higher-priority cabinets fill earlier and empty later. */
    private int priority = 0;

    public CopperDriveBayBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.COPPER_DRIVE_BAY.get(), pos, state);
    }

    public int viewers() {
        return viewerTracker.viewers();
    }

    @Override
    public boolean triggerEvent(int id, int param) {
        return viewerTracker.handleEvent(id, param) || super.triggerEvent(id, param);
    }

    public static void clientTick(Level level, BlockPos pos, BlockState state, CopperDriveBayBlockEntity be) {
        be.doorPrev = be.door;
        be.door = Math.max(0f, Math.min(1f, be.door + (be.viewerTracker.isClientOpen() ? 0.1f : -0.08f)));
    }

    public int getPriority() {
        return priority;
    }

    public void setPriority(int value) {
        this.priority = Mth.clamp(value, -Priority.MAX, Priority.MAX);
        setChanged();
        if (level != null && !level.isClientSide()) {
            GolemNetwork.clearCache();
        }
    }

    public ContainerData priorityData() {
        return Priority.data(this::getPriority, this::setPriority);
    }

    public Container getContainer() {
        return container;
    }

    public NonNullList<ItemStack> getDisks() {
        return container.getItems();
    }

    public int getMask() {
        return mask;
    }

    /** Recomputes slot mask and fill-level signature, broadcasting a single update packet when changed. */
    public void refreshVisuals() {
        int m = 0;
        int sig = 0;
        for (int i = 0; i < SLOTS; i++) {
            ItemStack d = container.getItem(i);
            if (!d.isEmpty()) {
                m |= 1 << i;
                int cap = Math.max(1, DiskStorage.capacity(d));
                long stored = MagneticDiskItem.getStoredCount(d);
                int level = (int) Math.min(4, stored * 4 / cap + (stored > 0 ? 1 : 0));
                sig = sig * 31 + (i + 1) * 8 + level + 977 * BuiltInRegistries.ITEM.getId(d.getItem());
            }
        }
        if (m != mask || sig != visualSig) {
            mask = m;
            visualSig = sig;
            BlockEntities.sendUpdate(this);
        }
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && !level.isClientSide()) {
            boolean changed = false;
            for (int i = 0; i < container.getContainerSize(); i++) {
                changed |= DiskStorage.syncStored(container.getItem(i));
            }
            if (changed) {
                setChanged();
                refreshVisuals();
                diskChangeEpoch++;
                GolemNetwork.clearCache();
            }
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("Disks", container.createTag(registries));
        tag.putInt("Priority", priority);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        container.fromTag(tag.getList("Disks", Tag.TAG_COMPOUND), registries);
        priority = tag.getInt("Priority");
        refreshVisuals();
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        tag.putInt("Mask", mask);
        tag.put("Disks", container.createTag(registries));
        tag.putBoolean("Open", viewerTracker.isOpen());
        return tag;
    }

    @Override
    public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider lookupProvider) {
        mask = tag.getInt("Mask");
        viewerTracker.setClientOpen(tag.getBoolean("Open"));
        container.fromTag(tag.getList("Disks", Tag.TAG_COMPOUND), lookupProvider);
    }

    @Override
    public void onDataPacket(Connection net, ClientboundBlockEntityDataPacket pkt, HolderLookup.Provider registries) {
        handleUpdateTag(pkt.getTag(), registries);
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
