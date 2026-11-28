package com.golemlogix.system;

import com.golemlogix.GolemLogixMod;
import com.golemlogix.blockentity.AtlasCoreBlockEntity;
import com.golemlogix.blockentity.BusBlockEntity;
import com.golemlogix.blockentity.CardCatalogBlockEntity;
import com.golemlogix.blockentity.CopperDriveBayBlockEntity;
import com.golemlogix.blockentity.CryoJarBlockEntity;
import com.golemlogix.blockentity.PatternProviderBlockEntity;
import com.golemlogix.blockentity.PeriscopeBlockEntity;
import com.golemlogix.blockentity.SecurityTerminalBlockEntity;
import com.golemlogix.blocks.CableBushingBlock;
import com.golemlogix.blocks.ChannelExpanderBlock;
import com.golemlogix.blocks.JarForm;
import com.golemlogix.config.GolemConfig;
import com.golemlogix.crafting.CraftingCpu;
import com.golemlogix.crafting.CraftingJob;
import com.golemlogix.crafting.CraftingPlan;
import com.golemlogix.data.PatternData;
import com.golemlogix.security.SecurityPermission;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.fluids.FluidStack;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * A Golem Logix network.
 * <p>
 * Built by walking neighbouring node blocks ({@link GolemNode}); a pair of Cryo-Source Telegraphs joins two parts of a network
 * that are far apart (even in different dimensions), so a node is a block in a level. It needs an Atlas Core powered with Source
 * to work. Every device takes one channel; {@link ChannelCalculation} hands channels out from the controller along shortest routes,
 * dense carriers first.
 * <p>
 * Implementation is split across package-private collaborators: {@link NetworkCache} (caching &amp; invalidation),
 * {@link NetworkScanner} (graph walk, channel allocation &amp; visuals), {@link NetworkStorage} (items, fluids &amp; Source),
 * and {@link NetworkCrafting} (pattern index, job dispatch &amp; machine returns).
 */
@EventBusSubscriber(modid = GolemLogixMod.MOD_ID)
public final class GolemNetwork {
    static final Direction[] DIRECTIONS = Direction.values();
    public static final int MAX_NODES = 16384;
    /** The Atlas Core alone, without even a starter tower under it. */
    public static final int BARE_CORE_CHANNELS = 8;
    /** The starter tower: the core on two conduits and a Cable Bushing. */
    public static final int TOWER_CHANNELS = 16;

    /** A node of a network: a block in a level (Level compares by identity). */
    public record Node(Level level, BlockPos pos) {
    }

    /** A storage behind a periscope; on the server the periscope (owner) keeps its handlers cached. */
    public record External(Level level, BlockPos target, Direction face, int priority, BlockPos periscope,
                           @Nullable PeriscopeBlockEntity owner) {
    }

    public record PatternRef(PatternData data, @Nullable PatternProviderBlockEntity provider) {
    }

    /**
     * Channels of one node, for the tooltip: whether it needs a channel and got one, how many channels pass through it and how many
     * it can carry, and, for a device without a channel, what stopped it (the full node on its route, if that was the reason).
     */
    public record NodeChannels(boolean member, boolean needsChannel, boolean hasChannel, int load, int capacity,
                               ChannelCalculation.Missing missing, @Nullable BlockPos bottleneck) {
    }

    public enum StartType {
        STARTED, OFFLINE, NO_PATTERN, MISSING, NO_CPU, RESERVE_FAILED
    }

    public record StartResult(StartType type, @Nullable CraftingPlan plan) {
    }

    /** The level the walk started in: methods taking only a BlockPos mean this level. */
    final Level level;
    final BlockPos start;

    @Nullable
    Level coreLevel;
    @Nullable
    BlockPos corePos;
    @Nullable
    AtlasCoreBlockEntity core;
    @Nullable
    AtlasStructure.Result structure;
    /** The core stands on a starter tower (and the array is not complete). */
    boolean tower;
    int lineCapacity = BARE_CORE_CHANNELS;
    int channelCapacity = BARE_CORE_CHANNELS;
    int channelsUsed;
    int deviceCount;

