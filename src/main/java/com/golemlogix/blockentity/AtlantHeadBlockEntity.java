package com.golemlogix.blockentity;

import com.golemlogix.blocks.AtlantBlock;
import com.golemlogix.init.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Atlant head (upper half of the statue): on the client it turns smoothly towards a player who is nearby and in front of the statue;
 * with no player it slowly turns back and looks straight ahead. Stores no data.
 */
public class AtlantHeadBlockEntity extends BlockEntity {
    /** Head rotation relative to the statue's facing (degrees): yaw and pitch, current and previous tick. */
    public float yaw, yawPrev, pitch, pitchPrev;
    private static final double RANGE = 12.0;
    private static final float MAX_YAW = 70f, MAX_PITCH = 35f;

    public AtlantHeadBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ATLANT_HEAD.get(), pos, state);
    }

    public static void clientTick(Level level, BlockPos pos, BlockState state, AtlantHeadBlockEntity be) {
        be.yawPrev = be.yaw;
        be.pitchPrev = be.pitch;
        float[] look = {be.yaw, be.pitch};
        track(level, pos, state.getValue(AtlantBlock.FACING), look);
        be.yaw = look[0];
        be.pitch = look[1];
    }

    /**
     * Head tracking step for the block headPos (the statue looks along facing): look = {yaw, pitch}, smoothly towards a nearby player
     * in front of the statue, otherwise straight ahead. Shared by standalone atlants and the ones built into the Cryo-Mainframe.
     */
    public static void track(Level level, BlockPos headPos, net.minecraft.core.Direction facing, float[] look) {
        float tYaw = 0f, tPitch = 0f;
        Player player = com.golemlogix.client.ClientHooks.localPlayer();
        double hx = headPos.getX() + 0.5, hy = headPos.getY() + 0.8, hz = headPos.getZ() + 0.5;
        if (player != null && player.level() == level && player.distanceToSqr(hx, hy, hz) < RANGE * RANGE) {
            double dx = player.getX() - hx, dy = player.getEyeY() - hy, dz = player.getZ() - hz;
            // world angle to the player in Minecraft terms (0 = south), relative to the statue's facing
            float world = (float) Math.toDegrees(Math.atan2(-dx, dz));
            float rel = Mth.wrapDegrees(world - facing.toYRot());
            if (Math.abs(rel) < 110f) {
                tYaw = Mth.clamp(rel, -MAX_YAW, MAX_YAW);
                tPitch = Mth.clamp((float) Math.toDegrees(Math.atan2(dy, Math.hypot(dx, dz))), -MAX_PITCH, MAX_PITCH);
            }
        }
        look[0] += Mth.wrapDegrees(tYaw - look[0]) * 0.15f;
        look[1] += (tPitch - look[1]) * 0.15f;
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
