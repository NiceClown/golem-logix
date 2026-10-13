package com.golemlogix.menu;

import com.golemlogix.crafting.CraftingCpu;
import com.golemlogix.crafting.CraftingJob;
import com.golemlogix.init.ModBlocks;
import com.golemlogix.init.ModMenus;
import com.golemlogix.network.MonitorPayload;
import com.golemlogix.system.GolemNetwork;
import com.golemlogix.system.ItemKey;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Recipe Monitor console: a list of all Cryo-Calculators of the network with orders, progress, steps and waits. There are no slots:
 * the server sends a snapshot every half second, the "Cancel" button goes as a menu button press (100 + calculator number).
 */
public class RecipeMonitorMenu extends GolemMenu {
    public static final int BUTTON_CANCEL_BASE = 100;
    private static final int MAX_STEPS = 27;

    @Nullable
    private final Level level;
    @Nullable
    private final BlockPos pos;
    private final ContainerLevelAccess access;
    private final Player player;
    /** Processors in the order of the monitor rows: the "cancel" button of row i cancels the job of sentOrder[i]. */
    private List<CraftingCpu> sentOrder = List.of();
    private MonitorPayload lastSent;

    /** Client: the last snapshot. */
    @Nullable
    public MonitorPayload clientData;
    public int clientVersion = 0;

    public static RecipeMonitorMenu fromNetwork(int id, Inventory inv, RegistryFriendlyByteBuf buf) {
        return new RecipeMonitorMenu(id, inv, null, null, ContainerLevelAccess.NULL);
    }

    public RecipeMonitorMenu(int id, Inventory inv, @Nullable Level level, @Nullable BlockPos pos, ContainerLevelAccess access) {
        super(ModMenus.RECIPE_MONITOR.get(), id);
        this.level = level;
        this.pos = pos;
        this.access = access;
        this.player = inv.player;
    }

    public void clientReceive(MonitorPayload p) {
        clientData = p;
        clientVersion++;
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id < BUTTON_CANCEL_BASE || level == null || pos == null) {
            return false;
        }
        int index = id - BUTTON_CANCEL_BASE;
        if (index >= sentOrder.size()) {
            return false;
        }
        GolemNetwork net = GolemNetwork.scan(level, pos);
        if (net.accessError(pos) != null) {
            return false;
        }
        CraftingCpu target = sentOrder.get(index);
        for (CraftingCpu cpu : net.cpus()) {
            if (cpu == target && !cpu.isRemoved()) {
                cpu.cancelJob();
                lastSent = null;
                return true;
            }
        }
        return false;
    }

    @Override
    public void broadcastChanges() {
        super.broadcastChanges();
        if (level == null || pos == null || !(player instanceof ServerPlayer sp) || level.getGameTime() % 10 != 0) {
            return;
        }
        GolemNetwork net = GolemNetwork.get(level, pos);
        String err = net.accessError(pos);
        List<MonitorPayload.Cpu> list = new ArrayList<>();
        List<CraftingCpu> order = new ArrayList<>();
        if (err == null) {
            List<CraftingCpu> cpus = new ArrayList<>(net.cpus());
            // busy ones first, then free ones; within that by coordinates (so the order does not jump)
            cpus.sort(Comparator.comparing((CraftingCpu c) -> c.isIdle()).thenComparingLong(c -> c.cpuPos().asLong()).thenComparingInt(CraftingCpu::core));
            for (CraftingCpu cpu : cpus) {
                if (cpu.isRemoved()) {
                    continue;
                }
                order.add(cpu);
                list.add(describe(cpu));
            }
        }
        MonitorPayload p = new MonitorPayload(containerId, err == null ? "" : err, list);
        if (!p.equals(lastSent)) {
            lastSent = p;
            sentOrder = order;
            PacketDistributor.sendToPlayer(sp, p);
        }
    }

    private static MonitorPayload.Cpu describe(CraftingCpu cpu) {
        CraftingJob job = cpu.getJob();
        if (job == null) {
            return new MonitorPayload.Cpu(cpu.cpuPos(), cpu.core(), -1, cpu.getCoprocessors(), 0, 0, ItemStack.EMPTY,
                    List.of(), List.of(), List.of());
        }
        List<MonitorPayload.Step> targets = new ArrayList<>();
        for (Map.Entry<ItemKey, Long> e : job.targets().entrySet()) {
            targets.add(new MonitorPayload.Step(e.getKey().stack(), e.getValue()));
        }
        List<MonitorPayload.Step> steps = new ArrayList<>();
        for (CraftingJob.StepState s : job.steps()) {
            if (s.remaining <= 0 || steps.size() >= MAX_STEPS) {
                continue;
            }
            ItemStack out = s.pattern.primaryOutput();
            if (!out.isEmpty()) {
                steps.add(new MonitorPayload.Step(out.copyWithCount(1), s.remaining * out.getCount()));
            }
        }
        List<MonitorPayload.Step> waiting = new ArrayList<>();
        for (Map.Entry<ItemKey, Long> e : job.awaiting().entrySet()) {
            if (waiting.size() >= 9) {
                break;
            }
            waiting.add(new MonitorPayload.Step(e.getKey().stack(), e.getValue()));
        }
        return new MonitorPayload.Cpu(cpu.cpuPos(), cpu.core(), job.status().ordinal(), cpu.getCoprocessors(), job.doneExecutions(),
                job.totalExecutions(), job.current().stack(), targets, steps, waiting);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(access, player, ModBlocks.RECIPE_MONITOR.get());
    }
}
