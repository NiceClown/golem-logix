package com.golemlogix.system;

import com.golemlogix.blockentity.CardCatalogBlockEntity;
import com.golemlogix.blockentity.PatternProviderBlockEntity;
import com.golemlogix.crafting.CraftingCpu;
import com.golemlogix.crafting.CraftingJob;
import com.golemlogix.crafting.CraftingPlan;
import com.golemlogix.crafting.CraftingPlanner;
import com.golemlogix.data.PatternData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.fluids.FluidStack;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Pattern indexing, crafting CPU selection, job reservation/rollback, and machine-output absorption for {@link GolemNetwork}.
 */
final class NetworkCrafting {
    private NetworkCrafting() {
    }

    /** Index "item -> the pattern that produces it"; rebuilt when punch cards change (the network itself is remembered longer). */
    static Map<ItemKey, GolemNetwork.PatternRef> patternIndex(GolemNetwork net) {
        int epoch = NetworkCache.patternEpoch();
        if (net.patternIndex == null || net.patternIndexEpoch != epoch) {
            net.patternIndexEpoch = epoch;
            net.patternIndex = new LinkedHashMap<>();
            for (PatternProviderBlockEntity provider : net.providers) {
                if (provider.isRemoved()) {
                    continue;
                }
                for (PatternData pattern : provider.getPatterns()) {
                    for (ItemKey out : pattern.outputTotals().keySet()) {
                        net.patternIndex.putIfAbsent(out, new GolemNetwork.PatternRef(pattern, provider));
                    }
                }
            }
            for (CardCatalogBlockEntity catalog : net.catalogs) {
                if (catalog.isRemoved()) {
                    continue;
                }
                for (PatternData pattern : catalog.patterns()) {
                    for (ItemKey out : pattern.outputTotals().keySet()) {
                        net.patternIndex.putIfAbsent(out, new GolemNetwork.PatternRef(pattern, null));
                    }
                }
            }
        }
        return net.patternIndex;
    }

    /**
     * Every gateway that can run this step: those holding the very pattern first, then those holding the same recipe with other
     * fuzziness (a player may have the same plate on two gateways, only one of them at the machine).
     */
    static List<PatternProviderBlockEntity> providersFor(GolemNetwork net, PatternData pattern) {
        List<PatternProviderBlockEntity> exact = new ArrayList<>();
        List<PatternProviderBlockEntity> same = null;
        for (PatternProviderBlockEntity provider : net.providers) {
            if (provider.isRemoved()) {
                continue;
            }
            if (provider.hasPattern(pattern)) {
                exact.add(provider);
            } else {
                for (PatternData p : provider.getPatterns()) {
                    if (p.sameRecipe(pattern)) {
                        if (same == null) {
                            same = new ArrayList<>();
                        }
                        same.add(provider);
                        break;
                    }
                }
            }
        }
        if (same != null) {
            exact.addAll(same);
        }
        return exact;
    }

    @Nullable
    static CraftingCpu freeCpu(GolemNetwork net) {
        for (CraftingCpu c : net.cpus) {
            if (!c.isRemoved() && c.isIdle() && c.canRun(net)) {
                return c;
            }
        }
        return null;
    }

    @Nullable
    static CraftingJob jobWantingHelp(GolemNetwork net) {
        CraftingJob best = null;
        long most = 0;
        for (CraftingCpu c : net.cpus) {
            CraftingJob job = c.isRemoved() ? null : c.getJob();
            if (job != null && job.wantsHelp() && job.remainingExecutions() > most && c.acceptsHelp() && c.canRun(net)) {
                best = job;
                most = job.remainingExecutions();
            }
        }
        return best != null && CraftLoad.nanosLeft(net.level) > 0 ? best : null;
    }

