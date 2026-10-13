package com.golemlogix.blockentity;

import com.golemlogix.init.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Блок-сущность трубы: заглушки на сторонах (сторона с заглушкой ни с чем не соединяется — так разделяют соседние линии,
 * чтобы каналы не смешивались) и отрисовка бегающих снеговичков. Заглушки хранятся здесь, а не в состоянии блока: шесть
 * флагов умножили бы число состояний трубы в 64 раза.
 */
public class PipeBlockEntity extends BlockEntity {
    private int plugs;

    public PipeBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SNOW_PIPE.get(), pos, state);
    }

    public boolean isPlugged(Direction dir) {
        return (plugs & (1 << dir.ordinal())) != 0;
    }

    public int plugMask() {
        return plugs;
    }

    public int plugCount() {
        return Integer.bitCount(plugs);
    }

    public void setPlugged(Direction dir, boolean on) {
        int before = plugs;
        plugs = on ? plugs | (1 << dir.ordinal()) : plugs & ~(1 << dir.ordinal());
        if (plugs != before) {
            setChanged();
            if (level != null && !level.isClientSide()) {
                level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
            }
        }
    }

    /** Закрыта ли сторона dir у блока в pos заглушкой (у не-трубы заглушек нет). */
    public static boolean plugged(BlockGetter level, BlockPos pos, Direction dir) {
        return level.getBlockEntity(pos) instanceof PipeBlockEntity be && be.isPlugged(dir);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putByte("Plugs", (byte) plugs);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        plugs = tag.getByte("Plugs") & 63;
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        tag.putByte("Plugs", (byte) plugs);
        return tag;
    }

    @Override
    public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider registries) {
        plugs = tag.getByte("Plugs") & 63;
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void onDataPacket(net.minecraft.network.Connection net, ClientboundBlockEntityDataPacket pkt, HolderLookup.Provider registries) {
        handleUpdateTag(pkt.getTag(), registries);
    }
}
