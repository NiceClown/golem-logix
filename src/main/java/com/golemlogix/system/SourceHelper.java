package com.golemlogix.system;

import com.golemlogix.blockentity.CreativeSourceJarBlockEntity;
import com.golemlogix.blockentity.EternalFrostResonatorBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Universal Source helper: the mod's unlimited blocks (Creative Source Jar, Resonator)
 * and Ars Nouveau jars / tiles (ISourceTile, through reflection, so Ars Nouveau stays optional).
 */
public class SourceHelper {
    private static Class<?> sourceTileClass = null;
    private static Method getSourceMethod = null;
    private static Method removeSourceMethod = null;
    private static Method addSourceMethod = null;
    private static Method canProvideMethod = null;
    private static Method canAcceptMethod = null;
    private static boolean checkedArsNouveau = false;

    private static void initArsNouveau() {
        if (checkedArsNouveau) {
            return;
        }
        checkedArsNouveau = true;
        try {
            sourceTileClass = Class.forName("com.hollingsworth.arsnouveau.api.source.ISourceTile");
            getSourceMethod = sourceTileClass.getMethod("getSource");
            removeSourceMethod = sourceTileClass.getMethod("removeSource", int.class, boolean.class);
            addSourceMethod = sourceTileClass.getMethod("addSource", int.class, boolean.class);
            canProvideMethod = sourceTileClass.getMethod("canProvideSource");
            canAcceptMethod = sourceTileClass.getMethod("canAcceptSource");
        } catch (Throwable ignored) {
            sourceTileClass = null;
        }
    }

    public static boolean arsNouveauLoaded() {
        return net.neoforged.fml.ModList.get().isLoaded("ars_nouveau");
    }

    /** Whether a blood mod for the Cryo-Distillation Tower is there: Neo Vitae, or any fluid a modpack put into the tag. */
    public static boolean distillationAvailable() {
        if (net.neoforged.fml.ModList.get().isLoaded("neovitae")) {
            return true;
        }
        return net.minecraft.core.registries.BuiltInRegistries.FLUID.getTag(com.golemlogix.init.ModTags.CRYO_DISTILLABLE)
                .map(t -> t.size() > 0).orElse(false);
    }

    /**
     * Whether Source has to be paid. By the requireSource setting yes, but only if there is a way to get it in survival: Ars Nouveau,
     * or blood for the Cryo-Distillation Tower (Neo Vitae). Without either (the Resonator is made in the Saturator, which needs Source
     * itself) the network and the Saturator work for free.
     */
    public static boolean sourceRequired() {
        return com.golemlogix.config.GolemConfig.REQUIRE_SOURCE.get() && (arsNouveauLoaded() || distillationAvailable());
    }

    /** A Source supplier connected to the network. */
    public interface Supplier {
        boolean infinite();

        int available();

        /** Takes up to amount, returns what was taken. */
        int drain(int amount);
    }

    private static final class InfiniteSupplier implements Supplier {
        @Override
        public boolean infinite() {
            return true;
        }

        @Override
        public int available() {
            return Integer.MAX_VALUE;
        }

        @Override
        public int drain(int amount) {
            return amount;
        }
    }

    private static final InfiniteSupplier INFINITE = new InfiniteSupplier();

    private static final class ArsSupplier implements Supplier {
        private final BlockEntity be;

        private ArsSupplier(BlockEntity be) {
            this.be = be;
        }

        @Override
        public boolean infinite() {
            return false;
        }

        @Override
        public int available() {
            try {
                if (!(boolean) canProvideMethod.invoke(be)) {
                    return 0;
                }
                return (int) getSourceMethod.invoke(be);
            } catch (Throwable t) {
                return 0;
            }
        }

        @Override
        public int drain(int amount) {
            int take = Math.min(amount, available());
            if (take <= 0) {
                return 0;
            }
            try {
                removeSourceMethod.invoke(be, take, false);
                return take;
            } catch (Throwable t) {
                return 0;
            }
        }
    }

    /**
     * Returns the Source supplier if the block entity is one. The Eternal Frost Resonator does not count as a supplier:
     * it does not feed the network but powers Portable Chronometers remotely (see {@link GolemNetwork#hasResonator()}).
     */
    public static Supplier asSupplier(BlockEntity be) {
        if (be instanceof CreativeSourceJarBlockEntity) {
            return INFINITE;
        }
        if (be instanceof com.golemlogix.blockentity.CryoJarBlockEntity jar) {
            return jar.owner();
        }
        if (be instanceof EternalFrostResonatorBlockEntity) {
            return null;
        }
        initArsNouveau();
        if (sourceTileClass != null && sourceTileClass.isInstance(be)) {
            return new ArsSupplier(be);
        }
        return null;
    }

    /** All Source suppliers directly adjacent to the position. */
    public static List<Supplier> adjacentSuppliers(Level level, BlockPos pos) {
        List<Supplier> list = new ArrayList<>(0);
        for (Direction dir : Direction.values()) {
            BlockPos p = pos.relative(dir);
            if (!level.isLoaded(p)) {
                continue;
            }
            BlockEntity be = level.getBlockEntity(p);
            if (be == null) {
                continue;
            }
            Supplier s = asSupplier(be);
            if (s != null) {
                list.add(s);
            }
        }
        return list;
    }

