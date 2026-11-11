package com.golemlogix.blocks;

import com.golemlogix.blockentity.FormationBusBlockEntity;
import com.golemlogix.init.ModBlockEntities;
import com.golemlogix.menu.BusMenu;
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

/** Formation Bus: a copper golem fused into a copper casing sets the blocks named in its filter down in front of it. */
public class FormationBusBlock extends GolemPlaneBlock {
    public static final MapCodec<FormationBusBlock> CODEC = simpleCodec(FormationBusBlock::new);

    public FormationBusBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends DirectionalBlock> codec() {
        return CODEC;
    }

    @Nullable
    @Override
    public net.minecraft.world.MenuProvider menu(@Nullable BlockEntity be) {
        return be instanceof FormationBusBlockEntity bus ? new SimpleMenuProvider((id, inv, p) -> new BusMenu(id, inv, bus.getFilters(),
                bus.getUpgrades(), BusMenu.KIND_FORMATION, bus.menuData()), Component.translatable("block.golem_logix.formation_bus")) : null;
    }

    @Override
    protected void writeMenuData(net.minecraft.network.RegistryFriendlyByteBuf buf) {
        buf.writeVarInt(BusMenu.KIND_FORMATION);
    }

    @Override
    public void onRemoved(BlockState state, Level level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof FormationBusBlockEntity bus) {
            bus.dropContents();
        }
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new FormationBusBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide() || type != ModBlockEntities.FORMATION_BUS.get()) {
            return null;
        }
        return (lvl, p, st, be) -> FormationBusBlockEntity.serverTick(lvl, p, st, (FormationBusBlockEntity) be);
    }
}
