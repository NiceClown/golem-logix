package com.golemlogix.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;

import java.util.function.Supplier;

/**
 * Кнопка в стиле мода (латунная рамка); подпись может меняться динамически.
 */
public class GolemButton extends Button {
    private final Supplier<Component> label;
    private final Supplier<Boolean> highlighted;

    public GolemButton(int x, int y, int w, int h, Supplier<Component> label, Supplier<Component> tooltip,
                       Supplier<Boolean> highlighted, OnPress onPress) {
        super(x, y, w, h, label.get(), onPress, DEFAULT_NARRATION);
        this.label = label;
        this.highlighted = highlighted;
        if (tooltip != null) {
            setTooltip(Tooltip.create(tooltip.get()));
        }
    }

    public GolemButton(int x, int y, int w, int h, Component label, OnPress onPress) {
        this(x, y, w, h, () -> label, null, () -> false, onPress);
    }

    @Override
    protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partial) {
        setMessage(label.get());
        boolean hover = isHoveredOrFocused() && active;
        int edge = hover ? GolemGui.CYAN : GolemGui.BRASS;
        int fill = !active ? 0xFF2A2018 : hover ? GolemGui.WOOD_LIGHT : GolemGui.WOOD;
        g.fill(getX(), getY(), getX() + width, getY() + height, GolemGui.WOOD_DARK);
        g.fill(getX() + 1, getY() + 1, getX() + width - 1, getY() + height - 1, edge);
        g.fill(getX() + 2, getY() + 2, getX() + width - 2, getY() + height - 2, fill);
        if (highlighted.get()) {
            g.fill(getX() + 2, getY() + height - 4, getX() + width - 2, getY() + height - 2, GolemGui.CYAN);
        }
        var font = Minecraft.getInstance().font;
        int color = !active ? 0xFF7A6A55 : hover ? GolemGui.CYAN : GolemGui.PARCHMENT;
        // длинная подпись уменьшается, чтобы не вылезать за рамку кнопки
        int textWidth = font.width(getMessage());
        float fit = Math.min(1f, (width - 6f) / Math.max(1, textWidth));
        g.pose().pushPose();
        g.pose().translate(getX() + width / 2f, getY() + height / 2f, 0);
        g.pose().scale(fit, fit, 1f);
        g.drawCenteredString(font, getMessage(), 0, -4, color);
        g.pose().popPose();
    }
}
