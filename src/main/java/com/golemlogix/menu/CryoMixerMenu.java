package com.golemlogix.menu;

import com.golemlogix.blockentity.CryoMixerBlockEntity;
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

/**
 * Cryo-Mixer window: the two flasks (water, Liquid Cryo-Source), the mode (snow or ice), the pace, what it is doing, the tray with
 * the snow or ice (auto-output on by default); a column of upgrade slots on the right (Compressed Co-Processors, a Redstone Module).
 */
public class CryoMixerMenu extends GolemMenu {
    public static final int IMAGE_WIDTH = 176;
    public static final int IMAGE_HEIGHT = 182;
    public static final int OUT_X = 150, OUT_Y = 62;
    public static final int BUTTON_MODE = 1;
    public static final int BUTTON_AUTO_OUTPUT = 2;

    private final ContainerLevelAccess access;
    private final ContainerData data;

    public static CryoMixerMenu fromNetwork(int id, Inventory inv, RegistryFriendlyByteBuf buf) {
        return new CryoMixerMenu(id, inv, new SimpleContainer(1), new SimpleContainer(UpgradeSpec.MIXER.slots()), new SimpleContainerData(9),
                null, ContainerLevelAccess.NULL);
    }

    public CryoMixerMenu(int id, Inventory inv, Container output, Container upgrades, ContainerData data,
                         @org.jetbrains.annotations.Nullable CryoMixerBlockEntity be, ContainerLevelAccess access) {
        super(ModMenus.CRYO_MIXER.get(), id);
        this.access = access;
        this.data = data;
        // the tray: only taken from
        addSlot(new Slot(output, 0, OUT_X, OUT_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return false;
            }
        });
        this.containerSlotCount = 1;
        addUpgradeSlots(upgrades, UpgradeSpec.MIXER, IMAGE_WIDTH + 5, 8);
        addPlayerSlots(inv, 8, IMAGE_HEIGHT - 82);
        addDataSlots(data);
    }

    public int water() {
        return data.get(0);
    }

    public int cryo() {
        return data.get(1);
    }

    public int progress() {
        return data.get(2);
    }

    public int ticksPerBlock() {
        return Math.max(1, data.get(3));
    }

    public CryoMixerBlockEntity.Mode mode() {
        return CryoMixerBlockEntity.Mode.byId(data.get(4));
    }

    public int status() {
        return data.get(5);
    }

    public int coprocessors() {
        return data.get(6);
    }

    public RedstoneMode redstoneMode() {
        return RedstoneMode.byId(data.get(7));
    }

    public boolean autoOutput() {
        return data.get(8) != 0;
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id == BUTTON_MODE) {
            data.set(4, mode() == CryoMixerBlockEntity.Mode.SNOW ? 1 : 0);
            return true;
        }
        if (id == BUTTON_AUTO_OUTPUT) {
            data.set(8, autoOutput() ? 0 : 1);
            return true;
        }
        if (id == BUTTON_REDSTONE) {
            data.set(7, redstoneMode().next(true).ordinal());
            return true;
        }
        return false;
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(access, player, ModBlocks.CRYO_MIXER.get());
    }
}
