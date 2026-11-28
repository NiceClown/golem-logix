package com.golemlogix.blockentity;

import com.golemlogix.blocks.CryoJarBlock;
import com.golemlogix.blocks.JarForm;
import com.golemlogix.init.ModBlockEntities;
import com.golemlogix.system.JarMerge;
import com.golemlogix.system.SourceHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import org.jetbrains.annotations.Nullable;

/**
 * Cryo-Source Jar: stores Source in the lower half (or controller corner of a merged multi-jar); upper and non-controller
 * parts forward every query and transfer to {@link #owner()}.
 */
public class CryoJarBlockEntity extends BlockEntity implements SourceHelper.Supplier {
    private int source;
    @Nullable
    private BlockPos controller;
    private boolean dirty;
    private long lastSync = -100;
    private int sentSource = -1;

    // client
    private int clientSource;
    public float fill, fillPrev;

    public CryoJarBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CRYO_JAR.get(), pos, state);
    }

    private boolean isLower() {
        BlockState s = getBlockState();
        return !s.hasProperty(CryoJarBlock.HALF) || s.getValue(CryoJarBlock.HALF) == DoubleBlockHalf.LOWER;
    }

    @Nullable
    public CryoJarBlockEntity owner() {
        CryoJarBlockEntity lower = isLower() ? this
                : level != null && level.getBlockEntity(worldPosition.below()) instanceof CryoJarBlockEntity j && j.isLower() ? j : null;
        if (lower == null || lower.controller == null || lower.controller.equals(lower.worldPosition)) {
            return lower;
        }
        return level != null && level.isLoaded(lower.controller) && level.getBlockEntity(lower.controller) instanceof CryoJarBlockEntity c
                && !c.isRemoved() ? c : null;
    }

    @Nullable
    public BlockPos controller() {
        return controller;
    }

    public void setController(@Nullable BlockPos controller) {
        this.controller = controller == null ? null : controller.immutable();
        setChanged();
    }

    public boolean isController() {
        return worldPosition.equals(controller);
    }

    public JarForm form() {
        BlockState s = getBlockState();
        return s.hasProperty(CryoJarBlock.FORM) ? s.getValue(CryoJarBlock.FORM) : JarForm.SINGLE;
    }

    public int capacity() {
        return JarMerge.capacity(form());
    }

    public int takeAll() {
        int all = source;
        source = 0;
        changed();
        return all;
    }

    public void putAll(int amount) {
        source = amount;
        changed();
    }

    @Override
    public boolean infinite() {
        return false;
    }

    @Override
    public int available() {
        CryoJarBlockEntity o = owner();
        return o == null || o.isRemoved() ? 0 : Math.min(o.source, o.capacity());
    }

    @Override
    public int drain(int amount) {
        CryoJarBlockEntity o = owner();
        if (o == null || o.isRemoved() || amount <= 0) {
            return 0;
        }
        int take = Math.min(amount, o.source);
        if (take > 0) {
            o.source -= take;
            o.changed();
        }
        return take;
    }

    public int fill(int amount, boolean simulate) {
        CryoJarBlockEntity o = owner();
        if (o == null || o.isRemoved() || amount <= 0) {
            return 0;
        }
        int add = Math.max(0, Math.min(amount, o.capacity() - o.source));
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
        if (level instanceof ServerLevel sl && isLower() && form() != JarForm.SINGLE
                && Math.floorMod(now + worldPosition.asLong(), 20) == 0 && revalidateMerged(sl)) {
            return;
        }
        if (!dirty || now - lastSync < 10) {
            return;
        }
        dirty = false;
        int cap = capacity();
        int step = Math.max(1, cap / 100);
        if (sentSource < 0 || Math.abs(source - sentSource) >= step
                || (source == 0) != (sentSource == 0) || (source == cap) != (sentSource == cap)) {
            sentSource = source;
            lastSync = now;
            BlockEntities.sendUpdate(this);
            updateComparators();
        }
    }

    /** Re-validates chunk-straddling Giga Jars / Reactors without ever force-loading unloaded chunks. */
    private boolean revalidateMerged(ServerLevel sl) {
        JarForm f = form();
        if (isController()) {
            for (BlockPos cell : JarMerge.cells(worldPosition, f.width())) {
                if (!sl.isLoaded(cell)) {
                    continue;
                }
                BlockState cs = sl.getBlockState(cell);
                BlockState us = sl.getBlockState(cell.above());
                if (!(cs.getBlock() instanceof CryoJarBlock)
                        || cs.getValue(CryoJarBlock.HALF) != DoubleBlockHalf.LOWER
                        || cs.getValue(CryoJarBlock.FORM) != f
                        || !(us.getBlock() instanceof CryoJarBlock)
                        || us.getValue(CryoJarBlock.HALF) != DoubleBlockHalf.UPPER
                        || us.getValue(CryoJarBlock.FORM) != f
                        || !(sl.getBlockEntity(cell) instanceof CryoJarBlockEntity jar)
                        || !worldPosition.equals(jar.controller())) {
                    JarMerge.apart(sl, worldPosition, null);
                    return true;
                }
            }
            return false;
        }
        if (controller == null) {
            JarMerge.resetSingle(sl, worldPosition, this);
            return true;
        }
        if (!sl.isLoaded(controller)) {
            return false;
        }
        BlockState cs = sl.getBlockState(controller);
        int dx = worldPosition.getX() - controller.getX();
        int dz = worldPosition.getZ() - controller.getZ();
        boolean valid = cs.getBlock() instanceof CryoJarBlock
                && cs.getValue(CryoJarBlock.HALF) == DoubleBlockHalf.LOWER
                && cs.getValue(CryoJarBlock.FORM) == f
                && worldPosition.getY() == controller.getY()
                && dx >= 0 && dx < f.width() && dz >= 0 && dz < f.width()
                && sl.getBlockEntity(controller) instanceof CryoJarBlockEntity c
                && c.isController();
        if (!valid) {
            JarMerge.resetSingle(sl, worldPosition, this);
            return true;
        }
        return false;
    }

    private void updateComparators() {
        var block = getBlockState().getBlock();
        level.updateNeighbourForOutputSignal(worldPosition, block);
        if (level.isLoaded(worldPosition.above())) {
            level.updateNeighbourForOutputSignal(worldPosition.above(), block);
        }
        JarForm f = form();
        if (f != JarForm.SINGLE && isController()) {
            for (BlockPos cell : JarMerge.cells(worldPosition, f.width())) {
                if (!cell.equals(worldPosition) && level.isLoaded(cell)) {
                    level.updateNeighbourForOutputSignal(cell, block);
                    if (level.isLoaded(cell.above())) {
                        level.updateNeighbourForOutputSignal(cell.above(), block);
                    }
                }
            }
        }
    }

    public void clientTick() {
        fillPrev = fill;
        float target = clientSource / (float) Math.max(1, capacity());
        fill += (Math.min(1f, target) - fill) * 0.12f;
    }

    public int clientSource() {
        return clientSource;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (isLower()) {
            tag.putInt("Source", source);
            if (controller != null) {
                tag.putLong("Controller", controller.asLong());
            }
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        source = tag.getInt("Source");
        controller = tag.contains("Controller") ? BlockPos.of(tag.getLong("Controller")) : null;
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("Source", source);
        if (controller != null) {
            tag.putLong("Controller", controller.asLong());
        }
        return tag;
    }

    @Override
    public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider registries) {
        clientSource = tag.getInt("Source");
        controller = tag.contains("Controller") ? BlockPos.of(tag.getLong("Controller")) : null;
        if (fill == 0f && fillPrev == 0f) {
            fill = fillPrev = Math.min(1f, clientSource / (float) Math.max(1, capacity()));
        }
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
