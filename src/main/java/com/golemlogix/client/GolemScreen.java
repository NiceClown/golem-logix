package com.golemlogix.client;

import com.golemlogix.menu.GolemMenu;
import com.golemlogix.menu.Priority;
import com.golemlogix.upgrade.RedstoneMode;
import com.golemlogix.upgrade.UpgradeType;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * Base of the mod's screens: panel, background of all slots, labels; the upgrade column to the right of the window and settings buttons on the left.
 */
public abstract class GolemScreen<T extends AbstractContainerMenu> extends AbstractContainerScreen<T> {
    protected GolemScreen(T menu, Inventory inv, Component title, int width, int height) {
        super(menu, inv, title);
        this.imageWidth = width;
        this.imageHeight = height;
    }

    private IntSupplier priorityValue = null;
    private int priorityX, priorityY;
    private int sideButtons = 0;
    protected final List<IconButton> sideButtonWidgets = new ArrayList<>();

    /**
     * Priority panel (label, number, buttons -10 -1 +1 +10 and reset). Coordinates are from the top left corner of the window;
     * width 54. Call from init() after super.init(). The action goes to the server as a menu button press.
     */
    protected void addPriorityPanel(int x, int y, IntSupplier value) {
        priorityValue = value;
        priorityX = x;
        priorityY = y;
        int bx = leftPos + x, by = topPos + y;
        addRenderableWidget(new GolemButton(bx, by + 22, 26, 14, Component.literal("-1"), b -> menuButton(Priority.BUTTON_MINUS_1)));
        addRenderableWidget(new GolemButton(bx + 28, by + 22, 26, 14, Component.literal("+1"), b -> menuButton(Priority.BUTTON_PLUS_1)));
        addRenderableWidget(new GolemButton(bx, by + 38, 26, 14, Component.literal("-10"), b -> menuButton(Priority.BUTTON_MINUS_10)));
        addRenderableWidget(new GolemButton(bx + 28, by + 38, 26, 14, Component.literal("+10"), b -> menuButton(Priority.BUTTON_PLUS_10)));
        addRenderableWidget(new GolemButton(bx, by + 54, 54, 12, () -> Component.translatable("gui.golem_logix.priority.reset"),
                () -> Component.translatable("gui.golem_logix.priority.tooltip"), () -> false, b -> menuButton(Priority.BUTTON_RESET)));
    }

    protected void menuButton(int id) {
        if (minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
        }
    }

    /** A settings button in the column to the left of the window (top to bottom). */
    protected void addSideButton(Supplier<ItemStack> icon, Supplier<Component> tip, BooleanSupplier visible, BooleanSupplier lit, int menuButtonId) {
        IconButton button = new IconButton(leftPos - 21, topPos + 4 + sideButtons * 20, icon, tip, visible, lit, b -> menuButton(menuButtonId));
        sideButtonWidgets.add(button);
        addRenderableWidget(button);
        updateSideButtons();
    }

    /** Redstone mode button: visible only when a Redstone Module sits in the upgrade slots. */
    protected void addRedstoneButton(Supplier<RedstoneMode> mode) {
        addSideButton(() -> new ItemStack(switch (mode.get()) {
                    case IGNORE -> Items.REDSTONE;
                    case HIGH -> Items.REDSTONE_TORCH;
                    case LOW -> Items.LEVER;
                    case PULSE -> Items.REPEATER;
                }),
                () -> Component.translatable("gui.golem_logix.redstone.title").append("\n")
                        .append(Component.translatable(mode.get().key())).append("\n")
                        .append(Component.translatable(mode.get().key() + ".hint").withStyle(net.minecraft.ChatFormatting.GRAY)),
                () -> menu instanceof GolemMenu gm && gm.hasUpgrade(UpgradeType.REDSTONE),
                () -> mode.get() != RedstoneMode.IGNORE, GolemMenu.BUTTON_REDSTONE);
    }

    @Override
    protected void init() {
        super.init();
        this.sideButtonWidgets.clear();
        this.sideButtons = 0;
        this.titleLabelX = 8;
        this.titleLabelY = 6;
        this.inventoryLabelX = 8;
        this.inventoryLabelY = this.imageHeight - 94;
    }

    /** Recomputes visibility and packs the side buttons vertically without gaps. */
    protected void updateSideButtons() {
        int count = 0;
        for (IconButton b : sideButtonWidgets) {
            if (b.updateVisibility()) {
                b.setX(leftPos - 21);
                b.setY(topPos + 4 + count * 20);
                count++;
            }
        }
        this.sideButtons = count;
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        updateSideButtons();
    }

