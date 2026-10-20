package com.golemlogix.client;

import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import com.golemlogix.GolemLogixMod;
import com.golemlogix.blockentity.CopperDriveBayBlockEntity;
import com.golemlogix.blocks.CopperDriveBayBlock;
import com.golemlogix.blocks.GolemProperties;
import com.golemlogix.items.MagneticDiskItem;
import com.golemlogix.system.DiskStorage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.joml.Vector3f;

/**
 * The dynamic part of the 2x2 drive cabinet, everything visible behind the display glass:
 * ten cassettes (label colour = disk capacity, four LEDs = fill level), each with two tape reels
 * (they spin while the network is powered, the tape winds from one reel to the other as the disk fills),
 * copper archivist golems patrolling the shelves, and the pulsing glow of the runes on the front.
 *
 * All sizes are in "cabinet" coordinates (units of 1/16 block): X left to right as seen from the FRONT (0..32),
 * Y up (0..32), Z inwards from the front; the cabinet models use the same frame (tools/Forge2.java).
 * From the main (bottom left) cell the model faces north; {@link Quads#faceModel} rotates it by FACING.
 */
public class DriveBayRenderer extends com.golemlogix.client.render.LegacyBer<CopperDriveBayBlockEntity> {
    private static final Identifier CASSETTE = tex("bay_cassette");
    private static final Identifier REEL = tex("bay_reel");
    private static final Identifier TAPE = tex("bay_tape");
    private static final Identifier TAPE_GLOW = tex("bay_tape_glow");
    private static final Identifier WHITE = tex("bay_white");
    private static final Identifier RUNES = tex("bay_runes_glow");

    /** Label colours by capacity (1k, 4k, 16k, 64k, 256k): item disks are metals, fluid disks cold tints. */
    private static final int[] ITEM_LABEL = {0xFFD9803A, 0xFF9AA4B4, 0xFFF0C850, 0xFF55E6E0, 0xFFA070FF};
    private static final int[] FLUID_LABEL = {0xFF7AC8FF, 0xFFC4ECFF, 0xFF3E7CE6, 0xFF2A50B8, 0xFFD070FF};
    /** Fill LEDs: from green to red. */
    private static final int[] LED = {0xFF3CE070, 0xFF9AE040, 0xFFE8D030, 0xFFF08828, 0xFFF03A2A};
    private static final int LED_OFF = 0xFF1E2622;

    static final int FULL_BRIGHT = 0xF000F0;

    /** The cassette fills almost the whole shelf cell and sticks out to the front (not hidden at the back wall); the reels are almost full height. */
    private static final float COL0 = 8.0f, COL1 = 16.0f, CASSETTE_W = 7.0f, CASSETTE_H = 4.0f;
    private static final float Z_FRONT = 4.6f, Z_BACK = 12.5f;
    private static final float REEL_R = 1.3f, REEL_DY = 2.35f, REEL_X0 = 1.85f, REEL_X1 = 5.15f;
    /** Doorways in the partition (as in tools/Forge2.java): x of the middle, height above the shelf, z from the glass to the doorway depth. */
    private static final float DOOR_X = 15.5f, DOOR_H = 3.4f, DOOR_Z0 = 2.5f, DOOR_Z1 = 6.0f;
    private static final int[] GOLEM_ROWS = {0, 2, 4};

    public DriveBayRenderer(BlockEntityRendererProvider.Context context) {
    }

    private static Identifier tex(String name) {
        return Identifier.fromNamespaceAndPath(GolemLogixMod.MOD_ID, "textures/entity/" + name + ".png");
    }

    @Override
    public int getViewDistance() {
        return 48;
    }

    @Override
    public AABB getRenderBoundingBox(CopperDriveBayBlockEntity be) {
        BlockPos p = be.getBlockPos();
        Direction right = CopperDriveBayBlock.rightOf(be.getBlockState().getValue(CopperDriveBayBlock.FACING));
        return new AABB(p).minmax(new AABB(p.relative(right).above())).inflate(0.25);
    }

    private static int tier(ItemStack disk) {
        int cap = DiskStorage.capacity(disk);
        return cap >= 256000 ? 4 : cap >= 64000 ? 3 : cap >= 16000 ? 2 : cap >= 4000 ? 1 : 0;
    }

    // ------------------------------------------------------------------------ primitives in cabinet coordinates

