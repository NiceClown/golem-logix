package com.golemlogix.client;

import com.golemlogix.blockentity.CryoMainframeBlockEntity;
import com.golemlogix.init.ModBlocks;
import com.golemlogix.menu.MainframeMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Cryo-Mainframe window: two shelves of calculators (empty slots show a translucent calculator), with a summary below:
 * how many cores and how many are busy, steps per cycle per core and how many times the coprocessors count.
 */
public class MainframeScreen extends GolemScreen<MainframeMenu> {
    private static final ItemStack CALC = new ItemStack(ModBlocks.CRYO_CALCULATOR.get());

    public MainframeScreen(MainframeMenu menu, Inventory inv, Component title) {
        super(menu, inv, title, MainframeMenu.IMAGE_WIDTH, MainframeMenu.IMAGE_HEIGHT);
    }

    @Override
    protected void init() {
        super.init();
        addRedstoneButton(menu::redstoneMode);
    }

    @Override
    protected void renderExtraBg(GuiGraphicsExtractor g, float partialTick, int mouseX, int mouseY) {
        GolemGui.inset(g, leftPos + MainframeMenu.SHELF_X - 3, topPos + MainframeMenu.SHELF_Y - 3, 6 * 18 + 4, 2 * 18 + 4);
        GolemGui.inset(g, leftPos + 7, topPos + 62, 162, 46);
    }

    @Override
    protected void renderOverlay(GuiGraphicsExtractor g, float partialTick, int mouseX, int mouseY) {
        for (int i = 0; i < CryoMainframeBlockEntity.CALC_SLOTS; i++) {
            Slot s = menu.slots.get(i);
            if (!s.hasItem()) {
                int x = leftPos + s.x, y = topPos + s.y;
                g.item(CALC, x, y);
                g.pose().pushMatrix();
                g.pose().translate(0, 0);
                g.fill(x, y, x + 16, y + 16, 0xA8241A12);
                g.pose().popMatrix();
            }
        }
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        super.extractLabels(g, mouseX, mouseY);
        int x = 11, y = 66;
        g.text(font, Component.translatable("gui.golem_logix.mainframe.cores", menu.cores(), menu.busy()), x, y, GolemGui.CYAN, false);
        g.text(font, Component.translatable("gui.golem_logix.mainframe.per_calc", menu.calculators(),
                CryoMainframeBlockEntity.coresPerCalculator()), x, y + 10, GolemGui.PARCHMENT, false);
        g.text(font, Component.translatable("gui.golem_logix.mainframe.workers", menu.workersPerCore()), x, y + 20, GolemGui.PARCHMENT, false);
        g.text(font, Component.translatable("gui.golem_logix.mainframe.multiplier", menu.multiplier()), x, y + 30, 0xFFA89880, false);
    }
}
