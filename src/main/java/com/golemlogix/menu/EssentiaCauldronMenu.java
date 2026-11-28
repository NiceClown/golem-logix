package com.golemlogix.menu;

import com.golemlogix.blockentity.EssentiaCauldronBlockEntity;
import com.golemlogix.init.ModBlocks;
import com.golemlogix.init.ModMenus;
import com.golemlogix.upgrade.UpgradeSpec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SimpleContainerData;

/**
 * Essentia Cryo-Cauldron window: Auram (left) and Gelum (right) gauges, Ice block cooling bar,
 * Source output rate, boil-off warning, and up to 28 Compressed Co-Processors on the right.
 */
public class EssentiaCauldronMenu extends GolemMenu {
    public static final int IMAGE_WIDTH = 176;
    public static final int IMAGE_HEIGHT = 182;

    private final ContainerLevelAccess access;
    private final ContainerData data;

    public static EssentiaCauldronMenu fromNetwork(int id, Inventory inv, RegistryFriendlyByteBuf buf) {
        return new EssentiaCauldronMenu(id, inv, new SimpleContainer(UpgradeSpec.ESSENTIA_CAULDRON.slots()),
                new SimpleContainerData(EssentiaCauldronBlockEntity.DATA_COUNT), ContainerLevelAccess.NULL);
    }

    public EssentiaCauldronMenu(int id, Inventory inv, Container upgrades, ContainerData data, ContainerLevelAccess access) {
        super(ModMenus.ESSENTIA_CAULDRON.get(), id);
        this.access = access;
        this.data = data;
        addUpgradeSlots(upgrades, UpgradeSpec.ESSENTIA_CAULDRON, IMAGE_WIDTH + 5, 8);
        addPlayerSlots(inv, 8, IMAGE_HEIGHT - 82);
        addDataSlots(data);
    }

    public int auram() {
        return data.get(0);
    }

    public int gelum() {
        return data.get(1);
    }

    public int capacity() {
        return EssentiaCauldronBlockEntity.TANK_CAPACITY;
    }

    public int iceTier() {
        return data.get(2);
    }

    public int icePercent() {
        return data.get(3);
    }

    public double sourcePerSecond() {
        return unpackInt(data, 4, 8) / 10.0;
    }

    public int status() {
        return data.get(5);
    }

    public int coprocessors() {
        return data.get(6);
    }

    public boolean boilOff() {
        return data.get(7) != 0;
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(access, player, ModBlocks.ESSENTIA_CAULDRON.get());
    }
}
