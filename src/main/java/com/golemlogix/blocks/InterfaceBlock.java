package com.golemlogix.blocks;

import com.golemlogix.blockentity.InterfaceBlockEntity;
import com.golemlogix.init.ModBlockEntities;
import com.golemlogix.menu.InterfaceMenu;
import com.golemlogix.system.GolemNode;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * Интерфейс Голема: со всех шести сторон — раструб приёма и разъём сети, один канал на выдачу запаса и приём в сеть.
 */
public class InterfaceBlock extends Block implements GolemNode, EntityBlock {
    public InterfaceBlock(Properties properties) {
        super(properties.sound(SoundType.COPPER).strength(3.0f).requiresCorrectToolForDrops().noOcclusion());
    }

    @Override
    public int channelCost() {
        return 1;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new InterfaceBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (type != ModBlockEntities.INTERFACE.get()) {
            return null;
        }
        return level.isClientSide()
                ? (lvl, p, st, be) -> InterfaceBlockEntity.clientTick(lvl, p, st, (InterfaceBlockEntity) be)
                : (lvl, p, st, be) -> InterfaceBlockEntity.serverTick(lvl, p, st, (InterfaceBlockEntity) be);
    }

    /** Событие «интерфейс поработал» доходит до клиентской блок-сущности (анимация). */
    @Override
    protected boolean triggerEvent(BlockState state, Level level, BlockPos pos, int id, int param) {
        BlockEntity be = level.getBlockEntity(pos);
        return be != null && be.triggerEvent(id, param);
    }

    /** Ведро (или любая ёмкость) по интерфейсу — жидкость выливается в его бак и уходит в сеть. */
    @Override
    protected net.minecraft.world.ItemInteractionResult useItemOn(net.minecraft.world.item.ItemStack stack, BlockState state, Level level,
                                                                   BlockPos pos, Player player, net.minecraft.world.InteractionHand hand,
                                                                   BlockHitResult hit) {
        if (net.neoforged.neoforge.fluids.FluidUtil.getFluidContained(stack).isPresent()
                && level.getBlockEntity(pos) instanceof InterfaceBlockEntity be) {
            if (!level.isClientSide()) {
                net.neoforged.neoforge.fluids.FluidUtil.interactWithFluidHandler(player, hand, be.fluids());
            }
            return net.minecraft.world.ItemInteractionResult.sidedSuccess(level.isClientSide());
        }
        return super.useItemOn(stack, state, level, pos, player, hand, hit);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (level.getBlockEntity(pos) instanceof InterfaceBlockEntity be) {
            player.openMenu(new SimpleMenuProvider(
                    (id, inv, p) -> new InterfaceMenu(id, inv, be.getConfig(), be.getStock(), be.getInbox(), be.getUpgrades(), be.menuData(), be),
                    Component.translatable("block.golem_logix.golem_interface")));
        }
        return InteractionResult.CONSUME;
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock())) {
            if (level.getBlockEntity(pos) instanceof InterfaceBlockEntity be) {
                Containers.dropContents(level, pos, be.getStock());
                Containers.dropContents(level, pos, be.getInbox());
                Containers.dropContents(level, pos, be.getUpgrades());
            }
            super.onRemove(state, level, pos, newState, isMoving);
        }
    }
}
