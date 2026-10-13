package com.golemlogix.blocks;

import com.golemlogix.blockentity.AtlasCoreBlockEntity;
import com.golemlogix.init.ModBlockEntities;
import com.golemlogix.system.AtlasPart;
import com.golemlogix.system.AtlasStructure;
import com.golemlogix.system.GolemNetwork;
import com.golemlogix.system.GolemNode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
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

public class AtlasCoreBlock extends Block implements GolemNode, EntityBlock, AtlasPart {
    public AtlasCoreBlock(Properties properties) {
        // частичная модель (пьедестал и кристалл): без noOcclusion блок считался бы глухим кубом и его грани были бы чёрными
        super(properties.sound(SoundType.ANVIL).strength(6.0f).requiresCorrectToolForDrops().noOcclusion().lightLevel(state -> 10));
    }

    /** Ядро связано только с частями решётки: устройства подключаются через Кабельные Муфты. */
    @Override
    public boolean canLink(BlockState other) {
        return other.getBlock() instanceof AtlasPart;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new AtlasCoreBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (!level.isClientSide() && type == ModBlockEntities.ATLAS_CORE.get()) {
            return (lvl, p, st, be) -> AtlasCoreBlockEntity.serverTick(lvl, p, st, (AtlasCoreBlockEntity) be);
        }
        return null;
    }

    /** ПКМ по ядру — сводка сети; если решётка не достроена, показывает, чего не хватает. */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (level.getBlockEntity(pos) instanceof AtlasCoreBlockEntity core) {
            core.refresh(level, pos);
            GolemNetwork net = GolemNetwork.scan(level, pos);
            AtlasStructure.Result st = net.structure();
            String sourceText = core.isInfiniteSource() ? "∞" : String.valueOf(core.getSourcePerSecond());
            if (core.isDuplicate()) {
                player.displayClientMessage(Component.translatable("message.golem_logix.core.duplicate"), false);
            } else if (st != null && !st.valid()) {
                BlockPos m = st.missingPos();
                player.displayClientMessage(Component.translatable("message.golem_logix.core.unformed",
                        st.expected().getName(), m.getX(), m.getY(), m.getZ(), GolemNetwork.BARE_CORE_CHANNELS), false);
                if (player instanceof ServerPlayer sp && level instanceof ServerLevel sl) {
                    sl.sendParticles(sp, ParticleTypes.END_ROD, true, m.getX() + 0.5, m.getY() + 0.5, m.getZ() + 0.5,
                            18, 0.3, 0.3, 0.3, 0.02);
                }
            } else {
                player.displayClientMessage(Component.translatable(
                        core.isPowered() ? "message.golem_logix.core.formed_on" : "message.golem_logix.core.formed_off",
                        net.channelsUsed(), net.channelCapacity(), net.lineCapacity(), net.deviceCount(), sourceText,
                        st == null ? 0 : st.expanders(), st == null ? 0 : st.quantumExpanders()), false);
            }
        }
        return InteractionResult.CONSUME;
    }
}