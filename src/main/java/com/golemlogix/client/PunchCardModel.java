package com.golemlogix.client;

import com.golemlogix.data.PatternData;
import com.golemlogix.init.ModDataComponents;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.ItemModel;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemModels;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.resources.model.ResolvableModel;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4fc;

/**
 * Item model "golem_logix:punch_card" of a written punch card: while Shift is held, what the card makes is drawn instead of the card
 * (like AE2 patterns), in the inventory, in a provider, in the hand. A blank card and a card without Shift are drawn by the base model.
 */
public record PunchCardModel(ItemModel base) implements ItemModel {
    /** Only for DevShots screenshots: behave as if Shift were held. */
    public static boolean devShift = false;

    @Override
    public void update(ItemStackRenderState output, ItemStack stack, ItemModelResolver resolver, ItemDisplayContext context,
                       @Nullable ClientLevel level, @Nullable ItemOwner owner, int seed) {
        PatternData data = stack.get(ModDataComponents.PATTERN.get());
        boolean shift = Minecraft.getInstance().hasShiftDown() || devShift;
        // the GUI caches drawn items by what makes them up: the Shift state is part of it
        output.appendModelIdentityElement(shift);
        if (data != null && shift) {
            ItemStack out = data.primaryOutput();
            if (!out.isEmpty() && !out.is(stack.getItem())) {
                resolver.appendItemLayers(output, out, context, level, owner, seed);
                return;
            }
        }
        base.update(output, stack, resolver, context, level, owner, seed);
    }

    public record Unbaked(ItemModel.Unbaked base) implements ItemModel.Unbaked {
        public static final MapCodec<Unbaked> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                ItemModels.CODEC.fieldOf("base").forGetter(Unbaked::base)
        ).apply(i, Unbaked::new));

        @Override
        public MapCodec<Unbaked> type() {
            return MAP_CODEC;
        }

        @Override
        public void resolveDependencies(ResolvableModel.Resolver resolver) {
            base.resolveDependencies(resolver);
        }

        @Override
        public ItemModel bake(ItemModel.BakingContext context, Matrix4fc transformation) {
            return new PunchCardModel(base.bake(context, transformation));
        }
    }
}
