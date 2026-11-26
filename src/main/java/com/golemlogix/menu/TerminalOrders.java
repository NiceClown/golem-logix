package com.golemlogix.menu;

import com.golemlogix.crafting.CraftingPlan;
import com.golemlogix.crafting.CraftingPlanner;
import com.golemlogix.network.CraftPlanPayload;
import com.golemlogix.network.CraftRequestPayload;
import com.golemlogix.security.SecurityPermission;
import com.golemlogix.system.GolemNetwork;
import com.golemlogix.system.ItemKey;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Autocrafting order handling for {@link TerminalMenu}: order previews, starting jobs,
 * JEI Ctrl+"+" precrafting, planner row formatting, and player chat/overlay status reports.
 */
final class TerminalOrders {
    private static final long MAX_CRAFT_AMOUNT = 1_000_000L;
    private static final int MAX_PRECRAFT_AMOUNT = 4096;
    private static final int MAX_MISSING_CHAT_LINES = 8;

    private TerminalOrders() {
    }

    /**
     * Handles an order preview or start request from the craft order screen.
     * Returns true when the terminal should force an immediate list/info sync.
     */
    static boolean handleCraftRequest(ServerPlayer sp, int containerId, GolemNetwork net, CraftRequestPayload p) {
        if (!net.hasPermission(sp, SecurityPermission.CRAFT)) {
            sendPlan(sp, containerId, p.request(), CraftPlanPayload.Status.OFFLINE, null);
            sp.sendOverlayMessage(Component.translatable("message.golem_logix.security_denied"));
            return false;
        }
        ItemKey key = ItemKey.of(p.key());
        long amount = Math.max(1, Math.min(p.amount(), MAX_CRAFT_AMOUNT));
        Component name = key.stack().getHoverName();

        if (!net.canCraft(key)) {
            sendPlan(sp, containerId, p.request(), CraftPlanPayload.Status.NO_PATTERN, null);
            return false;
        }

        if (p.preview()) {
            CraftingPlan plan = CraftingPlanner.plan(net, key, amount);
            CraftPlanPayload.Status status = plan.missing().isEmpty()
                    ? CraftPlanPayload.Status.OK : CraftPlanPayload.Status.MISSING;
            sendPlan(sp, containerId, p.request(), status, plan);
            return false;
        }

        Map<ItemKey, Long> one = new LinkedHashMap<>();
        one.put(key, amount);
        GolemNetwork.StartResult result = net.startCrafting(one, sp.getUUID(), null);
        CraftPlanPayload.Status status = mapStartStatus(result.type());
        CraftingPlan plan = result.plan() != null ? result.plan() : CraftingPlanner.plan(net, key, amount);
        sendPlan(sp, containerId, p.request(), status, plan);
        if (status == CraftPlanPayload.Status.STARTED) {
            sp.sendOverlayMessage(Component.translatable("message.golem_logix.craft.started", amount, name, plan.totalExecutions())
                    .withStyle(ChatFormatting.AQUA));
        }
        return true;
    }

    /**
     * Precraft (Ctrl + "+" in JEI): one job for all missing ingredients that have patterns.
     * Items without patterns are listed in chat. Returns true when a craft job was attempted.
     */
    static boolean handlePrecraft(ServerPlayer sp, GolemNetwork net, List<ItemStack> items, List<Integer> counts) {
        if (!net.hasPermission(sp, SecurityPermission.CRAFT)) {
            sp.sendOverlayMessage(Component.translatable("message.golem_logix.security_denied"));
            return false;
        }
        Map<ItemKey, Long> targets = new LinkedHashMap<>();
        List<Component> uncraftable = new ArrayList<>();
        int count = Math.min(items.size(), counts.size());
        for (int i = 0; i < count; i++) {
            ItemStack s = items.get(i);
            if (s.isEmpty()) {
                continue;
            }
            ItemKey key = ItemKey.of(s);
            long n = Math.max(1, Math.min(counts.get(i), MAX_PRECRAFT_AMOUNT));
            if (net.canCraft(key)) {
                targets.merge(key, n, Long::sum);
            } else {
                uncraftable.add(key.stack().getHoverName());
            }
        }
        if (!uncraftable.isEmpty()) {
            sp.sendSystemMessage(Component.translatable("message.golem_logix.precraft.uncraftable", joinComponents(uncraftable))
                    .withStyle(ChatFormatting.YELLOW));
        }
        if (targets.isEmpty()) {
            return false;
        }
        MutableComponent what = formatTargets(targets);
        GolemNetwork.StartResult result = net.startCrafting(targets, sp.getUUID(), null);
        ItemKey first = targets.keySet().iterator().next();
        reportStart(sp, result, Component.translatable("message.golem_logix.precraft.started", what),
                targets.get(first), first.stack().getHoverName());
        return true;
    }

