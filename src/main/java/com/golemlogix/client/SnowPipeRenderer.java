package com.golemlogix.client;

import com.golemlogix.GolemLogixMod;
import com.golemlogix.blockentity.PipeBlockEntity;
import com.golemlogix.blocks.GolemProperties;
import com.golemlogix.blocks.SnowPipeBlock;
import com.golemlogix.client.render.NixieDigits;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.model.data.ModelData;

import java.util.ArrayList;
import java.util.List;

/**
 * Block-entity renderer for Snow Pipes:
 * <ul>
 * <li>End plugs and Victorian warehouse docks ({@link PipeWarehouseRenderer}) on plugged sides and dead ends.</li>
 * <li>Channel heat lamps with glowing nixie digits on straight horizontal runs (every sixth block).</li>
 * <li>Snowman couriers and hanging copper-golem delivery arms running through powered networks ({@link PipeRunnerRenderer},
 * routed by {@link PipeRunners}).</li>
 * </ul>
 */
public class SnowPipeRenderer implements BlockEntityRenderer<PipeBlockEntity> {
    public static final ModelResourceLocation PLUG_BASIC = plugModel("basic_snow_pipe_plug");
    public static final ModelResourceLocation PLUG_DENSE = plugModel("dense_frost_pipe_plug");
    /** Warehouse dock door: a dead end elected as a warehouse is closed by a roller shutter with a lamp rather than a plug. */
    public static final ModelResourceLocation DOCK_BASIC = plugModel("basic_snow_pipe_dock");
    public static final ModelResourceLocation DOCK_DENSE = plugModel("dense_frost_pipe_dock");
    /** Heat lamp: baked body (cutout) and glass (translucent), drawn every sixth block of a straight horizontal pipe. */
    public static final ModelResourceLocation LAMP_BASIC = plugModel("basic_snow_pipe_lamp");
    public static final ModelResourceLocation LAMP_DENSE = plugModel("dense_frost_pipe_lamp");
    public static final ModelResourceLocation LAMP_GLASS_BASIC = plugModel("basic_snow_pipe_lamp_glass");
    public static final ModelResourceLocation LAMP_GLASS_DENSE = plugModel("dense_frost_pipe_lamp_glass");

    static final Direction[] DIRS = Direction.values();

    public SnowPipeRenderer(BlockEntityRendererProvider.Context context) {
    }

    private static ModelResourceLocation plugModel(String name) {
        return ModelResourceLocation.standalone(
                ResourceLocation.fromNamespaceAndPath(GolemLogixMod.MOD_ID, "block/" + name));
    }

    @Override
    public int getViewDistance() {
        return 64;
    }

    @Override
    public boolean shouldRender(PipeBlockEntity be, Vec3 cameraPosition) {
        if (!BlockEntityRenderer.super.shouldRender(be, cameraPosition)) {
            return false;
        }
        Level level = be.getLevel();
        if (level == null) {
            return false;
        }
        BlockState state = be.getBlockState();
        if (terminalMask(be, state) != 0 || SnowPipeBlock.hasLamp(state, be.getBlockPos())) {
            return true;
        }
        return state.hasProperty(GolemProperties.ACTIVE)
                && state.getValue(GolemProperties.ACTIVE)
                && PipeRunners.mayVisit(level, be.getBlockPos(), level.getGameTime());
    }

    /** Clears static client-side pipe caches (warehouse election and cargo samples) on world/dimension unload. */
    public static void clearCaches() {
        PipeWarehouseRenderer.clearCache();
        PipeRunnerRenderer.clearCache();
    }

