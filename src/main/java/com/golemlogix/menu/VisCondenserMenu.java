package com.golemlogix.menu;

import com.golemlogix.blockentity.VisCondenserBlockEntity;
import com.golemlogix.init.ModBlocks;
import com.golemlogix.init.ModMenus;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SimpleContainerData;

/**
 * Vis Condenser window: live chunk Aura / Flux telemetry, percent vs. exact threshold toggle, and steppers.
 */
public class VisCondenserMenu extends GolemMenu {
    public static final int IMAGE_WIDTH = 176;
    public static final int IMAGE_HEIGHT = 182;
    public static final int DATA_COUNT = 9;

    public static final int BUTTON_MODE = 1;
    public static final int BUTTON_MINUS_10 = 2;
    public static final int BUTTON_MINUS_5 = 3;
    public static final int BUTTON_MINUS_1 = 4;
    public static final int BUTTON_PLUS_1 = 5;
    public static final int BUTTON_PLUS_5 = 6;
    public static final int BUTTON_PLUS_10 = 7;

    private final ContainerLevelAccess access;
    private final ContainerData data;

    public static VisCondenserMenu fromNetwork(int id, Inventory inv, RegistryFriendlyByteBuf buf) {
        return new VisCondenserMenu(id, inv, new SimpleContainerData(DATA_COUNT), ContainerLevelAccess.NULL);
    }

    public VisCondenserMenu(int id, Inventory inv, ContainerData data, ContainerLevelAccess access) {
        super(ModMenus.VIS_CONDENSER.get(), id);
        this.access = access;
        this.data = data;
        addPlayerSlots(inv, 8, IMAGE_HEIGHT - 82);
        addDataSlots(data);
    }

    public double vis() {
        return data.get(0) / 10.0;
    }

    public int base() {
        return data.get(1);
    }

    public double flux() {
        return data.get(2) / 10.0;
    }

    public int threshold() {
        return data.get(3);
    }

    public int mode() {
        return data.get(4);
    }

    public boolean isPercentMode() {
        return mode() == VisCondenserBlockEntity.MODE_PERCENT;
    }

    public int status() {
        return data.get(5);
    }

    public double sourcePerSecond() {
        return data.get(6) / 10.0;
    }

    public boolean lowVisWarning() {
        return (data.get(7) & 1) != 0;
    }

    public boolean pausedByFloor() {
        return (data.get(7) & 2) != 0;
    }

    public boolean redstonePowered() {
        return (data.get(7) & 4) != 0;
    }

    public int lowVisPercent() {
        return data.get(8);
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        return switch (id) {
            case BUTTON_MODE -> {
                data.set(4, isPercentMode() ? VisCondenserBlockEntity.MODE_EXACT : VisCondenserBlockEntity.MODE_PERCENT);
                yield true;
            }
            case BUTTON_MINUS_10 -> {
                data.set(3, threshold() - 10);
                yield true;
            }
            case BUTTON_MINUS_5 -> {
                data.set(3, threshold() - 5);
                yield true;
            }
            case BUTTON_MINUS_1 -> {
                data.set(3, threshold() - 1);
                yield true;
            }
            case BUTTON_PLUS_1 -> {
                data.set(3, threshold() + 1);
                yield true;
            }
            case BUTTON_PLUS_5 -> {
                data.set(3, threshold() + 5);
                yield true;
            }
            case BUTTON_PLUS_10 -> {
                data.set(3, threshold() + 10);
                yield true;
            }
            default -> false;
        };
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(access, player, ModBlocks.VIS_CONDENSER.get());
    }
}
