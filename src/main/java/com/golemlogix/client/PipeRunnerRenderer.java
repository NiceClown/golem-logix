package com.golemlogix.client;

import com.golemlogix.blocks.SnowPipeBlock;
import com.golemlogix.client.render.Baked;
import com.golemlogix.client.render.PixelMesh;
import com.golemlogix.init.ModItems;
import com.golemlogix.system.GolemNetwork;
import com.google.common.base.Suppliers;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static com.golemlogix.client.SnowPipeRenderer.DIRS;
import static com.golemlogix.client.SnowPipeRenderer.renderBlockBox;
import static com.golemlogix.client.render.PixelMesh.smoothstep;

/**
 * Snowman courier, hanging copper-golem delivery arm, and network cargo sampling for {@link SnowPipeRenderer}:
 * dense pipes show a full snowman carrying items/crates; basic pipes show a pumpkin head carrying miniature items/crates.
 * At every station turn, a ceiling manipulator lowers a copper golem that drops the next cargo into the snowman's hands.
 */
final class PipeRunnerRenderer {
    /** Turn animation beats (turn in {@code [0, 1]}): unload -> 180-degree spin -> reach & drop -> catch & settle. */
    private static final float PHASE_UNLOAD_END = 0.35f;
    private static final float PHASE_TURN_END = 0.65f;
    private static final float PHASE_REACH_END = 0.80f;
    /** Mid-trip morph window where the loose item packs into a shipping crate. */
    private static final float MORPH_START = 0.44f, MORPH_END = 0.56f;
    private static final float DROP_START = 0.70f, DROP_END = 0.82f;

    private static final float DENSE_SCALE = 0.2f;
    private static final float DENSE_ITEM_SCALE = 0.44f;
    private static final float DENSE_BOX_SCALE = 0.42f;
    private static final float BASIC_PUMPKIN_SCALE = 0.19f;
    private static final float BASIC_ITEM_SCALE = 0.12f;
    private static final float BASIC_BOX_SCALE = 0.11f;

    private static final Vector3f UP = new Vector3f(0f, 1f, 0f);
    private static final Vector3f ARM_FORWARD = new Vector3f(0f, -0.20f, 0.98f).normalize();
    private static final SnowmanPuppet.Pose TURN_POSE =
            new SnowmanPuppet.Pose(0f, 0f, 0f, 0f, SnowmanPuppet.REST_LEFT, SnowmanPuppet.REST_RIGHT);
    private static final CopperGolemPuppet.Arms OPEN_ARMS = new CopperGolemPuppet.Arms(0.1f, -0.55f, 0.1f, 0.55f, 0f);
    private static final Vector3f FEET_POS = new Vector3f();
    private static final Vector3f GOLEM_POS = new Vector3f();
    private static final Vector3f HAND_L = new Vector3f();
    private static final Vector3f HAND_R = new Vector3f();
    private static final BlockPos.MutableBlockPos CONTAINER_POS = new BlockPos.MutableBlockPos();
    private static final PoseStack SCRATCH_BOX_POSE = new PoseStack();
    private static final double RUNNER_SIGHT_SQ = 32.0 * 32.0;

    private static BlockState[] cargoBoxes;

    private record RunnerBlockMeshes(TextureAtlasSprite atlasMark, Baked pumpkin, Baked barrel,
                                     Baked copperBlock, Baked cutCopperSlab, Baked rawCopper) {
    }

    private static RunnerBlockMeshes blockMeshes;

    static void clearBaked() {
        blockMeshes = null;
    }

    private static RunnerBlockMeshes blockMeshes() {
        TextureAtlasSprite mark = PixelMesh.sprite(MachineParts.BRASS);
        if (blockMeshes == null || blockMeshes.atlasMark() != mark) {
            blockMeshes = new RunnerBlockMeshes(mark,
                    bakeUnitBlock(Blocks.CARVED_PUMPKIN.defaultBlockState()),
                    bakeUnitBlock(Blocks.BARREL.defaultBlockState()),
                    bakeUnitBlock(Blocks.COPPER_BLOCK.defaultBlockState()),
                    bakeUnitBlock(Blocks.CUT_COPPER_SLAB.defaultBlockState()),
                    bakeUnitBlock(Blocks.RAW_COPPER_BLOCK.defaultBlockState()));
        }
        return blockMeshes;
    }

