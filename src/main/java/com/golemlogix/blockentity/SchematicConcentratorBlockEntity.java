package com.golemlogix.blockentity;

import com.golemlogix.init.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Cryo Schematic Concentrator: has no contents of its own (the window shows the punch cards of all providers in the network); the block
 * entity is needed for the animation: while the window is open the hologram above the screen unfolds and spins faster.
 */
public class SchematicConcentratorBlockEntity extends BlockEntity {
    public static final int EVENT_VIEWERS = 1;
    private int viewers = 0;
    private boolean clientOpen;
    /** How unfolded the hologram is 0..1 and the angle of its rings (client). */
    public float holo, holoPrev, spin, spinPrev;

    public SchematicConcentratorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SCHEMATIC_CONCENTRATOR.get(), pos, state);
    }

    public int viewers() {
        return viewers;
    }

    public void startOpen() {
        viewers++;
        if (viewers == 1 && level != null) {
            level.playSound(null, worldPosition, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 0.35f, 1.8f);
        }
        signal();
    }

    public void stopOpen() {
        viewers = Math.max(0, viewers - 1);
        if (viewers == 0 && level != null) {
            level.playSound(null, worldPosition, SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 0.3f, 1.8f);
        }
        signal();
    }

    private void signal() {
        if (level != null && !level.isClientSide()) {
            level.blockEvent(worldPosition, getBlockState().getBlock(), EVENT_VIEWERS, viewers);
        }
    }

    @Override
    public boolean triggerEvent(int id, int param) {
        if (id == EVENT_VIEWERS) {
            clientOpen = param > 0;
            return true;
        }
        return super.triggerEvent(id, param);
    }

    public static void clientTick(Level level, BlockPos pos, BlockState state, SchematicConcentratorBlockEntity be) {
        be.holoPrev = be.holo;
        be.holo = Math.max(0f, Math.min(1f, be.holo + (be.clientOpen ? 0.1f : -0.07f)));
        be.spinPrev = be.spin;
        be.spin += 1.2f + be.holo * 4.5f;
        if (level.getGameTime() % (be.clientOpen ? 5 : 31) == 0) {
            var r = level.random;
            // steam from the frosted pipe on the roof (its mouth in pixels of the north-facing model: x 10.75, z 11.6)
            Direction f = state.getValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING);
            double a = Math.toRadians(180 - f.toYRot()), dx = 10.75 / 16 - 0.5, dz = 11.6 / 16 - 0.5;
            double wx = dx * Math.cos(a) + dz * Math.sin(a), wz = -dx * Math.sin(a) + dz * Math.cos(a);
            level.addParticle(net.minecraft.core.particles.ParticleTypes.WHITE_SMOKE, pos.getX() + 0.5 + wx + (r.nextDouble() - 0.5) * 0.06,
                    pos.getY() + 1.28, pos.getZ() + 0.5 + wz + (r.nextDouble() - 0.5) * 0.06, 0, 0.03, 0);
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        tag.putBoolean("Open", viewers > 0);
        return tag;
    }

    public void readUpdateTag(CompoundTag tag, HolderLookup.Provider registries) {
        clientOpen = tag.getBoolean("Open");
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    // ---- 1.21.6+: the game's ValueInput/ValueOutput over the CompoundTag code above

    @Override
    public void handleUpdateTag(net.minecraft.world.level.storage.ValueInput input) {
        readUpdateTag(com.golemlogix.system.NbtBridge.read(input), input.lookup());
    }
}
