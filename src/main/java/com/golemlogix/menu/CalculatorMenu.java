package com.golemlogix.menu;

import com.golemlogix.blockentity.CryoCalculatorBlockEntity;
import com.golemlogix.crafting.CraftingJob;
import com.golemlogix.init.ModBlocks;
import com.golemlogix.init.ModMenus;
import com.golemlogix.network.CalculatorStatusPayload;
import com.golemlogix.system.GolemNetwork;
import com.golemlogix.upgrade.RedstoneMode;
import com.golemlogix.upgrade.UpgradeSpec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Cryo-Calculator status window: progress of the current job, contents, cancel button, Compressed Co-Processor and Redstone Module slots.
 */
public class CalculatorMenu extends GolemMenu {
    public static final int IMAGE_HEIGHT = 228;
    public static final int IMAGE_WIDTH = 176;

    @Nullable
    private final CryoCalculatorBlockEntity be;
    private final ContainerLevelAccess access;
    private final Player player;
    private final ContainerData data;
    private CalculatorStatusPayload lastSent = null;

    /** Client state. */
    public CalculatorStatusPayload clientStatus = null;

    public static CalculatorMenu fromNetwork(int id, Inventory inv, RegistryFriendlyByteBuf buf) {
        return new CalculatorMenu(id, inv, null, new SimpleContainer(UpgradeSpec.CALCULATOR.slots()), new SimpleContainerData(1),
                ContainerLevelAccess.NULL);
    }

    public CalculatorMenu(int id, Inventory inv, @Nullable CryoCalculatorBlockEntity be, Container upgrades, ContainerData data,
                          ContainerLevelAccess access) {
        super(ModMenus.CALCULATOR.get(), id);
        this.be = be;
        this.access = access;
        this.player = inv.player;
        this.data = data;
        addUpgradeSlots(upgrades, UpgradeSpec.CALCULATOR, IMAGE_WIDTH + 5, 8);
        addPlayerSlots(inv, 8, IMAGE_HEIGHT - 82);
        addDataSlots(data);
    }

    public RedstoneMode redstoneMode() {
        return RedstoneMode.byId(data.get(0));
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id == 0 && be != null && !be.isRemoved()) {
            be.cancelJob();
            return true;
        }
        if (id == BUTTON_REDSTONE) {
            data.set(0, redstoneMode().next(true).ordinal());
            return true;
        }
        return false;
    }

    @Override
    public void broadcastChanges() {
        super.broadcastChanges();
        if (be == null || be.isRemoved() || be.getLevel() == null || !(player instanceof ServerPlayer sp)) {
            return;
        }
        if (sp.level().getGameTime() % 5 != 0) {
            return;
        }
        CraftingJob job = be.getJob();
        GolemNetwork net = GolemNetwork.get(be.getLevel(), be.getBlockPos());
        boolean online = net.isOnline();
        CalculatorStatusPayload p;
        if (job == null) {
            p = new CalculatorStatusPayload(containerId, -1, 0, 0, be.getCoprocessors(), be.getWorkers(), 0, online,
                    ItemStack.EMPTY, 0, List.of());
        } else {
            List<ItemStack> contents = new ArrayList<>();
            for (Map.Entry<com.golemlogix.system.ItemKey, Long> e : job.inventory().entrySet()) {
                if (contents.size() >= 8) {
                    break;
                }
                contents.add(e.getKey().toStack((int) Math.min(e.getValue(), Integer.MAX_VALUE)));
            }
            p = new CalculatorStatusPayload(containerId, job.status().ordinal(), job.doneExecutions(), job.totalExecutions(),
                    be.getCoprocessors(), be.getWorkers(), job.awaitingTotal(), online,
                    job.target().toStack(1), job.amount(), contents);
        }
        if (!p.equals(lastSent)) {
            lastSent = p;
            PacketDistributor.sendToPlayer(sp, p);
        }
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(access, player, ModBlocks.CRYO_CALCULATOR.get());
    }
}