    private static Baked bakeUnitBlock(BlockState state) {
        return Baked.bake((vc, p) -> {
            p.translate(-0.5f, -0.5f, -0.5f);
            Baked.drawBlock(p.last(), vc, state);
        });
    }

    private static void drawBakedBox(PoseStack pose, MultiBufferSource buffers, Baked mesh,
                                     double x, double y, double z, float sx, float sy, float sz, int light) {
        PoseStack.Pose p = SCRATCH_BOX_POSE.last();
        p.pose().set(pose.last().pose()).translate((float) x, (float) y, (float) z).scale(sx, sy, sz);
        p.normal().set(pose.last().normal());
        mesh.draw(buffers, RenderType.cutout(), p, light, 0);
    }

    private static void drawCargoState(PoseStack pose, BlockRenderDispatcher dispatcher, MultiBufferSource buffers,
                                       BlockState state, double x, double y, double z, float size, int light, int overlay) {
        RunnerBlockMeshes m = blockMeshes();
        Baked mesh;
        if (state.is(Blocks.BARREL)) {
            mesh = m.barrel();
        } else if (state.is(Blocks.COPPER_BLOCK)) {
            mesh = m.copperBlock();
        } else {
            // Blocks.CHEST uses ENTITYBLOCK_ANIMATED in 1.21.1, so let dispatcher render its real chest model
            renderBlockBox(pose, dispatcher, buffers, state, x, y, z, size, light, overlay);
            return;
        }
        drawBakedBox(pose, buffers, mesh, x, y, z, size, size, size, light);
    }

    private static final Supplier<List<ItemStack>> FLUID_BUCKETS = Suppliers.memoize(() -> List.of(
            new ItemStack(Items.WATER_BUCKET),
            new ItemStack(Items.LAVA_BUCKET),
            new ItemStack(Items.MILK_BUCKET)
    ));

    private static final Supplier<List<ItemStack>> FALLBACK_ITEMS = Suppliers.memoize(() -> List.of(
            new ItemStack(ModItems.COPPER_PUNCH_CARD.get()),
            new ItemStack(ModItems.FLATTENED_COPPER_MATRIX.get()),
            new ItemStack(ModItems.FLATTENED_IRON_CORE.get()),
            new ItemStack(ModItems.FLATTENED_CRYO_SUBSTRATE.get()),
            new ItemStack(ModItems.COMPRESSED_COPROCESSOR.get()),
            new ItemStack(ModItems.COPPER_DISK_16K.get()),
            new ItemStack(ModItems.PORTABLE_CHRONOMETER.get()),
            new ItemStack(ModItems.PIPE_PLUG.get()),
            new ItemStack(ModItems.AUTOCRAFT_MODULE.get()),
            new ItemStack(Items.COPPER_INGOT),
            new ItemStack(Items.RAW_COPPER),
            new ItemStack(Items.REDSTONE),
            new ItemStack(Items.LAPIS_LAZULI),
            new ItemStack(Items.AMETHYST_SHARD),
            new ItemStack(Items.SNOWBALL),
            new ItemStack(Items.BLUE_ICE),
            new ItemStack(Items.COMPASS),
            new ItemStack(Items.CLOCK),
            new ItemStack(Items.GOLD_INGOT),
            new ItemStack(Items.IRON_INGOT)
    ));

    private static final class CargoPool {
        long expireTime = 0;
        final List<ItemStack> items = new ArrayList<>();
        final List<ItemStack> fluids = new ArrayList<>();
    }

    private static final Map<BlockPos, CargoPool> CARGO_POOLS = new HashMap<>();
    private static WeakReference<Level> cargoLevel = new WeakReference<>(null);

    private PipeRunnerRenderer() {
    }

