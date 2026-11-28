package com.golemlogix.client;

import com.golemlogix.blockentity.EssentiaCauldronBlockEntity;
import com.golemlogix.blocks.CryoJarBlock;
import com.golemlogix.blocks.EssentiaCauldronBlock;
import com.golemlogix.client.render.BerBuffers;
import com.golemlogix.client.render.LegacyBer;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.AABB;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import static com.golemlogix.client.MachineParts.BRASS;
import static com.golemlogix.client.MachineParts.COPPER;
import static com.golemlogix.client.MachineParts.CRYO;
import static com.golemlogix.client.MachineParts.DARK;
import static com.golemlogix.client.MachineParts.ICE;
import static com.golemlogix.client.MachineParts.LIGHT_BRASS;
import static com.golemlogix.client.MachineParts.WHITE;
import static com.golemlogix.client.render.PixelMesh.PIXEL;
import static com.golemlogix.client.render.PixelMesh.box;
import static com.golemlogix.client.render.PixelMesh.mcBlock;
import static com.golemlogix.client.render.PixelMesh.scaledRgb;
import static com.golemlogix.client.render.PixelMesh.sprite;

/**
 * The Essentia Cryo-Cauldron, two blocks high, drawn from its lower block: a brass-and-iron cryo heat-sink base ringed with
 * frost crystals over Ice; an unmistakable hollowed-out Iron Golem Head cauldron (pale riveted masonry, heavy brow ridge,
 * prominent nose, dark hollow eyes, riveted brass rim); left (+X, viewer's left) violet Auram nozzle and right (-X, viewer's
 * right) icy Gelum nozzle; bubbling swirling liquid inside the open skull; overhead Cryo-Source Jar pipes; and a copper
 * golem in royal-purple & gold Archmage robes and pointed wizard hat stirring the cauldron from a cantilevered platform.
 * <p>
 * Pixels in the north-facing frame: front is Z 0 (-Z), viewer's LEFT is +X (Auram, facing.getClockWise()),
 * viewer's RIGHT is -X (Gelum, facing.getCounterClockWise()); Y 0..32.
 */
public class EssentiaCauldronRenderer extends LegacyBer<EssentiaCauldronBlockEntity> {
    private static final Identifier IRON = mcBlock("iron_block");
    private static final Identifier BLACK = mcBlock("black_concrete");
    private static final Identifier GLOW = mcBlock("white_concrete");
    private static final Identifier ROBE = mcBlock("purple_wool");
    private static final Identifier OAK = mcBlock("dark_oak_planks");
    private static final Identifier WATER = mcBlock("water_still");

    private static final int PALE_IRON = 0xFFD6D0C8;
    private static final int BRIGHT_IRON = 0xFFE6E0D8;
    private static final int SHADOW_IRON = 0xFFA29C94;
    private static final int CAVITY_IRON = 0xFF4C505A;
    private static final int ROBE_MAIN = 0xFF6B36B8;
    private static final int ROBE_DARK = 0xFF4A2088;
    private static final int GOLD_TRIM = 0xFFF4D068;

    private static final Vector3f GOLEM_POS = new Vector3f(-2.6f * PIXEL, 17.7f * PIXEL, 8.2f * PIXEL);
    /** Angled 115 deg around Y (inward + slightly front-facing) so eyes, robe clasp & hat read clearly while stirring. */
    private static final Vector3f FWD_ARCHMAGE = new Vector3f(Mth.sin(115f * Mth.DEG_TO_RAD), 0f, Mth.cos(115f * Mth.DEG_TO_RAD));
    private static final Vector3f UP = new Vector3f(0f, 1f, 0f);
    private static final Quaternionf ROT_ARCHMAGE_Y = Axis.YP.rotationDegrees(115f);
    private static final Quaternionf ROT_X_180 = Axis.XP.rotationDegrees(180f);
    private static final Quaternionf LADLE_TILT = Axis.XP.rotationDegrees(112f);
    private static final double CREW_SIGHT_SQ = 42.0 * 42.0;

    private static final BlockPos.MutableBlockPos JAR_PROBE = new BlockPos.MutableBlockPos();
    private static final BlockPos.MutableBlockPos SIDE_PROBE = new BlockPos.MutableBlockPos();
    private static final Quaternionf SCRATCH_QUAT = new Quaternionf();
    private static final PoseStack SCRATCH_POSE = new PoseStack();

    private record Meshes(TextureAtlasSprite atlasMark, BerBuffers.Baked baseAndHead, BerBuffers.Baked frostRing,
                          BerBuffers.Baked platform, BerBuffers.Baked wizardHat, BerBuffers.Baked archmageRobe) {
    }

    private static Meshes meshes;

    private static Meshes meshes() {
        TextureAtlasSprite mark = sprite(BRASS);
        if (meshes == null || meshes.atlasMark() != mark) {
            meshes = new Meshes(mark,
                    BerBuffers.bake(EssentiaCauldronRenderer::bakeBaseAndHead),
                    BerBuffers.bake(EssentiaCauldronRenderer::bakeFrostRing),
                    BerBuffers.bake(EssentiaCauldronRenderer::bakePlatform),
                    BerBuffers.bake(EssentiaCauldronRenderer::bakeWizardHat),
                    BerBuffers.bake(EssentiaCauldronRenderer::bakeArchmageRobe));
        }
        return meshes;
    }

