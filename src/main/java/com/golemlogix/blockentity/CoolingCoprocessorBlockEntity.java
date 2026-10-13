package com.golemlogix.blockentity;

import com.golemlogix.init.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Сопроцессор Охлаждения: данных не хранит, нужен только для анимации-«маховика». Пока соседний калькулятор работает,
 * рёбра радиатора ритмично сжимаются и разжимаются; ход набирается и гаснет плавно, как у тяжёлого маховика.
 */
public class CoolingCoprocessorBlockEntity extends BlockEntity {
    /** Набранный ход 0..1 и фаза качания (радианы); prev — для сглаживания между тиками. */
    public float speed = 0f;
    public float speedPrev = 0f;
    public float phase = 0f;
    public float phasePrev = 0f;

    public CoolingCoprocessorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.COOLING_COPROCESSOR.get(), pos, state);
    }

    public static void clientTick(Level level, BlockPos pos, BlockState state, CoolingCoprocessorBlockEntity be) {
        boolean driven = false;
        for (Direction d : Direction.values()) {
            // состояние ACTIVE соседнего калькулятора или его визуальное окно работы
            BlockPos np = pos.relative(d);
            BlockState ns = level.getBlockState(np);
            if (ns.getBlock() instanceof com.golemlogix.blocks.CryoCalculatorBlock) {
                if (ns.hasProperty(com.golemlogix.blocks.GolemProperties.ACTIVE) && ns.getValue(com.golemlogix.blocks.GolemProperties.ACTIVE)) {
                    driven = true;
                    break;
                }
                if (level.getBlockEntity(np) instanceof CryoCalculatorBlockEntity calcBe && (calcBe.visualWorkTimer > 0 || calcBe.spin > 0.05f)) {
                    driven = true;
                    break;
                }
            }
        }
        be.speedPrev = be.speed;
        be.phasePrev = be.phase;
        // разгон быстрее, выбег медленнее: тяжёлый маховик
        float target = driven ? 1f : 0f;
        be.speed += (target - be.speed) * (driven ? 0.035f : 0.018f);
        if (be.speed < 0.003f && !driven) {
            be.speed = 0f;
        }
        be.phase += 0.32f * be.speed;
        if (be.phase > (float) (Math.PI * 2000)) {
            be.phase -= (float) (Math.PI * 2000);
            be.phasePrev -= (float) (Math.PI * 2000);
        }
    }
}
