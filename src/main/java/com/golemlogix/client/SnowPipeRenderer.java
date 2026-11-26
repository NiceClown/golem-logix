package com.golemlogix.client;

import com.golemlogix.blockentity.PipeBlockEntity;
import com.golemlogix.blocks.GolemProperties;
import com.golemlogix.blocks.SnowPipeBlock;
import com.golemlogix.client.render.BerBuffers;
import com.golemlogix.client.render.GlowTypes;
import com.golemlogix.client.render.LegacyBer;
import com.golemlogix.client.render.NixieDigits;
import com.golemlogix.client.render.Sprites;
import com.golemlogix.client.render.StandaloneModels;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
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
public class SnowPipeRenderer extends LegacyBer<PipeBlockEntity> {
    public static final Identifier PLUG_BASIC = StandaloneModels.PLUG_BASIC;
    public static final Identifier PLUG_DENSE = StandaloneModels.PLUG_DENSE;
    /** Warehouse dock door: a dead end elected as a warehouse is closed by a roller shutter with a lamp rather than a plug. */
    public static final Identifier DOCK_BASIC = StandaloneModels.DOCK_BASIC;
    public static final Identifier DOCK_DENSE = StandaloneModels.DOCK_DENSE;

    static final Direction[] DIRS = Direction.values();
    private static final Quaternionf ROT_LAMP_X = Axis.YP.rotationDegrees(-90f);
    private static final PoseStack LAMP_DIGIT_POSE = new PoseStack();
    private static final double WAREHOUSE_DECOR_SIGHT_SQ = 28.0 * 28.0;

    /** Pre-baked directional plugs, warehouse docks/interiors, and heat-lamp housings (rebuilt on resource reload). */
    private record PipeMeshes(TextureAtlasSprite atlasMark, List<BakedQuad> modelMark,
                              BerBuffers.Baked[][] plugs, BerBuffers.Baked[][] docks,
                              BerBuffers.Baked[][] warehouseDecor, BerBuffers.Baked[][] warehouseWarm,
                              BerBuffers.Baked[][] lamps, BerBuffers.Baked[][] lampGlass) {
    }

    private static PipeMeshes pipeMeshes;

    private static PipeMeshes pipeMeshes() {
        TextureAtlasSprite atlasMark = Sprites.block(MachineParts.BRASS);
        List<BakedQuad> modelMark = StandaloneModels.quads(PLUG_BASIC);
        if (pipeMeshes == null || pipeMeshes.atlasMark() != atlasMark || pipeMeshes.modelMark() != modelMark) {
            BerBuffers.Baked[][] plugs = new BerBuffers.Baked[2][6];
            BerBuffers.Baked[][] docks = new BerBuffers.Baked[2][6];
            BerBuffers.Baked[][] warehouseDecor = new BerBuffers.Baked[2][6];
            BerBuffers.Baked[][] warehouseWarm = new BerBuffers.Baked[2][6];
            BerBuffers.Baked[][] lamps = new BerBuffers.Baked[2][2];
            BerBuffers.Baked[][] lampGlass = new BerBuffers.Baked[2][2];
            for (int d = 0; d < 2; d++) {
                boolean dense = d == 1;
                Identifier plugId = dense ? PLUG_DENSE : PLUG_BASIC;
                Identifier dockId = dense ? DOCK_DENSE : DOCK_BASIC;
                Identifier lampId = dense ? StandaloneModels.LAMP_DENSE : StandaloneModels.LAMP_BASIC;
                Identifier glassId = dense ? StandaloneModels.LAMP_GLASS_DENSE : StandaloneModels.LAMP_GLASS_BASIC;
                for (int i = 0; i < DIRS.length; i++) {
                    Direction dir = DIRS[i];
                    plugs[d][i] = BerBuffers.bake((vc, p) -> {
                        Quads.orient(p, dir);
                        StandaloneModels.render(p.last(), vc, plugId, 1f, 1f, 1f, 0, OverlayTexture.NO_OVERLAY);
                    });
                    docks[d][i] = BerBuffers.bake((vc, p) -> {
                        Quads.orient(p, dir);
                        StandaloneModels.render(p.last(), vc, dockId, 1f, 1f, 1f, 0, OverlayTexture.NO_OVERLAY);
                    });
                    if (dir.getAxis().isHorizontal()) {
                        warehouseDecor[d][i] = BerBuffers.bake((vc, p) -> {
                            Quads.orient(p, dir);
                            PipeWarehouseRenderer.bakeDecorations(p, vc, dense, false);
                        });
                        warehouseWarm[d][i] = BerBuffers.bake((vc, p) -> {
                            Quads.orient(p, dir);
                            PipeWarehouseRenderer.bakeDecorations(p, vc, dense, true);
                        });
                    }
                }
                for (int ax = 0; ax < 2; ax++) {
                    boolean axisX = ax == 1;
                    lamps[d][ax] = BerBuffers.bake((vc, p) -> {
                        if (axisX) {
                            p.translate(0.5f, 0.5f, 0.5f);
                            p.mulPose(ROT_LAMP_X);
                            p.translate(-0.5f, -0.5f, -0.5f);
                        }
                        StandaloneModels.render(p.last(), vc, lampId, 1f, 1f, 1f, 0, OverlayTexture.NO_OVERLAY);
                    });
                    lampGlass[d][ax] = BerBuffers.bake((vc, p) -> {
                        if (axisX) {
                            p.translate(0.5f, 0.5f, 0.5f);
                            p.mulPose(ROT_LAMP_X);
                            p.translate(-0.5f, -0.5f, -0.5f);
                        }
                        StandaloneModels.render(p.last(), vc, glassId, 1f, 1f, 1f, 0, OverlayTexture.NO_OVERLAY);
                    });
                }
            }
            pipeMeshes = new PipeMeshes(atlasMark, modelMark, plugs, docks, warehouseDecor, warehouseWarm, lamps, lampGlass);
        }
        return pipeMeshes;
    }

