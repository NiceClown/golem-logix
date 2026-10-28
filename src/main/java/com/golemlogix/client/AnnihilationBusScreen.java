package com.golemlogix.client;

import com.golemlogix.blockentity.AnnihilationBusBlockEntity;
import com.golemlogix.menu.AnnihilationBusMenu;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Annihilation Bus: the filter grid, the tool slot beside it, the click settings in the column on the left. */
public class AnnihilationBusScreen extends GolemScreen<AnnihilationBusMenu> {
    public AnnihilationBusScreen(AnnihilationBusMenu menu, Inventory inv, Component title) {
        super(menu, inv, title, AnnihilationBusMenu.IMAGE_WIDTH, AnnihilationBusMenu.IMAGE_HEIGHT);
    }

    @Override
    protected void init() {
        super.init();
        addRedstoneButton(menu::redstoneMode);
        setting(() -> new ItemStack(switch (menu.mode()) {
                    case AnnihilationBusBlockEntity.RIGHT -> Items.LEVER;
                    case AnnihilationBusBlockEntity.ATTACK -> Items.IRON_SWORD;
                    default -> Items.IRON_PICKAXE;
                }), "click", () -> mode(), () -> true, AnnihilationBusMenu.BUTTON_CLICK);
        setting(() -> new ItemStack(Items.LEATHER_BOOTS), "sneak", () -> menu.sneak() ? "on" : "off", menu::sneak,
                AnnihilationBusMenu.BUTTON_SNEAK);
        setting(() -> new ItemStack(menu.denyList() ? Items.BARRIER : Items.PAPER), "list", () -> menu.denyList() ? "deny" : "allow",
                menu::denyList, AnnihilationBusMenu.BUTTON_LIST);
        setting(() -> new ItemStack(Items.WHEAT), "grown", () -> menu.grownOnly() ? "on" : "off", menu::grownOnly,
                AnnihilationBusMenu.BUTTON_GROWN);
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
    protected void renderExtraBg(GuiGraphicsExtractor g, float partialTick, int mouseX, int mouseY) {
        GolemGui.inset(g, leftPos + AnnihilationBusMenu.FILTER_X, topPos + 18, 56, 56);
        Component label = Component.translatable("gui.golem_logix.annihilation.tool");
        g.text(font, label, leftPos + AnnihilationBusMenu.TOOL_X + 8 - font.width(label) / 2, topPos + AnnihilationBusMenu.TOOL_Y - 11,
                GolemGui.CYAN_DARK, false);
        Component hint = Component.translatable("gui.golem_logix.bus.annihilation_hint." + mode());
        g.textWithWordWrap(font, hint, leftPos + 8, topPos + 90, imageWidth - 16, GolemGui.CYAN_DARK);
    }

    /** Over the slot frames: with the tool slot empty, a faded iron pickaxe - the strength the bare golem brings. */
    @Override
    protected void renderOverlay(GuiGraphicsExtractor g, float partialTick, int mouseX, int mouseY) {
        if (!menu.getSlot(AnnihilationBusMenu.TOOL_SLOT_INDEX).hasItem()) {
            int x = leftPos + AnnihilationBusMenu.TOOL_X, y = topPos + AnnihilationBusMenu.TOOL_Y;
            g.item(new ItemStack(menu.mode() == AnnihilationBusBlockEntity.ATTACK ? Items.IRON_SWORD : Items.IRON_PICKAXE), x, y);
            g.fill(x, y, x + 16, y + 16, 0xA8241A12);
        }
    }
}
