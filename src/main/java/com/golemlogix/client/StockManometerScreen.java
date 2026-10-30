package com.golemlogix.client;

import com.golemlogix.blockentity.StockManometerBlockEntity;
import com.golemlogix.items.FluidGhostItem;
import com.golemlogix.menu.StockManometerMenu;
import com.golemlogix.network.ManometerConfigPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * Stock Manometer window: the ghost of what it watches, the mode as a side button (above the threshold / below it /
 * proportional), the threshold and an optional second one typed in (on below 500, off above 2 000), and a line that says in
 * words what the signal does, with the stock now. A fluid counts in mB.
 */
public class StockManometerScreen extends GolemScreen<StockManometerMenu> {
    private EditBox low, high;

    public StockManometerScreen(StockManometerMenu menu, Inventory inv, Component title) {
        super(menu, inv, title, StockManometerMenu.IMAGE_WIDTH, StockManometerMenu.IMAGE_HEIGHT);
    }

    @Override
    protected void init() {
        super.init();
        addSideButton(() -> new ItemStack(switch (menu.getMode()) {
                    case StockManometerBlockEntity.MODE_BELOW -> Items.LEVER;
                    case StockManometerBlockEntity.MODE_ANALOG -> Items.COMPARATOR;
                    default -> Items.REDSTONE_TORCH;
                }),
                () -> {
                    String key = "gui.golem_logix.manometer.mode." + modeName();
                    return Component.translatable("gui.golem_logix.manometer.mode").append("\n").append(Component.translatable(key))
                            .append("\n").append(Component.translatable(key + ".hint").withStyle(ChatFormatting.GRAY));
                },
                () -> true, () -> menu.getMode() != StockManometerBlockEntity.MODE_ABOVE, StockManometerMenu.BUTTON_MODE);
        low = number(leftPos + 104, topPos + 22, menu.getThreshold());
        high = number(leftPos + 104, topPos + 40, menu.getHysteresis());
    }

    private String modeName() {
        return switch (menu.getMode()) {
            case StockManometerBlockEntity.MODE_BELOW -> "below";
            case StockManometerBlockEntity.MODE_ANALOG -> "analog";
            default -> "above";
        };
    }

    private EditBox number(int x, int y, int value) {
        EditBox box = new EditBox(font, x, y, 50, 12, Component.empty());
        box.setBordered(false);
        box.setMaxLength(9);
        box.setFilter(s -> s.matches("\\d{0,9}"));
        box.setTextColor(GolemGui.PARCHMENT);
        box.setValue(Integer.toString(value));
        box.setResponder(s -> send());
        addRenderableWidget(box);
        return box;
    }

    private static int parse(EditBox box, int fallback) {
        try {
            return box.getValue().isEmpty() ? fallback : (int) Math.min(Integer.MAX_VALUE, Long.parseLong(box.getValue()));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private void send() {
        if (low == null || high == null) {
            return;
        }
        int t = Math.max(1, parse(low, menu.getThreshold())), h = parse(high, 0);
        if (t != menu.getThreshold() || h != menu.getHysteresis()) {
            ClientPacketDistributor.sendToServer(new ManometerConfigPayload(menu.containerId, menu.getMode(), t, h));
        }
    }

    /** The fields follow the server while the player is not typing in them. */
    @Override
    protected void containerTick() {
        super.containerTick();
        if (!low.isFocused() && parse(low, -1) != menu.getThreshold()) {
            low.setValue(Integer.toString(menu.getThreshold()));
        }
        if (!high.isFocused() && parse(high, -1) != menu.getHysteresis()) {
            high.setValue(Integer.toString(menu.getHysteresis()));
        }
        high.visible = menu.getMode() != StockManometerBlockEntity.MODE_ANALOG;
    }

    @Override
    protected int filterSlots() {
        return 1;
    }

    @Override
    protected boolean keepsAmounts() {
        return true;
    }

    @Override
    protected void renderExtraBg(GuiGraphicsExtractor g, float partialTick, int mouseX, int mouseY) {
        GolemGui.inset(g, leftPos + 100, topPos + 18, 58, 16);
        if (menu.getMode() != StockManometerBlockEntity.MODE_ANALOG) {
            GolemGui.inset(g, leftPos + 100, topPos + 36, 58, 16);
        }
    }

    private String amount(long n) {
        boolean fluid = FluidGhostItem.is(menu.getSlot(0).getItem());
        return fluid ? GolemGui.formatCount(n) + " mB" : String.format(java.util.Locale.ROOT, "%,d", n).replace(',', ' ');
    }

    /** The "off again at" field explains itself on hover. */
    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(g, mouseX, mouseY, partialTick);
        int x = mouseX - leftPos, y = mouseY - topPos;
        if (menu.getMode() != StockManometerBlockEntity.MODE_ANALOG && x >= 48 && x < 158 && y >= 36 && y < 52) {
            java.util.List<Component> lines = new java.util.ArrayList<>();
            for (var line : font.getSplitter().splitLines(Component.translatable("gui.golem_logix.manometer.off.hint"), 200,
                    net.minecraft.network.chat.Style.EMPTY)) {
                lines.add(Component.literal(line.getString()));
            }
            g.setComponentTooltipForNextFrame(font, lines, mouseX, mouseY);
        }
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        g.text(font, title, 8, 6, GolemGui.PARCHMENT, false);
        boolean analog = menu.getMode() == StockManometerBlockEntity.MODE_ANALOG;
        g.text(font, Component.translatable(analog ? "gui.golem_logix.manometer.full" : "gui.golem_logix.manometer.on"), 48, 22,
                GolemGui.PARCHMENT, false);
        if (!analog) {
            g.text(font, Component.translatable("gui.golem_logix.manometer.off"), 48, 40, GolemGui.PARCHMENT, false);
        }
        int t = menu.getThreshold(), h = menu.getHysteresis();
        Component rule = switch (menu.getMode()) {
            case StockManometerBlockEntity.MODE_BELOW -> h > t
                    ? Component.translatable("gui.golem_logix.manometer.rule.below_band", amount(t), amount(h))
                    : Component.translatable("gui.golem_logix.manometer.rule.below", amount(t));
            case StockManometerBlockEntity.MODE_ANALOG -> Component.translatable("gui.golem_logix.manometer.rule.analog", amount(t));
            default -> h > 0 && h < t
                    ? Component.translatable("gui.golem_logix.manometer.rule.above_band", amount(t), amount(h))
                    : Component.translatable("gui.golem_logix.manometer.rule.above", amount(t));
        };
        g.textWithWordWrap(font, rule, 8, 58, imageWidth - 16, GolemGui.CYAN_DARK);
        g.text(font, Component.translatable("gui.golem_logix.manometer.status", amount(menu.getStock()), menu.getSignal()), 8, 82,
                0xFFA89880, false);
    }
}
