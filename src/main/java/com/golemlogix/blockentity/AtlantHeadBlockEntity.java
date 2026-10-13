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
 * Голова Атланта (верхняя половина статуи): на клиенте плавно поворачивается к игроку, если тот рядом и перед статуей;
 * без игрока — медленно возвращается и смотрит прямо. Данных не хранит.
 */
public class AtlantHeadBlockEntity extends BlockEntity {
    /** Поворот головы относительно взгляда статуи (градусы): рысканье и наклон, текущие и прошлого тика. */
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
     * Шаг слежения головы в блоке headPos (статуя смотрит в facing): look = {рысканье, наклон}, плавно к игроку рядом и
     * перед статуей, иначе — прямо. Общее для отдельных атлантов и встроенных в Крио-Мейнфрейм.
     */
    public static void track(Level level, BlockPos headPos, net.minecraft.core.Direction facing, float[] look) {
        float tYaw = 0f, tPitch = 0f;
        Player player = com.golemlogix.client.ClientHooks.localPlayer();
        double hx = headPos.getX() + 0.5, hy = headPos.getY() + 0.8, hz = headPos.getZ() + 0.5;
        if (player != null && player.level() == level && player.distanceToSqr(hx, hy, hz) < RANGE * RANGE) {
            double dx = player.getX() - hx, dy = player.getEyeY() - hy, dz = player.getZ() - hz;
            // мировой угол на игрока в системе Minecraft (0 — на юг), относительно взгляда статуи
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
}
