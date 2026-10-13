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

        public boolean isEmpty() {
            return items.isEmpty() && fluids.isEmpty();
        }
    }

    private final Map<UUID, Disk> disks = new HashMap<>();

    public static DiskStore get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(DiskStore::new, DiskStore::load, null), NAME);
    }

    public Disk getOrNull(UUID id) {
        return disks.get(id);
    }

    public Disk getOrCreate(UUID id) {
        return disks.computeIfAbsent(id, k -> new Disk());
    }

    public void drop(UUID id) {
        disks.remove(id);
    }

    // ------------------------------------------------------------------ NBT

    public static DiskStore load(CompoundTag tag, HolderLookup.Provider provider) {
        DiskStore store = new DiskStore();
        ListTag list = tag.getList("Disks", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag t = list.getCompound(i);
            if (!t.contains("Id")) {
                continue;
            }
            UUID id = NbtUtils.loadUUID(t.get("Id"));
            Disk disk = new Disk();

            ListTag items = t.getList("Items", Tag.TAG_COMPOUND);
            for (int j = 0; j < items.size(); j++) {
                CompoundTag it = items.getCompound(j);
                ItemStack stack = ItemStack.parse(provider, it.getCompound("Item")).orElse(ItemStack.EMPTY);
                int count = it.getInt("Count");
                if (!stack.isEmpty() && count > 0) {
                    disk.items.put(ItemKey.of(stack), count);
                    disk.storedItems += count;
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
                CompoundTag c = new CompoundTag();
                c.put("Item", it.getKey().toStack(1).save(provider));
                c.putInt("Count", it.getValue());
                items.add(c);
            }
            t.put("Items", items);
            ListTag fluids = new ListTag();
            for (FluidStack f : disk.fluids) {
                fluids.add(f.save(provider));
            }
            t.put("Fluids", fluids);
            list.add(t);
        }
        tag.put("Disks", list);
        return tag;
    }
}
