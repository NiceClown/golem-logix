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
 * Golem Interface: on all six sides an input funnel and a network socket; one channel for handing out stock and taking items into the network.
 */
public class InterfaceBlock extends Block implements com.golemlogix.blocks.RemovalAware, GolemNode, EntityBlock {
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

    /** The "interface did some work" event reaches the client block entity (animation). */
    @Override
    protected boolean triggerEvent(BlockState state, Level level, BlockPos pos, int id, int param) {
        BlockEntity be = level.getBlockEntity(pos);
        return be != null && be.triggerEvent(id, param);
    }

    /** A bucket (or any container) used on the interface: the fluid is poured into its tank and goes to the network. */
    @Override
    protected InteractionResult useItemOn(net.minecraft.world.item.ItemStack stack, BlockState state, Level level,
                                                                   BlockPos pos, Player player, net.minecraft.world.InteractionHand hand,
                                                                   BlockHitResult hit) {
        if (net.neoforged.neoforge.fluids.FluidUtil.getFluidContained(stack).isPresent()
                && level.getBlockEntity(pos) instanceof InterfaceBlockEntity be) {
            if (!level.isClientSide()) {
                net.neoforged.neoforge.fluids.FluidUtil.interactWithFluidHandler(player, hand, be.fluids());
            }
            return net.minecraft.world.InteractionResult.SUCCESS;
        }
        return super.useItemOn(stack, state, level, pos, player, hand, hit);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        com.golemlogix.system.GolemNetwork net = com.golemlogix.system.GolemNetwork.scan(level, pos);
        if (!net.hasPermission(player, com.golemlogix.security.SecurityPermission.BUILD)) {
            if (player instanceof net.minecraft.server.level.ServerPlayer sp) {
                sp.sendOverlayMessage(Component.translatable("message.golem_logix.security_denied"));
            }
            return InteractionResult.CONSUME;
        }
        if (level.getBlockEntity(pos) instanceof InterfaceBlockEntity be) {
            player.openMenu(new SimpleMenuProvider(
                    (id, inv, p) -> new InterfaceMenu(id, inv, be.getConfig(), be.getStock(), be.getInbox(), be.getUpgrades(), be.menuData(), be),
                    Component.translatable("block.golem_logix.golem_interface")));
        }
        return InteractionResult.CONSUME;
    }

    /** Replaced by another block (see RemovalAware). */
    @Override
    public void onRemoved(BlockState state, Level level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof InterfaceBlockEntity be) {
            Containers.dropContents(level, pos, be.getStock());
            Containers.dropContents(level, pos, be.getInbox());
            Containers.dropContents(level, pos, be.getUpgrades());
        }
    }
}
