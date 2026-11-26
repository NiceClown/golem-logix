package com.golemlogix.system;

import com.golemlogix.init.ModDataComponents;
import com.golemlogix.items.MagneticDiskItem;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Operations with magnetic disks. The contents are stored on the server in DiskStore by the disk UUID;
 * the disk itself only carries the UUID and the STORED counter (for the tooltip and the fill bar).
 * A disk stores a plain amount of pieces, any number of types; equipment takes many pieces each (see Gear).
 */
public final class DiskStorage {
    /** Bumped whenever any disk's item map or storedItems changes (server). */
    private static long itemMutationEpoch;
    /** Bumped whenever any disk's fluid list or storedFluids changes (server). */
    private static long fluidMutationEpoch;
    /** Bumped whenever a brand-new UUID or DiskStore.Disk entry is allocated (server). */
    private static long diskAllocEpoch;

    private DiskStorage() {
    }

    static long itemMutationEpoch() {
        return itemMutationEpoch;
    }

    static long fluidMutationEpoch() {
        return fluidMutationEpoch;
    }

    static long diskAllocEpoch() {
        return diskAllocEpoch;
    }

    static void markItemsMutated() {
        itemMutationEpoch++;
    }

    static void markDiskAllocated() {
        diskAllocEpoch++;
    }

    public static boolean isItemDisk(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof MagneticDiskItem d && !d.isFluid();
    }

    public static boolean isFluidDisk(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof MagneticDiskItem d && d.isFluid();
    }

    public static boolean isDisk(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof MagneticDiskItem;
    }

    public static int capacity(ItemStack disk) {
        return disk.getItem() instanceof MagneticDiskItem d ? d.getCapacity() : 0;
    }

    /** The disk store; server thread only (in single player the client thread must not read it bypassing the network). */
    @Nullable
    static DiskStore store() {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        return server == null || !server.isSameThread() ? null : DiskStore.get(server);
    }

    /** The disk's id in the store (null before its first write). */
    @Nullable
    public static UUID diskId(ItemStack disk) {
        return id(disk);
    }

    @Nullable
    private static UUID id(ItemStack disk) {
        return disk.get(ModDataComponents.DISK_ID.get());
    }

    /** Gives the disk a UUID if it has none yet (on the first write). */
    private static UUID ensureId(ItemStack disk) {
        UUID id = id(disk);
        if (id == null) {
            id = UUID.randomUUID();
            disk.set(ModDataComponents.DISK_ID.get(), id);
            diskAllocEpoch++;
        }
        return id;
    }

    @Nullable
    static DiskStore.Disk read(@Nullable DiskStore st, ItemStack disk) {
        if (st == null) {
            return null;
        }
        UUID id = id(disk);
        return id == null ? null : st.getOrNull(id);
    }

    @Nullable
    private static DiskStore.Disk read(ItemStack disk) {
        // Server thread only: client renderers in singleplayer must never read server maps while they mutate
        return read(store(), disk);
    }

    private static void markStored(ItemStack disk, DiskStore.Disk data, long total) {
        if (total <= 0 && data.isEmpty()) {
            disk.remove(ModDataComponents.STORED.get());
        } else {
            disk.set(ModDataComponents.STORED.get(), (int) Math.min(total, Integer.MAX_VALUE));
        }
    }

    // ---------------------------------------------------------------- items

    /** Sets the disk item's STORED counter from the store (server): old disks were counted by an older rule. */
    public static boolean syncStored(ItemStack disk) {
        if (!isDisk(disk)) {
            return false;
        }
        DiskStore st = store();
        if (st == null) {
            return false;
        }
        DiskStore.Disk d = read(st, disk);
        long stored = d == null ? 0L : (isFluidDisk(disk) ? d.storedFluids : d.storedItems);
        Integer expected = (d == null || (stored <= 0 && d.isEmpty())) ? null : (int) Math.min(stored, Integer.MAX_VALUE);
        Integer shown = disk.get(ModDataComponents.STORED.get());
        if (!Objects.equals(shown, expected)) {
            if (expected == null) {
                disk.remove(ModDataComponents.STORED.get());
            } else {
                disk.set(ModDataComponents.STORED.get(), expected);
            }
            return true;
        }
        return false;
    }