    static GolemNetwork.StartResult startCrafting(GolemNetwork net, Map<ItemKey, Long> targets,
                                                  @Nullable UUID requester, @Nullable GlobalPos bus) {
        if (!net.isOnline()) {
            return new GolemNetwork.StartResult(GolemNetwork.StartType.OFFLINE, null);
        }
        if (targets.isEmpty()) {
            return new GolemNetwork.StartResult(GolemNetwork.StartType.NO_PATTERN, null);
        }
        for (ItemKey t : targets.keySet()) {
            if (!net.canCraft(t)) {
                return new GolemNetwork.StartResult(GolemNetwork.StartType.NO_PATTERN, null);
            }
        }
        // A device's order: no plan without a free calculator, and none yet if it has just failed
        long now = net.level.getGameTime();
        if (bus != null) {
            if (freeCpu(net) == null) {
                return new GolemNetwork.StartResult(GolemNetwork.StartType.NO_CPU, null);
            }
            if (OrderBackoff.waiting(bus.dimension(), bus.pos(), targets.keySet(), now, net.networkEpoch())) {
                return new GolemNetwork.StartResult(GolemNetwork.StartType.MISSING, null);
            }
        }
        CraftingPlan plan = CraftingPlanner.plan(net, targets);
        if (!plan.missing().isEmpty()) {
            if (bus != null) {
                OrderBackoff.failed(bus.dimension(), bus.pos(), targets.keySet(), now, net.networkEpoch());
            }
            return new GolemNetwork.StartResult(GolemNetwork.StartType.MISSING, plan);
        }
        if (bus != null) {
            OrderBackoff.succeeded(bus.dimension(), bus.pos(), targets.keySet());
        }
        if (plan.steps().isEmpty()) {
            return new GolemNetwork.StartResult(GolemNetwork.StartType.NO_PATTERN, plan);
        }
        CraftingCpu cpu = freeCpu(net);
        if (cpu == null) {
            return new GolemNetwork.StartResult(GolemNetwork.StartType.NO_CPU, plan);
        }
        Map<ItemKey, Long> reserved = reserveAll(net, plan, cpu);
        if (reserved == null) {
            return new GolemNetwork.StartResult(GolemNetwork.StartType.RESERVE_FAILED, plan);
        }
        cpu.startJob(new CraftingJob(plan, reserved).requestedBy(requester, bus));
        return new GolemNetwork.StartResult(GolemNetwork.StartType.STARTED, plan);
    }

    /** Reserves every source material required by plan (all or nothing), rolling back on any race. */
    @Nullable
    private static Map<ItemKey, Long> reserveAll(GolemNetwork net, CraftingPlan plan, CraftingCpu cpu) {
        for (Map.Entry<ItemKey, Long> e : plan.fromStorage().entrySet()) {
            if (net.countOf(e.getKey()) < e.getValue()) {
                return null;
            }
        }
        // Liquid Cryo-Source goes last: it is made from Source and cannot be put back if something else fails
        List<Map.Entry<ItemKey, Long>> order = new ArrayList<>(plan.fromStorage().entrySet());
        order.sort(Comparator.comparing((Map.Entry<ItemKey, Long> e) -> Ingredients.isFluid(e.getKey())
                && CryoSource.is(Ingredients.fluid(e.getKey(), 1))));
        Map<ItemKey, Long> reserved = new LinkedHashMap<>();
        for (Map.Entry<ItemKey, Long> e : order) {
            long got = reserve(net, e.getKey(), e.getValue());
            if (got > 0) {
                reserved.merge(e.getKey(), got, Long::sum);
            }
            if (got < e.getValue()) {
                rollbackReserved(net, cpu, reserved);
                return null;
            }
        }
        return reserved;
    }

