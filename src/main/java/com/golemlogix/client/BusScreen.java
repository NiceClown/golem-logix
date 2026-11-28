package com.golemlogix.client;

import com.google.common.base.Suppliers;
import com.golemlogix.menu.BusMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.function.Supplier;

public class BusScreen extends GolemScreen<BusMenu> {
    private static final Supplier<ItemStack> ICON_BARRIER = Suppliers.memoize(() -> new ItemStack(Items.BARRIER));
    private static final Supplier<ItemStack> ICON_ITEM_FRAME = Suppliers.memoize(() -> new ItemStack(Items.ITEM_FRAME));
    private static final Supplier<ItemStack> ICON_HOPPER = Suppliers.memoize(() -> new ItemStack(Items.HOPPER));
    private static final Supplier<ItemStack> ICON_DROPPER = Suppliers.memoize(() -> new ItemStack(Items.DROPPER));
    private static final Supplier<ItemStack> ICON_CHEST = Suppliers.memoize(() -> new ItemStack(Items.CHEST));
    private static final Supplier<ItemStack> ICON_CLOCK = Suppliers.memoize(() -> new ItemStack(Items.CLOCK));
    private static final Supplier<ItemStack> ICON_ENDER_PEARL = Suppliers.memoize(() -> new ItemStack(Items.ENDER_PEARL));
    private static final Supplier<ItemStack> ICON_PAPER = Suppliers.memoize(() -> new ItemStack(Items.PAPER));
    private static final Supplier<ItemStack> ICON_WATER_BUCKET = Suppliers.memoize(() -> new ItemStack(Items.WATER_BUCKET));
    private static final Supplier<ItemStack> ICON_BUCKET = Suppliers.memoize(() -> new ItemStack(Items.BUCKET));

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
            addSideButton(() -> menu.denyList() ? ICON_BARRIER.get() : ICON_ITEM_FRAME.get(),
                    () -> {
                        String key = "gui.golem_logix.bus.list." + (menu.denyList() ? "deny" : "allow");
                        return Component.translatable("gui.golem_logix.bus.list").append("\n").append(Component.translatable(key))
                                .append("\n").append(Component.translatable(key + ".hint").withStyle(net.minecraft.ChatFormatting.GRAY));
                    },
                    () -> true, menu::denyList, BusMenu.BUTTON_LIST);
        }
        if (menu.isPeriscope()) {
            addSideButton(() -> switch (menu.access()) {
                        case com.golemlogix.blockentity.PeriscopeBlockEntity.INSERT_ONLY -> ICON_HOPPER.get();
                        case com.golemlogix.blockentity.PeriscopeBlockEntity.EXTRACT_ONLY -> ICON_DROPPER.get();
                        default -> ICON_CHEST.get();
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
                        case com.golemlogix.blockentity.FormationBusBlockEntity.ROUND_ROBIN -> ICON_CLOCK.get();
                        case com.golemlogix.blockentity.FormationBusBlockEntity.RANDOM -> ICON_ENDER_PEARL.get();
                        default -> ICON_PAPER.get();
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
        if (menu.isFormation()) {
            addSideButton(() -> menu.replaceFluids() ? ICON_WATER_BUCKET.get() : ICON_BUCKET.get(),
                    () -> {
                        String key = "gui.golem_logix.bus.fluids." + (menu.replaceFluids() ? "replace" : "keep");
                        return Component.translatable("gui.golem_logix.bus.fluids").append("\n").append(Component.translatable(key))
                                .append("\n").append(Component.translatable(key + ".hint").withStyle(net.minecraft.ChatFormatting.GRAY));
                    },
                    () -> true, menu::replaceFluids, BusMenu.BUTTON_FLUIDS);
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
    protected void extractSlot(GuiGraphicsExtractor g, net.minecraft.world.inventory.Slot slot, int mouseX, int mouseY) {
        super.extractSlot(g, slot, mouseX, mouseY);
        int keep = menu.isExport() && slot.index < com.golemlogix.blockentity.BusBlockEntity.FILTER_SLOTS ? menu.keepAt(slot.index) : 0;
        if (keep > 0) {
            boolean fluid = com.golemlogix.items.FluidGhostItem.is(slot.getItem())
                    || !com.golemlogix.system.Transfers.fluidOf(slot.getItem()).isEmpty();
            String text = fluid ? InterfaceSlotScreen.bucketText(keep) : keep >= 10_000 ? keep / 1000 + "k" : Integer.toString(keep);
            g.itemDecorations(font, slot.getItem(), slot.x, slot.y, text);
        }
    }

    @Override
    protected void renderExtraBg(GuiGraphicsExtractor g, float partialTick, int mouseX, int mouseY) {
        GolemGui.inset(g, leftPos + BusMenu.FILTER_X, topPos + 18, 56, 56);
        Component hint = Component.translatable(menu.isFormation() ? "gui.golem_logix.bus.formation_hint" : menu.isPeriscope() ? "gui.golem_logix.bus.periscope_hint"
                : menu.isExport() ? "gui.golem_logix.bus.export_hint" : "gui.golem_logix.bus.import_hint");
        g.textWithWordWrap(font, hint, leftPos + 8, topPos + 90, imageWidth - 16, GolemGui.CYAN_DARK);
    }
}
