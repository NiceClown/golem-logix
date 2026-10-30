package com.golemlogix.blocks;

import com.golemlogix.blockentity.SecurityTerminalBlockEntity;
import com.golemlogix.menu.SecurityTerminalMenu;
import com.golemlogix.system.GolemNode;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * Security Terminal: protects the network in multiplayer. While present on the network,
 * only the owner and players whose identity punch cards are inserted have access to
 * the network's storage, crafting, and configuration according to their permissions.
 */
public class SecurityTerminalBlock extends HorizontalDirectionalBlock implements GolemNode, EntityBlock, RemovalAware {
    public static final MapCodec<SecurityTerminalBlock> CODEC = simpleCodec(SecurityTerminalBlock::new);

    public SecurityTerminalBlock(Properties properties) {
        super(properties.sound(SoundType.METAL).strength(3.5f).requiresCorrectToolForDrops().noOcclusion());
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    public int channelCost() {
        return 1;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SecurityTerminalBlockEntity(pos, state);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (placer instanceof Player player && level.getBlockEntity(pos) instanceof SecurityTerminalBlockEntity be) {
            be.setOwner(player);
        }
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (player instanceof ServerPlayer sp && level.getBlockEntity(pos) instanceof SecurityTerminalBlockEntity be) {
            sp.openMenu(new SimpleMenuProvider(
                    (id, inv, p) -> new SecurityTerminalMenu(id, inv, be.getCards(), be.getBlockPos(), be.getOwnerId(), be.getOwnerName()),
                    Component.translatable("gui.golem_logix.title.security_terminal")),
                    buf -> {
                        buf.writeBlockPos(be.getBlockPos());
                        buf.writeBoolean(be.getOwnerId() != null);
                        if (be.getOwnerId() != null) buf.writeUUID(be.getOwnerId());
                        buf.writeUtf(be.getOwnerName());
                    });
        }
        return InteractionResult.CONSUME;
    }

    @Override
    public void onRemoved(BlockState state, Level level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof SecurityTerminalBlockEntity be) {
            Containers.dropContents(level, pos, be.getCards());
        }
    }
}