    /** Clears cached network cargo samples on world unload. */
    static void clearCache() {
        CARGO_POOLS.clear();
        cargoLevel = new WeakReference<>(null);
    }

    /** Renders the network's snowman courier if it currently occupies {@code pos}. */
    static void renderRunner(Level level, BlockPos pos, BlockState state, float partialTick,
                             PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        Vec3 cam = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        if (cam.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > RUNNER_SIGHT_SQ) {
            return;
        }
        float time = (level.getGameTime() % 24000L) + partialTick;
        PipeRunners.Spot spot = PipeRunners.locate(level, pos, state, level.getGameTime(), partialTick);
        if (spot == null) {
            return;
        }
        boolean dense = state.getBlock() instanceof SnowPipeBlock p && p.isDense();
        BlockRenderDispatcher dispatcher = Minecraft.getInstance().getBlockRenderer();
        ItemRenderer itemRenderer = Minecraft.getInstance().getItemRenderer();
        float turn = spot.turnProgress();
        boolean turning = spot.isTurning();
        float tripProgress = spot.tripProgress();

        ItemStack inItem = getCargoItem(spot.incomingCargo(), level, pos);
        ItemStack outItem = getCargoItem(spot.outgoingCargo(), level, pos);
        BlockState carriedBox = getCargoBox(spot.outgoingCargo());

        pose.pushPose();
        pose.translate(0.5 + spot.dx(), 0.5 + spot.dy(), 0.5 + spot.dz());

        float baseYaw = switch (spot.heading()) {
            case NORTH -> 0f;
            case EAST -> 270f;
            case SOUTH -> 180f;
            case WEST -> 90f;
            case UP, DOWN -> 180f;
        };
        boolean vertical = spot.heading().getAxis() == Direction.Axis.Y;
        float yaw = baseYaw;
        float step = (float) Math.sin(time * 0.55f);

        if (turning) {
            // ceiling mechanical arm + copper golem oriented strictly along the pipe axis
            pose.pushPose();
            pose.mulPose(Axis.YP.rotationDegrees(baseYaw));
            renderMechanicalArmAndGolem(pose, buffers, itemRenderer, level, outItem, dense, turn, time, light, overlay);
            pose.popPose();

            if (!vertical && turn >= PHASE_UNLOAD_END) {
                yaw = turn <= PHASE_TURN_END
                        ? baseYaw + smoothstep((turn - PHASE_UNLOAD_END) / 0.30f) * 180f
                        : baseYaw + 180f;
            }
            step = 0f;
        }

        pose.mulPose(Axis.YP.rotationDegrees(yaw));
        if (!turning) {
            pose.mulPose(Axis.ZP.rotationDegrees(step * 3f));
            pose.translate(0, Math.abs(step) * 0.012, 0);
        }

        if (dense) {
            renderDenseSnowman(pose, dispatcher, buffers, itemRenderer, level, inItem, outItem, carriedBox,
                    vertical, turning, turn, tripProgress, step, time, light, overlay);
        } else {
            renderBasicPumpkin(pose, dispatcher, buffers, itemRenderer, level, inItem, outItem, carriedBox,
                    turning, turn, tripProgress, time, light, overlay);
        }
        pose.popPose();
    }

