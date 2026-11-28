package com.golemlogix.blockentity;

import com.golemlogix.blocks.RemovalAware;
import com.golemlogix.items.FluidGhostItem;
import com.golemlogix.system.GolemNetwork;
import com.golemlogix.system.ItemKey;
import com.golemlogix.system.NbtBridge;
import com.golemlogix.system.Transfers;
import com.golemlogix.upgrade.RedstoneControl;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.fluid.FluidStacksResourceHandler;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.IntFunction;

/**
 * Shared block-entity plumbing: cached directions, client update broadcasts, ValueInput/ValueOutput
 * NBT bridging, fluid-slot serialization, redstone polling, slot ordering, and chunk-loaded guards.
 */
final class BlockEntities {
    static final Direction[] DIRECTIONS = Direction.values();
    private static final Direction[][] SINGLE_DIRECTIONS = new Direction[DIRECTIONS.length][];

    static {
        for (int i = 0; i < DIRECTIONS.length; i++) {
            SINGLE_DIRECTIONS[i] = new Direction[]{DIRECTIONS[i]};
        }
    }

    private BlockEntities() {
    }

    /** All six directions when {@code only == null}, otherwise a pre-allocated single-element array for {@code only}. */
    static Direction[] directions(@Nullable Direction only) {
        return only == null ? DIRECTIONS : SINGLE_DIRECTIONS[only.ordinal()];
    }

    /** Sends a block-entity update packet to tracking clients with default flags (3). */
    static void sendUpdate(BlockEntity be) {
        sendUpdate(be, 3);
    }

    /** Sends a block-entity update packet to tracking clients when running on the server. */
    static void sendUpdate(BlockEntity be, int flags) {
        Level level = be.getLevel();
        if (level != null && !level.isClientSide()) {
            BlockState state = be.getBlockState();
            level.sendBlockUpdated(be.getBlockPos(), state, state, flags);
        }
    }

    /** Delegates NeoForge's 1.21.5+ pre-removal hook to a {@link RemovalAware} block while contents are still intact. */
    static void onPreRemove(BlockEntity be, BlockPos pos, BlockState state) {
        Level level = be.getLevel();
        if (level != null && state.getBlock() instanceof RemovalAware aware) {
            aware.onRemoved(state, level, pos);
        }
    }

    /** Bridges a CompoundTag-based {@code saveTag(tag, registries)} into NeoForge's {@link ValueOutput}. */
    static void save(BlockEntity be, ValueOutput output, BiConsumer<CompoundTag, HolderLookup.Provider> writer) {
        CompoundTag tag = new CompoundTag();
        writer.accept(tag, NbtBridge.registries(be));
        NbtBridge.write(output, tag);
    }

    /** Bridges a registry-free {@code saveTag(tag)} into NeoForge's {@link ValueOutput}. */
    static void save(ValueOutput output, Consumer<CompoundTag> writer) {
        CompoundTag tag = new CompoundTag();
        writer.accept(tag);
        NbtBridge.write(output, tag);
    }

    /** Bridges NeoForge's {@link ValueInput} into a CompoundTag-based {@code loadTag(tag, registries)}. */
    static void load(ValueInput input, BiConsumer<CompoundTag, HolderLookup.Provider> reader) {
        reader.accept(NbtBridge.read(input), input.lookup());
    }

    /** Bridges NeoForge's {@link ValueInput} into a registry-free {@code loadTag(tag)}. */
    static void load(ValueInput input, Consumer<CompoundTag> reader) {
        reader.accept(NbtBridge.read(input));
    }

    /** Writes all non-empty tanks of a multi-slot handler as a {@link ListTag} keyed by byte {@code "Slot"}. */
    static void saveFluidSlots(CompoundTag tag, String key, FluidStacksResourceHandler handler, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        int size = handler.size();
        for (int i = 0; i < size; i++) {
            FluidStack fluid = Transfers.getFluid(handler, i);
            if (!fluid.isEmpty()) {
                CompoundTag entry = (CompoundTag) NbtBridge.saveFluid(fluid, registries);
                entry.putByte("Slot", (byte) i);
                list.add(entry);
            }
        }
        tag.put(key, list);
    }

    /** Clears and restores a multi-slot fluid handler from a {@link ListTag} written by {@link #saveFluidSlots}. */
    static void loadFluidSlots(CompoundTag tag, String key, FluidStacksResourceHandler handler, HolderLookup.Provider registries) {
        int size = handler.size();
        for (int i = 0; i < size; i++) {
            handler.set(i, FluidResource.EMPTY, 0);
        }
        ListTag list = tag.getListOrEmpty(key);
        for (int k = 0; k < list.size(); k++) {
            CompoundTag entry = list.getCompoundOrEmpty(k);
            int slot = entry.getByteOr("Slot", (byte) 0);
            if (slot >= 0 && slot < size) {
                FluidStack fluid = NbtBridge.loadFluid(registries, entry).orElse(FluidStack.EMPTY);
                handler.set(slot, FluidResource.of(fluid), fluid.getAmount());
            }
        }
    }

    /** Extracts the fluid represented by a ghost fluid item or filled fluid container, or {@link FluidStack#EMPTY}. */
    static FluidStack ghostFluid(ItemStack stack) {
        if (stack.isEmpty()) {
            return FluidStack.EMPTY;
        }
        FluidStack ghost = FluidGhostItem.fluid(stack);
        return ghost.isEmpty() ? Transfers.fluidOf(stack) : ghost;
    }

    /** Polls redstone neighbor signal and updates edge-detection state when a Redstone Module is installed. */
    static boolean pollRedstone(Level level, BlockPos pos, boolean hasRedstone, RedstoneControl redstone) {
        if (!hasRedstone) {
            return false;
        }
        boolean powered = level.hasNeighborSignal(pos);
        redstone.observe(powered);
        return powered;
    }

    /** Populates {@code out} with filter slot indices in IN_ORDER, ROUND_ROBIN, or RANDOM traversal order. */
    static void fillSlotOrder(int[] out, int order, int next, RandomSource random) {
        int n = out.length;
        int start = order == FormationBusBlockEntity.ROUND_ROBIN ? Math.floorMod(next, n) : 0;
        for (int k = 0; k < n; k++) {
            out[k] = (start + k) % n;
        }
        if (order == FormationBusBlockEntity.RANDOM) {
            for (int k = n - 1; k > 0; k--) {
                int r = random.nextInt(k + 1);
                int tmp = out[k];
                out[k] = out[r];
                out[r] = tmp;
            }
        }
    }

    /** Returns true only when every cell of a multiblock is currently loaded in {@code level}. */
    static boolean allLoaded(Level level, int count, IntFunction<BlockPos> cellAt) {
        for (int i = 0; i < count; i++) {
            if (!level.isLoaded(cellAt.apply(i))) {
                return false;
            }
        }
        return true;
    }

    /** Orders a single target from a device/bus using an unmodifiable singleton target map. */
    static GolemNetwork.StartResult requestCraft(GolemNetwork net, GlobalPos busPos, ItemKey key, long amount) {
        return net.startCrafting(Map.of(key, amount), null, busPos);
    }

    /** Drops and clears each container on the server. */
    static void dropContainers(@Nullable Level level, BlockPos pos, Container... containers) {
        if (level == null || level.isClientSide()) {
            return;
        }
        for (Container container : containers) {
            Containers.dropContents(level, pos, container);
        }
    }
}
