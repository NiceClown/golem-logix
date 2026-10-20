package com.golemlogix.client;

import com.golemlogix.blockentity.InterfaceBlockEntity;
import com.golemlogix.menu.InterfaceMenu;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Interface window. Under every occupied "keep in stock" slot there is a gear (the slot settings window: amount, volume,
 * quick buttons); containers with fluid also get a mode switch next to it: a drop (keep the fluid, the default) or a
 * square (keep the items themselves). Clicking a slot with an empty hand also opens the settings; the mouse wheel over the slots does
 * nothing. In the stock row a fluid slot shows the container and how many buckets. Items can be put into the input row by hand.
 */
public class InterfaceScreen extends GolemScreen<InterfaceMenu> {
    private static final int BOX = 8;

    public InterfaceScreen(InterfaceMenu menu, Inventory inv, Component title) {
        super(menu, inv, title, InterfaceMenu.IMAGE_WIDTH, InterfaceMenu.IMAGE_HEIGHT);
    }

    @Override
    protected void init() {
        super.init();
        addRedstoneButton(menu::redstoneMode);
    }

    private boolean hasGear(int i) {
        return !menu.configItem(i).isEmpty();
    }

    /** Whether the slot has a switch: the config holds a container with fluid. */
    private boolean hasToggle(int i) {
        // a fluid ghost is always fluid: only real containers can be kept as items
        return !com.golemlogix.items.FluidGhostItem.is(menu.configItem(i)) && !InterfaceBlockEntity.containedFluid(menu.configItem(i)).isEmpty();
    }

    private int gearX(int i) {
        return leftPos + 8 + i * 18;
    }

    private int toggleX(int i) {
        return gearX(i) + BOX;
    }

    private boolean inBox(double mx, double my, int x) {
        int y = topPos + InterfaceMenu.TOGGLE_Y;
        return mx >= x && mx < x + BOX && my >= y && my < y + BOX;
    }

    private int hoveredGear(double mx, double my) {
        for (int i = 0; i < InterfaceBlockEntity.SLOTS; i++) {
            if (hasGear(i) && inBox(mx, my, gearX(i))) {
                return i;
            }
        }
        return -1;
    }

    private int hoveredToggle(double mx, double my) {
        for (int i = 0; i < InterfaceBlockEntity.SLOTS; i++) {
            if (hasToggle(i) && inBox(mx, my, toggleX(i))) {
                return i;
            }
        }
        return -1;
    }

    /** Config slot under the cursor (number 0..8) or -1. */
    private int hoveredConfig() {
        return hoveredSlot != null && hoveredSlot.index < InterfaceBlockEntity.SLOTS ? hoveredSlot.index : -1;
    }

