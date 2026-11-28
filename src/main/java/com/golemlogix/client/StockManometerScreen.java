package com.golemlogix.client;

import com.golemlogix.blockentity.StockManometerBlockEntity;
import com.golemlogix.items.FluidGhostItem;
import com.golemlogix.menu.StockManometerMenu;
import com.golemlogix.network.ManometerConfigPayload;
import com.google.common.base.Suppliers;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

import java.util.function.Supplier;

/**
 * Stock Manometer window: the ghost of what it watches, the mode as a side button (above the threshold / below it /
 * proportional), the threshold and an optional second one typed in (on below 500, off above 2 000), and a line that says in
 * words what the signal does, with the stock now. A fluid counts in mB.
 */
public class StockManometerScreen extends GolemScreen<StockManometerMenu> {
    private static final Supplier<ItemStack> ICON_ABOVE = Suppliers.memoize(() -> new ItemStack(Items.REDSTONE_TORCH));
    private static final Supplier<ItemStack> ICON_BELOW = Suppliers.memoize(() -> new ItemStack(Items.LEVER));
    private static final Supplier<ItemStack> ICON_ANALOG = Suppliers.memoize(() -> new ItemStack(Items.COMPARATOR));
    private static final Supplier<ItemStack> ICON_BARRIER = Suppliers.memoize(() -> new ItemStack(Items.BARRIER));
    private static final Component LABEL_FULL = Component.translatable("gui.golem_logix.manometer.full");
    private static final Component LABEL_ON = Component.translatable("gui.golem_logix.manometer.on");
    private static final Component LABEL_OFF = Component.translatable("gui.golem_logix.manometer.off");

    private EditBox low, high;
    /** What this window last sent: a field is overwritten from the server only when someone else changed the value. */
    private int sentLow, sentHigh;
    private final Block[] cachedNeighbourBlocks = new Block[6];
    private final ItemStack[] cachedNeighbourIcons = new ItemStack[6];
    private java.util.List<Component> offHintLines = java.util.List.of();
    private int cachedMode = -1, cachedThreshold = -1, cachedHysteresis = -1, cachedSignal = -1;
    private long cachedStock = Long.MIN_VALUE;
    private boolean cachedFluid;
    private Component cachedRule = Component.empty();
    private Component cachedStatus = Component.empty();

    public StockManometerScreen(StockManometerMenu menu, Inventory inv, Component title) {
        super(menu, inv, title, StockManometerMenu.IMAGE_WIDTH, StockManometerMenu.IMAGE_HEIGHT);
    }

    @Override
    protected void init() {
        super.init();
        addSideButton(() -> switch (menu.getMode()) {
                    case StockManometerBlockEntity.MODE_BELOW -> ICON_BELOW.get();
                    case StockManometerBlockEntity.MODE_ANALOG -> ICON_ANALOG.get();
                    default -> ICON_ABOVE.get();
                },
                () -> {
                    String key = "gui.golem_logix.manometer.mode." + modeName();
                    return Component.translatable("gui.golem_logix.manometer.mode").append("\n").append(Component.translatable(key))
                            .append("\n").append(Component.translatable(key + ".hint").withStyle(ChatFormatting.GRAY));
                },
                () -> true, () -> menu.getMode() != StockManometerBlockEntity.MODE_ABOVE, StockManometerMenu.BUTTON_MODE);
        for (net.minecraft.core.Direction side : net.minecraft.core.Direction.values()) {
            addSideButton(() -> menu.getBridgeMode(side) == StockManometerBlockEntity.BRIDGE_OFF ? ICON_BARRIER.get() : neighbourIcon(side),
                    () -> bridgeTip(side), () -> !neighbour(side).isAir(), () -> bridged(side), StockManometerMenu.BUTTON_BRIDGE + side.ordinal());
        }
        sentLow = menu.getThreshold();
        sentHigh = menu.getHysteresis();
        low = number(leftPos + 122, topPos + 22, sentLow);
        high = number(leftPos + 122, topPos + 40, sentHigh);
        high.visible = menu.getMode() != StockManometerBlockEntity.MODE_ANALOG;
        java.util.List<Component> lines = new java.util.ArrayList<>();
        for (var line : font.getSplitter().splitLines(Component.translatable("gui.golem_logix.manometer.off.hint"), 200,
                net.minecraft.network.chat.Style.EMPTY)) {
            lines.add(Component.literal(line.getString()));
        }
        offHintLines = lines;
        cachedMode = -1;
    }

