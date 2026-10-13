package com.golemlogix.client.ghost;

import com.golemlogix.GolemLogixMod;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * Игровая шина клиента: тик проекции, горячие клавиши и отрисовка призрачных блоков в мире.
 */
@EventBusSubscriber(modid = GolemLogixMod.MOD_ID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public class GhostEvents {

    /**
     * Нажатие клавиши приходит сюда сразу после того, как игра отметила нажатие во всех привязках с этой клавишей,
     * но раньше, чем их опросят в тике. Поэтому, пока проекция включена, мы сами выполняем действие
     * и стираем нажатие у чужих привязок на той же клавише: приоритет всегда у нашего Y/N.
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onKey(InputEvent.Key event) {
        Minecraft mc = Minecraft.getInstance();
        if (event.getAction() != GLFW.GLFW_PRESS || mc.screen != null || mc.level == null || !GhostProjection.isActive()) {
            return;
        }
        KeyMapping hit = null;
        if (GolemKeys.GHOST_CONFIRM.matches(event.getKey(), event.getScanCode())) {
            hit = GolemKeys.GHOST_CONFIRM;
        } else if (GolemKeys.GHOST_CANCEL.matches(event.getKey(), event.getScanCode())) {
            hit = GolemKeys.GHOST_CANCEL;
        }
        if (hit == null || !hit.getKeyModifier().isActive(hit.getKeyConflictContext())) {
            return;
        }
        // отобрать нажатие у других привязок на той же клавише (в том числе из других модов)
        for (KeyMapping other : mc.options.keyMappings) {
            if (other != hit && other != GolemKeys.GHOST_CONFIRM && other != GolemKeys.GHOST_CANCEL
                    && other.getKey().equals(hit.getKey())) {
                while (other.consumeClick()) {
                    // сбросить счётчик нажатий
                }
                other.setDown(false);
            }
        }
        // чтобы наши привязки не сработали повторно в тике
        while (hit.consumeClick()) {
            // сбросить счётчик нажатий
        }
        if (hit == GolemKeys.GHOST_CONFIRM) {
            GhostProjection.toggleLock();
        } else {
            GhostProjection.clear();
        }
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen == null && !GhostProjection.isActive()) {
            // проекция выключена: опустошаем счётчики, чтобы старые нажатия не «выстрелили» позже
            while (GolemKeys.GHOST_CONFIRM.consumeClick()) {
                // ничего
            }
            while (GolemKeys.GHOST_CANCEL.consumeClick()) {
                // ничего
            }
        }
        GhostProjection.tick(mc);
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS || !GhostProjection.isActive()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || GhostProjection.anchor() == null) {
            return;
        }
        List<GhostProjection.GhostCell> cells = GhostProjection.missing(mc.level);
        if (cells.isEmpty()) {
            return;
        }
        PoseStack pose = event.getPoseStack();
        Vec3 cam = event.getCamera().getPosition();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        float pulse = 0.5f + 0.12f * (float) Math.sin((mc.level.getGameTime() + event.getPartialTick().getGameTimeDeltaPartialTick(false)) * 0.12);

        for (GhostProjection.GhostCell cell : cells) {
            // ожидаемый блок — холодно-бирюзовый, мешающий чужой блок — красный
            float alpha = cell.wrong() ? 0.6f : pulse;
            float r = cell.wrong() ? 1.6f : 0.75f;
            float g = cell.wrong() ? 0.45f : 1.15f;
            float b = cell.wrong() ? 0.45f : 1.25f;
            MultiBufferSource ghostBuffers = renderType -> new GhostVertexConsumer(buffers.getBuffer(RenderType.translucent()), alpha, r, g, b);
            pose.pushPose();
            pose.translate(cell.pos().getX() - cam.x, cell.pos().getY() - cam.y, cell.pos().getZ() - cam.z);
            mc.getBlockRenderer().renderSingleBlock(cell.state(), pose, ghostBuffers, 0xF000F0, OverlayTexture.NO_OVERLAY,
                    ModelData.EMPTY, RenderType.translucent());
            pose.popPose();
        }
        buffers.endBatch(RenderType.translucent());
    }
}