    /** What is on an item disk (read only). */
    public static Map<ItemKey, Integer> items(ItemStack disk) {
        if (!isItemDisk(disk)) {
            return Collections.emptyMap();
        }
        DiskStore.Disk d = read(disk);
        return d == null ? Collections.emptyMap() : Collections.unmodifiableMap(d.items);
    }

    /** Count of one item key on an item disk without wrapper allocations. */
    public static int itemCount(ItemStack disk, ItemKey key) {
        if (!isItemDisk(disk)) {
            return 0;
        }
        DiskStore.Disk d = read(disk);
        return d == null ? 0 : d.items.getOrDefault(key, 0);
    }

    /** How many units are written to the disk (items or mB). */
    public static long storedItems(ItemStack disk) {
        DiskStore.Disk d = read(disk);
        if (d == null) {
            return 0;
        }
        return isFluidDisk(disk) ? d.storedFluids : d.storedItems;
    }

    public static boolean containsItem(ItemStack disk, ItemStack stack) {
        return containsItem(disk, ItemKey.of(stack));
    }

    static boolean containsItem(ItemStack disk, ItemKey key) {
        DiskStore.Disk d = read(disk);
        return d != null && isItemDisk(disk) && d.items.containsKey(key);
    }

    /** Puts items on the disk. Returns how many were actually put (with simulate the disk does not change). */
    public static int insertItem(ItemStack disk, ItemStack stack, boolean simulate) {
        if (!isItemDisk(disk) || stack.isEmpty()) {
            return 0;
        }
        DiskStore st = store();
        if (st == null) {
            return 0;
        }
        DiskStore.Disk d = read(st, disk);
        int cap = capacity(disk);
        int cost = Gear.cost(stack);
        ItemKey key = simulate ? null : ItemKey.of(stack);
        return insertItemDirect(st, disk, d, cap, key, cost, stack.getCount(), simulate);
    }

    /** Hot-path item insert for NetworkStorage: avoids redundant store/capacity/Gear/ItemKey lookups across disks. */
    static int insertItemDirect(@Nullable DiskStore st, ItemStack disk, @Nullable DiskStore.Disk d, int cap,
                                @Nullable ItemKey key, int cost, int count, boolean simulate) {
        if (st == null || count <= 0) {
            return 0;
        }
        long stored = d == null ? 0 : d.storedItems;
        int n = (int) Math.min((cap - stored) / cost, count);
        // A disk too small for even one by the sum, or partly filled, still takes one piece of equipment if it has none
        if (n <= 0 && cost > 1 && cap <= Gear.COST && !holdsGear(d)) {
            n = 1;
        }
        if (n <= 0 || simulate) {
            return Math.max(n, 0);
        }
        if (d == null) {
            d = st.getOrCreate(ensureId(disk));
        }
        d.items.merge(key, n, Integer::sum);
        d.storedItems += (long) n * cost;
        if (cost > 1) {
            d.gearItems += n;
        }
        markStored(disk, d, d.storedItems);
        st.setDirty();
        itemMutationEpoch++;
        return n;
    }

    private static boolean holdsGear(@Nullable DiskStore.Disk d) {
        return d != null && d.gearItems > 0;
    }

    /** Takes items from the disk. Returns how many were actually taken. */
    public static int extractItem(ItemStack disk, ItemStack template, int max, boolean simulate) {
        if (!isItemDisk(disk) || template.isEmpty() || max <= 0) {
            return 0;
        }
        DiskStore st = store();
        if (st == null) {
            return 0;
        }
        return extractItemDirect(st, disk, read(st, disk), ItemKey.of(template), Gear.cost(template), max, simulate);
    }

    /** Hot-path item extract for NetworkStorage with pre-resolved DiskStore/Disk, ItemKey, and gear cost. */
    static int extractItemDirect(@Nullable DiskStore st, ItemStack disk, @Nullable DiskStore.Disk d,
                                 ItemKey key, int cost, int max, boolean simulate) {
        if (st == null || d == null || max <= 0) {
            return 0;
        }
        Integer have = d.items.get(key);
        if (have == null) {
            return 0;
        }
        int take = Math.min(max, have);
        if (!simulate) {
            if (take >= have) {
                d.items.remove(key);
            } else {
                d.items.put(key, have - take);
            }
            d.storedItems = Math.max(0, d.storedItems - (long) take * cost);
            if (cost > 1) {
                d.gearItems = Math.max(0, d.gearItems - take);
            }
            markStored(disk, d, d.storedItems);
            st.setDirty();
            itemMutationEpoch++;
        }
        return take;
    }

