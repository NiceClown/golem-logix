package com.golemlogix.blocks;

import com.golemlogix.blockentity.RecipeMonitorBlockEntity;
import com.golemlogix.init.ModBlockEntities;
import com.golemlogix.menu.RecipeMonitorMenu;
import com.golemlogix.system.GolemNode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * Монитор Рецептов: латунный шкаф с круглым иллюминатором, за которым в фиолетовом вихре кружатся перфокарты — по одной на
 * каждую идущую задачу. ПКМ открывает пульт: все Крио-Калькуляторы сети, их заказы, ход, шаги и кнопка отмены.
 */
public class RecipeMonitorBlock extends Block implements GolemNode, EntityBlock {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    public RecipeMonitorBlock(Properties properties) {
        super(properties.sound(SoundType.COPPER).strength(3.0f).lightLevel(state -> 6).requiresCorrectToolForDrops().noOcclusion());
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public int channelCost() {
        return 1;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new RecipeMonitorBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (type != ModBlockEntities.RECIPE_MONITOR.get()) {
            return null;
        }
        if (level.isClientSide()) {
            return (lvl, p, st, be) -> RecipeMonitorBlockEntity.clientTick(lvl, p, st, (RecipeMonitorBlockEntity) be);
        }
        return (lvl, p, st, be) -> RecipeMonitorBlockEntity.serverTick(lvl, p, st, (RecipeMonitorBlockEntity) be);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        player.openMenu(new SimpleMenuProvider(
                (id, inv, p) -> new RecipeMonitorMenu(id, inv, level, pos, ContainerLevelAccess.create(level, pos)),
                Component.translatable("block.golem_logix.recipe_monitor")));
        return InteractionResult.CONSUME;
    }
}
