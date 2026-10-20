package com.golemlogix.client;

import com.golemlogix.menu.BusMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public class BusScreen extends GolemScreen<BusMenu> {
    public BusScreen(BusMenu menu, Inventory inv, Component title) {
        super(menu, inv, title, BusMenu.IMAGE_WIDTH, BusMenu.IMAGE_HEIGHT);
    }

    @Override
    protected void init() {
        super.init();
        // on buses to the right of the filter grid, on the periscope (no filter) in the middle
        addPriorityPanel(menu.isPeriscope() ? 61 : BusMenu.FILTER_X + 56 + 8, 18, menu::getPriority);
        addRedstoneButton(menu::redstoneMode);
    }

    @Override
    protected void renderExtraBg(GuiGraphicsExtractor g, float partialTick, int mouseX, int mouseY) {
        if (!menu.isPeriscope()) {
            GolemGui.inset(g, leftPos + BusMenu.FILTER_X, topPos + 18, 56, 56);
        }
        Component hint = Component.translatable(menu.isPeriscope() ? "gui.golem_logix.bus.periscope_hint"
                : menu.isExport() ? "gui.golem_logix.bus.export_hint" : "gui.golem_logix.bus.import_hint");
        g.textWithWordWrap(font, hint, leftPos + 8, topPos + 90, imageWidth - 16, GolemGui.CYAN_DARK);
    }
}
