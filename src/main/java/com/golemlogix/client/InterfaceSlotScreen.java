package com.golemlogix.client;

import com.golemlogix.blockentity.InterfaceBlockEntity;
import com.golemlogix.items.FluidGhostItem;
import com.golemlogix.menu.InterfaceMenu;
import com.golemlogix.network.InterfaceAmountPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/**
 * Settings window for one Interface slot (the gear under the slot or a click on it with an empty hand): what to keep, how much
 * (items or mB), quick step buttons and preset values, fluid/item mode, clearing. The number can also be typed:
 * for fluids in mB or in buckets with the letter B ("16B"). With a Fuzzy Card an item slot also lists matching tags.
 * Enter applies, Esc goes back without changes.
 */
public class InterfaceSlotScreen extends Screen {
    private static final int W = 176, BASE_H = 150;
    /** Where the fuzzy tag list starts (below the typing hint). */
    private static final int TAG_Y = 128;

    private static final Component LABEL_KEEP_ITEMS = Component.translatable("gui.golem_logix.interface.keep_items");
    private static final Component LABEL_AMOUNT = Component.translatable("gui.golem_logix.interface.amount");
    private static final Component LABEL_TYPE_HINT = Component.translatable("gui.golem_logix.interface.type_hint").withStyle(ChatFormatting.ITALIC);
    private static final Component TOGGLE_MODE_TIP = Component.translatable("gui.golem_logix.interface.toggle_mode");

    private final InterfaceScreen parent;
    private final InterfaceMenu menu;
    private final int slot;
    private EditBox amount;
    private int left, top, h;
    private TagPicker picker;
    /** The mode the window was opened in: when the server flips it, tick() rebuilds the window with the new steps. */
    private boolean fluid;
    private boolean initialized;
    /** Last number from the server: until the player edits it, the field follows it. */
    private int synced;

    public InterfaceSlotScreen(InterfaceScreen parent, InterfaceMenu menu, int slot) {
        super(Component.translatable("gui.golem_logix.interface.slot_title", slot + 1));
        this.parent = parent;
        this.menu = menu;
        this.slot = slot;
    }

    static String bucketText(int mb) {
        return SlotAmountHelper.bucketText(mb);
    }

    @Override
    protected void init() {
        picker = !menu.canCycleFuzzy(slot) ? null : picker != null ? picker : new TagPicker(menu, slot, menu.configItem(slot));
        h = BASE_H + (picker != null ? picker.height() + 4 : 0);
        left = (width - W) / 2;
        top = (height - h) / 2;
        if (picker != null) {
            picker.place(left + 8, top + TAG_Y, W - 16);
        }
        boolean nextFluid = menu.isFluidSlot(slot);
        boolean modeChanged = initialized && nextFluid != fluid;
        fluid = nextFluid;
        initialized = true;
        synced = menu.amount(slot);
        String initial = amount != null && !modeChanged ? amount.getValue() : Integer.toString(synced);

        amount = SlotAmountHelper.createAmountBox(font, left + 62, top + 50, 58, 7, "\\d{1,6}[bBвВ]?", initial);
        addRenderableWidget(amount);
        setInitialFocus(amount);

        int[] deltas = fluid ? SlotAmountHelper.FLUID_DELTAS : SlotAmountHelper.ITEM_DELTAS;
        for (int k = 0; k < deltas.length; k++) {
            int d = deltas[k];
            addRenderableWidget(new GolemButton(left + 8 + k * 27, top + 70, 25, 14,
                    Component.literal(SlotAmountHelper.deltaLabel(d)), b -> change(d)));
        }
        List<Integer> presets = presets();
        int pw = (160 - (presets.size() - 1) * 2) / presets.size();
        for (int k = 0; k < presets.size(); k++) {
            int p = presets.get(k);
            addRenderableWidget(new GolemButton(left + 8 + k * (pw + 2), top + 88, pw, 14,
                    Component.literal(fluid ? SlotAmountHelper.bucketText(p) : Integer.toString(p)), b -> set(p)));
        }

        ItemStack cfg = menu.configItem(slot);
        boolean container = !FluidGhostItem.is(cfg) && !InterfaceBlockEntity.containedFluid(cfg).isEmpty();
        GolemButton mode = addRenderableWidget(new GolemButton(left + 8, top + h - 24, 70, 16,
                () -> Component.translatable(menu.itemMode(slot) ? "gui.golem_logix.interface.mode_item" : "gui.golem_logix.interface.mode_fluid"),
                () -> TOGGLE_MODE_TIP, () -> false, b -> toggleMode()));
        // only containers with fluid have a mode
        mode.visible = container;
        addRenderableWidget(new GolemButton(left + 80, top + h - 24, 42, 16, SlotAmountHelper.CLEAR_LABEL, b -> {
            PacketDistributor.sendToServer(new InterfaceAmountPayload(menu.containerId, slot, -1));
            onClose();
        }));
        addRenderableWidget(new GolemButton(left + 124, top + h - 24, 44, 16, SlotAmountHelper.DONE_LABEL, b -> {
            apply();
            onClose();
        }));
    }

