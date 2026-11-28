package com.golemlogix.client;

import com.google.common.base.Suppliers;
import com.golemlogix.menu.PatternProviderMenu;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.function.Supplier;

public class PatternProviderScreen extends GolemScreen<PatternProviderMenu> {
    private static final Supplier<ItemStack> ICON_CLOCK = Suppliers.memoize(() -> new ItemStack(Items.CLOCK));
    private static final Supplier<ItemStack> ICON_HOPPER = Suppliers.memoize(() -> new ItemStack(Items.HOPPER));
    private static final Supplier<ItemStack> ICON_SUNFLOWER = Suppliers.memoize(() -> new ItemStack(Items.SUNFLOWER));
    private static final Supplier<ItemStack> ICON_ARROW = Suppliers.memoize(() -> new ItemStack(Items.SPECTRAL_ARROW));

    public PatternProviderScreen(PatternProviderMenu menu, Inventory inv, Component title) {
        super(menu, inv, title, PatternProviderMenu.IMAGE_WIDTH, PatternProviderMenu.IMAGE_HEIGHT);
    }

    @Override
    protected void init() {
        super.init();
        // wait-for-result mode: a clock means it waits, a hopper means it loads without stopping
        addSideButton(() -> menu.isBlocking() ? ICON_CLOCK.get() : ICON_HOPPER.get(),
                () -> Component.translatable(menu.isBlocking() ? "gui.golem_logix.provider.blocking_on" : "gui.golem_logix.provider.blocking_off")
                        .append("\n").append(Component.translatable("gui.golem_logix.provider.blocking_hint").withStyle(ChatFormatting.GRAY)),
                () -> true, menu::isBlocking, PatternProviderMenu.BUTTON_BLOCKING);
        // feeding side: a sunflower means all sides, an arrow means one (the arrows on the block faces show it too);
        // the button goes above the redstone one: that one is visible only with the module and must not leave a hole
        addSideButton(() -> menu.output() == com.golemlogix.blocks.ProviderOutput.ALL ? ICON_SUNFLOWER.get() : ICON_ARROW.get(),
                () -> Component.translatable("gui.golem_logix.provider.output").append("\n")
                        .append(Component.translatable(menu.output().key()).withStyle(ChatFormatting.AQUA)).append("\n")
                        .append(Component.translatable("gui.golem_logix.provider.output_hint").withStyle(ChatFormatting.GRAY)),
                () -> true, () -> menu.output() != com.golemlogix.blocks.ProviderOutput.ALL, PatternProviderMenu.BUTTON_OUTPUT);
        addRedstoneButton(menu::redstoneMode);
    }

    @Override
    protected void renderExtraBg(GuiGraphicsExtractor g, float partialTick, int mouseX, int mouseY) {
        GolemGui.inset(g, leftPos + 60, topPos + 18, 56, 56);
        // the return slots: a column on the right (hovering it explains them)
        GolemGui.inset(g, leftPos + PatternProviderMenu.RETURN_X - 2, topPos + PatternProviderMenu.RETURN_Y - 2, 20,
                18 * com.golemlogix.blockentity.PatternProviderBlockEntity.RETURN_SLOTS + 2);
        Component hint = Component.translatable(menu.isBlocking() ? "gui.golem_logix.provider.hint_blocking" : "gui.golem_logix.provider.hint");
        g.textWithWordWrap(font, hint, leftPos + 8, topPos + 78, imageWidth - 16, GolemGui.CYAN_DARK);
    }

    @Override
    protected void extractTooltip(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        super.extractTooltip(g, mouseX, mouseY);
        int x = leftPos + PatternProviderMenu.RETURN_X - 1, y = topPos + PatternProviderMenu.RETURN_Y - 1;
        boolean over = mouseX >= x && mouseX < x + 18 && mouseY >= y
                && mouseY < y + 18 * com.golemlogix.blockentity.PatternProviderBlockEntity.RETURN_SLOTS;
        if (over && (hoveredSlot == null || !hoveredSlot.hasItem()) && menu.getCarried().isEmpty()) {
            g.setComponentTooltipForNextFrame(font, java.util.List.of(Component.translatable("gui.golem_logix.provider.returns"),
                    Component.translatable("gui.golem_logix.provider.returns_hint").withStyle(ChatFormatting.GRAY)), mouseX, mouseY);
        }
    }
}