    /** Bitmask of terminal directions ({@code 1 << dir.ordinal()}); avoids list/array allocations per pipe. */
    static int terminalMask(PipeBlockEntity be, BlockState state) {
        if (!(state.getBlock() instanceof SnowPipeBlock)) {
            return 0;
        }
        int mask = 0;
        int pipeLinks = 0;
        Direction singlePipeDir = null;
        int anyLinks = 0;
        Direction onlyLinkDir = null;
        for (int i = 0; i < DIRS.length; i++) {
            Direction d = DIRS[i];
            if (be.isPlugged(d)) {
                mask |= 1 << i;
            }
            SnowPipeBlock.Link link = SnowPipeBlock.link(state, d);
            if (link == SnowPipeBlock.Link.PIPE) {
                pipeLinks++;
                singlePipeDir = d;
            }
            if (link != SnowPipeBlock.Link.NONE) {
                anyLinks++;
                onlyLinkDir = d;
            }
        }
        if (pipeLinks == 1) {
            Direction deadEnd = singlePipeDir.getOpposite();
            if (SnowPipeBlock.link(state, deadEnd) == SnowPipeBlock.Link.NONE) {
                mask |= 1 << deadEnd.ordinal();
            }
        } else if (pipeLinks == 0 && anyLinks == 1) {
            // a single pipe: a plug only on the blind end, if it has one exit (otherwise it would close off a device)
            Direction deadEnd = onlyLinkDir.getOpposite();
            if (SnowPipeBlock.link(state, deadEnd) == SnowPipeBlock.Link.NONE) {
                mask |= 1 << deadEnd.ordinal();
            }
        }
        return mask;
    }

    /**
     * Returns the dead-end / terminal directions of this pipe:
     * <ol>
     * <li>Any side with an installed plug ({@code isPlugged}).</li>
     * <li>Any open dead end of a pipe run (exactly 1 {@code PIPE} link with {@code NONE} opposite).</li>
     * <li>The blind end opposite a single device link on a lone pipe.</li>
     * </ol>
     */
    public static List<Direction> getTerminalDirections(PipeBlockEntity be, BlockState state) {
        int mask = terminalMask(be, state);
        List<Direction> terminals = new ArrayList<>(2);
        for (int i = 0; i < DIRS.length; i++) {
            if ((mask & (1 << i)) != 0) {
                terminals.add(DIRS[i]);
            }
        }
        return terminals;
    }

    /** Whether this horizontal pipe dead end is elected as a Victorian warehouse (at most one within 3 blocks). */
    public static boolean isWarehouse(Level level, BlockPos pos, Direction dir) {
        return PipeWarehouseRenderer.isWarehouse(level, pos, dir);
    }

    @Override
    public void render(PipeBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        Level level = be.getLevel();
        if (level == null) {
            return;
        }
        BlockState state = be.getBlockState();
        renderPlugsAndWarehouses(be, state, pose, buffers, light);
        if (SnowPipeBlock.hasLamp(state, be.getBlockPos())) {
            renderHeatLamp(be, state, pose, buffers, light);
        }
        if (!state.hasProperty(GolemProperties.ACTIVE) || !state.getValue(GolemProperties.ACTIVE)) {
            return;
        }
        PipeRunnerRenderer.renderRunner(level, be.getBlockPos(), state, partialTick, pose, buffers, light, overlay);
    }

    /** Plugs and warehouse docks on closed sides and dead ends (north-facing standalone model oriented to each side). */
    private static void renderPlugsAndWarehouses(PipeBlockEntity be, BlockState state, PoseStack pose, MultiBufferSource buffers, int light) {
        int mask = terminalMask(be, state);
        if (mask == 0) {
            return;
        }
        boolean dense = state.getBlock() instanceof SnowPipeBlock p && p.isDense();
        var mc = Minecraft.getInstance();
        BakedModel plugModel = null;
        BakedModel dockModel = null;
        var dispatcher = mc.getBlockRenderer();
        var renderer = dispatcher.getModelRenderer();
        for (int i = 0; i < DIRS.length; i++) {
            if ((mask & (1 << i)) == 0) {
                continue;
            }
            Direction d = DIRS[i];
            boolean warehouse = be.getLevel() != null && PipeWarehouseRenderer.isWarehouse(be.getLevel(), be.getBlockPos(), d);
            BakedModel model;
            if (warehouse) {
                if (dockModel == null) {
                    dockModel = mc.getModelManager().getModel(dense ? DOCK_DENSE : DOCK_BASIC);
                }
                model = dockModel;
            } else {
                if (plugModel == null) {
                    plugModel = mc.getModelManager().getModel(dense ? PLUG_DENSE : PLUG_BASIC);
                }
                model = plugModel;
            }
            pose.pushPose();
            Quads.orient(pose, d);
            renderer.renderModel(pose.last(), buffers.getBuffer(RenderType.cutout()), null, model, 1f, 1f, 1f, light,
                    OverlayTexture.NO_OVERLAY, ModelData.EMPTY, RenderType.cutout());
            if (warehouse) {
                PipeWarehouseRenderer.renderDecorations(pose, dispatcher, buffers, dense, light);
            }
            pose.popPose();
        }
    }

