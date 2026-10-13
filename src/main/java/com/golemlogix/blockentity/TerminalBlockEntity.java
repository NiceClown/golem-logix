package com.golemlogix.blockentity;

import com.golemlogix.init.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Terminal block entity: stores no data, only needed for the screen animation on the client
 * (the network turns on -> a golem waves its hand across the whole screen, then the golem carries crates).
 */
public class TerminalBlockEntity extends BlockEntity {
    /** Client animation state (not used on the server). */
    public boolean animInitialized;
    public boolean lastActive;
    /** Game time (in ticks with fractions) when the greeting started. */
    public float waveStart = -1000f;

    public TerminalBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.TERMINAL.get(), pos, state);
    }
}
