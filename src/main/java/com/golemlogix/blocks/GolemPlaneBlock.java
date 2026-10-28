package com.golemlogix.blocks;

import com.golemlogix.system.GolemNode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * A whole-block device that works on the block in front of it, the golem inside doing the work: the Formation Bus (a copper
 * golem sets blocks down) and the Annihilation Bus (an iron golem breaks or clicks them). It faces where the player looks when
 * placing it, takes one channel and joins the network on every side but the front.
 */
public abstract class GolemPlaneBlock extends DirectionalBlock implements GolemNode, EntityBlock, RemovalAware {
    protected GolemPlaneBlock(Properties properties) {
        super(properties.sound(SoundType.COPPER).strength(2.5f).requiresCorrectToolForDrops());
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getNearestLookingDirection());
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    public int channelCost() {
        return 1;
    }

    /** The front belongs to the work: nothing links through it. */
    @Override
    public boolean canLink(BlockState self, BlockState other, Direction toOther) {
        return toOther != self.getValue(FACING) && canLink(self, other);
    }

    /** A neighbour changed (the block in front taken away or set down, redstone): an idle golem looks again at once. */
    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block,
                                   @Nullable net.minecraft.world.level.redstone.Orientation orientation, boolean moving) {
        super.neighborChanged(state, level, pos, block, orientation, moving);
        if (level.getBlockEntity(pos) instanceof Waking w) {
            w.wake();
        }
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide() && player instanceof net.minecraft.server.level.ServerPlayer sp) {
            BlockEntity be = level.getBlockEntity(pos);
            net.minecraft.world.MenuProvider menu = menu(be);
            if (menu != null) {
                sp.openMenu(menu, buf -> writeMenuData(buf));
            }
        }
        return InteractionResult.SUCCESS;
    }

    /** The window of the block entity, or null if it is not ours. */
    @Nullable
    public abstract net.minecraft.world.MenuProvider menu(@Nullable BlockEntity be);

    /** What the client needs to build the window (see the menu type's factory). */
    protected void writeMenuData(net.minecraft.network.RegistryFriendlyByteBuf buf) {
    }

    /** A block entity that sleeps while there is nothing to do and is woken by a neighbour's change. */
    public interface Waking {
        void wake();
    }
}