    /** Full snowman courier inside a dense pipe (running with morphing cargo, or 4-phase station exchange). */
    private static void renderDenseSnowman(PoseStack pose, BlockRenderDispatcher dispatcher, MultiBufferSource buffers,
                                           ItemRenderer itemRenderer, Level level,
                                           ItemStack inItem, ItemStack outItem, BlockState carriedBox,
                                           boolean vertical, boolean turning, float turn, float tripProgress,
                                           float step, float time, int light, int overlay) {
        FEET_POS.set(0f, vertical ? -0.16f : -0.355f, 0f);
        if (!turning) {
            SnowmanPuppet.render(pose, buffers, light, FEET_POS, 180f, DENSE_SCALE, SnowmanPuppet.Head.PUMPKIN,
                    SnowmanPuppet.Pose.holding(Math.abs(step) * 0.6f),
                    hp -> renderMorphingCargo(hp, dispatcher, buffers, itemRenderer, level, outItem, carriedBox, tripProgress,
                            0.0, 0.0, DENSE_ITEM_SCALE, DENSE_BOX_SCALE, time, light, overlay));
            return;
        }
        if (turn < PHASE_UNLOAD_END) {
            // phase 1: smoothly lower the old item onto the platform floor where it dissolves
            float pDown = turn / PHASE_UNLOAD_END;
            float handY = Mth.lerp(pDown, 13.5f, 3.5f);
            float handZ = Mth.lerp(pDown, 9.0f, 11.5f);
            HAND_L.set(3.2f, handY, handZ);
            HAND_R.set(-3.2f, handY, handZ);
            SnowmanPuppet.Pose downPose = new SnowmanPuppet.Pose(0f, pDown * 0.12f, 0f, pDown * 20f, HAND_L, HAND_R);
            float cargoY = Mth.lerp(pDown, 0f, -0.60f);
            float cargoZ = Mth.lerp(pDown, 0f, 0.12f);
            float itemScale = pDown > 0.75f ? (1f - (pDown - 0.75f) / 0.25f) * DENSE_ITEM_SCALE : DENSE_ITEM_SCALE;
            SnowmanPuppet.render(pose, buffers, light, FEET_POS, 180f, DENSE_SCALE, SnowmanPuppet.Head.PUMPKIN, downPose,
                    hp -> {
                        if (itemScale > 0.01f) {
                            renderItem(itemRenderer, buffers, level, inItem, hp, 0, cargoY, cargoZ, itemScale, time * 3f, light, overlay);
                        }
                    });
        } else if (turn < PHASE_TURN_END) {
            // phase 2: empty-handed 180-degree turn
            SnowmanPuppet.render(pose, buffers, light, FEET_POS, 180f, DENSE_SCALE, SnowmanPuppet.Head.PUMPKIN, TURN_POSE, null);
        } else if (turn < PHASE_REACH_END) {
            // phase 3a: reach arms upward toward the falling item
            float pReach = (turn - PHASE_TURN_END) / 0.15f;
            float handY = Mth.lerp(pReach, 9.0f, 18.0f);
            float handZ = Mth.lerp(pReach, 1.5f, 7.5f);
            HAND_L.set(3.2f, handY, handZ);
            HAND_R.set(-3.2f, handY, handZ);
            SnowmanPuppet.Pose reachPose = new SnowmanPuppet.Pose(0f, 0f, 0f, -14f, HAND_L, HAND_R);
            SnowmanPuppet.render(pose, buffers, light, FEET_POS, 180f, DENSE_SCALE, SnowmanPuppet.Head.PUMPKIN, reachPose, null);
        } else {
            // phase 3b: catch with springy squash and settle cargo into carrying posture
            float pSettle = (turn - PHASE_REACH_END) / 0.20f;
            float squash = (float) Math.sin((1f - pSettle) * Math.PI) * 0.14f;
            float handY = Mth.lerp(pSettle, 18.0f, 13.5f);
            float handZ = Mth.lerp(pSettle, 7.5f, 9.0f);
            HAND_L.set(3.2f, handY, handZ);
            HAND_R.set(-3.2f, handY, handZ);
            SnowmanPuppet.Pose catchPose = new SnowmanPuppet.Pose(0f, squash, 0f, Mth.lerp(pSettle, -14f, 8f), HAND_L, HAND_R);
            SnowmanPuppet.render(pose, buffers, light, FEET_POS, 180f, DENSE_SCALE, SnowmanPuppet.Head.PUMPKIN, catchPose,
                    hp -> renderItem(itemRenderer, buffers, level, outItem, hp, 0, 0, 0, DENSE_ITEM_SCALE, time * 3f, light, overlay));
        }
    }

