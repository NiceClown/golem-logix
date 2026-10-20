package com.golemlogix.system;

import com.golemlogix.GolemLogixMod;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
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

    /** Saved as one compound (the format of 1.21.1, now in data/golem_logix/disks.dat) through the server's registries. */
    private static final SavedDataType<DiskStore> TYPE = new SavedDataType<>(Identifier.fromNamespaceAndPath(GolemLogixMod.MOD_ID, "disks"),
            level -> fromLegacyFile(), level -> {
                HolderLookup.Provider registries = registries();
                return CompoundTag.CODEC.xmap(tag -> load(tag, registries), store -> store.save(new CompoundTag(), registries));
            });

    public static DiskStore get(MinecraftServer server) {
        return server.getDataStorage().computeIfAbsent(TYPE);
    }

    private static HolderLookup.Provider registries() {
        return net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer().registryAccess();
    }

    /** A world from 1.21.1 kept its disks in data/golem_logix_disks.dat: they move to the new file on the next save. */
    private static DiskStore fromLegacyFile() {
        MinecraftServer server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            java.nio.file.Path old = server.getWorldPath(net.minecraft.world.level.storage.LevelResource.DATA).resolve(NAME + ".dat");
            if (java.nio.file.Files.isRegularFile(old)) {
                try {
                    CompoundTag root = net.minecraft.nbt.NbtIo.readCompressed(old, net.minecraft.nbt.NbtAccounter.unlimitedHeap());
                    DiskStore store = load(root.getCompoundOrEmpty("data"), server.registryAccess());
                    store.setDirty();
                    GolemLogixMod.LOGGER.info("Golem Logix: moved {} disks from {}", store.disks.size(), old.getFileName());
                    return store;
                } catch (java.io.IOException | RuntimeException e) {
                    GolemLogixMod.LOGGER.warn("Golem Logix: could not read the old disk file {}", old, e);
                }
            }
        }
        return new DiskStore();
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
        ListTag list = tag.getListOrEmpty("Disks");
        for (int i = 0; i < list.size(); i++) {
            CompoundTag t = list.getCompoundOrEmpty(i);
            UUID id = t.read("Id", UUIDUtil.CODEC).orElse(null);
            if (id == null) {
                continue;
            }
            Disk disk = new Disk();

            ListTag items = t.getListOrEmpty("Items");
            for (int j = 0; j < items.size(); j++) {
                CompoundTag it = items.getCompoundOrEmpty(j);
                ItemStack stack = com.golemlogix.system.NbtBridge.loadItem(provider, it.getCompoundOrEmpty("Item")).orElse(ItemStack.EMPTY);
                int count = it.getIntOr("Count", 0);
                if (!stack.isEmpty() && count > 0) {
                    disk.items.put(ItemKey.of(stack), count);
                    disk.storedItems += count;
                }
            }
            ListTag fluids = t.getListOrEmpty("Fluids");
            for (int j = 0; j < fluids.size(); j++) {
                com.golemlogix.system.NbtBridge.loadFluid(provider, fluids.getCompoundOrEmpty(j)).ifPresent(f -> {
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

    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        ListTag list = new ListTag();
        for (Map.Entry<UUID, Disk> e : disks.entrySet()) {
            Disk disk = e.getValue();
            if (disk.isEmpty()) {
                continue;
            }
            CompoundTag t = new CompoundTag();
            t.store("Id", UUIDUtil.CODEC, e.getKey());
            ListTag items = new ListTag();
            for (Map.Entry<ItemKey, Integer> it : disk.items.entrySet()) {
                CompoundTag c = new CompoundTag();
                c.put("Item", com.golemlogix.system.NbtBridge.saveItem(it.getKey().toStack(1), provider));
                c.putInt("Count", it.getValue());
                items.add(c);
            }
            t.put("Items", items);
            ListTag fluids = new ListTag();
            for (FluidStack f : disk.fluids) {
                fluids.add(com.golemlogix.system.NbtBridge.saveFluid(f, provider));
            }
            t.put("Fluids", fluids);
            list.add(t);
        }
        tag.put("Disks", list);
        return tag;
    }
}