    /** The upgrade column (to the right of the window), if the menu has one: x, y, width, height in screen coordinates. */
    protected Rect2i upgradePanel() {
        if (!(menu instanceof GolemMenu gm) || !gm.hasUpgradeSlots()) {
            return null;
        }
        Slot first = menu.slots.get(gm.upgradeStart());
        int n = gm.upgradeEnd() - gm.upgradeStart();
        return new Rect2i(leftPos + first.x - 5, topPos + first.y - 5, GolemMenu.UPGRADE_COLUMN, n * 18 + 8);
    }

    /** Areas outside the window that belong to it (upgrade column, buttons on the left): JEI avoids them, and a click on them is not "outside the window". */
    public List<Rect2i> extraAreas() {
        List<Rect2i> out = new ArrayList<>();
        Rect2i up = upgradePanel();
        if (up != null) {
            out.add(up);
        }
        if (sideButtons > 0) {
            out.add(new Rect2i(leftPos - 23, topPos + 2, 23, sideButtons * 20 + 2));
        }
        return out;
    }

    @Override
    protected boolean hasClickedOutside(double mouseX, double mouseY, int guiLeft, int guiTop, int button) {
        for (Rect2i r : extraAreas()) {
            if (r.contains((int) mouseX, (int) mouseY)) {
                return false;
            }
        }
        return super.hasClickedOutside(mouseX, mouseY, guiLeft, guiTop, button);
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        GolemGui.panel(g, leftPos, topPos, imageWidth, imageHeight);
        Rect2i up = upgradePanel();
        if (up != null) {
            GolemGui.panel(g, up.getX(), up.getY(), up.getWidth(), up.getHeight());
        }
        renderExtraBg(g, partialTick, mouseX, mouseY);
        for (Slot slot : menu.slots) {
            if (slot.isActive()) {
                GolemGui.slot(g, leftPos + slot.x - 1, topPos + slot.y - 1);
            }
        }
        if (up != null && menu instanceof GolemMenu gm && gm.upgradeSpec() != null) {
            // empty upgrade slots: a translucent "projection" of what can go here (one per slot:
            // speed upgrades up to the machine's limit first, then the autocraft module and the redstone module)
            List<ItemStack> hints = upgradeHints(gm.upgradeSpec());
            for (int i = gm.upgradeStart(); i < gm.upgradeEnd(); i++) {
                Slot s = menu.slots.get(i);
                int k = i - gm.upgradeStart();
                if (!s.hasItem() && k < hints.size()) {
                    int x = leftPos + s.x, y = topPos + s.y;
                    g.renderItem(hints.get(k), x, y);
                    g.pose().pushPose();
                    g.pose().translate(0, 0, 300);
                    g.fill(x, y, x + 16, y + 16, 0xA8241A12);
                    g.pose().popPose();
                }
            }
        }
        renderOverlay(g, partialTick, mouseX, mouseY);
    }

    /** Hints for the upgrade slots in order: what the machine accepts and how many of each. */
    private static List<ItemStack> upgradeHints(com.golemlogix.upgrade.UpgradeSpec spec) {
        List<ItemStack> out = new ArrayList<>();
        for (int i = 0; i < spec.max(UpgradeType.SPEED); i++) {
            out.add(new ItemStack(com.golemlogix.init.ModItems.COMPRESSED_COPROCESSOR.get()));
        }
        if (spec.max(UpgradeType.CRAFTING) > 0) {
            out.add(new ItemStack(com.golemlogix.init.ModItems.AUTOCRAFT_MODULE.get()));
        }
        if (spec.max(UpgradeType.REDSTONE) > 0) {
            out.add(new ItemStack(com.golemlogix.init.ModItems.REDSTONE_MODULE.get()));
        }
        return out;
    }

    /** Drawn UNDER the slot frames (insets, background elements). */
    protected void renderExtraBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
    }

    /** Drawn ON TOP of the slot frames, but under the items. */
    protected void renderOverlay(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
    }

    /** Fluid ghosts in ghost slots are drawn as their fluid; their mB label unless the screen labels slots itself. */
    @Override
    protected void renderSlot(GuiGraphics g, net.minecraft.world.inventory.Slot slot) {
        if (FluidIcons.drawGhost(g, font, slot.getItem(), slot.x, slot.y, labelFluidGhosts())) {
            return;
        }
        super.renderSlot(g, slot);
    }

    protected boolean labelFluidGhosts() {
        return true;
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(font, title, titleLabelX, titleLabelY, GolemGui.PARCHMENT, false);
        g.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, GolemGui.PARCHMENT, false);
        if (priorityValue != null) {
            Component label = Component.translatable("gui.golem_logix.priority");
            g.drawString(font, label, priorityX + (54 - font.width(label)) / 2, priorityY, GolemGui.PARCHMENT, false);
            String v = Integer.toString(priorityValue.getAsInt());
            g.drawString(font, v, priorityX + (54 - font.width(v)) / 2, priorityY + 11, GolemGui.CYAN, false);
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        updateSideButtons();
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);
    }
}