    /**
     * Heat lamp on a straight horizontal pipe (every sixth block, see {@link SnowPipeBlock#hasLamp}): two nixie tubes on a walnut
     * base showing channel load (cyan -> amber -> red). Mounted on the east side of N-S runs, south side of E-W runs.
     */
    private static void renderHeatLamp(PipeBlockEntity be, BlockState state, PoseStack pose, MultiBufferSource buffers, int light) {
        boolean dense = state.getBlock() instanceof SnowPipeBlock p && p.isDense();
        var mc = Minecraft.getInstance();
        var renderer = mc.getBlockRenderer().getModelRenderer();
        BakedModel lampModel = mc.getModelManager().getModel(dense ? LAMP_DENSE : LAMP_BASIC);
        pose.pushPose();
        if (SnowPipeBlock.straightAxis(state) == Direction.Axis.X) {
            pose.translate(0.5, 0.5, 0.5);
            pose.mulPose(Axis.YP.rotationDegrees(-90));
            pose.translate(-0.5, -0.5, -0.5);
        }
        renderer.renderModel(pose.last(), buffers.getBuffer(RenderType.cutout()), null, lampModel, 1f, 1f, 1f, light,
                OverlayTexture.NO_OVERLAY, ModelData.EMPTY, RenderType.cutout());
        // the digits face east, in front of the tubes' mesh planes; seen from there the tens tube is on the left (south)
        int load = Math.min(99, be.lampLoad());
        int color = NixieDigits.loadColor(load, be.lampCapacity());
        // Outside the translucent glass wall (x = 16.2 / 18.3) so 1.21.1 translucent glass sorting doesn't swallow emissive digits
        float x = (dense ? 14.7f : 12.6f) + 3.65f;
        VertexConsumer nixie = NixieDigits.buffer(buffers);
        if (load >= 10) {
            NixieDigits.digit(pose.last(), nixie, load / 10, x, 8f, 9.35f, 2.2f, 4.4f, 0f, 0f, -1f, 0f, 1f, 0f, color);
        }
        NixieDigits.digit(pose.last(), nixie, load % 10, x, 8f, 6.65f, 2.2f, 4.4f, 0f, 0f, -1f, 0f, 1f, 0f, color);
        // under Iris/Oculus shader packs, translucent glass can wash out or hide emissive digits behind it: skip glass then
        if (!JarWholeRenderer.shaderPackActive()) {
            BakedModel glassModel = mc.getModelManager().getModel(dense ? LAMP_GLASS_DENSE : LAMP_GLASS_BASIC);
            renderer.renderModel(pose.last(), buffers.getBuffer(RenderType.translucent()), null, glassModel, 1f, 1f, 1f, light,
                    OverlayTexture.NO_OVERLAY, ModelData.EMPTY, RenderType.translucent());
        }
        pose.popPose();
    }

    /** Renders a cube block of {@code size} centred at {@code (x, y, z)} in the current pose frame. */
    static void renderBlockBox(PoseStack pose, BlockRenderDispatcher dispatcher, MultiBufferSource buffers, BlockState state,
                               double x, double y, double z, float size, int light, int overlay) {
        renderBlockBox(pose, dispatcher, buffers, state, x, y, z, size, size, size, light, overlay);
    }

    /** Renders a block scaled to {@code (sx, sy, sz)} centred at {@code (x, y, z)} in the current pose frame. */
    static void renderBlockBox(PoseStack pose, BlockRenderDispatcher dispatcher, MultiBufferSource buffers, BlockState state,
                               double x, double y, double z, float sx, float sy, float sz, int light, int overlay) {
        pose.pushPose();
        pose.translate(x, y, z);
        pose.scale(sx, sy, sz);
        pose.translate(-0.5, -0.5, -0.5);
        dispatcher.renderSingleBlock(state, pose, buffers, light, overlay);
        pose.popPose();
    }
}