    /** Sends the order plan to the order window's planner: missing, crafted and taken from the network (in that order). */
    static void sendPlan(ServerPlayer sp, int containerId, int request, CraftPlanPayload.Status status, @Nullable CraftingPlan plan) {
        List<CraftPlanPayload.Entry> entries = plan == null ? List.of() : planEntries(plan);
        PacketDistributor.sendToPlayer(sp, new CraftPlanPayload(containerId, request, status,
                plan == null ? 0 : plan.steps().size(),
                plan == null ? 0 : plan.totalExecutions(), entries));
    }

    /** Planner rows: per item, how much to take from the network, how much to craft, how much is missing. */
    static List<CraftPlanPayload.Entry> planEntries(CraftingPlan plan) {
        Map<ItemKey, long[]> rows = new LinkedHashMap<>();
        for (Map.Entry<ItemKey, Long> e : plan.missing().entrySet()) {
            rows.computeIfAbsent(e.getKey(), k -> new long[3])[2] += e.getValue();
        }
        for (CraftingPlan.Step s : plan.steps()) {
            for (Map.Entry<ItemKey, Integer> out : s.pattern().outputTotals().entrySet()) {
                rows.computeIfAbsent(out.getKey(), k -> new long[3])[1] += s.times() * out.getValue();
            }
        }
        for (Map.Entry<ItemKey, Long> e : plan.fromStorage().entrySet()) {
            rows.computeIfAbsent(e.getKey(), k -> new long[3])[0] += e.getValue();
        }
        List<CraftPlanPayload.Entry> entries = new ArrayList<>(rows.size());
        for (Map.Entry<ItemKey, long[]> e : rows.entrySet()) {
            long[] v = e.getValue();
            entries.add(new CraftPlanPayload.Entry(e.getKey().stack(), v[0], v[1], v[2]));
        }
        entries.sort(Comparator.comparingInt(e -> e.missing() > 0 ? 0 : e.craft() > 0 ? 1 : 2));
        return entries.size() > CraftPlanPayload.MAX_ENTRIES
                ? new ArrayList<>(entries.subList(0, CraftPlanPayload.MAX_ENTRIES)) : entries;
    }

    private static CraftPlanPayload.Status mapStartStatus(GolemNetwork.StartType type) {
        return switch (type) {
            case STARTED -> CraftPlanPayload.Status.STARTED;
            case MISSING -> CraftPlanPayload.Status.MISSING;
            case NO_CPU -> CraftPlanPayload.Status.NO_CPU;
            case RESERVE_FAILED -> CraftPlanPayload.Status.RESERVE_FAILED;
            case OFFLINE -> CraftPlanPayload.Status.OFFLINE;
            case NO_PATTERN -> CraftPlanPayload.Status.NO_PATTERN;
        };
    }

    private static MutableComponent joinComponents(List<Component> parts) {
        MutableComponent list = Component.empty();
        for (int i = 0; i < parts.size(); i++) {
            if (i > 0) {
                list.append(Component.literal(", "));
            }
            list.append(parts.get(i));
        }
        return list;
    }

    private static MutableComponent formatTargets(Map<ItemKey, Long> targets) {
        MutableComponent what = Component.empty();
        int i = 0;
        for (Map.Entry<ItemKey, Long> e : targets.entrySet()) {
            if (i++ > 0) {
                what.append(Component.literal(", "));
            }
            what.append(Component.literal(e.getValue() + "× ")).append(e.getKey().stack().getHoverName());
        }
        return what;
    }

    private static void reportStart(ServerPlayer sp, GolemNetwork.StartResult result, Component started, long amount, Component name) {
        switch (result.type()) {
            case STARTED -> sp.sendSystemMessage(started.copy().withStyle(ChatFormatting.AQUA));
            case MISSING -> {
                sp.sendSystemMessage(Component.translatable("message.golem_logix.craft.missing", amount, name).withStyle(ChatFormatting.RED));
                reportMissing(sp, result.plan());
            }
            case NO_CPU -> sp.sendSystemMessage(Component.translatable("message.golem_logix.craft.no_cpu").withStyle(ChatFormatting.RED));
            case RESERVE_FAILED -> sp.sendSystemMessage(Component.translatable("message.golem_logix.craft.reserve_failed").withStyle(ChatFormatting.RED));
            case OFFLINE -> sp.sendSystemMessage(Component.translatable("message.golem_logix.no_power").withStyle(ChatFormatting.RED));
            case NO_PATTERN -> sp.sendSystemMessage(Component.translatable("message.golem_logix.craft.no_pattern", name).withStyle(ChatFormatting.RED));
        }
    }

    private static void reportMissing(ServerPlayer sp, @Nullable CraftingPlan plan) {
        if (plan == null) {
            return;
        }
        int shown = 0;
        for (Map.Entry<ItemKey, Long> e : plan.missing().entrySet()) {
            if (shown++ >= MAX_MISSING_CHAT_LINES) {
                sp.sendSystemMessage(Component.literal("  ...").withStyle(ChatFormatting.GRAY));
                break;
            }
            sp.sendSystemMessage(Component.translatable("message.golem_logix.craft.missing_item", e.getValue(), e.getKey().stack().getHoverName())
                    .withStyle(ChatFormatting.YELLOW));
        }
    }
}
