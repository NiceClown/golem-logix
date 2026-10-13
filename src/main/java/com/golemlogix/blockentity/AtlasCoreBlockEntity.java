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
 * Ядро Атланта: раз в секунду пересчитывает сеть и снимает с неё питание Source.
 * Нет Source — вся сеть отключена.
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
    /** Клиентская анимация электронов. */
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

    /** Решётка Атланта достроена (синхронизируется на клиент для рендера). */
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
        be.initialized = true;
        be.refresh(level, pos);
    }

    /** Принудительный пересчёт (первый тик, ПКМ по ядру). */
    public void refresh(Level level, BlockPos pos) {
        GolemNetwork net = GolemNetwork.scan(level, pos);

        boolean newPowered;
        int cost = 0;
        duplicate = net.corePos() != null && !net.corePos().equals(pos);
        if (duplicate) {
            // второе ядро в той же сети не работает
            newPowered = false;
        } else {
            cost = GolemConfig.CORE_SOURCE_PER_SECOND.get()
                    + (int) Math.ceil(net.channelsUsed() * GolemConfig.CHANNEL_SOURCE_PER_SECOND.get());
            newPowered = net.consumeSource(cost);
        }

        if (!duplicate) {
            net.syncActiveVisuals(newPowered);
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
