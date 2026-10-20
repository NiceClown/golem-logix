package com.golemlogix.system;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

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

    private static net.minecraft.resources.RegistryOps<net.minecraft.nbt.Tag> ops(HolderLookup.Provider registries) {
        return registries.createSerializationContext(net.minecraft.nbt.NbtOps.INSTANCE);
    }

    public static net.minecraft.nbt.Tag saveItem(net.minecraft.world.item.ItemStack stack, HolderLookup.Provider registries) {
        return net.minecraft.world.item.ItemStack.CODEC.encodeStart(ops(registries), stack).getOrThrow();
    }

    /** The item's tag merged into an existing compound (a slot entry). */
    public static CompoundTag saveItemInto(net.minecraft.world.item.ItemStack stack, HolderLookup.Provider registries, CompoundTag into) {
        if (saveItem(stack, registries) instanceof CompoundTag t) {
            into.merge(t);
        }
        return into;
    }

    public static java.util.Optional<net.minecraft.world.item.ItemStack> loadItem(HolderLookup.Provider registries, net.minecraft.nbt.Tag tag) {
        return net.minecraft.world.item.ItemStack.CODEC.parse(ops(registries), tag).result().filter(s -> !s.isEmpty());
    }

    public static net.minecraft.nbt.Tag saveFluid(net.neoforged.neoforge.fluids.FluidStack stack, HolderLookup.Provider registries) {
        return net.neoforged.neoforge.fluids.FluidStack.CODEC.encodeStart(ops(registries), stack).getOrThrow();
    }

    public static java.util.Optional<net.neoforged.neoforge.fluids.FluidStack> loadFluid(HolderLookup.Provider registries, net.minecraft.nbt.Tag tag) {
        return net.neoforged.neoforge.fluids.FluidStack.CODEC.parse(ops(registries), tag).result().filter(s -> !s.isEmpty());
    }

    /** A tank's fluid as a compound (empty for an empty tank). */
    public static CompoundTag saveTank(net.neoforged.neoforge.fluids.capability.templates.FluidTank tank, HolderLookup.Provider registries) {
        return tank.isEmpty() ? new CompoundTag() : (CompoundTag) saveFluid(tank.getFluid(), registries);
    }

    public static void loadTank(net.neoforged.neoforge.fluids.capability.templates.FluidTank tank, HolderLookup.Provider registries, CompoundTag tag) {
        tank.setFluid(tag.isEmpty() ? net.neoforged.neoforge.fluids.FluidStack.EMPTY
                : loadFluid(registries, tag).orElse(net.neoforged.neoforge.fluids.FluidStack.EMPTY));
    }

    /** Registries for encoding items and fluids while saving. */
    public static HolderLookup.Provider registries(BlockEntity be) {
        if (be.getLevel() != null) {
            return be.getLevel().registryAccess();
        }
        var server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
        return server != null ? server.registryAccess() : RegistryAccess.EMPTY;
    }
}
