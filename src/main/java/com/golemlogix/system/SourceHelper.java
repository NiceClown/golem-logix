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
 * Универсальный помощник Source: безлимитные блоки мода (Креативный Сосуд, Резонатор)
 * и банки / тайлы Ars Nouveau (ISourceTile, через рефлексию — Ars Nouveau необязателен).
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

    /**
     * Нужно ли платить Source. По настройке requireSource — да, но только если установлен Ars Nouveau: без него Source в выживании
     * взять негде (Резонатор делается в Насыщателе, которому самому нужен Source), поэтому сеть и Насыщатель тогда работают даром.
     */
    public static boolean sourceRequired() {
        return com.golemlogix.config.GolemConfig.REQUIRE_SOURCE.get() && arsNouveauLoaded();
    }

    /** Источник Source, подключённый к сети. */
    public interface Supplier {
        boolean infinite();

        int available();

        /** Снимает до amount, возвращает снятое. */
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
     * Возвращает поставщика Source, если блок-сущность является им. Вечный Морозный Резонатор поставщиком не считается:
     * он не кормит сеть, а дистанционно питает Портативные Хронометры (см. {@link GolemNetwork#hasResonator()}).
     */
    public static Supplier asSupplier(BlockEntity be) {
        if (be instanceof CreativeSourceJarBlockEntity) {
            return INFINITE;
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

    /** Все поставщики Source, непосредственно примыкающие к позиции. */
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
     * Старый API для блоков, питающихся рядом (Насыщатель Маны): проверяет/снимает Source
     * в радиусе нескольких блоков.
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
     * Снимает до amount Source с поставщиков вокруг origin (та же область, что у {@link #extractSource}), можно частями.
     * Возвращает, сколько снято; безлимитный поставщик отдаёт всё запрошенное.
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
     * Снимает до amount Source с поставщиков, примыкающих к области (набору позиций машины): сами позиции области пропускаются.
     * Безлимитный поставщик отдаёт всё запрошенное.
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

    /** Заливает Source в соседние банки Ars Nouveau (для генераторов). Возвращает, сколько влито. */
    public static int pushSourceToNeighbours(Level level, BlockPos pos, int amount) {
        initArsNouveau();
        if (sourceTileClass == null || amount <= 0) {
            return 0;
        }
        int left = amount;
        for (Direction dir : Direction.values()) {
            if (left <= 0) {
                break;
            }
            BlockPos p = pos.relative(dir);
            if (!level.isLoaded(p)) {
                continue;
            }
            BlockEntity be = level.getBlockEntity(p);
            if (be == null || !sourceTileClass.isInstance(be)) {
                continue;
            }
            try {
                if (!(boolean) canAcceptMethod.invoke(be)) {
                    continue;
                }
                int max = (int) sourceTileClass.getMethod("getMaxSource").invoke(be);
                int cur = (int) getSourceMethod.invoke(be);
                int add = Math.min(left, max - cur);
                if (add > 0) {
                    addSourceMethod.invoke(be, add, false);
                    left -= add;
                }
            } catch (Throwable ignored) {
            }
        }
        return amount - left;
    }
}
