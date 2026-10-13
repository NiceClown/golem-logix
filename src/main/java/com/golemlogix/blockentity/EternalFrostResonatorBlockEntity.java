package com.golemlogix.blockentity;

import com.golemlogix.init.ModBlockEntities;
import com.golemlogix.system.GolemNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Вечный Морозный Резонатор: вытягивает Source из эфирного вакуума и по эфиру же отправляет его в Портативные Хронометры,
 * привязанные к сети, к которой он приставлен, — их больше не нужно заряжать. Сеть он не питает и банки Ars Nouveau не наполняет.
 */
public class EternalFrostResonatorBlockEntity extends BlockEntity {
    public EternalFrostResonatorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ETERNAL_FROST_RESONATOR.get(), pos, state);
    }

    /** Приставлен ли резонатор к какой-нибудь сети (иначе ему некого питать). */
    public boolean linked() {
        if (level == null) {
            return false;
        }
        for (Direction d : Direction.values()) {
            BlockPos p = worldPosition.relative(d);
            if (level.isLoaded(p) && level.getBlockState(p).getBlock() instanceof com.golemlogix.system.GolemNode
                    && GolemNetwork.get(level, p).hasCore()) {
                return true;
            }
        }
        return false;
    }

    /** Клиент: стоит ли рядом узел сети (вихрь тогда крутится быстрее и ярче). */
    public boolean clientLinked = false;

    /** Клиент: снежинки кружат в стеклянной камере и вылетают из трубы-вытяжки. */
    public static void clientTick(Level level, BlockPos pos, BlockState state, EternalFrostResonatorBlockEntity be) {
        long t = level.getGameTime();
        if ((t + pos.asLong()) % 20 == 0) {
            boolean linked = false;
            for (Direction d : Direction.values()) {
                if (level.getBlockState(pos.relative(d)).getBlock() instanceof com.golemlogix.system.GolemNode) {
                    linked = true;
                }
            }
            be.clientLinked = linked;
        }
        var r = level.random;
        if (t % (be.clientLinked ? 2 : 5) == 0) {
            double a = r.nextDouble() * Math.PI * 2, rad = 0.12 + r.nextDouble() * 0.2;
            double x = pos.getX() + 0.5 + Math.cos(a) * rad, z = pos.getZ() + 0.5 + Math.sin(a) * rad;
            level.addParticle(net.minecraft.core.particles.ParticleTypes.SNOWFLAKE, x, pos.getY() + 0.6 + r.nextDouble() * 0.3, z,
                    -Math.sin(a) * 0.02, 0.03 + r.nextDouble() * 0.02, Math.cos(a) * 0.02);
        }
        if (t % 9 == 0) {
            level.addParticle(net.minecraft.core.particles.ParticleTypes.SNOWFLAKE, pos.getX() + 0.5 + (r.nextDouble() - 0.5) * 0.15,
                    pos.getY() + 1.25, pos.getZ() + 0.5 + (r.nextDouble() - 0.5) * 0.15, (r.nextDouble() - 0.5) * 0.02, 0.05,
                    (r.nextDouble() - 0.5) * 0.02);
        }
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, EternalFrostResonatorBlockEntity be) {
        long t = level.getGameTime() + pos.asLong();
        if (t % 40 == 0 && level instanceof ServerLevel sl) {
            sl.sendParticles(ParticleTypes.SNOWFLAKE, pos.getX() + 0.5, pos.getY() + 1.1, pos.getZ() + 0.5,
                    3, 0.25, 0.1, 0.25, 0.01);
            // искры «передачи» в эфир — только когда есть сеть, к Хронометрам которой идёт Source
            if (be.linked()) {
                sl.sendParticles(ParticleTypes.END_ROD, pos.getX() + 0.5, pos.getY() + 0.9, pos.getZ() + 0.5,
                        2, 0.08, 0.3, 0.08, 0.02);
            }
        }
    }
}
