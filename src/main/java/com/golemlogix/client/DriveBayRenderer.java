package com.golemlogix.client;

import com.golemlogix.GolemLogixMod;
import com.golemlogix.blockentity.CopperDriveBayBlockEntity;
import com.golemlogix.blocks.CopperDriveBayBlock;
import com.golemlogix.blocks.GolemProperties;
import com.golemlogix.client.render.LegacyBer;
import com.golemlogix.client.render.Sprites;
import com.golemlogix.items.MagneticDiskItem;
import com.golemlogix.system.DiskStorage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.joml.Vector3f;

import static com.golemlogix.client.render.PixelMesh.PIXEL;
import static com.golemlogix.client.render.PixelMesh.modBlock;
import static com.golemlogix.client.render.PixelMesh.smoothstep;

/**
 * Dynamic interior and doors of the 2x2 Copper Drive Bay cabinet:
 * ten cassettes (label colour = disk capacity, four LEDs = fill level) with spinning tape reels and glowing windings,
 * copper archivist golems patrolling the shelves through swinging brass saloon doors, pulsing front runes, and two
 * hinged glass display doors that swing open while the GUI is open.
 * <p>
 * Coordinates are cabinet pixels (1/16 block): X 0..32 left to right as seen from the front, Y 0..32 up, Z inwards.
 * Drawn from the bottom-left cell facing north; {@link Quads#faceModel} rotates into the block's facing.
 */
public class DriveBayRenderer extends LegacyBer<CopperDriveBayBlockEntity> {
    private static final Identifier CASSETTE = entityTex("bay_cassette");
    private static final Identifier REEL = entityTex("bay_reel");
    private static final Identifier TAPE = entityTex("bay_tape");
    private static final Identifier TAPE_GLOW = entityTex("bay_tape_glow");
    private static final Identifier WHITE = entityTex("bay_white");
    private static final Identifier RUNES = entityTex("bay_runes_glow");
    private static final Identifier GLASS_DISPLAY = modBlock("cab_glass_d");
    private static final Identifier GLASS_GAUGE = modBlock("cab_glass_g");
    private static final Identifier SAT_BRASS = modBlock("sat_brass");

    private static final Vector3f UP = new Vector3f(0f, 1f, 0f);
    private static final Vector3f FWD_RIGHT = new Vector3f(-1f, 0f, 0f);
    private static final Vector3f FWD_LEFT = new Vector3f(1f, 0f, 0f);
    private static final Vector3f GOLEM_AT = new Vector3f();

    /** Label colours by capacity (1k, 4k, 16k, 64k, 256k): item disks are metals, fluid disks cold tints. */
    private static final int[] ITEM_LABEL = {0xFFD9803A, 0xFF9AA4B4, 0xFFF0C850, 0xFF55E6E0, 0xFFA070FF};
    private static final int[] FLUID_LABEL = {0xFF7AC8FF, 0xFFC4ECFF, 0xFF3E7CE6, 0xFF2A50B8, 0xFFD070FF};
    /** Fill LEDs: from green to red. */
    private static final int[] LED = {0xFF3CE070, 0xFF9AE040, 0xFFE8D030, 0xFFF08828, 0xFFF03A2A};
    private static final int LED_OFF = 0xFF1E2622;
    private static final int CASSETTE_SIDE_TINT = 0xFF7C3A1E;
    private static final int SALOON_BRASS = 0xFFB8862B;
    private static final int SALOON_PANEL = 0xFF6A4A1A;

    /** Cassette and reel layout inside each shelf cell. */
    private static final float COL0 = 8.0f, COL1 = 16.0f, CASSETTE_W = 7.0f, CASSETTE_H = 4.0f;
    private static final float Z_FRONT = 4.6f, Z_BACK = 12.5f;
    private static final float REEL_R = 1.3f, REEL_DY = 2.35f, REEL_X0 = 1.85f, REEL_X1 = 5.15f;
    private static final float SHELF_TOP = 28f, SHELF_PITCH = 4.8f, SHELF_FLOOR_OFFSET = 0.5f;
    /** Partition doorways (centre X, height above shelf, Z span from glass into cabinet). */
    private static final float DOOR_X = 15.5f, DOOR_H = 3.4f, DOOR_Z0 = 2.5f, DOOR_Z1 = 6.0f;
    private static final int[] GOLEM_ROWS = {0, 2, 4};
    /** Display and gauge glass door vertical bounds and depth. */
    private static final float GLASS_Z = 0.8f, GLASS_Y0 = 4f, GLASS_Y1 = 28f;

