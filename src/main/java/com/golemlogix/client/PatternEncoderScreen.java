package com.golemlogix.client;

import com.golemlogix.menu.PatternEncoderMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public class PatternEncoderScreen extends GolemScreen<PatternEncoderMenu> {
    public PatternEncoderScreen(PatternEncoderMenu menu, Inventory inv, Component title) {
        super(menu, inv, title, 176, PatternEncoderMenu.IMAGE_HEIGHT);
    }

    @Override
    protected void init() {
        super.init();
        addRenderableWidget(new GolemButton(leftPos + 28, topPos + 70, 58, 13,
                () -> Component.translatable(menu.isCraftingMode() ? "gui.golem_logix.encoder.mode_crafting" : "gui.golem_logix.encoder.mode_processing"),
                () -> Component.translatable("gui.golem_logix.encoder.mode_tooltip"), () -> false,
                b -> minecraft.gameMode.handleInventoryButtonClick(menu.containerId, PatternEncoderMenu.BUTTON_TOGGLE)));
        // fuzzy mode: lit while on
        addRenderableWidget(new GolemButton(leftPos + 88, topPos + 70, 26, 13,
                () -> Component.literal("≈"),
                () -> Component.translatable("gui.golem_logix.encoder.fuzzy_tooltip"), menu::isFuzzy,
                b -> minecraft.gameMode.handleInventoryButtonClick(menu.containerId, PatternEncoderMenu.BUTTON_FUZZY)));
        addRenderableWidget(new GolemButton(leftPos + 88, topPos + 84, 26, 18,
                Component.translatable("gui.golem_logix.encoder.encode"),
                b -> minecraft.gameMode.handleInventoryButtonClick(menu.containerId, PatternEncoderMenu.BUTTON_ENCODE)));
        addRenderableWidget(new GolemButton(leftPos + 30, topPos + 106, 40, 12,
                Component.translatable("gui.golem_logix.encoder.clear"),
                b -> minecraft.gameMode.handleInventoryButtonClick(menu.containerId, PatternEncoderMenu.BUTTON_CLEAR)));
        addRenderableWidget(new GolemButton(leftPos + 74, topPos + 106, 40, 12,
                Component.translatable("gui.golem_logix.encoder.load"),
                b -> minecraft.gameMode.handleInventoryButtonClick(menu.containerId, PatternEncoderMenu.BUTTON_LOAD)));
        inventoryLabelY = imageHeight - 94;
    }

    @Override
    protected void renderOverlay(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        g.drawString(font, "=>", leftPos + 94, topPos + 38, GolemGui.CYAN, false);
        if (menu.isCraftingMode()) {
            // in crafting table mode only the first output is available
            for (int i = 1; i < 3; i++) {
                g.fill(leftPos + 118, topPos + 18 + i * 18, leftPos + 134, topPos + 34 + i * 18, 0xC0000000);
            }
        }
    }
}