    public SnowPipeRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public int getViewDistance() {
        return 64;
    }

    @Override
    public boolean shouldRender(PipeBlockEntity be, Vec3 cameraPosition) {
        if (!super.shouldRender(be, cameraPosition)) {
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
        PipeRunnerRenderer.renderRunner(level, be.getBlockPos(), state, partialTick, pose, buffers, light);
    }

    /** Plugs and warehouse docks on closed sides and dead ends (pre-baked per orientation to avoid per-frame quad transforms). */
    private static void renderPlugsAndWarehouses(PipeBlockEntity be, BlockState state, PoseStack pose, MultiBufferSource buffers, int light) {
        int mask = terminalMask(be, state);
        if (mask == 0) {
            return;
        }
        int dIdx = (state.getBlock() instanceof SnowPipeBlock p && p.isDense()) ? 1 : 0;
        PipeMeshes meshes = pipeMeshes();
        RenderType cutout = Sheets.cutoutBlockSheet();
        PoseStack.Pose current = pose.last();
        BlockPos pos = be.getBlockPos();
        boolean nearDecor = LegacyBer.camera().distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) < WAREHOUSE_DECOR_SIGHT_SQ;
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
                    meshes.warehouseWarm()[dIdx][i].draw(buffers, cutout, current, LightCoordsUtil.FULL_BRIGHT, 0);
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

        meshes.lamps()[dIdx][axIdx].draw(buffers, Sheets.cutoutBlockSheet(), basePose, light, 0);

        PoseStack.Pose digitPose = basePose;
        if (axisX) {
            digitPose = LAMP_DIGIT_POSE.last();
            digitPose.set(basePose);
            digitPose.pose().translate(0.5f, 0.5f, 0.5f).rotate(ROT_LAMP_X).translate(-0.5f, -0.5f, -0.5f);
            digitPose.normal().rotate(ROT_LAMP_X);
        }

        // the digits face east, in front of the tubes' mesh planes; seen from there the tens tube is on the left (south)
        int load = Math.min(99, be.lampLoad());
        int color = NixieDigits.loadColor(load, be.lampCapacity());
        float x = (dense ? 14.7f : 12.6f) + 2.62f;
        VertexConsumer nixie = NixieDigits.buffer(buffers);
        if (load >= 10) {
            NixieDigits.digit(digitPose, nixie, load / 10, x, 8f, 9.35f, 2.2f, 4.4f, 0f, 0f, -1f, 0f, 1f, 0f, color);
        }
        NixieDigits.digit(digitPose, nixie, load % 10, x, 8f, 6.65f, 2.2f, 4.4f, 0f, 0f, -1f, 0f, 1f, 0f, color);
        // under Iris/Sodium the glass is drawn before the digits and hides them (and cuts through the pipe's own glass): omit glass
        if (!GlowTypes.shaderSafe()) {
            meshes.lampGlass()[dIdx][axIdx].draw(buffers, Sheets.translucentBlockSheet(), basePose, light, 0);
        }
    }

    /** Renders a cube block of {@code size} centred at {@code (x, y, z)} in the current pose frame. */
    static void renderBlockBox(PoseStack pose, MultiBufferSource buffers, BlockState state,
                               double x, double y, double z, float size, int light) {
        renderBlockBox(pose, buffers, state, x, y, z, size, size, size, light);
    }

    /** Renders a block scaled to {@code (sx, sy, sz)} centred at {@code (x, y, z)} in the current pose frame. */
    static void renderBlockBox(PoseStack pose, MultiBufferSource buffers, BlockState state,
                               double x, double y, double z, float sx, float sy, float sz, int light) {
        pose.pushPose();
        pose.translate(x, y, z);
        pose.scale(sx, sy, sz);
        pose.translate(-0.5, -0.5, -0.5);
        BerBuffers.block(buffers, state, pose, light);
        pose.popPose();
    }
}