    /** Returns partially reserved ingredients to storage; drops any items that no longer fit so nothing is voided. */
    private static void rollbackReserved(GolemNetwork net, CraftingCpu cpu, Map<ItemKey, Long> reserved) {
        Level cpuLevel = cpu.cpuLevel();
        boolean useCpu = cpuLevel != null || net.contains(net.level, cpu.cpuPos());
        Level dropLevel = cpuLevel != null ? cpuLevel : (useCpu ? net.level : net.coreLevel);
        BlockPos dropPos = useCpu ? cpu.cpuPos() : net.corePos;
        for (Map.Entry<ItemKey, Long> r : reserved.entrySet()) {
            long leftover = giveBack(net, r.getKey(), r.getValue());
            if (leftover > 0 && !Ingredients.isFluid(r.getKey()) && dropLevel != null && dropPos != null) {
                int maxStack = Math.max(1, r.getKey().stack().getMaxStackSize());
                while (leftover > 0) {
                    int chunk = (int) Math.min(leftover, maxStack);
                    Containers.dropItemStack(dropLevel, dropPos.getX() + 0.5, dropPos.getY() + 1.0,
                            dropPos.getZ() + 0.5, r.getKey().toStack(chunk));
                    leftover -= chunk;
                }
            }
        }
    }

    /** Takes up to amount of an ingredient out of storage (items by count, fluids in mB); returns how much was taken. */
    private static long reserve(GolemNetwork net, ItemKey key, long amount) {
        long left = amount;
        boolean fluid = Ingredients.isFluid(key);
        while (left > 0) {
            int chunk = (int) Math.min(left, 1_000_000_000L);
            int got = fluid ? net.extractFluid(Ingredients.fluid(key, 1), chunk, false).getAmount()
                    : net.extractItem(key.stack(), chunk, false).getCount();
            if (got <= 0) {
                break;
            }
            left -= got;
        }
        return amount - left;
    }

    /** Puts an ingredient back into storage; returns how much did not fit (Liquid Cryo-Source never fits). */
    static long giveBack(GolemNetwork net, ItemKey key, long amount) {
        long n = amount;
        while (n > 0) {
            int chunk = (int) Math.min(n, 1_000_000_000L);
            int stored = Ingredients.isFluid(key) ? net.insertFluid(Ingredients.fluid(key, chunk), false)
                    : chunk - net.insertItem(key.toStack(chunk), false).getCount();
            n -= stored;
            if (stored < chunk) {
                break;
            }
        }
        return n;
    }

    static boolean providerBusy(GolemNetwork net, GlobalPos provider) {
        for (CraftingCpu cpu : net.cpus) {
            if (!cpu.isRemoved() && cpu.getJob() != null && cpu.getJob().hasInFlight(provider)) {
                return true;
            }
        }
        return false;
    }

    static boolean busCraftPending(GolemNetwork net, GlobalPos bus, ItemKey key) {
        for (CraftingCpu cpu : net.cpus) {
            CraftingJob job = cpu.isRemoved() ? null : cpu.getJob();
            if (job != null && bus.equals(job.requesterBus()) && job.targets().containsKey(key)) {
                return true;
            }
        }
        return false;
    }

    static int machineWants(GolemNetwork net, ItemKey key) {
        long n = 0;
        for (CraftingCpu cpu : net.cpus) {
            if (!cpu.isRemoved()) {
                n += cpu.wants(key);
            }
        }
        return (int) Math.min(n, Integer.MAX_VALUE);
    }

    static ItemStack insertFromMachine(GolemNetwork net, ItemStack stack, @Nullable BlockPos skipTarget) {
        ItemStack rest = stack;
        for (CraftingCpu cpu : net.cpus) {
            if (rest.isEmpty()) {
                break;
            }
            if (!cpu.isRemoved()) {
                rest = cpu.absorb(rest);
            }
        }
        return net.insertItem(rest, false, skipTarget);
    }

    static int insertFluidFromMachine(GolemNetwork net, FluidStack fluid, @Nullable BlockPos skipTarget) {
        if (fluid.isEmpty()) {
            return 0;
        }
        int taken = 0;
        for (CraftingCpu cpu : net.cpus) {
            if (taken >= fluid.getAmount()) {
                break;
            }
            if (!cpu.isRemoved()) {
                taken += cpu.absorbFluid(fluid.copyWithAmount(fluid.getAmount() - taken));
            }
        }
        return taken >= fluid.getAmount() ? taken
                : taken + net.insertFluid(fluid.copyWithAmount(fluid.getAmount() - taken), false, skipTarget);
    }
}
