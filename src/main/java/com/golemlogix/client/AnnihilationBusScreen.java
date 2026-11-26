package com.golemlogix.client;

import com.golemlogix.blockentity.AnnihilationBusBlockEntity;
import com.golemlogix.menu.AnnihilationBusMenu;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Annihilation Bus: the filter grid, the tool slot beside it, the click settings in the column on the left. */
public class AnnihilationBusScreen extends GolemScreen<AnnihilationBusMenu> {
    private static final ItemStack ICON_LEVER = new ItemStack(Items.LEVER);
    private static final ItemStack ICON_SWORD = new ItemStack(Items.IRON_SWORD);
    private static final ItemStack ICON_PICKAXE = new ItemStack(Items.IRON_PICKAXE);
    private static final ItemStack ICON_BOOTS = new ItemStack(Items.LEATHER_BOOTS);
    private static final ItemStack ICON_BARRIER = new ItemStack(Items.BARRIER);
    private static final ItemStack ICON_PAPER = new ItemStack(Items.PAPER);
    private static final ItemStack ICON_WHEAT = new ItemStack(Items.WHEAT);

    public AnnihilationBusScreen(AnnihilationBusMenu menu, Inventory inv, Component title) {
        super(menu, inv, title, AnnihilationBusMenu.IMAGE_WIDTH, AnnihilationBusMenu.IMAGE_HEIGHT);
    }

    @Override
    protected void init() {
        super.init();
        addRedstoneButton(menu::redstoneMode);
        setting(() -> switch (menu.mode()) {
                    case AnnihilationBusBlockEntity.RIGHT -> ICON_LEVER;
                    case AnnihilationBusBlockEntity.ATTACK -> ICON_SWORD;
                    default -> ICON_PICKAXE;
                }, "click", this::mode, () -> true, AnnihilationBusMenu.BUTTON_CLICK);
        setting(() -> ICON_BOOTS, "sneak", () -> menu.sneak() ? "on" : "off", menu::sneak,
                AnnihilationBusMenu.BUTTON_SNEAK);
        setting(() -> menu.denyList() ? ICON_BARRIER : ICON_PAPER, "list", () -> menu.denyList() ? "deny" : "allow",
                menu::denyList, AnnihilationBusMenu.BUTTON_LIST);
        setting(() -> ICON_WHEAT, "grown", () -> menu.grownOnly() ? "on" : "off", menu::grownOnly,
                AnnihilationBusMenu.BUTTON_GROWN);
    }

    @Override
    protected int filterSlots() {
        return AnnihilationBusBlockEntity.FILTER_SLOTS;
    }

    private String mode() {
        return switch (menu.mode()) {
            case AnnihilationBusBlockEntity.RIGHT -> "right";
            case AnnihilationBusBlockEntity.ATTACK -> "attack";
            default -> "left";
        };
    }

    /** A setting in the side column: its title, its current value and a hint (gui.golem_logix.annihilation.<key>[.<value>]). */
    private void setting(java.util.function.Supplier<ItemStack> icon, String key, java.util.function.Supplier<String> value,
                         java.util.function.BooleanSupplier lit, int button) {
        String base = "gui.golem_logix.annihilation." + key;
        addSideButton(icon, () -> Component.translatable(base).append("\n")
                        .append(Component.translatable(base + "." + value.get())).append("\n")
                        .append(Component.translatable(base + "." + value.get() + ".hint").withStyle(ChatFormatting.GRAY)),
                () -> true, lit, button);
    }

    @Override
    protected void renderExtraBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        GolemGui.inset(g, leftPos + AnnihilationBusMenu.FILTER_X, topPos + 18, 56, 56);
        Component label = Component.translatable("gui.golem_logix.annihilation.tool");
        g.drawString(font, label, leftPos + AnnihilationBusMenu.TOOL_X + 8 - font.width(label) / 2, topPos + AnnihilationBusMenu.TOOL_Y - 11,
                GolemGui.CYAN_DARK, false);
        Component hint = Component.translatable("gui.golem_logix.bus.annihilation_hint." + mode());
        g.drawWordWrap(font, hint, leftPos + 8, topPos + 90, imageWidth - 16, GolemGui.CYAN_DARK);
    }

    /** Over the slot frames: with the tool slot empty, a faded iron pickaxe - the strength the bare golem brings. */
    @Override
    protected void renderOverlay(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        if (!menu.getSlot(AnnihilationBusMenu.TOOL_SLOT_INDEX).hasItem()) {
            int x = leftPos + AnnihilationBusMenu.TOOL_X, y = topPos + AnnihilationBusMenu.TOOL_Y;
            g.renderItem(menu.mode() == AnnihilationBusBlockEntity.ATTACK ? ICON_SWORD : ICON_PICKAXE, x, y);
            g.pose().pushPose();
            g.pose().translate(0, 0, 300);
            g.fill(x, y, x + 16, y + 16, 0xA8241A12);
            g.pose().popPose();
        }
    }
}
