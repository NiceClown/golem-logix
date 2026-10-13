package com.golemlogix.menu;

import com.golemlogix.blockentity.CryoMainframeBlockEntity;
import com.golemlogix.init.ModBlocks;
import com.golemlogix.init.ModMenus;
import com.golemlogix.upgrade.RedstoneMode;
import com.golemlogix.upgrade.UpgradeSpec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Окно Крио-Мейнфрейма: две полки по шесть ячеек под Крио-Калькуляторы, сводка ядер и ускорения, колонка Сжатых
 * Сопроцессоров и Модуля Редстоуна справа. Пока окно открыто, стеклянные двери шкафа распахнуты.
 */
public class MainframeMenu extends GolemMenu {
    public static final int IMAGE_WIDTH = 176;
    public static final int IMAGE_HEIGHT = 206;
    public static final int SHELF_X = 35, SHELF_Y = 20;

    @Nullable
    private final CryoMainframeBlockEntity be;
    private final ContainerLevelAccess access;
    private final ContainerData data;

    public static MainframeMenu fromNetwork(int id, Inventory inv, RegistryFriendlyByteBuf buf) {
        return new MainframeMenu(id, inv, null, new SimpleContainer(CryoMainframeBlockEntity.CALC_SLOTS),
                new SimpleContainer(UpgradeSpec.MAINFRAME.slots()), new SimpleContainerData(6), ContainerLevelAccess.NULL);
    }

    public MainframeMenu(int id, Inventory inv, @Nullable CryoMainframeBlockEntity be, Container calculators, Container upgrades,
                         ContainerData data, ContainerLevelAccess access) {
        super(ModMenus.MAINFRAME.get(), id);
        this.be = be;
        this.access = access;
        this.data = data;
        for (int i = 0; i < CryoMainframeBlockEntity.CALC_SLOTS; i++) {
            this.addSlot(new CalculatorSlot(calculators, i, SHELF_X + (i % 6) * 18, SHELF_Y + (i / 6) * 18));
        }
        this.containerSlotCount = CryoMainframeBlockEntity.CALC_SLOTS;
        addUpgradeSlots(upgrades, UpgradeSpec.MAINFRAME, IMAGE_WIDTH + 5, 8);
        addPlayerSlots(inv, 8, IMAGE_HEIGHT - 82);
        addDataSlots(data);
        if (be != null) {
            be.startOpen();
        }
    }

    /** Ячейка полки: только Крио-Калькулятор, по одному. */
    public static class CalculatorSlot extends Slot {
        CalculatorSlot(Container c, int i, int x, int y) {
            super(c, i, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return stack.is(ModBlocks.CRYO_CALCULATOR.get().asItem());
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }

        @Override
        public int getMaxStackSize(ItemStack stack) {
            return 1;
        }
    }

    public RedstoneMode redstoneMode() {
        return RedstoneMode.byId(data.get(0));
    }

    public int cores() {
        return data.get(1);
    }

    public int busy() {
        return data.get(2);
    }

    public int workersPerCore() {
        return data.get(3);
    }

    public int calculators() {
        return data.get(4);
    }

    public int multiplier() {
        return data.get(5);
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id == BUTTON_REDSTONE) {
            data.set(0, redstoneMode().next(true).ordinal());
            return true;
        }
        return false;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        if (be != null) {
            be.stopOpen();
        }
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(access, player, ModBlocks.CRYO_MAINFRAME.get());
    }
}
