package com.golemlogix.client;

import com.golemlogix.items.FluidGhostItem;
import com.golemlogix.menu.BusMenu;
import com.golemlogix.menu.GolemMenu;
import com.golemlogix.network.FilterSlotPayload;
import com.golemlogix.system.Fuzzy;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * Settings window for one bus filter slot - the same in every bus, opened by a middle-click (the mouse wheel) on the slot, as the
 * Interface's gear opens its own: the item, its tag with a Fuzzy Card (the next tag at a click), clearing the slot; on the Export
 * Bus also how much the container in front should hold (the bus tops it up to that and no further; 0: no limit), typed or with
 * step and preset buttons, for a fluid in mB or in buckets ("16B"). Enter applies, Esc goes back.
 */
public class FilterSlotScreen extends Screen {
    private static final int W = 176;

    private final Screen parent;
    private final GolemMenu menu;
    private final int slot;
    private final boolean keep;
    private EditBox amount;
    private int left, top, h;
    private boolean fluid;

    public FilterSlotScreen(Screen parent, GolemMenu menu, int slot, boolean keep) {
        super(Component.translatable("gui.golem_logix.bus.slot_title", slot + 1));
        this.parent = parent;
        this.menu = menu;
        this.slot = slot;
        this.keep = keep;
    }

    private ItemStack filter() {
        return menu.getSlot(slot).getItem();
    }

    private boolean fuzzy() {
        return menu.canCycleFuzzy(slot) && !FluidGhostItem.is(filter());
    }

    /** The rows: the item (top 20), the keep amount (46..102, the Export Bus), the tag (a row of its own with a Fuzzy Card). */
    private int tagY() {
        return keep ? 116 : 46;
    }

    @Override
    protected void init() {
        h = tagY() + (fuzzy() ? 28 : 4) + 24;
        left = (width - W) / 2;
        top = (height - h) / 2;
        ItemStack f = filter();
        fluid = FluidGhostItem.is(f) || net.neoforged.neoforge.fluids.FluidUtil.getFluidContained(f).filter(x -> !x.isEmpty()).isPresent();
        if (keep) {
            amount = new EditBox(font, left + 62, top + 50, 58, 12, Component.empty());
            amount.setBordered(false);
            amount.setMaxLength(8);
            amount.setFilter(s -> s.isEmpty() || s.matches("\\d{1,7}[bBвВ]?"));
            amount.setTextColor(GolemGui.PARCHMENT);
            amount.setValue(Integer.toString(BusMenu.keep(f)));
            addRenderableWidget(amount);
            setInitialFocus(amount);
            int[] deltas = fluid ? new int[]{-10_000, -1000, -100, 100, 1000, 10_000} : new int[]{-64, -10, -1, 1, 10, 64};
            for (int k = 0; k < deltas.length; k++) {
                int d = deltas[k];
                addRenderableWidget(new GolemButton(left + 8 + k * 27, top + 70, 25, 14, Component.literal(deltaLabel(d)), b -> set(value() + d)));
            }
            int stack = Math.max(1, f.getMaxStackSize());
            int[] presets = fluid ? new int[]{0, 1000, 4000, 16_000, 64_000} : new int[]{0, Math.max(1, stack / 4), stack, stack * 4, stack * 16};
            int pw = (160 - (presets.length - 1) * 2) / presets.length;
            for (int k = 0; k < presets.length; k++) {
                int p = presets[k];
                Component label = p == 0 ? Component.translatable("gui.golem_logix.bus.keep_none")
                        : Component.literal(fluid ? InterfaceSlotScreen.bucketText(p) : Integer.toString(p));
                addRenderableWidget(new GolemButton(left + 8 + k * (pw + 2), top + 88, pw, 14, label, b -> set(p)));
            }
        }
        if (fuzzy()) {
            addRenderableWidget(new GolemButton(left + 108, top + tagY() - 3, 60, 14, Component.translatable("gui.golem_logix.bus.next_tag"),
                    b -> ClientPacketDistributor.sendToServer(new com.golemlogix.network.FuzzyCyclePayload(menu.containerId, slot))));
        }
        addRenderableWidget(new GolemButton(left + 80, top + h - 22, 42, 16, Component.translatable("gui.golem_logix.interface.clear"), b -> {
            ClientPacketDistributor.sendToServer(new FilterSlotPayload(menu.containerId, slot, -1));
            onClose();
        }));
        addRenderableWidget(new GolemButton(left + 124, top + h - 22, 44, 16, Component.translatable("gui.golem_logix.interface.done"), b -> {
            apply();
            onClose();
        }));
    }