    /** Nodes in walk order; a node's index is its place in these lists and in the arrays below. */
    final List<Node> nodeList = new ArrayList<>();
    final Map<Node, Integer> nodeIndex = new HashMap<>();
    final List<BlockState> nodeStates = new ArrayList<>();
    /** Channel limit of every node (only with a core). */
    int[] capacities = new int[0];
    /** The node belongs to the controller: the core and the conduits that carry its channels to the exits. */
    boolean[] controller = new boolean[0];
    /** The channel assignment; null without a core. */
    @Nullable
    ChannelCalculation channels;
    final Set<Node> activeDevices = new HashSet<>();
    final List<CopperDriveBayBlockEntity> bays = new ArrayList<>();
    final List<External> externals = new ArrayList<>();
    final List<PatternProviderBlockEntity> providers = new ArrayList<>();
    final List<CardCatalogBlockEntity> catalogs = new ArrayList<>();
    final List<BusBlockEntity> buses = new ArrayList<>();
    final List<CraftingCpu> cpus = new ArrayList<>();
    final List<SourceHelper.Supplier> sources = new ArrayList<>();
    int gigaJars;
    int reactors;
    private final SourceDiscount fallbackDiscount = new SourceDiscount();
    int resonators;
    final List<SecurityTerminalBlockEntity> securityTerminals = new ArrayList<>();
    /** Blocks next to the nodes that the network uses (Source jars, resonators, containers behind periscopes), with their block. */
    final Map<Node, Block> peripherals = new HashMap<>();
    /** Telegraph links inside this network (each pair counted once): every one of them costs Source. */
    int telegraphLinks;
    @Nullable
    private Map<BusBlockEntity, Integer> busRanks;

    @Nullable
    Map<ItemKey, PatternRef> patternIndex;
    int patternIndexEpoch;
    long networkEpoch;
    @Nullable
    NetworkStorage.DiskIndex diskIndex;

    @Nullable
    private Map<ItemKey, Long> sharedList;
    private long sharedListTick = -1;
    private long sharedListEpoch = -1;
    private long sharedListDiskEpoch = -1;
    @Nullable
    private NetworkStorage.DiskIndex sharedListIndex;

    GolemNetwork(Level level, BlockPos start) {
        this.level = level;
        this.start = start;
    }

    Node node(BlockPos pos) {
        return new Node(level, pos);
    }

    @Nullable
    GlobalPos anchor() {
        return !level.isClientSide() && coreLevel != null && corePos != null
                ? GlobalPos.of(coreLevel.dimension(), corePos) : null;
    }

    void bumpEpoch() {
        NetworkCache.bumpEpoch(this);
    }

    // ---------------------------------------------------------- cache & scan

    /** Grows whenever items or fluids go into or out of any network's storage (server). Sleeping export buses watch it. */
    public static long storageEpoch() {
        return NetworkCache.storageEpoch();
    }

    public long networkEpoch() {
        return NetworkCache.networkEpoch(this);
    }

    /** A network with a cache, for ticking devices. */
    public static GolemNetwork get(Level level, BlockPos start) {
        return NetworkCache.get(level, start);
    }

    /** Walks the network now and remembers it for all its nodes. */
    public static GolemNetwork refresh(Level level, BlockPos start) {
        return NetworkCache.refresh(level, start);
    }

    /** Walks the network immediately without consulting or updating the cache. */
    public static GolemNetwork scan(Level level, BlockPos start) {
        return NetworkScanner.scan(level, start);
    }

    /**
     * The working network a block that is being removed belonged to, walked from its neighbours: when a block entity's removal runs,
     * the block itself is already gone from the level, so a walk from its own position finds nothing. Null if no neighbour is on one.
     */
    @Nullable
    public static GolemNetwork aroundRemoved(Level level, BlockPos pos) {
        return NetworkCache.aroundRemoved(level, pos);
    }

    /** Every remembered network on the server becomes stale (a multiblock formed, a periscope switched, a priority changed). */
    public static void clearCache() {
        NetworkCache.clearCache();
    }

    /** The punch cards of a provider or a catalog changed. */
    public static void patternsChanged() {
        NetworkCache.patternsChanged();
    }

    /**
     * Links changed at pos without a block changing (a pipe plug, a multiblock that formed, a telegraph card): the remembered
     * networks are stale and the one around pos gets new channels and lights at the end of the tick.
     */
    public static void nodesChanged(Level level, BlockPos pos) {
        NetworkCache.nodesChanged(level, pos);
    }

    /** The server stopped: nothing of its levels is kept. */
    public static void forgetAll() {
        NetworkCache.forgetAll();
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        NetworkCache.onLevelUnload(event);
    }

