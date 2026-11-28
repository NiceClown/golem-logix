package com.golemlogix.system;

import com.golemlogix.blockentity.CopperDriveBayBlockEntity;
import com.golemlogix.blocks.CryoMixerBlock;
import com.golemlogix.blocks.DistillationTowerBlock;
import com.golemlogix.blocks.SaturatorPartBlock;
import com.golemlogix.config.GolemConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.neoforged.neoforge.capabilities.BlockCapability;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.wrapper.InvWrapper;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Item, fluid, Cryo-Source, and Source storage operations over a {@link GolemNetwork}'s drive bays, periscopes, and Source suppliers.
 */
final class NetworkStorage {
    private static final int FLUID_MAP_THRESHOLD = 16;

    /** One mounted, non-duplicate disk in a drive cabinet, with its resolved server-side backing data. */
    private static final class DiskEntry {
        final CopperDriveBayBlockEntity bay;
        final ItemStack disk;
        final int capacity;
        final int orderIndex;
        int tierIndexHigh;
        @Nullable
        final DiskStore.Disk data;

        DiskEntry(CopperDriveBayBlockEntity bay, ItemStack disk, int capacity, int orderIndex, @Nullable DiskStore.Disk data) {
            this.bay = bay;
            this.disk = disk;
            this.capacity = capacity;
            this.orderIndex = orderIndex;
            this.data = data;
        }
    }

    /** Storages (cabinets and periscopes) sharing one priority; equal ones go in network walk order. */
    private static final class Tier {
        final int priority;
        final int indexHigh;
        final List<DiskEntry> itemDisks;
        final List<DiskEntry> fluidDisks;
        final List<GolemNetwork.External> exts;

        Tier(int priority, int indexHigh, List<DiskEntry> itemDisks, List<DiskEntry> fluidDisks, List<GolemNetwork.External> exts) {
            this.priority = priority;
            this.indexHigh = indexHigh;
            this.itemDisks = itemDisks;
            this.fluidDisks = fluidDisks;
            this.exts = exts;
        }
    }

    /** HashMap key for grouping FluidStacks in O(1) without converting to ItemStack. */
    private static final class FluidKey {
        final FluidStack stack;
        final int hash;

        FluidKey(FluidStack stack) {
            this.stack = stack;
            this.hash = FluidStack.hashFluidAndComponents(stack);
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof FluidKey k && hash == k.hash && FluidStack.isSameFluidSameComponents(stack, k.stack);
        }

        @Override
        public int hashCode() {
            return hash;
        }
    }

    /**
     * Per-network cached snapshot of mounted disks, priority tiers, capacities, and a lazily-built inverted item index.
     * Validated cheaply against drive-bay slot ItemStack references, bay priorities/removal, periscope removal, and
     * {@link DiskStorage#diskAllocEpoch()} so slot or priority mutations in a cabinet never read stale data.
     */
    static final class DiskIndex {
        @Nullable
        final DiskStore store;
        final long diskAllocEpoch;
        final int[] bayPriorities;
        final boolean[] bayRemoved;
        final ItemStack[] slotStacks;
        final boolean[] slotEmpty;
        final boolean[] extRemoved;

        final List<DiskEntry> itemDisks;
        final List<DiskEntry> fluidDisks;
        final List<Tier> tiersHigh;
        final List<Tier> tiersLow;
        final long itemCapacity;
        final long fluidCapacity;

        /** Lazily built ItemKey -> DiskEntry (single disk) or List<DiskEntry> (sorted by tierIndexHigh ASC, orderIndex ASC). */
        @Nullable
        Map<ItemKey, Object> itemLocations;
        long itemLocationsEpoch = -1;

        long cachedItemsStored;
        long cachedItemsStoredEpoch = -1;
        long cachedFluidsStored;
        long cachedFluidsStoredEpoch = -1;

        DiskIndex(@Nullable DiskStore store, long diskAllocEpoch, int[] bayPriorities, boolean[] bayRemoved,
                  ItemStack[] slotStacks, boolean[] slotEmpty, boolean[] extRemoved,
                  List<DiskEntry> itemDisks, List<DiskEntry> fluidDisks,
                  List<Tier> tiersHigh, List<Tier> tiersLow,
                  long itemCapacity, long fluidCapacity) {
            this.store = store;
            this.diskAllocEpoch = diskAllocEpoch;
            this.bayPriorities = bayPriorities;
            this.bayRemoved = bayRemoved;
            this.slotStacks = slotStacks;
            this.slotEmpty = slotEmpty;
            this.extRemoved = extRemoved;
            this.itemDisks = itemDisks;
            this.fluidDisks = fluidDisks;
            this.tiersHigh = tiersHigh;
            this.tiersLow = tiersLow;
            this.itemCapacity = itemCapacity;
            this.fluidCapacity = fluidCapacity;
        }

        boolean isValid(GolemNetwork net, @Nullable DiskStore st) {
            if (store != st || diskAllocEpoch != DiskStorage.diskAllocEpoch()) {
                return false;
            }
            int slotOffset = 0;
            for (int b = 0; b < net.bays.size(); b++) {
                CopperDriveBayBlockEntity bay = net.bays.get(b);
                boolean removed = bay.isRemoved();
                if (removed != bayRemoved[b]) {
                    return false;
                }
                if (!removed) {
                    if (bay.getPriority() != bayPriorities[b]) {
                        return false;
                    }
                    List<ItemStack> disks = bay.getDisks();
                    for (int s = 0; s < disks.size(); s++) {
                        ItemStack cur = disks.get(s);
                        if (cur != slotStacks[slotOffset + s] || cur.isEmpty() != slotEmpty[slotOffset + s]) {
                            return false;
                        }
                    }
                }
                slotOffset += CopperDriveBayBlockEntity.SLOTS;
            }
            for (int e = 0; e < net.externals.size(); e++) {
                if (isRemoved(net.externals.get(e)) != extRemoved[e]) {
                    return false;
                }
            }
            return true;
        }

        Map<ItemKey, Object> itemLocations() {
            long epoch = DiskStorage.itemMutationEpoch();
            if (itemLocations == null || itemLocationsEpoch != epoch) {
                Map<ItemKey, Object> map = new HashMap<>();
                for (Tier tier : tiersHigh) {
                    for (DiskEntry d : tier.itemDisks) {
                        if (d.data == null || d.data.items.isEmpty()) {
                            continue;
                        }
                        for (ItemKey k : d.data.items.keySet()) {
                            addLocationUnordered(map, k, d);
                        }
                    }
                }
                itemLocations = map;
                itemLocationsEpoch = epoch;
            }
            return itemLocations;
        }

        @SuppressWarnings("unchecked")
        private static void addLocationUnordered(Map<ItemKey, Object> map, ItemKey key, DiskEntry d) {
            Object existing = map.get(key);
            if (existing == null) {
                map.put(key, d);
            } else if (existing instanceof DiskEntry first) {
                List<DiskEntry> list = new ArrayList<>(2);
                list.add(first);
                list.add(d);
                map.put(key, list);
            } else {
                ((List<DiskEntry>) existing).add(d);
            }
        }