    public EssentiaCauldronRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public AABB getRenderBoundingBox(EssentiaCauldronBlockEntity be) {
        BlockPos p = be.getBlockPos();
        return new AABB(p.getX() - 1.1, p.getY(), p.getZ() - 1.1, p.getX() + 2.1, p.getY() + 2.4, p.getZ() + 2.1);
    }

    @Override
    public int getViewDistance() {
        return 96;
    }

    @Override
    public void render(EssentiaCauldronBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers,
                       int packedLight, int packedOverlay) {
        Level level = be.getLevel();
        BlockState state = be.getBlockState();
        if (level == null || !state.hasProperty(EssentiaCauldronBlock.FACING) || state.getValue(EssentiaCauldronBlock.SECTION) != 0) {
            return;
        }
        BlockPos pos = be.getBlockPos();
        int light = LevelRenderer.getLightCoords(level, pos.above());
        int lightTop = LevelRenderer.getLightCoords(level, pos.above(2));
        float work = Mth.lerp(partialTick, be.workPrev, be.work);
        float phase = Mth.lerp(partialTick, be.phasePrev, be.phase);
        float time = ((level.getGameTime() + Math.floorMod(pos.asLong(), 67L)) % 72000L) + partialTick;
        int auram = be.clientAuram;
        int gelum = be.clientGelum;
        int iceTier = be.clientIceTier;
        boolean boilOff = be.clientBoilOff || be.clientStatus == EssentiaCauldronBlockEntity.STATUS_BOIL_OFF;
        boolean brewing = be.clientStatus == EssentiaCauldronBlockEntity.STATUS_WORKING;

        Meshes baked = meshes();
        RenderType solid = RenderTypes.entityCutout(TextureAtlas.LOCATION_BLOCKS);
        VertexConsumer vc = buffers.getBuffer(solid);

        // World-aligned overhead outlet pipes to adjacent Cryo-Source Jars
        renderJarPipes(vc, pose.last(), level, pos, lightTop);

        Direction facing = state.getValue(EssentiaCauldronBlock.FACING);
        boolean showCrew = LegacyBer.camera().distanceToSqr(pos.getX() + 0.5, pos.getY() + 1.4, pos.getZ() + 0.5) < CREW_SIGHT_SQ;
        float golemTurn = showCrew ? platformTurn(level, pos, facing) : 0f;

        pose.pushPose();
        Quads.faceModel(pose, facing);
        PoseStack.Pose p = pose.last();

        baked.baseAndHead().draw(buffers, solid, p, light, 0);

        // Ice-cooling frost crystal ring around heat-sink base (or steam wisps when melted)
        if (iceTier > 0) {
            int frostTint = switch (iceTier) {
                case EssentiaCauldronBlockEntity.ICE_BLUE -> 0xFF7AD2FF;
                case EssentiaCauldronBlockEntity.ICE_PACKED -> 0xFFB4ECFF;
                default -> 0xFFE2F8FF;
            };
            baked.frostRing().draw(buffers, solid, p, LightCoordsUtil.FULL_BRIGHT, frostTint);
        } else if (auram > 0 || boilOff) {
            renderHeatSteam(vc, p, boilOff, time);
        }

        // Dynamic glowing Essentia nozzle sight-glass: +X (viewer's LEFT) = Auram violet, -X (viewer's RIGHT) = Gelum cyan
        renderNozzleGlasses(vc, p, auram, gelum, boilOff, time);

        // Faint arcane reflection inside the hollow Iron Golem eye sockets
        renderEyeGlints(vc, p, brewing, boilOff, auram, gelum, time);

        // Bubbling cauldron liquid + rising bubbles / Flux fumes inside the hollowed skull
        renderLiquidAndBubbles(vc, p, buffers, light, auram, gelum, brewing, boilOff, work, phase, time);

        // Cantilevered Archmage Platform + Archmage Copper Golem Puppet
        if (showCrew) {
            renderArchmage(baked, solid, pose, buffers, golemTurn, lightTop, brewing, boilOff, work, phase, time);
        }

        pose.popPose();
    }

    /** Picks a clear upper-half side for the Archmage platform: right (-X), left (+X), or back (+Z), never blocking front face. */
    private static float platformTurn(Level level, BlockPos pos, Direction facing) {
        if (sideClear(level, pos, facing.getCounterClockWise())) {
            return 0f;
        }
        if (sideClear(level, pos, facing.getClockWise())) {
            return 180f;
        }
        if (sideClear(level, pos, facing.getOpposite())) {
            return 90f;
        }
        return 0f;
    }

    private static boolean sideClear(Level level, BlockPos pos, Direction dir) {
        SIDE_PROBE.set(pos.getX() + dir.getStepX(), pos.getY() + 1, pos.getZ() + dir.getStepZ());
        return level.getBlockState(SIDE_PROBE).getCollisionShape(level, SIDE_PROBE).isEmpty();
    }

