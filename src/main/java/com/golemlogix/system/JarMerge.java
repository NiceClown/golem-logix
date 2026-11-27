package com.golemlogix.system;

import com.golemlogix.blockentity.CryoJarBlockEntity;
import com.golemlogix.blocks.CryoJarBlock;
import com.golemlogix.blocks.JarForm;
import com.golemlogix.config.GolemConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Cryo-Source Jars standing side by side join into one, as Create's fluid tanks do: four in a 2 by 2 square become a Giga Jar, nine
 * in a 3 by 3 square an Antigravity Reactor. The jars stay jars, each block a part of the whole: the corner with the lowest x and z
 * holds all the Source and draws the machine, and breaking any jar takes the machine apart again, sharing its Source out among the
 * jars that are left. Nothing runs while it stands: the joining is looked for when a jar is placed or a machine comes apart.
 */
public final class JarMerge {
    private JarMerge() {
    }

    /** What a whole of this form holds: the jars it is made of, and a small bonus for joining them. */
    public static int capacity(JarForm form) {
        long jars = (long) GolemConfig.CRYO_JAR_CAPACITY.get() * form.jars();
        int bonus = switch (form) {
            case SINGLE -> 0;
            case GIGA -> GolemConfig.CRYO_JAR_GIGA_BONUS.get();
            case SUPER -> GolemConfig.CRYO_JAR_SUPER_BONUS.get();
        };
        return (int) Math.min(Integer.MAX_VALUE, jars * (100 + bonus) / 100);
    }

    /** The lower halves of a square of jars from this corner. */
    public static List<BlockPos> cells(BlockPos corner, int width) {
        List<BlockPos> out = new ArrayList<>(width * width);
        for (int x = 0; x < width; x++) {
            for (int z = 0; z < width; z++) {
                out.add(corner.offset(x, 0, z));
            }
        }
        return out;
    }

    /** Looks for a whole this jar could join, the larger first. A jar in a Giga Jar can still go into a reactor; it does not leave one. */
    public static void scan(ServerLevel level, BlockPos lower) {
        JarForm now = form(level, lower);
        if (now == null || now == JarForm.SUPER) {
            return;
        }
        for (int dx = -2; dx <= 0; dx++) {
            for (int dz = -2; dz <= 0; dz++) {
                BlockPos corner = lower.offset(dx, 0, dz);
                if (square(level, corner, JarForm.SUPER)) {
                    join(level, corner, JarForm.SUPER);
                    return;
                }
            }
        }
        if (now != JarForm.SINGLE) {
            return;
        }
        for (int dx = -1; dx <= 0; dx++) {
            for (int dz = -1; dz <= 0; dz++) {
                BlockPos corner = lower.offset(dx, 0, dz);
                if (square(level, corner, JarForm.GIGA)) {
                    join(level, corner, JarForm.GIGA);
                    return;
                }
            }
        }
    }

    /** The form of the jar whose lower half stands here, or null if there is no whole jar. */
    @Nullable
    private static JarForm form(Level level, BlockPos lower) {
        if (!level.isLoaded(lower) || !level.isLoaded(lower.above())) {
            return null;
        }
        BlockState s = level.getBlockState(lower);
        if (!(s.getBlock() instanceof CryoJarBlock) || s.getValue(CryoJarBlock.HALF) != DoubleBlockHalf.LOWER
                || !(level.getBlockState(lower.above()).getBlock() instanceof CryoJarBlock)) {
            return null;
        }
        return s.getValue(CryoJarBlock.FORM);
    }

    private static boolean square(Level level, BlockPos corner, JarForm into) {
        for (BlockPos cell : cells(corner, into.width())) {
            JarForm f = form(level, cell);
            // only a jar on its own joins a Giga Jar; a reactor takes those in a Giga Jar as well
            if (f == null || f == JarForm.SUPER || (into == JarForm.GIGA && f != JarForm.SINGLE)) {
                return false;
            }
        }
        return true;
    }

