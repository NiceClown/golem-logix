package com.golemlogix.menu;

import com.golemlogix.blockentity.InterfaceBlockEntity;
import com.golemlogix.init.ModMenus;
import com.golemlogix.items.UpgradeItem;
import com.golemlogix.upgrade.RedstoneMode;
import com.golemlogix.upgrade.UpgradeSpec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Окно Интерфейса: ряд «держать в запасе» (призраки; количество — отдельным числом в окне настройки ячейки, шестерёнка под
 * ячейкой или клик пустой рукой), под ним ряд выдачи (что сейчас лежит в запасе) и ряд приёма (сюда можно положить предмет
 * руками — он сразу уйдёт в сеть). Справа — колонка апгрейдов, слева — режим редстоуна.
 * <p>
 * Ячейки настройки нельзя ни крутить колесом, ни «тянуть»: любой клик, кроме клика предметом (поставить) и Shift-клика
 * (очистить), игнорируется — раньше моды прокрутки умудрялись вынимать оттуда предметы.
 */
public class InterfaceMenu extends GolemMenu {
    public static final int IMAGE_WIDTH = 176;
    public static final int IMAGE_HEIGHT = 214;
    /** Ряд настройки, под ним — кнопки ячеек (шестерёнка и режим, TOGGLE_Y, высота 8), ряд запаса и ряд приёма. */
    public static final int CONFIG_Y = 28, TOGGLE_Y = 46, STOCK_Y = 68, INBOX_Y = 98;
    /** Кнопки меню «переключить режим ячейки i»: BUTTON_MODE + i. */
    public static final int BUTTON_MODE = 20;

    private final Container config;
    private final ContainerData data;
    /** Сам интерфейс (на сервере): количество и предмет ячеек меняются через него. */
    @Nullable
    private final InterfaceBlockEntity be;

    public static InterfaceMenu fromNetwork(int id, Inventory inv, RegistryFriendlyByteBuf buf) {
        return new InterfaceMenu(id, inv, new SimpleContainer(InterfaceBlockEntity.SLOTS), new SimpleContainer(InterfaceBlockEntity.SLOTS),
                new SimpleContainer(InterfaceBlockEntity.SLOTS), new SimpleContainer(UpgradeSpec.INTERFACE.slots()),
                new SimpleContainerData(InterfaceBlockEntity.DATA_SIZE), null);
    }

    public InterfaceMenu(int id, Inventory inv, Container config, Container stock, Container inbox, Container upgrades, ContainerData data,
                         @Nullable InterfaceBlockEntity be) {
        super(ModMenus.INTERFACE.get(), id);
        this.config = config;
        this.data = data;
        this.be = be;
        for (int i = 0; i < InterfaceBlockEntity.SLOTS; i++) {
            this.addSlot(new GhostSlot(config, i, 8 + i * 18, CONFIG_Y));
        }
        for (int i = 0; i < InterfaceBlockEntity.SLOTS; i++) {
            this.addSlot(new TakeOnlySlot(stock, i, 8 + i * 18, STOCK_Y));
        }
        for (int i = 0; i < InterfaceBlockEntity.SLOTS; i++) {
            this.addSlot(new Slot(inbox, i, 8 + i * 18, INBOX_Y));
        }
        this.containerSlotCount = InterfaceBlockEntity.SLOTS * 3;
        addUpgradeSlots(upgrades, UpgradeSpec.INTERFACE, IMAGE_WIDTH + 5, 8);
        addPlayerSlots(inv, 8, IMAGE_HEIGHT - 82);
        addDataSlots(data);
    }

    /** Ячейка, из которой игрок может только забрать (положить нельзя: запас набирает сам интерфейс). */
    static class TakeOnlySlot extends Slot {
        TakeOnlySlot(Container c, int i, int x, int y) {
            super(c, i, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }
    }

    public ItemStack configItem(int i) {
        return config.getItem(i);
    }

    public RedstoneMode redstoneMode() {
        return RedstoneMode.byId(data.get(0));
    }

    /** Ячейка i в режиме «предмет» (держит сами вёдра), а не «жидкость». */
    public boolean itemMode(int i) {
        return (data.get(1) & (1 << i)) != 0;
    }

