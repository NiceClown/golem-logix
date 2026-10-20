package com.golemlogix.items;

import com.golemlogix.init.ModDataComponents;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.ChatFormatting;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Aetherial Linkage card: in the Write slot of a Cryo-Source Telegraph it records that telegraph (its place and its current seal);
 * in the Memory slot of another telegraph it links that one to the recorded telegraph. A blank card records nothing.
 */
public class TelegraphLinkCardItem extends Item {
    /** What a written card holds: where the telegraph stands and its seal (a Reset gives the telegraph a new one). */
    public record Link(GlobalPos target, int seal) {
        public static final Codec<Link> CODEC = RecordCodecBuilder.create(i -> i.group(
                GlobalPos.CODEC.fieldOf("target").forGetter(Link::target),
                Codec.INT.fieldOf("seal").forGetter(Link::seal)).apply(i, Link::new));
        public static final StreamCodec<ByteBuf, Link> STREAM_CODEC = StreamCodec.composite(
                GlobalPos.STREAM_CODEC, Link::target, ByteBufCodecs.INT, Link::seal, Link::new);
    }

    public TelegraphLinkCardItem(Properties properties) {
        super(properties);
    }

    @Nullable
    public static Link link(ItemStack stack) {
        return stack.getItem() instanceof TelegraphLinkCardItem ? stack.get(ModDataComponents.TELEGRAPH_LINK.get()) : null;
    }

    public static boolean isCard(ItemStack stack) {
        return stack.getItem() instanceof TelegraphLinkCardItem;
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return link(stack) != null;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, net.minecraft.world.item.component.TooltipDisplay display,
                                java.util.function.Consumer<Component> lines, TooltipFlag flag) {
        List<Component> tooltip = new com.golemlogix.items.TooltipLines(lines);
        Link link = link(stack);
        if (link == null) {
            tooltip.add(Component.translatable("tooltip.golem_logix.telegraph_link.blank").withStyle(ChatFormatting.GRAY));
        } else {
            tooltip.add(Component.translatable("tooltip.golem_logix.telegraph_link.target", link.target().pos().getX(),
                    link.target().pos().getY(), link.target().pos().getZ()).withStyle(ChatFormatting.AQUA));
            tooltip.add(Component.literal(link.target().dimension().identifier().toString()).withStyle(ChatFormatting.DARK_GRAY));
        }
        tooltip.add(Component.translatable("tooltip.golem_logix.telegraph_link.hint").withStyle(ChatFormatting.DARK_GRAY));
    }
}
