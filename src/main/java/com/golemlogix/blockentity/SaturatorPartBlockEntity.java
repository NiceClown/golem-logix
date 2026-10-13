package com.golemlogix.blockentity;

import com.golemlogix.blocks.ManaSaturatorBlock;
import com.golemlogix.blocks.SaturatorPartBlock;
import com.golemlogix.init.ModBlockEntities;
import com.golemlogix.system.SaturatorStructure;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Что стояло на месте части до сборки машины (чтобы вернуть при разборе) и где хозяин. Раз в две секунды часть проверяет,
 * что хозяин на месте и собран, иначе возвращает прежний блок сама (так не остаются «осиротевшие» части после разрушения
 * хозяина командой или в незагруженном чанке).
 */
public class SaturatorPartBlockEntity extends BlockEntity {
    private BlockState original = Blocks.AIR.defaultBlockState();
    private BlockPos master = BlockPos.ZERO;

    public SaturatorPartBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SATURATOR_PART.get(), pos, state);
    }

    public void setup(BlockState original, BlockPos master) {
        this.original = original;
        this.master = master.immutable();
        setChanged();
    }

    public BlockState getOriginal() {
        return original;
    }

    public BlockPos getMaster() {
        return master;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, SaturatorPartBlockEntity be) {
        if ((level.getGameTime() + pos.asLong()) % 40 != 0) {
            return;
        }
        BlockPos master = SaturatorPartBlock.controllerOf(pos, state);
        if (!level.isLoaded(master)) {
            return;
        }
        BlockState ms = level.getBlockState(master);
        if (!(ms.getBlock() instanceof ManaSaturatorBlock) || !ms.getValue(ManaSaturatorBlock.FORMED)
                || ms.getValue(ManaSaturatorBlock.FACING) != state.getValue(SaturatorPartBlock.FACING)) {
            SaturatorStructure.restore(level, pos);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("Original", NbtUtils.writeBlockState(original));
        tag.putLong("Master", master.asLong());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("Original")) {
            original = NbtUtils.readBlockState(registries.lookupOrThrow(Registries.BLOCK), tag.getCompound("Original"));
        }
        master = BlockPos.of(tag.getLong("Master"));
    }
}