    private static void renderNozzleGlasses(VertexConsumer vc, PoseStack.Pose p, int auram, int gelum,
                                            boolean boilOff, float time) {
        TextureAtlasSprite glowSprite = sprite(GLOW);
        float pulse = 0.80f + 0.20f * Mth.sin(time * (boilOff ? 0.45f : 0.14f));
        // Viewer's LEFT (+X, facing.getClockWise()): AURAM VIOLET sight-ring centered at Y=8, Z=8 (aligned with Thaumaturge tubes)
        float auramBright = auram > 0 ? (0.72f + 0.28f * Math.min(1f, auram / 32f)) * pulse : 0.45f;
        int auramCol = scaledRgb(boilOff ? 0xF038FF : 0xC462FF, auramBright);
        box(vc, p, 14.95f, 6.25f, 6.25f, 15.80f, 9.75f, 9.75f, glowSprite, auramCol, LightCoordsUtil.FULL_BRIGHT);

        // Viewer's RIGHT (-X, facing.getCounterClockWise()): GELUM ICY CYAN sight-ring centered at Y=8, Z=8
        float gelumBright = gelum > 0 ? (0.72f + 0.28f * Math.min(1f, gelum / 32f)) * pulse : 0.45f;
        int gelumCol = scaledRgb(0x4EF4FF, gelumBright);
        box(vc, p, 0.20f, 6.25f, 6.25f, 1.05f, 9.75f, 9.75f, glowSprite, gelumCol, LightCoordsUtil.FULL_BRIGHT);
    }

    private static void renderEyeGlints(VertexConsumer vc, PoseStack.Pose p, boolean brewing, boolean boilOff,
                                        int auram, int gelum, float time) {
        if (!boilOff && !brewing && auram == 0 && gelum == 0) {
            return;
        }
        TextureAtlasSprite glowSprite = sprite(GLOW);
        float pulse = 0.55f + 0.45f * Mth.sin(time * (boilOff ? 0.55f : 0.12f));
        int leftCol = boilOff ? scaledRgb(0xFF28C8, pulse) : scaledRgb(auram > 0 ? 0xA85CFF : 0x3894A8, pulse * 0.65f);
        int rightCol = boilOff ? scaledRgb(0xFF28C8, pulse) : scaledRgb(gelum > 0 ? 0x4CEEFF : 0x7A48B8, pulse * 0.65f);
        // Subtle recessed glints inside the hollow dark sockets (left eye +X = 10.6..12.0, right eye -X = 4.0..5.4)
        box(vc, p, 10.7f, 9.6f, 1.28f, 11.9f, 10.5f, 1.44f, glowSprite, leftCol, LightCoordsUtil.FULL_BRIGHT);
        box(vc, p, 4.1f, 9.6f, 1.28f, 5.3f, 10.5f, 1.44f, glowSprite, rightCol, LightCoordsUtil.FULL_BRIGHT);
    }

    private static void renderHeatSteam(VertexConsumer vc, PoseStack.Pose p, boolean boilOff, float time) {
        TextureAtlasSprite glowSprite = sprite(GLOW);
        for (int i = 0; i < 4; i++) {
            float u = ((time * 0.045f + i * 0.25f) % 1.0f);
            float a = i * Mth.HALF_PI + time * 0.03f;
            float sx = 8f + Mth.cos(a) * 6.8f;
            float sz = 8f + Mth.sin(a) * 6.8f;
            float sy = 1.2f + u * 4.5f;
            float szBox = 0.5f * (1f - u * 0.5f);
            int col = boilOff ? 0xFFA038D0 : 0xFF9EAAB2;
            box(vc, p, sx - szBox, sy - szBox, sz - szBox, sx + szBox, sy + szBox, sz + szBox,
                    glowSprite, col, boilOff ? LightCoordsUtil.FULL_BRIGHT : LightCoordsUtil.pack(10, 10));
        }
    }

