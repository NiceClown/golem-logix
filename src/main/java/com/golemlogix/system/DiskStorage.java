package com.golemlogix.system;

import com.golemlogix.init.ModDataComponents;
import com.golemlogix.items.MagneticDiskItem;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Operations with magnetic disks. The contents are stored on the server in DiskStore by the disk UUID;
 * the disk itself only carries the UUID and the STORED counter (for the tooltip and the fill bar).
 * A disk stores a plain amount, any number of types.
 */
public final class DiskStorage {
    private DiskStorage() {
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
    private static DiskStore store() {
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
        }
        return id;
    }

    @Nullable
    private static DiskStore.Disk read(ItemStack disk) {
        UUID id = id(disk);
        if (id == null) {
            return null;
        }
        // server thread only: the client (renderers in single player) must never read the server's maps while it writes them,
        // it gets samples of the network's contents from the Atlas Core instead (AtlasCoreBlockEntity#cargoItems)
        DiskStore st = store();
        return st == null ? null : st.getOrNull(id);
    }

    private static void markStored(ItemStack disk, DiskStore.Disk data, long total) {
        if (total <= 0 && data.isEmpty()) {
            disk.remove(ModDataComponents.STORED.get());
        } else {
            disk.set(ModDataComponents.STORED.get(), (int) Math.min(total, Integer.MAX_VALUE));
        }
    }

    // ---------------------------------------------------------------- items

    /** What is on an item disk (read only). */
    public static Map<ItemKey, Integer> items(ItemStack disk) {
        if (!isItemDisk(disk)) {
            return Collections.emptyMap();
        }
        DiskStore.Disk d = read(disk);
        return d == null ? Collections.emptyMap() : Collections.unmodifiableMap(d.items);
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
        DiskStore.Disk d = read(disk);
        return d != null && isItemDisk(disk) && d.items.containsKey(ItemKey.of(stack));
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
        DiskStore.Disk d = simulate ? read(disk) : st.getOrCreate(ensureId(disk));
        long stored = d == null ? 0 : d.storedItems;
        int n = (int) Math.min(capacity(disk) - stored, stack.getCount());
        if (n <= 0 || simulate) {
            return Math.max(n, 0);
        }
        d.items.merge(ItemKey.of(stack), n, Integer::sum);
        d.storedItems += n;
        markStored(disk, d, d.storedItems);
        st.setDirty();
        return n;
    }

    /** Takes items from the disk. Returns how many were actually taken. */
    public static int extractItem(ItemStack disk, ItemStack template, int max, boolean simulate) {
        if (!isItemDisk(disk) || template.isEmpty() || max <= 0) {
            return 0;
        }
        DiskStore st = store();
        DiskStore.Disk d = read(disk);
        if (st == null || d == null) {
            return 0;
        }
        ItemKey key = ItemKey.of(template);
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
            d.storedItems -= take;
            markStored(disk, d, d.storedItems);
            st.setDirty();
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

    public static boolean containsFluid(ItemStack disk, FluidStack fluid) {
        for (FluidStack f : fluids(disk)) {
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
        DiskStore.Disk d = simulate ? read(disk) : st.getOrCreate(ensureId(disk));
        long stored = d == null ? 0 : d.storedFluids;
        int n = (int) Math.min(capacity(disk) - stored, fluid.getAmount());
        if (n <= 0 || simulate) {
            return Math.max(n, 0);
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
        return n;
    }

    public static int extractFluid(ItemStack disk, FluidStack template, int max, boolean simulate) {
        if (!isFluidDisk(disk) || template.isEmpty() || max <= 0) {
            return 0;
        }
        DiskStore st = store();
        DiskStore.Disk d = read(disk);
        if (st == null || d == null) {
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
                    d.storedFluids -= take;
                    markStored(disk, d, d.storedFluids);
                    st.setDirty();
                }
                return take;
            }
        }
        return 0;
    }

    /** A copy of the disk's fluid list (for safe iteration). */
    public static List<FluidStack> fluidsCopy(ItemStack disk) {
        return new ArrayList<>(fluids(disk));
    }
}
