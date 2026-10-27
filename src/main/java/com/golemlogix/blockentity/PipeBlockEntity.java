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
 * Pipe block entity: plugs on the sides (a plugged side connects to nothing, which is how neighbouring lines are kept apart
 * so their channels do not mix) and the running snowmen. Plugs are stored here and not in the block state: six
 * flags would multiply the number of pipe states by 64.
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

    /** The heat lamp's reading (see SnowPipeBlock.hasLamp): channels running through this pipe and how many it carries. */
    private int lampLoad, lampCapacity;

    public int lampLoad() {
        return lampLoad;
    }

    public int lampCapacity() {
        return lampCapacity;
    }

    /** Server: the network's walk gives a lamp pipe its load; the client hears only of a change. */
    public void setLamp(int load, int capacity) {
        if (load != lampLoad || capacity != lampCapacity) {
            lampLoad = load;
            lampCapacity = capacity;
            if (level != null && !level.isClientSide()) {
                level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 2);
            }
        }
    }

    /** Whether side dir of the block at pos is closed by a plug (a non-pipe has no plugs). */
    public static boolean plugged(BlockGetter level, BlockPos pos, Direction dir) {
        return level.getBlockEntity(pos) instanceof PipeBlockEntity be && be.isPlugged(dir);
    }

    protected void saveTag(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putByte("Plugs", (byte) plugs);
    }

    protected void loadTag(CompoundTag tag, HolderLookup.Provider registries) {
        plugs = tag.getByteOr("Plugs", (byte) 0) & 63;
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        tag.putByte("Plugs", (byte) plugs);
        tag.putInt("LampLoad", lampLoad);
        tag.putInt("LampCapacity", lampCapacity);
        return tag;
    }

    public void readUpdateTag(CompoundTag tag, HolderLookup.Provider registries) {
        plugs = tag.getByteOr("Plugs", (byte) 0) & 63;
        lampLoad = tag.getIntOr("LampLoad", 0);
        lampCapacity = tag.getIntOr("LampCapacity", 0);
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

    @Override
    public void onDataPacket(net.minecraft.network.Connection net, net.minecraft.world.level.storage.ValueInput input) {
        handleUpdateTag(input);
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