    private static void renderLiquidAndBubbles(VertexConsumer vc, PoseStack.Pose p, MultiBufferSource buffers, int light,
                                               int auram, int gelum, boolean brewing, boolean boilOff,
                                               float work, float phase, float time) {
        float fill = Mth.clamp((auram + gelum) / (float) (EssentiaCauldronBlockEntity.TANK_CAPACITY * 2), 0f, 1f);
        // Keep bubbling brew clearly visible near the brass rim whenever the cauldron holds Essentia
        float surfaceY = (auram == 0 && gelum == 0) ? 10.5f : Mth.lerp(fill, 14.4f, 17.15f);
        float wave = (brewing || boilOff) ? (boilOff ? 0.32f : 0.16f) * Mth.sin(time * (boilOff ? 0.55f : 0.22f)) : 0f;

        // Liquid tint: violet Auram + frosty Gelum -> swirling luminous cyan Cryo-Source, or angry violet-magenta on boil-off
        int liquidTint;
        Identifier liquidTex;
        boolean fullBright = true;
        if (boilOff) {
            float flash = 0.80f + 0.20f * Mth.sin(time * 0.5f);
            liquidTint = scaledRgb(0xE82EF0, flash);
            liquidTex = GLOW;
        } else if (auram > 0 && gelum > 0) {
            float swirl = 0.5f + 0.5f * Mth.sin(time * 0.12f);
            int r = (int) Mth.lerp(swirl, 0x54, 0x98);
            int g = (int) Mth.lerp(swirl, 0xF2, 0xD8);
            int b = 0xFF;
            liquidTint = 0xFF000000 | (r << 16) | (g << 8) | b;
            liquidTex = CRYO;
        } else if (auram > 0) {
            liquidTint = 0xFF9E4CF2;
            liquidTex = GLOW;
        } else if (gelum > 0) {
            liquidTint = 0xFF8CEEFF;
            liquidTex = CRYO;
        } else {
            liquidTint = 0xFF283A48;
            liquidTex = WATER;
            fullBright = false;
        }

        // Solid luminous cauldron surface slab so the brew pops vividly against the shadowed iron cavity
        box(vc, p, 3.65f, 6.25f, 3.65f, 12.35f, surfaceY + wave, 12.35f, liquidTex, liquidTint,
                fullBright ? LightCoordsUtil.FULL_BRIGHT : light);

        // Swirling glowing vortex crest & rising frost/essentia bubbles or purple Flux fumes
        TextureAtlasSprite glowSprite = sprite(GLOW);
        if (brewing || boilOff) {
            // Orbiting swirl foam specks on the liquid surface
            for (int i = 0; i < 6; i++) {
                float a = phase * (boilOff ? 2.4f : 1.15f) + i * Mth.TWO_PI / 6f;
                float r = 1.6f + 1.5f * ((i % 3) / 2f);
                float bx = 8f + Mth.cos(a) * r;
                float bz = 8f + Mth.sin(a) * r;
                float by = surfaceY + wave + 0.12f;
                int col = boilOff ? ((i & 1) == 0 ? 0xFFFF48E8 : 0xFF9818C8)
                        : ((i & 1) == 0 ? 0xFFB888FF : 0xFF74FAFF);
                box(vc, p, bx - 0.55f, by - 0.15f, bz - 0.55f, bx + 0.55f, by + 0.25f, bz + 0.55f,
                        glowSprite, col, LightCoordsUtil.FULL_BRIGHT);
            }
            // Rising bubbles (or tall churning purple Flux smoke cubes during boil-off!)
            int count = boilOff ? 8 : 5;
            for (int i = 0; i < count; i++) {
                float u = ((time * (boilOff ? 0.06f : 0.032f) + i / (float) count) % 1.0f);
                float a = i * 2.399f + time * 0.08f;
                float r = 0.8f + (i % 3) * 1.1f;
                float bx = 8f + Mth.cos(a) * r;
                float bz = 8f + Mth.sin(a) * r;
                float by = surfaceY + 0.4f + u * (boilOff ? 10.5f : 5.2f);
                float sz = boilOff ? (0.45f + 0.42f * u) : (0.36f * (1f - 0.4f * u));
                int col = boilOff ? ((i & 1) == 0 ? 0xFF7E1CA8 : 0xFFD038F0)
                        : ((i & 1) == 0 ? 0xFF88FAFF : 0xFFC898FF);
                box(vc, p, bx - sz, by - sz, bz - sz, bx + sz, by + sz, bz + sz,
                        glowSprite, col, LightCoordsUtil.FULL_BRIGHT);
            }
        }
    }

    private static void renderArchmage(Meshes baked, RenderType solid, PoseStack pose, MultiBufferSource buffers,
                                       float golemTurn, int light, boolean brewing, boolean boilOff,
                                       float work, float phase, float time) {
        PoseStack.Pose bp = SCRATCH_POSE.last();
        bp.set(pose.last());
        if (golemTurn != 0f) {
            SCRATCH_QUAT.rotationY(golemTurn * Mth.DEG_TO_RAD);
            bp.pose().rotateAround(SCRATCH_QUAT, 0.5f, 0.5f, 0.5f);
            bp.normal().rotate(SCRATCH_QUAT);
        }
        baked.platform().draw(buffers, solid, bp, light, 0);

        float rightX, rightZ, leftX, leftZ, headYaw, bow;
        if (boilOff) {
            // Flux boil-off panic: frantically waving BOTH arms overhead!
            float panic = time * 0.85f;
            rightX = -2.85f + 0.45f * Mth.sin(panic);
            rightZ = 0.42f + 0.28f * Mth.cos(panic * 1.15f);
            leftX = -2.85f - 0.45f * Mth.sin(panic);
            leftZ = -0.42f - 0.28f * Mth.cos(panic * 1.15f);
            headYaw = 0.48f * Mth.sin(panic * 0.7f);
            bow = 0.0f;
        } else if (brewing || work > 0.05f) {
            // Brewing: leaning over the rim, tracing smooth continuous stirring circles through the cauldron liquid!
            float stir = phase * 1.15f;
            rightX = -1.20f + 0.24f * Mth.sin(stir);
            rightZ = 0.24f + 0.28f * Mth.cos(stir);
            leftX = -1.10f - 0.18f * Mth.sin(stir);
            leftZ = -0.24f + 0.22f * Mth.cos(stir);
            headYaw = 0.16f * Mth.cos(stir);
            bow = 0.28f;
        } else {
            // Idle / Redstone paused: resting calmly holding the ladle upright beside the cauldron
            rightX = -0.52f;
            rightZ = 0.14f;
            leftX = -0.18f;
            leftZ = -0.08f;
            headYaw = 0.16f * Mth.sin(time * 0.03f);
            bow = 0.04f;
        }

        float scale = 0.45f;
        CopperGolemPuppet.Arms arms = new CopperGolemPuppet.Arms(
                rightX, rightZ, leftX, leftZ, headYaw,
                handPose -> renderStirringLadle(handPose, buffers, light, brewing, boilOff),
                null);

        CopperGolemPuppet.render(SCRATCH_POSE, buffers, light, GOLEM_POS, FWD_ARCHMAGE, UP, scale,
                new CopperGolemPuppet.Anim(time * 0.1f, false, false, bow), 1, null, arms,
                headPose -> baked.wizardHat().draw(buffers, solid, headPose.last(), light, 0));

        // Archmage Robe & Mantle aligned to the exact torso matrix of CopperGolemPuppet with +0.38 px clearance (ZERO Z-fighting!)
        SCRATCH_POSE.pushPose();
        SCRATCH_POSE.translate(GOLEM_POS.x, GOLEM_POS.y, GOLEM_POS.z);
        SCRATCH_POSE.mulPose(ROT_ARCHMAGE_Y);
        SCRATCH_POSE.scale(scale, scale, scale);
        SCRATCH_POSE.mulPose(ROT_X_180);
        SCRATCH_POSE.translate(0f, -5f * PIXEL, 0f);
        SCRATCH_POSE.mulPose(Axis.XP.rotation(bow * 0.35f));
        baked.archmageRobe().draw(buffers, solid, SCRATCH_POSE.last(), light, 0);
        SCRATCH_POSE.popPose();
    }

