package com.golemlogix.blockentity;

import com.golemlogix.init.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Cooling Co-Processor: stores no data, it is only needed for the flywheel animation. While the neighbouring calculator works,
 * the radiator fins rhythmically squeeze and release; the motion builds up and fades smoothly, like a heavy flywheel.
 */
public class CoolingCoprocessorBlockEntity extends BlockEntity {
    /** Built-up motion 0..1 and swing phase (radians); prev values are for smoothing between ticks. */
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
            // the neighbouring calculator's ACTIVE state or its visual work window
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
        // spins up faster than it runs down: a heavy flywheel
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

    /** Since 1.21.5 the block's removal side effects run from here, while this entity and its contents are still in place. */
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (level != null && state.getBlock() instanceof com.golemlogix.blocks.RemovalAware r) {
            r.onRemoved(state, level, pos);
        }
    }
}