    /** Miniature pumpkin-head courier inside a basic pipe. */
    private static void renderBasicPumpkin(PoseStack pose, BlockRenderDispatcher dispatcher, MultiBufferSource buffers,
                                           ItemRenderer itemRenderer, Level level,
                                           ItemStack inItem, ItemStack outItem, BlockState carriedBox,
                                           boolean turning, float turn, float tripProgress, float time, int light, int overlay) {
        Baked pumpkin = blockMeshes().pumpkin();
        if (!turning) {
            drawBakedBox(pose, buffers, pumpkin, 0, 0, 0, BASIC_PUMPKIN_SCALE, BASIC_PUMPKIN_SCALE, BASIC_PUMPKIN_SCALE, light);
            renderMorphingCargo(pose, dispatcher, buffers, itemRenderer, level, outItem, carriedBox, tripProgress,
                    0.15, 0.16, BASIC_ITEM_SCALE, BASIC_BOX_SCALE, time, light, overlay);
            return;
        }
        if (turn < PHASE_UNLOAD_END) {
            // phase 1: hop flings the old item forward and down onto the floor
            float pDown = turn / PHASE_UNLOAD_END;
            float hop = (float) Math.sin(pDown * Math.PI) * 0.03f;
            drawBakedBox(pose, buffers, pumpkin, 0, hop, 0, BASIC_PUMPKIN_SCALE, BASIC_PUMPKIN_SCALE, BASIC_PUMPKIN_SCALE, light);

            float flingP = Mth.clamp(pDown / 0.85f, 0f, 1f);
            float flingY = Mth.lerp(flingP * flingP, 0.15f + hop, -0.14f);
            float flingZ = Mth.lerp(flingP, 0.0f, 0.18f);
            float sc = flingP > 0.70f ? (1f - (flingP - 0.70f) / 0.30f) * BASIC_ITEM_SCALE : BASIC_ITEM_SCALE;
            if (sc > 0.005f) {
                renderItem(itemRenderer, buffers, level, inItem, pose, 0, flingY, flingZ, sc, time * 3f + flingP * 120f, light, overlay);
            }
        } else if (turn < PHASE_REACH_END) {
            // phases 2 & 3a: empty turn, then wait for the drop from above
            drawBakedBox(pose, buffers, pumpkin, 0, 0, 0, BASIC_PUMPKIN_SCALE, BASIC_PUMPKIN_SCALE, BASIC_PUMPKIN_SCALE, light);
        } else {
            // phase 3b: catch on top of the pumpkin with soft bounce
            float pSettle = (turn - PHASE_REACH_END) / 0.20f;
            float squashHop = (float) -Math.sin((1f - pSettle) * Math.PI) * 0.03f;
            drawBakedBox(pose, buffers, pumpkin, 0, squashHop, 0, BASIC_PUMPKIN_SCALE, BASIC_PUMPKIN_SCALE, BASIC_PUMPKIN_SCALE, light);
            renderItem(itemRenderer, buffers, level, outItem, pose, 0, 0.15f + squashHop, 0, BASIC_ITEM_SCALE, time * 3f, light, overlay);
        }
    }

    /** Cargo mid-trip morph: loose item (0..0.44), spin-shrink into crate (0.44..0.56), closed crate (0.56..1.0). */
    private static void renderMorphingCargo(PoseStack pose, BlockRenderDispatcher dispatcher, MultiBufferSource buffers,
                                            ItemRenderer itemRenderer, Level level,
                                            ItemStack outItem, BlockState carriedBox, float tripProgress,
                                            double itemY, double boxY, float itemScale, float boxScale,
                                            float time, int light, int overlay) {
        if (tripProgress < MORPH_START) {
            renderItem(itemRenderer, buffers, level, outItem, pose, 0, itemY, 0, itemScale, time * 3f, light, overlay);
        } else if (tripProgress > MORPH_END) {
            drawCargoState(pose, dispatcher, buffers, carriedBox, 0, boxY, 0, boxScale, light, overlay);
        } else {
            float m = (tripProgress - MORPH_START) / (MORPH_END - MORPH_START);
            if (m < 0.5f) {
                float p = m / 0.5f;
                renderItem(itemRenderer, buffers, level, outItem, pose, 0, itemY, 0, (1f - p) * itemScale, time * (3f + p * 12f), light, overlay);
            } else {
                float p = (m - 0.5f) / 0.5f;
                drawCargoState(pose, dispatcher, buffers, carriedBox, 0, boxY, 0, p * boxScale, light, overlay);
            }
        }
    }

