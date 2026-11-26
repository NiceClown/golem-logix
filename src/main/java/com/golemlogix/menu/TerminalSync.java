package com.golemlogix.menu;

import com.golemlogix.crafting.CraftingCpu;
import com.golemlogix.items.PortableChronometerItem;
import com.golemlogix.network.TerminalInfoPayload;
import com.golemlogix.network.TerminalListPayload;
import com.golemlogix.system.GolemNetwork;
import com.golemlogix.system.ItemKey;
import com.golemlogix.system.SourceHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.network.PacketDistributor;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Server-side item/craftable delta tracking and status bar syncing for {@link TerminalMenu}.
 * Sends chunked full lists on initial open and minimal diffs afterwards, avoiding redundant
 * craftable-set allocations on ticks where nothing changed.
 */
final class TerminalSync {
    @Nullable
    private Map<ItemKey, Long> lastItems;
    @Nullable
    private Set<ItemKey> lastCraftable;
    @Nullable
    private TerminalInfoPayload lastInfo;
    private boolean forceSync = true;

    void markDirty() {
        forceSync = true;
    }

    boolean isDue(long tick) {
        return forceSync || tick % 10 == 0;
    }

    boolean consumeForce() {
        boolean force = forceSync;
        forceSync = false;
        return force;
    }

    void sync(ServerPlayer sp, int containerId, GolemNetwork net, @Nullable String err,
              boolean wireless, @Nullable ItemStack chronometer) {
        Map<ItemKey, Long> items = err == null ? net.listItemsShared() : Map.of();
        Set<ItemKey> liveCraftable = err == null ? net.patternIndex().keySet() : Set.of();

        if (lastItems == null) {
            sendList(sp, containerId, items, liveCraftable);
            lastItems = items;
            lastCraftable = liveCraftable.isEmpty() ? Set.of() : Set.copyOf(liveCraftable);
        } else {
            boolean itemsChanged = items != lastItems && !items.equals(lastItems);
            boolean craftableChanged = !liveCraftable.equals(lastCraftable);
            if (itemsChanged || craftableChanged) {
                sendChanges(sp, containerId, items, liveCraftable, itemsChanged, craftableChanged);
                if (craftableChanged) {
                    lastCraftable = liveCraftable.isEmpty() ? Set.of() : Set.copyOf(liveCraftable);
                }
            }
            lastItems = items;
        }

        TerminalInfoPayload info = buildInfo(containerId, net, err, wireless, chronometer);
        if (!sameInfo(info, lastInfo)) {
            lastInfo = info;
            PacketDistributor.sendToPlayer(sp, info);
        }
    }

    private static TerminalInfoPayload buildInfo(int containerId, GolemNetwork net, @Nullable String err,
                                                 boolean wireless, @Nullable ItemStack chronometer) {
        int busy = 0;
        int total = 0;
        if (err == null) {
            for (CraftingCpu cpu : net.cpus()) {
                if (!cpu.isRemoved()) {
                    total++;
                    if (!cpu.isIdle()) {
                        busy++;
                    }
                }
            }
        }
        int charge = -1;
        boolean resonator = false;
        if (wireless && SourceHelper.sourceRequired()) {
            charge = chronometer == null ? 0 : PortableChronometerItem.getCharge(chronometer);
            resonator = err == null && net.hasResonator();
        }
        return new TerminalInfoPayload(containerId, err == null ? "" : err,
                net.channelsUsed(), net.channelCapacity(),
                err == null ? net.itemsStoredOnDisks() : 0, err == null ? net.itemCapacity() : 0,
                err == null ? net.fluidStoredOnDisks() : 0, err == null ? net.fluidCapacity() : 0,
                busy, total, err == null ? net.listFluids() : List.of(), charge, resonator);
    }

    private static boolean sameInfo(TerminalInfoPayload a, @Nullable TerminalInfoPayload b) {
        if (b == null) {
            return false;
        }
        if (!a.error().equals(b.error()) || a.channelsUsed() != b.channelsUsed() || a.channelCapacity() != b.channelCapacity()
                || a.itemsUsed() != b.itemsUsed() || a.itemsCapacity() != b.itemsCapacity()
                || a.fluidsUsed() != b.fluidsUsed() || a.fluidsCapacity() != b.fluidsCapacity()
                || a.cpuBusy() != b.cpuBusy() || a.cpuTotal() != b.cpuTotal() || a.fluids().size() != b.fluids().size()
                || a.charge() != b.charge() || a.resonator() != b.resonator()) {
            return false;
        }
        for (int i = 0; i < a.fluids().size(); i++) {
            if (!FluidStack.matches(a.fluids().get(i), b.fluids().get(i))) {
                return false;
            }
        }
        return true;
    }

