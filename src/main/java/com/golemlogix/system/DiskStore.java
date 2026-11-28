package com.golemlogix.system;

import com.golemlogix.GolemLogixMod;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.neoforge.fluids.FluidStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Server-side store of magnetic disk contents. The disk itself only carries a UUID and a counter,
 * while the items and fluids live here (saved with the world). This way the contents do not travel over the network,
 * are not lost by client manipulations (the creative inventory) and do not bloat stacks.
 */
public class DiskStore extends SavedData {
    public static final String NAME = GolemLogixMod.MOD_ID + "_disks";

    public static final class Disk {
        public final LinkedHashMap<ItemKey, Integer> items = new LinkedHashMap<>();
        public final List<FluidStack> fluids = new ArrayList<>();
        public long storedItems;
        public long storedFluids;
        /** Distinct equipment pieces on this disk: lets small disks check for equipment in O(1). */
        public int gearItems;

        public boolean isEmpty() {
            return items.isEmpty() && fluids.isEmpty();
        }
    }

    private record ServerCache(MinecraftServer server, DiskStore store) {
    }

    private final Map<UUID, Disk> disks = new HashMap<>();
    private static volatile ServerCache serverCache;

    public static DiskStore get(MinecraftServer server) {
        ServerCache cached = serverCache;
        if (cached != null && cached.server() == server) {
            return cached.store();
        }
        DiskStore store = server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(DiskStore::new, DiskStore::load, null), NAME);
        serverCache = new ServerCache(server, store);
        return store;
    }

    static void forgetCachedServer() {
        serverCache = null;
    }

    public Disk getOrNull(UUID id) {
        return disks.get(id);
    }

    public Disk getOrCreate(UUID id) {
        Disk d = disks.get(id);
        if (d == null) {
            d = new Disk();
            disks.put(id, d);
            DiskStorage.markDiskAllocated();
        }
        return d;
    }

    /** Recomputes used space and equipment counts after a /reload, in case the golem_logix:not_gear tag changed. */
    public void recount() {
        for (Disk disk : disks.values()) {
            long sum = 0;
            int gear = 0;
            for (Map.Entry<ItemKey, Integer> e : disk.items.entrySet()) {
                int c = e.getValue();
                int cost = Gear.cost(e.getKey().stack());
                sum += (long) c * cost;
                if (cost > 1) {
                    gear += c;
                }
            }
            disk.storedItems = sum;
            disk.gearItems = gear;
        }
        DiskStorage.markItemsMutated();
    }

    // ------------------------------------------------------------------ NBT

    public static DiskStore load(CompoundTag tag, HolderLookup.Provider provider) {
        DiskStore store = new DiskStore();
        ListTag list = tag.getList("Disks", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag t = list.getCompound(i);
            if (!t.hasUUID("Id")) {
                continue;
            }
            UUID id = t.getUUID("Id");
            Disk disk = new Disk();

            ListTag items = t.getList("Items", Tag.TAG_COMPOUND);
            for (int j = 0; j < items.size(); j++) {
                CompoundTag it = items.getCompound(j);
                ItemStack stack = ItemStack.parse(provider, it.getCompound("Item")).orElse(ItemStack.EMPTY);
                int count = it.getInt("Count");
                if (!stack.isEmpty() && count > 0) {
                    disk.items.merge(ItemKey.of(stack), count, Integer::sum);
                    int cost = Gear.cost(stack);
                    disk.storedItems += (long) count * cost;
                    if (cost > 1) {
                        disk.gearItems += count;
                    }
                }
            }
            ListTag fluids = t.getList("Fluids", Tag.TAG_COMPOUND);
            for (int j = 0; j < fluids.size(); j++) {
                FluidStack.parse(provider, fluids.getCompound(j)).ifPresent(f -> {
                    if (!f.isEmpty()) {
                        disk.fluids.add(f);
                        disk.storedFluids += f.getAmount();
                    }
                });
            }
            if (!disk.isEmpty()) {
                store.disks.put(id, disk);
            }
        }
        return store;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        ListTag list = new ListTag();
        for (Map.Entry<UUID, Disk> e : disks.entrySet()) {
            Disk disk = e.getValue();
            if (disk.isEmpty()) {
                continue;
            }
            CompoundTag t = new CompoundTag();
            t.put("Id", NbtUtils.createUUID(e.getKey()));
            ListTag items = new ListTag();
            for (Map.Entry<ItemKey, Integer> it : disk.items.entrySet()) {
                try {
                    ItemStack one = it.getKey().toStack(1);
                    if (one.isEmpty()) {
                        continue;
                    }
                    Tag encoded = one.save(provider);
                    CompoundTag c = new CompoundTag();
                    c.put("Item", encoded);
                    c.putInt("Count", it.getValue());
                    items.add(c);
                } catch (Exception ex) {
                    GolemLogixMod.LOGGER.error("Failed to save item {} (x{}) on disk {}; skipping entry",
                            it.getKey().stack().getItem(), it.getValue(), e.getKey(), ex);
                }
            }
            t.put("Items", items);
            ListTag fluids = new ListTag();
            for (FluidStack f : disk.fluids) {
                try {
                    if (f.isEmpty()) {
                        continue;
                    }
                    fluids.add(f.save(provider));
                } catch (Exception ex) {
                    GolemLogixMod.LOGGER.error("Failed to save fluid {} ({} mB) on disk {}; skipping entry",
                            f.getFluid(), f.getAmount(), e.getKey(), ex);
                }
            }
            t.put("Fluids", fluids);
            if (!items.isEmpty() || !fluids.isEmpty()) {
                list.add(t);
            }
        }
        tag.put("Disks", list);
        return tag;
    }
}