    /**
     * Hanging ceiling manipulator and copper golem at every station turn: extends downward, holds the next cargo item, opens
     * its hands at {@code DROP_START}, and lets the item free-fall into the waiting snowman's hands.
     */
    private static void renderMechanicalArmAndGolem(PoseStack pose, MultiBufferSource buffers,
                                                    ItemRenderer itemRenderer, @Nullable Level level, ItemStack outItem,
                                                    boolean dense, float turn, float time, int light, int overlay) {
        float ceilingY = dense ? 0.28f : 0.165f;
        float targetFeetY = dense ? 0.02f : 0.01f;
        float retractedFeetY = dense ? 0.10f : 0.07f;
        float golemScale = dense ? 0.095f : 0.055f;
        float railThick = dense ? 0.035f : 0.022f;
        RunnerBlockMeshes m = blockMeshes();

        // compact ceiling flange mounting the carriage
        float bracketSize = dense ? 0.18f : 0.12f;
        drawBakedBox(pose, buffers, m.cutCopperSlab(), 0, ceilingY + 0.02f, 0, bracketSize, railThick, bracketSize, light);

        // extension profile: lower -> hold -> retract (uses quintic Mth.smoothstep)
        float ext;
        if (turn < 0.28f) {
            ext = Mth.clamp(turn / 0.28f, 0f, 1f);
        } else if (turn < DROP_END) {
            ext = 1f;
        } else {
            ext = 1f - Mth.clamp((turn - DROP_END) / 0.18f, 0f, 1f);
        }
        ext = (float) Mth.smoothstep(ext);
        float currentFeetY = Mth.lerp(ext, retractedFeetY, targetFeetY);

        // ceiling carriage, telescopic rod, and copper cradle
        drawBakedBox(pose, buffers, m.copperBlock(), 0, ceilingY, 0,
                dense ? 0.13f : 0.08f, dense ? 0.035f : 0.022f, dense ? 0.11f : 0.07f, light);
        float rodTop = ceilingY - 0.015f;
        float rodBottom = currentFeetY + golemScale * 1.15f;
        if (rodTop > rodBottom) {
            drawBakedBox(pose, buffers, m.cutCopperSlab(), 0, (rodTop + rodBottom) / 2f, 0,
                    dense ? 0.035f : 0.022f, rodTop - rodBottom, dense ? 0.035f : 0.022f, light);
        }
        drawBakedBox(pose, buffers, m.rawCopper(), 0, rodBottom, 0,
                dense ? 0.06f : 0.038f, 0.02f, dense ? 0.06f : 0.038f, light);

        // hanging copper golem carrying the next cargo until DROP_START
        boolean holding = turn < DROP_START;
        CopperGolemPuppet.Anim golemAnim = new CopperGolemPuppet.Anim(time * 0.1f, false, holding, 0.12f);
        CopperGolemPuppet.Arms arms = holding ? null : OPEN_ARMS;
        float cargoScale = dense ? 0.28f : 0.16f;

        GOLEM_POS.set(0f, currentFeetY, 0f);
        CopperGolemPuppet.render(pose, buffers, light, GOLEM_POS, ARM_FORWARD, UP, golemScale, golemAnim, 0,
                hp -> renderItem(itemRenderer, buffers, level, outItem, hp, 0, 0, 0, cargoScale, time * 3f, light, overlay), arms);

        // free-falling cargo between golem release and snowman catch
        if (turn >= DROP_START && turn < DROP_END) {
            float dropP = (turn - DROP_START) / (DROP_END - DROP_START);
            float startY = currentFeetY + golemScale * 0.60f;
            float targetY = dense ? 0.00f : -0.02f;
            float curY = Mth.lerp(dropP * dropP, startY, targetY);
            float curZ = Mth.lerp(dropP, 0.04f, 0.0f);
            renderItem(itemRenderer, buffers, level, outItem, pose, 0, curY, curZ, cargoScale, time * 3f + dropP * 90f, light, overlay);
        }
    }

