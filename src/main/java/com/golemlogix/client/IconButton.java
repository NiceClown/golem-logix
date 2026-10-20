package com.golemlogix.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * A square 18x18 brass button with an item icon (settings buttons to the left of the window: redstone, wait mode).
 * The icon and the tooltip change with the mode; the button can hide (for example without a Redstone Module).
 */
public class IconButton extends Button {
    private final Supplier<ItemStack> icon;
    private final Supplier<Component> tip;
    private final BooleanSupplier visibleIf;
    private final BooleanSupplier lit;
    private Component lastTip;

    public IconButton(int x, int y, Supplier<ItemStack> icon, Supplier<Component> tip, BooleanSupplier visibleIf, BooleanSupplier lit,
                      OnPress onPress) {
        super(x, y, 18, 18, Component.empty(), onPress, DEFAULT_NARRATION);
        this.icon = icon;
        this.tip = tip;
        this.visibleIf = visibleIf;
        this.lit = lit;
    }

    public boolean updateVisibility() {
        this.visible = visibleIf.getAsBoolean();
        return this.visible;
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
        if (!visible) {
            return;
        }
        Component t = tip.get();
        if (!t.equals(lastTip)) {
            lastTip = t;
            setTooltip(Tooltip.create(t));
        }
        boolean hover = isHoveredOrFocused();
        g.fill(getX(), getY(), getX() + width, getY() + height, GolemGui.WOOD_DARK);
        g.fill(getX() + 1, getY() + 1, getX() + width - 1, getY() + height - 1, hover ? GolemGui.CYAN : GolemGui.BRASS);
        g.fill(getX() + 2, getY() + 2, getX() + width - 2, getY() + height - 2, lit.getAsBoolean() ? 0xFF1B4A48 : GolemGui.WOOD);
        g.item(icon.get(), getX() + 1, getY() + 1);
    }

    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {
        return updateVisibility() && super.isMouseOver(mouseX, mouseY);
    }
}
