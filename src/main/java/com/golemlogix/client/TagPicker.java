package com.golemlogix.client;

import com.golemlogix.menu.GolemMenu;
import com.golemlogix.network.FuzzyTagPayload;
import com.golemlogix.system.Fuzzy;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;
import java.util.Objects;

/**
 * The fuzzy tag list of a slot window (a bus slot, an Interface slot) with a Fuzzy Card: what may stand for the ghost - the item
 * itself with any components, or any item of one of its tags, each with its name and how many items it holds. One click picks,
 * the wheel scrolls; the chosen one is lit.
 */
final class TagPicker {
    static final int ROW = 12, VISIBLE = 6;
    private static final Component HEADER = Component.translatable("gui.golem_logix.bus.tag");
    private static final Component TAG_ANY = Component.translatable("gui.golem_logix.bus.tag_any");
    private static final Component TAG_ANY_HINT = Component.translatable("gui.golem_logix.bus.tag_any.hint");

    private final GolemMenu menu;
    private final int slot;
    private final List<TagKey<Item>> tags;
    private final Component[] tagNames;
    private final int[] tagSizes;
    private final String[] tagSizeLabels;
    private int x, y, w, scroll;

    TagPicker(GolemMenu menu, int slot, ItemStack ghost) {
        this.menu = menu;
        this.slot = slot;
        this.tags = Fuzzy.tagsOf(ghost);
        int n = this.tags.size();
        this.tagNames = new Component[n];
        this.tagSizes = new int[n];
        this.tagSizeLabels = new String[n];
        for (int i = 0; i < n; i++) {
            TagKey<Item> t = this.tags.get(i);
            this.tagNames[i] = tagName(t);
            this.tagSizes[i] = Fuzzy.tagSize(t);
            this.tagSizeLabels[i] = Integer.toString(this.tagSizes[i]);
        }
    }

    int rows() {
        return Math.min(VISIBLE, tags.size() + 1);
    }

    /** Height from the label to the bottom of the list. */
    int height() {
        return 12 + rows() * ROW + 2;
    }

    /** Placed at x, y (the label's top left), w wide. */
    void place(int x, int y, int w) {
        this.x = x;
        this.y = y;
        this.w = w;
        scroll = Math.max(0, Math.min(scroll, tags.size() + 1 - rows()));
    }

    private int listY() {
        return y + 12;
    }

    /** The choice under the mouse: -1 for none, 0 for "any components", k for the tag k - 1. */
    private int choiceAt(double mx, double my) {
        if (mx < x || mx >= x + w || my < listY() || my >= listY() + rows() * ROW) {
            return -1;
        }
        int idx = scroll + (int) ((my - listY()) / ROW);
        return idx >= 0 && idx < tags.size() + 1 ? idx : -1;
    }

    /** A tag's name: its translation where a mod gives one (c:glass_blocks: "Glass Blocks"), otherwise its id. */
    static Component tagName(TagKey<Item> t) {
        var id = t.location();
        return Component.translatableWithFallback("tag.item." + id.getNamespace() + "." + id.getPath().replace('/', '.'), "#" + id);
    }

    void background(GuiGraphics g) {
        GolemGui.inset(g, x - 1, listY() - 1, w + 2, rows() * ROW + 2);
    }

    void render(GuiGraphics g, Font font, ItemStack ghost, int mouseX, int mouseY) {
        g.drawString(font, HEADER, x, y, GolemGui.PARCHMENT, false);
        TagKey<Item> cur = Fuzzy.tag(ghost);
        int hover = choiceAt(mouseX, mouseY);
        for (int r = 0; r < rows(); r++) {
            int k = scroll + r, ry = listY() + r * ROW;
            TagKey<Item> t = k == 0 ? null : tags.get(k - 1);
            boolean chosen = Objects.equals(t, cur);
            if (chosen) {
                g.fill(x, ry, x + w, ry + ROW, 0x6038B0A8);
            } else if (k == hover) {
                g.fill(x, ry, x + w, ry + ROW, 0x30FFFFFF);
            }
            Component name = k == 0 ? TAG_ANY : tagNames[k - 1];
            String n = k == 0 ? "" : tagSizeLabels[k - 1];
            int nw = font.width(n);
            g.drawString(font, net.minecraft.locale.Language.getInstance().getVisualOrder(font.substrByWidth(name, Math.max(0, w - 10 - nw))), x + 3, ry + 2,
                    chosen ? GolemGui.CYAN : GolemGui.PARCHMENT, false);
            g.drawString(font, n, x + w - 3 - nw, ry + 2, 0xFF8A7A66, false);
        }
        int total = tags.size() + 1;
        if (total > VISIBLE) {
            // a thin scrollbar on the list's right edge
            int barH = Math.max(6, rows() * ROW * VISIBLE / total);
            int barY = listY() + (rows() * ROW - barH) * scroll / (total - VISIBLE);
            g.fill(x + w - 1, barY, x + w, barY + barH, 0xFFA89880);
        }
        if (hover >= 1) {
            int ti = hover - 1;
            TagKey<Item> t = tags.get(ti);
            g.renderComponentTooltip(font, List.of(tagNames[ti], Component.literal("#" + t.location()).withStyle(ChatFormatting.DARK_GRAY),
                    Component.translatable("gui.golem_logix.bus.tag_items", tagSizes[ti]).withStyle(ChatFormatting.GRAY)), mouseX, mouseY);
        } else if (hover == 0) {
            g.renderTooltip(font, TAG_ANY_HINT, mouseX, mouseY);
        }
    }

    boolean clicked(double mx, double my, int button) {
        int k = button == 0 ? choiceAt(mx, my) : -1;
        if (k < 0) {
            return false;
        }
        String id = k == 0 ? "" : tags.get(k - 1).location().toString();
        PacketDistributor.sendToServer(new FuzzyTagPayload(menu.containerId, slot, id));
        return true;
    }

    boolean scrolled(double sy) {
        if (tags.size() + 1 <= VISIBLE || sy == 0) {
            return false;
        }
        scroll = Math.max(0, Math.min(tags.size() + 1 - VISIBLE, scroll - (int) Math.signum(sy)));
        return true;
    }
}
