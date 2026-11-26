package com.golemlogix.client;

import com.golemlogix.menu.BusMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class BusScreen extends GolemScreen<BusMenu> {
    private static final ItemStack ICON_BARRIER = new ItemStack(Items.BARRIER);
    private static final ItemStack ICON_ITEM_FRAME = new ItemStack(Items.ITEM_FRAME);
    private static final ItemStack ICON_HOPPER = new ItemStack(Items.HOPPER);
    private static final ItemStack ICON_DROPPER = new ItemStack(Items.DROPPER);
    private static final ItemStack ICON_CHEST = new ItemStack(Items.CHEST);
    private static final ItemStack ICON_CLOCK = new ItemStack(Items.CLOCK);
    private static final ItemStack ICON_ENDER_PEARL = new ItemStack(Items.ENDER_PEARL);
    private static final ItemStack ICON_PAPER = new ItemStack(Items.PAPER);

    public BusScreen(BusMenu menu, Inventory inv, Component title) {
        super(menu, inv, title, BusMenu.IMAGE_WIDTH, BusMenu.IMAGE_HEIGHT);
    }

    @Override
    protected void init() {
        super.init();
        // to the right of the filter grid; the Formation Bus has none
        if (!menu.isFormation()) {
            addPriorityPanel(BusMenu.FILTER_X + 56 + 8, 18, menu::getPriority);
        }
        addRedstoneButton(menu::redstoneMode);
        if (menu.hasList()) {
            addSideButton(() -> menu.denyList() ? ICON_BARRIER : ICON_ITEM_FRAME,
                    () -> {
                        String key = "gui.golem_logix.bus.list." + (menu.denyList() ? "deny" : "allow");
                        return Component.translatable("gui.golem_logix.bus.list").append("\n").append(Component.translatable(key))
                                .append("\n").append(Component.translatable(key + ".hint").withStyle(net.minecraft.ChatFormatting.GRAY));
                    },
                    () -> true, menu::denyList, BusMenu.BUTTON_LIST);
        }
        if (menu.isPeriscope()) {
            addSideButton(() -> switch (menu.access()) {
                        case com.golemlogix.blockentity.PeriscopeBlockEntity.INSERT_ONLY -> ICON_HOPPER;
                        case com.golemlogix.blockentity.PeriscopeBlockEntity.EXTRACT_ONLY -> ICON_DROPPER;
                        default -> ICON_CHEST;
                    },
                    () -> {
                        String key = "gui.golem_logix.bus.access." + switch (menu.access()) {
                            case com.golemlogix.blockentity.PeriscopeBlockEntity.INSERT_ONLY -> "insert";
                            case com.golemlogix.blockentity.PeriscopeBlockEntity.EXTRACT_ONLY -> "extract";
                            default -> "both";
                        };
                        return Component.translatable("gui.golem_logix.bus.access").append("\n").append(Component.translatable(key))
                                .append("\n").append(Component.translatable(key + ".hint").withStyle(net.minecraft.ChatFormatting.GRAY));
                    },
                    () -> true, () -> menu.access() != com.golemlogix.blockentity.PeriscopeBlockEntity.BOTH, BusMenu.BUTTON_ACCESS);
        }
        if (menu.hasOrder()) {
            addSideButton(() -> switch (menu.order()) {
                        case com.golemlogix.blockentity.FormationBusBlockEntity.ROUND_ROBIN -> ICON_CLOCK;
                        case com.golemlogix.blockentity.FormationBusBlockEntity.RANDOM -> ICON_ENDER_PEARL;
                        default -> ICON_PAPER;
                    },
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
        return com.golemlogix.blockentity.BusBlockEntity.FILTER_SLOTS;
    }

    @Override
    protected boolean keepsAmounts() {
        return menu.isExport();
    }

    /** The Export Bus shows each slot's keep amount in its corner, as a stack count over the ghost. */
    @Override
    protected void renderSlot(GuiGraphics g, net.minecraft.world.inventory.Slot slot) {
        super.renderSlot(g, slot);
        int keep = menu.isExport() && slot.index < com.golemlogix.blockentity.BusBlockEntity.FILTER_SLOTS ? menu.keepAt(slot.index) : 0;
        if (keep > 0) {
            boolean fluid = com.golemlogix.items.FluidGhostItem.is(slot.getItem())
                    || net.neoforged.neoforge.fluids.FluidUtil.getFluidContained(slot.getItem()).filter(f -> !f.isEmpty()).isPresent();
            String text = fluid ? InterfaceSlotScreen.bucketText(keep) : keep >= 10_000 ? keep / 1000 + "k" : Integer.toString(keep);
            g.renderItemDecorations(font, slot.getItem(), slot.x, slot.y, text);
        }
    }

    @Override
    protected void renderExtraBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        GolemGui.inset(g, leftPos + BusMenu.FILTER_X, topPos + 18, 56, 56);
        Component hint = Component.translatable(menu.isFormation() ? "gui.golem_logix.bus.formation_hint" : menu.isPeriscope() ? "gui.golem_logix.bus.periscope_hint"
                : menu.isExport() ? "gui.golem_logix.bus.export_hint" : "gui.golem_logix.bus.import_hint");
        g.drawWordWrap(font, hint, leftPos + 8, topPos + 90, imageWidth - 16, GolemGui.CYAN_DARK);
    }
}
