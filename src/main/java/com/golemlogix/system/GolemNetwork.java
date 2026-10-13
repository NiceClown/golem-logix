package com.golemlogix.system;

import com.golemlogix.blockentity.AtlasCoreBlockEntity;
import com.golemlogix.blockentity.BusBlockEntity;
import com.golemlogix.blockentity.CopperDriveBayBlockEntity;
import com.golemlogix.blockentity.CryoCalculatorBlockEntity;
import com.golemlogix.blockentity.PatternProviderBlockEntity;
import com.golemlogix.blockentity.PeriscopeBlockEntity;
import com.golemlogix.blocks.AtlasCoreBlock;
import com.golemlogix.blocks.CableBushingBlock;
import net.minecraft.world.level.block.Block;
import com.golemlogix.blocks.GolemProperties;
import com.golemlogix.blocks.PeriscopeStorageBusBlock;
import com.golemlogix.config.GolemConfig;
import com.golemlogix.crafting.CraftingJob;
import com.golemlogix.crafting.CraftingPlan;
import com.golemlogix.crafting.CraftingPlanner;
import com.golemlogix.data.PatternData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;

import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Сеть Голем Логикс.
 *
 * Строится обходом соседних блоков-узлов (GolemNode). Для работы нужно Ядро Атланта с питанием Source.
 * Каждое устройство занимает каналы; канал идёт от ядра по дереву труб, у каждой трубы свой лимит
 * (базовая снежная труба 16, плотная морозная 64). Из сети во внешний мир каналы выходят через Кабельные Муфты:
 * у достроенной Решётки Атланта их 5 и у каждой «линия» из 64 каналов (всего 320); Расширители Каналов в стенах
 * решётки поднимают ёмкость каждой линии. Без решётки ядро даёт лишь 8 каналов «на старте».
 */
public final class GolemNetwork {
    public static final int MAX_NODES = 4096;
    public static final int BARE_CORE_CHANNELS = 8;

    private final Level level;
    private final BlockPos start;

    private BlockPos corePos;
    private AtlasCoreBlockEntity core;
    private AtlasStructure.Result structure;
    private int lineCapacity = BARE_CORE_CHANNELS;
    private int channelCapacity;
    private int channelsUsed;
    private int deviceCount;

    private final Set<BlockPos> nodes = new HashSet<>();
    private final Set<BlockPos> activeDevices = new HashSet<>();
    /** Сколько каналов проходит через каждый узел-проводник (трубы, муфты) — для подсказок и ограничений. */
    private final Map<BlockPos, Integer> load = new HashMap<>();
    private final List<CopperDriveBayBlockEntity> bays = new ArrayList<>();
    private final List<External> externals = new ArrayList<>();
    private final List<PatternProviderBlockEntity> providers = new ArrayList<>();
    private final List<com.golemlogix.blockentity.CardCatalogBlockEntity> catalogs = new ArrayList<>();
    private final List<BusBlockEntity> buses = new ArrayList<>();
    private final List<com.golemlogix.crafting.CraftingCpu> cpus = new ArrayList<>();
    private final List<SourceHelper.Supplier> sources = new ArrayList<>();
    private int resonators = 0;

    private Map<ItemKey, PatternRef> patternIndex;

    private GolemNetwork(Level level, BlockPos start) {
        this.level = level;
        this.start = start;
    }

    /** Внешний контейнер за Перископом: приоритет берётся с самого перископа. */
    public record External(BlockPos target, Direction face, int priority, BlockPos periscope) {
    }

    public record PatternRef(PatternData data, PatternProviderBlockEntity provider) {
    }

    // ------------------------------------------------------------------ cache

    private record CacheKey(ResourceKey<Level> dim, BlockPos pos) {
    }

    private record CacheEntry(GolemNetwork network, long time) {
    }

    private static final Map<CacheKey, CacheEntry> CACHE = new HashMap<>();
    private static final int CACHE_TTL = 8;

    /** Сеть с коротким кэшем — для тикающих устройств. */
    public static GolemNetwork get(Level level, BlockPos start) {
        long now = level.getGameTime();
        CacheKey key = new CacheKey(level.dimension(), start.immutable());
        CacheEntry e = CACHE.get(key);
        if (e != null && now - e.time < CACHE_TTL && now >= e.time) {
            return e.network;
        }
        if (CACHE.size() > 8192) {
            Iterator<Map.Entry<CacheKey, CacheEntry>> it = CACHE.entrySet().iterator();
            while (it.hasNext()) {
                CacheEntry c = it.next().getValue();
                if (now - c.time >= CACHE_TTL || now < c.time) {
                    it.remove();
                }
            }
        }
        GolemNetwork net = scan(level, start);
        CacheEntry entry = new CacheEntry(net, now);
        CACHE.put(key, entry);
        // из любого узла обход даёт ту же сеть: остальные узлы (шины, калькуляторы, терминалы) получают её без повторного обхода
        for (BlockPos p : net.nodes) {
            CACHE.put(new CacheKey(level.dimension(), p), entry);
        }
        return net;
    }

    public static void clearCache() {
        CACHE.clear();
    }