    /** Sends only what changed since the previous snapshot: an entry with count 0 and !craftable is removed on the client. */
    private void sendChanges(ServerPlayer sp, int containerId, Map<ItemKey, Long> items, Set<ItemKey> craftable,
                             boolean itemsChanged, boolean craftableChanged) {
        List<TerminalListPayload.Entry> changes = new ArrayList<>();
        if (!itemsChanged) {
            // Only the craftable set changed: scan craftable/lastCraftable directly instead of every stored item
            for (ItemKey k : craftable) {
                if (!lastCraftable.contains(k)) {
                    changes.add(new TerminalListPayload.Entry(k.stack(), items.getOrDefault(k, 0L), true));
                }
            }
            for (ItemKey k : lastCraftable) {
                if (!craftable.contains(k)) {
                    changes.add(new TerminalListPayload.Entry(k.stack(), items.getOrDefault(k, 0L), false));
                }
            }
        } else if (!craftableChanged) {
            // Only stored counts changed: skip craftable lookups for unchanged entries and skip craftable-only loops
            boolean anyCraftable = !craftable.isEmpty();
            for (Map.Entry<ItemKey, Long> e : items.entrySet()) {
                ItemKey k = e.getKey();
                if (!e.getValue().equals(lastItems.get(k))) {
                    changes.add(new TerminalListPayload.Entry(k.stack(), e.getValue(), anyCraftable && craftable.contains(k)));
                }
            }
            for (ItemKey k : lastItems.keySet()) {
                if (!items.containsKey(k)) {
                    changes.add(new TerminalListPayload.Entry(k.stack(), 0, anyCraftable && craftable.contains(k)));
                }
            }
        } else {
            for (Map.Entry<ItemKey, Long> e : items.entrySet()) {
                ItemKey k = e.getKey();
                boolean can = craftable.contains(k);
                if (!e.getValue().equals(lastItems.get(k)) || can != lastCraftable.contains(k)) {
                    changes.add(new TerminalListPayload.Entry(k.stack(), e.getValue(), can));
                }
            }
            for (ItemKey k : lastItems.keySet()) {
                if (!items.containsKey(k)) {
                    changes.add(new TerminalListPayload.Entry(k.stack(), 0, craftable.contains(k)));
                }
            }
            for (ItemKey k : craftable) {
                if (!items.containsKey(k) && !lastItems.containsKey(k) && !lastCraftable.contains(k)) {
                    changes.add(new TerminalListPayload.Entry(k.stack(), 0, true));
                }
            }
            for (ItemKey k : lastCraftable) {
                if (!items.containsKey(k) && !lastItems.containsKey(k) && !craftable.contains(k)) {
                    changes.add(new TerminalListPayload.Entry(k.stack(), 0, false));
                }
            }
        }
        for (int i = 0; i < changes.size(); i += TerminalListPayload.CHUNK) {
            int end = Math.min(changes.size(), i + TerminalListPayload.CHUNK);
            PacketDistributor.sendToPlayer(sp, new TerminalListPayload(containerId, false, new ArrayList<>(changes.subList(i, end))));
        }
    }

    private static void sendList(ServerPlayer sp, int containerId, Map<ItemKey, Long> items, Set<ItemKey> craftable) {
        List<TerminalListPayload.Entry> all = new ArrayList<>(items.size() + craftable.size());
        for (Map.Entry<ItemKey, Long> e : items.entrySet()) {
            all.add(new TerminalListPayload.Entry(e.getKey().stack(), e.getValue(), craftable.contains(e.getKey())));
        }
        for (ItemKey k : craftable) {
            if (!items.containsKey(k)) {
                all.add(new TerminalListPayload.Entry(k.stack(), 0, true));
            }
        }
        boolean first = true;
        int i = 0;
        do {
            int end = Math.min(all.size(), i + TerminalListPayload.CHUNK);
            PacketDistributor.sendToPlayer(sp, new TerminalListPayload(containerId, first, new ArrayList<>(all.subList(i, end))));
            first = false;
            i = end;
        } while (i < all.size());
    }
}
