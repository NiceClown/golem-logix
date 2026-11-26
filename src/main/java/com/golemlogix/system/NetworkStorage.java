package com.golemlogix.system;

import com.golemlogix.blockentity.CopperDriveBayBlockEntity;
import com.golemlogix.config.GolemConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.capabilities.BlockCapability;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.resource.Resource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
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
    /** A disk in its cabinet. */
    private record DiskRef(CopperDriveBayBlockEntity bay, ItemStack disk) {
    }

    /** Storages (cabinets and periscopes) sharing one priority; equal ones go in network walk order. */
    private record Tier(int priority, List<CopperDriveBayBlockEntity> bays, List<GolemNetwork.External> exts) {
    }

    private NetworkStorage() {
    }

    // ------------------------------------------------------------- disks & tiers

    private static List<ItemStack> allDisks(GolemNetwork net, boolean fluid) {
        if (net.bays.isEmpty()) {
            return List.of();
        }
        List<ItemStack> disks = null;
        UUID firstId = null;
        Set<UUID> seenIds = null;
        for (CopperDriveBayBlockEntity bay : net.bays) {
            if (bay.isRemoved()) {
                continue;
            }
            for (ItemStack disk : bay.getDisks()) {
                if (disk.isEmpty()) {
                    continue;
                }
                UUID id = DiskStorage.diskId(disk);
                if (id != null) {
                    if (seenIds != null) {
                        if (!seenIds.add(id)) {
                            continue;
                        }
                    } else if (firstId == null) {
                        firstId = id;
                    } else if (firstId.equals(id)) {
                        continue;
                    } else {
                        seenIds = new HashSet<>();
                        seenIds.add(firstId);
                        seenIds.add(id);
                    }
                }
                if (fluid ? DiskStorage.isFluidDisk(disk) : DiskStorage.isItemDisk(disk)) {
                    if (disks == null) {
                        disks = new ArrayList<>();
                    }
                    disks.add(disk);
                }
            }
        }
        return disks == null ? List.of() : disks;
    }

    /**
     * Disks whose id is already mounted earlier in the network: a copy (a creative middle-click, an inventory backup, another mod)
     * shares the original's contents, so it is skipped and nothing is counted or offered twice.
     */
    private static Set<ItemStack> copiedDisks(GolemNetwork net) {
        if (net.bays.isEmpty()) {
            return Set.of();
        }
        UUID firstId = null;
        Set<UUID> ids = null;
        Set<ItemStack> copies = null;
        for (CopperDriveBayBlockEntity bay : net.bays) {
            if (bay.isRemoved()) {
                continue;
            }
            for (ItemStack disk : bay.getDisks()) {
                if (disk.isEmpty()) {
                    continue;
                }
                UUID id = DiskStorage.diskId(disk);
                if (id == null) {
                    continue;
                }
                boolean duplicate;
                if (ids != null) {
                    duplicate = !ids.add(id);
                } else if (firstId == null) {
                    firstId = id;
                    duplicate = false;
                } else if (firstId.equals(id)) {
                    duplicate = true;
                } else {
                    ids = new HashSet<>();
                    ids.add(firstId);
                    ids.add(id);
                    duplicate = false;
                }
                if (duplicate) {
                    if (copies == null) {
                        copies = Collections.newSetFromMap(new IdentityHashMap<>());
                    }
                    copies.add(disk);
                }
            }
        }
        return copies == null ? Set.of() : copies;
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

    private static List<Tier> tiers(GolemNetwork net, boolean highestFirst) {
        boolean hasAny = false;
        boolean singlePriority = true;
        int firstPriority = 0;
        for (CopperDriveBayBlockEntity bay : net.bays) {
            if (bay.isRemoved()) {
                continue;
            }
            int p = bay.getPriority();
            if (!hasAny) {
                hasAny = true;
                firstPriority = p;
            } else if (p != firstPriority) {
                singlePriority = false;
                break;
            }
        }
        if (singlePriority) {
            for (GolemNetwork.External ex : net.externals) {
                if (isRemoved(ex)) {
                    continue;
                }
                int p = ex.priority();
                if (!hasAny) {
                    hasAny = true;
                    firstPriority = p;
                } else if (p != firstPriority) {
                    singlePriority = false;
                    break;
                }
            }
        }
        if (!hasAny) {
            return List.of();
        }
        if (singlePriority) {
            List<CopperDriveBayBlockEntity> liveBays = new ArrayList<>(net.bays.size());
            for (CopperDriveBayBlockEntity bay : net.bays) {
                if (!bay.isRemoved()) {
                    liveBays.add(bay);
                }
            }
            List<GolemNetwork.External> liveExts = new ArrayList<>(net.externals.size());
            for (GolemNetwork.External ex : net.externals) {
                if (!isRemoved(ex)) {
                    liveExts.add(ex);
                }
            }
            return List.of(new Tier(firstPriority, liveBays, liveExts));
        }
        Comparator<Integer> order = highestFirst ? Comparator.reverseOrder() : Comparator.naturalOrder();
        TreeMap<Integer, Tier> map = new TreeMap<>(order);
        for (CopperDriveBayBlockEntity bay : net.bays) {
            if (!bay.isRemoved()) {
                map.computeIfAbsent(bay.getPriority(), p -> new Tier(p, new ArrayList<>(), new ArrayList<>())).bays.add(bay);
            }
        }
        for (GolemNetwork.External ex : net.externals) {
            if (!isRemoved(ex)) {
                map.computeIfAbsent(ex.priority(), p -> new Tier(p, new ArrayList<>(), new ArrayList<>())).exts.add(ex);
            }
        }
        return new ArrayList<>(map.values());
    }

    private static List<DiskRef> disksOf(Tier tier, boolean fluid, Set<ItemStack> copies) {
        if (tier.bays.isEmpty()) {
            return List.of();
        }
        List<DiskRef> disks = new ArrayList<>();
        for (CopperDriveBayBlockEntity bay : tier.bays) {
            for (ItemStack disk : bay.getDisks()) {
                if ((fluid ? DiskStorage.isFluidDisk(disk) : DiskStorage.isItemDisk(disk))
                        && (copies.isEmpty() || !copies.contains(disk))) {
                    disks.add(new DiskRef(bay, disk));
                }
            }
        }
        return disks;
    }

    // ------------------------------------------------------------- externals

    private static boolean skips(GolemNetwork net, GolemNetwork.External ex, @Nullable BlockPos skipTarget) {
        return skipTarget != null && ex.level() == net.level && ex.target().equals(skipTarget);
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
    private static ResourceHandler<ItemResource> itemHandler(GolemNetwork.External ex) {
        Level lvl = ex.level();
        if (isRemoved(ex) || !lvl.isLoaded(ex.target())) {
            return null;
        }
        if (ex.owner() != null && lvl instanceof ServerLevel sl) {
            return ex.owner().targetCaps(sl, ex.target(), ex.face()).itemsAnySide();
        }
        return anySideCapability(lvl, ex.target(), ex.face(), Capabilities.Item.BLOCK);
    }

    @Nullable
    private static ResourceHandler<FluidResource> fluidHandler(GolemNetwork.External ex) {
        Level lvl = ex.level();
        if (isRemoved(ex) || !lvl.isLoaded(ex.target())) {
            return null;
        }
        if (ex.owner() != null && lvl instanceof ServerLevel sl) {
            return ex.owner().targetCaps(sl, ex.target(), ex.face()).fluidsAnySide();
        }
        return anySideCapability(lvl, ex.target(), ex.face(), Capabilities.Fluid.BLOCK);
    }

    @Nullable
    private static <R extends Resource> ResourceHandler<R> anySideCapability(
            Level lvl, BlockPos target, Direction preferred, BlockCapability<ResourceHandler<R>, Direction> cap) {
        ResourceHandler<R> h = lvl.getCapability(cap, target, preferred);
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
        Map<ItemKey, Long> all = new LinkedHashMap<>();
        for (ItemStack disk : allDisks(net, false)) {
            for (Map.Entry<ItemKey, Integer> e : DiskStorage.rawItems(disk).entrySet()) {
                all.merge(e.getKey(), (long) e.getValue(), Long::sum);
            }
        }
        for (GolemNetwork.External ex : net.externals) {
            ResourceHandler<ItemResource> h = itemHandler(ex);
            if (h == null) {
                continue;
            }
            for (int i = 0; i < h.size(); i++) {
                ItemResource r = h.getResource(i);
                int amt = h.getAmountAsInt(i);
                if (!r.isEmpty() && amt > 0) {
                    ItemStack s = r.toStack(1);
                    if (mayExtract(ex, s)) {
                        all.merge(ItemKey.ofSingleOwned(s), (long) amt, Long::sum);
                    }
                }
            }
        }
        return all;
    }

    static long itemCountOf(GolemNetwork net, ItemKey key) {
        if (key.stack().isEmpty()) {
            return 0;
        }
        long n = 0;
        for (ItemStack disk : allDisks(net, false)) {
            n += DiskStorage.itemCount(disk, key);
        }
        if (net.externals.isEmpty()) {
            return n;
        }
        ItemResource want = ItemResource.of(key.stack());
        for (GolemNetwork.External ex : net.externals) {
            if (!mayExtract(ex, key.stack())) {
                continue;
            }
            ResourceHandler<ItemResource> h = itemHandler(ex);
            if (h == null) {
                continue;
            }
            for (int i = 0; i < h.size(); i++) {
                int amt = h.getAmountAsInt(i);
                if (amt > 0 && h.getResource(i).equals(want)) {
                    n += amt;
                }
            }
        }
        return n;
    }

    static long diskCapacity(GolemNetwork net, boolean fluid) {
        long cap = 0;
        for (ItemStack disk : allDisks(net, fluid)) {
            cap += DiskStorage.capacity(disk);
        }
        return cap;
    }

    static long diskStored(GolemNetwork net, boolean fluid) {
        long used = 0;
        for (ItemStack disk : allDisks(net, fluid)) {
            used += DiskStorage.storedItems(disk);
        }
        return used;
    }

    static ItemStack insertItem(GolemNetwork net, ItemStack input, boolean simulate, @Nullable BlockPos skipTarget) {
        if (input.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack remaining = input.copy();
        if (simulate) {
            return simulateInsertItem(net, remaining, skipTarget);
        }

        Set<ItemStack> copies = copiedDisks(net);
        ItemKey itemKey = ItemKey.of(remaining);
        Set<CopperDriveBayBlockEntity> touched = null;
        for (Tier tier : tiers(net, true)) {
            List<DiskRef> disks = disksOf(tier, false, copies);
            for (int pass = 0; pass < 2 && !remaining.isEmpty(); pass++) {
                for (DiskRef d : disks) {
                    if (remaining.isEmpty()) {
                        break;
                    }
                    if (pass == 0 && !DiskStorage.containsItem(d.disk, itemKey)) {
                        continue;
                    }
                    int n = DiskStorage.insertItem(d.disk, remaining, false);
                    if (n > 0) {
                        remaining.shrink(n);
                        if (touched == null) {
                            touched = new HashSet<>();
                        }
                        touched.add(d.bay);
                    }
                }
            }
            remaining = insertIntoExternals(net, tier, remaining, false, skipTarget);
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

    private static ItemStack simulateInsertItem(GolemNetwork net, ItemStack remaining, @Nullable BlockPos skipTarget) {
        if (Gear.is(remaining)) {
            // Equipment takes more of a disk, and small disks have a rule of their own: each disk answers for itself
            for (ItemStack disk : allDisks(net, false)) {
                remaining.shrink(DiskStorage.insertItem(disk, remaining, true));
                if (remaining.isEmpty()) {
                    return ItemStack.EMPTY;
                }
            }
        } else {
            long free = 0;
            for (ItemStack disk : allDisks(net, false)) {
                free += Math.max(0, DiskStorage.capacity(disk) - DiskStorage.storedItems(disk));
            }
            remaining.shrink((int) Math.min(free, remaining.getCount()));
            if (remaining.isEmpty()) {
                return ItemStack.EMPTY;
            }
        }
        // Disks are already counted above; check external containers one by one
        for (Tier tier : tiers(net, true)) {
            if (remaining.isEmpty()) {
                break;
            }
            remaining = insertIntoExternals(net, tier, remaining, true, skipTarget);
        }
        return remaining;
    }

    private static ItemStack insertIntoExternals(GolemNetwork net, Tier tier, ItemStack remaining,
                                                 boolean simulate, @Nullable BlockPos skipTarget) {
        for (GolemNetwork.External ex : tier.exts) {
            if (remaining.isEmpty()) {
                break;
            }
            if (skips(net, ex, skipTarget)) {
                continue;
            }
            ResourceHandler<ItemResource> h = itemHandler(ex);
            if (h == null || !mayInsert(ex, remaining)) {
                continue;
            }
            remaining = Transfers.insertItem(h, remaining, false, simulate);
        }
        return remaining;
    }

    static ItemStack extractItem(GolemNetwork net, ItemStack template, int amount, boolean simulate, @Nullable BlockPos skipTarget) {
        if (template.isEmpty() || amount <= 0) {
            return ItemStack.EMPTY;
        }
        int left = amount;
        Set<CopperDriveBayBlockEntity> touched = null;
        Set<ItemStack> copies = copiedDisks(net);
        ItemResource want = net.externals.isEmpty() ? null : ItemResource.of(template);

        Transaction tx = null;
        boolean txModified = false;
        try {
            for (Tier tier : tiers(net, false)) {
                if (left <= 0) {
                    break;
                }
                for (DiskRef d : disksOf(tier, false, copies)) {
                    if (left <= 0) {
                        break;
                    }
                    int n = DiskStorage.extractItem(d.disk, template, left, simulate);
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
                    ResourceHandler<ItemResource> h = itemHandler(ex);
                    if (h == null) {
                        continue;
                    }
                    for (int i = 0; i < h.size() && left > 0; i++) {
                        ItemResource r = h.getResource(i);
                        int amt = h.getAmountAsInt(i);
                        if (amt > 0 && r.equals(want)) {
                            if (tx == null) {
                                tx = Transaction.openRoot();
                            }
                            int got = h.extract(i, r, Math.min(left, amt), tx);
                            if (got > 0) {
                                left -= got;
                                txModified = true;
                            }
                        }
                    }
                }
            }
            if (tx != null && !simulate && txModified) {
                tx.commit();
            }
        } finally {
            if (tx != null) {
                tx.close();
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

    // ----------------------------------------------------------------- fluids

    static List<FluidStack> listFluids(GolemNetwork net) {
        Map<FluidResource, Long> totals = new LinkedHashMap<>();
        for (ItemStack disk : allDisks(net, true)) {
            for (FluidStack f : DiskStorage.rawFluids(disk)) {
                if (!f.isEmpty()) {
                    totals.merge(FluidResource.of(f), (long) f.getAmount(), Long::sum);
                }
            }
        }
        for (GolemNetwork.External ex : net.externals) {
            ResourceHandler<FluidResource> h = fluidHandler(ex);
            if (h == null) {
                continue;
            }
            for (int i = 0; i < h.size(); i++) {
                FluidResource r = h.getResource(i);
                int amt = h.getAmountAsInt(i);
                if (r.isEmpty() || amt <= 0) {
                    continue;
                }
                FluidStack f = r.toStack(1);
                // Liquid Cryo-Source in a tank behind a periscope is not network stock: the network makes its own from Source
                if (!CryoSource.is(f) && mayExtract(ex, f)) {
                    totals.merge(r, (long) amt, Long::sum);
                }
            }
        }
        List<FluidStack> all = new ArrayList<>(totals.size());
        for (Map.Entry<FluidResource, Long> e : totals.entrySet()) {
            all.add(e.getKey().toStack((int) Math.min(e.getValue(), Integer.MAX_VALUE)));
        }
        return all;
    }

    static int insertFluid(GolemNetwork net, FluidStack input, boolean simulate, @Nullable BlockPos skipTarget) {
        // Liquid Cryo-Source is never stored: it only exists while automation pumps it out of the network
        if (input.isEmpty() || CryoSource.is(input)) {
            return 0;
        }
        int left = input.getAmount();
        if (simulate) {
            long free = 0;
            for (ItemStack disk : allDisks(net, true)) {
                free += Math.max(0, DiskStorage.capacity(disk) - DiskStorage.storedItems(disk));
            }
            left = (int) Math.max(0, input.getAmount() - free);
            if (left <= 0) {
                return input.getAmount();
            }
        }
        Set<CopperDriveBayBlockEntity> touched = null;
        Set<ItemStack> copies = simulate ? Set.of() : copiedDisks(net);
        for (Tier tier : tiers(net, true)) {
            if (left <= 0) {
                break;
            }
            if (!simulate) {
                List<DiskRef> disks = disksOf(tier, true, copies);
                for (int pass = 0; pass < 2 && left > 0; pass++) {
                    for (DiskRef d : disks) {
                        if (left <= 0) {
                            break;
                        }
                        if (pass == 0 && !DiskStorage.containsFluid(d.disk, input)) {
                            continue;
                        }
                        int n = DiskStorage.insertFluid(d.disk, input.copyWithAmount(left), false);
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
                ResourceHandler<FluidResource> h = fluidHandler(ex);
                if (h == null || !mayInsert(ex, input)) {
                    continue;
                }
                left -= Transfers.insertFluid(h, input.copyWithAmount(left), simulate);
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
        int left = amount;
        Set<CopperDriveBayBlockEntity> touched = null;
        Set<ItemStack> copies = copiedDisks(net);
        for (Tier tier : tiers(net, false)) {
            if (left <= 0) {
                break;
            }
            for (DiskRef d : disksOf(tier, true, copies)) {
                if (left <= 0) {
                    break;
                }
                int n = DiskStorage.extractFluid(d.disk, template, left, simulate);
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
                ResourceHandler<FluidResource> h = fluidHandler(ex);
                if (h == null) {
                    continue;
                }
                FluidStack got = Transfers.extractFluid(h, template.copyWithAmount(left), simulate);
                left -= got.getAmount();
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
        long n = 0;
        for (ItemStack disk : allDisks(net, true)) {
            n += DiskStorage.fluidCount(disk, fluid);
        }
        if (net.externals.isEmpty()) {
            return n;
        }
        FluidResource want = FluidResource.of(fluid);
        for (GolemNetwork.External ex : net.externals) {
            if (!mayExtract(ex, fluid)) {
                continue;
            }
            ResourceHandler<FluidResource> h = fluidHandler(ex);
            if (h == null) {
                continue;
            }
            for (int i = 0; i < h.size(); i++) {
                int amt = h.getAmountAsInt(i);
                if (amt > 0 && h.getResource(i).equals(want)) {
                    n += amt;
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
        long total = 0;
        for (SourceHelper.Supplier s : net.sources) {
            total += s.available();
        }
        return total;
    }

    static long cryoSourceAvailable(GolemNetwork net) {
        if (!net.isOnline()) {
            return 0;
        }
        long per = GolemConfig.CRYO_SOURCE_COST.get();
        if (per <= 0 || !SourceHelper.sourceRequired() || net.hasInfiniteSource()) {
            return Integer.MAX_VALUE;
        }
        return availableSource(net) / per;
    }

    /** Makes Liquid Cryo-Source from the network's Source (all of the amount that the Source covers). */
    private static FluidStack extractCryoSource(GolemNetwork net, int amount, boolean simulate) {
        int n = (int) Math.min(amount, cryoSourceAvailable(net));
        long per = GolemConfig.CRYO_SOURCE_COST.get();
        if (per > 0 && SourceHelper.sourceRequired() && !net.hasInfiniteSource()) {
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
        return amount <= 0 || !SourceHelper.sourceRequired() || net.hasInfiniteSource()
                || availableSource(net) >= amount;
    }

    static boolean consumeSource(GolemNetwork net, int amount) {
        if (amount <= 0 || !SourceHelper.sourceRequired() || net.hasInfiniteSource()) {
            return true;
        }
        if (availableSource(net) < amount) {
            return false;
        }
        int left = amount;
        for (SourceHelper.Supplier s : net.sources) {
            left -= s.drain(left);
            if (left <= 0) {
                break;
            }
        }
        return true;
    }
}
