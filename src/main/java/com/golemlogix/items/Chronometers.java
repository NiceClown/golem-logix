package com.golemlogix.items;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Where the player's Portable Chronometer is: in a hand, in the Curios "chronometer" slot (when Curios is installed) or anywhere
 * in the inventory. The Curios API is touched only when the mod is loaded (CuriosCompat).
 */
public final class Chronometers {
    public static final boolean CURIOS = ModList.get().isLoaded("curios");
    /** The "slot" of a Chronometer worn in a Curios slot: not an inventory slot, nothing to lock in the terminal window. */
    public static final int CURIO = -1;

    /** A Chronometer and its inventory slot (0..35, 40 = offhand) or CURIO. */
    public record Found(ItemStack stack, int slot) {
    }

    private Chronometers() {
    }

    /** The Chronometer the hotkey opens: the one in the main hand, the offhand, the Curios slot, then the first in the inventory. */
    @Nullable
    public static Found find(Player player) {
        Inventory inv = player.getInventory();
        if (is(inv.getSelected())) {
            return new Found(inv.getSelected(), inv.selected);
        }
        if (is(player.getOffhandItem())) {
            return new Found(player.getOffhandItem(), Inventory.SLOT_OFFHAND);
        }
        ItemStack worn = worn(player);
        if (!worn.isEmpty()) {
            return new Found(worn, CURIO);
        }
        for (int i = 0; i < inv.items.size(); i++) {
            if (is(inv.items.get(i))) {
                return new Found(inv.items.get(i), i);
            }
        }
        return null;
    }

    /** Every Chronometer the player carries: the inventory, the offhand and the Curios slot. */
    public static List<ItemStack> carried(Player player) {
        List<ItemStack> out = new ArrayList<>();
        for (ItemStack s : player.getInventory().getNonEquipmentItems()) {
            if (is(s)) {
                out.add(s);
            }
        }
        for (ItemStack s : player.getInventory().offhand) {
            if (is(s)) {
                out.add(s);
            }
        }
        ItemStack worn = worn(player);
        if (!worn.isEmpty()) {
            out.add(worn);
        }
        return out;
    }

    /** The Chronometer in the Curios slot (the live stack), or EMPTY without Curios. */
    public static ItemStack worn(Player player) {
        return CURIOS ? com.golemlogix.integration.curios.CuriosCompat.chronometer(player) : ItemStack.EMPTY;
    }

    public static boolean is(ItemStack stack) {
        return stack.getItem() instanceof PortableChronometerItem;
    }
}
