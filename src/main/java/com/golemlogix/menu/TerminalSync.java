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
 * Sends chunked full lists on initial open, single-pass diffs afterwards, and compares status-bar
 * fields before allocating or sending a {@link TerminalInfoPayload}.
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

    void clearForce() {
        forceSync = false;
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
            boolean checkItems = items != lastItems;
            boolean craftableChanged = !liveCraftable.equals(lastCraftable);
            if (checkItems || craftableChanged) {
                sendChanges(sp, containerId, items, liveCraftable, checkItems, craftableChanged);
                if (craftableChanged) {
                    lastCraftable = liveCraftable.isEmpty() ? Set.of() : Set.copyOf(liveCraftable);
                }
            }
            lastItems = items;
        }

        syncInfo(sp, containerId, net, err, wireless, chronometer);
    }

    private void syncInfo(ServerPlayer sp, int containerId, GolemNetwork net, @Nullable String err,
                          boolean wireless, @Nullable ItemStack chronometer) {
        int busy = 0;
        int total = 0;
        long itemsUsed = 0;
        long itemsCap = 0;
        long fluidsUsed = 0;
        long fluidsCap = 0;
        List<FluidStack> fluids = List.of();
        if (err == null) {
            for (CraftingCpu cpu : net.cpus()) {
                if (!cpu.isRemoved()) {
                    total++;
                    if (!cpu.isIdle()) {
                        busy++;
                    }
                }
            }
            itemsUsed = net.itemsStoredOnDisks();
            itemsCap = net.itemCapacity();
            fluidsUsed = net.fluidStoredOnDisks();
            fluidsCap = net.fluidCapacity();
            fluids = net.listFluids();
        }
        int charge = -1;
        boolean resonator = false;
        if (wireless && SourceHelper.sourceRequired()) {
            charge = chronometer == null ? 0 : PortableChronometerItem.getCharge(chronometer);
            resonator = err == null && net.hasResonator();
        }
        String errorText = err == null ? "" : err;
        int channelsUsed = net.channelsUsed();
        int channelCapacity = net.channelCapacity();
        if (sameAsLastInfo(errorText, channelsUsed, channelCapacity,
                itemsUsed, itemsCap, fluidsUsed, fluidsCap, busy, total, fluids, charge, resonator)) {
            return;
        }
        TerminalInfoPayload info = new TerminalInfoPayload(containerId, errorText,
                channelsUsed, channelCapacity, itemsUsed, itemsCap, fluidsUsed, fluidsCap,
                busy, total, fluids, charge, resonator);
        lastInfo = info;
        PacketDistributor.sendToPlayer(sp, info);
    }

    private boolean sameAsLastInfo(String error, int channelsUsed, int channelCapacity,
                                   long itemsUsed, long itemsCap, long fluidsUsed, long fluidsCap,
                                   int busy, int total, List<FluidStack> fluids, int charge, boolean resonator) {
        TerminalInfoPayload b = lastInfo;
        if (b == null || !error.equals(b.error()) || channelsUsed != b.channelsUsed() || channelCapacity != b.channelCapacity()
                || itemsUsed != b.itemsUsed() || itemsCap != b.itemsCapacity()
                || fluidsUsed != b.fluidsUsed() || fluidsCap != b.fluidsCapacity()
                || busy != b.cpuBusy() || total != b.cpuTotal()
                || charge != b.charge() || resonator != b.resonator()) {
            return false;
        }
        if (fluids == b.fluids()) {
            return true;
        }
        if (fluids.size() != b.fluids().size()) {
            return false;
        }
        for (int i = 0; i < fluids.size(); i++) {
            if (!FluidStack.matches(fluids.get(i), b.fluids().get(i))) {
                return false;
            }
        }
        return true;
    }

    private static List<TerminalListPayload.Entry> addChange(@Nullable List<TerminalListPayload.Entry> changes,
                                                             ItemStack key, long count, boolean craftable) {
        if (changes == null) {
            changes = new ArrayList<>();
        }
        changes.add(new TerminalListPayload.Entry(key, count, craftable));
        return changes;
    }

    /**
     * Diffs {@code items}/{@code craftable} against the previous snapshot in a single pass and sends only changed entries
     * (an entry with count 0 and !craftable is removed on the client).
     */
    private void sendChanges(ServerPlayer sp, int containerId, Map<ItemKey, Long> items, Set<ItemKey> craftable,
                             boolean checkItems, boolean craftableChanged) {
        List<TerminalListPayload.Entry> changes = null;
        if (!checkItems) {
            // Only the craftable set changed: scan craftable/lastCraftable directly instead of every stored item
            for (ItemKey k : craftable) {
                if (!lastCraftable.contains(k)) {
                    changes = addChange(changes, k.stack(), items.getOrDefault(k, 0L), true);
                }
            }
            for (ItemKey k : lastCraftable) {
                if (!craftable.contains(k)) {
                    changes = addChange(changes, k.stack(), items.getOrDefault(k, 0L), false);
                }
            }
        } else if (!craftableChanged) {
            // Only stored counts may have changed: single pass over items, skipping removal pass if all previous keys were seen
            boolean anyCraftable = !craftable.isEmpty();
            int seen = 0;
            for (Map.Entry<ItemKey, Long> e : items.entrySet()) {
                ItemKey k = e.getKey();
                Long prev = lastItems.get(k);
                if (prev != null) {
                    seen++;
                    if (prev.longValue() == e.getValue().longValue()) {
                        continue;
                    }
                }
                changes = addChange(changes, k.stack(), e.getValue(), anyCraftable && craftable.contains(k));
            }
            if (seen < lastItems.size()) {
                for (ItemKey k : lastItems.keySet()) {
                    if (!items.containsKey(k)) {
                        changes = addChange(changes, k.stack(), 0, anyCraftable && craftable.contains(k));
                    }
                }
            }
        } else {
            int seen = 0;
            for (Map.Entry<ItemKey, Long> e : items.entrySet()) {
                ItemKey k = e.getKey();
                Long prev = lastItems.get(k);
                boolean countDiff;
                if (prev != null) {
                    seen++;
                    countDiff = prev.longValue() != e.getValue().longValue();
                } else {
                    countDiff = true;
                }
                boolean can = craftable.contains(k);
                if (countDiff || can != lastCraftable.contains(k)) {
                    changes = addChange(changes, k.stack(), e.getValue(), can);
                }
            }
            if (seen < lastItems.size()) {
                for (ItemKey k : lastItems.keySet()) {
                    if (!items.containsKey(k)) {
                        changes = addChange(changes, k.stack(), 0, craftable.contains(k));
                    }
                }
            }
            for (ItemKey k : craftable) {
                if (!items.containsKey(k) && !lastItems.containsKey(k) && !lastCraftable.contains(k)) {
                    changes = addChange(changes, k.stack(), 0, true);
                }
            }
            for (ItemKey k : lastCraftable) {
                if (!items.containsKey(k) && !lastItems.containsKey(k) && !craftable.contains(k)) {
                    changes = addChange(changes, k.stack(), 0, false);
                }
            }
        }
        if (changes == null) {
            return;
        }
        for (int i = 0; i < changes.size(); i += TerminalListPayload.CHUNK) {
            int end = Math.min(changes.size(), i + TerminalListPayload.CHUNK);
            List<TerminalListPayload.Entry> chunk = i == 0 && end == changes.size()
                    ? changes : new ArrayList<>(changes.subList(i, end));
            PacketDistributor.sendToPlayer(sp, new TerminalListPayload(containerId, false, chunk));
        }
    }

    private static void sendList(ServerPlayer sp, int containerId, Map<ItemKey, Long> items, Set<ItemKey> craftable) {
        boolean anyCraftable = !craftable.isEmpty();
        int totalHint = items.size() + craftable.size();
        List<TerminalListPayload.Entry> chunk = new ArrayList<>(Math.min(TerminalListPayload.CHUNK, Math.max(16, totalHint)));
        boolean first = true;
        for (Map.Entry<ItemKey, Long> e : items.entrySet()) {
            chunk.add(new TerminalListPayload.Entry(e.getKey().stack(), e.getValue(), anyCraftable && craftable.contains(e.getKey())));
            if (chunk.size() == TerminalListPayload.CHUNK) {
                PacketDistributor.sendToPlayer(sp, new TerminalListPayload(containerId, first, chunk));
                first = false;
                chunk = new ArrayList<>(TerminalListPayload.CHUNK);
            }
        }
        for (ItemKey k : craftable) {
            if (!items.containsKey(k)) {
                chunk.add(new TerminalListPayload.Entry(k.stack(), 0, true));
                if (chunk.size() == TerminalListPayload.CHUNK) {
                    PacketDistributor.sendToPlayer(sp, new TerminalListPayload(containerId, first, chunk));
                    first = false;
                    chunk = new ArrayList<>(TerminalListPayload.CHUNK);
                }
            }
        }
        if (first || !chunk.isEmpty()) {
            PacketDistributor.sendToPlayer(sp, new TerminalListPayload(containerId, first, chunk));
        }
    }
}
