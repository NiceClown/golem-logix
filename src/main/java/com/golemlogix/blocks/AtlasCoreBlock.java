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
        // partial model (pedestal and crystal): without noOcclusion the block would count as a solid cube and its faces would be black
        super(properties.sound(SoundType.ANVIL).strength(6.0f).requiresCorrectToolForDrops().noOcclusion().lightLevel(state -> 10));
    }

    /**
     * The core links to parts of the array, and through its bottom face to anything: in a tower or an array a conduit stands there
     * (devices connect through Cable Bushings), while a core alone takes a pipe or a device under it (8 channels). Never sideways or
     * upwards: a pipe there would go round the bushings' lines.
     */
    @Override
    public boolean canLink(BlockState self, BlockState other, net.minecraft.core.Direction toOther) {
        return toOther == net.minecraft.core.Direction.DOWN || other.getBlock() instanceof AtlasPart;
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

    /** Right-click on the core shows a network summary; if the array is not complete, it shows what is missing. */
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
                // the core alone, or on a starter tower: what it gives now, and the next block the whole array needs
                player.displayClientMessage(Component.translatable(net.isTower() ? "message.golem_logix.core.tower" : "message.golem_logix.core.unformed",
                        net.channelsUsed(), net.channelCapacity(), st.expected().getName(), m.getX(), m.getY(), m.getZ()), false);
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