package com.golemlogix.system;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.fluid.FluidStacksResourceHandler;

import java.util.Optional;

/**
 * Block entities keep their data as a CompoundTag (the same keys as in the 1.21.1 version, so old worlds load); since 1.21.6 the game
 * hands them a ValueInput / ValueOutput instead. This reads the whole input as one tag and writes a tag into the output, key by key.
 */
public final class NbtBridge {
    private static final MapCodec<CompoundTag> WHOLE = MapCodec.assumeMapUnsafe(CompoundTag.CODEC);

    private NbtBridge() {
    }

    public static CompoundTag read(ValueInput input) {
        return input.read(WHOLE).orElseGet(CompoundTag::new);
    }

    public static void write(ValueOutput output, CompoundTag tag) {
        output.store(WHOLE, tag);
    }

    // ---- items and fluids as tags (ItemStack#save and #parse are gone since 1.21.5; the codecs remain)

    private static RegistryOps<Tag> ops(HolderLookup.Provider registries) {
        return registries.createSerializationContext(NbtOps.INSTANCE);
    }

    public static Tag saveItem(ItemStack stack, HolderLookup.Provider registries) {
        return ItemStack.CODEC.encodeStart(ops(registries), stack).getOrThrow();
    }

    /** The item's tag merged into an existing compound (a slot entry). */
    public static CompoundTag saveItemInto(ItemStack stack, HolderLookup.Provider registries, CompoundTag into) {
        if (saveItem(stack, registries) instanceof CompoundTag t) {
            into.merge(t);
        }
        return into;
    }

    public static Optional<ItemStack> loadItem(HolderLookup.Provider registries, Tag tag) {
        return ItemStack.CODEC.parse(ops(registries), tag).result().filter(s -> !s.isEmpty());
    }

    public static Tag saveFluid(FluidStack stack, HolderLookup.Provider registries) {
        return FluidStack.CODEC.encodeStart(ops(registries), stack).getOrThrow();
    }

    public static Optional<FluidStack> loadFluid(HolderLookup.Provider registries, Tag tag) {
        return FluidStack.CODEC.parse(ops(registries), tag).result().filter(s -> !s.isEmpty());
    }

    /** A tank's fluid as a compound (empty for an empty tank). */
    public static CompoundTag saveTank(FluidStacksResourceHandler tank, HolderLookup.Provider registries) {
        FluidStack f = Transfers.getFluid(tank, 0);
        return f.isEmpty() ? new CompoundTag() : (CompoundTag) saveFluid(f, registries);
    }

    public static void loadTank(FluidStacksResourceHandler tank, HolderLookup.Provider registries, CompoundTag tag) {
        FluidStack f = tag.isEmpty() ? FluidStack.EMPTY
                : loadFluid(registries, tag).orElse(FluidStack.EMPTY);
        tank.set(0, FluidResource.of(f), f.getAmount());
    }

    /** Registries for encoding items and fluids while saving. */
    public static HolderLookup.Provider registries(BlockEntity be) {
        if (be.getLevel() != null) {
            return be.getLevel().registryAccess();
        }
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        return server != null ? server.registryAccess() : RegistryAccess.EMPTY;
    }
}