        /** Inserts d into itemLocations in (tierIndexHigh ASC, orderIndex ASC) order if not already present. */
        @SuppressWarnings("unchecked")
        void recordPresent(ItemKey key, DiskEntry d) {
            if (itemLocations == null) {
                return;
            }
            Object existing = itemLocations.get(key);
            if (existing == null) {
                itemLocations.put(key, d);
            } else if (existing instanceof DiskEntry first) {
                if (first == d) {
                    return;
                }
                List<DiskEntry> list = new ArrayList<>(2);
                if (compareHighOrder(first, d) <= 0) {
                    list.add(first);
                    list.add(d);
                } else {
                    list.add(d);
                    list.add(first);
                }
                itemLocations.put(key, list);
            } else {
                List<DiskEntry> list = (List<DiskEntry>) existing;
                int insertAt = list.size();
                for (int i = 0; i < list.size(); i++) {
                    DiskEntry cur = list.get(i);
                    if (cur == d) {
                        return;
                    }
                    if (compareHighOrder(d, cur) < 0) {
                        insertAt = i;
                        break;
                    }
                }
                list.add(insertAt, d);
            }
        }

        @SuppressWarnings("unchecked")
        void recordAbsent(ItemKey key, DiskEntry d) {
            if (itemLocations == null) {
                return;
            }
            Object existing = itemLocations.get(key);
            if (existing == d) {
                itemLocations.remove(key);
            } else if (existing instanceof List<?> raw) {
                List<DiskEntry> list = (List<DiskEntry>) raw;
                list.remove(d);
                if (list.isEmpty()) {
                    itemLocations.remove(key);
                } else if (list.size() == 1) {
                    itemLocations.put(key, list.get(0));
                }
            }
        }

        private static int compareHighOrder(DiskEntry a, DiskEntry b) {
            int cmp = Integer.compare(a.tierIndexHigh, b.tierIndexHigh);
            return cmp != 0 ? cmp : Integer.compare(a.orderIndex, b.orderIndex);
        }

        long itemsStored() {
            long epoch = DiskStorage.itemMutationEpoch();
            if (cachedItemsStoredEpoch != epoch) {
                long sum = 0;
                for (DiskEntry d : itemDisks) {
                    if (d.data != null) {
                        sum += d.data.storedItems;
                    }
                }
                cachedItemsStored = sum;
                cachedItemsStoredEpoch = epoch;
            }
            return cachedItemsStored;
        }

        long fluidsStored() {
            long epoch = DiskStorage.fluidMutationEpoch();
            if (cachedFluidsStoredEpoch != epoch) {
                long sum = 0;
                for (DiskEntry d : fluidDisks) {
                    if (d.data != null) {
                        sum += d.data.storedFluids;
                    }
                }
                cachedFluidsStored = sum;
                cachedFluidsStoredEpoch = epoch;
            }
            return cachedFluidsStored;
        }
    }

    private NetworkStorage() {
    }

    // ------------------------------------------------------------- disk index

    static DiskIndex currentIndex(GolemNetwork net) {
        return index(net, DiskStorage.store());
    }

    private static DiskIndex index(GolemNetwork net, @Nullable DiskStore st) {
        DiskIndex cached = net.diskIndex;
        if (cached != null && cached.isValid(net, st)) {
            return cached;
        }
        DiskIndex fresh = buildIndex(net, st);
        net.diskIndex = fresh;
        return fresh;
    }

    private static DiskIndex buildIndex(GolemNetwork net, @Nullable DiskStore st) {
        long allocEpoch = DiskStorage.diskAllocEpoch();
        int bayCount = net.bays.size();
        int extCount = net.externals.size();
        int[] bayPriorities = new int[bayCount];
        boolean[] bayRemoved = new boolean[bayCount];
        ItemStack[] slotStacks = new ItemStack[bayCount * CopperDriveBayBlockEntity.SLOTS];
        boolean[] slotEmpty = new boolean[slotStacks.length];
        boolean[] extRemoved = new boolean[extCount];

        List<DiskEntry> itemDisks = new ArrayList<>();
        List<DiskEntry> fluidDisks = new ArrayList<>();
        long itemCap = 0;
        long fluidCap = 0;

        // Deduplicate mounted disk UUIDs in network walk order (matching copiedDisks semantics)
        UUID firstItemId = null;
        Set<UUID> seenItemIds = null;
        UUID firstFluidId = null;
        Set<UUID> seenFluidIds = null;

        boolean hasAny = false;
        boolean singlePriority = true;
        int firstPriority = 0;
        int orderIndex = 0;

        for (int b = 0; b < bayCount; b++) {
            CopperDriveBayBlockEntity bay = net.bays.get(b);
            boolean removed = bay.isRemoved();
            bayRemoved[b] = removed;
            if (removed) {
                continue;
            }
            int p = bay.getPriority();
            bayPriorities[b] = p;
            if (!hasAny) {
                hasAny = true;
                firstPriority = p;
            } else if (p != firstPriority) {
                singlePriority = false;
            }
            List<ItemStack> disks = bay.getDisks();
            int base = b * CopperDriveBayBlockEntity.SLOTS;
            for (int s = 0; s < disks.size(); s++) {
                ItemStack disk = disks.get(s);
                boolean empty = disk.isEmpty();
                slotStacks[base + s] = disk;
                slotEmpty[base + s] = empty;
                if (empty) {
                    continue;
                }
                boolean isItem = DiskStorage.isItemDisk(disk);
                boolean isFluid = !isItem && DiskStorage.isFluidDisk(disk);
                if (!isItem && !isFluid) {
                    continue;
                }
                UUID id = DiskStorage.diskId(disk);
                if (isItem) {
                    if (id != null) {
                        if (seenItemIds != null) {
                            if (!seenItemIds.add(id)) {
                                continue;
                            }
                        } else if (firstItemId == null) {
                            firstItemId = id;
                        } else if (firstItemId.equals(id)) {
                            continue;
                        } else {
                            seenItemIds = new HashSet<>();
                            seenItemIds.add(firstItemId);
                            seenItemIds.add(id);
                        }
                    }
                    int cap = DiskStorage.capacity(disk);
                    DiskStore.Disk data = st == null || id == null ? null : st.getOrNull(id);
                    itemDisks.add(new DiskEntry(bay, disk, cap, orderIndex++, data));
                    itemCap += cap;
                } else {
                    if (id != null) {
                        if (seenFluidIds != null) {
                            if (!seenFluidIds.add(id)) {
                                continue;
                            }
                        } else if (firstFluidId == null) {
                            firstFluidId = id;
                        } else if (firstFluidId.equals(id)) {
                            continue;
                        } else {
                            seenFluidIds = new HashSet<>();
                            seenFluidIds.add(firstFluidId);
                            seenFluidIds.add(id);
                        }
                    }
                    int cap = DiskStorage.capacity(disk);
                    DiskStore.Disk data = st == null || id == null ? null : st.getOrNull(id);
                    fluidDisks.add(new DiskEntry(bay, disk, cap, orderIndex++, data));
                    fluidCap += cap;
                }
            }
        }

        List<GolemNetwork.External> liveExts = null;
        for (int e = 0; e < extCount; e++) {
            GolemNetwork.External ex = net.externals.get(e);
            boolean removed = isRemoved(ex);
            extRemoved[e] = removed;
            if (removed) {
                continue;
            }
            if (liveExts == null) {
                liveExts = new ArrayList<>(extCount);
            }
            liveExts.add(ex);
            int p = ex.priority();
            if (!hasAny) {
                hasAny = true;
                firstPriority = p;
            } else if (p != firstPriority) {
                singlePriority = false;
            }
        }

        List<Tier> tiersHigh;
        List<Tier> tiersLow;
        if (!hasAny) {
            tiersHigh = List.of();
            tiersLow = List.of();
        } else if (singlePriority) {
            Tier only = new Tier(firstPriority, 0, itemDisks, fluidDisks, liveExts == null ? List.of() : liveExts);
            tiersHigh = List.of(only);
            tiersLow = tiersHigh;
        } else {
            TreeMap<Integer, TempTier> map = new TreeMap<>(Comparator.reverseOrder());
            for (DiskEntry d : itemDisks) {
                map.computeIfAbsent(d.bay.getPriority(), TempTier::new).itemDisks.add(d);
            }
            for (DiskEntry d : fluidDisks) {
                map.computeIfAbsent(d.bay.getPriority(), TempTier::new).fluidDisks.add(d);
            }
            if (liveExts != null) {
                for (GolemNetwork.External ex : liveExts) {
                    map.computeIfAbsent(ex.priority(), TempTier::new).exts.add(ex);
                }
            }
            tiersHigh = new ArrayList<>(map.size());
            int tIdx = 0;
            for (TempTier temp : map.values()) {
                for (DiskEntry d : temp.itemDisks) {
                    d.tierIndexHigh = tIdx;
                }
                for (DiskEntry d : temp.fluidDisks) {
                    d.tierIndexHigh = tIdx;
                }
                tiersHigh.add(new Tier(temp.priority, tIdx, temp.itemDisks, temp.fluidDisks, temp.exts));
                tIdx++;
            }
            tiersLow = new ArrayList<>(tiersHigh.size());
            for (int i = tiersHigh.size() - 1; i >= 0; i--) {
                tiersLow.add(tiersHigh.get(i));
            }
        }

        return new DiskIndex(st, allocEpoch, bayPriorities, bayRemoved, slotStacks, slotEmpty, extRemoved,
                itemDisks, fluidDisks, tiersHigh, tiersLow, itemCap, fluidCap);
    }

