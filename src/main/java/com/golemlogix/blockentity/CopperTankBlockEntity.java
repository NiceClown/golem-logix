package com.golemlogix.blockentity;

import com.golemlogix.init.ModBlockEntities;
import com.golemlogix.init.ModDataComponents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.SimpleFluidContent;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.fluid.FluidStacksResourceHandler;

/**
 * Copper Tank: one tank of a single fluid. Its capacity is one tank's worth, or the sum of the tanks joined in a crafting grid;
 * the fluid and the capacity go into the item when the block is broken (the loot table copies them) and come back when it is
 * placed, so a full tank can be carried away. The client gets the contents for the fluid drawn behind the glass.
 */
public class CopperTankBlockEntity extends BlockEntity {
    /** One tank's worth (mB), and the most joined tanks can hold. */
    public static final int CAPACITY = 16_000, MAX_CAPACITY = 1_024_000;

    private final Tank tank = new Tank();

    public CopperTankBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.COPPER_TANK.get(), pos, state);
    }

    /** The tank, for pipes, buses, buckets and other mods. */
    public FluidStacksResourceHandler tank() {
        return tank;
    }

    public FluidStack fluid() {
        return tank.copyToList().getFirst();
    }

    public int capacity() {
        return tank.capacity();
    }

    /** The comparator: 1 for any fluid, 15 when full. */
    public int signal() {
        int amount = fluid().getAmount();
        return amount <= 0 ? 0 : 1 + (int) (14L * amount / capacity());
    }

    private final class Tank extends FluidStacksResourceHandler {
        Tank() {
            super(1, CAPACITY);
        }

        int capacity() {
            return capacity;
        }

        void setCapacity(int capacity) {
            this.capacity = capacity;
        }

        @Override
        protected void onContentsChanged(int index, FluidStack previousContents) {
            setChanged();
            if (level != null && !level.isClientSide()) {
                level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 2);
                level.updateNeighbourForOutputSignal(worldPosition, getBlockState().getBlock());
            }
        }
    }

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        out.putInt("Capacity", tank.capacity());
        tank.serialize(out);
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        tank.setCapacity(Math.max(1, in.getIntOr("Capacity", CAPACITY)));
        tank.deserialize(in);
    }

    // ---- the item keeps the fluid and the capacity

    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder components) {
        super.collectImplicitComponents(components);
        FluidStack fluid = fluid();
        if (!fluid.isEmpty()) {
            components.set(ModDataComponents.TANK_FLUID.get(), SimpleFluidContent.copyOf(fluid));
        }
        if (tank.capacity() != CAPACITY) {
            components.set(ModDataComponents.TANK_CAPACITY.get(), tank.capacity());
        }
    }

    @Override
    protected void applyImplicitComponents(DataComponentGetter components) {
        super.applyImplicitComponents(components);
        tank.setCapacity(components.getOrDefault(ModDataComponents.TANK_CAPACITY.get(), CAPACITY));
        FluidStack fluid = components.getOrDefault(ModDataComponents.TANK_FLUID.get(), SimpleFluidContent.EMPTY).copy();
        tank.set(0, FluidResource.of(fluid), Math.min(fluid.getAmount(), tank.capacity()));
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveCustomOnly(registries);
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
