package com.golemlogix.blocks;

import com.golemlogix.blockentity.CryoMainframeBlockEntity;
import com.golemlogix.init.ModBlockEntities;
import com.golemlogix.menu.MainframeMenu;
import com.golemlogix.system.GolemNode;
import com.golemlogix.system.MainframeStructure;
import com.mojang.serialization.MapCodec;
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
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Cryo-Mainframe, the owner of a multiblock cabinet (see {@link MainframeStructure}). Cryo-Calculators go inside
 * (each gives several independent cores) together with Compressed Co-Processors (each counts as several and speeds up all cores).
 * The whole cabinet takes one channel in the network. Once assembled it is drawn entirely by the renderer: the glass doors open while
 * someone looks into its window.
 */
public class CryoMainframeBlock extends HorizontalDirectionalBlock implements com.golemlogix.blocks.RemovalAware, EntityBlock, GolemNode {
    public static final MapCodec<CryoMainframeBlock> CODEC = simpleCodec(CryoMainframeBlock::new);
    public static final BooleanProperty FORMED = BooleanProperty.create("formed");

    public CryoMainframeBlock(Properties properties) {
        super(properties.sound(SoundType.COPPER).strength(5.0f, 9.0f).requiresCorrectToolForDrops().noOcclusion()
                .lightLevel(state -> state.getValue(FORMED) ? 8 : 3));
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(FORMED, false));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, FORMED);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        // the renderer draws the whole assembled cabinet
        return state.getValue(FORMED) ? RenderShape.INVISIBLE : RenderShape.MODEL;
    }

    @Override
    public int channelCost() {
        return 1;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CryoMainframeBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (type != ModBlockEntities.CRYO_MAINFRAME.get()) {
            return null;
        }
        if (level.isClientSide()) {
            return (lvl, p, st, be) -> CryoMainframeBlockEntity.clientTick(lvl, p, st, (CryoMainframeBlockEntity) be);
        }
        return (lvl, p, st, be) -> CryoMainframeBlockEntity.serverTick(lvl, p, st, (CryoMainframeBlockEntity) be);
    }

    /** Block events (the window was opened or closed: the doors) go to the block entity, otherwise the server would not send them to clients. */
    @Override
    protected boolean triggerEvent(BlockState state, Level level, BlockPos pos, int id, int param) {
        super.triggerEvent(state, level, pos, id, param);
        BlockEntity be = level.getBlockEntity(pos);
        return be != null && be.triggerEvent(id, param);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        Direction facing = state.getValue(FACING);
        if (!state.getValue(FORMED)) {
            List<MainframeStructure.Missing> missing = MainframeStructure.check(level, pos, facing);
            if (missing.isEmpty()) {
                MainframeStructure.form(level, pos, facing);
                player.sendOverlayMessage(Component.translatable("message.golem_logix.mainframe.formed"));
            } else {
                MainframeStructure.Missing m = missing.get(0);
                player.sendSystemMessage(Component.translatable("message.golem_logix.mainframe.missing", missing.size(),
                        m.block().getName(), m.pos().getX(), m.pos().getY(), m.pos().getZ()));
            }
            return InteractionResult.CONSUME;
        }
        if (level.getBlockEntity(pos) instanceof CryoMainframeBlockEntity be) {
            player.openMenu(new SimpleMenuProvider(
                    (id, inv, p) -> new MainframeMenu(id, inv, be, be.getCalculators(), be.getUpgrades(), be.menuData(),
                            ContainerLevelAccess.create(level, pos)),
                    Component.translatable("block.golem_logix.cryo_mainframe")));
        }
        return InteractionResult.CONSUME;
    }

    /** Replaced by another block (see RemovalAware). */
    @Override
    public void onRemoved(BlockState state, Level level, BlockPos pos) {
        if (!level.isClientSide()) {
            if (level.getBlockEntity(pos) instanceof CryoMainframeBlockEntity be) {
                be.dropContents();
            }
            if (state.getValue(FORMED)) {
                MainframeStructure.disassemble(level, pos, state.getValue(FACING), pos);
            }
        }
    }
}
