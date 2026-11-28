package com.golemlogix.system;

import com.golemlogix.blockentity.CreativeSourceJarBlockEntity;
import com.golemlogix.blockentity.CryoJarBlockEntity;
import com.golemlogix.blockentity.EternalFrostResonatorBlockEntity;
import com.golemlogix.config.GolemConfig;
import com.golemlogix.init.ModTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.fml.ModList;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

/**
 * Universal Source helper: the mod's unlimited blocks (Creative Source Jar, Resonator)
 * and Ars Nouveau jars / tiles (ISourceTile, through reflection, so Ars Nouveau stays optional).
 */
public class SourceHelper {
    private static final Direction[] DIRECTIONS = Direction.values();
    private static Class<?> sourceTileClass = null;
    private static Method getSourceMethod = null;
    private static Method getMaxSourceMethod = null;
    private static Method removeSourceMethod = null;
    private static Method addSourceMethod = null;
    private static Method canProvideMethod = null;
    private static Method canAcceptMethod = null;
    private static volatile boolean checkedArsNouveau = false;

    private record TileCaps(boolean overridesRemove, boolean overridesAdd) {
    }

    private static final ClassValue<TileCaps> TILE_CAPS = new ClassValue<>() {
        @Override
        protected TileCaps computeValue(Class<?> type) {
            return new TileCaps(overrides2Arg(type, "removeSource"), overrides2Arg(type, "addSource"));
        }
    };

    private static boolean overrides2Arg(Class<?> type, String name) {
        if (sourceTileClass == null) {
            return false;
        }
        try {
            return type.getMethod(name, int.class, boolean.class).getDeclaringClass() != sourceTileClass;
        } catch (Throwable t) {
            return false;
        }
    }

    private static void initArsNouveau() {
        if (checkedArsNouveau) {
            return;
        }
        synchronized (SourceHelper.class) {
            if (checkedArsNouveau) {
                return;
            }
            try {
                Class<?> cls = Class.forName("com.hollingsworth.arsnouveau.api.source.ISourceTile");
                getSourceMethod = cls.getMethod("getSource");
                getMaxSourceMethod = cls.getMethod("getMaxSource");
                removeSourceMethod = cls.getMethod("removeSource", int.class, boolean.class);
                addSourceMethod = cls.getMethod("addSource", int.class, boolean.class);
                canProvideMethod = cls.getMethod("canProvideSource");
                canAcceptMethod = cls.getMethod("canAcceptSource");
                sourceTileClass = cls;
            } catch (Throwable ignored) {
                sourceTileClass = null;
            }
            checkedArsNouveau = true;
        }
    }

    public static boolean arsNouveauLoaded() {
        return ModList.get().isLoaded("ars_nouveau");
    }

    /** Whether Thaumaturge is loaded (enables Vis Condenser and Essentia Cryo-Cauldron as Source producers). */
    public static boolean thaumaturgeLoaded() {
        return ModList.get().isLoaded("thaumaturge");
    }

    /** Whether a blood mod for the Cryo-Distillation Tower is there: Neo Vitae, or any fluid a modpack put into the tag. */
    public static boolean distillationAvailable() {
        if (ModList.get().isLoaded("neovitae")) {
            return true;
        }
        return BuiltInRegistries.FLUID.get(ModTags.CRYO_DISTILLABLE)
                .map(t -> t.size() > 0).orElse(false);
    }

    /** Whether any Source-providing magic mod (Ars Nouveau, Neo Vitae / distillable blood, or Thaumaturge) is present. */
    public static boolean hasSourceProviderMod() {
        return arsNouveauLoaded() || distillationAvailable() || thaumaturgeLoaded();
    }

    /**
     * Whether Source has to be paid. By the requireSource setting yes, but only if there is a way to get it in survival: Ars Nouveau,
     * or blood for the Cryo-Distillation Tower (Neo Vitae). Without either (the Resonator is made in the Saturator, which needs Source
     * itself) the network and the Saturator work for free.
     */
    public static boolean sourceRequired() {
        return GolemConfig.REQUIRE_SOURCE.get() && hasSourceProviderMod();
    }

