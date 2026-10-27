package com.golemlogix.blockentity;

import com.golemlogix.init.ModBlockEntities;
import com.golemlogix.system.GolemNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Channel Counter: once a second it reads the network's channels in use and its capacity, and tells the client only of a change.
 * The mode (in use / capacity) is the player's choice and is saved with the block.
 */
public class ChannelCounterBlockEntity extends BlockEntity {
    private int used, capacity;
    private boolean online;
    private boolean showCapacity;

    public ChannelCounterBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CHANNEL_COUNTER.get(), pos, state);
    }

    public int used() {
        return used;
    }

    public int capacity() {
        return capacity;
    }

    public boolean online() {
        return online;
    }

    public boolean showsCapacity() {
        return showCapacity;
    }

    public void toggleMode() {
        showCapacity = !showCapacity;
        setChanged();
        if (level != null) {
            level.playSound(null, worldPosition, SoundEvents.COMPARATOR_CLICK, SoundSource.BLOCKS, 0.4f, showCapacity ? 1.3f : 0.9f);
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 2);
        }
    }

    public void serverTick() {
        if (level == null || (level.getGameTime() + worldPosition.asLong()) % 20 != 0) {
            return;
        }
        GolemNetwork net = GolemNetwork.get(level, worldPosition);
        boolean on = net.hasCore() && net.isOnline();
        int u = on ? net.channelsUsed() : 0, c = on ? net.channelCapacity() : 0;
        if (on != online || u != used || c != capacity) {
            online = on;
            used = u;
            capacity = c;
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 2);
        }
    }

    protected void saveTag(CompoundTag tag) {
        tag.putBoolean("ShowCapacity", showCapacity);
    }

    protected void loadTag(CompoundTag tag) {
        showCapacity = tag.getBooleanOr("ShowCapacity", false);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("Used", used);
        tag.putInt("Capacity", capacity);
        tag.putBoolean("Online", online);
        tag.putBoolean("ShowCapacity", showCapacity);
        return tag;
    }

    public void readUpdateTag(CompoundTag tag) {
        used = tag.getIntOr("Used", 0);
        capacity = tag.getIntOr("Capacity", 0);
        online = tag.getBooleanOr("Online", false);
        showCapacity = tag.getBooleanOr("ShowCapacity", false);
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    // ---- 1.21.6+: the game's ValueInput/ValueOutput over the CompoundTag code above

    @Override
    protected void saveAdditional(net.minecraft.world.level.storage.ValueOutput out) {
        super.saveAdditional(out);
        CompoundTag tag = new CompoundTag();
        saveTag(tag);
        com.golemlogix.system.NbtBridge.write(out, tag);
    }

    @Override
    protected void loadAdditional(net.minecraft.world.level.storage.ValueInput in) {
        super.loadAdditional(in);
        loadTag(com.golemlogix.system.NbtBridge.read(in));
    }

    @Override
    public void handleUpdateTag(net.minecraft.world.level.storage.ValueInput in) {
        readUpdateTag(com.golemlogix.system.NbtBridge.read(in));
    }

    @Override
    public void onDataPacket(net.minecraft.network.Connection net, net.minecraft.world.level.storage.ValueInput in) {
        handleUpdateTag(in);
    }
}
