package com.golemlogix.client;

import com.golemlogix.menu.DriveBayMenu;
import com.golemlogix.blockentity.CopperDriveBayBlockEntity;
import com.golemlogix.items.MagneticDiskItem;
import com.golemlogix.system.DiskStorage;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

public class DriveBayScreen extends GolemScreen<DriveBayMenu> {
    private static final int INFO_X = 8, INFO_W = 54;

    public DriveBayScreen(DriveBayMenu menu, Inventory inv, Component title) {
        super(menu, inv, title, 176, DriveBayMenu.IMAGE_HEIGHT);
    }

    @Override
    protected void init() {
        super.init();
        addPriorityPanel(114, 18, menu::getPriority);
    }

    /** Сводка по кассетам слева от витрины: сколько стоит дисков и насколько заполнены предметные и жидкостные. */
    private record Summary(int disks, long itemStored, long itemCapacity, long fluidStored, long fluidCapacity) {
    }

    private Summary summary() {
        int disks = 0;
        long is = 0, ic = 0, fs = 0, fc = 0;
        for (int i = 0; i < CopperDriveBayBlockEntity.SLOTS && i < menu.slots.size(); i++) {
            ItemStack d = menu.slots.get(i).getItem();
            if (d.isEmpty()) {
                continue;
            }
            disks++;
            // заполнение — из синхронизируемого счётчика на самом диске: содержимое дисков есть только на сервере
            if (DiskStorage.isFluidDisk(d)) {
                fs += MagneticDiskItem.getStoredCount(d);
                fc += DiskStorage.capacity(d);
            } else {
                is += MagneticDiskItem.getStoredCount(d);
                ic += DiskStorage.capacity(d);
            }
        }
        return new Summary(disks, is, ic, fs, fc);
    }

    @Override
    protected void renderExtraBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        // стеклянная дверца шкафа с латунными полками между рядами кассет
        int rows = DriveBayMenu.ROWS;
        GolemGui.inset(g, leftPos + 66, topPos + 15, 44, rows * 18 + 8);
        for (int row = 1; row < rows; row++) {
            int y = topPos + 19 + row * 18 - 2;
            g.fill(leftPos + 67, y, leftPos + 109, y + 1, GolemGui.BRASS_DARK);
        }
        // сводка слева: два счётчика заполнения
        Summary s = summary();
        GolemGui.bar(g, leftPos + INFO_X, topPos + 58, INFO_W, 8, s.itemCapacity > 0 ? (double) s.itemStored / s.itemCapacity : 0, GolemGui.BRASS);
        GolemGui.bar(g, leftPos + INFO_X, topPos + 90, INFO_W, 8, s.fluidCapacity > 0 ? (double) s.fluidStored / s.fluidCapacity : 0, GolemGui.CYAN);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        super.renderLabels(g, mouseX, mouseY);
        Summary s = summary();
        g.drawString(font, Component.translatable("gui.golem_logix.drive_bay.cassettes"), INFO_X, 20, GolemGui.PARCHMENT, false);
        g.drawString(font, s.disks + " / " + CopperDriveBayBlockEntity.SLOTS, INFO_X, 31, GolemGui.CYAN, false);
        g.drawString(font, Component.translatable("gui.golem_logix.drive_bay.items"), INFO_X, 47, GolemGui.PARCHMENT, false);
        g.drawString(font, GolemGui.formatCount(s.itemStored) + " / " + GolemGui.formatCount(s.itemCapacity), INFO_X, 69, GolemGui.BRASS_L, false);
        g.drawString(font, Component.translatable("gui.golem_logix.drive_bay.fluids"), INFO_X, 79, GolemGui.PARCHMENT, false);
        g.drawString(font, GolemGui.formatCount(s.fluidStored) + " / " + GolemGui.formatCount(s.fluidCapacity) + " mB", INFO_X, 101, GolemGui.CYAN, false);
    }
}
