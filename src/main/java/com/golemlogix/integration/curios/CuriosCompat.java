package com.golemlogix.integration.curios;

import com.golemlogix.items.Chronometers;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotResult;

/**
 * Curios: the Portable Chronometer has its own slot "chronometer" (data/golem_logix/curios: the slot and its player assignment,
 * the item tag curios:chronometer). Only called when Curios is loaded (Chronometers.CURIOS).
 */
public final class CuriosCompat {
    private CuriosCompat() {
    }

    /** The Chronometer worn in a Curios slot (the live stack: charge changes go to the slot), or EMPTY. */
    public static ItemStack chronometer(Player player) {
        return CuriosApi.getCuriosInventory(player)
                .flatMap(h -> h.findFirstCurio(Chronometers::is))
                .map(SlotResult::stack)
                .orElse(ItemStack.EMPTY);
    }

    /** Puts the stack into the player's Chronometer slot (game tests); false if the player has no such slot. */
    public static boolean wear(Player player, ItemStack stack) {
        return CuriosApi.getCuriosInventory(player)
                .flatMap(h -> {
                    // a test's fake player never joined a level: Curios has not built its slots yet
                    if (h.getCurios().isEmpty()) {
                        h.reset();
                    }
                    return h.getStacksHandler("chronometer");
                })
                .map(slots -> {
                    slots.getStacks().setStackInSlot(0, stack);
                    return true;
                })
                .orElse(false);
    }

    /** What Curios gives this player (for test messages): the slot ids of its inventory, or "no inventory". */
    public static String describe(Player player) {
        return CuriosApi.getCuriosInventory(player).map(h -> "slots " + h.getCurios().keySet() + ", for players "
                + CuriosApi.getPlayerSlots(player).keySet()).orElse("no inventory");
    }
}
