package com.golemlogix.blockentity;

import com.golemlogix.init.ModBlockEntities;
import com.golemlogix.items.MagneticDiskItem;
import com.golemlogix.menu.Priority;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.ContainerData;
import com.golemlogix.system.DiskStorage;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Copper Drive Cabinet (2x2 multiblock): 10 slots (2 columns x 5 rows) for magnetic cassettes, item and fluid disks.
 * Lives in the main (bottom left) part of the cabinet.
 */
public class CopperDriveBayBlockEntity extends BlockEntity {
    public static final int SLOTS = 10;

    private final OwnedContainer container = new OwnedContainer(SLOTS, this, DiskStorage::isDisk) {
        @Override
        public void setChanged() {
            super.setChanged();
            updateMask();
            refreshVisuals();
        }

        /** The window was opened: the glass display door swings open (like a chest: by the number of viewers). */
        @Override
        public void startOpen(net.minecraft.world.entity.ContainerUser user) {
            if (!(user.getLivingEntity() instanceof net.minecraft.world.entity.player.Player player) || !player.isSpectator()) {
                viewers++;
                signalViewers(viewers == 1);
            }
        }

        @Override
        public void stopOpen(net.minecraft.world.entity.ContainerUser user) {
            if (!(user.getLivingEntity() instanceof net.minecraft.world.entity.player.Player player) || !player.isSpectator()) {
                viewers = Math.max(0, viewers - 1);
                signalViewers(viewers == 0);
            }
        }
    };

    /** Block event: how many players look into the window (clients open and close the display door). */
    public static final int EVENT_VIEWERS = 1;
    private int viewers = 0;
    private boolean clientOpen = false;
    /** Door opening 0..1 (client). */
    public float door, doorPrev;

    public int viewers() {
        return viewers;
    }

    private void signalViewers(boolean edge) {
        if (level == null || level.isClientSide()) {
            return;
        }
        if (edge) {
            level.playSound(null, worldPosition, viewers > 0 ? net.minecraft.sounds.SoundEvents.COPPER_DOOR_OPEN
                    : net.minecraft.sounds.SoundEvents.COPPER_DOOR_CLOSE, net.minecraft.sounds.SoundSource.BLOCKS, 0.6f, 1.25f);
        }
        level.blockEvent(worldPosition, getBlockState().getBlock(), EVENT_VIEWERS, viewers);
    }

    @Override
    public boolean triggerEvent(int id, int param) {
        if (id == EVENT_VIEWERS) {
            clientOpen = param > 0;
            return true;
        }
        return super.triggerEvent(id, param);
    }

    public static void clientTick(net.minecraft.world.level.Level level, BlockPos pos, BlockState state, CopperDriveBayBlockEntity be) {
        be.doorPrev = be.door;
        be.door = Math.max(0f, Math.min(1f, be.door + (be.clientOpen ? 0.1f : -0.08f)));
    }

    /** Bit mask of occupied slots, the client needs it for rendering. */
    private int mask = 0;
    /** Signature of the visible state (slots + fill level), so nothing extra is sent. */
    private int visualSig = 0;
    /** Cabinet priority: a bigger number means this cabinet's disks fill earlier and empty later. */
    private int priority = 0;

    public CopperDriveBayBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.COPPER_DRIVE_BAY.get(), pos, state);
    }

    public int getPriority() {
        return priority;
    }

    public void setPriority(int value) {
        this.priority = Mth.clamp(value, -Priority.MAX, Priority.MAX);
        setChanged();
    }

    public ContainerData priorityData() {
        return Priority.data(this::getPriority, this::setPriority);
    }

    public Container getContainer() {
        return container;
    }

    /** Live list of disks (changes to the stacks are saved). */
    public NonNullList<ItemStack> getDisks() {
        return container.getItems();
    }

    public int getMask() {
        return mask;
    }

    /** Recomputes how the cabinet looks from outside and sends it to clients when it changes. */
    public void refreshVisuals() {
        int sig = 0;
        for (int i = 0; i < SLOTS; i++) {
            ItemStack d = container.getItem(i);
            if (!d.isEmpty()) {
                int cap = Math.max(1, DiskStorage.capacity(d));
                // the counter on the disk itself (the client sees it too)
                long stored = MagneticDiskItem.getStoredCount(d);
                int level = (int) Math.min(4, stored * 4 / cap + (stored > 0 ? 1 : 0));
                sig = sig * 31 + (i + 1) * 8 + level + 977 * BuiltInRegistries.ITEM.getId(d.getItem());
            }
        }
        if (sig != visualSig) {
            visualSig = sig;
            if (level != null && !level.isClientSide()) {
                level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
            }
        }
    }

    private void updateMask() {
        int m = 0;
        for (int i = 0; i < SLOTS; i++) {
            if (!container.getItem(i).isEmpty()) {
                m |= 1 << i;
            }
        }
        if (m != mask) {
            mask = m;
            if (level != null && !level.isClientSide()) {
                level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
            }
        }
    }

    protected void saveTag(CompoundTag tag, HolderLookup.Provider registries) {
        tag.put("Disks", container.createTag(registries));
        tag.putInt("Priority", priority);
    }

    protected void loadTag(CompoundTag tag, HolderLookup.Provider registries) {
        container.fromTag(tag.getListOrEmpty("Disks"), registries);
        priority = tag.getIntOr("Priority", 0);
        int m = 0;
        for (int i = 0; i < SLOTS; i++) {
            if (!container.getItem(i).isEmpty()) {
                m |= 1 << i;
            }
        }
        mask = m;
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        tag.putInt("Mask", mask);
        tag.put("Disks", container.createTag(registries));
        tag.putBoolean("Open", viewers > 0);
        return tag;
    }

    public void readUpdateTag(CompoundTag tag, HolderLookup.Provider lookupProvider) {
        mask = tag.getIntOr("Mask", 0);
        clientOpen = tag.getBooleanOr("Open", false);
        container.fromTag(tag.getListOrEmpty("Disks"), lookupProvider);
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
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

    @Override
    public void handleUpdateTag(net.minecraft.world.level.storage.ValueInput input) {
        readUpdateTag(com.golemlogix.system.NbtBridge.read(input), input.lookup());
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
