package com.golemlogix.blockentity;

import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

/**
 * Tracks how many players currently have a block entity's screen open, broadcasting
 * {@link #EVENT_VIEWERS} so clients animate doors, drawers, or holograms smoothly.
 */
final class ViewerTracker {
    static final int EVENT_VIEWERS = 1;

    private int viewers;
    private boolean clientOpen;

    int viewers() {
        return viewers;
    }

    boolean isOpen() {
        return viewers > 0;
    }

    boolean isClientOpen() {
        return clientOpen;
    }

    void setClientOpen(boolean open) {
        clientOpen = open;
    }

    void open(BlockEntity be, @Nullable Runnable onFirstOpen) {
        viewers++;
        Level level = be.getLevel();
        if (level != null && !level.isClientSide()) {
            if (viewers == 1 && onFirstOpen != null) {
                onFirstOpen.run();
            }
            level.blockEvent(be.getBlockPos(), be.getBlockState().getBlock(), EVENT_VIEWERS, viewers);
        }
    }

    void close(BlockEntity be, @Nullable Runnable onLastClose) {
        if (viewers == 0) {
            return;
        }
        viewers--;
        Level level = be.getLevel();
        if (level != null && !level.isClientSide()) {
            if (viewers == 0 && onLastClose != null) {
                onLastClose.run();
            }
            level.blockEvent(be.getBlockPos(), be.getBlockState().getBlock(), EVENT_VIEWERS, viewers);
        }
    }

    boolean handleEvent(int id, int param) {
        if (id == EVENT_VIEWERS) {
            clientOpen = param > 0;
            return true;
        }
        return false;
    }
}