    private static BlockState getCargoBox(int cargoId) {
        if (cargoBoxes == null) {
            BlockState barrel = Blocks.BARREL.defaultBlockState();
            cargoBoxes = new BlockState[]{
                    barrel,
                    barrel,
                    Blocks.COPPER_BLOCK.defaultBlockState(),
                    barrel,
                    Blocks.CHEST.defaultBlockState(),
                    barrel
            };
        }
        return cargoBoxes[Math.floorMod(cargoId, cargoBoxes.length)];
    }

    /** Samples an item or fluid bucket for {@code cargoId} from the cached network core sample (or adjacent containers / fallback). */
    private static ItemStack getCargoItem(int cargoId, @Nullable Level level, @Nullable BlockPos pos) {
        List<ItemStack> netItems = null;
        List<ItemStack> netFluids = null;

        if (level != null && pos != null) {
            if (cargoLevel.get() != level) {
                CARGO_POOLS.clear();
                cargoLevel = new WeakReference<>(level);
            }
            long now = level.getGameTime();
            CargoPool pool = CARGO_POOLS.get(pos);
            if (pool == null || pool.expireTime < now || pool.expireTime - now > 40L) {
                if (CARGO_POOLS.size() > 512) {
                    CARGO_POOLS.clear();
                }
                pool = sampleCargoPool(level, pos, now);
                CARGO_POOLS.put(pos.immutable(), pool);
            }
            netItems = pool.items;
            netFluids = pool.fluids;
        }

        // 10% chance: fluid bucket from the network (or vanilla fallback)
        if (Math.floorMod(cargoId, 10) == 7) {
            if (netFluids != null && !netFluids.isEmpty()) {
                return netFluids.get(Math.floorMod(cargoId / 10, netFluids.size()));
            }
            List<ItemStack> buckets = FLUID_BUCKETS.get();
            return buckets.get(Math.floorMod(cargoId / 10, buckets.size()));
        }

        if (netItems != null && !netItems.isEmpty()) {
            return netItems.get(Math.floorMod(cargoId, netItems.size()));
        }
        List<ItemStack> fallback = FALLBACK_ITEMS.get();
        return fallback.get(Math.floorMod(cargoId, fallback.size()));
    }

    private static CargoPool sampleCargoPool(Level level, BlockPos pos, long now) {
        CargoPool pool = new CargoPool();
        pool.expireTime = now + 20L;
        try {
            // network core broadcasts lightweight cargo samples to clients
            GolemNetwork net = GolemNetwork.get(level, pos);
            if (net != null && net.core() != null) {
                pool.items.addAll(net.core().cargoItems());
                pool.fluids.addAll(net.core().cargoFluids());
            }
            if (pool.items.isEmpty()) {
                for (Direction dir : DIRS) {
                    CONTAINER_POS.setWithOffset(pos, dir);
                    var nbe = level.getBlockEntity(CONTAINER_POS);
                    if (nbe instanceof Container container && container.getContainerSize() > 0) {
                        for (int s = 0; s < container.getContainerSize(); s++) {
                            ItemStack st = container.getItem(s);
                            if (!st.isEmpty()) {
                                pool.items.add(st.copyWithCount(1));
                            }
                        }
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return pool;
    }

    private static void renderItem(ItemRenderer itemRenderer, MultiBufferSource buffers, @Nullable Level level, ItemStack stack,
                                   PoseStack pose, double x, double y, double z, float scale, float rotDeg, int light, int overlay) {
        if (stack.isEmpty() || scale <= 0.001f) {
            return;
        }
        pose.pushPose();
        pose.translate(x, y, z);
        pose.scale(scale, scale, scale);
        pose.mulPose(Axis.YP.rotationDegrees(rotDeg));
        itemRenderer.renderStatic(stack, ItemDisplayContext.FIXED, light, overlay, pose, buffers, level, 0);
        pose.popPose();
    }
}