    private void openSlot(int i) {
        if (minecraft != null) {
            minecraft.setScreen(new InterfaceSlotScreen(this, menu, i));
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        // a middle-click or Ctrl+click on a config slot with a Fuzzy Card chooses its tag (before the settings window)
        if (fuzzyClick(button)) {
            return true;
        }
        int g = hoveredGear(mx, my);
        if (g >= 0 && button == 0) {
            openSlot(g);
            return true;
        }
        int t = hoveredToggle(mx, my);
        if (t >= 0 && button == 0) {
            menuButton(InterfaceMenu.BUTTON_MODE + t);
            return true;
        }
        int c = hoveredConfig();
        if (c >= 0 && menu.getCarried().isEmpty() && !net.minecraft.client.Minecraft.getInstance().hasShiftDown()) {
            // an empty hand on a slot opens the settings window (the click does not go to the server: there is nothing to take from a ghost slot)
            if (hasGear(c) && (button == 0 || button == 1)) {
                openSlot(c);
            }
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
        // the wheel over config slots changes nothing (and keeps scrolling mods from moving the ghosts)
        if (hoveredConfig() >= 0) {
            return true;
        }
        return super.mouseScrolled(mx, my, scrollX, scrollY);
    }

    @Override
    protected void renderExtraBg(GuiGraphicsExtractor g, float partialTick, int mouseX, int mouseY) {
        for (int y : new int[]{InterfaceMenu.CONFIG_Y, InterfaceMenu.STOCK_Y, InterfaceMenu.INBOX_Y}) {
            GolemGui.inset(g, leftPos + 6, topPos + y - 2, 164, 20);
        }
        int ty = topPos + InterfaceMenu.TOGGLE_Y;
        int gearHover = hoveredGear(mouseX, mouseY), toggleHover = hoveredToggle(mouseX, mouseY);
        for (int i = 0; i < InterfaceBlockEntity.SLOTS; i++) {
            if (hasGear(i)) {
                box(g, gearX(i), ty, gearHover == i);
                gear(g, gearX(i) + 1, ty + 1, gearHover == i ? GolemGui.CYAN : 0xFFD8DEE6);
            }
            if (hasToggle(i)) {
                int x = toggleX(i);
                box(g, x, ty, toggleHover == i);
                if (!menu.itemMode(i)) {
                    // drop: blue, with a highlight
                    g.fill(x + 3, ty + 1, x + 5, ty + 2, 0xFF3E8CE6);
                    g.fill(x + 2, ty + 2, x + 6, ty + 4, 0xFF3E8CE6);
                    g.fill(x + 1, ty + 4, x + 7, ty + 6, 0xFF2A68C8);
                    g.fill(x + 2, ty + 6, x + 6, ty + 7, 0xFF2A68C8);
                    g.fill(x + 2, ty + 4, x + 3, ty + 6, 0xFFCFE4FF);
                } else {
                    // item: the dark square of a slot
                    g.fill(x + 1, ty + 1, x + 7, ty + 7, 0xFF16222B);
                    g.fill(x + 2, ty + 2, x + 6, ty + 6, 0xFF9AA4B4);
                }
            }
        }
    }

    private static void box(GuiGraphicsExtractor g, int x, int y, boolean hover) {
        g.fill(x, y, x + BOX, y + BOX, 0xFF1A0F08);
        g.fill(x + 1, y + 1, x + BOX - 1, y + BOX - 1, hover ? GolemGui.BRASS_L : GolemGui.BRASS_DARK);
    }

    /** A 6x6 gear: teeth at the corners and sides, a hole in the middle. */
    private static void gear(GuiGraphicsExtractor g, int x, int y, int color) {
        String[] rows = {"X.XX.X", ".XXXX.", "XX..XX", "XX..XX", ".XXXX.", "X.XX.X"};
        for (int r = 0; r < rows.length; r++) {
            for (int c = 0; c < rows[r].length(); c++) {
                if (rows[r].charAt(c) == 'X') {
                    g.fill(x + c, y + r, x + c + 1, y + r + 1, color);
                }
            }
        }
    }

    @Override
    protected void renderOverlay(GuiGraphicsExtractor g, float partialTick, int mouseX, int mouseY) {
        // fluid stock in the output row: the container drawn translucent and how many buckets
        for (int i = 0; i < InterfaceBlockEntity.SLOTS; i++) {
            if (!menu.isFluidSlot(i)) {
                continue;
            }
            ItemStack icon = menu.configItem(i);
            int x = leftPos + 8 + i * 18, y = topPos + InterfaceMenu.STOCK_Y;
            int amount = menu.fluidAmount(i);
            if (amount > 0 && !FluidIcons.drawGhost(g, font, icon, x, y, false)) {
                g.item(icon.copyWithCount(1), x, y);
            }
            String text = amount >= 1000 ? InterfaceSlotScreen.bucketText(amount) : amount + "";
            small(g, text, x, y, amount > 0 ? 0xFF96F2EE : 0xFF6A7480);
        }
    }

    /** The config row labels its slots itself (how much to keep). */
    @Override
    protected boolean labelFluidGhosts() {
        return false;
    }

    /** Small label in the bottom right corner of a slot (over the item). */
    private void small(GuiGraphicsExtractor g, String text, int x, int y, int color) {
        g.pose().pushMatrix();
        g.pose().translate(0, 0);
        g.pose().scale(0.5f, 0.5f);
        g.text(font, text, (x + 17) * 2 - font.width(text), (y + 12) * 2, color, true);
        g.pose().popMatrix();
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        super.extractLabels(g, mouseX, mouseY);
        g.text(font, Component.translatable("gui.golem_logix.interface.keep"), 8, InterfaceMenu.CONFIG_Y - 11, GolemGui.CYAN, false);
        g.text(font, Component.translatable("gui.golem_logix.interface.stock"), 8, InterfaceMenu.STOCK_Y - 11, GolemGui.CYAN, false);
        g.text(font, Component.translatable("gui.golem_logix.interface.inbox"), 8, InterfaceMenu.INBOX_Y - 11, GolemGui.CYAN, false);
        // how much to keep, as a number on the config slot (the ghost itself is always 1)
        for (int i = 0; i < InterfaceBlockEntity.SLOTS; i++) {
            if (menu.configItem(i).isEmpty()) {
                continue;
            }
            int x = 8 + i * 18, y = InterfaceMenu.CONFIG_Y, n = menu.amount(i);
            if (menu.isFluidSlot(i)) {
                small(g, n >= 1000 ? InterfaceSlotScreen.bucketText(n) : n + "", x, y, 0xFF96F2EE);
            } else if (n > 1) {
                String s = Integer.toString(n);
                g.pose().pushMatrix();
                g.pose().translate(0, 0);
                g.text(font, s, x + 17 - font.width(s), y + 9, 0xFFFFFFFF, true);
                g.pose().popMatrix();
            }
        }
    }

    @Override
    protected void extractTooltip(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        int c = hoveredConfig();
        if (c >= 0 && menu.getCarried().isEmpty() && hoveredSlot.hasItem()) {
            ItemStack stack = hoveredSlot.getItem();
            List<Component> lines = new ArrayList<>(getTooltipFromContainerItem(stack));
            int n = menu.amount(c);
            lines.add(Component.translatable("gui.golem_logix.interface.keep_amount",
                    menu.isFluidSlot(c) ? n + " mB (" + InterfaceSlotScreen.bucketText(n) + ")" : Integer.toString(n)).withStyle(ChatFormatting.AQUA));
            lines.add(Component.translatable("gui.golem_logix.interface.slot_hint").withStyle(ChatFormatting.DARK_GRAY));
            g.setTooltipForNextFrame(font, lines, stack.getTooltipImage(), mouseX, mouseY);
            return;
        }
        if (c >= 0 && menu.getCarried().isEmpty()) {
            g.setComponentTooltipForNextFrame(font, List.of(Component.translatable("gui.golem_logix.interface.keep"),
                    Component.translatable("gui.golem_logix.interface.empty_hint").withStyle(ChatFormatting.GRAY)), mouseX, mouseY);
            return;
        }
        if (hoveredSlot != null && !hoveredSlot.hasItem() && menu.getCarried().isEmpty()
                && hoveredSlot.index >= 2 * InterfaceBlockEntity.SLOTS && hoveredSlot.index < 3 * InterfaceBlockEntity.SLOTS) {
            g.setComponentTooltipForNextFrame(font, List.of(Component.translatable("gui.golem_logix.interface.inbox"),
                    Component.translatable("gui.golem_logix.interface.inbox.hint").withStyle(ChatFormatting.GRAY)), mouseX, mouseY);
            return;
        }
        super.extractTooltip(g, mouseX, mouseY);
        int gi = hoveredGear(mouseX, mouseY);
        if (gi >= 0) {
            g.setComponentTooltipForNextFrame(font, List.of(Component.translatable("gui.golem_logix.interface.gear"),
                    Component.translatable("gui.golem_logix.interface.gear.hint").withStyle(ChatFormatting.GRAY)), mouseX, mouseY);
            return;
        }
        int i = hoveredToggle(mouseX, mouseY);
        if (i >= 0) {
            var fluid = InterfaceBlockEntity.containedFluid(menu.configItem(i));
            boolean fluidMode = !menu.itemMode(i);
            g.setComponentTooltipForNextFrame(font, List.of(
                    Component.translatable(fluidMode ? "gui.golem_logix.interface.mode_fluid" : "gui.golem_logix.interface.mode_item"),
                    Component.translatable(fluidMode ? "gui.golem_logix.interface.mode_fluid.hint" : "gui.golem_logix.interface.mode_item.hint",
                            fluid.getHoverName()).withStyle(ChatFormatting.GRAY),
                    Component.translatable("gui.golem_logix.interface.mode_click").withStyle(ChatFormatting.DARK_GRAY)),
                    mouseX, mouseY);
        }
    }
}