    /** Brass-and-frost stirring ladle held in the right hand, tilted down (+112 deg X) so it dips into the cauldron brew! */
    private static void renderStirringLadle(PoseStack pose, MultiBufferSource buffers, int light,
                                            boolean brewing, boolean boilOff) {
        pose.pushPose();
        pose.mulPose(LADLE_TILT);
        VertexConsumer vc = buffers.getBuffer(RenderTypes.entityCutout(TextureAtlas.LOCATION_BLOCKS));
        PoseStack.Pose p = pose.last();
        // Long brass handle extending from pommel (+3.2) down into the cauldron brew (-16.0)
        box(vc, p, -0.52f, -0.52f, -16.0f, 0.52f, 0.52f, 3.2f, BRASS, 0xFFE8C468, light);
        // Frost/ice grip collar and glowing pommel jewel
        box(vc, p, -0.80f, -0.80f, -2.6f, 0.80f, 0.80f, -1.2f, ICE, WHITE, light);
        box(vc, p, -0.78f, -0.78f, 2.8f, 0.78f, 0.78f, 4.0f, sprite(GLOW),
                boilOff ? 0xFFFF38D0 : 0xFF72F2FF, LightCoordsUtil.FULL_BRIGHT);
        // Brass ladle scoop bowl dipping into the liquid at the far tip
        box(vc, p, -1.75f, -0.75f, -18.8f, 1.75f, 0.95f, -15.8f, BRASS, LIGHT_BRASS, light);
        // Glowing frosty liquid coating inside the ladle scoop
        int scoopCol = boilOff ? 0xFFFF48E8 : brewing ? 0xFF7CF8FF : 0xFFA4D8E8;
        box(vc, p, -1.30f, -1.05f, -18.3f, 1.30f, -0.60f, -16.2f, sprite(GLOW), scoopCol,
                (brewing || boilOff) ? LightCoordsUtil.FULL_BRIGHT : light);
        pose.popPose();
    }

