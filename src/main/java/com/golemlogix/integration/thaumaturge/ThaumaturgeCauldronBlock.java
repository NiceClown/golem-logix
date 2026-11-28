package com.golemlogix.integration.thaumaturge;

import com.golemlogix.blocks.EssentiaCauldronBlock;
import com.leclowndu93150.thaumaturge.api.essentia.IEssentiaStreamPort;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Thaumaturge-aware subclass of {@link EssentiaCauldronBlock} exposing left (Auram) and right (Gelum) nozzle ports
 * for Thaumaturge's Essentia stream visuals. Only loaded when Thaumaturge is present.
 */
final class ThaumaturgeCauldronBlock extends EssentiaCauldronBlock implements IEssentiaStreamPort {
    ThaumaturgeCauldronBlock(Properties properties) {
        super(properties);
    }

    @Override
    public StreamPort essentiaStreamPort(BlockGetter level, BlockPos pos, BlockState state, Vec3 otherEnd, boolean incoming) {
        BlockPos base = baseOf(pos, state);
        Direction facing = state.getValue(FACING);
        Direction left = primarySide(facing);
        Direction right = coolantSide(facing);
        Vec3 center = new Vec3(base.getX() + 0.5, base.getY() + 0.5, base.getZ() + 0.5);
        Vec3 leftAnchor = center.add(left.getStepX() * 0.5, 0.0, left.getStepZ() * 0.5);
        Vec3 rightAnchor = center.add(right.getStepX() * 0.5, 0.0, right.getStepZ() * 0.5);
        boolean useLeft = otherEnd == null || otherEnd.distanceToSqr(leftAnchor) <= otherEnd.distanceToSqr(rightAnchor);
        Direction chosen = useLeft ? left : right;
        Vec3 anchor = useLeft ? leftAnchor : rightAnchor;
        Vec3 clearance = center.add(chosen.getStepX() * 0.85, 0.0, chosen.getStepZ() * 0.85);
        return new StreamPort(anchor, clearance);
    }
}
