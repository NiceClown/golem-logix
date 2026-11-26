package com.golemlogix.blockentity;

import com.golemlogix.crafting.CraftingJob;
import com.golemlogix.system.GolemNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * Shared helpers for autocrafting CPUs ({@link CryoCalculatorBlockEntity} and {@link CryoMainframeBlockEntity}):
 * job refunding, dev-visual item lookup, and client spin-up / foreman animation state.
 */
final class CraftingCpus {
    private CraftingCpus() {
    }

    /** Returns a cancelled or broken job's reserved items to {@code net}, dropping only what does not fit. */
    static void refundAndDrop(Level level, BlockPos pos, CraftingJob job, GolemNetwork net, double yOffset) {
        double x = pos.getX() + 0.5;
        double y = pos.getY() + yOffset;
        double z = pos.getZ() + 0.5;
        for (ItemStack overflow : job.refundAll(net)) {
            Containers.dropItemStack(level, x, y, z, overflow);
        }
    }

    /** Resolves an optional registry item ID used by dev visual scenes. */
    static ItemStack resolveDevItem(@Nullable String itemId) {
        if (itemId == null) {
            return ItemStack.EMPTY;
        }
        Identifier id = Identifier.tryParse(itemId);
        return id == null ? ItemStack.EMPTY : new ItemStack(BuiltInRegistries.ITEM.getValue(id));
    }

    /**
     * Client-side spin-up and foreman animation shared between the calculator and the mainframe.
     * Keeps instant crafts visible for at least {@link #MIN_WORK_TICKS} ticks so animations finish smoothly.
     */
    static final class Animation {
        private static final int MIN_WORK_TICKS = 32;

        float spin;
        float spinPrev;
        float foreman;
        float foremanPrev;
        int visualWorkTimer;
        private int foremanLeft;
        private boolean foremanCalled;

        /**
         * Advances one client tick. Returns true when {@code spin} has settled back to {@code 0f}
         * so the caller can release cached display items.
         */
        boolean tick(boolean activeNow, boolean boosted) {
            spinPrev = spin;
            foremanPrev = foreman;
            if (activeNow) {
                visualWorkTimer = Math.max(visualWorkTimer, MIN_WORK_TICKS);
            } else if (visualWorkTimer > 0) {
                visualWorkTimer--;
            }
            foremanLeft = boosted && !foremanCalled
                    ? CryoCalculatorBlockEntity.FOREMAN_MIN_TICKS
                    : Math.max(0, foremanLeft - 1);
            foremanCalled = boosted;
            // the crew keeps working while the foreman urges them on
            visualWorkTimer = Math.max(visualWorkTimer, foremanLeft);

            boolean settled = false;
            if (activeNow || visualWorkTimer > 0) {
                // energetic spin-up to maximum
                spin += (1f - spin) * 0.14f;
                if (spin > 0.98f) {
                    spin = 1f;
                }
            } else {
                // smooth return to idle
                spin += (0f - spin) * 0.05f;
                if (spin < 0.005f) {
                    spin = 0f;
                    settled = true;
                }
            }
            boolean foremanWanted = (boosted || foremanLeft > 0) && spin > 0.4f;
            foreman = Math.max(0f, Math.min(1f, foreman + (foremanWanted ? 0.08f : -0.05f)));
            return settled;
        }
    }
}
