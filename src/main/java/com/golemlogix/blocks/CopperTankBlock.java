package com.golemlogix.blocks;

import com.golemlogix.blockentity.CopperTankBlockEntity;
import com.golemlogix.items.CopperTankItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.neoforged.neoforge.transfer.fluid.FluidUtil;
import org.jetbrains.annotations.Nullable;

/**
 * Copper Tank: a copper chest (what is left of a copper block when a copper golem is made of it) with four panes of glass round
 * it, holding one fluid. Anything that carries fluids fills and empties it - a bucket in hand, pipes, buses, other mods. Broken,
 * it keeps its fluid as an item; tanks joined in a crafting grid hold as much as all of them; the item has a bucket mode.
 */
public class CopperTankBlock extends Block implements EntityBlock {
    private static final VoxelShape SHAPE = Block.box(1, 0, 1, 15, 16, 15);

    public CopperTankBlock(Properties properties) {
        super(properties.sound(SoundType.COPPER).strength(2.0f, 6.0f).requiresCorrectToolForDrops().noOcclusion());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    /**
     * A bucket or any other fluid container pours in or fills up. Another tank pours too, but only in bucket mode: otherwise it is
     * being placed (tanks stack into columns).
     */
    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                          InteractionHand hand, BlockHitResult hit) {
        if (stack.getItem() instanceof CopperTankItem && !CopperTankItem.bucketMode(stack)) {
            return InteractionResult.PASS;
        }
        if (!stack.isEmpty() && stack.getCapability(Capabilities.Fluid.ITEM, ItemAccess.forStack(stack)) != null) {
            return FluidUtil.interactWithFluidHandler(player, hand, level, pos, hit.getDirection(), null)
                    ? InteractionResult.SUCCESS : InteractionResult.CONSUME;
        }
        return InteractionResult.TRY_WITH_EMPTY_HAND;
    }

    /** An empty hand reads the tank: the fluid and how much of it. */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof CopperTankBlockEntity be) {
            player.sendOverlayMessage(CopperTankItem.contentsLine(be.fluid(), be.capacity()));
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos, Direction direction) {
        return level.getBlockEntity(pos) instanceof CopperTankBlockEntity be ? be.signal() : 0;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CopperTankBlockEntity(pos, state);
    }
}
