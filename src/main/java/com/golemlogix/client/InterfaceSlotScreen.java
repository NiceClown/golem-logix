package com.golemlogix.client;

import com.golemlogix.blockentity.InterfaceBlockEntity;
import com.golemlogix.menu.InterfaceMenu;
import com.golemlogix.network.InterfaceAmountPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Settings window for one Interface slot (the gear under the slot or a click on it with an empty hand): what to keep, how much
 * (items or mB), quick step buttons and preset values, fluid/item mode, clearing. The number can also be typed:
 * for fluids in mB or in buckets with the letter B ("16B"). Enter applies, Esc goes back without changes.
 */
public class InterfaceSlotScreen extends Screen {
    private static final int W = 176, H = 150;

    private final InterfaceScreen parent;
    private final InterfaceMenu menu;
    private final int slot;
    private EditBox amount;
    private int left, top;
    /** The mode the window was opened in: if it changed, the server recomputed the number, and it is picked up a couple of ticks later. */
    private boolean fluid;
    private int reloadIn = -1;
    /** Last number from the server: until the player edits it, the field follows it. */
    private int synced;

    public InterfaceSlotScreen(InterfaceScreen parent, InterfaceMenu menu, int slot) {
        super(Component.translatable("gui.golem_logix.interface.slot_title", slot + 1));
        this.parent = parent;
        this.menu = menu;
        this.slot = slot;
    }

    @Override
    protected void init() {
        left = (width - W) / 2;
        top = (height - H) / 2;
        fluid = menu.isFluidSlot(slot);
        synced = menu.amount(slot);

        amount = new EditBox(font, left + 62, top + 50, 58, 12, Component.empty());
        amount.setBordered(false);
        amount.setMaxLength(7);
        amount.setFilter(s -> s.isEmpty() || s.matches("\\d{1,6}[bBвВ]?"));
        amount.setTextColor(GolemGui.PARCHMENT);
        amount.setValue(Integer.toString(synced));
        addRenderableWidget(amount);
        setInitialFocus(amount);

        int[] deltas = fluid ? new int[]{-10_000, -1000, -100, 100, 1000, 10_000} : new int[]{-64, -10, -1, 1, 10, 64};
        for (int k = 0; k < deltas.length; k++) {
            int d = deltas[k];
            addRenderableWidget(new GolemButton(left + 8 + k * 27, top + 70, 25, 14, Component.literal(deltaLabel(d)), b -> change(d)));
        }
        List<Integer> presets = presets();
        int pw = (160 - (presets.size() - 1) * 2) / presets.size();
        for (int k = 0; k < presets.size(); k++) {
            int p = presets.get(k);
            addRenderableWidget(new GolemButton(left + 8 + k * (pw + 2), top + 88, pw, 14, Component.literal(presetLabel(p)), b -> set(p)));
        }

        boolean container = !com.golemlogix.items.FluidGhostItem.is(menu.configItem(slot))
                && !InterfaceBlockEntity.containedFluid(menu.configItem(slot)).isEmpty();
        GolemButton mode = addRenderableWidget(new GolemButton(left + 8, top + 126, 70, 16,
                () -> Component.translatable(menu.itemMode(slot) ? "gui.golem_logix.interface.mode_item" : "gui.golem_logix.interface.mode_fluid"),
                () -> Component.translatable("gui.golem_logix.interface.toggle_mode"), () -> false, b -> toggleMode()));
        // only containers with fluid have a mode
        mode.visible = container;
        addRenderableWidget(new GolemButton(left + 80, top + 126, 42, 16, Component.translatable("gui.golem_logix.interface.clear"), b -> {
            net.neoforged.neoforge.client.network.ClientPacketDistributor.sendToServer(new InterfaceAmountPayload(menu.containerId, slot, -1));
            onClose();
        }));
        addRenderableWidget(new GolemButton(left + 124, top + 126, 44, 16, Component.translatable("gui.golem_logix.interface.done"), b -> {
            apply();
            onClose();
        }));
    }