    private static String deltaLabel(int d) {
        String sign = d > 0 ? "+" : "-";
        int a = Math.abs(d);
        return sign + (a >= 1000 && a % 1000 == 0 ? a / 1000 + "B" : Integer.toString(a));
    }

    /** The entered number (the letter B, Latin or Cyrillic, means buckets for a fluid), never below 0. */
    private int value() {
        String s = amount.getValue();
        int mult = 1;
        if (!s.isEmpty() && "bBвВ".indexOf(s.charAt(s.length() - 1)) >= 0) {
            s = s.substring(0, s.length() - 1);
            mult = fluid ? 1000 : 1;
        }
        try {
            return (int) Math.max(0, Math.min(Integer.MAX_VALUE, Long.parseLong(s) * mult));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private void set(int v) {
        amount.setValue(Integer.toString(Math.max(0, v)));
    }

    private void apply() {
        if (keep) {
            ClientPacketDistributor.sendToServer(new FilterSlotPayload(menu.containerId, slot, value()));
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (filter().isEmpty()) {
            onClose();
        }
    }

    @Override
    public void onClose() {
        if (minecraft != null) {
            minecraft.setScreen(parent);
        }
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(g, mouseX, mouseY, partialTick);
        GolemGui.panel(g, left, top, W, h);
        GolemGui.slot(g, left + 8, top + 20);
        if (keep) {
            GolemGui.inset(g, left + 58, top + 46, 66, 16);
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(g, mouseX, mouseY, partialTick);
        g.text(font, title, left + 8, top + 7, GolemGui.PARCHMENT, false);
        ItemStack cfg = filter();
        if (!FluidIcons.drawGhost(g, font, cfg, left + 9, top + 21, false)) {
            g.item(Fuzzy.plain(cfg).copyWithCount(1), left + 9, top + 21);
        }
        g.text(font, Fuzzy.plain(cfg).getHoverName(), left + 30, top + 20, GolemGui.CYAN, false);
        g.text(font, Component.translatable(keep ? "gui.golem_logix.bus.keep_what" : "gui.golem_logix.bus.slot_what"), left + 30, top + 30,
                0xFFA89880, false);
        if (keep) {
            g.text(font, Component.translatable("gui.golem_logix.bus.keep"), left + 8, top + 52, GolemGui.PARCHMENT, false);
            g.text(font, Component.translatable(fluid ? "gui.golem_logix.interface.unit_mb" : "gui.golem_logix.interface.unit_items"),
                    left + 128, top + 50, 0xFFA89880, false);
            int v = value();
            Component info = v == 0 ? Component.translatable("gui.golem_logix.bus.keep_info_none")
                    : Component.translatable("gui.golem_logix.bus.keep_info", fluid ? InterfaceSlotScreen.bucketText(v) : Integer.toString(v));
            g.text(font, info, left + 8, top + 106, 0xFF8A7A66, false);
        }
        if (fuzzy()) {
            var t = Fuzzy.tag(cfg);
            g.text(font, Component.translatable("gui.golem_logix.bus.tag"), left + 8, top + tagY(), GolemGui.PARCHMENT, false);
            Component what = t == null ? Component.translatable("tooltip.golem_logix.fuzzy.any_components")
                    : Component.literal("#" + t.location());
            g.text(font, net.minecraft.locale.Language.getInstance().getVisualOrder(font.substrByWidth(what, W - 16)), left + 8,
                    top + tagY() + 14, GolemGui.CYAN, false);
        }
        if (mouseX >= left + 8 && mouseX < left + 26 && mouseY >= top + 20 && mouseY < top + 38) {
            g.setTooltipForNextFrame(font, cfg, mouseX, mouseY);
        }
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        int keyCode = event.key();
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
