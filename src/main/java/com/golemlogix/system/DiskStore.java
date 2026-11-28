package com.golemlogix.system;

import com.golemlogix.GolemLogixMod;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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

    private record ServerCache(MinecraftServer server, DiskStore store) {}

    private final Map<UUID, Disk> disks = new HashMap<>();
    private static volatile ServerCache serverCache;

    /** Saved as one compound (the format of 1.21.1, now in data/golem_logix/disks.dat) through the server's registries. */
    private static final SavedDataType<DiskStore> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath(GolemLogixMod.MOD_ID, "disks"),
            level -> fromLegacyFile(),
            level -> {
                HolderLookup.Provider registries = registries();
                return CompoundTag.CODEC.xmap(tag -> load(tag, registries), store -> store.save(new CompoundTag(), registries));
            });

    public static DiskStore get(MinecraftServer server) {
        ServerCache cached = serverCache;
        if (cached != null && cached.server() == server) {
            return cached.store();
        }
        DiskStore store = server.getDataStorage().computeIfAbsent(TYPE);
        serverCache = new ServerCache(server, store);
        return store;
    }

    static void forgetCachedServer() {
        serverCache = null;
    }

    private static HolderLookup.Provider registries() {
        return ServerLifecycleHooks.getCurrentServer().registryAccess();
    }

    /** A world from 1.21.1 kept its disks in data/golem_logix_disks.dat: they move to the new file on the next save. */
    private static DiskStore fromLegacyFile() {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            Path old = server.getWorldPath(LevelResource.DATA).resolve(NAME + ".dat");
            if (Files.isRegularFile(old)) {
                try {
                    CompoundTag root = NbtIo.readCompressed(old, NbtAccounter.unlimitedHeap());
                    DiskStore store = load(root.getCompoundOrEmpty("data"), server.registryAccess());
                    store.setDirty();
                    GolemLogixMod.LOGGER.info("Golem Logix: moved {} disks from {}", store.disks.size(), old.getFileName());
                    return store;
                } catch (IOException | RuntimeException e) {
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
                ItemStack stack = NbtBridge.loadItem(provider, it.getCompoundOrEmpty("Item")).orElse(ItemStack.EMPTY);
                int count = it.getIntOr("Count", 0);
                if (!stack.isEmpty() && count > 0) {
                    disk.items.merge(ItemKey.of(stack), count, Integer::sum);
                    int cost = Gear.cost(stack);
                    disk.storedItems += (long) count * cost;
                    if (cost > 1) {
                        disk.gearItems += count;
                    }
                }
            }
            ListTag fluids = t.getListOrEmpty("Fluids");
            for (int j = 0; j < fluids.size(); j++) {
                NbtBridge.loadFluid(provider, fluids.getCompoundOrEmpty(j)).ifPresent(f -> {
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
        RegistryOps<Tag> ops = provider.createSerializationContext(NbtOps.INSTANCE);
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
                try {
                    ItemStack stack = it.getKey().toStack(1);
                    ItemStack.CODEC.encodeStart(ops, stack)
                            .resultOrPartial(err -> GolemLogixMod.LOGGER.error(
                                    "Golem Logix: failed to save item {} on disk {}: {}", stack.getItem(), e.getKey(), err))
                            .ifPresent(encoded -> {
                                CompoundTag c = new CompoundTag();
                                c.put("Item", encoded);
                                c.putInt("Count", it.getValue());
                                items.add(c);
                            });
                } catch (RuntimeException ex) {
                    GolemLogixMod.LOGGER.error("Golem Logix: failed to save item on disk {}", e.getKey(), ex);
                }
            }
            t.put("Items", items);
            ListTag fluids = new ListTag();
            for (FluidStack f : disk.fluids) {
                try {
                    FluidStack.CODEC.encodeStart(ops, f)
                            .resultOrPartial(err -> GolemLogixMod.LOGGER.error(
                                    "Golem Logix: failed to save fluid {} on disk {}: {}", f.getFluid(), e.getKey(), err))
                            .ifPresent(fluids::add);
                } catch (RuntimeException ex) {
                    GolemLogixMod.LOGGER.error("Golem Logix: failed to save fluid on disk {}", e.getKey(), ex);
                }
            }
            t.put("Fluids", fluids);
            list.add(t);
        }
        tag.put("Disks", list);
        return tag;
    }
}
