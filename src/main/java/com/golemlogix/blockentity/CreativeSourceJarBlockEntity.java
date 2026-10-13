package com.golemlogix.blockentity;

import com.golemlogix.init.ModBlockEntities;
import com.golemlogix.system.SourceHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Creative Source Jar: unlimited Source. It also fills neighbouring Ars Nouveau jars.
 */
public class CreativeSourceJarBlockEntity extends BlockEntity {
    public CreativeSourceJarBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CREATIVE_SOURCE_JAR.get(), pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, CreativeSourceJarBlockEntity be) {
        if ((level.getGameTime() + pos.asLong()) % 20 == 0) {
            SourceHelper.pushSourceToNeighbours(level, pos, 10000);
        }
    }
}
