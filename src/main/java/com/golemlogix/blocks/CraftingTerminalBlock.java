package com.golemlogix.blocks;

import com.golemlogix.blockentity.TerminalBlockEntity;
import com.golemlogix.init.ModBlocks;
import com.golemlogix.menu.TerminalMenu;
import com.golemlogix.system.GolemNetwork;
import com.golemlogix.system.GolemNode;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

public class CraftingTerminalBlock extends HorizontalDirectionalBlock implements GolemNode, EntityBlock {
    public static final MapCodec<CraftingTerminalBlock> CODEC = simpleCodec(CraftingTerminalBlock::new);

    public CraftingTerminalBlock(Properties properties) {
        super(properties.sound(SoundType.METAL).strength(3.0f).noOcclusion().lightLevel(state -> 6).requiresCorrectToolForDrops());
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(GolemProperties.ACTIVE, false));
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TerminalBlockEntity(pos, state);
    }

    @Override
    public int channelCost() {
        return 1;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (player instanceof ServerPlayer sp) {
            GolemNetwork net = GolemNetwork.scan(level, pos);
            String err = net.accessError(pos);
            if (err != null) {
                sp.sendOverlayMessage(Component.translatable(err));
                return InteractionResult.CONSUME;
            }
            if (!net.hasPermission(player, com.golemlogix.security.SecurityPermission.INSERT)
                    && !net.hasPermission(player, com.golemlogix.security.SecurityPermission.EXTRACT)
                    && !net.hasPermission(player, com.golemlogix.security.SecurityPermission.CRAFT)) {
                sp.sendOverlayMessage(Component.translatable("message.golem_logix.security_denied"));
                return InteractionResult.CONSUME;
            }
            sp.openMenu(new SimpleMenuProvider(
                            (id, inv, p) -> new TerminalMenu(id, inv, level, pos, pos, ModBlocks.CRAFTING_TERMINAL.get(), true, false, 0),
                            Component.translatable("gui.golem_logix.title.crafting_terminal")),
                    buf -> {
                        buf.writeBoolean(true);
                        buf.writeBoolean(false);
                    });
        }
        return InteractionResult.CONSUME;
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, GolemProperties.ACTIVE);
    }
}