    /** Preset values: for fluids a bucket ... 256 buckets, for items from 1 to a stack. */
    private List<Integer> presets() {
        List<Integer> out = new ArrayList<>();
        int max = Math.max(1, menu.maxAmount(slot));
        int[] base = fluid ? new int[]{1000, 4000, 16_000, 64_000, InterfaceBlockEntity.MAX_FLUID} : new int[]{1, max / 4, max / 2, max};
        for (int v : base) {
            if (v >= 1 && v <= max && !out.contains(v)) {
                out.add(v);
            }
        }
        return out;
    }

    private int value() {
        return clamp(SlotAmountHelper.parseAmount(amount.getValue(), fluid, synced));
    }

    private int clamp(int v) {
        return Math.max(1, Math.min(Math.max(1, menu.maxAmount(slot)), v));
    }

    private void set(int v) {
        amount.setValue(Integer.toString(clamp(v)));
    }

    private void change(int delta) {
        int step = Math.abs(delta);
        int v = value();
        // the "+64" step from 1 gives 64, not 65: round numbers are handier
        int next = delta > 0 ? (v == 1 && step > 1 ? step : v + delta) : v + delta;
        set(next);
    }

    private void apply() {
        PacketDistributor.sendToServer(new InterfaceAmountPayload(menu.containerId, slot, value()));
    }

    private void toggleMode() {
        apply();
        if (minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, InterfaceMenu.BUTTON_MODE + slot);
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (menu.configItem(slot).isEmpty()) {
            onClose();
            return;
        }
        if (menu.isFluidSlot(slot) != fluid) {
            // the mode changed on the server: rebuild with the new units and step buttons
            rebuildWidgets();
            return;
        }
        int now = menu.amount(slot);
        if (now != synced) {
            if (amount.getValue().equals(Integer.toString(synced))) {
                amount.setValue(Integer.toString(now));
            }
            synced = now;
        }
    }

    @Override
    public void onClose() {
        if (minecraft != null) {
            minecraft.setScreen(parent);
        }
    }

    /** Background: the world blur (Screen.render calls it itself) and the panel, before buttons and labels so the blur does not touch them. */
    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(g, mouseX, mouseY, partialTick);
        GolemGui.panel(g, left, top, W, h);
        GolemGui.slot(g, left + 8, top + 20);
        GolemGui.inset(g, left + 58, top + 46, 66, 16);
        if (picker != null) {
            picker.background(g);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return picker != null && picker.clicked(mouseX, mouseY, button) || super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        return picker != null && picker.scrolled(sy) || super.mouseScrolled(mx, my, sx, sy);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        g.drawString(font, title, left + 8, top + 7, GolemGui.PARCHMENT, false);

        ItemStack cfg = menu.configItem(slot);
        if (!FluidIcons.drawGhost(g, font, cfg, left + 9, top + 21, false)) {
            g.renderItem(cfg.getCount() == 1 ? cfg : cfg.copyWithCount(1), left + 9, top + 21);
        }
        Language lang = Language.getInstance();
        g.drawString(font, lang.getVisualOrder(font.substrByWidth(cfg.getHoverName(), W - 38)), left + 30, top + 20, GolemGui.CYAN, false);
        Component mode = fluid
                ? Component.translatable("gui.golem_logix.interface.keep_fluid", InterfaceBlockEntity.containedFluid(cfg).getHoverName())
                : LABEL_KEEP_ITEMS;
        g.drawString(font, lang.getVisualOrder(font.substrByWidth(mode, W - 38)), left + 30, top + 30, 0xFFA89880, false);

        g.drawString(font, LABEL_AMOUNT, left + 8, top + 52, GolemGui.PARCHMENT, false);
        g.drawString(font, fluid ? SlotAmountHelper.UNIT_MB : SlotAmountHelper.UNIT_ITEMS, left + 128, top + 50, 0xFFA89880, false);

        int v = value();
        Component info = fluid
                ? Component.translatable("gui.golem_logix.interface.fluid_info",
                SlotAmountHelper.bucketText(v), SlotAmountHelper.bucketText(menu.maxAmount(slot)))
                : Component.translatable("gui.golem_logix.interface.item_info", menu.maxAmount(slot));
        g.drawString(font, lang.getVisualOrder(font.substrByWidth(info, W - 16)), left + 8, top + 106, 0xFF8A7A66, false);
        g.drawString(font, lang.getVisualOrder(font.substrByWidth(LABEL_TYPE_HINT, W - 16)), left + 8, top + 115, 0xFF6A5A48, false);

        if (picker != null) {
            picker.render(g, font, cfg, mouseX, mouseY);
        }
        if (mouseX >= left + 8 && mouseX < left + 26 && mouseY >= top + 20 && mouseY < top + 38) {
            g.renderTooltip(font, cfg, mouseX, mouseY);
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (SlotAmountHelper.isEnterKey(keyCode)) {
            apply();
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