    // ------------------------------------------------------------------- scan

    public static GolemNetwork scan(Level level, BlockPos start) {
        GolemNetwork net = new GolemNetwork(level, start.immutable());
        net.doScan();
        return net;
    }

    private void doScan() {
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        queue.add(start);
        Set<BlockPos> seen = new HashSet<>();
        seen.add(start);

        while (!queue.isEmpty() && nodes.size() < MAX_NODES) {
            BlockPos pos = queue.poll();
            if (!level.isLoaded(pos)) {
                continue;
            }
            BlockState state = level.getBlockState(pos);
            if (!(state.getBlock() instanceof GolemNode)) {
                continue;
            }
            nodes.add(pos);

            if (state.getBlock() instanceof AtlasCoreBlock && corePos == null) {
                corePos = pos;
            }
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof CopperDriveBayBlockEntity bay) {
                bays.add(bay);
            } else if (be instanceof BusBlockEntity bus) {
                buses.add(bus);
            } else if (be instanceof PatternProviderBlockEntity provider) {
                providers.add(provider);
            } else if (be instanceof com.golemlogix.blockentity.CardCatalogBlockEntity catalog) {
                catalogs.add(catalog);
            } else if (be instanceof CryoCalculatorBlockEntity cpu) {
                cpus.add(cpu);
            } else if (be instanceof com.golemlogix.blockentity.CryoMainframeBlockEntity mainframe) {
                // ядра мейнфрейма — отдельные вычислители, но канал у них один (у блока-хозяина)
                cpus.addAll(mainframe.cores());
            } else if (be instanceof AtlasCoreBlockEntity c && pos.equals(corePos)) {
                core = c;
            }
            if (state.getBlock() instanceof PeriscopeStorageBusBlock) {
                Direction facing = state.getValue(PeriscopeStorageBusBlock.FACING);
                BlockPos target = pos.relative(facing);
                if (level.isLoaded(target) && !(level.getBlockState(target).getBlock() instanceof GolemNode)) {
                    PeriscopeBlockEntity pb = level.getBlockEntity(pos) instanceof PeriscopeBlockEntity p ? p : null;
                    // Модуль Редстоуна перископа отключает хранилище по сигналу
                    if (pb == null || pb.storageEnabled()) {
                        externals.add(new External(target, facing.getOpposite(), pb == null ? 0 : pb.getPriority(), pos));
                    }
                }
            }

            for (Direction dir : Direction.values()) {
                BlockPos next = pos.relative(dir);
                // в «увиденные» узел попадает, только если связь есть: к разъёму многоблока можно прийти с другой стороны
                if (!seen.contains(next) && linkable(pos, state, next, dir)) {
                    seen.add(next);
                    queue.add(next);
                }
            }
        }

        // источники Source рядом с узлами; Резонатор не источник для сети — он питает Хронометры, привязанные к ней
        Set<BlockEntity> seenSources = new HashSet<>();
        for (BlockPos pos : nodes) {
            for (Direction dir : Direction.values()) {
                BlockPos p = pos.relative(dir);
                if (nodes.contains(p) || !level.isLoaded(p)) {
                    continue;
                }
                BlockEntity be = level.getBlockEntity(p);
                if (be != null && seenSources.add(be)) {
                    if (be instanceof com.golemlogix.blockentity.EternalFrostResonatorBlockEntity) {
                        resonators++;
                        continue;
                    }
                    SourceHelper.Supplier s = SourceHelper.asSupplier(be);
                    if (s != null) {
                        sources.add(s);
                    }
                }
            }
        }

