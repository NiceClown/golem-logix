package com.golemlogix.client;

import com.golemlogix.blockentity.EssentiaCauldronBlockEntity;
import com.golemlogix.init.ModItems;
import com.golemlogix.menu.EssentiaCauldronMenu;
import com.golemlogix.upgrade.UpgradeSpec;
import com.google.common.base.Suppliers;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.Locale;
import java.util.function.Supplier;

/**
 * Essentia Cryo-Cauldron window: dual vertical Essentia tanks on the left (Auram violet, Gelum icy cyan), live Source/s
 * and Compressed Co-Processor status on the right, and an Ice cooling charge bar below.
 */
public class EssentiaCauldronScreen extends GolemScreen<EssentiaCauldronMenu> {
    private static final int AURAM_COLOR = 0xFF7B38C8, AURAM_LIGHT = 0xFFD49CFF;
    private static final int GELUM_COLOR = 0xFF38C4DE, GELUM_LIGHT = 0xFFC2FAFF;
    private static final int TANK_Y = 18, TANK_H = 62;
    private static final Supplier<ItemStack> COPROCESSOR = Suppliers.memoize(() -> new ItemStack(ModItems.COMPRESSED_COPROCESSOR.get()));

    public EssentiaCauldronScreen(EssentiaCauldronMenu menu, Inventory inv, Component title) {
        super(menu, inv, title, EssentiaCauldronMenu.IMAGE_WIDTH, EssentiaCauldronMenu.IMAGE_HEIGHT);
    }

    private void tank(GuiGraphics g, int x, int amount, int capacity, int color, int light) {
        int y = topPos + TANK_Y, w = 14;
        GolemGui.inset(g, x - 2, y - 2, w + 4, TANK_H + 4);
        int filled = (int) Math.round(TANK_H * Math.min(1.0, amount / (double) Math.max(1, capacity)));
        if (filled > 0) {
            g.fill(x, y + TANK_H - filled, x + w, y + TANK_H, color);
            g.fill(x, y + TANK_H - filled, x + w, y + TANK_H - filled + 1, light);
            g.fill(x + 2, y + TANK_H - filled + 1, x + 4, y + TANK_H, 0x30FFFFFF);
        }
        for (int k = 1; k < 4; k++) {
            g.fill(x + w - 4, y + TANK_H * k / 4, x + w, y + TANK_H * k / 4 + 1, GolemGui.BRASS);
        }
    }

    @Override
    protected void renderExtraBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        // Left tank = Auram (violet), Right tank = Gelum (icy cyan)
        tank(g, leftPos + 10, menu.auram(), menu.capacity(), AURAM_COLOR, AURAM_LIGHT);
        tank(g, leftPos + 30, menu.gelum(), menu.capacity(), GELUM_COLOR, GELUM_LIGHT);

        // Status readout panel
        GolemGui.inset(g, leftPos + 50, topPos + 16, 119, 44);
        if (menu.boilOff()) {
            // Subtle crimson inner warning rim on boil-off
            g.fill(leftPos + 51, topPos + 17, leftPos + 168, topPos + 18, GolemGui.RED);
            g.fill(leftPos + 51, topPos + 58, leftPos + 168, topPos + 59, GolemGui.RED);
        }

        // Ice cooling panel & bar
        GolemGui.inset(g, leftPos + 50, topPos + 62, 119, 22);
        int tier = menu.iceTier();
        int iceColor = switch (tier) {
            case EssentiaCauldronBlockEntity.ICE_BLUE -> 0xFF4CA8F8;
            case EssentiaCauldronBlockEntity.ICE_PACKED -> 0xFF72D2F2;
            case EssentiaCauldronBlockEntity.ICE_NORMAL -> 0xFFB0EEFF;
            default -> GolemGui.RED;
        };
        GolemGui.bar(g, leftPos + 53, topPos + 74, 113, 7, tier > 0 ? menu.icePercent() / 100.0 : 0.0, iceColor);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        super.renderLabels(g, mouseX, mouseY);
        int x = 54, right = 166;
        String sourceStr = String.format(Locale.ROOT,
                menu.sourcePerSecond() == Math.rint(menu.sourcePerSecond()) ? "%.0f" : "%.1f",
                menu.sourcePerSecond());
        fitted(g, Component.translatable("gui.golem_logix.cauldron.source", sourceStr), x, 20, right - x, GolemGui.CYAN);

