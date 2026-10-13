package com.golemlogix.integration.jade;

import com.golemlogix.GolemLogixMod;
import com.golemlogix.blockentity.PipeBlockEntity;
import com.golemlogix.blocks.AtlasCoreBlock;
import com.golemlogix.blocks.CableBushingBlock;
import com.golemlogix.blocks.ChannelExpanderBlock;
import com.golemlogix.blocks.SnowPipeBlock;
import com.golemlogix.system.GolemNetwork;
import com.golemlogix.system.GolemNode;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

import java.util.ArrayList;
import java.util.List;

/**
 * Каналы в подсказке Jade для всех узлов сети мода (кроме ядра и муфты — у них свои строки): у устройства — есть ли у него
 * канал и почему нет; у трубы — сколько каналов через неё идёт и какие стороны закрыты заглушками; у расширителя — загрузка
 * его выхода. Сеть считается на клиенте и запоминается на секунду.
 */
public enum ChannelProvider implements IBlockComponentProvider {
    INSTANCE;

    private static final ResourceLocation UID = ResourceLocation.fromNamespaceAndPath(GolemLogixMod.MOD_ID, "channels");
    private BlockPos cachedPos;
    private long cachedTime = Long.MIN_VALUE;
    private List<Component> cached = List.of();

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        BlockState state = accessor.getBlockState();
        if (!(state.getBlock() instanceof GolemNode node) || state.getBlock() instanceof AtlasCoreBlock
                || state.getBlock() instanceof CableBushingBlock
                || !GolemLogixMod.MOD_ID.equals(net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock()).getNamespace())) {
            return;
        }
        BlockPos pos = accessor.getPosition();
        long now = accessor.getLevel().getGameTime();
        if (!pos.equals(cachedPos) || now - cachedTime > 20 || now < cachedTime) {
            cached = describe(GolemNetwork.scan(accessor.getLevel(), pos), accessor, node, state, pos);
            cachedPos = pos.immutable();
            cachedTime = now;
        }
        cached.forEach(tooltip::add);
    }

    private static List<Component> describe(GolemNetwork net, BlockAccessor accessor, GolemNode node, BlockState state, BlockPos pos) {
        List<Component> out = new ArrayList<>();
        int cost = node.channelCost(state);
        if (state.getBlock() instanceof SnowPipeBlock pipe) {
            if (net.hasCore()) {
                out.add(Component.translatable("jade.golem_logix.pipe", net.loadAt(pos), pipe.getMaxChannels()));
            }
            if (accessor.getBlockEntity() instanceof PipeBlockEntity be && be.plugMask() != 0) {
                MutableComponent sides = Component.empty();
                boolean first = true;
                for (Direction d : Direction.values()) {
                    if (be.isPlugged(d)) {
                        if (!first) {
                            sides.append(", ");
                        }
                        sides.append(Component.translatable("direction.golem_logix." + d.getName()));
                        first = false;
                    }
                }
                out.add(Component.translatable("jade.golem_logix.plugs", sides).withStyle(ChatFormatting.AQUA));
            }
            return out;
        }
        if (state.getBlock() instanceof ChannelExpanderBlock) {
            out.add(Component.translatable("jade.golem_logix.expander", net.loadAt(pos), net.exitCapacity(pos)));
            return out;
        }
        if (cost <= 0) {
            return out;
        }
        if (!net.hasCore()) {
            out.add(Component.translatable("jade.golem_logix.device.no_core").withStyle(ChatFormatting.RED));
        } else if (!net.isOnline()) {
            out.add(Component.translatable("jade.golem_logix.device.no_power").withStyle(ChatFormatting.RED));
        } else if (net.isDeviceActive(pos)) {
            out.add(Component.translatable("jade.golem_logix.device.ok", cost, net.channelsUsed(), net.channelCapacity())
                    .withStyle(ChatFormatting.GREEN));
        } else if (net.channelsUsed() + cost > net.channelCapacity()) {
            out.add(Component.translatable("jade.golem_logix.device.full", net.channelsUsed(), net.channelCapacity())
                    .withStyle(ChatFormatting.RED));
        } else {
            out.add(Component.translatable("jade.golem_logix.device.path").withStyle(ChatFormatting.RED));
        }
        return out;
    }

    @Override
    public ResourceLocation getUid() {
        return UID;
    }
}