    private static final class TempTier {
        final int priority;
        final List<DiskEntry> itemDisks = new ArrayList<>();
        final List<DiskEntry> fluidDisks = new ArrayList<>();
        final List<GolemNetwork.External> exts = new ArrayList<>();

        TempTier(int priority) {
            this.priority = priority;
        }
    }

    /** Only the cabinets whose disks really changed are marked (rather than every cabinet in the network). */
    private static void markBaysChanged(@Nullable Set<CopperDriveBayBlockEntity> touched) {
        if (touched == null) {
            return;
        }
        for (CopperDriveBayBlockEntity bay : touched) {
            bay.setChanged();
            bay.refreshVisuals();
        }
    }

    // ------------------------------------------------------------- externals

    /** Identifies one slot of a canonical external storage (double chests &amp; WorldlyContainers). */
    private record ExternalSlotKey(GlobalPos storage, int slot) {
    }

    /**
     * Identifies one item slot or fluid tank by backing handler reference identity + slot/tank index so two
     * independent sided handlers at local index 0 on one block stay distinct, while shared handlers (Copper Tank,
     * Cryo-Mixer, Distillation Tower, Mana Saturator, single vanilla Containers via InvWrapper) collapse.
     * shortcut: 1.21.1 IItemHandler/IFluidHandler have no rollback transactions to detect distinct fresh wrapper
     * objects over one backing non-Container/non-chest storage.
     */
    static final class HandlerSlotKey {
        final Object handler;
        final int slot;

        HandlerSlotKey(Object handler, int slot) {
            this.handler = handler;
            this.slot = slot;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof HandlerSlotKey k && k.handler == handler && k.slot == slot;
        }

        @Override
        public int hashCode() {
            return System.identityHashCode(handler) * 31 + slot;
        }
    }

    /**
     * Canonical storage position for an external target: multi-block machines (Cryo-Mixer, Cryo-Distillation Tower,
     * Mana Saturator) resolve to their base/controller; both halves of a vanilla double chest resolve to the lesser
     * BlockPos; all other blocks resolve to their own position.
     */
    static BlockPos canonicalStoragePos(Level level, BlockPos pos) {
        if (!level.isLoaded(pos)) {
            return pos.immutable();
        }
        BlockState state = level.getBlockState(pos);
        Block block = state.getBlock();
        if (block instanceof CryoMixerBlock) {
            return CryoMixerBlock.baseOf(pos, state).immutable();
        }
        if (block instanceof DistillationTowerBlock) {
            return DistillationTowerBlock.baseOf(pos, state).immutable();
        }
        if (block instanceof SaturatorPartBlock) {
            return SaturatorPartBlock.controllerOf(pos, state).immutable();
        }
        if (block instanceof ChestBlock && state.hasProperty(ChestBlock.TYPE)) {
            ChestType type = state.getValue(ChestBlock.TYPE);
            if (type != ChestType.SINGLE) {
                BlockPos other = pos.relative(ChestBlock.getConnectedDirection(state));
                if (level.isLoaded(other)) {
                    BlockState otherState = level.getBlockState(other);
                    if (otherState.is(block)
                            && otherState.hasProperty(ChestBlock.TYPE)
                            && otherState.getValue(ChestBlock.TYPE) == type.getOpposite()
                            && otherState.getValue(ChestBlock.FACING) == state.getValue(ChestBlock.FACING)) {
                        return pos.compareTo(other) <= 0 ? pos.immutable() : other.immutable();
                    }
                }
            }
        }
        return pos.immutable();
    }

    static GlobalPos canonicalStorageKey(Level level, BlockPos pos) {
        return GlobalPos.of(level.dimension(), canonicalStoragePos(level, pos));
    }

    /**
     * Returns the set of canonical targets referenced by 2+ periscopes, or {@code null} when every periscope points to
     * a distinct storage (skipping per-slot dedup allocations on single-path storages).
     */
    @Nullable
    private static Set<GlobalPos> sharedStorageKeys(List<GolemNetwork.External> externals) {
        int n = externals.size();
        if (n <= 1) {
            return null;
        }
        if (n == 2) {
            GolemNetwork.External a = externals.get(0);
            GolemNetwork.External b = externals.get(1);
            if (a.level() != b.level()) {
                return null;
            }
            BlockPos pa = canonicalStoragePos(a.level(), a.target());
            BlockPos pb = canonicalStoragePos(b.level(), b.target());
            return pa.equals(pb) ? Set.of(GlobalPos.of(a.level().dimension(), pa)) : null;
        }
        Set<GlobalPos> seen = new HashSet<>(n * 2);
        Set<GlobalPos> dups = null;
        for (int i = 0; i < n; i++) {
            GolemNetwork.External ex = externals.get(i);
            GlobalPos c = canonicalStorageKey(ex.level(), ex.target());
            if (!seen.add(c)) {
                if (dups == null) {
                    dups = new HashSet<>();
                }
                dups.add(c);
            }
        }
        return dups;
    }

    @Nullable
    private static GlobalPos sharedCanonical(@Nullable Set<GlobalPos> shared, GolemNetwork.External ex) {
        if (shared == null) {
            return null;
        }
        GlobalPos c = canonicalStorageKey(ex.level(), ex.target());
        return shared.contains(c) ? c : null;
    }

    /** Maps sided handler-local slot indices to real container slot indices on WorldlyContainer targets (e.g. Furnace). */
    @Nullable
    private static int[] worldlySlots(GolemNetwork.External ex, IItemHandler h) {
        if (ex.level().getBlockEntity(ex.target()) instanceof WorldlyContainer wc) {
            int[] faceSlots = wc.getSlotsForFace(ex.face());
            if (faceSlots.length == h.getSlots()) {
                return faceSlots;
            }
        }
        return null;
    }