    // --------------------------------------------------------------- fluids

    public static List<FluidStack> fluids(ItemStack disk) {
        if (!isFluidDisk(disk)) {
            return List.of();
        }
        DiskStore.Disk d = read(disk);
        return d == null ? List.of() : Collections.unmodifiableList(d.fluids);
    }

    /** Amount (mB) of one matching fluid on a fluid disk. */
    public static long fluidCount(ItemStack disk, FluidStack fluid) {
        if (!isFluidDisk(disk) || fluid.isEmpty()) {
            return 0L;
        }
        return fluidCountDirect(read(disk), fluid);
    }

    static long fluidCountDirect(@Nullable DiskStore.Disk d, FluidStack fluid) {
        if (d == null) {
            return 0L;
        }
        long n = 0L;
        for (FluidStack f : d.fluids) {
            if (FluidStack.isSameFluidSameComponents(f, fluid)) {
                n += f.getAmount();
            }
        }
        return n;
    }

    public static boolean containsFluid(ItemStack disk, FluidStack fluid) {
        if (!isFluidDisk(disk) || fluid.isEmpty()) {
            return false;
        }
        return containsFluidDirect(read(disk), fluid);
    }

    static boolean containsFluidDirect(@Nullable DiskStore.Disk d, FluidStack fluid) {
        if (d == null) {
            return false;
        }
        for (FluidStack f : d.fluids) {
            if (FluidStack.isSameFluidSameComponents(f, fluid)) {
                return true;
            }
        }
        return false;
    }

    public static int insertFluid(ItemStack disk, FluidStack fluid, boolean simulate) {
        if (!isFluidDisk(disk) || fluid.isEmpty()) {
            return 0;
        }
        DiskStore st = store();
        if (st == null) {
            return 0;
        }
        return insertFluidDirect(st, disk, read(st, disk), capacity(disk), fluid, fluid.getAmount(), simulate);
    }

    static int insertFluidDirect(@Nullable DiskStore st, ItemStack disk, @Nullable DiskStore.Disk d, int cap,
                                 FluidStack fluid, int amount, boolean simulate) {
        if (st == null || amount <= 0) {
            return 0;
        }
        long stored = d == null ? 0 : d.storedFluids;
        int n = (int) Math.min(cap - stored, amount);
        if (n <= 0 || simulate) {
            return Math.max(n, 0);
        }
        if (d == null) {
            d = st.getOrCreate(ensureId(disk));
        }
        boolean found = false;
        for (int i = 0; i < d.fluids.size(); i++) {
            FluidStack f = d.fluids.get(i);
            if (FluidStack.isSameFluidSameComponents(f, fluid)) {
                d.fluids.set(i, f.copyWithAmount(f.getAmount() + n));
                found = true;
                break;
            }
        }
        if (!found) {
            d.fluids.add(fluid.copyWithAmount(n));
        }
        d.storedFluids += n;
        markStored(disk, d, d.storedFluids);
        st.setDirty();
        fluidMutationEpoch++;
        return n;
    }

    public static int extractFluid(ItemStack disk, FluidStack template, int max, boolean simulate) {
        if (!isFluidDisk(disk) || template.isEmpty() || max <= 0) {
            return 0;
        }
        DiskStore st = store();
        if (st == null) {
            return 0;
        }
        return extractFluidDirect(st, disk, read(st, disk), template, max, simulate);
    }

    static int extractFluidDirect(@Nullable DiskStore st, ItemStack disk, @Nullable DiskStore.Disk d,
                                  FluidStack template, int max, boolean simulate) {
        if (st == null || d == null || max <= 0) {
            return 0;
        }
        for (int i = 0; i < d.fluids.size(); i++) {
            FluidStack f = d.fluids.get(i);
            if (FluidStack.isSameFluidSameComponents(f, template)) {
                int take = Math.min(max, f.getAmount());
                if (!simulate) {
                    if (take >= f.getAmount()) {
                        d.fluids.remove(i);
                    } else {
                        d.fluids.set(i, f.copyWithAmount(f.getAmount() - take));
                    }
                    d.storedFluids = Math.max(0, d.storedFluids - take);
                    markStored(disk, d, d.storedFluids);
                    st.setDirty();
                    fluidMutationEpoch++;
                }
                return take;
            }
        }
        return 0;
    }
}
