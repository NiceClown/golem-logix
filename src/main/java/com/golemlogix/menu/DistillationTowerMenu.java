package com.golemlogix.menu;

import com.golemlogix.blockentity.DistillationTowerBlockEntity;
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
 * Cryo-Distillation Tower window: the blood tank, how fast it distils and into how much Source, what it is doing; a column of up
 * to four Compressed Co-Processors on the right speeds it up for a hungry network.
 */
public class DistillationTowerMenu extends GolemMenu {
    public static final int IMAGE_WIDTH = 176;
    public static final int IMAGE_HEIGHT = 182;

    private final ContainerLevelAccess access;
    private final ContainerData data;

    public static DistillationTowerMenu fromNetwork(int id, Inventory inv, RegistryFriendlyByteBuf buf) {
        return new DistillationTowerMenu(id, inv, new SimpleContainer(UpgradeSpec.DISTILLATION.slots()), new SimpleContainerData(5),
                ContainerLevelAccess.NULL);
    }

    public DistillationTowerMenu(int id, Inventory inv, Container upgrades, ContainerData data, ContainerLevelAccess access) {
        super(ModMenus.DISTILLATION_TOWER.get(), id);
        this.access = access;
        this.data = data;
        addUpgradeSlots(upgrades, UpgradeSpec.DISTILLATION, IMAGE_WIDTH + 5, 8);
        addPlayerSlots(inv, 8, IMAGE_HEIGHT - 82);
        addDataSlots(data);
    }

    public int blood() {
        return data.get(0);
    }

    public int capacity() {
        return DistillationTowerBlockEntity.TANK;
    }

    /** mB of blood a second. */
    public int rate() {
        return data.get(1);
    }

    /** Source a second. */
    public double sourcePerSecond() {
        return data.get(2) / 10.0;
    }

    /** 0 = no blood, 1 = working, 2 = no jar with room. */
    public int status() {
        return data.get(3);
    }

    public int coprocessors() {
        return data.get(4);
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(access, player, ModBlocks.CRYO_DISTILLATION_TOWER.get());
    }
}
