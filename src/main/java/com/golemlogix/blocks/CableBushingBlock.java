package com.golemlogix.blocks;

import com.golemlogix.system.AtlasPart;
import com.golemlogix.system.GolemNetwork;
import com.golemlogix.system.GolemNode;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Cable Bushing: the "exit" of channels out of the Atlas Array. The line capacity is set by the completed array
 * (see GolemNetwork), so the bushing itself stores no limit. Right-click shows how many channels this line uses.
 */
public class CableBushingBlock extends Block implements GolemNode, AtlasPart {
    public CableBushingBlock(Properties properties) {
        super(properties.sound(SoundType.COPPER).strength(4.0f).requiresCorrectToolForDrops());
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        player.sendSystemMessage(describe(GolemNetwork.scan(level, pos), pos));
        return InteractionResult.CONSUME;
    }

    /** The text "X of Y used" for chat and tooltips. */
    public static Component describe(GolemNetwork net, BlockPos pos) {
        if (!net.hasCore()) {
            return Component.translatable("message.golem_logix.bushing.no_core");
        }
        return Component.translatable("message.golem_logix.bushing.line",
                net.loadAt(pos), net.lineCapacity(), net.channelsUsed(), net.channelCapacity());
    }
}