    private net.minecraft.world.level.block.state.BlockState neighbour(net.minecraft.core.Direction side) {
        if (minecraft == null || minecraft.level == null) {
            return Blocks.AIR.defaultBlockState();
        }
        return minecraft.level.getBlockState(menu.getDevicePos().relative(side));
    }

    private ItemStack neighbourIcon(net.minecraft.core.Direction side) {
        int i = side.ordinal();
        Block b = neighbour(side).getBlock();
        if (cachedNeighbourBlocks[i] != b || cachedNeighbourIcons[i] == null) {
            cachedNeighbourBlocks[i] = b;
            cachedNeighbourIcons[i] = new ItemStack(b);
        }
        return cachedNeighbourIcons[i];
    }

    /** Whether the bridge is there (the window's mode, not the block entity's: it answers at once for the player clicking). */
    private boolean bridged(net.minecraft.core.Direction side) {
        return switch (menu.getBridgeMode(side)) {
            case StockManometerBlockEntity.BRIDGE_ON -> true;
            case StockManometerBlockEntity.BRIDGE_OFF -> false;
            default -> minecraft != null && minecraft.level != null
                    && com.golemlogix.blocks.StockManometerBlock.isReceiver(minecraft.level, menu.getDevicePos(), side);
        };
    }

    private Component bridgeTip(net.minecraft.core.Direction side) {
        String state = switch (menu.getBridgeMode(side)) {
            case StockManometerBlockEntity.BRIDGE_ON -> "on";
            case StockManometerBlockEntity.BRIDGE_OFF -> "off";
            default -> bridged(side) ? "auto_yes" : "auto_no";
        };
        return Component.translatable("gui.golem_logix.manometer.bridge." + side.getName()).append("\n")
                .append(neighbour(side).getBlock().getName()).append("\n")
                .append(Component.translatable("gui.golem_logix.manometer.bridge." + state)).append("\n")
                .append(Component.translatable("gui.golem_logix.manometer.bridge.hint").withStyle(ChatFormatting.GRAY));
    }

    private String modeName() {
        return switch (menu.getMode()) {
            case StockManometerBlockEntity.MODE_BELOW -> "below";
            case StockManometerBlockEntity.MODE_ANALOG -> "analog";
            default -> "above";
        };
    }

