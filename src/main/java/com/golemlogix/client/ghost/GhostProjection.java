package com.golemlogix.client.ghost;

import com.golemlogix.blocks.ManaSaturatorBlock;
import com.golemlogix.init.ModBlocks;
import com.golemlogix.system.SaturatorBlueprint;
import com.golemlogix.system.SaturatorStructure;
import net.minecraft.core.Direction;
import com.golemlogix.system.AtlasBlueprint;
import com.golemlogix.system.AtlasBlueprint.Variant;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Клиентская «голограмма» стройки Решётки Атланта: призрачные блоки показывают, куда и что ставить.
 * Пока проекция не зафиксирована, она следует за блоком, на который вы смотрите; если рядом уже стоит
 * Ядро Атланта, проекция сама привязывается к нему.
 */
public final class GhostProjection {
    /** Одна ячейка, где ещё нужно поставить блок. wrong — там стоит чужой блок, мешающий постройке. */
    public record GhostCell(BlockPos pos, BlockState state, boolean wrong) {
    }

    private static boolean active;
    private static Variant variant = Variant.FULL;
    @Nullable
    private static BlockPos anchor;
    private static boolean locked;
    /** Куда смотрит фасадом Насыщатель (следует за игроком, пока проекция не зафиксирована). */
    private static Direction facing = Direction.SOUTH;
    private static int completeTicks;
    @Nullable
    private static ResourceKey<Level> dimension;

    private GhostProjection() {
    }

    public static boolean isActive() {
        return active;
    }

    public static Variant variant() {
        return variant;
    }

    public static boolean isLocked() {
        return locked;
    }

    @Nullable
    public static BlockPos anchor() {
        return anchor;
    }

    public static boolean isShowing(Variant v) {
        return active && variant == v;
    }

    public static void start(Variant v) {
        variant = v;
        active = true;
        locked = false;
        anchor = null;
        completeTicks = 0;
        Minecraft mc = Minecraft.getInstance();
        dimension = mc.level != null ? mc.level.dimension() : null;
    }

    public static void clear() {
        active = false;
        anchor = null;
        locked = false;
        completeTicks = 0;
    }

    public static void toggleLock() {
        if (active && anchor != null) {
            locked = !locked;
        }
    }

    public static void tick(Minecraft mc) {
        if (!active || mc.level == null || mc.player == null) {
            return;
        }
        if (dimension != null && mc.level.dimension() != dimension) {
            clear();
            return;
        }
        if (!locked) {
            if (variant == Variant.SATURATOR || variant == Variant.MAINFRAME || variant == Variant.CATALOG) {
                facing = mc.player.getDirection().getOpposite();
            }
            HitResult hit = mc.hitResult;
            if (hit instanceof BlockHitResult bhr && hit.getType() == HitResult.Type.BLOCK) {
                anchor = bhr.getBlockPos().relative(bhr.getDirection());
                if (mc.level.getGameTime() % 5 == 0) {
                    snapToNearbyCore(mc, bhr.getBlockPos());
                }
            }
        }
        if (anchor == null) {
            return;
        }
        if (missing(mc.level).isEmpty()) {
            if (++completeTicks == 1) {
                mc.player.displayClientMessage(Component.translatable("gui.golem_logix.ghost.complete"), true);
            }
            if (completeTicks > 60) {
                clear();
            }
        } else {
            completeTicks = 0;
        }
    }

    /** Если рядом уже стоит Ядро (или «голова» Насыщателя) — проекция встаёт точно по нему и фиксируется. */
    private static void snapToNearbyCore(Minecraft mc, BlockPos around) {
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        boolean saturator = variant == Variant.SATURATOR, mainframe = variant == Variant.MAINFRAME, catalog = variant == Variant.CATALOG;
        var head = saturator ? ModBlocks.MANA_SATURATOR.get() : mainframe ? ModBlocks.CRYO_MAINFRAME.get()
                : catalog ? ModBlocks.CARD_CATALOG.get() : ModBlocks.ATLAS_CORE.get();
        for (BlockPos p : BlockPos.betweenClosed(around.offset(-5, -4, -5), around.offset(5, 6, 5))) {
            if (mc.level.getBlockState(p).is(head)) {
                double d = p.distSqr(around);
                if (d < bestDist) {
                    bestDist = d;
                    best = p.immutable();
                }
            }
        }
        if (best != null) {
            if (mainframe) {
                facing = mc.level.getBlockState(best).getValue(com.golemlogix.blocks.CryoMainframeBlock.FACING);
                anchor = best;
            } else if (catalog) {
                facing = mc.level.getBlockState(best).getValue(com.golemlogix.blocks.CardCatalogBlock.FACING);
                anchor = best;
            } else if (saturator) {
                facing = mc.level.getBlockState(best).getValue(ManaSaturatorBlock.FACING);
                anchor = SaturatorStructure.cellPos(best, facing, SaturatorStructure.PORT);
            } else {
                anchor = best.below(3);
            }
            locked = true;
        }
    }

    /** Все ячейки, которые ещё не соответствуют чертежу. */
    public static List<GhostCell> missing(Level level) {
        List<GhostCell> out = new ArrayList<>();
        if (anchor == null) {
            return out;
        }
        for (AtlasBlueprint.Cell c : currentCells()) {
            BlockPos pos = anchor.offset(c.dx(), c.dy(), c.dz());
            BlockState actual = level.getBlockState(pos);
            if (c.matches(actual)) {
                continue;
            }
            boolean wrong = !actual.isAir() && !actual.canBeReplaced();
            out.add(new GhostCell(pos, c.state(), wrong));
        }
        return out;
    }

    /** Сколько каких предметов ещё нужно поставить (Атлант — один предмет на две ячейки). */
    public static Map<Item, Integer> missingItems(Level level) {
        Map<Item, Integer> out = new LinkedHashMap<>();
        if (anchor == null) {
            return out;
        }
        Set<String> atlants = new HashSet<>();
        for (GhostCell g : missing(level)) {
            Item item = g.state().getBlock().asItem();
            if (g.state().getBlock() == ModBlocks.ATLAS_ATLANT.get()) {
                String key = (g.pos().getX()) + "," + (g.pos().getZ());
                if (!atlants.add(key)) {
                    continue;
                }
            }
            out.merge(item, 1, Integer::sum);
        }
        return out;
    }

    private static List<AtlasBlueprint.Cell> currentCells() {
        return switch (variant) {
            case SATURATOR -> SaturatorBlueprint.cells(facing);
            case MAINFRAME -> com.golemlogix.system.MainframeBlueprint.cells(facing);
            case CATALOG -> com.golemlogix.system.CatalogBlueprint.cells(facing);
            default -> AtlasBlueprint.cells(variant);
        };
    }

    public static int totalCells() {
        return currentCells().size();
    }
}