    private static void join(ServerLevel level, BlockPos corner, JarForm into) {
        List<BlockPos> cells = cells(corner, into.width());
        // a Giga Jar among them comes apart first, also the jars of it that stay outside this square; the jars inside the square
        // take their share without the one-jar cap, since it goes straight into the reactor (a full Giga Jar lost Source here)
        Set<BlockPos> inside = new HashSet<>(cells);
        for (BlockPos cell : cells) {
            if (level.getBlockEntity(cell) instanceof CryoJarBlockEntity jar && jar.controller() != null) {
                apart(level, jar.controller(), null, inside);
            }
        }
        long total = 0;
        for (BlockPos cell : cells) {
            if (level.getBlockEntity(cell) instanceof CryoJarBlockEntity jar) {
                total += jar.takeAll();
                jar.setController(corner);
            }
            setForm(level, cell, into);
        }
        if (level.getBlockEntity(corner) instanceof CryoJarBlockEntity whole) {
            whole.putAll((int) Math.min(total, capacity(into)));
        }
        sync(level, cells);
        notifyNetwork(level, cells);
    }

    /**
     * Takes a whole apart: its Source is shared out among the jars that stay, as far as they hold it, and they look again for a whole
     * to join. removed: the jar being broken, if one is (it has no share: a jar in the hand holds nothing).
     */
    public static void apart(ServerLevel level, BlockPos corner, @Nullable BlockPos removed) {
        apart(level, corner, removed, Set.of());
    }

    /** absorbed: jars that join a larger whole right after this, so they take any Source the jars outside it cannot hold. */
    private static void apart(ServerLevel level, BlockPos corner, @Nullable BlockPos removed, Set<BlockPos> absorbed) {
        if (!level.isLoaded(corner) || !(level.getBlockEntity(corner) instanceof CryoJarBlockEntity whole)) {
            return;
        }
        JarForm form = whole.getBlockState().hasProperty(CryoJarBlock.FORM) ? whole.getBlockState().getValue(CryoJarBlock.FORM) : JarForm.SINGLE;
        if (form == JarForm.SINGLE) {
            return;
        }
        int total = whole.takeAll();
        List<BlockPos> stay = new ArrayList<>();
        for (BlockPos cell : cells(corner, form.width())) {
            if (!cell.equals(removed) && level.isLoaded(cell) && level.getBlockEntity(cell) instanceof CryoJarBlockEntity) {
                stay.add(cell);
            }
        }
        int each = stay.isEmpty() ? 0 : total / stay.size();
        int extra = stay.isEmpty() ? 0 : total % stay.size();
        int one = capacity(JarForm.SINGLE);
        int[] share = new int[stay.size()];
        int left = total;
        for (int i = 0; i < stay.size(); i++) {
            share[i] = Math.min(one, each + (i < extra ? 1 : 0));
            left -= share[i];
        }
        // what the capped shares leave goes to a jar that is about to be absorbed (its whole holds it), else it is lost as before
        for (int i = 0; i < stay.size() && left > 0; i++) {
            if (absorbed.contains(stay.get(i))) {
                share[i] += left;
                left = 0;
            }
        }
        for (int i = 0; i < stay.size(); i++) {
            BlockPos cell = stay.get(i);
            if (!(level.getBlockEntity(cell) instanceof CryoJarBlockEntity jar)) {
                continue;
            }
            jar.setController(null);
            jar.putAll(share[i]);
            setForm(level, cell, JarForm.SINGLE);
            level.scheduleTick(cell, level.getBlockState(cell).getBlock(), 1);
        }
        sync(level, stay);
        notifyNetwork(level, cells(corner, form.width()));
    }

    /** Notifies networks adjacent to any lower or upper block of the joined/parted footprint so channels and bonuses refresh immediately. */
    private static void notifyNetwork(ServerLevel level, List<BlockPos> footprint) {
        for (BlockPos cell : footprint) {
            GolemNetwork.nodesChanged(level, cell);
            GolemNetwork.nodesChanged(level, cell.above());
        }
    }

    /** Only the state changes: its neighbours have nothing to learn from it. */
    private static void setForm(ServerLevel level, BlockPos lower, JarForm form) {
        if (!level.isLoaded(lower)) {
            return;
        }
        for (BlockPos p : new BlockPos[]{lower, lower.above()}) {
            BlockState s = level.getBlockState(p);
            if (s.getBlock() instanceof CryoJarBlock && s.getValue(CryoJarBlock.FORM) != form) {
                level.setBlock(p, s.setValue(CryoJarBlock.FORM, form), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
            }
        }
    }

    private static void sync(ServerLevel level, List<BlockPos> cells) {
        for (BlockPos cell : cells) {
            if (!level.isLoaded(cell)) {
                continue;
            }
            BlockState s = level.getBlockState(cell);
            level.sendBlockUpdated(cell, s, s, Block.UPDATE_ALL);
            level.updateNeighbourForOutputSignal(cell, s.getBlock());
        }
    }
}
