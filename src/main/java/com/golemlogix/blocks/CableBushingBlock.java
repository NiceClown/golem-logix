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
 * Кабельная Муфта: «выход» каналов из Решётки Атланта наружу. Ёмкость линии задаёт достроенная решётка
 * (см. GolemNetwork), поэтому сама муфта лимита не хранит. ПКМ — сколько каналов занято на этой линии.
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
        player.displayClientMessage(describe(GolemNetwork.scan(level, pos), pos), false);
        return InteractionResult.CONSUME;
    }

    /** Текст «занято X из Y» для чата и подсказок. */
    public static Component describe(GolemNetwork net, BlockPos pos) {
        if (!net.hasCore()) {
            return Component.translatable("message.golem_logix.bushing.no_core");
        }
        return Component.translatable("message.golem_logix.bushing.line",
                net.loadAt(pos), net.lineCapacity(), net.channelsUsed(), net.channelCapacity());
    }
}
