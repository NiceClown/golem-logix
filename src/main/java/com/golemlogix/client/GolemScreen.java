package com.golemlogix.client;

import com.golemlogix.menu.GolemMenu;
import com.golemlogix.menu.Priority;
import com.golemlogix.upgrade.RedstoneMode;
import com.golemlogix.upgrade.UpgradeType;
import net.minecraft.client.gui.GuiGraphicsExtractor;
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
        super(menu, inv, title, width, height);
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
    protected boolean hasClickedOutside(double mouseX, double mouseY, int guiLeft, int guiTop) {
        for (Rect2i r : extraAreas()) {
            if (r.contains((int) mouseX, (int) mouseY)) {
                return false;
            }
        }
        return super.hasClickedOutside(mouseX, mouseY, guiLeft, guiTop);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(g, mouseX, mouseY, partialTick);
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
            // empty upgrade slots: a translucent "projection" of the one kind of upgrade the slot takes
            List<ItemStack> hints = upgradeHints(gm.upgradeSpec());
            for (int i = gm.upgradeStart(); i < gm.upgradeEnd(); i++) {
                Slot s = menu.slots.get(i);
                int k = i - gm.upgradeStart();
                if (!s.hasItem() && k < hints.size() && !hints.get(k).isEmpty()) {
                    int x = leftPos + s.x, y = topPos + s.y;
                    g.item(hints.get(k), x, y);
                    g.pose().pushMatrix();
                    g.pose().translate(0, 0);
                    g.fill(x, y, x + 16, y + 16, 0xA8241A12);
                    g.pose().popMatrix();
                }
            }
        }
        renderOverlay(g, partialTick, mouseX, mouseY);
    }

    /** The ghost of each upgrade slot: the kind of upgrade it is for (the Plasma Temper's only once its secret is known). */
    private static List<ItemStack> upgradeHints(com.golemlogix.upgrade.UpgradeSpec spec) {
        List<ItemStack> out = new ArrayList<>();
        for (int i = 0; i < spec.slots(); i++) {
            UpgradeType t = spec.typeAt(i);
            out.add(t == null ? ItemStack.EMPTY : new ItemStack(switch (t) {
                case SPEED -> com.golemlogix.init.ModItems.COMPRESSED_COPROCESSOR.get();
                case CRAFTING -> com.golemlogix.init.ModItems.AUTOCRAFT_MODULE.get();
                case REDSTONE -> com.golemlogix.init.ModItems.REDSTONE_MODULE.get();
                case FUZZY -> com.golemlogix.init.ModItems.FUZZY_CARD.get();
                case TEMPER -> ClientSecrets.all() ? com.golemlogix.init.ModItems.PLASMA_TEMPER.get() : net.minecraft.world.item.Items.AIR;
            }));
        }
        return out;
    }

    /** Drawn UNDER the slot frames (insets, background elements). */
    protected void renderExtraBg(GuiGraphicsExtractor g, float partialTick, int mouseX, int mouseY) {
    }

    /** Drawn ON TOP of the slot frames, but under the items. */
    protected void renderOverlay(GuiGraphicsExtractor g, float partialTick, int mouseX, int mouseY) {
    }

    /** Fluid ghosts in ghost slots are drawn as their fluid; their mB label unless the screen labels slots itself. */
    @Override
    protected void extractSlot(GuiGraphicsExtractor g, net.minecraft.world.inventory.Slot slot, int mouseX, int mouseY) {
        if (FluidIcons.drawGhost(g, font, slot.getItem(), slot.x, slot.y, labelFluidGhosts())) {
            return;
        }
        super.extractSlot(g, slot, mouseX, mouseY);
        // a fuzzy ghost: "#" with a tag chosen, "≈" for the same item with any components
        if (menu instanceof com.golemlogix.menu.GolemMenu gm && slot.hasItem() && gm.fuzzyActive(slot.index)) {
            String mark = com.golemlogix.system.Fuzzy.tag(slot.getItem()) != null ? "#" : "≈";
            g.pose().pushMatrix();
            g.pose().translate(slot.x + 10.5f, slot.y - 1f);
            g.pose().scale(0.75f, 0.75f);
            g.text(font, mark, 0, 0, GolemGui.CYAN, true);
            g.pose().popMatrix();
        }
    }

    /**
     * The filter slot window of a bus: the screens of buses say which ghost slots are filters (a middle-click on a filled one opens
     * FilterSlotScreen); -1 elsewhere.
     */
    protected int filterSlots() {
        return -1;
    }

    /** Whether the bus window can set a keep amount in its slot windows (the Export Bus). */
    protected boolean keepsAmounts() {
        return false;
    }

    /** The ghost filter slot under the point (screen coordinates), or -1. */
    protected int filterSlotAt(double x, double y) {
        for (int i = 0; i < filterSlots() && i < menu.slots.size(); i++) {
            var slot = menu.getSlot(i);
            if (x >= leftPos + slot.x - 1 && x < leftPos + slot.x + 17 && y >= topPos + slot.y - 1 && y < topPos + slot.y + 17) {
                return i;
            }
        }
        return -1;
    }

    /** A middle-click on a filled bus filter slot opens its settings window. True if it did. */
    protected boolean slotWindowClick(double x, double y, int button) {
        int i = button == 2 ? filterSlotAt(x, y) : -1;
        if (i >= 0 && menu.getSlot(i).hasItem() && minecraft != null && menu instanceof com.golemlogix.menu.GolemMenu gm) {
            minecraft.setScreen(new FilterSlotScreen(this, gm, i, keepsAmounts()));
            return true;
        }
        return false;
    }

    /** A middle-click (or Ctrl+left-click) on a fuzzy ghost slot asks the server for its next tag. True if it was such a click. */
    protected boolean fuzzyClick(int button) {
        if (menu instanceof com.golemlogix.menu.GolemMenu gm && hoveredSlot != null && hoveredSlot.hasItem()
                && (button == 2 || (button == 0 && net.minecraft.client.Minecraft.getInstance().hasControlDown())) && gm.canCycleFuzzy(hoveredSlot.index)) {
            net.neoforged.neoforge.client.network.ClientPacketDistributor.sendToServer(
                    new com.golemlogix.network.FuzzyCyclePayload(menu.containerId, hoveredSlot.index));
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
        double mouseX = event.x(), mouseY = event.y();
        int button = event.button();
        return slotWindowClick(mouseX, mouseY, button) || fuzzyClick(button) || super.mouseClicked(event, doubleClick);
    }

    /** The item tooltip of a fuzzy ghost slot says what it takes, and how to change it. */
    @Override
    protected List<Component> getTooltipFromContainerItem(ItemStack stack) {
        List<Component> lines = super.getTooltipFromContainerItem(stack);
        if (menu instanceof com.golemlogix.menu.GolemMenu gm && hoveredSlot != null && hoveredSlot.getItem() == stack) {
            Component line = gm.fuzzyLine(hoveredSlot.index);
            if (line != null) {
                lines = new java.util.ArrayList<>(lines);
                lines.add(line);
                if (gm.canCycleFuzzy(hoveredSlot.index)) {
                    lines.add(Component.translatable(hoveredSlot.index < filterSlots() ? "tooltip.golem_logix.fuzzy.hint_bus"
                            : "tooltip.golem_logix.fuzzy.hint").withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
                }
            }
            if (hoveredSlot.index < filterSlots() && !gm.canCycleFuzzy(hoveredSlot.index)) {
                lines = new java.util.ArrayList<>(lines);
                lines.add(Component.translatable("tooltip.golem_logix.slot_window_hint").withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
            }
        }
        return lines;
    }

    protected boolean labelFluidGhosts() {
        return true;
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        g.text(font, title, titleLabelX, titleLabelY, GolemGui.PARCHMENT, false);
        g.text(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, GolemGui.PARCHMENT, false);
        if (priorityValue != null) {
            Component label = Component.translatable("gui.golem_logix.priority");
            g.text(font, label, priorityX + (54 - font.width(label)) / 2, priorityY, GolemGui.PARCHMENT, false);
            String v = Integer.toString(priorityValue.getAsInt());
            g.text(font, v, priorityX + (54 - font.width(v)) / 2, priorityY + 11, GolemGui.CYAN, false);
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        updateSideButtons();
        super.extractRenderState(g, mouseX, mouseY, partialTick);
    }
}
