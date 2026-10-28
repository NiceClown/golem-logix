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
 * Interface's gear opens its own: the item; with a Fuzzy Card a list of what may stand for it (this item with any components, or
 * any item of one of its tags, with how many items each tag holds) - one click picks; clearing the slot; on the Export
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
    /** The fuzzy choices: null (this item, any components), then the item's tags; the first row shown. */
    private java.util.List<net.minecraft.tags.TagKey<net.minecraft.world.item.Item>> tags = java.util.List.of();
    private int scroll;
    private static final int ROW = 12, VISIBLE = 6;

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

    /** The rows: the item (top 20), the keep amount (46..102, the Export Bus), the tag list (with a Fuzzy Card). */
    private int tagY() {
        return keep ? 116 : 46;
    }

    private int rows() {
        return Math.min(VISIBLE, tags.size() + 1);
    }

    private int listY() {
        return top + tagY() + 12;
    }

    /** The choice under the mouse: -1 for none, 0 for "any components", k for the tag k - 1. */
    private int choiceAt(double mx, double my) {
        if (!fuzzy() || mx < left + 8 || mx >= left + W - 8 || my < listY() || my >= listY() + rows() * ROW) {
            return -1;
        }
        return scroll + (int) ((my - listY()) / ROW);
    }

    @Override
    protected void init() {
        tags = fuzzy() ? com.golemlogix.system.Fuzzy.tagsOf(filter()) : java.util.List.of();
        scroll = Math.max(0, Math.min(scroll, tags.size() + 1 - rows()));
        h = tagY() + (fuzzy() ? 14 + rows() * ROW + 4 : 4) + 24;
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
        if (fuzzy()) {
            GolemGui.inset(g, left + 7, listY() - 1, W - 14, rows() * ROW + 2);
        }
    }

    /** A tag's name: its translation where a mod gives one (c:glass_blocks: "Glass Blocks"), otherwise its id. */
    private static Component tagName(net.minecraft.tags.TagKey<net.minecraft.world.item.Item> t) {
        var id = t.location();
        return Component.translatableWithFallback("tag.item." + id.getNamespace() + "." + id.getPath().replace('/', '.'), "#" + id);
    }

    private void extractTagList(GuiGraphicsExtractor g, ItemStack cfg, int mouseX, int mouseY) {
        g.text(font, Component.translatable("gui.golem_logix.bus.tag"), left + 8, top + tagY(), GolemGui.PARCHMENT, false);
        var cur = Fuzzy.tag(cfg);
        int hover = choiceAt(mouseX, mouseY);
        for (int r = 0; r < rows(); r++) {
            int k = scroll + r, y = listY() + r * ROW;
            var t = k == 0 ? null : tags.get(k - 1);
            boolean chosen = java.util.Objects.equals(t, cur);
            if (chosen) {
                g.fill(left + 8, y, left + W - 8, y + ROW, 0x6038B0A8);
            } else if (k == hover) {
                g.fill(left + 8, y, left + W - 8, y + ROW, 0x30FFFFFF);
            }
            Component name = t == null ? Component.translatable("gui.golem_logix.bus.tag_any") : tagName(t);
            String n = t == null ? "" : Integer.toString(Fuzzy.tagSize(t));
            int nw = font.width(n);
            g.text(font, net.minecraft.locale.Language.getInstance().getVisualOrder(font.substrByWidth(name, W - 26 - nw)), left + 11, y + 2,
                    chosen ? GolemGui.CYAN : GolemGui.PARCHMENT, false);
            g.text(font, n, left + W - 11 - nw, y + 2, 0xFF8A7A66, false);
        }
        if (tags.size() + 1 > VISIBLE) {
            // a thin scrollbar on the list's right edge
            int total = tags.size() + 1, barH = Math.max(6, rows() * ROW * VISIBLE / total);
            int barY = listY() + (rows() * ROW - barH) * scroll / (total - VISIBLE);
            g.fill(left + W - 9, barY, left + W - 8, barY + barH, 0xFFA89880);
        }
        if (hover >= 1) {
            var t = tags.get(hover - 1);
            g.setComponentTooltipForNextFrame(font, java.util.List.of(tagName(t), Component.literal("#" + t.location()).withStyle(net.minecraft.ChatFormatting.DARK_GRAY),
                    Component.translatable("gui.golem_logix.bus.tag_items", Fuzzy.tagSize(t)).withStyle(net.minecraft.ChatFormatting.GRAY)), mouseX, mouseY);
        } else if (hover == 0) {
            g.setTooltipForNextFrame(font, Component.translatable("gui.golem_logix.bus.tag_any.hint"), mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
        int k = event.button() == 0 ? choiceAt(event.x(), event.y()) : -1;
        if (k >= 0) {
            String id = k == 0 ? "" : tags.get(k - 1).location().toString();
            ClientPacketDistributor.sendToServer(new com.golemlogix.network.FuzzyTagPayload(menu.containerId, slot, id));
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        if (fuzzy() && tags.size() + 1 > VISIBLE && sy != 0) {
            scroll = Math.max(0, Math.min(tags.size() + 1 - VISIBLE, scroll - (int) Math.signum(sy)));
            return true;
        }
        return super.mouseScrolled(mx, my, sx, sy);
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
            extractTagList(g, cfg, mouseX, mouseY);
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
