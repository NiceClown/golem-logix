package com.golemlogix.blockentity;

import com.golemlogix.init.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;

/**
 * Cryo Schematic Concentrator: holds no inventory of its own; tracks active screen viewers so the client unfolds
 * and accelerates the roof hologram animation while someone views the network's patterns.
 */
public class SchematicConcentratorBlockEntity extends BlockEntity {
    public static final int EVENT_VIEWERS = ViewerTracker.EVENT_VIEWERS;

    private final ViewerTracker viewerTracker = new ViewerTracker();
    /** Client hologram unfold 0..1 and ring rotation angle. */
    public float holo, holoPrev, spin, spinPrev;

    public SchematicConcentratorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SCHEMATIC_CONCENTRATOR.get(), pos, state);
    }

    public int viewers() {
        return viewerTracker.viewers();
    }

    public void startOpen() {
        viewerTracker.open(this, () ->
                level.playSound(null, worldPosition, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 0.35f, 1.8f));
    }

    public void stopOpen() {
        viewerTracker.close(this, () ->
                level.playSound(null, worldPosition, SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 0.3f, 1.8f));
    }

    @Override
    public boolean triggerEvent(int id, int param) {
        return viewerTracker.handleEvent(id, param) || super.triggerEvent(id, param);
    }

    public static void clientTick(Level level, BlockPos pos, BlockState state, SchematicConcentratorBlockEntity be) {
        boolean open = be.viewerTracker.isClientOpen();
        be.holoPrev = be.holo;
        be.holo = Math.max(0f, Math.min(1f, be.holo + (open ? 0.1f : -0.07f)));
        be.spinPrev = be.spin;
        be.spin += 1.2f + be.holo * 4.5f;
        if (level.getGameTime() % (open ? 5 : 31) == 0) {
            var r = level.getRandom();
            Direction f = state.getValue(HorizontalDirectionalBlock.FACING);
            double a = Math.toRadians(180 - f.toYRot()), dx = 10.75 / 16 - 0.5, dz = 11.6 / 16 - 0.5;
            double wx = dx * Math.cos(a) + dz * Math.sin(a), wz = -dx * Math.sin(a) + dz * Math.cos(a);
            level.addParticle(ParticleTypes.WHITE_SMOKE, pos.getX() + 0.5 + wx + (r.nextDouble() - 0.5) * 0.06,
                    pos.getY() + 1.28, pos.getZ() + 0.5 + wz + (r.nextDouble() - 0.5) * 0.06, 0, 0.03, 0);
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        tag.putBoolean("Open", viewerTracker.isOpen());
        return tag;
    }

    public void readUpdateTag(CompoundTag tag, HolderLookup.Provider registries) {
        viewerTracker.setClientOpen(tag.getBooleanOr("Open", false));
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void handleUpdateTag(ValueInput input) {
        BlockEntities.load(input, this::readUpdateTag);
    }

    @Override
    public void onDataPacket(Connection net, ValueInput input) {
        handleUpdateTag(input);
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        BlockEntities.onPreRemove(this, pos, state);
    }
}