    public DriveBayRenderer(BlockEntityRendererProvider.Context context) {
    }

    private static Identifier entityTex(String name) {
        return Identifier.fromNamespaceAndPath(GolemLogixMod.MOD_ID, "textures/entity/" + name + ".png");
    }

    @Override
    public int getViewDistance() {
        return 48;
    }

    @Override
    public AABB getRenderBoundingBox(CopperDriveBayBlockEntity be) {
        BlockPos p = be.getBlockPos();
        BlockState state = be.getBlockState();
        if (!state.hasProperty(CopperDriveBayBlock.FACING)) {
            return new AABB(p);
        }
        Direction right = CopperDriveBayBlock.rightOf(state.getValue(CopperDriveBayBlock.FACING));
        return new AABB(p).minmax(new AABB(p.relative(right).above())).inflate(0.25);
    }

    private static int diskTier(ItemStack disk) {
        int cap = DiskStorage.capacity(disk);
        return cap >= 256000 ? 4 : cap >= 64000 ? 3 : cap >= 16000 ? 2 : cap >= 4000 ? 1 : 0;
    }

    private static float shelfBottomY(int row) {
        return SHELF_TOP - SHELF_PITCH * (row + 1) + SHELF_FLOOR_OFFSET;
    }

    private static float slotX(int slot) {
        return slot % 2 == 0 ? COL0 : COL1;
    }

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
        float time = (be.getLevel().getGameTime() % 72000L) + partialTick;

        pose.pushPose();
        Quads.faceModel(pose, facing);
        PoseStack.Pose p = pose.last();

