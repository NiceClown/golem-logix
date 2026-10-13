package com.golemlogix.client;

import com.golemlogix.blockentity.RecipeMonitorBlockEntity;
import com.golemlogix.blocks.RecipeMonitorBlock;
import com.golemlogix.init.ModItems;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * Монитор Рецептов: в фиолетовом вихре иллюминатора по кругу плывут перфокарты — по одной на каждую идущую в сети задачу
 * (до шести), как карточки в окне портала на референсе. Карты появляются и исчезают плавно.
 */
public class RecipeMonitorRenderer implements BlockEntityRenderer<RecipeMonitorBlockEntity> {
    /** Центр иллюминатора на фасаде (пиксели модели) и радиус орбиты карт. */
    private static final float CX = 8f, CY = 8.5f, RADIUS = 3.6f;

    private ItemStack card;

    public RecipeMonitorRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(RecipeMonitorBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        if (be.getLevel() == null) {
            return;
        }
        lampGolem(be, partialTick, pose, buffers, light);
        float shown = Mth.lerp(partialTick, be.shownCardsPrev, be.shownCards);
        if (shown < 0.02f) {
            return;
        }
        if (card == null) {
            card = new ItemStack(ModItems.COPPER_PUNCH_CARD.get());
        }
        float time = be.getLevel().getGameTime() + partialTick;
        int count = (int) Math.ceil(shown - 0.001f);
        pose.pushPose();
        Quads.faceModel(pose, be.getBlockState().getValue(RecipeMonitorBlock.FACING));
        for (int i = 0; i < count; i++) {
            // последняя карта «проявляется» по мере роста shown
            float alive = Math.min(1f, shown - i);
            float a = (float) Math.toRadians(time * 2.4f + i * 360f / Math.max(1, count));
            float x = CX + Mth.cos(a) * RADIUS, y = CY + Mth.sin(a) * RADIUS;
            pose.pushPose();
            // перед вихрем (он утоплен в рамку), «север» модели — фасад
            pose.translate(1f - x / 16f, y / 16f, 0.9f / 16f);
            pose.mulPose(Axis.YP.rotationDegrees(180f + Mth.sin(time * 0.08f + i) * 25f));
            pose.mulPose(Axis.ZP.rotationDegrees(Mth.sin(time * 0.05f + i * 1.7f) * 20f));
            float s = 0.2f * alive;
            pose.scale(s, s, s);
            Minecraft.getInstance().getItemRenderer().renderStatic(card, ItemDisplayContext.FIXED, LightTexture.FULL_BRIGHT,
                    OverlayTexture.NO_OVERLAY, pose, buffers, be.getLevel(), i);
            pose.popPose();
        }
        pose.popPose();
    }

    /**
     * Медный голем-фонарщик на крыше: в простое лампа ровно светится фиолетовым, а голем бродит по площадке; пока идёт крафт —
     * стоит посередине, лампа мигает зелёным; авария или нет связи — горит красным.
     */
    private void lampGolem(RecipeMonitorBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        pose.pushPose();
        Quads.faceModel(pose, be.getBlockState().getValue(RecipeMonitorBlock.FACING));
        LampGolem.render(pose, buffers, light, be.walker, partialTick, be.lampState(), be.getLevel().getGameTime() + partialTick, 16f, 16.4f);
        pose.popPose();
    }
}