    private static void bakeBaseAndHead(VertexConsumer vc, PoseStack pose) {
        PoseStack.Pose p = pose.last();
        TextureAtlasSprite ironSprite = sprite(IRON);
        TextureAtlasSprite brassSprite = sprite(BRASS);
        TextureAtlasSprite darkSprite = sprite(DARK);

        // 1. Cryo heat-sink base (Y 0..4.2) sitting flush on the Ice block below
        box(vc, p, 1.0f, 0.0f, 1.0f, 15.0f, 1.5f, 15.0f, darkSprite, WHITE, 0);
        box(vc, p, 2.0f, 1.5f, 2.0f, 14.0f, 4.2f, 14.0f, darkSprite, 0xFFD0D4DC, 0);
        // Horizontal brass cooling fins around heat-sink
        box(vc, p, 1.2f, 1.9f, 1.2f, 14.8f, 2.55f, 14.8f, brassSprite, 0xFFD8B05C, 0);
        box(vc, p, 1.2f, 3.05f, 1.2f, 14.8f, 3.7f, 14.8f, brassSprite, 0xFFE6C472, 0);

        // 2. Hollowed-out Iron Golem Head Cauldron (Y 4.2..16.0): 4 hollow walls + basin floor (open interior 3.6..12.4!)
        // Basin floor
        box(vc, p, 2.0f, 4.2f, 2.0f, 14.0f, 6.2f, 14.0f, darkSprite, 0xFF888C96, 0);
        // Outer pale-iron masonry walls (front Z 1.6..3.6, back Z 12.4..14.4, left X 12.4..14.4, right X 1.6..3.6)
        box(vc, p, 1.6f, 4.2f, 1.6f, 14.4f, 16.0f, 3.6f, ironSprite, PALE_IRON, 0);
        box(vc, p, 1.6f, 4.2f, 12.4f, 14.4f, 16.0f, 14.4f, ironSprite, PALE_IRON, 0);
        box(vc, p, 12.4f, 4.2f, 3.6f, 14.4f, 16.0f, 12.4f, ironSprite, PALE_IRON, 0);
        box(vc, p, 1.6f, 4.2f, 3.6f, 3.6f, 16.0f, 12.4f, ironSprite, PALE_IRON, 0);
        // Darkened inner cavity lining (strictly inside walls & below brass rim to avoid coplanar seams)
        box(vc, p, 3.55f, 6.22f, 3.55f, 12.45f, 15.96f, 3.65f, ironSprite, CAVITY_IRON, 0);
        box(vc, p, 3.55f, 6.22f, 12.35f, 12.45f, 15.96f, 12.45f, ironSprite, CAVITY_IRON, 0);
        box(vc, p, 12.35f, 6.22f, 3.65f, 12.45f, 15.96f, 12.35f, ironSprite, CAVITY_IRON, 0);
        box(vc, p, 3.55f, 6.22f, 3.65f, 3.65f, 15.96f, 12.35f, ironSprite, CAVITY_IRON, 0);

        // 3. Unmistakable Iron Golem Face embossed on front (-Z, Z < 1.6)
        // Heavy furrowed brow ridge across forehead
        box(vc, p, 1.35f, 11.45f, 0.55f, 14.65f, 13.75f, 1.62f, ironSprite, BRIGHT_IRON, 0);
        box(vc, p, 1.45f, 11.15f, 0.72f, 14.55f, 11.48f, 1.62f, ironSprite, SHADOW_IRON, 0);
        // Iconic protruding Iron Golem nose
        box(vc, p, 6.4f, 5.5f, -0.35f, 9.6f, 12.2f, 1.62f, ironSprite, BRIGHT_IRON, 0);
        box(vc, p, 6.5f, 5.2f, -0.25f, 9.5f, 5.55f, 1.62f, ironSprite, SHADOW_IRON, 0);
        // Hollow dark eye sockets under the brow (viewer's left X 10.0..13.2, viewer's right X 2.8..6.0)
        box(vc, p, 2.7f, 8.9f, 1.32f, 6.0f, 11.15f, 1.65f, BLACK, WHITE, 0);
        box(vc, p, 10.0f, 8.9f, 1.32f, 13.3f, 11.15f, 1.65f, BLACK, WHITE, 0);
        // Lower cheek bone ledges beneath eye sockets
        box(vc, p, 2.4f, 8.2f, 1.15f, 6.1f, 8.95f, 1.62f, ironSprite, SHADOW_IRON, 0);
        box(vc, p, 9.9f, 8.2f, 1.15f, 13.6f, 8.95f, 1.62f, ironSprite, SHADOW_IRON, 0);
        // Iron rivets along front corners & cheeks
        for (float y : new float[]{5.6f, 14.5f}) {
            box(vc, p, 1.3f, y, 1.3f, 2.1f, y + 0.8f, 2.1f, darkSprite, WHITE, 0);
            box(vc, p, 13.9f, y, 1.3f, 14.7f, y + 0.8f, 2.1f, darkSprite, WHITE, 0);
            box(vc, p, 1.3f, y, 13.9f, 2.1f, y + 0.8f, 14.7f, darkSprite, WHITE, 0);
            box(vc, p, 13.9f, y, 13.9f, 14.7f, y + 0.8f, 14.7f, darkSprite, WHITE, 0);
        }

        // 4. Thick riveted brass cauldron rim around the open crown (Y 16.0..18.2, open interior 3.4..12.6!)
        box(vc, p, 1.0f, 16.0f, 1.0f, 15.0f, 18.2f, 3.4f, brassSprite, 0xFFE6C470, 0);
        box(vc, p, 1.0f, 16.0f, 12.6f, 15.0f, 18.2f, 15.0f, brassSprite, 0xFFE6C470, 0);
        box(vc, p, 12.6f, 16.0f, 3.4f, 15.0f, 18.2f, 12.6f, brassSprite, 0xFFE6C470, 0);
        box(vc, p, 1.0f, 16.0f, 3.4f, 3.4f, 18.2f, 12.6f, brassSprite, 0xFFE6C470, 0);
        // Rim rivets around all four sides
        for (float t : new float[]{2.4f, 8.0f, 13.6f}) {
            box(vc, p, t - 0.4f, 16.7f, 0.82f, t + 0.4f, 17.5f, 1.05f, darkSprite, WHITE, 0);
            box(vc, p, t - 0.4f, 16.7f, 14.95f, t + 0.4f, 17.5f, 15.18f, darkSprite, WHITE, 0);
            box(vc, p, 0.82f, 16.7f, t - 0.4f, 1.05f, 17.5f, t + 0.4f, darkSprite, WHITE, 0);
            box(vc, p, 14.95f, 16.7f, t - 0.4f, 15.18f, 17.5f, t + 0.4f, darkSprite, WHITE, 0);
        }

        // 5. Left (+X, Auram) & Right (-X, Gelum) brass Essentia suction nozzle couplings centered at Y = 8.0, Z = 8.0 (flush with Thaumaturge 5..11 tube flange)
        // +X nozzle (viewer's LEFT -> Auram)
        box(vc, p, 14.25f, 5.2f, 5.2f, 15.05f, 10.8f, 10.8f, brassSprite, 0xFFD8B05C, 0);
        box(vc, p, 15.05f, 5.65f, 5.65f, 15.70f, 6.30f, 10.35f, brassSprite, LIGHT_BRASS, 0);
        box(vc, p, 15.05f, 9.70f, 5.65f, 15.70f, 10.35f, 10.35f, brassSprite, LIGHT_BRASS, 0);
        box(vc, p, 15.05f, 6.30f, 5.65f, 15.70f, 9.70f, 6.30f, brassSprite, LIGHT_BRASS, 0);
        box(vc, p, 15.05f, 6.30f, 9.70f, 15.70f, 9.70f, 10.35f, brassSprite, LIGHT_BRASS, 0);
        box(vc, p, 15.70f, 5.3f, 5.3f, 16.02f, 10.7f, 10.7f, brassSprite, LIGHT_BRASS, 0);
        // -X nozzle (viewer's RIGHT -> Gelum)
        box(vc, p, 0.95f, 5.2f, 5.2f, 1.75f, 10.8f, 10.8f, brassSprite, 0xFFD8B05C, 0);
        box(vc, p, 0.30f, 5.65f, 5.65f, 0.95f, 6.30f, 10.35f, brassSprite, LIGHT_BRASS, 0);
        box(vc, p, 0.30f, 9.70f, 5.65f, 0.95f, 10.35f, 10.35f, brassSprite, LIGHT_BRASS, 0);
        box(vc, p, 0.30f, 6.30f, 5.65f, 0.95f, 9.70f, 6.30f, brassSprite, LIGHT_BRASS, 0);
        box(vc, p, 0.30f, 6.30f, 9.70f, 0.95f, 9.70f, 10.35f, brassSprite, LIGHT_BRASS, 0);
        box(vc, p, -0.02f, 5.3f, 5.3f, 0.30f, 10.7f, 10.7f, brassSprite, LIGHT_BRASS, 0);
    }