    private EditBox number(int x, int y, int value) {
        EditBox box = new EditBox(font, x, y, 44, 12, Component.empty());
        box.setBordered(false);
        box.setMaxLength(7);
        box.setFilter(s -> s.matches("\\d{0,7}"));
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
        int t = Math.max(1, parse(low, sentLow)), h = parse(high, 0);
        if (t != sentLow || h != sentHigh) {
            sentLow = t;
            sentHigh = h;
            PacketDistributor.sendToServer(new ManometerConfigPayload(menu.containerId, menu.getMode(), t, h));
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        boolean handled = super.mouseClicked(mouseX, mouseY, button);
        if (low != null && !low.isMouseOver(mouseX, mouseY) && low.isFocused()) {
            low.setFocused(false);
        }
        if (high != null && !high.isMouseOver(mouseX, mouseY) && high.isFocused()) {
            high.setFocused(false);
        }
        return handled;
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            return super.keyPressed(key, scanCode, modifiers);
        }
        EditBox focused = low != null && low.isFocused() ? low : high != null && high.visible && high.isFocused() ? high : null;
        if (focused != null) {
            if (key == GLFW.GLFW_KEY_TAB) {
                return super.keyPressed(key, scanCode, modifiers);
            }
            if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
                send();
                focused.setFocused(false);
                return true;
            }
            focused.keyPressed(key, scanCode, modifiers);
            return true;
        }
        return super.keyPressed(key, scanCode, modifiers);
    }

    /** Whatever is typed when the window closes is kept. */
    @Override
    public void removed() {
        send();
        super.removed();
    }

    /** The fields follow the server while the player is not typing in them. */
    @Override
    protected void containerTick() {
        super.containerTick();
        // a change from elsewhere (the slot window's amount, another player): the field follows it
        if (menu.getThreshold() != sentLow && !low.isFocused()) {
            sentLow = menu.getThreshold();
            low.setValue(Integer.toString(sentLow));
        }
        if (menu.getHysteresis() != sentHigh && !high.isFocused()) {
            sentHigh = menu.getHysteresis();
            high.setValue(Integer.toString(sentHigh));
        }
        high.visible = menu.getMode() != StockManometerBlockEntity.MODE_ANALOG;
        if (!high.visible && high.isFocused()) {
            high.setFocused(false);
        }
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
    protected void renderExtraBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        GolemGui.inset(g, leftPos + 118, topPos + 18, 50, 16);
        if (menu.getMode() != StockManometerBlockEntity.MODE_ANALOG) {
            GolemGui.inset(g, leftPos + 118, topPos + 36, 50, 16);
        }
    }

    private static String amount(long n, boolean fluid) {
        return fluid ? GolemGui.formatCount(n) + " mB" : String.format(java.util.Locale.ROOT, "%,d", n).replace(',', ' ');
    }

    /** The "off again at" field explains itself on hover. */
    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        int x = mouseX - leftPos, y = mouseY - topPos;
        if (menu.getMode() != StockManometerBlockEntity.MODE_ANALOG && x >= 48 && x < 168 && y >= 36 && y < 52) {
            g.renderComponentTooltip(font, offHintLines, mouseX, mouseY);
        }
    }

    private void ensureTextCache() {
        int mode = menu.getMode();
        int t = menu.getThreshold();
        int h = menu.getHysteresis();
        long stock = menu.getStock();
        int signal = menu.getSignal();
        boolean fluid = FluidGhostItem.is(menu.getSlot(0).getItem());
        if (mode == cachedMode && t == cachedThreshold && h == cachedHysteresis
                && stock == cachedStock && signal == cachedSignal && fluid == cachedFluid) {
            return;
        }
        cachedMode = mode;
        cachedThreshold = t;
        cachedHysteresis = h;
        cachedStock = stock;
        cachedSignal = signal;
        cachedFluid = fluid;
        cachedRule = switch (mode) {
            case StockManometerBlockEntity.MODE_BELOW -> h > t
                    ? Component.translatable("gui.golem_logix.manometer.rule.below_band", amount(t, fluid), amount(h, fluid))
                    : Component.translatable("gui.golem_logix.manometer.rule.below", amount(t, fluid));
            case StockManometerBlockEntity.MODE_ANALOG -> Component.translatable("gui.golem_logix.manometer.rule.analog", amount(t, fluid));
            default -> h > 0 && h < t
                    ? Component.translatable("gui.golem_logix.manometer.rule.above_band", amount(t, fluid), amount(h, fluid))
                    : Component.translatable("gui.golem_logix.manometer.rule.above", amount(t, fluid));
        };
        cachedStatus = Component.translatable("gui.golem_logix.manometer.status", amount(stock, fluid), signal);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        ensureTextCache();
        g.drawString(font, title, 8, 6, GolemGui.PARCHMENT, false);
        boolean analog = cachedMode == StockManometerBlockEntity.MODE_ANALOG;
        g.drawString(font, analog ? LABEL_FULL : LABEL_ON, 48, 22, GolemGui.PARCHMENT, false);
        if (!analog) {
            g.drawString(font, LABEL_OFF, 48, 40, GolemGui.PARCHMENT, false);
        }
        g.drawWordWrap(font, cachedRule, 8, 58, imageWidth - 16, GolemGui.CYAN_DARK);
        g.drawString(font, cachedStatus, 8, 82, 0xFFA89880, false);
    }
}