    /**
     * A block changed on the server (the neighbour notification of NeoForge). Marks networks stale only when a node or peripheral
     * appears, disappears, or changes block type (ignoring purely visual state updates).
     */
    public static void onBlockChanged(Level level, BlockPos pos, BlockState state) {
        NetworkCache.onBlockChanged(level, pos, state);
    }

    /** End of a server level tick: the networks where nodes changed get their channels and lights recomputed. */
    public static void processChanges(Level level) {
        NetworkCache.processChanges(level);
    }

    public void syncActiveVisuals(boolean active) {
        NetworkScanner.syncActiveVisuals(this, active);
    }

    // --------------------------------------------------------- topology & status

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

    /** The level of the core (another dimension when a telegraph joins this part to the base). */
    @Nullable
    public Level coreLevel() {
        return coreLevel;
    }

    @Nullable
    public AtlasCoreBlockEntity core() {
        return core;
    }

    /** There is a core and it is powered with Source. */
    public boolean isOnline() {
        return core != null && core.isPowered();
    }

    /** The core stands on a starter tower (and the whole array is not built yet). */
    public boolean isTower() {
        return tower;
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

    /** Telegraph links of the network (each costs Source every second). */
    public int telegraphLinks() {
        return telegraphLinks;
    }

    /** The complete array of the core is still the same as when the network was walked. */
    public boolean structureValid() {
        return structure != null && structure.valid();
    }

    /** Capacity of one line (one cable bushing). */
    public int lineCapacity() {
        return lineCapacity;
    }

    @Nullable
    public AtlasStructure.Result structure() {
        return structure;
    }

    /** The node is part of the network (the walk reached it). */
    public boolean contains(BlockPos pos) {
        return nodeIndex.containsKey(node(pos));
    }

    public boolean contains(Level level, BlockPos pos) {
        return nodeIndex.containsKey(new Node(level, pos));
    }

    /** How many channels pass through a node now (for example a cable bushing), its own channel included. */
    public int loadAt(BlockPos pos) {
        Integer i = nodeIndex.get(node(pos));
        return i == null || channels == null ? 0 : channels.load(i);
    }

    /** How many channels the node can carry: -1 for the controller (no limit) and outside a network with a core. */
    public int capacityAt(BlockPos pos) {
        Integer i = nodeIndex.get(node(pos));
        return i == null || channels == null || controller[i] ? -1 : capacities[i];
    }

    /** Capacity of a bushing or an expander exit (for tooltips and tests); the same as {@link #capacityAt} for other nodes. */
    public int exitCapacity(BlockPos pos) {
        Integer idx = nodeIndex.get(node(pos));
        Block b = idx != null ? nodeStates.get(idx).getBlock()
                : (level.isLoaded(pos) ? level.getBlockState(pos).getBlock() : null);
        if (b instanceof CableBushingBlock) {
            return lineCapacity;
        }
        if (b instanceof ChannelExpanderBlock ex) {
            return NetworkScanner.expanderExit(this, ex);
        }
        return Math.max(0, capacityAt(pos));
    }

    /** The core of this network is the given block of the walk's level (false for an idle second core). */
    public boolean isPrimaryCore(BlockPos pos) {
        return coreLevel == level && pos.equals(corePos);
    }

    public NodeChannels channelsAt(BlockPos pos) {
        Integer i = nodeIndex.get(node(pos));
        if (i == null) {
            return new NodeChannels(false, false, false, 0, -1, ChannelCalculation.Missing.NONE, null);
        }
        BlockState state = nodeStates.get(i);
        boolean needs = ((GolemNode) state.getBlock()).channelCost(state) > 0;
        if (channels == null) {
            return new NodeChannels(true, needs, false, 0, -1, ChannelCalculation.Missing.NONE, null);
        }
        int b = channels.bottleneck(i);
        return new NodeChannels(true, needs, channels.granted(i), channels.load(i), controller[i] ? -1 : capacities[i],
                channels.missing(i), b >= 0 ? nodeList.get(b).pos() : null);
    }

    public boolean isDeviceActive(BlockPos pos) {
        return activeDevices.contains(node(pos));
    }

    public boolean isDeviceActive(Level level, BlockPos pos) {
        return activeDevices.contains(new Node(level, pos));
    }

    /** The joined jars on this network: towers touching the network pour into them from anywhere. */
    public List<CryoJarBlockEntity> joinedJars() {
        List<CryoJarBlockEntity> out = new ArrayList<>();
        for (SourceHelper.Supplier s : sources) {
            if (s instanceof CryoJarBlockEntity j && !j.isRemoved() && j.form() != JarForm.SINGLE) {
                out.add(j);
            }
        }
        return out;
    }

    public List<SourceHelper.Supplier> sources() {
        return sources;
    }

    /** At least one 2x2 Giga Jar is connected to the network: grants a non-stacking Source consumption discount. */
    public boolean hasGigaJar() {
        return gigaJars > 0;
    }

    /** Distinct Giga Jars connected to this network (storage stacks; the discount does not). */
    public int gigaJarCount() {
        return gigaJars;
    }

    /** At least one 3x3 Antigravity Reactor is connected to the network: grants a non-stacking channel capacity boost. */
    public boolean hasReactor() {
        return reactors > 0;
    }

    /** Distinct Antigravity Reactors connected to this network (storage stacks; the channel bonus does not). */
    public int reactorCount() {
        return reactors;
    }

    /** Active non-stacking Source consumption discount percent (0 when no Giga Jar is connected). */
    public int sourceDiscountPercent() {
        return SourceDiscount.discountPercent(hasGigaJar(), GolemConfig.GIGA_SOURCE_DISCOUNT.get());
    }

    /** Active non-stacking channel capacity bonus percent (0 when no Antigravity Reactor is connected). */
    public int channelBonusPercent() {
        return ChannelCalculation.bonusPercent(hasReactor(), GolemConfig.REACTOR_CHANNEL_BONUS.get());
    }

    /** Scales a base channel limit by this network's active Reactor channel bonus. */
    public int scaleChannels(int base) {
        return ChannelCalculation.scale(base, channelBonusPercent());
    }

    SourceDiscount sourceDiscount() {
        return core != null ? core.sourceDiscount() : fallbackDiscount;
    }

    /** An Eternal Frost Resonator stands next to the network: the Chronometers of this network do not need charging. */
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
     * The bus's turn among all buses of the network: 0 for the highest priority one. A bus works on the beat "time - turn",
     * so buses with a bigger priority make their move before the others in every period. Computed once per walk.
     */
    public int busRank(BusBlockEntity bus) {
        if (busRanks == null) {
            List<BusBlockEntity> sorted = new ArrayList<>(buses);
            sorted.sort(Comparator.comparingInt(BusBlockEntity::getPriority).reversed()
                    .thenComparingLong(b -> b.getBlockPos().asLong()));
            Map<BusBlockEntity, Integer> ranks = new IdentityHashMap<>();
            for (int i = 0; i < sorted.size(); i++) {
                ranks.put(sorted.get(i), i);
            }
            busRanks = ranks;
        }
        return busRanks.getOrDefault(bus, 0);
    }

    public List<CraftingCpu> cpus() {
        return cpus;
    }

    public List<PatternProviderBlockEntity> providers() {
        return providers;
    }

    /** Punch Card Catalogs with a channel: the calculators run their crafting-table patterns themselves. */
    public List<CardCatalogBlockEntity> catalogs() {
        return catalogs;
    }

    // -------------------------------------------------------------- security

    /** For the terminal: whether it can be used. Returns null or an error message key. */
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

    public List<SecurityTerminalBlockEntity> securityTerminals() {
        return securityTerminals;
    }

    public boolean hasSecurityTerminal() {
        for (SecurityTerminalBlockEntity sec : securityTerminals) {
            if (!sec.isRemoved()) {
                return true;
            }
        }
        return false;
    }

    public boolean hasPermission(@Nullable Player player, SecurityPermission perm) {
        if (player == null) {
            return true;
        }
        if (player.isCreative() && player.canUseGameMasterBlocks()) {
            return true;
        }
        for (SecurityTerminalBlockEntity sec : securityTerminals) {
            if (!sec.isRemoved() && !sec.hasPermission(player, perm)) {
                return false;
            }
        }
        return true;
    }

    @Nullable
    public String accessError(@Nullable BlockPos devicePos, @Nullable Player player) {
        String err = accessError(devicePos);
        if (err != null) {
            return err;
        }
        if (player != null && !hasPermission(player, SecurityPermission.BUILD)) {
            return "message.golem_logix.security_denied";
        }
        return null;
    }

    // --------------------------------------------------------------- storage

    /** All items of the network: type -> count. */
    public Map<ItemKey, Long> listItems() {
        return NetworkStorage.listItems(this);
    }

    /** The list for the terminals: open windows of one tick share one reading of the network. Not to be changed. */
    public Map<ItemKey, Long> listItemsShared() {
        long now = level.getServer() != null ? level.getServer().getTickCount() : level.getGameTime();
        long epoch = NetworkCache.storageEpoch();
        long diskEpoch = DiskStorage.itemMutationEpoch();
        NetworkStorage.DiskIndex curIndex = NetworkStorage.currentIndex(this);
        if (sharedList == null || sharedListTick != now || sharedListEpoch != epoch
                || sharedListDiskEpoch != diskEpoch || sharedListIndex != curIndex) {
            sharedList = NetworkStorage.listItems(this, curIndex);
            sharedListTick = now;
            sharedListEpoch = epoch;
            sharedListDiskEpoch = diskEpoch;
            sharedListIndex = curIndex;
        }
        return sharedList;
    }

    /** Returns the network's item map for planner fuzzy scans (reuses the tick-shared snapshot when no external chests exist). */
    public Map<ItemKey, Long> listItemsForPlanning() {
        return externals.isEmpty() ? listItemsShared() : listItems();
    }

    public long itemCountOf(ItemKey key) {
        return NetworkStorage.itemCountOf(this, key);
    }

    public long itemCapacity() {
        return NetworkStorage.diskCapacity(this, false);
    }

    public long itemsStoredOnDisks() {
        return NetworkStorage.diskStored(this, false);
    }

    public long fluidCapacity() {
        return NetworkStorage.diskCapacity(this, true);
    }

    public long fluidStoredOnDisks() {
        return NetworkStorage.diskStored(this, true);
    }

    /**
     * Puts items into the network. Returns the rest that did not fit.
     * Order: highest priority to lowest; within one priority, disks already holding the item, then other disks, then periscopes.
     */
    public ItemStack insertItem(ItemStack input, boolean simulate) {
        return NetworkStorage.insertItem(this, input, simulate, null);
    }

    /** Same as {@link #insertItem(ItemStack, boolean)}, skipping the container at skipTarget (prevents import bus loops). */
    public ItemStack insertItem(ItemStack input, boolean simulate, @Nullable BlockPos skipTarget) {
        return NetworkStorage.insertItem(this, input, simulate, skipTarget);
    }

    /** Takes up to amount items from the network (lowest-priority storages first). */
    public ItemStack extractItem(ItemStack template, int amount, boolean simulate) {
        return NetworkStorage.extractItem(this, template, amount, simulate, null);
    }

    /** Same as {@link #extractItem(ItemStack, int, boolean)}, skipping the container at skipTarget (prevents export bus loops). */
    public ItemStack extractItem(ItemStack template, int amount, boolean simulate, @Nullable BlockPos skipTarget) {
        return NetworkStorage.extractItem(this, template, amount, simulate, skipTarget);
    }

    /** All fluids of the network: type -> mB. */
    public List<FluidStack> listFluids() {
        return NetworkStorage.listFluids(this);
    }

    /** Puts fluid into the network (highest priority first). Returns how many mB were accepted. */
    public int insertFluid(FluidStack input, boolean simulate) {
        return NetworkStorage.insertFluid(this, input, simulate, null);
    }

    public int insertFluid(FluidStack input, boolean simulate, @Nullable BlockPos skipTarget) {
        return NetworkStorage.insertFluid(this, input, simulate, skipTarget);
    }

    /** Takes fluid from the network (lowest priority first). */
    public FluidStack extractFluid(FluidStack template, int amount, boolean simulate) {
        return NetworkStorage.extractFluid(this, template, amount, simulate, null);
    }

    public FluidStack extractFluid(FluidStack template, int amount, boolean simulate, @Nullable BlockPos skipTarget) {
        return NetworkStorage.extractFluid(this, template, amount, simulate, skipTarget);
    }

    /** How much Liquid Cryo-Source (mB) the network can make right now from its Source. */
    public long cryoSourceAvailable() {
        return NetworkStorage.cryoSourceAvailable(this);
    }

    /** How much of a fluid the network has (mB); Liquid Cryo-Source counts what the Source can make. */
    public long fluidCountOf(FluidStack fluid) {
        return NetworkStorage.fluidCountOf(this, fluid);
    }

    public long fluidCountOf(Fluid fluid) {
        return NetworkStorage.fluidCountOf(this, new FluidStack(fluid, 1));
    }

    /** Stock of an ingredient: items by count, fluid keys in mB. */
    public long countOf(ItemKey key) {
        return NetworkStorage.countOf(this, key);
    }

    /** Whether there is enough Source to take amount (takes nothing). */
    public boolean hasSource(int amount) {
        return NetworkStorage.hasSource(this, amount);
    }

    /** Takes Source from the network. Always succeeds without the Source requirement or with an infinite source. */
    public boolean consumeSource(int amount) {
        return NetworkStorage.consumeSource(this, amount);
    }

    // -------------------------------------------------------------- crafting

    /** Index "item -> the pattern that produces it"; rebuilt when punch cards change. */
    public Map<ItemKey, PatternRef> patternIndex() {
        return NetworkCrafting.patternIndex(this);
    }

    public boolean canCraft(ItemKey key) {
        return patternIndex().containsKey(key);
    }

    /** Every gateway that can run this step: exact pattern holders first, then same-recipe holders with different fuzziness. */
    public List<PatternProviderBlockEntity> providersFor(PatternData pattern) {
        return NetworkCrafting.providersFor(this, pattern);
    }

    /** Plans and starts autocrafting on the first free Cryo-Calculator. */
    public StartResult startCrafting(ItemKey target, long amount) {
        Map<ItemKey, Long> one = new LinkedHashMap<>();
        one.put(target, amount);
        return NetworkCrafting.startCrafting(this, one, null, null);
    }

    /**
     * Plans and starts autocrafting of several items at once on the first free Cryo-Calculator.
     * requester = the player who gets the "ready" notification; bus = the export bus that ordered the craft itself.
     */
    public StartResult startCrafting(Map<ItemKey, Long> targets, @Nullable UUID requester, @Nullable GlobalPos bus) {
        return NetworkCrafting.startCrafting(this, targets, requester, bus);
    }

    /** Whether there is a free calculator with a channel. */
    @Nullable
    public CraftingCpu freeCpu() {
        return NetworkCrafting.freeCpu(this);
    }

    /** The job an idle processor should lend its steps to, or null when none could use help or the tick's time is up. */
    @Nullable
    public CraftingJob jobWantingHelp() {
        return NetworkCrafting.jobWantingHelp(this);
    }

    /** Puts an ingredient back into storage; returns how much did not fit (Liquid Cryo-Source never fits). */
    public long giveBack(ItemKey key, long amount) {
        return NetworkCrafting.giveBack(this, key, amount);
    }

    /** The provider waits for the result of the previous portion (wait mode): some job has not received its output yet. */
    public boolean providerBusy(GlobalPos provider) {
        return NetworkCrafting.providerBusy(this, provider);
    }

    /** A job ordered by this bus for this item is already running. */
    public boolean busCraftPending(GlobalPos bus, ItemKey key) {
        return NetworkCrafting.busCraftPending(this, bus, key);
    }

    /** How many of this item active autocraft jobs are waiting for from machines. */
    public int machineWants(ItemStack stack) {
        return NetworkCrafting.machineWants(this, ItemKey.of(stack));
    }

    /** How many mB of this fluid active autocraft jobs are waiting for from machines. */
    public int machineWantsFluid(FluidStack fluid) {
        return fluid.isEmpty() ? 0 : NetworkCrafting.machineWants(this, Ingredients.fluidKey(fluid));
    }

    /** Insertion from a machine: first satisfies active autocraft jobs, the rest goes to storage. */
    public ItemStack insertFromMachine(ItemStack stack) {
        return NetworkCrafting.insertFromMachine(this, stack, null);
    }

    /** skipTarget = the container the items were just taken from (they are not put back into it). */
    public ItemStack insertFromMachine(ItemStack stack, @Nullable BlockPos skipTarget) {
        return NetworkCrafting.insertFromMachine(this, stack, skipTarget);
    }

    /** Fluid from a machine: first satisfies waiting jobs, the rest goes into storage. Returns the mB taken. */
    public int insertFluidFromMachine(FluidStack fluid, @Nullable BlockPos skipTarget) {
        return NetworkCrafting.insertFluidFromMachine(this, fluid, skipTarget);
    }
}
