package com.golemlogix.blocks;

import com.golemlogix.blockentity.PatternProviderBlockEntity;
import com.golemlogix.init.ModBlockEntities;
import com.golemlogix.menu.PatternProviderMenu;
import com.golemlogix.system.GolemNode;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
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

public class PatternProviderBlock extends Block implements com.golemlogix.blocks.RemovalAware, GolemNode, EntityBlock {
    /** Where to feed ingredients: to all sides or to one (the arrows on the faces show the choice). */
    public static final net.minecraft.world.level.block.state.properties.EnumProperty<ProviderOutput> OUTPUT =
            net.minecraft.world.level.block.state.properties.EnumProperty.create("output", ProviderOutput.class);

    public PatternProviderBlock(Properties properties) {
        super(properties.sound(SoundType.COPPER).strength(3.0f).requiresCorrectToolForDrops());
        this.registerDefaultState(this.stateDefinition.any().setValue(OUTPUT, ProviderOutput.ALL));
    }

    @Override
    protected void createBlockStateDefinition(net.minecraft.world.level.block.state.StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(OUTPUT);
    }

    /** Rotating and mirroring structures also rotates the feeding side. */
    @Override
    protected BlockState rotate(BlockState state, net.minecraft.world.level.block.Rotation rotation) {
        var d = state.getValue(OUTPUT).direction();
        return d == null || d.getAxis().isVertical() ? state : state.setValue(OUTPUT, ProviderOutput.of(rotation.rotate(d)));
    }

    @Override
    protected BlockState mirror(BlockState state, net.minecraft.world.level.block.Mirror mirror) {
        var d = state.getValue(OUTPUT).direction();
        return d == null || d.getAxis().isVertical() ? state : state.setValue(OUTPUT, ProviderOutput.of(mirror.mirror(d)));
    }

    @Override
    public int channelCost() {
        return 1;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new PatternProviderBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (!level.isClientSide() && type == ModBlockEntities.PATTERN_PROVIDER.get()) {
            return (lvl, p, st, be) -> PatternProviderBlockEntity.serverTick(lvl, p, st, (PatternProviderBlockEntity) be);
        }
        return null;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (level.getBlockEntity(pos) instanceof PatternProviderBlockEntity provider) {
            player.openMenu(new SimpleMenuProvider(
                    (id, inv, p) -> new PatternProviderMenu(id, inv, provider.getContainer(), provider.getUpgrades(), provider.menuData()),
                    Component.translatable("block.golem_logix.pattern_provider")));
        }
        return InteractionResult.CONSUME;
    }

    /** Replaced by another block (see RemovalAware). */
    @Override
    public void onRemoved(BlockState state, Level level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof PatternProviderBlockEntity provider) {
            for (int i = 0; i < PatternProviderBlockEntity.SLOTS; i++) {
                ItemStack s = provider.getContainer().getItem(i);
                if (!s.isEmpty()) {
                    Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, s.copy());
                }
            }
            Containers.dropContents(level, pos, provider.getUpgrades());
        }
    }
}
