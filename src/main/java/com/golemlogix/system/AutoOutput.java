package com.golemlogix.system;

import com.golemlogix.blockentity.PatternProviderBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * Auto-output of the mod's machines (on by default): a finished item goes into a neighbouring Pattern Provider first (its
 * return slots send it into the network, so a machine fed by a provider needs no Import Bus and no channel of its own),
 * and only when there is none, or it is full, into any neighbouring inventory. The machine's own blocks are skipped.
 */
public final class AutoOutput {
    /** How often a machine tries to hand out what it made (ticks). */
    public static final int INTERVAL = 10;

    private AutoOutput() {
    }

    /** Hands out as much of the stack as the neighbours of the machine's blocks take; returns the rest (the stack is not changed). */
    public static ItemStack push(Level level, Collection<BlockPos> cells, ItemStack stack) {
        if (stack.isEmpty()) {
            return ItemStack.EMPTY;
        }
        Set<BlockPos> own = new HashSet<>(cells);
        ItemStack rest = stack.copy();
        // the first pass only into Pattern Providers, the second into everything else
        for (int pass = 0; pass < 2; pass++) {
            for (BlockPos cell : cells) {
                for (Direction dir : Direction.values()) {
                    BlockPos target = cell.relative(dir);
                    if (own.contains(target) || !level.isLoaded(target)) {
                        continue;
                    }
                    boolean provider = level.getBlockEntity(target) instanceof PatternProviderBlockEntity;
                    if (provider != (pass == 0)) {
                        continue;
                    }
                    IItemHandler handler = level.getCapability(net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK, target, dir.getOpposite());
                    if (handler == null) {
                        continue;
                    }
                    rest = ItemHandlerHelper.insertItem(handler, rest, false);
                    if (rest.isEmpty()) {
                        return ItemStack.EMPTY;
                    }
                }
            }
        }
        return rest;
    }
}
