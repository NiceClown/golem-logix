package com.golemlogix.blocks;

import com.golemlogix.blockentity.AnnihilationBusBlockEntity;
import com.golemlogix.init.ModBlockEntities;
import com.golemlogix.menu.AnnihilationBusMenu;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/** Annihilation Bus: a small iron golem in a brass niche breaks, or clicks, the block in front of it with the tool it holds. */
public class AnnihilationBusBlock extends GolemPlaneBlock {
    public static final MapCodec<AnnihilationBusBlock> CODEC = simpleCodec(AnnihilationBusBlock::new);

    public AnnihilationBusBlock(Properties properties) {
        super(properties.noOcclusion());
    }

    @Override
    protected MapCodec<? extends DirectionalBlock> codec() {
        return CODEC;
    }

    @Nullable
    @Override
    public net.minecraft.world.MenuProvider menu(@Nullable BlockEntity be) {
        return be instanceof AnnihilationBusBlockEntity bus ? new SimpleMenuProvider((id, inv, p) -> new AnnihilationBusMenu(id, inv,
                bus.getFilters(), bus.getTool(), bus.getUpgrades(), bus.menuData()), Component.translatable("block.golem_logix.annihilation_bus"))
                : null;
    }

    @Override
    public void onRemoved(BlockState state, Level level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof AnnihilationBusBlockEntity bus) {
            bus.dropContents();
        }
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new AnnihilationBusBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide() || type != ModBlockEntities.ANNIHILATION_BUS.get()) {
            return null;
        }
        return (lvl, p, st, be) -> AnnihilationBusBlockEntity.serverTick(lvl, p, st, (AnnihilationBusBlockEntity) be);
    }
}
