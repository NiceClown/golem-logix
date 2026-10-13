package com.golemlogix.blocks;

import com.golemlogix.blockentity.CreativeSourceJarBlockEntity;
import com.golemlogix.init.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

public class CreativeSourceJarBlock extends Block implements EntityBlock {
    /** Реальные размеры банки с пробкой (как в модели), а не весь блок: рядом остаются доступны соседние блоки. */
    private static final VoxelShape SHAPE = Block.box(3, 0, 3, 13, 15, 13);

    public CreativeSourceJarBlock(Properties properties) {
        super(properties.sound(SoundType.GLASS).strength(3.0f).lightLevel(state -> 15).noOcclusion());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CreativeSourceJarBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (!level.isClientSide() && type == ModBlockEntities.CREATIVE_SOURCE_JAR.get()) {
            return (lvl, p, st, be) -> CreativeSourceJarBlockEntity.serverTick(lvl, p, st, (CreativeSourceJarBlockEntity) be);
        }
        return null;
    }
}