    private static void bakeFrostRing(VertexConsumer vc, PoseStack pose) {
        PoseStack.Pose p = pose.last();
        TextureAtlasSprite iceSprite = sprite(ICE);
        // Frost/ice crystal clusters ringing the cryo heat-sink base when Ice sits underneath
        float[][] shards = {
                {0.4f, 0.4f, 2.2f, 2.8f}, {13.4f, 0.4f, 2.2f, 3.1f},
                {0.4f, 13.4f, 2.2f, 2.9f}, {13.4f, 13.4f, 2.2f, 2.6f},
                {4.2f, 0.3f, 1.8f, 2.1f}, {10.0f, 0.3f, 1.8f, 2.3f},
                {4.2f, 13.9f, 1.8f, 2.2f}, {10.0f, 13.9f, 1.8f, 1.9f},
                {0.3f, 4.4f, 1.8f, 2.0f}, {0.3f, 9.8f, 1.8f, 2.4f},
                {13.9f, 4.4f, 1.8f, 2.3f}, {13.9f, 9.8f, 1.8f, 2.0f}
        };
        for (float[] s : shards) {
            box(vc, p, s[0], 0.0f, s[1], s[0] + s[2], s[3], s[1] + s[2], iceSprite, WHITE, 0);
        }
    }

    private static void bakePlatform(VertexConsumer vc, PoseStack pose) {
        PoseStack.Pose p = pose.last();
        // Cantilevered Archmage brass-and-dark-oak side platform at Y = 17.6 (cleanly above the Y = 6..11 Essentia nozzle!)
        box(vc, p, -5.0f, 17.0f, 4.8f, 1.0f, 17.7f, 11.2f, OAK, WHITE, 0);
        box(vc, p, -5.4f, 16.8f, 4.4f, -4.8f, 17.9f, 11.6f, BRASS, LIGHT_BRASS, 0);
        box(vc, p, -5.0f, 16.8f, 4.4f, 1.2f, 17.9f, 4.9f, BRASS, 0xFFE0BC70, 0);
        box(vc, p, -5.0f, 16.8f, 11.1f, 1.2f, 17.9f, 11.6f, BRASS, 0xFFE0BC70, 0);
        // Brass mounting bracket & struts anchoring above the nozzle (Y = 13.6..17.8)
        box(vc, p, 0.9f, 13.4f, 5.0f, 1.6f, 17.8f, 11.0f, BRASS, 0xFFD8B464, 0);
        com.golemlogix.client.render.PixelMesh.limb(vc, pose, -4.6f, 17.0f, 5.2f, 1.1f, 13.8f, 5.2f, 0.65f, BRASS, 0);
        com.golemlogix.client.render.PixelMesh.limb(vc, pose, -4.6f, 17.0f, 10.8f, 1.1f, 13.8f, 10.8f, 0.65f, BRASS, 0);
        // Rear corner brass rail finials
        box(vc, p, -5.35f, 17.9f, 4.45f, -4.75f, 20.1f, 5.05f, BRASS, LIGHT_BRASS, 0);
        box(vc, p, -5.35f, 17.9f, 10.95f, -4.75f, 20.1f, 11.55f, BRASS, LIGHT_BRASS, 0);
        box(vc, p, -5.25f, 19.6f, 4.8f, -4.85f, 20.0f, 11.2f, COPPER, 0xFFE8B478, 0);
    }

    /** Pointed Archmage wizard hat in CopperGolemPuppet's head frame (+Y down, face at -Z, lightning rod up to Y = -13). */
    private static void bakeWizardHat(VertexConsumer vc, PoseStack pose) {
        PoseStack.Pose p = pose.last();
        TextureAtlasSprite cloth = sprite(ROBE);
        TextureAtlasSprite gold = sprite(BRASS);
        // Wide curved purple brim with gold outer trim sitting above the eyes (y = -5.7..-4.7)
        box(vc, p, -7.4f, -5.65f, -8.2f, 7.4f, -4.85f, 8.2f, cloth, ROBE_MAIN, 0);
        box(vc, p, -7.8f, -5.45f, -8.6f, 7.8f, -5.05f, -8.15f, gold, GOLD_TRIM, 0);
        box(vc, p, -7.8f, -5.45f, 8.15f, 7.8f, -5.05f, 8.6f, gold, GOLD_TRIM, 0);
        box(vc, p, -7.8f, -5.45f, -8.15f, -7.35f, -5.05f, 8.15f, gold, GOLD_TRIM, 0);
        box(vc, p, 7.35f, -5.45f, -8.15f, 7.8f, -5.05f, 8.15f, gold, GOLD_TRIM, 0);

        // Gold hat band & front arcane buckle
        box(vc, p, -4.55f, -7.0f, -4.75f, 4.55f, -5.65f, 4.75f, gold, GOLD_TRIM, 0);
        box(vc, p, -1.2f, -7.25f, -5.15f, 1.2f, -5.55f, -4.65f, gold, 0xFFFFEB98, 0);

        // Tapered angled wizard cone (encloses the copper golem's y=-5..-13 lightning rod completely!)
        box(vc, p, -4.25f, -9.3f, -4.35f, 4.25f, -7.0f, 4.35f, cloth, ROBE_MAIN, 0);
        box(vc, p, -3.45f, -11.5f, -3.35f, 3.45f, -9.3f, 3.85f, cloth, ROBE_DARK, 0);
        box(vc, p, -2.55f, -13.6f, -2.35f, 2.55f, -11.5f, 3.25f, cloth, ROBE_MAIN, 0);
        // Rakish bent pointed tip with golden tassel bead
        box(vc, p, -1.55f, -15.4f, -0.65f, 1.75f, -13.5f, 3.55f, cloth, ROBE_DARK, 0);
        box(vc, p, -0.75f, -16.1f, 1.85f, 1.35f, -14.6f, 4.95f, cloth, ROBE_MAIN, 0);
        box(vc, p, -0.35f, -15.4f, 4.65f, 0.95f, -14.1f, 5.85f, gold, GOLD_TRIM, 0);
    }

