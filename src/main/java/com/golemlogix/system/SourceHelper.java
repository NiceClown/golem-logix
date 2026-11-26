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
import java.util.Collection;
import java.util.HashSet;
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

    /** Whether a blood mod for the Cryo-Distillation Tower is there: Neo Vitae, or any fluid a modpack put into the tag. */
    public static boolean distillationAvailable() {
        if (ModList.get().isLoaded("neovitae")) {
            return true;
        }
        return BuiltInRegistries.FLUID.get(ModTags.CRYO_DISTILLABLE)
                .map(t -> t.size() > 0).orElse(false);
    }

    /** Whether any Source-providing magic mod (Ars Nouveau or Neo Vitae / distillable blood) is present. */
    public static boolean hasSourceProviderMod() {
        return arsNouveauLoaded() || distillationAvailable();
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
                return (int) getSourceMethod.invoke(be);
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
        if (be == null || sourceTileClass == null || !sourceTileClass.isInstance(be)) {
            return 0;
        }
        try {
            if (!(boolean) canAcceptMethod.invoke(be)) {
                return 0;
            }
            int max = (int) getMaxSourceMethod.invoke(be);
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
}