    private static String deltaLabel(int d) {
        String sign = d > 0 ? "+" : "-";
        int a = Math.abs(d);
        return sign + (a >= 1000 && a % 1000 == 0 ? a / 1000 + "B" : Integer.toString(a));
    }

    private String presetLabel(int p) {
        return fluid ? bucketText(p) : Integer.toString(p);
    }

    static String bucketText(int mb) {
        return mb % 1000 == 0 ? mb / 1000 + "B" : String.format(Locale.ROOT, "%.1fB", mb / 1000f);
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

    /** The entered number (the letter B, Latin or Cyrillic, means buckets), within the slot's limits. */
    private int value() {
        String s = amount.getValue();
        int mult = 1;
        if (!s.isEmpty() && "bBвВ".indexOf(s.charAt(s.length() - 1)) >= 0) {
            s = s.substring(0, s.length() - 1);
            mult = fluid ? 1000 : 1;
        }
        try {
            return clamp((int) Math.min(Integer.MAX_VALUE, Long.parseLong(s) * mult));
        } catch (NumberFormatException e) {
            return clamp(synced);
        }
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
        net.neoforged.neoforge.client.network.ClientPacketDistributor.sendToServer(new InterfaceAmountPayload(menu.containerId, slot, value()));
    }

    private void toggleMode() {
        apply();
        if (minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, InterfaceMenu.BUTTON_MODE + slot);
        }
        reloadIn = 4;
    }

    @Override
    public void tick() {
        super.tick();
        if (menu.configItem(slot).isEmpty()) {
            onClose();
            return;
        }
        if (reloadIn > 0 && --reloadIn == 0) {
            // the mode changed: the server converted the number into the new units, so the window is rebuilt (the step buttons differ)
            reloadIn = -1;
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
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(g, mouseX, mouseY, partialTick);
        GolemGui.panel(g, left, top, W, H);
        GolemGui.slot(g, left + 8, top + 20);
        GolemGui.inset(g, left + 58, top + 46, 66, 16);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(g, mouseX, mouseY, partialTick);
        g.text(font, title, left + 8, top + 7, GolemGui.PARCHMENT, false);

        ItemStack cfg = menu.configItem(slot);
        if (!FluidIcons.drawGhost(g, font, cfg, left + 9, top + 21, false)) {
            g.item(cfg.copyWithCount(1), left + 9, top + 21);
        }
        g.text(font, cfg.getHoverName(), left + 30, top + 20, GolemGui.CYAN, false);
        var contained = InterfaceBlockEntity.containedFluid(cfg);
        Component mode = fluid
                ? Component.translatable("gui.golem_logix.interface.keep_fluid", contained.getHoverName())
                : Component.translatable("gui.golem_logix.interface.keep_items");
        g.text(font, mode, left + 30, top + 30, 0xFFA89880, false);

        g.text(font, Component.translatable("gui.golem_logix.interface.amount"), left + 8, top + 52, GolemGui.PARCHMENT, false);
        g.text(font, Component.translatable(fluid ? "gui.golem_logix.interface.unit_mb" : "gui.golem_logix.interface.unit_items"),
                left + 128, top + 50, 0xFFA89880, false);

        int v = value();
        Component info = fluid
                ? Component.translatable("gui.golem_logix.interface.fluid_info", bucketText(v), bucketText(menu.maxAmount(slot)))
                : Component.translatable("gui.golem_logix.interface.item_info", menu.maxAmount(slot));
        g.text(font, info, left + 8, top + 106, 0xFF8A7A66, false);
        g.text(font, Component.translatable("gui.golem_logix.interface.type_hint").withStyle(ChatFormatting.ITALIC),
                left + 8, top + 115, 0xFF6A5A48, false);

        if (mouseX >= left + 8 && mouseX < left + 26 && mouseY >= top + 20 && mouseY < top + 38) {
            g.setTooltipForNextFrame(font, cfg, mouseX, mouseY);
        }
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        int keyCode = event.key(), scanCode = event.scancode(), modifiers = event.modifiers();
        if (keyCode == 257 || keyCode == 335) {
            apply();
            onClose();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