    /** Archmage Robe & Mantle in CopperGolemPuppet's body frame (+Y down, torso -4..4, -6..0, -3..3; +0.38 px clear!). */
    private static void bakeArchmageRobe(VertexConsumer vc, PoseStack pose) {
        PoseStack.Pose p = pose.last();
        TextureAtlasSprite cloth = sprite(ROBE);
        TextureAtlasSprite gold = sprite(BRASS);
        // Main royal purple tunic wrapping torso with generous 0.38 px clearance (ZERO Z-fighting!)
        box(vc, p, -4.38f, -6.25f, -3.40f, 4.38f, 0.45f, 3.40f, cloth, ROBE_MAIN, 0);
        // Flared lower robe skirt covering upper legs (leaving copper feet peeking out below y = 3.4..5.0)
        box(vc, p, -4.58f, 0.45f, -3.58f, 4.58f, 2.85f, 3.58f, cloth, ROBE_DARK, 0);
        // Golden lower robe hem
        box(vc, p, -4.68f, 2.85f, -3.68f, 4.68f, 3.55f, 3.68f, gold, GOLD_TRIM, 0);
        // Royal shoulder mantle / capelet draping over shoulders
        box(vc, p, -4.72f, -6.38f, -3.65f, 4.72f, -3.95f, 3.65f, cloth, ROBE_DARK, 0);
        box(vc, p, -4.82f, -4.25f, -3.72f, 4.82f, -3.75f, 3.72f, gold, GOLD_TRIM, 0);
        // Front vertical golden arcane stole & glowing chest clasp (strictly proud of mantle & hem)
        box(vc, p, -1.15f, -6.10f, -3.86f, 1.15f, 3.35f, -3.52f, gold, GOLD_TRIM, 0);
        box(vc, p, -1.45f, -5.65f, -4.12f, 1.45f, -4.15f, -3.72f, gold, 0xFFFFEB98, 0);
    }

    /** Overhead brass-and-ice outlet pipes auto-connecting from the cauldron rim to adjacent Cryo-Source Jars. */
    private static void renderJarPipes(VertexConsumer vc, PoseStack.Pose p, Level level, BlockPos base, int light) {
        for (Direction d : Direction.Plane.HORIZONTAL) {
            JAR_PROBE.set(base.getX() + d.getStepX(), base.getY(), base.getZ() + d.getStepZ());
            BlockState s = level.getBlockState(JAR_PROBE);
            if (!(s.getBlock() instanceof CryoJarBlock) || s.getValue(CryoJarBlock.HALF) != DoubleBlockHalf.LOWER) {
                continue;
            }
            // Rises cleanly from cauldron brass rim edge -> up past open top -> across with ice collar -> down into jar vent
            pipeRun(vc, p, d, 5.6f, 7.6f, 0.95f, 17.6f, 34.0f, BRASS, WHITE, light);
            pipeRun(vc, p, d, 5.4f, 7.8f, 1.2f, 24.5f, 25.7f, ICE, WHITE, light);
            pipeRun(vc, p, d, 5.6f, 17.4f, 1.0f, 32.2f, 34.0f, BRASS, 0xFFE8C878, light);
            pipeRun(vc, p, d, 10.6f, 12.2f, 1.3f, 31.9f, 34.3f, ICE, WHITE, light);
            pipeRun(vc, p, d, 14.8f, 17.2f, 1.2f, 31.4f, 33.8f, BRASS, WHITE, light);
        }
    }

    private static void pipeRun(VertexConsumer vc, PoseStack.Pose p, Direction d, float u0, float u1, float halfW,
                                float y0, float y1, Identifier tex, int tint, int light) {
        float a0 = 8 + (d.getAxisDirection() == Direction.AxisDirection.POSITIVE ? u0 : -u1);
        float a1 = 8 + (d.getAxisDirection() == Direction.AxisDirection.POSITIVE ? u1 : -u0);
        if (d.getAxis() == Direction.Axis.X) {
            box(vc, p, a0, y0, 8 - halfW, a1, y1, 8 + halfW, tex, tint, light);
        } else {
            box(vc, p, 8 - halfW, y0, a0, 8 + halfW, y1, a1, tex, tint, light);
        }
    }
}
