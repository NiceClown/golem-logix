package com.golemlogix.client;

import com.golemlogix.blockentity.CryoMixerBlockEntity;
import com.golemlogix.init.ModItems;
import com.golemlogix.menu.CryoMixerMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.Locale;

/**
 * Cryo-Mixer window: on the left the two flasks (water blue, Cryo-Source icy cyan, filling from the bottom), on the right what the
 * mixer does (the mode, the fluids, the pace, the co-processors, the state), under it the mode switch and the progress into the
 * tray. The upgrade slots stand in a column on the right side of the window.
 */
public class CryoMixerScreen extends GolemScreen<CryoMixerMenu> {
    private static final int WATER = 0xFF2A62B4, WATER_LIGHT = 0xFF62A0E6, CRYO = 0xFF38C8D6, CRYO_LIGHT = 0xFFB8F4FA;
    private static final int TANK_Y = 18, TANK_H = 58;
    private static final ItemStack COPROCESSOR = new ItemStack(ModItems.COMPRESSED_COPROCESSOR.get());

    public CryoMixerScreen(CryoMixerMenu menu, Inventory inv, Component title) {
        super(menu, inv, title, CryoMixerMenu.IMAGE_WIDTH, CryoMixerMenu.IMAGE_HEIGHT);
    }

    @Override
    protected void init() {
        super.init();
        addRenderableWidget(new GolemButton(leftPos + 52, topPos + 63, 84, 14,
                () -> Component.translatable("gui.golem_logix.mixer.mode." + menu.mode().name().toLowerCase(Locale.ROOT)),
                () -> Component.translatable("gui.golem_logix.mixer.mode_tip"), () -> false,
                b -> minecraft.gameMode.handleInventoryButtonClick(menu.containerId, CryoMixerMenu.BUTTON_MODE)));
        // auto-output: a hopper hands the blocks out (to a Pattern Provider first), a chest keeps them in the tray
        addSideButton(() -> new ItemStack(menu.autoOutput() ? Items.HOPPER : Items.CHEST),
                () -> Component.translatable("gui.golem_logix.auto_output.title").append("\n")
                        .append(Component.translatable(menu.autoOutput() ? "gui.golem_logix.auto_output.on" : "gui.golem_logix.auto_output.off")
                                .withStyle(net.minecraft.ChatFormatting.AQUA)).append("\n")
                        .append(Component.translatable("gui.golem_logix.auto_output.hint").withStyle(net.minecraft.ChatFormatting.GRAY)),
                () -> true, menu::autoOutput, CryoMixerMenu.BUTTON_AUTO_OUTPUT);
        addRedstoneButton(menu::redstoneMode);
    }

    /** One flask: a column filling from the bottom, with marks every quarter. */
    private void flask(GuiGraphicsExtractor g, int x, int amount, int capacity, int color, int light) {
        int y = topPos + TANK_Y, w = 14;
        GolemGui.inset(g, x - 2, y - 2, w + 4, TANK_H + 4);
        int filled = (int) Math.round(TANK_H * Math.min(1.0, amount / (double) capacity));
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
    protected void renderExtraBg(GuiGraphicsExtractor g, float partialTick, int mouseX, int mouseY) {
        flask(g, leftPos + 10, menu.water(), CryoMixerBlockEntity.WATER_TANK, WATER, WATER_LIGHT);
        flask(g, leftPos + 30, menu.cryo(), CryoMixerBlockEntity.CRYO_TANK, CRYO, CRYO_LIGHT);
        GolemGui.inset(g, leftPos + 50, topPos + 16, 119, 44);
        // progress into the tray: a bar that fills while the block freezes
        int x = leftPos + 139, y = topPos + CryoMixerMenu.OUT_Y + 7, w = 9;
        g.fill(x, y, x + w, y + 3, 0xFF2A2018);
        int done = (int) Math.round(w * Math.min(1.0, menu.progress() / (double) menu.ticksPerBlock()));
        if (done > 0) {
            g.fill(x, y, x + done, y + 3, GolemGui.CYAN);
        }
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        super.extractLabels(g, mouseX, mouseY);
        int x = 54, y = 19, right = 167;
        CryoMixerBlockEntity.Mode mode = menu.mode();
        fitted(g, Component.translatable("gui.golem_logix.mixer.makes", Component.translatable(
                mode == CryoMixerBlockEntity.Mode.SNOW ? Items.SNOW_BLOCK.getDescriptionId() : Items.ICE.getDescriptionId())),
                x, y, right - x, GolemGui.PARCHMENT);
        String seconds = String.format(Locale.ROOT, "%.1f", menu.ticksPerBlock() / 20f);
        fitted(g, Component.translatable("gui.golem_logix.mixer.pace", seconds, mode.water(), mode.cryo()), x, y + 10, right - x,
                GolemGui.CYAN);
        g.pose().pushMatrix();
        g.pose().translate(x, y + 20);
        g.pose().scale(0.625f, 0.625f);
        g.item(COPROCESSOR, 0, 0);
        g.pose().popMatrix();
        fitted(g, Component.translatable("gui.golem_logix.tower.coprocessors", menu.coprocessors()), x + 12, y + 21, right - x - 12, 0xFFA89880);
        int status = menu.status();
        int color = status == CryoMixerBlockEntity.WORKING ? GolemGui.GREEN
                : status == CryoMixerBlockEntity.TRAY_FULL || status == CryoMixerBlockEntity.PAUSED ? GolemGui.COPPER : GolemGui.RED;
        fitted(g, Component.translatable("gui.golem_logix.mixer.status." + status), x, y + 31, right - x, color);
    }

    /** A line of the inset, squeezed to its width when it is too long (the ice pace, longer translations). */
    private void fitted(GuiGraphicsExtractor g, Component text, int x, int y, int maxWidth, int color) {
        int w = font.width(text);
        if (w <= maxWidth) {
            g.text(font, text, x, y, color, false);
            return;
        }
        float s = maxWidth / (float) w;
        g.pose().pushMatrix();
        g.pose().translate(x, y + (1 - s) * 4);
        g.pose().scale(s, s);
        g.text(font, text, 0, 0, color, false);
        g.pose().popMatrix();
    }

    @Override
    protected void extractTooltip(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        super.extractTooltip(g, mouseX, mouseY);
        int y = topPos + TANK_Y - 2;
        if (mouseY >= y && mouseY < y + TANK_H + 4) {
            if (mouseX >= leftPos + 8 && mouseX < leftPos + 26) {
                g.setTooltipForNextFrame(font, Component.translatable("gui.golem_logix.mixer.water_tip", menu.water(),
                        CryoMixerBlockEntity.WATER_TANK), mouseX, mouseY);
            } else if (mouseX >= leftPos + 28 && mouseX < leftPos + 46) {
                g.setTooltipForNextFrame(font, Component.translatable("gui.golem_logix.mixer.cryo_tip", menu.cryo(),
                        CryoMixerBlockEntity.CRYO_TANK), mouseX, mouseY);
            }
        }
    }
}
