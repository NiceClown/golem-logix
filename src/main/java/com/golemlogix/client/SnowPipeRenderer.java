package com.golemlogix.client;

import com.golemlogix.GolemLogixMod;
import com.golemlogix.blockentity.PipeBlockEntity;
import com.golemlogix.blocks.GolemProperties;
import com.golemlogix.blocks.SnowPipeBlock;
import com.golemlogix.client.render.Baked;
import com.golemlogix.client.render.NixieDigits;
import com.golemlogix.client.render.PixelMesh;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

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
    private static final Quaternionf ROT_LAMP_X = Axis.YP.rotationDegrees(-90f);
    private static final PoseStack LAMP_DIGIT_POSE = new PoseStack();
    private static final double WAREHOUSE_DECOR_SIGHT_SQ = 28.0 * 28.0;

    /** Pre-baked directional plugs, warehouse docks/interiors, and heat-lamp housings (rebuilt on resource reload). */
    private record PipeMeshes(TextureAtlasSprite atlasMark, BakedModel modelMark,
                              Baked[][] plugs, Baked[][] docks,
                              Baked[][] warehouseDecor, Baked[][] warehouseWarm,
                              Baked[][] lamps, Baked[][] lampGlass) {
    }

    private static PipeMeshes pipeMeshes;

    private static PipeMeshes pipeMeshes() {
        TextureAtlasSprite atlasMark = PixelMesh.sprite(MachineParts.BRASS);
        BakedModel modelMark = Minecraft.getInstance().getModelManager().getModel(PLUG_BASIC);
        if (pipeMeshes == null || pipeMeshes.atlasMark() != atlasMark || pipeMeshes.modelMark() != modelMark) {
            Baked[][] plugs = new Baked[2][6];
            Baked[][] docks = new Baked[2][6];
            Baked[][] warehouseDecor = new Baked[2][6];
            Baked[][] warehouseWarm = new Baked[2][6];
            Baked[][] lamps = new Baked[2][2];
            Baked[][] lampGlass = new Baked[2][2];
            for (int d = 0; d < 2; d++) {
                boolean dense = d == 1;
                ModelResourceLocation plugId = dense ? PLUG_DENSE : PLUG_BASIC;
                ModelResourceLocation dockId = dense ? DOCK_DENSE : DOCK_BASIC;
                ModelResourceLocation lampId = dense ? LAMP_DENSE : LAMP_BASIC;
                ModelResourceLocation glassId = dense ? LAMP_GLASS_DENSE : LAMP_GLASS_BASIC;
                for (int i = 0; i < DIRS.length; i++) {
                    Direction dir = DIRS[i];
                    plugs[d][i] = Baked.bake((vc, p) -> {
                        Quads.orient(p, dir);
                        Baked.drawModel(p.last(), vc, plugId);
                    });
                    docks[d][i] = Baked.bake((vc, p) -> {
                        Quads.orient(p, dir);
                        Baked.drawModel(p.last(), vc, dockId);
                    });
                    if (dir.getAxis().isHorizontal()) {
                        warehouseDecor[d][i] = Baked.bake((vc, p) -> {
                            Quads.orient(p, dir);
                            PipeWarehouseRenderer.bakeDecorations(p, vc, dense, false);
                        });
                        warehouseWarm[d][i] = Baked.bake((vc, p) -> {
                            Quads.orient(p, dir);
                            PipeWarehouseRenderer.bakeDecorations(p, vc, dense, true);
                        });
                    }
                }
                for (int ax = 0; ax < 2; ax++) {
                    boolean axisX = ax == 1;
                    lamps[d][ax] = Baked.bake((vc, p) -> {
                        if (axisX) {
                            p.translate(0.5f, 0.5f, 0.5f);
                            p.mulPose(ROT_LAMP_X);
                            p.translate(-0.5f, -0.5f, -0.5f);
                        }
                        Baked.drawModel(p.last(), vc, lampId);
                    });
                    lampGlass[d][ax] = Baked.bake((vc, p) -> {
                        if (axisX) {
                            p.translate(0.5f, 0.5f, 0.5f);
                            p.mulPose(ROT_LAMP_X);
                            p.translate(-0.5f, -0.5f, -0.5f);
                        }
                        Baked.drawModel(p.last(), vc, glassId);
                    });
                }
            }
            pipeMeshes = new PipeMeshes(atlasMark, modelMark, plugs, docks, warehouseDecor, warehouseWarm, lamps, lampGlass);
        }
        return pipeMeshes;
    }

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

    /** Clears pre-baked meshes on client resource reload. */
    public static void clearBaked() {
        pipeMeshes = null;
        PipeRunnerRenderer.clearBaked();
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
        renderPlugsAndWarehouses(be, state, pose, buffers, light, overlay);
        if (SnowPipeBlock.hasLamp(state, be.getBlockPos())) {
            renderHeatLamp(be, state, pose, buffers, light);
        }
        if (!state.hasProperty(GolemProperties.ACTIVE) || !state.getValue(GolemProperties.ACTIVE)) {
            return;
        }
        PipeRunnerRenderer.renderRunner(level, be.getBlockPos(), state, partialTick, pose, buffers, light, overlay);
    }

    /** Plugs and warehouse docks on closed sides and dead ends (pre-baked per orientation to avoid per-frame quad transforms). */
    private static void renderPlugsAndWarehouses(PipeBlockEntity be, BlockState state, PoseStack pose,
                                                 MultiBufferSource buffers, int light, int overlay) {
        int mask = terminalMask(be, state);
        if (mask == 0) {
            return;
        }
        boolean dense = state.getBlock() instanceof SnowPipeBlock p && p.isDense();
        int dIdx = dense ? 1 : 0;
        PipeMeshes meshes = pipeMeshes();
        RenderType cutout = RenderType.cutout();
        PoseStack.Pose current = pose.last();
        BlockPos pos = be.getBlockPos();
        Vec3 cam = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        boolean nearDecor = cam.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) < WAREHOUSE_DECOR_SIGHT_SQ;
        for (int i = 0; i < DIRS.length; i++) {
            if ((mask & (1 << i)) == 0) {
                continue;
            }
            Direction d = DIRS[i];
            boolean warehouse = be.getLevel() != null && PipeWarehouseRenderer.isWarehouse(be.getLevel(), pos, d);
            if (warehouse) {
                meshes.docks()[dIdx][i].draw(buffers, cutout, current, light, 0);
                if (nearDecor && meshes.warehouseDecor()[dIdx][i] != null) {
                    meshes.warehouseDecor()[dIdx][i].draw(buffers, cutout, current, light, 0);
                    meshes.warehouseWarm()[dIdx][i].draw(buffers, cutout, current, LightTexture.FULL_BRIGHT, 0);
                    PipeWarehouseRenderer.renderCornerChest(pose, d, buffers, dense, light, overlay);
                }
            } else {
                meshes.plugs()[dIdx][i].draw(buffers, cutout, current, light, 0);
            }
        }
    }

    /**
     * Heat lamp on a straight horizontal pipe (every sixth block, see {@link SnowPipeBlock#hasLamp}): two nixie tubes on a walnut
     * base showing channel load (cyan -> amber -> red). Mounted on the east side of N-S runs, south side of E-W runs.
     */
    private static void renderHeatLamp(PipeBlockEntity be, BlockState state, PoseStack pose, MultiBufferSource buffers, int light) {
        boolean dense = state.getBlock() instanceof SnowPipeBlock p && p.isDense();
        int dIdx = dense ? 1 : 0;
        boolean axisX = SnowPipeBlock.straightAxis(state) == Direction.Axis.X;
        int axIdx = axisX ? 1 : 0;
        PipeMeshes meshes = pipeMeshes();
        PoseStack.Pose basePose = pose.last();

        meshes.lamps()[dIdx][axIdx].draw(buffers, RenderType.cutout(), basePose, light, 0);

        PoseStack.Pose digitPose = basePose;
        if (axisX) {
            digitPose = LAMP_DIGIT_POSE.last();
            digitPose.pose().set(basePose.pose()).translate(0.5f, 0.5f, 0.5f).rotate(ROT_LAMP_X).translate(-0.5f, -0.5f, -0.5f);
            digitPose.normal().set(basePose.normal()).rotate(ROT_LAMP_X);
        }

        // the digits face east, in front of the tubes' mesh planes; seen from there the tens tube is on the left (south)
        int load = Math.min(99, be.lampLoad());
        int color = NixieDigits.loadColor(load, be.lampCapacity());
        // Outside the translucent glass wall (x = 16.2 / 18.3) so 1.21.1 translucent glass sorting doesn't swallow emissive digits
        float x = (dense ? 14.7f : 12.6f) + 3.65f;
        VertexConsumer nixie = NixieDigits.buffer(buffers);
        if (load >= 10) {
            NixieDigits.digit(digitPose, nixie, load / 10, x, 8f, 9.35f, 2.2f, 4.4f, 0f, 0f, -1f, 0f, 1f, 0f, color);
        }
        NixieDigits.digit(digitPose, nixie, load % 10, x, 8f, 6.65f, 2.2f, 4.4f, 0f, 0f, -1f, 0f, 1f, 0f, color);
        // under Iris/Oculus shader packs, translucent glass can wash out or hide emissive digits behind it: skip glass then
        if (!JarWholeRenderer.shaderPackActive()) {
            meshes.lampGlass()[dIdx][axIdx].draw(buffers, RenderType.translucent(), basePose, light, 0);
        }
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
