package com.golemlogix.client;

import com.golemlogix.menu.PatternProviderMenu;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class PatternProviderScreen extends GolemScreen<PatternProviderMenu> {
    public PatternProviderScreen(PatternProviderMenu menu, Inventory inv, Component title) {
        super(menu, inv, title, PatternProviderMenu.IMAGE_WIDTH, PatternProviderMenu.IMAGE_HEIGHT);
    }

    @Override
    protected void init() {
        super.init();
        // wait-for-result mode: a clock means it waits, a hopper means it loads without stopping
        addSideButton(() -> new ItemStack(menu.isBlocking() ? Items.CLOCK : Items.HOPPER),
                () -> Component.translatable(menu.isBlocking() ? "gui.golem_logix.provider.blocking_on" : "gui.golem_logix.provider.blocking_off")
                        .append("\n").append(Component.translatable("gui.golem_logix.provider.blocking_hint").withStyle(ChatFormatting.GRAY)),
                () -> true, menu::isBlocking, PatternProviderMenu.BUTTON_BLOCKING);
        // feeding side: a sunflower means all sides, an arrow means one (the arrows on the block faces show it too);
        // the button goes above the redstone one: that one is visible only with the module and must not leave a hole
        addSideButton(() -> new ItemStack(menu.output() == com.golemlogix.blocks.ProviderOutput.ALL ? Items.SUNFLOWER : Items.SPECTRAL_ARROW),
                () -> Component.translatable("gui.golem_logix.provider.output").append("\n")
                        .append(Component.translatable(menu.output().key()).withStyle(ChatFormatting.AQUA)).append("\n")
                        .append(Component.translatable("gui.golem_logix.provider.output_hint").withStyle(ChatFormatting.GRAY)),
                () -> true, () -> menu.output() != com.golemlogix.blocks.ProviderOutput.ALL, PatternProviderMenu.BUTTON_OUTPUT);
        addRedstoneButton(menu::redstoneMode);
    }

    @Override
    protected void renderExtraBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        GolemGui.inset(g, leftPos + 60, topPos + 18, 56, 56);
        Component hint = Component.translatable(menu.isBlocking() ? "gui.golem_logix.provider.hint_blocking" : "gui.golem_logix.provider.hint");
        g.drawWordWrap(font, hint, leftPos + 8, topPos + 78, imageWidth - 16, GolemGui.CYAN_DARK);
    }
}
