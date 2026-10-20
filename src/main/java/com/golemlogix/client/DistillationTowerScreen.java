package com.golemlogix.client;

import com.golemlogix.init.ModItems;
import com.golemlogix.menu.DistillationTowerMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.Locale;

/**
 * Cryo-Distillation Tower window: on the left the blood column (dark red, filling from the bottom), on the right what the tower
 * does: blood in the tank, the pace (mB of blood and Source a second), the co-processors and the state. The co-processor slots
 * stand in a column on the right side of the window.
 */
public class DistillationTowerScreen extends GolemScreen<DistillationTowerMenu> {
    private static final int BLOOD = 0xFF8A1418, BLOOD_LIGHT = 0xFFC83A3A;
    private static final ItemStack COPROCESSOR = new ItemStack(ModItems.COMPRESSED_COPROCESSOR.get());

    public DistillationTowerScreen(DistillationTowerMenu menu, Inventory inv, Component title) {
        super(menu, inv, title, DistillationTowerMenu.IMAGE_WIDTH, DistillationTowerMenu.IMAGE_HEIGHT);
    }

    @Override
    protected void renderExtraBg(GuiGraphicsExtractor g, float partialTick, int mouseX, int mouseY) {
        // the tank: a column filling from the bottom, with marks every quarter
        int x = leftPos + 10, y = topPos + 18, w = 18, h = 58;
        GolemGui.inset(g, x - 2, y - 2, w + 4, h + 4);
        int filled = (int) Math.round(h * Math.min(1.0, menu.blood() / (double) menu.capacity()));
        if (filled > 0) {
            g.fill(x, y + h - filled, x + w, y + h, BLOOD);
            g.fill(x, y + h - filled, x + w, y + h - filled + 1, BLOOD_LIGHT);
            g.fill(x + 2, y + h - filled + 1, x + 4, y + h, 0x30FFFFFF);
        }
        for (int k = 1; k < 4; k++) {
            g.fill(x + w - 5, y + h * k / 4, x + w, y + h * k / 4 + 1, GolemGui.BRASS);
        }
        GolemGui.inset(g, leftPos + 34, topPos + 16, 135, 62);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        super.extractLabels(g, mouseX, mouseY);
        int x = 38, y = 20;
        g.text(font, Component.translatable("gui.golem_logix.tower.blood", menu.blood(), menu.capacity()), x, y, GolemGui.PARCHMENT, false);
        g.text(font, Component.translatable("gui.golem_logix.tower.rate", menu.rate()), x, y + 12, GolemGui.PARCHMENT, false);
        String source = String.format(Locale.ROOT, menu.sourcePerSecond() == Math.rint(menu.sourcePerSecond()) ? "%.0f" : "%.1f",
                menu.sourcePerSecond());
        g.text(font, Component.translatable("gui.golem_logix.tower.source", source), x, y + 22, GolemGui.CYAN, false);
        // the co-processors: a small icon with their number
        g.pose().pushMatrix();
        g.pose().translate(x, y + 33);
        g.pose().scale(0.625f, 0.625f);
        g.item(COPROCESSOR, 0, 0);
        g.pose().popMatrix();
        g.text(font, Component.translatable("gui.golem_logix.tower.coprocessors", menu.coprocessors()), x + 12, y + 34,
                0xFFA89880, false);
        int status = menu.status();
        int color = status == 1 ? GolemGui.GREEN : status == 2 ? GolemGui.COPPER : GolemGui.RED;
        g.text(font, Component.translatable("gui.golem_logix.tower.status." + status), x, y + 46, color, false);
    }

    @Override
    protected void extractTooltip(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        super.extractTooltip(g, mouseX, mouseY);
        if (mouseX >= leftPos + 8 && mouseX < leftPos + 30 && mouseY >= topPos + 16 && mouseY < topPos + 78) {
            g.setTooltipForNextFrame(font, Component.translatable("gui.golem_logix.tower.tank_tip"), mouseX, mouseY);
        }
    }
}