    private static int realSlot(@Nullable int[] faceSlots, int handlerSlot) {
        return faceSlots != null && handlerSlot < faceSlots.length ? faceSlots[handlerSlot] : handlerSlot;
    }

    private static boolean isDoubleChest(Level level, BlockPos pos) {
        if (!level.isLoaded(pos)) {
            return false;
        }
        BlockState state = level.getBlockState(pos);
        return state.getBlock() instanceof ChestBlock
                && state.hasProperty(ChestBlock.TYPE)
                && state.getValue(ChestBlock.TYPE) != ChestType.SINGLE;
    }

    /**
     * Returns the backing handler identity for non-canonical shared targets, or {@code null} when physical slot keys
     * ({@link ExternalSlotKey}) must be used (WorldlyContainer or vanilla double chest).
     */
    @Nullable
    private static Object identitySlotOwner(GolemNetwork.External ex, IItemHandler h, @Nullable int[] faceSlots) {
        if (faceSlots != null || isDoubleChest(ex.level(), ex.target())) {
            return null;
        }
        if (h instanceof InvWrapper iw) {
            Container inv = iw.getInv();
            return inv != null ? inv : h;
        }
        return h;
    }

    private static Object itemSlotKey(GlobalPos storageKey, @Nullable int[] faceSlots, @Nullable Object slotOwner, int slot) {
        return slotOwner != null
                ? new HandlerSlotKey(slotOwner, slot)
                : new ExternalSlotKey(storageKey, realSlot(faceSlots, slot));
    }

    private static boolean skips(GolemNetwork net, GolemNetwork.External ex, @Nullable BlockPos skipTarget) {
        if (skipTarget == null || ex.level() != net.level) {
            return false;
        }
        if (ex.target().equals(skipTarget)) {
            return true;
        }
        // Max Manhattan span across any Golem Logix capability multiblock (3x3x3 Mana Saturator = 6)
        return ex.target().distManhattan(skipTarget) <= 6
                && canonicalStoragePos(ex.level(), ex.target()).equals(canonicalStoragePos(net.level, skipTarget));
    }

    private static boolean mayInsert(GolemNetwork.External ex, ItemStack s) {
        return ex.owner() == null || ex.owner().mayInsert(s);
    }

    private static boolean mayExtract(GolemNetwork.External ex, ItemStack s) {
        return ex.owner() == null || ex.owner().mayExtract(s);
    }

    private static boolean mayInsert(GolemNetwork.External ex, FluidStack f) {
        return ex.owner() == null || ex.owner().mayInsert(f);
    }

    private static boolean mayExtract(GolemNetwork.External ex, FluidStack f) {
        return ex.owner() == null || ex.owner().mayExtract(f);
    }

    private static boolean isRemoved(GolemNetwork.External ex) {
        return ex.owner() != null && ex.owner().isRemoved();
    }

    /**
     * Item handler of the container behind a periscope. Tries the face the periscope is pressed against, then null,
     * then the remaining faces: many tanks and chests expose capabilities only on certain sides.
     */
    @Nullable
    private static IItemHandler itemHandler(GolemNetwork.External ex) {
        Level lvl = ex.level();
        if (isRemoved(ex) || !lvl.isLoaded(ex.target())) {
            return null;
        }
        if (ex.owner() != null && lvl instanceof ServerLevel sl) {
            return ex.owner().targetCaps(sl, ex.target(), ex.face()).itemsAnySide();
        }
        return anySideCapability(lvl, ex.target(), ex.face(), Capabilities.ItemHandler.BLOCK);
    }

    @Nullable
    private static IFluidHandler fluidHandler(GolemNetwork.External ex) {
        Level lvl = ex.level();
        if (isRemoved(ex) || !lvl.isLoaded(ex.target())) {
            return null;
        }
        if (ex.owner() != null && lvl instanceof ServerLevel sl) {
            return ex.owner().targetCaps(sl, ex.target(), ex.face()).fluidsAnySide();
        }
        return anySideCapability(lvl, ex.target(), ex.face(), Capabilities.FluidHandler.BLOCK);
    }