    /**
     * Old API for blocks that feed from nearby (the Mana Saturator): checks/takes Source
     * within a radius of a few blocks.
     */
    public static boolean extractSource(Level level, BlockPos origin, int requiredSource, boolean simulate) {
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int dx = -2; dx <= 2; dx++) {
            for (int dy = -1; dy <= 2; dy++) {
                for (int dz = -2; dz <= 2; dz++) {
                    m.set(origin.getX() + dx, origin.getY() + dy, origin.getZ() + dz);
                    if (!level.isLoaded(m)) {
                        continue;
                    }
                    BlockEntity be = level.getBlockEntity(m);
                    if (be == null) {
                        continue;
                    }
                    Supplier s = asSupplier(be);
                    if (s == null) {
                        continue;
                    }
                    if (s.infinite()) {
                        return true;
                    }
                    if (s.available() >= requiredSource) {
                        if (!simulate) {
                            s.drain(requiredSource);
                        }
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Takes up to amount Source from the suppliers around origin (the same area as {@link #extractSource}), possibly in parts.
     * Returns how much was taken; an unlimited supplier gives everything requested.
     */
    public static int drainNearby(Level level, BlockPos origin, int amount) {
        int taken = 0;
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int dx = -2; dx <= 2 && taken < amount; dx++) {
            for (int dy = -1; dy <= 2 && taken < amount; dy++) {
                for (int dz = -2; dz <= 2 && taken < amount; dz++) {
                    m.set(origin.getX() + dx, origin.getY() + dy, origin.getZ() + dz);
                    if (!level.isLoaded(m)) {
                        continue;
                    }
                    BlockEntity be = level.getBlockEntity(m);
                    Supplier s = be == null ? null : asSupplier(be);
                    if (s == null) {
                        continue;
                    }
                    if (s.infinite()) {
                        return amount;
                    }
                    taken += s.drain(amount - taken);
                }
            }
        }
        return taken;
    }

    /**
     * Takes up to amount Source from the suppliers adjacent to the area (the set of machine positions): the area positions themselves are skipped.
     * An unlimited supplier gives everything requested.
     */
    public static int drainAround(Level level, java.util.Collection<BlockPos> region, int amount) {
        java.util.Set<BlockPos> inside = new java.util.HashSet<>(region);
        java.util.Set<BlockPos> seen = new java.util.HashSet<>();
        int taken = 0;
        for (BlockPos cell : region) {
            for (Direction dir : Direction.values()) {
                BlockPos p = cell.relative(dir);
                if (inside.contains(p) || !seen.add(p) || !level.isLoaded(p)) {
                    continue;
                }
                BlockEntity be = level.getBlockEntity(p);
                Supplier s = be == null ? null : asSupplier(be);
                if (s == null) {
                    continue;
                }
                if (s.infinite()) {
                    return amount;
                }
                taken += s.drain(amount - taken);
                if (taken >= amount) {
                    return taken;
                }
            }
        }
        return taken;
    }

    /**
     * The entity that really holds a supplier's Source: for the upper half of a Cryo-Source Jar its lower half, otherwise the entity
     * itself. The network counts every supplier once by it (a jar touching it with both halves is one jar).
     */
    public static BlockEntity owner(BlockEntity be) {
        if (be instanceof com.golemlogix.blockentity.CryoJarBlockEntity jar) {
            BlockEntity o = jar.owner();
            return o == null ? be : o;
        }
        return be;
    }

    /** Pours Source into the jar at p (a Cryo-Source Jar or an Ars Nouveau one); simulate only says how much would go in. */
    private static int fillAt(Level level, BlockPos p, int amount, boolean simulate) {
        if (amount <= 0 || !level.isLoaded(p)) {
            return 0;
        }
        BlockEntity be = level.getBlockEntity(p);
        if (be instanceof com.golemlogix.blockentity.CryoJarBlockEntity jar) {
            return jar.fill(amount, simulate);
        }
        initArsNouveau();
        if (be == null || sourceTileClass == null || !sourceTileClass.isInstance(be)) {
            return 0;
        }
        try {
            if (!(boolean) canAcceptMethod.invoke(be)) {
                return 0;
            }
            int max = (int) sourceTileClass.getMethod("getMaxSource").invoke(be);
            int cur = (int) getSourceMethod.invoke(be);
            int add = Math.max(0, Math.min(amount, max - cur));
            if (add > 0 && !simulate) {
                addSourceMethod.invoke(be, add, false);
            }
            return add;
        } catch (Throwable ignored) {
            return 0;
        }
    }

    /** Pours Source into the jars beside a block (generators). Returns how much was poured. */
    public static int pushSourceToNeighbours(Level level, BlockPos pos, int amount) {
        return fillAround(level, java.util.List.of(pos), amount, false);
    }

    /**
     * Pours up to amount Source into the jars touching a machine (its blocks are skipped); a jar touched with both halves or from
     * several blocks is counted once. Returns how much went in; simulate: how much would.
     */
    public static int fillAround(Level level, java.util.Collection<BlockPos> region, int amount, boolean simulate) {
        java.util.Set<BlockPos> inside = new java.util.HashSet<>(region);
        java.util.Set<Object> seen = new java.util.HashSet<>();
        int left = amount;
        for (BlockPos cell : region) {
            for (Direction dir : Direction.values()) {
                if (left <= 0) {
                    return amount - left;
                }
                BlockPos p = cell.relative(dir);
                if (inside.contains(p) || !level.isLoaded(p)) {
                    continue;
                }
                BlockEntity be = level.getBlockEntity(p);
                if (be == null || !seen.add(owner(be))) {
                    continue;
                }
                left -= fillAt(level, p, left, simulate);
            }
        }
        return amount - left;
    }
}
