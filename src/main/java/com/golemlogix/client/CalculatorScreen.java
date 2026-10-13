package com.golemlogix.client;

import com.golemlogix.crafting.CraftingJob;
import com.golemlogix.menu.CalculatorMenu;
import com.golemlogix.network.CalculatorStatusPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

public class CalculatorScreen extends GolemScreen<CalculatorMenu> {
    public CalculatorScreen(CalculatorMenu menu, Inventory inv, Component title) {
        super(menu, inv, title, CalculatorMenu.IMAGE_WIDTH, CalculatorMenu.IMAGE_HEIGHT);
    }

    private GolemButton cancel;

    @Override
    protected void init() {
        super.init();
        cancel = addRenderableWidget(new GolemButton(leftPos + 58, topPos + 112, 60, 14,
                Component.translatable("gui.golem_logix.calc.cancel"),
                b -> minecraft.gameMode.handleInventoryButtonClick(menu.containerId, 0)));
        addRedstoneButton(menu::redstoneMode);
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        // "Abort" only when there is something to abort
        cancel.visible = menu.clientStatus != null && menu.clientStatus.state() >= 0;
    }

    /** A 10x10 item icon with a number to the right; returns x after the label. */
    private int smallIcon(GuiGraphics g, ItemStack icon, int x, int y, String count, int color) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(0.625f, 0.625f, 1f);
        g.renderItem(icon, 0, 0);
        g.pose().popPose();
        g.drawString(font, count, x + 12, y + 1, color, false);
        return x + 12 + font.width(count);
    }

    @Override
    protected void renderTooltip(GuiGraphics g, int mouseX, int mouseY) {
        super.renderTooltip(g, mouseX, mouseY);
        // tooltip for the speed-up line
        if (menu.clientStatus != null && mouseX >= leftPos + 8 && mouseX < leftPos + 168 && mouseY >= topPos + 98 && mouseY < topPos + 111) {
            g.renderTooltip(font, Component.translatable("gui.golem_logix.calc.power", menu.clientStatus.coprocessors(), menu.clientStatus.workers()),
                    mouseX, mouseY);
        }
    }

    /** Job state colour (shared by the calculator and the Recipe Monitor). */
    public static int statusColor(CraftingJob.Status st) {
        return switch (st) {
            case RUNNING, DONE, DELIVERING -> GolemGui.GREEN;
            case WAITING_MACHINE -> GolemGui.CYAN;
            case PAUSED -> GolemGui.COPPER;
            default -> GolemGui.RED;
        };
    }

    public static CraftingJob.Status status(int ordinal) {
        CraftingJob.Status[] all = CraftingJob.Status.values();
        return all[Math.max(0, Math.min(ordinal, all.length - 1))];
    }

    @Override
    protected void renderExtraBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        CalculatorStatusPayload s = menu.clientStatus;
        GolemGui.inset(g, leftPos + 8, topPos + 18, 160, 78);
        // text inside a frame 160 px wide: long lines wrap or get cut
        if (s == null) {
            g.drawWordWrap(font, Component.translatable("gui.golem_logix.calc.waiting"), leftPos + 14, topPos + 24, 148, GolemGui.PARCHMENT);
            return;
        }

        int x = leftPos + 14;
        int y = topPos + 24;
        if (s.state() < 0) {
            g.drawWordWrap(font, Component.translatable("gui.golem_logix.calc.idle"), x, y, 148, GolemGui.CYAN);
        } else {
            ItemStack target = s.target();
            if (!target.isEmpty()) {
                g.renderItem(target, x, y - 3);
                String name = font.plainSubstrByWidth(s.amount() + "x " + target.getHoverName().getString(), 128);
                g.drawString(font, name, x + 20, y + 1, GolemGui.PARCHMENT, false);
            }
            CraftingJob.Status st = status(s.state());
            g.drawString(font, Component.translatable("gui.golem_logix.calc.status." + st.name().toLowerCase()), x, y + 16, statusColor(st), false);
            double frac = s.total() <= 0 ? 0 : (double) s.done() / s.total();
            GolemGui.bar(g, x, y + 28, 148, 10, frac, GolemGui.CYAN_DARK);
            g.drawCenteredString(font, s.done() + " / " + s.total(), x + 74, y + 29, GolemGui.PARCHMENT);
            if (s.awaiting() > 0) {
                g.drawString(font, font.plainSubstrByWidth(Component.translatable("gui.golem_logix.calc.awaiting", s.awaiting()).getString(), 148),
                        x, y + 41, GolemGui.PARCHMENT, false);
            }
        }
        // speed-up: icons of placed and compressed coprocessors with counts, steps per cycle on the right
        int compressed = Math.max(0, (s.workers() - com.golemlogix.blockentity.CryoCalculatorBlockEntity.BASE_WORKERS
                - com.golemlogix.blockentity.CryoCalculatorBlockEntity.WORKERS_PER_COPROCESSOR * s.coprocessors())
                / com.golemlogix.blockentity.CryoCalculatorBlockEntity.WORKERS_PER_COMPRESSED);
        int color = s.online() ? GolemGui.PARCHMENT : GolemGui.RED;
        int px = leftPos + 10, py = topPos + 100;
        px = smallIcon(g, new ItemStack(com.golemlogix.init.ModBlocks.COOLING_COPROCESSOR.get()), px, py, "" + s.coprocessors(), color);
        smallIcon(g, new ItemStack(com.golemlogix.init.ModItems.COMPRESSED_COPROCESSOR.get()), px + 6, py, "" + compressed, color);
        Component steps = Component.translatable("gui.golem_logix.calc.steps", s.workers());
        g.drawString(font, steps, leftPos + 166 - font.width(steps), py + 1, color, false);

        // the contents stay inside the frame (bottom at 96), icons keep a pixel away from it, numbers do not stick out past the edge
        int cx = leftPos + 13;
        int cy = topPos + 76;
        int i = 0;
        for (ItemStack c : s.contents()) {
            if (i >= 8) {
                break;
            }
            g.renderItem(c, cx + i * 19, cy);
            g.renderItemDecorations(font, c, cx + i * 19, cy);
            i++;
        }
    }
}