    @Nullable
    private static <H> H anySideCapability(Level lvl, BlockPos target, Direction preferred, BlockCapability<H, Direction> cap) {
        H h = lvl.getCapability(cap, target, preferred);
        if (h != null) {
            return h;
        }
        h = lvl.getCapability(cap, target, null);
        if (h != null) {
            return h;
        }
        for (Direction d : GolemNetwork.DIRECTIONS) {
            if (d == preferred) {
                continue;
            }
            h = lvl.getCapability(cap, target, d);
            if (h != null) {
                return h;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ items

    static Map<ItemKey, Long> listItems(GolemNetwork net) {
        return listItems(net, index(net, DiskStorage.store()));
    }

    static Map<ItemKey, Long> listItems(GolemNetwork net, DiskIndex idx) {
        int est = 0;
        for (DiskEntry d : idx.itemDisks) {
            if (d.data != null) {
                est += d.data.items.size();
            }
        }
        Map<ItemKey, Long> all = new LinkedHashMap<>(Math.max(16, est * 4 / 3 + 1));
        for (DiskEntry d : idx.itemDisks) {
            if (d.data == null) {
                continue;
            }
            for (Map.Entry<ItemKey, Integer> e : d.data.items.entrySet()) {
                all.merge(e.getKey(), (long) e.getValue(), Long::sum);
            }
        }
        Set<GlobalPos> shared = sharedStorageKeys(net.externals);
        Set<Object> seenSlots = shared != null ? new HashSet<>() : null;
        for (GolemNetwork.External ex : net.externals) {
            IItemHandler h = itemHandler(ex);
            if (h == null) {
                continue;
            }
            GlobalPos storageKey = sharedCanonical(shared, ex);
            int[] faceSlots = storageKey != null ? worldlySlots(ex, h) : null;
            Object slotOwner = storageKey != null ? identitySlotOwner(ex, h, faceSlots) : null;
            for (int i = 0; i < h.getSlots(); i++) {
                ItemStack s = h.getStackInSlot(i);
                if (!s.isEmpty() && mayExtract(ex, s)) {
                    if (storageKey != null && !seenSlots.add(itemSlotKey(storageKey, faceSlots, slotOwner, i))) {
                        continue;
                    }
                    all.merge(ItemKey.of(s), (long) s.getCount(), Long::sum);
                }
            }
        }
        return all;
    }

    @SuppressWarnings("unchecked")
    static long itemCountOf(GolemNetwork net, ItemKey key) {
        if (key.stack().isEmpty()) {
            return 0;
        }
        DiskIndex idx = index(net, DiskStorage.store());
        long n = 0;
        if (!idx.itemDisks.isEmpty()) {
            Object loc = idx.itemLocations().get(key);
            if (loc instanceof DiskEntry d) {
                if (d.data != null) {
                    n += d.data.items.getOrDefault(key, 0);
                }
            } else if (loc instanceof List<?> raw) {
                for (DiskEntry d : (List<DiskEntry>) raw) {
                    if (d.data != null) {
                        n += d.data.items.getOrDefault(key, 0);
                    }
                }
            }
        }
        if (net.externals.isEmpty()) {
            return n;
        }
        Set<GlobalPos> shared = sharedStorageKeys(net.externals);
        Set<Object> seenSlots = shared != null ? new HashSet<>() : null;
        for (GolemNetwork.External ex : net.externals) {
            if (!mayExtract(ex, key.stack())) {
                continue;
            }
            IItemHandler h = itemHandler(ex);
            if (h == null) {
                continue;
            }
            GlobalPos storageKey = sharedCanonical(shared, ex);
            int[] faceSlots = storageKey != null ? worldlySlots(ex, h) : null;
            Object slotOwner = storageKey != null ? identitySlotOwner(ex, h, faceSlots) : null;
            for (int i = 0; i < h.getSlots(); i++) {
                ItemStack s = h.getStackInSlot(i);
                if (!s.isEmpty() && key.matches(s)) {
                    if (storageKey != null && !seenSlots.add(itemSlotKey(storageKey, faceSlots, slotOwner, i))) {
                        continue;
                    }
                    n += s.getCount();
                }
            }
        }
        return n;
    }

    static long diskCapacity(GolemNetwork net, boolean fluid) {
        DiskIndex idx = index(net, DiskStorage.store());
        return fluid ? idx.fluidCapacity : idx.itemCapacity;
    }

    static long diskStored(GolemNetwork net, boolean fluid) {
        DiskIndex idx = index(net, DiskStorage.store());
        return fluid ? idx.fluidsStored() : idx.itemsStored();
    }

    @SuppressWarnings("unchecked")
    static ItemStack insertItem(GolemNetwork net, ItemStack input, boolean simulate, @Nullable BlockPos skipTarget) {
        if (input.isEmpty()) {
            return ItemStack.EMPTY;
        }
        DiskStore st = DiskStorage.store();
        DiskIndex idx = index(net, st);
        ItemStack remaining = input.copy();
        int cost = Gear.cost(remaining);
        if (simulate) {
            return simulateInsertItem(net, st, idx, remaining, cost, skipTarget);
        }

        ItemKey itemKey = ItemKey.of(remaining);
        Map<ItemKey, Object> locations = idx.itemDisks.isEmpty() ? null : idx.itemLocations();
        Set<CopperDriveBayBlockEntity> touched = null;
        for (Tier tier : idx.tiersHigh) {
            if (!tier.itemDisks.isEmpty()) {
                // Pass 0: disks in this tier that already hold this item (O(1) lookup via itemLocations)
                Object loc = locations == null ? null : locations.get(itemKey);
                if (loc instanceof DiskEntry d) {
                    if (d.tierIndexHigh == tier.indexHigh) {
                        touched = insertIntoDisk(st, idx, d, itemKey, cost, remaining, touched);
                    }
                } else if (loc instanceof List<?> raw) {
                    List<DiskEntry> holders = (List<DiskEntry>) raw;
                    for (int i = 0; i < holders.size() && !remaining.isEmpty(); i++) {
                        DiskEntry d = holders.get(i);
                        if (d.tierIndexHigh == tier.indexHigh) {
                            touched = insertIntoDisk(st, idx, d, itemKey, cost, remaining, touched);
                        }
                    }
                }
                // Pass 1: any disk in this tier with room
                for (int i = 0; i < tier.itemDisks.size() && !remaining.isEmpty(); i++) {
                    DiskEntry d = tier.itemDisks.get(i);
                    if (d.data != null && d.data.storedItems + cost > d.capacity
                            && !(cost > 1 && d.capacity <= Gear.COST && d.data.gearItems == 0)) {
                        continue;
                    }
                    touched = insertIntoDisk(st, idx, d, itemKey, cost, remaining, touched);
                }
            }
            if (!remaining.isEmpty()) {
                remaining = insertIntoExternals(net, tier, remaining, false, skipTarget, null, null);
            }
            if (remaining.isEmpty()) {
                break;
            }
        }
        markBaysChanged(touched);
        if (remaining.getCount() != input.getCount()) {
            net.bumpEpoch();
        }
        return remaining;
    }

    @Nullable
    private static Set<CopperDriveBayBlockEntity> insertIntoDisk(@Nullable DiskStore st, DiskIndex idx, DiskEntry d,
                                                                 ItemKey key, int cost, ItemStack remaining,
                                                                 @Nullable Set<CopperDriveBayBlockEntity> touched) {
        long prevEpoch = DiskStorage.itemMutationEpoch();
        long prevStored = d.data == null ? 0L : d.data.storedItems;
        int n = DiskStorage.insertItemDirect(st, d.disk, d.data, d.capacity, key, cost, remaining.getCount(), false);
        if (n > 0) {
            remaining.shrink(n);
            if (touched == null) {
                touched = new HashSet<>();
            }
            touched.add(d.bay);
            if (idx.diskAllocEpoch == DiskStorage.diskAllocEpoch()) {
                long newEpoch = DiskStorage.itemMutationEpoch();
                if (idx.itemLocationsEpoch == prevEpoch) {
                    idx.recordPresent(key, d);
                    idx.itemLocationsEpoch = newEpoch;
                }
                if (idx.cachedItemsStoredEpoch == prevEpoch && d.data != null) {
                    idx.cachedItemsStored += d.data.storedItems - prevStored;
                    idx.cachedItemsStoredEpoch = newEpoch;
                }
            }
        }
        return touched;
    }

    private static ItemStack simulateInsertItem(GolemNetwork net, @Nullable DiskStore st, DiskIndex idx,
                                                ItemStack remaining, int cost, @Nullable BlockPos skipTarget) {
        if (st != null && !idx.itemDisks.isEmpty()) {
            if (cost > 1) {
                // Equipment takes more of a disk, and small disks have a rule of their own: each disk answers for itself
                for (DiskEntry d : idx.itemDisks) {
                    int n = DiskStorage.insertItemDirect(st, d.disk, d.data, d.capacity, null, cost, remaining.getCount(), true);
                    remaining.shrink(n);
                    if (remaining.isEmpty()) {
                        return ItemStack.EMPTY;
                    }
                }
            } else {
                int need = remaining.getCount();
                long free = 0;
                for (DiskEntry d : idx.itemDisks) {
                    long stored = d.data == null ? 0L : d.data.storedItems;
                    free += Math.max(0L, d.capacity - stored);
                    if (free >= need) {
                        return ItemStack.EMPTY;
                    }
                }
                remaining.shrink((int) free);
            }
        }
        // Disks are already counted above; check external containers one by one (deduplicating shared slots)
        Set<GlobalPos> shared = sharedStorageKeys(net.externals);
        Set<Object> simInsertedSlots = shared != null ? new HashSet<>() : null;
        for (Tier tier : idx.tiersHigh) {
            if (remaining.isEmpty()) {
                break;
            }
            remaining = insertIntoExternals(net, tier, remaining, true, skipTarget, shared, simInsertedSlots);
        }
        return remaining;
    }

    private static ItemStack insertIntoExternals(GolemNetwork net, Tier tier, ItemStack remaining,
                                                 boolean simulate, @Nullable BlockPos skipTarget,
                                                 @Nullable Set<GlobalPos> shared,
                                                 @Nullable Set<Object> simInsertedSlots) {
        for (GolemNetwork.External ex : tier.exts) {
            if (remaining.isEmpty()) {
                break;
            }
            if (skips(net, ex, skipTarget)) {
                continue;
            }
            IItemHandler h = itemHandler(ex);
            if (h == null || !mayInsert(ex, remaining)) {
                continue;
            }
            GlobalPos storageKey = sharedCanonical(shared, ex);
            int[] faceSlots = storageKey != null ? worldlySlots(ex, h) : null;
            Object slotOwner = storageKey != null ? identitySlotOwner(ex, h, faceSlots) : null;
            for (int i = 0; i < h.getSlots() && !remaining.isEmpty(); i++) {
                Object slotKey = null;
                if (storageKey != null) {
                    slotKey = itemSlotKey(storageKey, faceSlots, slotOwner, i);
                    if (simInsertedSlots.contains(slotKey)) {
                        continue;
                    }
                }
                int before = remaining.getCount();
                remaining = h.insertItem(i, remaining, simulate);
                if (storageKey != null && remaining.getCount() < before) {
                    simInsertedSlots.add(slotKey);
                }
            }
        }
        return remaining;
    }

    @SuppressWarnings("unchecked")
    static ItemStack extractItem(GolemNetwork net, ItemStack template, int amount, boolean simulate, @Nullable BlockPos skipTarget) {
        if (template.isEmpty() || amount <= 0) {
            return ItemStack.EMPTY;
        }
        DiskStore st = DiskStorage.store();
        DiskIndex idx = index(net, st);
        ItemKey key = ItemKey.of(template);
        Object loc = idx.itemDisks.isEmpty() ? null : idx.itemLocations().get(key);
        if (loc == null && net.externals.isEmpty()) {
            return ItemStack.EMPTY;
        }

        int cost = Gear.cost(template);
        int left = amount;
        Set<CopperDriveBayBlockEntity> touched = null;

        // Fast path when no external periscopes exist: extract directly from the 1+ disks holding key
        if (net.externals.isEmpty()) {
            if (loc instanceof DiskEntry d) {
                long prevEpoch = DiskStorage.itemMutationEpoch();
                long prevStored = d.data == null ? 0L : d.data.storedItems;
                int n = DiskStorage.extractItemDirect(st, d.disk, d.data, key, cost, left, simulate);
                if (n > 0) {
                    left -= n;
                    if (!simulate) {
                        touched = new HashSet<>(2);
                        touched.add(d.bay);
                        updateIndexAfterExtract(idx, d, key, prevEpoch, prevStored);
                    }
                }
            } else if (loc instanceof List<?> raw) {
                List<DiskEntry> holders = (List<DiskEntry>) raw;
                if (idx.tiersLow.size() == 1) {
                    for (int i = 0; i < holders.size() && left > 0; i++) {
                        DiskEntry d = holders.get(i);
                        long prevEpoch = DiskStorage.itemMutationEpoch();
                        long prevStored = d.data == null ? 0L : d.data.storedItems;
                        int n = DiskStorage.extractItemDirect(st, d.disk, d.data, key, cost, left, simulate);
                        if (n > 0) {
                            left -= n;
                            if (!simulate) {
                                if (touched == null) {
                                    touched = new HashSet<>();
                                }
                                touched.add(d.bay);
                                if (updateIndexAfterExtract(idx, d, key, prevEpoch, prevStored)) {
                                    i--;
                                }
                            }
                        }
                    }
                } else {
                    for (Tier tier : idx.tiersLow) {
                        if (left <= 0) {
                            break;
                        }
                        for (int i = 0; i < holders.size() && left > 0; i++) {
                            DiskEntry d = holders.get(i);
                            if (d.tierIndexHigh != tier.indexHigh) {
                                continue;
                            }
                            long prevEpoch = DiskStorage.itemMutationEpoch();
                            long prevStored = d.data == null ? 0L : d.data.storedItems;
                            int n = DiskStorage.extractItemDirect(st, d.disk, d.data, key, cost, left, simulate);
                            if (n > 0) {
                                left -= n;
                                if (!simulate) {
                                    if (touched == null) {
                                        touched = new HashSet<>();
                                    }
                                    touched.add(d.bay);
                                    if (updateIndexAfterExtract(idx, d, key, prevEpoch, prevStored)) {
                                        i--;
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (!simulate) {
                markBaysChanged(touched);
                if (left != amount) {
                    net.bumpEpoch();
                }
            }
            int taken = amount - left;
            return taken > 0 ? template.copyWithCount(taken) : ItemStack.EMPTY;
        }

        // General path with external periscopes: interleave disk extraction and external extraction per priority tier
        Set<GlobalPos> shared = simulate ? sharedStorageKeys(net.externals) : null;
        Map<Object, Integer> simTaken = shared != null ? new HashMap<>() : null;
        for (Tier tier : idx.tiersLow) {
            if (left <= 0) {
                break;
            }
            if (loc instanceof DiskEntry d) {
                if (d.tierIndexHigh == tier.indexHigh) {
                    long prevEpoch = DiskStorage.itemMutationEpoch();
                    long prevStored = d.data == null ? 0L : d.data.storedItems;
                    int n = DiskStorage.extractItemDirect(st, d.disk, d.data, key, cost, left, simulate);
                    if (n > 0) {
                        left -= n;
                        if (!simulate) {
                            if (touched == null) {
                                touched = new HashSet<>();
                            }
                            touched.add(d.bay);
                            updateIndexAfterExtract(idx, d, key, prevEpoch, prevStored);
                        }
                    }
                }
            } else if (loc instanceof List<?> raw) {
                List<DiskEntry> holders = (List<DiskEntry>) raw;
                for (int i = 0; i < holders.size() && left > 0; i++) {
                    DiskEntry d = holders.get(i);
                    if (d.tierIndexHigh != tier.indexHigh) {
                        continue;
                    }
                    long prevEpoch = DiskStorage.itemMutationEpoch();
                    long prevStored = d.data == null ? 0L : d.data.storedItems;
                    int n = DiskStorage.extractItemDirect(st, d.disk, d.data, key, cost, left, simulate);
                    if (n > 0) {
                        left -= n;
                        if (!simulate) {
                            if (touched == null) {
                                touched = new HashSet<>();
                            }
                            touched.add(d.bay);
                            if (updateIndexAfterExtract(idx, d, key, prevEpoch, prevStored)) {
                                i--;
                            }
                        }
                    }
                }
            }
            for (GolemNetwork.External ex : tier.exts) {
                if (left <= 0) {
                    break;
                }
                if (skips(net, ex, skipTarget) || !mayExtract(ex, template)) {
                    continue;
                }
                IItemHandler h = itemHandler(ex);
                if (h == null) {
                    continue;
                }
                GlobalPos storageKey = sharedCanonical(shared, ex);
                int[] faceSlots = storageKey != null ? worldlySlots(ex, h) : null;
                Object slotOwner = storageKey != null ? identitySlotOwner(ex, h, faceSlots) : null;
                for (int i = 0; i < h.getSlots() && left > 0; i++) {
                    ItemStack s = h.getStackInSlot(i);
                    if (!s.isEmpty() && ItemStack.isSameItemSameComponents(s, template)) {
                        int avail = s.getCount();
                        Object slotKey = null;
                        int prevTaken = 0;
                        if (storageKey != null) {
                            slotKey = itemSlotKey(storageKey, faceSlots, slotOwner, i);
                            prevTaken = simTaken.getOrDefault(slotKey, 0);
                            avail -= prevTaken;
                            if (avail <= 0) {
                                continue;
                            }
                        }
                        ItemStack got = h.extractItem(i, Math.min(left, avail), simulate);
                        int gotCount = got.getCount();
                        left -= gotCount;
                        if (storageKey != null && gotCount > 0) {
                            simTaken.put(slotKey, prevTaken + gotCount);
                        }
                    }
                }
            }
        }
        if (!simulate) {
            markBaysChanged(touched);
            if (left != amount) {
                net.bumpEpoch();
            }
        }
        int taken = amount - left;
        return taken > 0 ? template.copyWithCount(taken) : ItemStack.EMPTY;
    }

    /** Returns true if d was removed from a multi-disk holders list (so callers iterating by index adjust their cursor). */
    private static boolean updateIndexAfterExtract(DiskIndex idx, DiskEntry d, ItemKey key, long prevEpoch, long prevStored) {
        long newEpoch = DiskStorage.itemMutationEpoch();
        boolean removedFromList = false;
        if (idx.itemLocationsEpoch == prevEpoch) {
            if (d.data == null || !d.data.items.containsKey(key)) {
                boolean wasList = idx.itemLocations != null && idx.itemLocations.get(key) instanceof List<?>;
                idx.recordAbsent(key, d);
                removedFromList = wasList;
            }
            idx.itemLocationsEpoch = newEpoch;
        }
        if (idx.cachedItemsStoredEpoch == prevEpoch && d.data != null) {
            idx.cachedItemsStored += d.data.storedItems - prevStored;
            idx.cachedItemsStoredEpoch = newEpoch;
        }
        return removedFromList;
    }

    // ----------------------------------------------------------------- fluids

    static List<FluidStack> listFluids(GolemNetwork net) {
        DiskIndex idx = index(net, DiskStorage.store());
        List<FluidStack> small = null;
        Map<FluidKey, Long> totals = null;

        for (DiskEntry d : idx.fluidDisks) {
            if (d.data == null) {
                continue;
            }
            for (FluidStack f : d.data.fluids) {
                if (f.isEmpty()) {
                    continue;
                }
                if (totals != null) {
                    totals.merge(new FluidKey(f), (long) f.getAmount(), Long::sum);
                } else {
                    if (small == null) {
                        small = new ArrayList<>(4);
                    }
                    totals = mergeFluidSmall(small, f, f.getAmount());
                }
            }
        }
        Set<GlobalPos> shared = sharedStorageKeys(net.externals);
        Set<HandlerSlotKey> seenTanks = shared != null ? new HashSet<>() : null;
        for (GolemNetwork.External ex : net.externals) {
            IFluidHandler h = fluidHandler(ex);
            if (h == null) {
                continue;
            }
            boolean checkDedup = sharedCanonical(shared, ex) != null;
            for (int i = 0; i < h.getTanks(); i++) {
                FluidStack f = h.getFluidInTank(i);
                // Liquid Cryo-Source in a tank behind a periscope is not network stock: the network makes its own from Source
                if (!f.isEmpty() && !CryoSource.is(f) && mayExtract(ex, f)) {
                    if (checkDedup && !seenTanks.add(new HandlerSlotKey(h, i))) {
                        continue;
                    }
                    if (totals != null) {
                        totals.merge(new FluidKey(f), (long) f.getAmount(), Long::sum);
                    } else {
                        if (small == null) {
                            small = new ArrayList<>(4);
                        }
                        totals = mergeFluidSmall(small, f, f.getAmount());
                    }
                }
            }
        }
        if (totals != null) {
            List<FluidStack> all = new ArrayList<>(totals.size());
            for (Map.Entry<FluidKey, Long> e : totals.entrySet()) {
                all.add(e.getKey().stack.copyWithAmount((int) Math.min(e.getValue(), Integer.MAX_VALUE)));
            }
            return all;
        }
        return small == null ? List.of() : small;
    }

    /** Linear merge for small fluid counts (0..16 types); upgrades to a LinkedHashMap if a network exceeds the threshold. */
    @Nullable
    private static Map<FluidKey, Long> mergeFluidSmall(List<FluidStack> small, FluidStack fluid, int amount) {
        for (int i = 0; i < small.size(); i++) {
            FluidStack existing = small.get(i);
            if (FluidStack.isSameFluidSameComponents(existing, fluid)) {
                long sum = (long) existing.getAmount() + amount;
                existing.setAmount((int) Math.min(sum, Integer.MAX_VALUE));
                return null;
            }
        }
        if (small.size() < FLUID_MAP_THRESHOLD) {
            small.add(fluid.copyWithAmount(amount));
            return null;
        }
        Map<FluidKey, Long> map = new LinkedHashMap<>(small.size() * 2);
        for (FluidStack f : small) {
            map.merge(new FluidKey(f), (long) f.getAmount(), Long::sum);
        }
        map.merge(new FluidKey(fluid), (long) amount, Long::sum);
        return map;
    }

    static int insertFluid(GolemNetwork net, FluidStack input, boolean simulate, @Nullable BlockPos skipTarget) {
        // Liquid Cryo-Source is never stored: it only exists while automation pumps it out of the network
        if (input.isEmpty() || CryoSource.is(input)) {
            return 0;
        }
        DiskStore st = DiskStorage.store();
        DiskIndex idx = index(net, st);
        int left = input.getAmount();
        if (simulate) {
            if (st != null) {
                long free = 0;
                for (DiskEntry d : idx.fluidDisks) {
                    long stored = d.data == null ? 0L : d.data.storedFluids;
                    free += Math.max(0L, d.capacity - stored);
                    if (free >= left) {
                        return input.getAmount();
                    }
                }
                left -= (int) free;
            }
        }
        Set<CopperDriveBayBlockEntity> touched = null;
        Set<GlobalPos> shared = simulate ? sharedStorageKeys(net.externals) : null;
        Set<IFluidHandler> simFilledHandlers = shared != null ? Collections.newSetFromMap(new IdentityHashMap<>()) : null;
        IFluidHandler.FluidAction action = simulate ? IFluidHandler.FluidAction.SIMULATE : IFluidHandler.FluidAction.EXECUTE;
        for (Tier tier : idx.tiersHigh) {
            if (left <= 0) {
                break;
            }
            if (!simulate && !tier.fluidDisks.isEmpty()) {
                for (int pass = 0; pass < 2 && left > 0; pass++) {
                    for (DiskEntry d : tier.fluidDisks) {
                        if (left <= 0) {
                            break;
                        }
                        if (pass == 0 && !DiskStorage.containsFluidDirect(d.data, input)) {
                            continue;
                        }
                        int n = DiskStorage.insertFluidDirect(st, d.disk, d.data, d.capacity, input, left, false);
                        if (n > 0) {
                            left -= n;
                            if (touched == null) {
                                touched = new HashSet<>();
                            }
                            touched.add(d.bay);
                        }
                    }
                }
            }
            for (GolemNetwork.External ex : tier.exts) {
                if (left <= 0) {
                    break;
                }
                if (skips(net, ex, skipTarget)) {
                    continue;
                }
                IFluidHandler h = fluidHandler(ex);
                if (h == null || !mayInsert(ex, input)) {
                    continue;
                }
                boolean checkDedup = sharedCanonical(shared, ex) != null;
                if (checkDedup && simFilledHandlers.contains(h)) {
                    continue;
                }
                int filled = h.fill(input.copyWithAmount(left), action);
                left -= filled;
                if (checkDedup && filled > 0) {
                    simFilledHandlers.add(h);
                }
            }
        }
        if (!simulate) {
            markBaysChanged(touched);
            if (left != input.getAmount()) {
                net.bumpEpoch();
            }
        }
        return input.getAmount() - left;
    }

    static FluidStack extractFluid(GolemNetwork net, FluidStack template, int amount, boolean simulate, @Nullable BlockPos skipTarget) {
        if (template.isEmpty() || amount <= 0) {
            return FluidStack.EMPTY;
        }
        if (CryoSource.is(template)) {
            return extractCryoSource(net, amount, simulate);
        }
        DiskStore st = DiskStorage.store();
        DiskIndex idx = index(net, st);
        int left = amount;
        Set<CopperDriveBayBlockEntity> touched = null;
        Set<GlobalPos> shared = simulate ? sharedStorageKeys(net.externals) : null;
        Map<IFluidHandler, Integer> simDrained = shared != null ? new IdentityHashMap<>() : null;
        IFluidHandler.FluidAction action = simulate ? IFluidHandler.FluidAction.SIMULATE : IFluidHandler.FluidAction.EXECUTE;
        for (Tier tier : idx.tiersLow) {
            if (left <= 0) {
                break;
            }
            for (DiskEntry d : tier.fluidDisks) {
                if (left <= 0) {
                    break;
                }
                int n = DiskStorage.extractFluidDirect(st, d.disk, d.data, template, left, simulate);
                if (n > 0) {
                    left -= n;
                    if (!simulate) {
                        if (touched == null) {
                            touched = new HashSet<>();
                        }
                        touched.add(d.bay);
                    }
                }
            }
            for (GolemNetwork.External ex : tier.exts) {
                if (left <= 0) {
                    break;
                }
                if (skips(net, ex, skipTarget) || !mayExtract(ex, template)) {
                    continue;
                }
                IFluidHandler h = fluidHandler(ex);
                if (h == null) {
                    continue;
                }
                if (sharedCanonical(shared, ex) != null) {
                    int already = simDrained.getOrDefault(h, 0);
                    int ask = (int) Math.min((long) left + already, Integer.MAX_VALUE);
                    int totalDrainable = h.drain(template.copyWithAmount(ask), action).getAmount();
                    int extra = Math.max(0, Math.min(left, totalDrainable - already));
                    if (extra > 0) {
                        left -= extra;
                        simDrained.put(h, already + extra);
                    }
                } else {
                    FluidStack got = h.drain(template.copyWithAmount(left), action);
                    left -= got.getAmount();
                }
            }
        }
        if (!simulate) {
            markBaysChanged(touched);
            if (left != amount) {
                net.bumpEpoch();
            }
        }
        int taken = amount - left;
        return taken > 0 ? template.copyWithAmount(taken) : FluidStack.EMPTY;
    }

    static long fluidCountOf(GolemNetwork net, FluidStack fluid) {
        if (fluid.isEmpty()) {
            return 0;
        }
        if (CryoSource.is(fluid)) {
            return cryoSourceAvailable(net);
        }
        DiskIndex idx = index(net, DiskStorage.store());
        long n = 0;
        for (DiskEntry d : idx.fluidDisks) {
            n += DiskStorage.fluidCountDirect(d.data, fluid);
        }
        if (net.externals.isEmpty()) {
            return n;
        }
        Set<GlobalPos> shared = sharedStorageKeys(net.externals);
        Set<HandlerSlotKey> seenTanks = shared != null ? new HashSet<>() : null;
        for (GolemNetwork.External ex : net.externals) {
            if (!mayExtract(ex, fluid)) {
                continue;
            }
            IFluidHandler h = fluidHandler(ex);
            if (h == null) {
                continue;
            }
            boolean checkDedup = sharedCanonical(shared, ex) != null;
            for (int i = 0; i < h.getTanks(); i++) {
                FluidStack f = h.getFluidInTank(i);
                if (!f.isEmpty() && FluidStack.isSameFluidSameComponents(f, fluid)) {
                    if (checkDedup && !seenTanks.add(new HandlerSlotKey(h, i))) {
                        continue;
                    }
                    n += f.getAmount();
                }
            }
        }
        return n;
    }

    static long countOf(GolemNetwork net, ItemKey key) {
        return Ingredients.isFluid(key) ? fluidCountOf(net, Ingredients.fluid(key, 1)) : itemCountOf(net, key);
    }

    // --------------------------------------------------------- Source & Cryo

    private static long availableSource(GolemNetwork net) {
        if (net.sources.isEmpty()) {
            return 0;
        }
        Set<SourceHelper.Supplier> leaves = new HashSet<>();
        for (SourceHelper.Supplier s : net.sources) {
            s.collectLeafSuppliers(leaves);
        }
        long total = 0;
        for (SourceHelper.Supplier s : leaves) {
            total += s.available();
        }
        return total;
    }

    static long cryoSourceAvailable(GolemNetwork net) {
        if (!net.isOnline()) {
            return 0;
        }
        long per = GolemConfig.CRYO_SOURCE_COST.get();
        int discount = net.sourceDiscountPercent();
        if (per <= 0 || discount >= 100 || !SourceHelper.sourceRequired() || net.hasInfiniteSource()) {
            return Integer.MAX_VALUE;
        }
        long maxBase = net.sourceDiscount().maxAffordableBase(availableSource(net), discount);
        return maxBase / per;
    }

    /** Makes Liquid Cryo-Source from the network's Source (all of the amount that the Source covers). */
    private static FluidStack extractCryoSource(GolemNetwork net, int amount, boolean simulate) {
        int n = (int) Math.min(amount, cryoSourceAvailable(net));
        long per = GolemConfig.CRYO_SOURCE_COST.get();
        if (per > 0 && net.sourceDiscountPercent() < 100 && SourceHelper.sourceRequired() && !net.hasInfiniteSource()) {
            // consumeSource takes an int, so never hand out more mB than an int cost can pay for
            n = (int) Math.min(n, Integer.MAX_VALUE / per);
        }
        if (n <= 0) {
            return FluidStack.EMPTY;
        }
        if (!simulate && !consumeSource(net, (int) CryoSource.cost(n))) {
            return FluidStack.EMPTY;
        }
        return CryoSource.stack(n);
    }

    static boolean hasSource(GolemNetwork net, int amount) {
        if (amount <= 0 || !SourceHelper.sourceRequired() || net.hasInfiniteSource()) {
            return true;
        }
        int discount = net.sourceDiscountPercent();
        int needed = net.sourceDiscount().previewDrain(amount, discount);
        return availableSource(net) >= needed;
    }

    static boolean consumeSource(GolemNetwork net, int amount) {
        if (amount <= 0 || !SourceHelper.sourceRequired() || net.hasInfiniteSource()) {
            return true;
        }
        int discount = net.sourceDiscountPercent();
        SourceDiscount meter = net.sourceDiscount();
        int needed = meter.previewDrain(amount, discount);
        if (availableSource(net) < needed) {
            return false;
        }
        Set<SourceHelper.Supplier> seen = new HashSet<>();
        int left = needed;
        for (SourceHelper.Supplier s : net.sources) {
            if (left <= 0) {
                break;
            }
            left -= s.drainUnique(left, seen);
        }
        if (left > 0) {
            return false;
        }
        int creditBefore = meter.creditHundredths();
        meter.charge(amount, discount);
        if (net.core != null && meter.creditHundredths() != creditBefore) {
            net.core.setChanged();
        }
        return true;
    }
}
