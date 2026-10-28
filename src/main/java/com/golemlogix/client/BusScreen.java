package com.golemlogix.client;

import com.golemlogix.menu.BusMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public class BusScreen extends GolemScreen<BusMenu> {
    public BusScreen(BusMenu menu, Inventory inv, Component title) {
        super(menu, inv, title, BusMenu.IMAGE_WIDTH, BusMenu.IMAGE_HEIGHT);
    }

    @Override
    protected void init() {
        super.init();
        // on buses to the right of the filter grid, on the periscope (no filter) in the middle; the Formation Bus has none
        if (!menu.isFormation()) {
            addPriorityPanel(menu.isPeriscope() ? 61 : BusMenu.FILTER_X + 56 + 8, 18, menu::getPriority);
        }
        addRedstoneButton(menu::redstoneMode);
        if (menu.hasOrder()) {
            addSideButton(() -> new net.minecraft.world.item.ItemStack(switch (menu.order()) {
                        case com.golemlogix.blockentity.FormationBusBlockEntity.ROUND_ROBIN -> net.minecraft.world.item.Items.CLOCK;
                        case com.golemlogix.blockentity.FormationBusBlockEntity.RANDOM -> net.minecraft.world.item.Items.ENDER_PEARL;
                        default -> net.minecraft.world.item.Items.PAPER;
                    }),
                    () -> {
                        String key = "gui.golem_logix.bus.order." + switch (menu.order()) {
                            case com.golemlogix.blockentity.FormationBusBlockEntity.ROUND_ROBIN -> "round";
                            case com.golemlogix.blockentity.FormationBusBlockEntity.RANDOM -> "random";
                            default -> "first";
                        };
                        return Component.translatable("gui.golem_logix.bus.order").append("\n").append(Component.translatable(key))
                                .append("\n").append(Component.translatable(key + ".hint").withStyle(net.minecraft.ChatFormatting.GRAY));
                    },
                    () -> true, () -> menu.order() != com.golemlogix.blockentity.FormationBusBlockEntity.IN_ORDER, BusMenu.BUTTON_ORDER);
        }
    }

    @Override
    protected int filterSlots() {
        return menu.isPeriscope() ? -1 : com.golemlogix.blockentity.BusBlockEntity.FILTER_SLOTS;
    }

    @Override
    protected boolean keepsAmounts() {
        return menu.isExport();
    }

    /** The Export Bus shows each slot's keep amount in its corner, as a stack count over the ghost. */
    @Override
    protected void extractSlot(GuiGraphicsExtractor g, net.minecraft.world.inventory.Slot slot, int mouseX, int mouseY) {
        super.extractSlot(g, slot, mouseX, mouseY);
        int keep = menu.isExport() && slot.index < com.golemlogix.blockentity.BusBlockEntity.FILTER_SLOTS ? menu.keepAt(slot.index) : 0;
        if (keep > 0) {
            boolean fluid = com.golemlogix.items.FluidGhostItem.is(slot.getItem())
                    || net.neoforged.neoforge.fluids.FluidUtil.getFluidContained(slot.getItem()).filter(f -> !f.isEmpty()).isPresent();
            String text = fluid ? InterfaceSlotScreen.bucketText(keep) : keep >= 10_000 ? keep / 1000 + "k" : Integer.toString(keep);
            g.itemDecorations(font, slot.getItem(), slot.x, slot.y, text);
        }
    }

    @Override
    protected void renderExtraBg(GuiGraphicsExtractor g, float partialTick, int mouseX, int mouseY) {
        if (!menu.isPeriscope()) {
            GolemGui.inset(g, leftPos + BusMenu.FILTER_X, topPos + 18, 56, 56);
        }
        Component hint = Component.translatable(menu.isFormation() ? "gui.golem_logix.bus.formation_hint" : menu.isPeriscope() ? "gui.golem_logix.bus.periscope_hint"
                : menu.isExport() ? "gui.golem_logix.bus.export_hint" : "gui.golem_logix.bus.import_hint");
        g.textWithWordWrap(font, hint, leftPos + 8, topPos + 90, imageWidth - 16, GolemGui.CYAN_DARK);
    }
}