        if (corePos != null) {
            structure = AtlasStructure.check(level, corePos);
            if (structure.valid()) {
                lineCapacity = GolemConfig.LINE_CHANNELS.get() + GolemConfig.EXPANDER_BONUS.get() * structure.expanders()
                        + GolemConfig.QUANTUM_BONUS.get() * structure.quantumExpanders();
                channelCapacity = lineCapacity * AtlasStructure.BUSHINGS;
            } else {
                lineCapacity = BARE_CORE_CHANNELS;
                channelCapacity = BARE_CORE_CHANNELS;
            }
            allocateChannels();
            // хранилища и поставщики без канала молчат, как терминалы и шины
            bays.removeIf(b -> !activeDevices.contains(b.getBlockPos()));
            externals.removeIf(e -> !activeDevices.contains(e.periscope()));
            providers.removeIf(p -> !activeDevices.contains(p.getBlockPos()));
            catalogs.removeIf(p -> !activeDevices.contains(p.getBlockPos()));
            // два перископа на один сундук не должны удваивать его содержимое
            Set<BlockPos> targets = new HashSet<>();
            externals.removeIf(e -> !targets.add(e.target()));
        } else {
            channelCapacity = BARE_CORE_CHANNELS;
        }
    }

    /** Два соседних узла соединяются, только если каждый согласен на связь с другим (dir — от from к соседу). */
    private boolean linkable(BlockPos fromPos, BlockState from, BlockPos nextPos, Direction dir) {
        if (!level.isLoaded(nextPos)) {
            return false;
        }
        return GolemNode.linked(level, fromPos, from, nextPos, level.getBlockState(nextPos), dir);
    }

    /** Сколько каналов выдаёт наружу грань Расширителя Каналов (только в достроенной решётке). */
    private int expanderExit(com.golemlogix.blocks.ChannelExpanderBlock ex) {
        if (structure == null || !structure.valid()) {
            return 0;
        }
        return ex.isQuantum() ? GolemConfig.QUANTUM_EXIT.get() : GolemConfig.EXPANDER_EXIT.get();
    }

    /** Ёмкость выхода, через который идут каналы в этот узел (для подсказок): муфта — линия, расширитель — свой выход. */
    public int exitCapacity(BlockPos pos) {
        Block b = level.getBlockState(pos).getBlock();
        if (b instanceof CableBushingBlock) {
            return lineCapacity;
        }
        if (b instanceof com.golemlogix.blocks.ChannelExpanderBlock ex) {
            return expanderExit(ex);
        }
        return b instanceof GolemNode n ? n.channelCapacity() : 0;
    }

    /** Узел входит в сеть (обход до него дошёл). */
    public boolean contains(BlockPos pos) {
        return nodes.contains(pos);
    }

    /** Раздаёт каналы устройствам по дереву путей от ядра. */
    private void allocateChannels() {
        Map<BlockPos, BlockPos> parent = new HashMap<>();
        List<BlockPos> order = new ArrayList<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        queue.add(corePos);
        parent.put(corePos, null);
        while (!queue.isEmpty()) {
            BlockPos pos = queue.poll();
            order.add(pos);
            BlockState from = level.getBlockState(pos);
            for (Direction dir : Direction.values()) {
                BlockPos next = pos.relative(dir);
                if (nodes.contains(next) && !parent.containsKey(next) && linkable(pos, from, next, dir)) {
                    parent.put(next, pos);
                    queue.add(next);
                }
            }
        }

        for (BlockPos pos : order) {
            BlockState state = level.getBlockState(pos);
            int cost = ((GolemNode) state.getBlock()).channelCost(state);
            if (cost <= 0) {
                continue;
            }
            deviceCount++;
            if (channelsUsed + cost > channelCapacity) {
                continue;
            }
            boolean ok = true;
            boolean exitCapped = false;
            List<BlockPos> path = new ArrayList<>();
            for (BlockPos p = parent.get(pos); p != null && !p.equals(corePos); p = parent.get(p)) {
                Block block = level.getBlockState(p).getBlock();
                int cap;
                if (block instanceof CableBushingBlock) {
                    // только ближайшая к устройству муфта («выход» из решётки) ограничена ёмкостью линии
                    cap = exitCapped ? Integer.MAX_VALUE : lineCapacity;
                    exitCapped = true;
                } else if (block instanceof com.golemlogix.blocks.ChannelExpanderBlock ex) {
                    // расширитель в стене решётки — тоже выход наружу, со своей ёмкостью
                    cap = exitCapped ? Integer.MAX_VALUE : expanderExit(ex);
                    exitCapped = true;
                } else {
                    cap = ((GolemNode) block).channelCapacity();
                }
                if (load.getOrDefault(p, 0) + cost > cap) {
                    ok = false;
                    break;
                }
                path.add(p);
            }
            if (!ok) {
                continue;
            }
            for (BlockPos p : path) {
                load.merge(p, cost, Integer::sum);
            }
            channelsUsed += cost;
            activeDevices.add(pos);
        }
    }

    // ----------------------------------------------------------------- status

    public Level level() {
        return level;
    }

    public boolean hasCore() {
        return corePos != null;
    }

    @Nullable
    public BlockPos corePos() {
        return corePos;
    }

    @Nullable
    public AtlasCoreBlockEntity core() {
        return core;
    }

    /** Ядро есть и запитано Source. */
    public boolean isOnline() {
        return core != null && core.isPowered();
    }

    public int channelCapacity() {
        return channelCapacity;
    }

    public int channelsUsed() {
        return channelsUsed;
    }

    public int deviceCount() {
        return deviceCount;
    }

    /** Сколько каналов сейчас идёт через узел (например, через кабельную муфту). */
    public int loadAt(BlockPos pos) {
        return load.getOrDefault(pos, 0);
    }

    /** Ёмкость одной линии (одной кабельной муфты). */
    public int lineCapacity() {
        return lineCapacity;
    }

    @Nullable
    public AtlasStructure.Result structure() {
        return structure;
    }

    public boolean isDeviceActive(BlockPos pos) {
        return activeDevices.contains(pos);
    }

    public List<SourceHelper.Supplier> sources() {
        return sources;
    }

    /** К сети приставлен Вечный Морозный Резонатор: Хронометры этой сети не нужно заряжать. */
    public boolean hasResonator() {
        return resonators > 0;
    }

    public boolean hasInfiniteSource() {
        for (SourceHelper.Supplier s : sources) {
            if (s.infinite()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Очерёдность шины среди всех шин сети: 0 — у самой приоритетной. Шина работает в такт «время - очерёдность»,
     * поэтому шины с большим приоритетом делают свой ход раньше остальных в каждом периоде.
     */
    public int busRank(BusBlockEntity bus) {
        List<BusBlockEntity> sorted = new ArrayList<>(buses);
        sorted.sort(java.util.Comparator.comparingInt(BusBlockEntity::getPriority).reversed()
                .thenComparingLong(b -> b.getBlockPos().asLong()));
        int idx = sorted.indexOf(bus);
        return Math.max(0, idx);
    }

    public List<com.golemlogix.crafting.CraftingCpu> cpus() {
        return cpus;
    }

    public List<PatternProviderBlockEntity> providers() {
        return providers;
    }

    /** Картотеки перфокарт с каналом: их верстачные шаблоны выполняют сами калькуляторы. */
    public List<com.golemlogix.blockentity.CardCatalogBlockEntity> catalogs() {
        return catalogs;
    }

    /** Для терминала: можно ли им пользоваться. Возвращает null или ключ сообщения об ошибке. */
    @Nullable
    public String accessError(@Nullable BlockPos devicePos) {
        if (!hasCore()) {
            return "message.golem_logix.no_core";
        }
        if (!isOnline()) {
            return "message.golem_logix.no_power";
        }
        if (devicePos != null && !isDeviceActive(devicePos)) {
            return "message.golem_logix.no_channels";
        }
        return null;
    }

    /**
     * Включает/выключает анимации труб, шкафов и терминалов (свойство active): сеть запитана и — для устройств, которым нужен
     * канал, — канал им достался. Раньше флаг получали все узлы подряд, и устройства сверх лимита каналов (или за трубой с
     * меньшей ёмкостью) выглядели рабочими. Калькулятор свой флаг ставит сам (он означает «идёт задача»).
     */
    public void syncActiveVisuals(boolean active) {
        for (BlockPos pos : nodes) {
            BlockState state = level.getBlockState(pos);
            if (!state.hasProperty(GolemProperties.ACTIVE) || state.getBlock() instanceof com.golemlogix.blocks.CryoCalculatorBlock) {
                continue;
            }
            boolean on = active && (((GolemNode) state.getBlock()).channelCost(state) <= 0 || activeDevices.contains(pos));
            if (state.getValue(GolemProperties.ACTIVE) != on) {
                level.setBlock(pos, state.setValue(GolemProperties.ACTIVE, on), 18);
            }
        }
    }

    // ---------------------------------------------------------------- storage

    private List<ItemStack> allDisks(boolean fluid) {
        List<ItemStack> disks = new ArrayList<>();
        for (CopperDriveBayBlockEntity bay : bays) {
            if (bay.isRemoved()) {
                continue;
            }
            for (ItemStack disk : bay.getDisks()) {
                if (fluid ? DiskStorage.isFluidDisk(disk) : DiskStorage.isItemDisk(disk)) {
                    disks.add(disk);
                }
            }
        }
        return disks;
    }

    private void markBaysChanged() {
        for (CopperDriveBayBlockEntity bay : bays) {
            bay.setChanged();
            bay.refreshVisuals();
        }
    }

    /**
     * Обработчик предметов контейнера за перископом. Сначала грань, к которой прижат перископ, потом «общая» (null)
     * и остальные грани: многие баки и сундуки отдают доступ только с некоторых сторон.
     */
    @Nullable
    private IItemHandler itemHandler(External ex) {
        if (!level.isLoaded(ex.target)) {
            return null;
        }
        IItemHandler h = level.getCapability(Capabilities.ItemHandler.BLOCK, ex.target, ex.face);
        if (h == null) {
            h = level.getCapability(Capabilities.ItemHandler.BLOCK, ex.target, null);
        }
        for (Direction d : Direction.values()) {
            if (h != null) {
                break;
            }
            h = level.getCapability(Capabilities.ItemHandler.BLOCK, ex.target, d);
        }
        return h;
    }

    @Nullable
    private IFluidHandler fluidHandler(External ex) {
        if (!level.isLoaded(ex.target)) {
            return null;
        }
        IFluidHandler h = level.getCapability(Capabilities.FluidHandler.BLOCK, ex.target, ex.face);
        if (h == null) {
            h = level.getCapability(Capabilities.FluidHandler.BLOCK, ex.target, null);
        }
        for (Direction d : Direction.values()) {
            if (h != null) {
                break;
            }
            h = level.getCapability(Capabilities.FluidHandler.BLOCK, ex.target, d);
        }
        return h;
    }

    /** Хранилища (шкафы и перископы), от высшего приоритета к низшему; равные идут в порядке обхода сети. */
    private record Tier(int priority, List<CopperDriveBayBlockEntity> bays, List<External> exts) {
    }

    private List<Tier> tiers(boolean highestFirst) {
        java.util.Comparator<Integer> order = highestFirst ? java.util.Comparator.<Integer>reverseOrder() : java.util.Comparator.<Integer>naturalOrder();
        java.util.TreeMap<Integer, Tier> map = new java.util.TreeMap<Integer, Tier>(order);
        for (CopperDriveBayBlockEntity bay : bays) {
            if (!bay.isRemoved()) {
                map.computeIfAbsent(bay.getPriority(), p -> new Tier(p, new ArrayList<>(), new ArrayList<>())).bays.add(bay);
            }
        }
        for (External ex : externals) {
            map.computeIfAbsent(ex.priority(), p -> new Tier(p, new ArrayList<>(), new ArrayList<>())).exts.add(ex);
        }
        return new ArrayList<>(map.values());
    }

    private static List<ItemStack> disksOf(Tier tier, boolean fluid) {
        List<ItemStack> disks = new ArrayList<>();
        for (CopperDriveBayBlockEntity bay : tier.bays) {
            for (ItemStack disk : bay.getDisks()) {
                if (fluid ? DiskStorage.isFluidDisk(disk) : DiskStorage.isItemDisk(disk)) {
                    disks.add(disk);
                }
            }
        }
        return disks;
    }

    /** Все предметы сети: тип -> количество. */
    public Map<ItemKey, Long> listItems() {
        Map<ItemKey, Long> all = new LinkedHashMap<>();
        for (ItemStack disk : allDisks(false)) {
            for (Map.Entry<ItemKey, Integer> e : DiskStorage.items(disk).entrySet()) {
                all.merge(e.getKey(), (long) e.getValue(), Long::sum);
            }
        }
        for (External ex : externals) {
            IItemHandler h = itemHandler(ex);
            if (h == null) {
                continue;
            }
            for (int i = 0; i < h.getSlots(); i++) {
                ItemStack s = h.getStackInSlot(i);
                if (!s.isEmpty()) {
                    all.merge(ItemKey.of(s), (long) s.getCount(), Long::sum);
                }
            }
        }
        return all;
    }

    public long itemCountOf(ItemKey key) {
        long n = 0;
        for (ItemStack disk : allDisks(false)) {
            n += DiskStorage.items(disk).getOrDefault(key, 0);
        }
        for (External ex : externals) {
            IItemHandler h = itemHandler(ex);
            if (h == null) {
                continue;
            }
            for (int i = 0; i < h.getSlots(); i++) {
                ItemStack s = h.getStackInSlot(i);
                if (!s.isEmpty() && key.matches(s)) {
                    n += s.getCount();
                }
            }
        }
        return n;
    }

    public long itemCapacity() {
        long cap = 0;
        for (ItemStack disk : allDisks(false)) {
            cap += DiskStorage.capacity(disk);
        }
        return cap;
    }

    public long itemsStoredOnDisks() {
        long used = 0;
        for (ItemStack disk : allDisks(false)) {
            used += DiskStorage.storedItems(disk);
        }
        return used;
    }

    public long fluidCapacity() {
        long cap = 0;
        for (ItemStack disk : allDisks(true)) {
            cap += DiskStorage.capacity(disk);
        }
        return cap;
    }

    public long fluidStoredOnDisks() {
        long used = 0;
        for (ItemStack disk : allDisks(true)) {
            used += DiskStorage.storedItems(disk);
        }
        return used;
    }

    /**
     * Кладёт предметы в сеть. Возвращает остаток, который не влез.
     * Порядок: от хранилища с наибольшим приоритетом к наименьшему; внутри одного приоритета сначала диски,
     * где такой предмет уже лежит, потом любые диски, затем внешние контейнеры за перископами.
     */
    public ItemStack insertItem(ItemStack input, boolean simulate) {
        return insertItem(input, simulate, null);
    }

    /**
     * То же, но в контейнер skipTarget (если он подключён перископом) не кладём: шина импорта не должна возвращать предметы
     * в тот же сундук, из которого их только что забрала, — иначе она гоняет их по кругу и щёлкает без толку.
     */
    public ItemStack insertItem(ItemStack input, boolean simulate, @Nullable BlockPos skipTarget) {
        if (input.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack remaining = input.copy();
        List<Tier> tiers = tiers(true);

        if (simulate) {
            long free = 0;
            for (ItemStack disk : allDisks(false)) {
                free += DiskStorage.capacity(disk) - DiskStorage.storedItems(disk);
            }
            remaining.shrink((int) Math.min(free, remaining.getCount()));
        } else {
            boolean changed = false;
            for (Tier tier : tiers) {
                List<ItemStack> disks = disksOf(tier, false);
                for (int pass = 0; pass < 2 && !remaining.isEmpty(); pass++) {
                    for (ItemStack disk : disks) {
                        if (remaining.isEmpty()) {
                            break;
                        }
                        if (pass == 0 && !DiskStorage.containsItem(disk, remaining)) {
                            continue;
                        }
                        int n = DiskStorage.insertItem(disk, remaining, false);
                        if (n > 0) {
                            remaining.shrink(n);
                            changed = true;
                        }
                    }
                }
                remaining = insertIntoExternals(tier, remaining, false, skipTarget);
                if (remaining.isEmpty()) {
                    break;
                }
            }
            if (changed) {
                markBaysChanged();
            }
            return remaining;
        }
        // имитация: диски уже учтены суммой свободного места, внешние проверяем по очереди
        for (Tier tier : tiers) {
            if (remaining.isEmpty()) {
                break;
            }
            remaining = insertIntoExternals(tier, remaining, true, skipTarget);
        }
        return remaining;
    }

    private ItemStack insertIntoExternals(Tier tier, ItemStack remaining, boolean simulate, @Nullable BlockPos skipTarget) {
        for (External ex : tier.exts) {
            if (remaining.isEmpty()) {
                break;
            }
            if (ex.target().equals(skipTarget)) {
                continue;
            }
            IItemHandler h = itemHandler(ex);
            if (h == null) {
                continue;
            }
            for (int i = 0; i < h.getSlots() && !remaining.isEmpty(); i++) {
                remaining = h.insertItem(i, remaining, simulate);
            }
        }
        return remaining;
    }

    /**
     * Достаёт предметы из сети (не больше amount). Возвращает полученный стак (count может быть больше стака).
     * Берёт сперва из хранилищ с НАИМЕНЬШИМ приоритетом, чтобы высокоприоритетные оставались «основным складом».
     */
    public ItemStack extractItem(ItemStack template, int amount, boolean simulate) {
        return extractItem(template, amount, simulate, null);
    }

    /** То же, но из контейнера skipTarget не берём: шина экспорта не перекладывает предметы из сундука в тот же сундук. */
    public ItemStack extractItem(ItemStack template, int amount, boolean simulate, @Nullable BlockPos skipTarget) {
        if (template.isEmpty() || amount <= 0) {
            return ItemStack.EMPTY;
        }
        int left = amount;
        boolean changed = false;

        for (Tier tier : tiers(false)) {
            if (left <= 0) {
                break;
            }
            for (ItemStack disk : disksOf(tier, false)) {
                if (left <= 0) {
                    break;
                }
                int n = DiskStorage.extractItem(disk, template, left, simulate);
                if (n > 0) {
                    left -= n;
                    changed = true;
                }
            }
            for (External ex : tier.exts) {
                if (left <= 0) {
                    break;
                }
                if (ex.target().equals(skipTarget)) {
                    continue;
                }
                IItemHandler h = itemHandler(ex);
                if (h == null) {
                    continue;
                }
                for (int i = 0; i < h.getSlots() && left > 0; i++) {
                    ItemStack s = h.getStackInSlot(i);
                    if (!s.isEmpty() && ItemStack.isSameItemSameComponents(s, template)) {
                        ItemStack got = h.extractItem(i, Math.min(left, s.getCount()), simulate);
                        left -= got.getCount();
                    }
                }
            }
        }
        if (changed && !simulate) {
            markBaysChanged();
        }
        int taken = amount - left;
        return taken > 0 ? template.copyWithCount(taken) : ItemStack.EMPTY;
    }
    /** Все жидкости сети: тип -> mB. */
    public List<FluidStack> listFluids() {
        List<FluidStack> all = new ArrayList<>();
        for (ItemStack disk : allDisks(true)) {
            for (FluidStack f : DiskStorage.fluids(disk)) {
                mergeFluid(all, f);
            }
        }
        for (External ex : externals) {
            IFluidHandler h = fluidHandler(ex);
            if (h == null) {
                continue;
            }
            for (int i = 0; i < h.getTanks(); i++) {
                FluidStack f = h.getFluidInTank(i);
                if (!f.isEmpty()) {
                    mergeFluid(all, f);
                }
            }
        }
        return all;
    }

    private static void mergeFluid(List<FluidStack> all, FluidStack f) {
        for (int i = 0; i < all.size(); i++) {
            FluidStack e = all.get(i);
            if (FluidStack.isSameFluidSameComponents(e, f)) {
                all.set(i, e.copyWithAmount((int) Math.min((long) e.getAmount() + f.getAmount(), Integer.MAX_VALUE)));
                return;
            }
        }
        all.add(f.copy());
    }

    /** Кладёт жидкость в сеть (порядок — как у предметов: от высшего приоритета). Возвращает, сколько mB принято. */
    public int insertFluid(FluidStack input, boolean simulate) {
        return insertFluid(input, simulate, null);
    }

    public int insertFluid(FluidStack input, boolean simulate, @Nullable BlockPos skipTarget) {
        if (input.isEmpty()) {
            return 0;
        }
        int left = input.getAmount();
        boolean changed = false;
        if (simulate) {
            long free = 0;
            for (ItemStack disk : allDisks(true)) {
                free += DiskStorage.capacity(disk) - DiskStorage.storedItems(disk);
            }
            left = (int) Math.max(0, input.getAmount() - free);
        }
        for (Tier tier : tiers(true)) {
            if (left <= 0) {
                break;
            }
            if (!simulate) {
                List<ItemStack> disks = disksOf(tier, true);
                for (int pass = 0; pass < 2 && left > 0; pass++) {
                    for (ItemStack disk : disks) {
                        if (left <= 0) {
                            break;
                        }
                        if (pass == 0 && !DiskStorage.containsFluid(disk, input)) {
                            continue;
                        }
                        int n = DiskStorage.insertFluid(disk, input.copyWithAmount(left), false);
                        if (n > 0) {
                            left -= n;
                            changed = true;
                        }
                    }
                }
            }
            for (External ex : tier.exts) {
                if (left <= 0) {
                    break;
                }
                if (ex.target().equals(skipTarget)) {
                    continue;
                }
                IFluidHandler h = fluidHandler(ex);
                if (h == null) {
                    continue;
                }
                int n = h.fill(input.copyWithAmount(left), simulate ? IFluidHandler.FluidAction.SIMULATE : IFluidHandler.FluidAction.EXECUTE);
                left -= n;
            }
        }
        if (changed) {
            markBaysChanged();
        }
        return input.getAmount() - left;
    }

    /** Достаёт жидкость из сети (сперва из хранилищ с наименьшим приоритетом). */
    public FluidStack extractFluid(FluidStack template, int amount, boolean simulate) {
        return extractFluid(template, amount, simulate, null);
    }

    public FluidStack extractFluid(FluidStack template, int amount, boolean simulate, @Nullable BlockPos skipTarget) {
        if (template.isEmpty() || amount <= 0) {
            return FluidStack.EMPTY;
        }
        int left = amount;
        boolean changed = false;
        for (Tier tier : tiers(false)) {
            if (left <= 0) {
                break;
            }
            for (ItemStack disk : disksOf(tier, true)) {
                if (left <= 0) {
                    break;
                }
                int n = DiskStorage.extractFluid(disk, template, left, simulate);
                if (n > 0) {
                    left -= n;
                    changed = true;
                }
            }
            for (External ex : tier.exts) {
                if (left <= 0) {
                    break;
                }
                if (ex.target().equals(skipTarget)) {
                    continue;
                }
                IFluidHandler h = fluidHandler(ex);
                if (h == null) {
                    continue;
                }
                FluidStack got = h.drain(template.copyWithAmount(left), simulate ? IFluidHandler.FluidAction.SIMULATE : IFluidHandler.FluidAction.EXECUTE);
                left -= got.getAmount();
            }
        }
        if (changed && !simulate) {
            markBaysChanged();
        }
        int taken = amount - left;
        return taken > 0 ? template.copyWithAmount(taken) : FluidStack.EMPTY;
    }
    // --------------------------------------------------------------- crafting

    /** Индекс «предмет -> шаблон, который его производит». */
    public Map<ItemKey, PatternRef> patternIndex() {
        if (patternIndex == null) {
            patternIndex = new LinkedHashMap<>();
            for (PatternProviderBlockEntity provider : providers) {
                if (provider.isRemoved()) {
                    continue;
                }
                for (PatternData pattern : provider.getPatterns()) {
                    for (ItemKey out : pattern.outputTotals().keySet()) {
                        patternIndex.putIfAbsent(out, new PatternRef(pattern, provider));
                    }
                }
            }
            for (com.golemlogix.blockentity.CardCatalogBlockEntity catalog : catalogs) {
                if (catalog.isRemoved()) {
                    continue;
                }
                for (PatternData pattern : catalog.patterns()) {
                    for (ItemKey out : pattern.outputTotals().keySet()) {
                        patternIndex.putIfAbsent(out, new PatternRef(pattern, null));
                    }
                }
            }
        }
        return patternIndex;
    }

    public boolean canCraft(ItemKey key) {
        return patternIndex().containsKey(key);
    }

    @Nullable
    public PatternProviderBlockEntity providerFor(PatternData pattern) {
        for (PatternProviderBlockEntity provider : providers) {
            if (!provider.isRemoved() && provider.hasPattern(pattern)) {
                return provider;
            }
        }
        return null;
    }

    public enum StartType {
        STARTED, OFFLINE, NO_PATTERN, MISSING, NO_CPU, RESERVE_FAILED
    }

    public record StartResult(StartType type, CraftingPlan plan) {
    }

    /** Планирует и запускает автокрафт на первом свободном Крио-Калькуляторе. */
    public StartResult startCrafting(ItemKey target, long amount) {
        Map<ItemKey, Long> one = new LinkedHashMap<>();
        one.put(target, amount);
        return startCrafting(one, null, null);
    }

    /** Есть ли свободный калькулятор с каналом. */
    @Nullable
    public com.golemlogix.crafting.CraftingCpu freeCpu() {
        for (com.golemlogix.crafting.CraftingCpu c : cpus) {
            if (!c.isRemoved() && c.isIdle() && c.canRun(this)) {
                return c;
            }
        }
        return null;
    }

    /**
     * Планирует и запускает автокрафт сразу нескольких предметов на первом свободном Крио-Калькуляторе.
     * requester — игрок, которому придёт уведомление о готовности; bus — шина экспорта, заказавшая крафт сама.
     */
    public StartResult startCrafting(Map<ItemKey, Long> targets, @Nullable java.util.UUID requester, @Nullable BlockPos bus) {
        if (!isOnline()) {
            return new StartResult(StartType.OFFLINE, null);
        }
        if (targets.isEmpty()) {
            return new StartResult(StartType.NO_PATTERN, null);
        }
        for (ItemKey t : targets.keySet()) {
            if (!canCraft(t)) {
                return new StartResult(StartType.NO_PATTERN, null);
            }
        }
        CraftingPlan plan = CraftingPlanner.plan(this, targets);
        if (!plan.missing().isEmpty()) {
            return new StartResult(StartType.MISSING, plan);
        }
        if (plan.steps().isEmpty()) {
            return new StartResult(StartType.NO_PATTERN, plan);
        }
        com.golemlogix.crafting.CraftingCpu cpu = freeCpu();
        if (cpu == null) {
            return new StartResult(StartType.NO_CPU, plan);
        }

        // резервируем исходные материалы: всё или ничего
        for (Map.Entry<ItemKey, Long> e : plan.fromStorage().entrySet()) {
            if (itemCountOf(e.getKey()) < e.getValue()) {
                return new StartResult(StartType.RESERVE_FAILED, plan);
            }
        }
        Map<ItemKey, Long> reserved = new LinkedHashMap<>();
        for (Map.Entry<ItemKey, Long> e : plan.fromStorage().entrySet()) {
            long left = e.getValue();
            while (left > 0) {
                int chunk = (int) Math.min(left, 1_000_000_000L);
                ItemStack got = extractItem(e.getKey().stack(), chunk, false);
                if (got.isEmpty()) {
                    break;
                }
                reserved.merge(e.getKey(), (long) got.getCount(), Long::sum);
                left -= got.getCount();
            }
            if (left > 0) {
                // гонка: вернуть взятое
                for (Map.Entry<ItemKey, Long> r : reserved.entrySet()) {
                    long n = r.getValue();
                    while (n > 0) {
                        int chunk = (int) Math.min(n, 1_000_000_000L);
                        ItemStack rest = insertItem(r.getKey().toStack(chunk), false);
                        n -= chunk - rest.getCount();
                        if (!rest.isEmpty()) {
                            break;
                        }
                    }
                }
                return new StartResult(StartType.RESERVE_FAILED, plan);
            }
        }
        cpu.startJob(new CraftingJob(plan, reserved).requestedBy(requester, bus));
        return new StartResult(StartType.STARTED, plan);
    }

    /** Поставщик ждёт возврата результата прошлой порции (режим ожидания): какая-то задача ещё не получила его выход. */
    public boolean providerBusy(BlockPos provider) {
        for (com.golemlogix.crafting.CraftingCpu cpu : cpus) {
            if (!cpu.isRemoved() && cpu.getJob() != null && cpu.getJob().hasInFlight(provider)) {
                return true;
            }
        }
        return false;
    }

    /** Уже идёт задача, которую заказала эта шина, на этот предмет. */
    public boolean busCraftPending(BlockPos bus, ItemKey key) {
        for (com.golemlogix.crafting.CraftingCpu cpu : cpus) {
            CraftingJob job = cpu.isRemoved() ? null : cpu.getJob();
            if (job != null && bus.equals(job.requesterBus()) && job.targets().containsKey(key)) {
                return true;
            }
        }
        return false;
    }

    /** Хватает ли Source на списание amount (ничего не снимает). */
    public boolean hasSource(int amount) {
        if (amount <= 0 || !SourceHelper.sourceRequired() || hasInfiniteSource()) {
            return true;
        }
        long total = 0;
        for (SourceHelper.Supplier s : sources) {
            total += s.available();
        }
        return total >= amount;
    }

    /** Снимает Source из сети. Без требования Source или при бесконечном источнике всегда успешно. */
    public boolean consumeSource(int amount) {
        if (amount <= 0 || !SourceHelper.sourceRequired() || hasInfiniteSource()) {
            return true;
        }
        long total = 0;
        for (SourceHelper.Supplier s : sources) {
            total += s.available();
        }
        if (total < amount) {
            return false;
        }
        int left = amount;
        for (SourceHelper.Supplier s : sources) {
            left -= s.drain(left);
            if (left <= 0) {
                break;
            }
        }
        return true;
    }

    /**
     * Вставка от машины (шина импорта и т.п.): сначала предметы, которых ждут активные задачи
     * автокрафта, остальное уходит в хранилище.
     */
    public int machineWants(ItemStack stack) {
        long n = 0;
        ItemKey key = ItemKey.of(stack);
        for (com.golemlogix.crafting.CraftingCpu cpu : cpus) {
            if (!cpu.isRemoved()) {
                n += cpu.wants(key);
            }
        }
        return (int) Math.min(n, Integer.MAX_VALUE);
    }

    public ItemStack insertFromMachine(ItemStack stack) {
        return insertFromMachine(stack, null);
    }

    /** skipTarget — контейнер, из которого предметы только что взяты (в него же их обратно не кладём). */
    public ItemStack insertFromMachine(ItemStack stack, @Nullable BlockPos skipTarget) {
        ItemStack rest = stack;
        for (com.golemlogix.crafting.CraftingCpu cpu : cpus) {
            if (rest.isEmpty()) {
                break;
            }
            if (!cpu.isRemoved()) {
                rest = cpu.absorb(rest);
            }
        }
        return insertItem(rest, false, skipTarget);
    }
}