    /** A Source supplier connected to the network. */
    public interface Supplier {
        boolean infinite();

        int available();

        /** Takes up to amount, returns what was taken. */
        int drain(int amount);

        /** Collects the underlying physical leaf suppliers reachable through this supplier. */
        default void collectLeafSuppliers(Set<Supplier> out) {
            out.add(this);
        }

        /** Drains up to amount from leaf suppliers not yet in visited, marking each drained leaf visited. */
        default int drainUnique(int amount, Set<Supplier> visited) {
            if (amount <= 0 || !visited.add(this)) {
                return 0;
            }
            return drain(amount);
        }
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
            if (be.isRemoved()) {
                return 0;
            }
            try {
                if (!(boolean) canProvideMethod.invoke(be)) {
                    return 0;
                }
                if (TILE_CAPS.get(be.getClass()).overridesRemove()) {
                    return Math.max(0, (int) removeSourceMethod.invoke(be, Integer.MAX_VALUE, true));
                }
                return Math.max(0, (int) getSourceMethod.invoke(be));
            } catch (Throwable t) {
                return 0;
            }
        }

        @Override
        public int drain(int amount) {
            if (be.isRemoved() || amount <= 0) {
                return 0;
            }
            int take = Math.min(amount, available());
            if (take <= 0) {
                return 0;
            }
            try {
                if (TILE_CAPS.get(be.getClass()).overridesRemove()) {
                    return Math.max(0, Math.min(take, (int) removeSourceMethod.invoke(be, take, false)));
                }
                int before = (int) getSourceMethod.invoke(be);
                removeSourceMethod.invoke(be, take, false);
                int after = (int) getSourceMethod.invoke(be);
                return Math.max(0, Math.min(take, before - after));
            } catch (Throwable t) {
                return 0;
            }
        }

        @Override
        public boolean equals(Object o) {
            return this == o || (o instanceof ArsSupplier other && this.be == other.be);
        }