    /** Ячейка настройки держит жидкость (в ней ёмкость с жидкостью и режим «жидкость»). */
    public boolean isFluidSlot(int i) {
        return !itemMode(i) && !InterfaceBlockEntity.containedFluid(config.getItem(i)).isEmpty();
    }

    /** Запас жидкости ячейки i, mB (с точностью до десятка). */
    public int fluidAmount(int i) {
        return data.get(InterfaceBlockEntity.DATA_FLUID + i) * 10;
    }

    /** Заданное количество ячейки: штуки или mB. */
    public int amount(int i) {
        return (data.get(InterfaceBlockEntity.DATA_AMOUNT_LO + i) & 0x7FFF) | ((data.get(InterfaceBlockEntity.DATA_AMOUNT_HI + i) & 0x7FFF) << 15);
    }

    /** Предел количества ячейки: жидкость — до 256 вёдер в mB, предмет — стопка. */
    public int maxAmount(int i) {
        ItemStack cfg = config.getItem(i);
        return cfg.isEmpty() ? 0 : isFluidSlot(i) ? InterfaceBlockEntity.MAX_FLUID : cfg.getMaxStackSize();
    }

    /** Сервер: задать количество ячейки (пакет InterfaceAmountPayload из окна настройки). */
    public void setAmount(int i, int value) {
        if (be != null && i >= 0 && i < InterfaceBlockEntity.SLOTS) {
            be.setAmount(i, value);
        }
    }

    /** Сервер: очистить ячейку настройки. */
    public void clearSlot(int i) {
        if (be != null && i >= 0 && i < InterfaceBlockEntity.SLOTS) {
            be.setSlot(i, ItemStack.EMPTY, 0);
        }
    }

    /** Количество по умолчанию для нового призрака: жидкость — ведро, предмет — столько, сколько в стопке курсора. */
    private int defaultAmount(int i, ItemStack stack) {
        boolean fluid = !itemMode(i) && !InterfaceBlockEntity.containedFluid(stack).isEmpty();
        return fluid ? stack.getCount() * InterfaceBlockEntity.MB_PER_BUCKET : stack.getCount();
    }

    @Override
    public void setGhost(Player player, int slotId, ItemStack stack) {
        if (isGhost(slotId) && be != null) {
            int i = this.slots.get(slotId).getContainerSlot();
            be.setSlot(i, stack, defaultAmount(i, stack.copyWithCount(1)));
        }
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id == BUTTON_REDSTONE) {
            data.set(0, redstoneMode().next(true).ordinal());
            return true;
        }
        if (id >= BUTTON_MODE && id < BUTTON_MODE + InterfaceBlockEntity.SLOTS) {
            data.set(1, data.get(1) ^ (1 << (id - BUTTON_MODE)));
            return true;
        }
        return false;
    }

    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        if (isGhost(slotId)) {
            int i = this.slots.get(slotId).getContainerSlot();
            ItemStack carried = getCarried();
            if (clickType == ClickType.QUICK_MOVE) {
                clearSlot(i);
            } else if (clickType == ClickType.PICKUP && !carried.isEmpty() && be != null) {
                // предмет в курсоре — поставить его (количество — по стопке курсора, потом его правят в окне настройки)
                be.setSlot(i, carried, defaultAmount(i, carried));
            }
            // всё остальное (колесо, перетаскивание, клик пустой рукой — он открывает окно настройки на клиенте) — мимо
            return;
        }
        super.clicked(slotId, button, clickType, player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack stack = this.slots.get(index).getItem();
        boolean fromPlayer = index >= playerStart && index < playerEnd;
        if (!fromPlayer || stack.getItem() instanceof UpgradeItem) {
            return super.quickMoveStack(player, index);
        }
        // шифт-клик из инвентаря — в ряд приёма: предмет сразу уйдёт в сеть (когда терминалы далеко)
        int inboxStart = InterfaceBlockEntity.SLOTS * 2;
        ItemStack copy = stack.copy();
        if (!moveItemStackTo(stack, inboxStart, inboxStart + InterfaceBlockEntity.SLOTS, false)) {
            return ItemStack.EMPTY;
        }
        this.slots.get(index).setChanged();
        return copy;
    }

    @Override
    public boolean stillValid(Player player) {
        return config.stillValid(player);
    }
}
