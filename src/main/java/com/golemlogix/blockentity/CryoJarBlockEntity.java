package com.golemlogix.blockentity;

import com.golemlogix.blocks.CryoJarBlock;
import com.golemlogix.config.GolemConfig;
import com.golemlogix.init.ModBlockEntities;
import com.golemlogix.system.SourceHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import org.jetbrains.annotations.Nullable;

/**
 * The Source of a Cryo-Source Jar. The lower half keeps it; the upper half's entity only stands for the jar (so a pipe touching the
 * top counts too) and hands every call to the lower one. To the network the jar is a Source supplier like an Ars Nouveau jar.
 */
public class CryoJarBlockEntity extends BlockEntity implements SourceHelper.Supplier {
    private int source;
    private boolean dirty;
    private long lastSync = -100;
    private int sentSource = -1;
    // client
    private int clientSource;
    /** Client: the shown level 0..1 (eases towards the real one) and the previous tick's. */
    public float fill, fillPrev;

    public CryoJarBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CRYO_JAR.get(), pos, state);
    }

    private boolean isLower() {
        BlockState s = getBlockState();
        return !s.hasProperty(CryoJarBlock.HALF) || s.getValue(CryoJarBlock.HALF) == DoubleBlockHalf.LOWER;
    }

    /** The lower half's entity, the one that keeps the Source (null while the jar is half broken). */
    @Nullable
    public CryoJarBlockEntity owner() {
        if (isLower()) {
            return this;
        }
        return level != null && level.getBlockEntity(worldPosition.below()) instanceof CryoJarBlockEntity j && j.isLower() ? j : null;
    }

    public int capacity() {
        return GolemConfig.CRYO_JAR_CAPACITY.get();
    }

    @Override
    public boolean infinite() {
        return false;
    }

    @Override
    public int available() {
        CryoJarBlockEntity o = owner();
        return o == null ? 0 : Math.min(o.source, capacity());
    }

    @Override
    public int drain(int amount) {
        CryoJarBlockEntity o = owner();
        if (o == null || amount <= 0) {
            return 0;
        }
        int take = Math.min(amount, o.source);
        if (take > 0) {
            o.source -= take;
            o.changed();
        }
        return take;
    }

    /** Pours Source in; returns how much went in (simulate: only how much would). */
    public int fill(int amount, boolean simulate) {
        CryoJarBlockEntity o = owner();
        if (o == null || amount <= 0) {
            return 0;
        }
        int add = Math.max(0, Math.min(amount, capacity() - o.source));
        if (add > 0 && !simulate) {
            o.source += add;
            o.changed();
        }
        return add;
    }

    private void changed() {
        dirty = true;
        setChanged();
    }

    public void serverTick() {
        long now = level.getGameTime();
        // the level is shown in steps of 1%: clients get it at most twice a second, and not for every drop
        if (dirty && now - lastSync >= 10) {
            dirty = false;
            int step = Math.max(1, capacity() / 100);
            if (sentSource < 0 || Math.abs(source - sentSource) >= step || (source == 0) != (sentSource == 0) || source == capacity()) {
                sentSource = source;
                lastSync = now;
                level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
                level.updateNeighbourForOutputSignal(worldPosition, getBlockState().getBlock());
            }
        }
    }

    public void clientTick() {
        fillPrev = fill;
        float target = clientSource / (float) Math.max(1, capacity());
        fill += (Math.min(1f, target) - fill) * 0.12f;
    }

    /** Client: the Source the jar holds (as last sent). */
    public int clientSource() {
        return clientSource;
    }

    protected void saveTag(CompoundTag tag, HolderLookup.Provider registries) {
        if (isLower()) {
            tag.putInt("Source", source);
        }
    }

    protected void loadTag(CompoundTag tag, HolderLookup.Provider registries) {
        source = tag.getIntOr("Source", 0);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("Source", source);
        return tag;
    }

    public void readUpdateTag(CompoundTag tag, HolderLookup.Provider registries) {
        clientSource = tag.getIntOr("Source", 0);
        if (fill == 0f && fillPrev == 0f) {
            // a jar seen for the first time shows its level at once, without filling up in front of the player
            fill = fillPrev = Math.min(1f, clientSource / (float) Math.max(1, capacity()));
        }
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