    private static void vertex(VertexConsumer vc, PoseStack.Pose pose, float x, float y, float z, float u, float v,
                               int color, int light, float nx, float ny, float nz) {
        vc.addVertex(pose, (16f - x) / 16f, y / 16f, z / 16f).setColor(color).setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, nx, ny, nz);
    }

    /** A rectangle facing the viewer at depth z; (u0,v0) is the top left corner of the texture. */
    private static void front(VertexConsumer vc, PoseStack.Pose pose, float x0, float y0, float x1, float y1, float z,
                              float u0, float v0, float u1, float v1, int color, int light) {
        vertex(vc, pose, x0, y1, z, u0, v0, color, light, 0, 0, -1);
        vertex(vc, pose, x1, y1, z, u1, v0, color, light, 0, 0, -1);
        vertex(vc, pose, x1, y0, z, u1, v1, color, light, 0, 0, -1);
        vertex(vc, pose, x0, y0, z, u0, v1, color, light, 0, 0, -1);
    }

    /** A square with side 2*half around (cx, cy), rotated by angle degrees in the front plane. */
    private static void spun(VertexConsumer vc, PoseStack.Pose pose, float cx, float cy, float half, float z, float angle,
                             int color, int light) {
        float c = Mth.cos(angle * Mth.DEG_TO_RAD) * half, s = Mth.sin(angle * Mth.DEG_TO_RAD) * half;
        // corners (top left, top right, bottom right, bottom left) rotated by the angle
        vertex(vc, pose, cx - c - s, cy - s + c, z, 0, 0, color, light, 0, 0, -1);
        vertex(vc, pose, cx + c - s, cy + s + c, z, 1, 0, color, light, 0, 0, -1);
        vertex(vc, pose, cx + c + s, cy + s - c, z, 1, 1, color, light, 0, 0, -1);
        vertex(vc, pose, cx - c + s, cy - s - c, z, 0, 1, color, light, 0, 0, -1);
    }

    /** Side faces of a cassette (depth). The centre of the WHITE texture is a solid colour. */
    private static void sides(VertexConsumer vc, PoseStack.Pose pose, float x0, float y0, float x1, float y1, float z0, float z1,
                              int color, int light) {
        // top (normal up)
        vertex(vc, pose, x0, y1, z0, 0.5f, 0.5f, color, light, 0, 1, 0);
        vertex(vc, pose, x1, y1, z0, 0.5f, 0.5f, color, light, 0, 1, 0);
        vertex(vc, pose, x1, y1, z1, 0.5f, 0.5f, color, light, 0, 1, 0);
        vertex(vc, pose, x0, y1, z1, 0.5f, 0.5f, color, light, 0, 1, 0);
        // bottom
        vertex(vc, pose, x0, y0, z1, 0.5f, 0.5f, color, light, 0, -1, 0);
        vertex(vc, pose, x1, y0, z1, 0.5f, 0.5f, color, light, 0, -1, 0);
        vertex(vc, pose, x1, y0, z0, 0.5f, 0.5f, color, light, 0, -1, 0);
        vertex(vc, pose, x0, y0, z0, 0.5f, 0.5f, color, light, 0, -1, 0);
        // left (smaller X) and right
        vertex(vc, pose, x0, y1, z1, 0.5f, 0.5f, color, light, 1, 0, 0);
        vertex(vc, pose, x0, y1, z0, 0.5f, 0.5f, color, light, 1, 0, 0);
        vertex(vc, pose, x0, y0, z0, 0.5f, 0.5f, color, light, 1, 0, 0);
        vertex(vc, pose, x0, y0, z1, 0.5f, 0.5f, color, light, 1, 0, 0);
        vertex(vc, pose, x1, y1, z0, 0.5f, 0.5f, color, light, -1, 0, 0);
        vertex(vc, pose, x1, y1, z1, 0.5f, 0.5f, color, light, -1, 0, 0);
        vertex(vc, pose, x1, y0, z1, 0.5f, 0.5f, color, light, -1, 0, 0);
        vertex(vc, pose, x1, y0, z0, 0.5f, 0.5f, color, light, -1, 0, 0);
    }

    // ------------------------------------------------------------------------------------- rendering

    @Override
    public void render(CopperDriveBayBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        if (be.getLevel() == null) {
            return;
        }
        BlockState state = be.getBlockState();
        if (!state.hasProperty(CopperDriveBayBlock.FACING)) {
            return;
        }
        Direction facing = state.getValue(CopperDriveBayBlock.FACING);
        boolean active = state.getValue(GolemProperties.ACTIVE);
        float time = be.getLevel().getGameTime() + partialTick;

        pose.pushPose();
        Quads.faceModel(pose, facing);
        PoseStack.Pose p = pose.last();

        if (be.getMask() != 0) {
            cassettes(be, p, buffers, light, active, time);
        }
        boolean walking = active && be.getMask() != 0;
        doors(p, buffers, light, walking, time);
        if (walking) {
            golems(pose, buffers, light, time);
        }
        if (active) {
            // the runes on the plinth and the top beam pulse
            float pulse = 0.55f + 0.45f * Mth.sin(time * 0.09f);
            int a = (int) (255 * pulse);
            front(buffers.getBuffer(RenderTypes.eyes(RUNES)), p, 0f, 0f, 32f, 32f, -0.03f, 0f, 0f, 1f, 1f, (a << 24) | 0xFFFFFF, FULL_BRIGHT);
        }
        // the glass display door: open while someone has the cabinet window open
        float open = Mth.lerp(partialTick, be.doorPrev, be.door);
        float ease = open * open * (3f - 2f * open);
        // the big display is hinged at the left post, the narrow right door with the gauges at the right one
        glassDoor(p, buffers, light, 7.5f, 16f, 1f, "cab_glass_d", ease * 105f);
        glassDoor(p, buffers, light, 29.5f, 4f, -1f, "cab_glass_g", ease * 100f);
        pose.popPose();
    }

    private void cassettes(CopperDriveBayBlockEntity be, PoseStack.Pose p, MultiBufferSource buffers, int light, boolean active, float time) {
        // Buffers of different types cannot be held at once: requesting a new one finishes the previous one. Hence passes by texture.
        // pass 1: cassette fronts
        VertexConsumer body = buffers.getBuffer(RenderTypes.entityCutout(CASSETTE));
        for (int slot = 0; slot < CopperDriveBayBlockEntity.SLOTS; slot++) {
            if (be.getDisks().get(slot).isEmpty()) {
                continue;
            }
            float x0 = slot % 2 == 0 ? COL0 : COL1, x1 = x0 + CASSETTE_W;
            float y0 = 28f - 4.8f * (slot / 2 + 1) + 0.5f, y1 = y0 + CASSETTE_H;
            front(body, p, x0, y0, x1, y1, Z_FRONT, 0f, 0f, 1f, 1f, 0xFFFFFFFF, light);
        }
        // pass 2: side faces, labels and LEDs (solid colour)
        VertexConsumer solid = buffers.getBuffer(RenderTypes.entityCutout(WHITE));
        for (int slot = 0; slot < CopperDriveBayBlockEntity.SLOTS; slot++) {
            ItemStack disk = be.getDisks().get(slot);
            if (disk.isEmpty()) {
                continue;
            }
            float x0 = slot % 2 == 0 ? COL0 : COL1, x1 = x0 + CASSETTE_W;
            float y0 = 28f - 4.8f * (slot / 2 + 1) + 0.5f, y1 = y0 + CASSETTE_H;
            boolean fluid = disk.getItem() instanceof MagneticDiskItem d && d.isFluid();
            int label = (fluid ? FLUID_LABEL : ITEM_LABEL)[tier(disk)];
            sides(solid, p, x0, y0, x1, y1, Z_FRONT, Z_BACK, 0xFF7C3A1E, light);
            // label: light paper from the texture with the disk tint on top
            front(solid, p, x0 + 0.5f, y1 - 0.75f, x1 - 0.5f, y1 - 0.15f, Z_FRONT - 0.02f, 0.5f, 0.5f, 0.5f, 0.5f, label, light);
            // fill LEDs
            long stored = MagneticDiskItem.getStoredCount(disk);
            long cap = Math.max(1, DiskStorage.capacity(disk));
            int lit = stored <= 0 ? 0 : (int) Math.min(4, 1 + stored * 4 / (cap + 1));
            for (int i = 0; i < 4; i++) {
                float lx = x1 - 2.05f + i * 0.4f;
                front(solid, p, lx, y1 - 0.68f, lx + 0.28f, y1 - 0.22f, Z_FRONT - 0.04f, 0.5f, 0.5f, 0.5f, 0.5f, i < lit ? LED[i] : LED_OFF,
                        i < lit ? FULL_BRIGHT : light);
            }
        }
        // pass 3: tape wound behind the reels
        VertexConsumer tape = buffers.getBuffer(RenderTypes.entityCutout(TAPE));
        for (int slot = 0; slot < CopperDriveBayBlockEntity.SLOTS; slot++) {
            ItemStack disk = be.getDisks().get(slot);
            if (disk.isEmpty()) {
                continue;
            }
            float x0 = slot % 2 == 0 ? COL0 : COL1;
            float cy = 28f - 4.8f * (slot / 2 + 1) + 0.5f + CASSETTE_H - REEL_DY;
            float fill = Mth.clamp((float) MagneticDiskItem.getStoredCount(disk) / Math.max(1, DiskStorage.capacity(disk)), 0f, 1f);
            float spin = active ? time * 7f + slot * 29f : slot * 29f;
            spun(tape, p, x0 + REEL_X0, cy, 0.55f + 0.7f * fill, Z_FRONT - 0.05f, spin, 0xFFFFFFFF, light);
            spun(tape, p, x0 + REEL_X1, cy, 1.25f - 0.7f * fill, Z_FRONT - 0.05f, -spin, 0xFFFFFFFF, light);
        }
        // pass 3b: teal glow of the windings (only when the network is powered), on top of the tape
        if (active) {
            VertexConsumer glow = buffers.getBuffer(RenderTypes.eyes(TAPE_GLOW));
            for (int slot = 0; slot < CopperDriveBayBlockEntity.SLOTS; slot++) {
                ItemStack disk = be.getDisks().get(slot);
                if (disk.isEmpty()) {
                    continue;
                }
                float x0 = slot % 2 == 0 ? COL0 : COL1;
                float cy = 28f - 4.8f * (slot / 2 + 1) + 0.5f + CASSETTE_H - REEL_DY;
                float fill = Mth.clamp((float) MagneticDiskItem.getStoredCount(disk) / Math.max(1, DiskStorage.capacity(disk)), 0f, 1f);
                float spin = time * 7f + slot * 29f;
                spun(glow, p, x0 + REEL_X0, cy, 0.55f + 0.7f * fill, Z_FRONT - 0.06f, spin, 0xFFFFFFFF, FULL_BRIGHT);
                spun(glow, p, x0 + REEL_X1, cy, 1.25f - 0.7f * fill, Z_FRONT - 0.06f, -spin, 0xFFFFFFFF, FULL_BRIGHT);
            }
        }
        // pass 4: reels
        VertexConsumer reel = buffers.getBuffer(RenderTypes.entityCutout(REEL));
        for (int slot = 0; slot < CopperDriveBayBlockEntity.SLOTS; slot++) {
            if (be.getDisks().get(slot).isEmpty()) {
                continue;
            }
            float x0 = slot % 2 == 0 ? COL0 : COL1;
            float cy = 28f - 4.8f * (slot / 2 + 1) + 0.5f + CASSETTE_H - REEL_DY;
            float spin = active ? time * 7f + slot * 29f : slot * 29f;
            spun(reel, p, x0 + REEL_X0, cy, REEL_R, Z_FRONT - 0.09f, spin, 0xFFFFFFFF, light);
            spun(reel, p, x0 + REEL_X1, cy, REEL_R, Z_FRONT - 0.09f, -spin * 1.1f, 0xFFFFFFFF, light);
        }
    }

    /** Where archivist i is on the shelf now (x in cabinet coordinates) and whether it walks to the right. */
    private static float golemX(int i, float time) {
        float phase = (time * 0.0095f + i * 0.37f) % 2f;
        float t = phase < 1f ? phase : 2f - phase;
        t = t * t * (3f - 2f * t);
        return 9.2f + 13.6f * t;
    }

    /**
     * Door leaves in the partition between the shelf columns: a pair of brass "saloon" leaves in each doorway that swing open
     * when an archivist comes to the doorway (and close behind it).
     */
    private void doors(PoseStack.Pose p, MultiBufferSource buffers, int light, boolean walking, float time) {
        VertexConsumer vc = buffers.getBuffer(RenderTypes.entityCutout(WHITE));
        for (int r = 0; r < 5; r++) {
            float yb = 28f - 4.8f * (r + 1) + 0.5f;
            float open = 0f;
            for (int i = 0; walking && i < GOLEM_ROWS.length; i++) {
                if (GOLEM_ROWS[i] == r) {
                    float dx = Math.abs(golemX(i, time) - DOOR_X);
                    open = Mth.clamp(1f - (dx - 1.2f) / 1.6f, 0f, 1f);
                    open = open * open * (3f - 2f * open);
                }
            }
            // a leaf is hinged at each edge of the doorway; an open one is turned 80° into the doorway along the partition
            float half = (DOOR_Z1 - DOOR_Z0) / 2f, a = open * 80f * Mth.DEG_TO_RAD;
            float y0 = yb + 0.3f, y1 = yb + DOOR_H - 0.4f;
            for (int side = 0; side < 2; side++) {
                float hz = side == 0 ? DOOR_Z0 : DOOR_Z1, dir = side == 0 ? 1f : -1f;
                float tipX = DOOR_X + Mth.sin(a) * half, tipZ = hz + dir * Mth.cos(a) * half;
                leaf(vc, p, DOOR_X, hz, tipX, tipZ, y0, y1, light);
            }
        }
    }

    /** A leaf: a brass board from the hinge (hx, hz) to the edge (tx, tz), with a dark panel. */
    private static void leaf(VertexConsumer vc, PoseStack.Pose p, float hx, float hz, float tx, float tz, float y0, float y1, int light) {
        int brass = 0xFFB8862B, panel = 0xFF6A4A1A;
        quad(vc, p, hx, hz, tx, tz, y0, y1, brass, light);
        float ix0 = hx + (tx - hx) * 0.2f, iz0 = hz + (tz - hz) * 0.2f, ix1 = hx + (tx - hx) * 0.8f, iz1 = hz + (tz - hz) * 0.8f;
        quad(vc, p, ix0 - 0.03f, iz0, ix1 - 0.03f, iz1, y0 + 0.4f, y1 - 0.4f, panel, light);
        quad(vc, p, ix0 + 0.03f, iz0, ix1 + 0.03f, iz1, y0 + 0.4f, y1 - 0.4f, panel, light);
    }

    /** A vertical double-sided rectangle between the points (x0, z0) and (x1, z1). */
    private static void quad(VertexConsumer vc, PoseStack.Pose p, float x0, float z0, float x1, float z1, float y0, float y1, int color, int light) {
        vertex(vc, p, x0, y1, z0, 0.5f, 0.5f, color, light, 1, 0, 0);
        vertex(vc, p, x1, y1, z1, 0.5f, 0.5f, color, light, 1, 0, 0);
        vertex(vc, p, x1, y0, z1, 0.5f, 0.5f, color, light, 1, 0, 0);
        vertex(vc, p, x0, y0, z0, 0.5f, 0.5f, color, light, 1, 0, 0);
    }

    /** Door glass at depth 0.8 and height 4..28, as it was in the model (tools/Forge2.java): display 7.5..23.5, right one 25.5..29.5. */
    private static final float GLASS_Z = 0.8f, GLASS_Y0 = 4f, GLASS_Y1 = 28f;

    /**
     * Door: a brass frame, a handle at the free edge and glass (texture from the block atlas). hinge = x of the hinge, width = width,
     * dir = which way the leaf goes from the hinge (+1 right, -1 left); angle = how many degrees it is open outwards (towards the viewer).
     * The cabinet in the world is drawn in the cutout layer (before block entities), so the translucent glass lies over the shelves.
     */
    private static void glassDoor(PoseStack.Pose p, MultiBufferSource buffers, int light, float hinge, float width, float dir, String tex,
                                  float angle) {
        var atlas = com.golemlogix.client.render.Sprites.blocks();
        TextureAtlasSprite glass = atlas.getSprite(Identifier.fromNamespaceAndPath(GolemLogixMod.MOD_ID, "block/" + tex));
        TextureAtlasSprite brass = atlas.getSprite(Identifier.fromNamespaceAndPath(GolemLogixMod.MOD_ID, "block/sat_brass"));
        float c = Mth.cos(angle * Mth.DEG_TO_RAD), s = Mth.sin(angle * Mth.DEG_TO_RAD);
        // a door point at distance d from the hinge, shifted by t along the normal (+ is into the cabinet)
        java.util.function.BiFunction<Float, Float, float[]> at =
                (d, t) -> new float[]{hinge + dir * (d * c + t * s), GLASS_Z - d * s + t * c};
        VertexConsumer vc = buffers.getBuffer(RenderTypes.entityCutout(TextureAtlas.LOCATION_BLOCKS));
        float f = Math.min(0.7f, width * 0.15f);
        // frame: top, bottom, at the hinge, at the free edge, on both sides of the glass
        for (float t : new float[]{-0.12f, 0.12f}) {
            strip(vc, p, at, 0f, width, GLASS_Y1 - f, GLASS_Y1, t, brass, light);
            strip(vc, p, at, 0f, width, GLASS_Y0, GLASS_Y0 + f, t, brass, light);
            strip(vc, p, at, 0f, f, GLASS_Y0, GLASS_Y1, t, brass, light);
            strip(vc, p, at, width - f, width, GLASS_Y0, GLASS_Y1, t, brass, light);
        }
        // handle: a brass bracket in front of the glass at the free edge
        float h0 = width - Math.min(2.2f, width * 0.45f), h1 = h0 + 0.7f;
        for (float t : new float[]{-0.9f, -0.5f}) {
            strip(vc, p, at, h0, h1, 14f, 18f, t, brass, light);
        }
        strip(vc, p, at, h0, h1, 14f, 14.5f, -0.7f, brass, light);
        // glass
        VertexConsumer gv = buffers.getBuffer(RenderTypes.entityTranslucent(TextureAtlas.LOCATION_BLOCKS));
        float[] a = at.apply(f, 0f), b = at.apply(width - f, 0f);
        float u0 = glass.getU(f / width), u1 = glass.getU(1f - f / width);
        float v0 = glass.getV(f / (GLASS_Y1 - GLASS_Y0)), v1 = glass.getV(1f - f / (GLASS_Y1 - GLASS_Y0));
        if (dir < 0) {
            float tmp = u0;
            u0 = u1;
            u1 = tmp;
        }
        vertex(gv, p, a[0], GLASS_Y1 - f, a[1], u0, v0, 0xFFFFFFFF, light, 0, 0, -1);
        vertex(gv, p, b[0], GLASS_Y1 - f, b[1], u1, v0, 0xFFFFFFFF, light, 0, 0, -1);
        vertex(gv, p, b[0], GLASS_Y0 + f, b[1], u1, v1, 0xFFFFFFFF, light, 0, 0, -1);
        vertex(gv, p, a[0], GLASS_Y0 + f, a[1], u0, v1, 0xFFFFFFFF, light, 0, 0, -1);
    }

    /** A strip of the door from d0 to d1 (along the leaf) and from y0 to y1, shifted by t along the normal, with the sprite texture. */
    private static void strip(VertexConsumer vc, PoseStack.Pose p, java.util.function.BiFunction<Float, Float, float[]> at,
                              float d0, float d1, float y0, float y1, float t, TextureAtlasSprite sprite, int light) {
        float[] a = at.apply(d0, t), b = at.apply(d1, t);
        float u0 = sprite.getU(0f), u1 = sprite.getU(Math.min(1f, (d1 - d0) / 16f));
        float v0 = sprite.getV(0f), v1 = sprite.getV(Math.min(1f, (y1 - y0) / 16f));
        vertex(vc, p, a[0], y1, a[1], u0, v0, 0xFFFFFFFF, light, 0, 0, -1);
        vertex(vc, p, b[0], y1, b[1], u1, v0, 0xFFFFFFFF, light, 0, 0, -1);
        vertex(vc, p, b[0], y0, b[1], u1, v1, 0xFFFFFFFF, light, 0, 0, -1);
        vertex(vc, p, a[0], y0, a[1], u0, v1, 0xFFFFFFFF, light, 0, 0, -1);
    }

    /** Three archivists walk back and forth along the shelves in front of the cassettes (they take a crate at the left end and leave it at the right). */
    private void golems(PoseStack pose, MultiBufferSource buffers, int light, float time) {
        int[] rows = GOLEM_ROWS;
        for (int i = 0; i < rows.length; i++) {
            float phase = (time * 0.0095f + i * 0.37f) % 2f;
            boolean toRight = phase < 1f;
            float x = golemX(i, time);
            float y = 28f - 4.8f * (rows[i] + 1) + 0.5f;
            // forward in the model frame: the viewer looks from the north, their "right" is the model's -x
            Vector3f forward = new Vector3f(toRight ? -1f : 1f, 0f, 0f);
            Vector3f at = new Vector3f((16f - x) / 16f, y / 16f, 3.6f / 16f);
            CopperGolemPuppet.Anim anim = new CopperGolemPuppet.Anim(time * 0.42f + i * 2f, true, toRight, 0f);
            CopperGolemPuppet.render(pose, buffers, light, at, forward, new Vector3f(0f, 1f, 0f), 0.115f, anim, i);
        }
    }
}