        if (be.getMask() != 0) {
            renderCassettes(be, p, buffers, light, active, time);
        }
        boolean walking = active && be.getMask() != 0;
        renderSaloonDoors(p, buffers, light, walking, time);
        if (walking) {
            renderArchivistGolems(pose, buffers, light, time);
        }
        if (active) {
            // pulsing runes on the plinth and top beam
            float pulse = 0.55f + 0.45f * Mth.sin(time * 0.09f);
            int a = (int) (255 * pulse);
            front(buffers.getBuffer(RenderTypes.eyes(RUNES)), p, 0f, 0f, 32f, 32f, -0.03f, 0f, 0f, 1f, 1f,
                    (a << 24) | 0xFFFFFF, LightCoordsUtil.FULL_BRIGHT);
        }
        // hinged display and gauge glass doors (drawn after interior glow so BerBuffers preserves blended order)
        float ease = smoothstep(Mth.lerp(partialTick, be.doorPrev, be.door));
        renderGlassDoor(p, buffers, light, 7.5f, 16f, 1f, GLASS_DISPLAY, ease * 105f);
        renderGlassDoor(p, buffers, light, 29.5f, 4f, -1f, GLASS_GAUGE, ease * 100f);
        pose.popPose();
    }

    // ------------------------------------------------------------------------------------- cassettes (batched by texture)

    /** Draws all installed cassettes in five texture-batched passes to minimise render-type switches. */
    private static void renderCassettes(CopperDriveBayBlockEntity be, PoseStack.Pose p, MultiBufferSource buffers,
                                        int light, boolean active, float time) {
        renderCassetteFronts(be, p, buffers.getBuffer(RenderTypes.entityCutout(CASSETTE)), light);
        renderCassetteBodiesAndLeds(be, p, buffers.getBuffer(RenderTypes.entityCutout(WHITE)), light);
        renderTapeWindings(be, p, buffers.getBuffer(RenderTypes.entityCutout(TAPE)), light, active, time);
        if (active) {
            renderTapeGlow(be, p, buffers.getBuffer(RenderTypes.eyes(TAPE_GLOW)), time);
        }
        renderReels(be, p, buffers.getBuffer(RenderTypes.entityCutout(REEL)), light, active, time);
    }

    private static void renderCassetteFronts(CopperDriveBayBlockEntity be, PoseStack.Pose p, VertexConsumer body, int light) {
        for (int slot = 0; slot < CopperDriveBayBlockEntity.SLOTS; slot++) {
            if (be.getDisks().get(slot).isEmpty()) {
                continue;
            }
            float x0 = slotX(slot), x1 = x0 + CASSETTE_W;
            float y0 = shelfBottomY(slot / 2), y1 = y0 + CASSETTE_H;
            front(body, p, x0, y0, x1, y1, Z_FRONT, 0f, 0f, 1f, 1f, 0xFFFFFFFF, light);
        }
    }

    private static void renderCassetteBodiesAndLeds(CopperDriveBayBlockEntity be, PoseStack.Pose p, VertexConsumer solid, int light) {
        for (int slot = 0; slot < CopperDriveBayBlockEntity.SLOTS; slot++) {
            ItemStack disk = be.getDisks().get(slot);
            if (disk.isEmpty()) {
                continue;
            }
            float x0 = slotX(slot), x1 = x0 + CASSETTE_W;
            float y0 = shelfBottomY(slot / 2), y1 = y0 + CASSETTE_H;
            boolean fluid = disk.getItem() instanceof MagneticDiskItem d && d.isFluid();
            int label = (fluid ? FLUID_LABEL : ITEM_LABEL)[diskTier(disk)];
            sides(solid, p, x0, y0, x1, y1, Z_FRONT, Z_BACK, CASSETTE_SIDE_TINT, light);
            front(solid, p, x0 + 0.5f, y1 - 0.75f, x1 - 0.5f, y1 - 0.15f, Z_FRONT - 0.02f, 0.5f, 0.5f, 0.5f, 0.5f, label, light);

            long stored = MagneticDiskItem.getStoredCount(disk);
            long cap = Math.max(1, DiskStorage.capacity(disk));
            int lit = stored <= 0 ? 0 : (int) Math.min(4, 1 + stored * 4 / (cap + 1));
            for (int i = 0; i < 4; i++) {
                float lx = x1 - 2.05f + i * 0.4f;
                front(solid, p, lx, y1 - 0.68f, lx + 0.28f, y1 - 0.22f, Z_FRONT - 0.04f, 0.5f, 0.5f, 0.5f, 0.5f,
                        i < lit ? LED[i] : LED_OFF, i < lit ? LightCoordsUtil.FULL_BRIGHT : light);
            }
        }
    }

    private static void renderTapeWindings(CopperDriveBayBlockEntity be, PoseStack.Pose p, VertexConsumer tape,
                                           int light, boolean active, float time) {
        for (int slot = 0; slot < CopperDriveBayBlockEntity.SLOTS; slot++) {
            ItemStack disk = be.getDisks().get(slot);
            if (disk.isEmpty()) {
                continue;
            }
            float x0 = slotX(slot);
            float cy = shelfBottomY(slot / 2) + CASSETTE_H - REEL_DY;
            float fill = Mth.clamp((float) MagneticDiskItem.getStoredCount(disk) / Math.max(1, DiskStorage.capacity(disk)), 0f, 1f);
            float spin = active ? time * 7f + slot * 29f : slot * 29f;
            spun(tape, p, x0 + REEL_X0, cy, 0.55f + 0.7f * fill, Z_FRONT - 0.05f, spin, 0xFFFFFFFF, light);
            spun(tape, p, x0 + REEL_X1, cy, 1.25f - 0.7f * fill, Z_FRONT - 0.05f, -spin, 0xFFFFFFFF, light);
        }
    }

    private static void renderTapeGlow(CopperDriveBayBlockEntity be, PoseStack.Pose p, VertexConsumer glow, float time) {
        for (int slot = 0; slot < CopperDriveBayBlockEntity.SLOTS; slot++) {
            ItemStack disk = be.getDisks().get(slot);
            if (disk.isEmpty()) {
                continue;
            }
            float x0 = slotX(slot);
            float cy = shelfBottomY(slot / 2) + CASSETTE_H - REEL_DY;
            float fill = Mth.clamp((float) MagneticDiskItem.getStoredCount(disk) / Math.max(1, DiskStorage.capacity(disk)), 0f, 1f);
            float spin = time * 7f + slot * 29f;
            spun(glow, p, x0 + REEL_X0, cy, 0.55f + 0.7f * fill, Z_FRONT - 0.06f, spin, 0xFFFFFFFF, LightCoordsUtil.FULL_BRIGHT);
            spun(glow, p, x0 + REEL_X1, cy, 1.25f - 0.7f * fill, Z_FRONT - 0.06f, -spin, 0xFFFFFFFF, LightCoordsUtil.FULL_BRIGHT);
        }
    }

    private static void renderReels(CopperDriveBayBlockEntity be, PoseStack.Pose p, VertexConsumer reel,
                                    int light, boolean active, float time) {
        for (int slot = 0; slot < CopperDriveBayBlockEntity.SLOTS; slot++) {
            if (be.getDisks().get(slot).isEmpty()) {
                continue;
            }
            float x0 = slotX(slot);
            float cy = shelfBottomY(slot / 2) + CASSETTE_H - REEL_DY;
            float spin = active ? time * 7f + slot * 29f : slot * 29f;
            spun(reel, p, x0 + REEL_X0, cy, REEL_R, Z_FRONT - 0.09f, spin, 0xFFFFFFFF, light);
            spun(reel, p, x0 + REEL_X1, cy, REEL_R, Z_FRONT - 0.09f, -spin * 1.1f, 0xFFFFFFFF, light);
        }
    }

    // ------------------------------------------------------------------------------------- partition doors & archivists

    /** Horizontal position of patrolling archivist {@code i} in cabinet pixels. */
    private static float golemX(int i, float time) {
        float phase = (time * 0.0095f + i * 0.37f) % 2f;
        float t = smoothstep(phase < 1f ? phase : 2f - phase);
        return 9.2f + 13.6f * t;
    }

    /** Brass saloon doors in the centre partition between columns: swing open as a patrolling archivist approaches. */
    private static void renderSaloonDoors(PoseStack.Pose p, MultiBufferSource buffers, int light, boolean walking, float time) {
        VertexConsumer vc = buffers.getBuffer(RenderTypes.entityCutout(WHITE));
        float half = (DOOR_Z1 - DOOR_Z0) / 2f;
        for (int r = 0; r < 5; r++) {
            float yb = shelfBottomY(r);
            float open = 0f;
            for (int i = 0; walking && i < GOLEM_ROWS.length; i++) {
                if (GOLEM_ROWS[i] == r) {
                    float dx = Math.abs(golemX(i, time) - DOOR_X);
                    open = smoothstep(Mth.clamp(1f - (dx - 1.2f) / 1.6f, 0f, 1f));
                }
            }
            float a = open * 80f * Mth.DEG_TO_RAD;
            float y0 = yb + 0.3f, y1 = yb + DOOR_H - 0.4f;
            for (int side = 0; side < 2; side++) {
                float hz = side == 0 ? DOOR_Z0 : DOOR_Z1, dir = side == 0 ? 1f : -1f;
                float tipX = DOOR_X + Mth.sin(a) * half, tipZ = hz + dir * Mth.cos(a) * half;
                saloonLeaf(vc, p, DOOR_X, hz, tipX, tipZ, y0, y1, light);
            }
        }
    }

    private static void saloonLeaf(VertexConsumer vc, PoseStack.Pose p, float hx, float hz, float tx, float tz, float y0, float y1, int light) {
        verticalQuad(vc, p, hx, hz, tx, tz, y0, y1, SALOON_BRASS, light);
        float ix0 = hx + (tx - hx) * 0.2f, iz0 = hz + (tz - hz) * 0.2f;
        float ix1 = hx + (tx - hx) * 0.8f, iz1 = hz + (tz - hz) * 0.8f;
        verticalQuad(vc, p, ix0 - 0.03f, iz0, ix1 - 0.03f, iz1, y0 + 0.4f, y1 - 0.4f, SALOON_PANEL, light);
        verticalQuad(vc, p, ix0 + 0.03f, iz0, ix1 + 0.03f, iz1, y0 + 0.4f, y1 - 0.4f, SALOON_PANEL, light);
    }

    /** Three copper archivists patrolling shelves 0, 2, and 4 carrying crates left-to-right. */
    private static void renderArchivistGolems(PoseStack pose, MultiBufferSource buffers, int light, float time) {
        for (int i = 0; i < GOLEM_ROWS.length; i++) {
            float phase = (time * 0.0095f + i * 0.37f) % 2f;
            boolean toRight = phase < 1f;
            float x = golemX(i, time);
            float y = shelfBottomY(GOLEM_ROWS[i]);
            GOLEM_AT.set((16f - x) * PIXEL, y * PIXEL, 3.6f * PIXEL);
            CopperGolemPuppet.Anim anim = new CopperGolemPuppet.Anim(time * 0.42f + i * 2f, true, toRight, 0f);
            CopperGolemPuppet.render(pose, buffers, light, GOLEM_AT, toRight ? FWD_RIGHT : FWD_LEFT, UP, 0.115f, anim, i);
        }
    }

    // ------------------------------------------------------------------------------------- hinged glass doors

    /** Hinged cabinet door: brass frame and handle (cutout), followed by translucent display/gauge glass. */
    private static void renderGlassDoor(PoseStack.Pose p, MultiBufferSource buffers, int light, float hinge, float width, float dir,
                                        Identifier glassTex, float angle) {
        var atlas = Sprites.blocks();
        TextureAtlasSprite glass = atlas.getSprite(glassTex);
        TextureAtlasSprite brass = atlas.getSprite(SAT_BRASS);
        float c = Mth.cos(angle * Mth.DEG_TO_RAD), s = Mth.sin(angle * Mth.DEG_TO_RAD);
        float f = Math.min(0.7f, width * 0.15f);

        VertexConsumer vc = buffers.getBuffer(RenderTypes.entityCutout(TextureAtlas.LOCATION_BLOCKS));
        for (int side = -1; side <= 1; side += 2) {
            float t = side * 0.12f;
            doorStrip(vc, p, hinge, dir, c, s, 0f, width, GLASS_Y1 - f, GLASS_Y1, t, brass, light);
            doorStrip(vc, p, hinge, dir, c, s, 0f, width, GLASS_Y0, GLASS_Y0 + f, t, brass, light);
            doorStrip(vc, p, hinge, dir, c, s, 0f, f, GLASS_Y0, GLASS_Y1, t, brass, light);
            doorStrip(vc, p, hinge, dir, c, s, width - f, width, GLASS_Y0, GLASS_Y1, t, brass, light);
        }
        // handle bracket at the free edge
        float h0 = width - Math.min(2.2f, width * 0.45f), h1 = h0 + 0.7f;
        doorStrip(vc, p, hinge, dir, c, s, h0, h1, 14f, 18f, -0.9f, brass, light);
        doorStrip(vc, p, hinge, dir, c, s, h0, h1, 14f, 18f, -0.5f, brass, light);
        doorStrip(vc, p, hinge, dir, c, s, h0, h1, 14f, 14.5f, -0.7f, brass, light);

        // translucent glass pane
        VertexConsumer gv = buffers.getBuffer(RenderTypes.entityTranslucent(TextureAtlas.LOCATION_BLOCKS));
        float d1 = width - f;
        float ax = hinge + dir * (f * c), az = GLASS_Z - f * s;
        float bx = hinge + dir * (d1 * c), bz = GLASS_Z - d1 * s;
        float u0 = glass.getU(f / width), u1 = glass.getU(1f - f / width);
        float v0 = glass.getV(f / (GLASS_Y1 - GLASS_Y0)), v1 = glass.getV(1f - f / (GLASS_Y1 - GLASS_Y0));
        if (dir < 0) {
            float tmp = u0;
            u0 = u1;
            u1 = tmp;
        }
        vertex(gv, p, ax, GLASS_Y1 - f, az, u0, v0, 0xFFFFFFFF, light, 0, 0, -1);
        vertex(gv, p, bx, GLASS_Y1 - f, bz, u1, v0, 0xFFFFFFFF, light, 0, 0, -1);
        vertex(gv, p, bx, GLASS_Y0 + f, bz, u1, v1, 0xFFFFFFFF, light, 0, 0, -1);
        vertex(gv, p, ax, GLASS_Y0 + f, az, u0, v1, 0xFFFFFFFF, light, 0, 0, -1);
    }

    private static void doorStrip(VertexConsumer vc, PoseStack.Pose p, float hinge, float dir, float c, float s,
                                  float d0, float d1, float y0, float y1, float t, TextureAtlasSprite sprite, int light) {
        float ax = hinge + dir * (d0 * c + t * s), az = GLASS_Z - d0 * s + t * c;
        float bx = hinge + dir * (d1 * c + t * s), bz = GLASS_Z - d1 * s + t * c;
        float u0 = sprite.getU(0f), u1 = sprite.getU(Math.min(1f, (d1 - d0) / 16f));
        float v0 = sprite.getV(0f), v1 = sprite.getV(Math.min(1f, (y1 - y0) / 16f));
        vertex(vc, p, ax, y1, az, u0, v0, 0xFFFFFFFF, light, 0, 0, -1);
        vertex(vc, p, bx, y1, bz, u1, v0, 0xFFFFFFFF, light, 0, 0, -1);
        vertex(vc, p, bx, y0, bz, u1, v1, 0xFFFFFFFF, light, 0, 0, -1);
        vertex(vc, p, ax, y0, az, u0, v1, 0xFFFFFFFF, light, 0, 0, -1);
    }

    // ------------------------------------------------------------------------ primitives in cabinet coordinates

    private static void vertex(VertexConsumer vc, PoseStack.Pose pose, float x, float y, float z, float u, float v,
                               int color, int light, float nx, float ny, float nz) {
        vc.addVertex(pose, (16f - x) * PIXEL, y * PIXEL, z * PIXEL).setColor(color).setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, nx, ny, nz);
    }

    private static void front(VertexConsumer vc, PoseStack.Pose pose, float x0, float y0, float x1, float y1, float z,
                              float u0, float v0, float u1, float v1, int color, int light) {
        vertex(vc, pose, x0, y1, z, u0, v0, color, light, 0, 0, -1);
        vertex(vc, pose, x1, y1, z, u1, v0, color, light, 0, 0, -1);
        vertex(vc, pose, x1, y0, z, u1, v1, color, light, 0, 0, -1);
        vertex(vc, pose, x0, y0, z, u0, v1, color, light, 0, 0, -1);
    }

    private static void spun(VertexConsumer vc, PoseStack.Pose pose, float cx, float cy, float half, float z, float angle,
                             int color, int light) {
        float c = Mth.cos(angle * Mth.DEG_TO_RAD) * half, s = Mth.sin(angle * Mth.DEG_TO_RAD) * half;
        vertex(vc, pose, cx - c - s, cy - s + c, z, 0, 0, color, light, 0, 0, -1);
        vertex(vc, pose, cx + c - s, cy + s + c, z, 1, 0, color, light, 0, 0, -1);
        vertex(vc, pose, cx + c + s, cy + s - c, z, 1, 1, color, light, 0, 0, -1);
        vertex(vc, pose, cx - c + s, cy - s - c, z, 0, 1, color, light, 0, 0, -1);
    }

    private static void sides(VertexConsumer vc, PoseStack.Pose pose, float x0, float y0, float x1, float y1, float z0, float z1,
                              int color, int light) {
        // top
        vertex(vc, pose, x0, y1, z0, 0.5f, 0.5f, color, light, 0, 1, 0);
        vertex(vc, pose, x1, y1, z0, 0.5f, 0.5f, color, light, 0, 1, 0);
        vertex(vc, pose, x1, y1, z1, 0.5f, 0.5f, color, light, 0, 1, 0);
        vertex(vc, pose, x0, y1, z1, 0.5f, 0.5f, color, light, 0, 1, 0);
        // bottom
        vertex(vc, pose, x0, y0, z1, 0.5f, 0.5f, color, light, 0, -1, 0);
        vertex(vc, pose, x1, y0, z1, 0.5f, 0.5f, color, light, 0, -1, 0);
        vertex(vc, pose, x1, y0, z0, 0.5f, 0.5f, color, light, 0, -1, 0);
        vertex(vc, pose, x0, y0, z0, 0.5f, 0.5f, color, light, 0, -1, 0);
        // left and right
        vertex(vc, pose, x0, y1, z1, 0.5f, 0.5f, color, light, 1, 0, 0);
        vertex(vc, pose, x0, y1, z0, 0.5f, 0.5f, color, light, 1, 0, 0);
        vertex(vc, pose, x0, y0, z0, 0.5f, 0.5f, color, light, 1, 0, 0);
        vertex(vc, pose, x0, y0, z1, 0.5f, 0.5f, color, light, 1, 0, 0);
        vertex(vc, pose, x1, y1, z0, 0.5f, 0.5f, color, light, -1, 0, 0);
        vertex(vc, pose, x1, y1, z1, 0.5f, 0.5f, color, light, -1, 0, 0);
        vertex(vc, pose, x1, y0, z1, 0.5f, 0.5f, color, light, -1, 0, 0);
        vertex(vc, pose, x1, y0, z0, 0.5f, 0.5f, color, light, -1, 0, 0);
    }

    private static void verticalQuad(VertexConsumer vc, PoseStack.Pose p, float x0, float z0, float x1, float z1,
                                     float y0, float y1, int color, int light) {
        vertex(vc, p, x0, y1, z0, 0.5f, 0.5f, color, light, 1, 0, 0);
        vertex(vc, p, x1, y1, z1, 0.5f, 0.5f, color, light, 1, 0, 0);
        vertex(vc, p, x1, y0, z1, 0.5f, 0.5f, color, light, 1, 0, 0);
        vertex(vc, p, x0, y0, z0, 0.5f, 0.5f, color, light, 1, 0, 0);
    }
}