        @Override
        public int hashCode() {
            return System.identityHashCode(be);
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
        if (be instanceof CryoJarBlockEntity jar) {
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

    /**
     * Takes up to amount Source from the suppliers adjacent to the area (the set of machine positions): the area positions themselves are skipped.
     * An unlimited supplier gives everything requested.
     */
    public static int drainAround(Level level, Collection<BlockPos> region, int amount) {
        Set<BlockPos> inside = region.size() <= 1 ? null : new HashSet<>(region);
        Set<BlockEntity> seen = new HashSet<>();
        int taken = 0;
        for (BlockPos cell : region) {
            for (Direction dir : DIRECTIONS) {
                BlockPos p = cell.relative(dir);
                if ((inside != null && inside.contains(p)) || !level.isLoaded(p)) {
                    continue;
                }
                BlockEntity be = level.getBlockEntity(p);
                if (be == null || !seen.add(owner(be))) {
                    continue;
                }
                Supplier s = asSupplier(be);
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
        if (be instanceof CryoJarBlockEntity jar) {
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
        if (be instanceof CryoJarBlockEntity jar) {
            return jar.fill(amount, simulate);
        }
        initArsNouveau();
        if (be == null || be.isRemoved() || sourceTileClass == null || !sourceTileClass.isInstance(be)) {
            return 0;
        }
        try {
            if (!(boolean) canAcceptMethod.invoke(be)) {
                return 0;
            }
            if (TILE_CAPS.get(be.getClass()).overridesAdd()) {
                return Math.max(0, Math.min(amount, (int) addSourceMethod.invoke(be, amount, simulate)));
            }
            int max = (int) getMaxSourceMethod.invoke(be);
            int cur = (int) getSourceMethod.invoke(be);
            int add = Math.max(0, Math.min(amount, max - cur));
            if (add <= 0 || simulate) {
                return add;
            }
            addSourceMethod.invoke(be, add, false);
            int after = (int) getSourceMethod.invoke(be);
            return Math.max(0, Math.min(add, after - cur));
        } catch (Throwable ignored) {
            return 0;
        }
    }

    /** Pours Source into the jars beside a block (generators). Returns how much was poured. */
    public static int pushSourceToNeighbours(Level level, BlockPos pos, int amount) {
        return fillAround(level, List.of(pos), amount, false);
    }

    /**
     * Pours up to amount Source into the jars touching a machine (its blocks are skipped); a jar touched with both halves or from
     * several blocks is counted once. Returns how much went in; simulate: how much would.
     */
    public static int fillAround(Level level, Collection<BlockPos> region, int amount, boolean simulate) {
        Set<BlockPos> inside = region.size() <= 1 ? null : new HashSet<>(region);
        Set<BlockEntity> seen = new HashSet<>();
        int left = amount;
        for (BlockPos cell : region) {
            for (Direction dir : DIRECTIONS) {
                if (left <= 0) {
                    return amount - left;
                }
                BlockPos p = cell.relative(dir);
                if ((inside != null && inside.contains(p)) || !level.isLoaded(p)) {
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

    /**
     * Pours up to amount Source into jars touching region AND joined jars (Giga Jars / Antigravity Reactors) on every
     * network touching region (including unpowered networks so they can recover). Each jar owner is filled at most once.
     */
    public static int fillAroundAndNetworks(Level level, Collection<BlockPos> region, int amount, boolean simulate) {
        if (amount <= 0) {
            return 0;
        }
        int put = fillAround(level, region, amount, simulate);
        if (put >= amount) {
            return put;
        }
        Set<BlockPos> inside = region.size() <= 1 ? null : new HashSet<>(region);
        Set<GolemNetwork> seenNets = null;
        Set<Object> nearOwners = null;
        List<CryoJarBlockEntity> farJars = null;
        Set<Object> seenFar = null;
        for (BlockPos cell : region) {
            for (Direction d : DIRECTIONS) {
                BlockPos p = cell.relative(d);
                if ((inside != null && inside.contains(p)) || !level.isLoaded(p)) {
                    continue;
                }
                if (level.getBlockState(p).getBlock() instanceof GolemNode) {
                    GolemNetwork net = GolemNetwork.get(level, p);
                    if (seenNets == null) {
                        seenNets = Collections.newSetFromMap(new IdentityHashMap<>());
                    }
                    if (seenNets.add(net)) {
                        List<CryoJarBlockEntity> joined = net.joinedJars();
                        if (!joined.isEmpty()) {
                            if (nearOwners == null) {
                                nearOwners = collectAdjacentOwners(level, region, inside);
                            }
                            if (farJars == null) {
                                farJars = new ArrayList<>();
                                seenFar = new HashSet<>();
                            }
                            for (CryoJarBlockEntity jar : joined) {
                                CryoJarBlockEntity owner = jar.owner();
                                if (owner != null && !nearOwners.contains(owner) && seenFar.add(owner)) {
                                    farJars.add(owner);
                                }
                            }
                        }
                    }
                }
            }
        }
        if (farJars != null) {
            for (CryoJarBlockEntity jar : farJars) {
                if (put >= amount) {
                    break;
                }
                put += jar.fill(amount - put, simulate);
            }
        }
        return put;
    }

    private static Set<Object> collectAdjacentOwners(Level level, Collection<BlockPos> region, Set<BlockPos> inside) {
        Set<Object> near = new HashSet<>();
        for (BlockPos cell : region) {
            for (Direction d : DIRECTIONS) {
                BlockPos p = cell.relative(d);
                if ((inside == null || !inside.contains(p)) && level.isLoaded(p)) {
                    BlockEntity be = level.getBlockEntity(p);
                    if (be != null) {
                        near.add(owner(be));
                    }
                }
            }
        }
        return near;
    }
}
