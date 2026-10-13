package com.golemlogix.blockentity;

import com.golemlogix.config.GolemConfig;
import com.golemlogix.init.ModBlockEntities;
import com.golemlogix.init.ModSounds;
import com.golemlogix.system.AtlasStructure;
import com.golemlogix.system.GolemNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Atlas Core: once a second it rescans the network and draws Source to power it.
 * No Source means the whole network is off.
 */
public class AtlasCoreBlockEntity extends BlockEntity {
    private boolean powered = false;
    private int channelsUsed = 0;
    private int channelCapacity = 0;
    private int devices = 0;
    private int sourcePerSecond = 0;
    private boolean infiniteSource = false;
    private boolean duplicate = false;
    private boolean wasDuplicate = false;
    private boolean formed = false;
    private boolean initialized = false;
    /** Client electron animation. */
    public float orbit = 0;

    public AtlasCoreBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ATLAS_CORE.get(), pos, state);
    }

    public boolean isPowered() {
        return powered;
    }

    public int getChannelsUsed() {
        return channelsUsed;
    }

    public int getChannelCapacity() {
        return channelCapacity;
    }

    public int getDevices() {
        return devices;
    }

    public int getSourcePerSecond() {
        return sourcePerSecond;
    }

    public boolean isInfiniteSource() {
        return infiniteSource;
    }

    /** The Atlas Array is complete (synced to the client for rendering). */
    public boolean isFormed() {
        return formed;
    }

    public boolean isDuplicate() {
        return duplicate;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, AtlasCoreBlockEntity be) {
        if (be.powered && (level.getGameTime() + pos.asLong()) % 100 == 0) {
            level.playSound(null, pos, ModSounds.VACUUM_HUM.get(), SoundSource.BLOCKS, 0.35f, 1.0f);
        }
        if (be.initialized && (level.getGameTime() + pos.asLong()) % 20 != 0) {
            return;
        }
        boolean first = !be.initialized;
        be.initialized = true;
        be.update(level, pos, first);
    }

    /** Forced rescan (right-click on the core, tests); the fresh network is shared with every device of it. */
    public void refresh(Level level, BlockPos pos) {
        update(level, pos, true);
    }

    /** The network whose lights were last set, and how many seconds ago (they are set again on a change and now and then). */
    private GolemNetwork syncedNetwork;
    private int secondsSinceSync;

    /**
     * Once a second: drains Source and lights the network. The walk itself is the one the devices share and is remembered until
     * something changes (it used to be repeated every second); the array is checked here, because casings and Atlants are not
     * network nodes and their changes do not reach the network on their own.
     */
    private void update(Level level, BlockPos pos, boolean force) {
        GolemNetwork net = force ? GolemNetwork.refresh(level, pos) : GolemNetwork.get(level, pos);
        if (!force && net.isPrimaryCore(pos) && AtlasStructure.check(level, pos).valid() != net.structureValid()) {
            GolemNetwork.nodesChanged(level, pos);
            net = GolemNetwork.refresh(level, pos);
        }

        boolean newPowered;
        int cost = 0;
        duplicate = !net.isPrimaryCore(pos);
        if (duplicate) {
            // a second core in the same network does not work (the network chose the other one, the same from every device)
            newPowered = false;
        } else {
            cost = GolemConfig.CORE_SOURCE_PER_SECOND.get()
                    + (int) Math.ceil(net.channelsUsed() * GolemConfig.CHANNEL_SOURCE_PER_SECOND.get())
                    + net.telegraphLinks() * GolemConfig.TELEGRAPH_SOURCE_PER_SECOND.get();
            newPowered = net.consumeSource(cost);
        }

        // the lights only change with the network or the power; walking every node every second for nothing was wasted work
        if (!duplicate && (force || net != syncedNetwork || newPowered != powered || ++secondsSinceSync >= 10)) {
            net.syncActiveVisuals(newPowered);
            syncedNetwork = net;
            secondsSinceSync = 0;
        }
        boolean newFormed = net.structure() != null && net.structure().valid();
        if (newFormed && !formed) {
            AtlasStructure.orientAtlants(level, pos);
        }
        boolean newInfinite = net.hasInfiniteSource() || !com.golemlogix.system.SourceHelper.sourceRequired();
        int newSps = newInfinite ? 0 : cost;
        boolean changed = newFormed != formed || newPowered != powered || net.channelsUsed() != channelsUsed
                || net.channelCapacity() != channelCapacity || net.deviceCount() != devices
                || newInfinite != infiniteSource || newSps != sourcePerSecond || duplicate != wasDuplicate;
        wasDuplicate = duplicate;
        powered = newPowered;
        formed = newFormed;
        channelsUsed = net.channelsUsed();
        channelCapacity = net.channelCapacity();
        devices = net.deviceCount();
        infiniteSource = newInfinite;
        sourcePerSecond = newSps;
        if (changed) {
            setChanged();
            level.sendBlockUpdated(pos, getBlockState(), getBlockState(), 3);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putBoolean("Powered", powered);
        tag.putBoolean("Formed", formed);
        tag.putInt("ChannelsUsed", channelsUsed);
        tag.putInt("ChannelCapacity", channelCapacity);
        tag.putInt("Devices", devices);
        tag.putInt("SourcePerSecond", sourcePerSecond);
        tag.putBoolean("InfiniteSource", infiniteSource);
        tag.putBoolean("Duplicate", duplicate);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        powered = tag.getBoolean("Powered");
        formed = tag.getBoolean("Formed");
        channelsUsed = tag.getInt("ChannelsUsed");
        channelCapacity = tag.getInt("ChannelCapacity");
        devices = tag.getInt("Devices");
        sourcePerSecond = tag.getInt("SourcePerSecond");
        infiniteSource = tag.getBoolean("InfiniteSource");
        duplicate = tag.getBoolean("Duplicate");
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag, registries);
        return tag;
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