        // Co-processor count with icon (matching DistillationTowerScreen / CryoMixerScreen)
        g.pose().pushPose();
        g.pose().translate(x, 31, 0);
        g.pose().scale(0.625f, 0.625f, 1f);
        g.renderItem(COPROCESSOR.get(), 0, 0);
        g.pose().popPose();
        fitted(g, Component.translatable("gui.golem_logix.tower.coprocessors",
                menu.coprocessors(), UpgradeSpec.ESSENTIA_CAULDRON.maxSpeed()), x + 12, 32, right - x - 12, 0xFFA89880);

        int status = menu.status();
        int statusColor = switch (status) {
            case EssentiaCauldronBlockEntity.STATUS_WORKING -> GolemGui.GREEN;
            case EssentiaCauldronBlockEntity.STATUS_BOIL_OFF, EssentiaCauldronBlockEntity.STATUS_NO_ICE,
                 EssentiaCauldronBlockEntity.STATUS_NO_THAUMATURGE -> GolemGui.RED;
            default -> GolemGui.COPPER;
        };
        Component statusText = menu.boilOff()
                ? Component.translatable("gui.golem_logix.cauldron.boil_warn")
                : Component.translatable("gui.golem_logix.cauldron.status." + status);
        fitted(g, statusText, x, 46, right - x, statusColor);

        // Ice cooling status line above bar
        int tier = menu.iceTier();
        int tierColor = switch (tier) {
            case EssentiaCauldronBlockEntity.ICE_BLUE -> 0xFF70C8FF;
            case EssentiaCauldronBlockEntity.ICE_PACKED -> 0xFF9EE8FF;
            case EssentiaCauldronBlockEntity.ICE_NORMAL -> 0xFFD4F6FF;
            default -> GolemGui.RED;
        };
        Component iceText = tier > 0
                ? Component.translatable("gui.golem_logix.cauldron.ice." + tier, menu.icePercent())
                : Component.translatable("gui.golem_logix.cauldron.ice.0");
        fitted(g, iceText, x, 64, right - x, tierColor);
    }

    @Override
    protected void renderTooltip(GuiGraphics g, int mouseX, int mouseY) {
        super.renderTooltip(g, mouseX, mouseY);
        int ty = topPos + TANK_Y - 2;
        if (mouseY >= ty && mouseY < ty + TANK_H + 4) {
            if (mouseX >= leftPos + 8 && mouseX < leftPos + 26) {
                g.renderTooltip(font, Component.translatable("gui.golem_logix.cauldron.auram_tip",
                        menu.auram(), menu.capacity()), mouseX, mouseY);
            } else if (mouseX >= leftPos + 28 && mouseX < leftPos + 46) {
                g.renderTooltip(font, Component.translatable("gui.golem_logix.cauldron.gelum_tip",
                        menu.gelum(), menu.capacity()), mouseX, mouseY);
            }
        }
        if (mouseX >= leftPos + 50 && mouseX < leftPos + 169 && mouseY >= topPos + 62 && mouseY < topPos + 84) {
            g.renderTooltip(font, Component.translatable("gui.golem_logix.cauldron.ice_tip"), mouseX, mouseY);
        }
    }

    private void fitted(GuiGraphics g, Component text, int x, int y, int maxWidth, int color) {
        int w = font.width(text);
        if (w <= maxWidth) {
            g.drawString(font, text, x, y, color, false);
            return;
        }
        float s = maxWidth / (float) w;
        g.pose().pushPose();
        g.pose().translate(x, y + (1 - s) * 4, 0);
        g.pose().scale(s, s, 1f);
        g.drawString(font, text, 0, 0, color, false);
        g.pose().popPose();
    }
}
