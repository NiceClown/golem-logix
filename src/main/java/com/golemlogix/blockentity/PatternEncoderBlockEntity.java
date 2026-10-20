package com.golemlogix.blockentity;

import com.golemlogix.init.ModBlockEntities;
import com.golemlogix.menu.PatternEncoderMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The encoder keeps its contents: the stock of blank punch cards, the finished card and the entered recipe (grid and outputs) with its mode.
 * Before, everything lived in the window and went back to the player on close, so the stack of blank cards had to be carried around.
 */
public class PatternEncoderBlockEntity extends BlockEntity {
    private final OwnedContainer items = new OwnedContainer(PatternEncoderMenu.SLOT_COUNT, this, s -> true);
    private int mode;
    /** Fuzzy mode of the entered recipe (0 off, 1 on). */
    private int fuzzy;

    public PatternEncoderBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.PATTERN_ENCODER.get(), pos, state);
    }

    public OwnedContainer getItems() {
        return items;
    }

    /** Window data: 0 = mode (0 crafting table, 1 processing), 1 = fuzzy mode (0 off, 1 on). */
    public ContainerData data() {
        return new ContainerData() {
            @Override
            public int get(int index) {
                return index == 1 ? fuzzy : mode;
            }

            @Override
            public void set(int index, int value) {
                if (index == 1) {
                    fuzzy = value;
                } else {
                    mode = value;
                }
                setChanged();
            }

            @Override
            public int getCount() {
                return 2;
            }
        };
    }

    protected void saveTag(CompoundTag tag, HolderLookup.Provider registries) {
        tag.put("Items", items.createTag(registries));
        tag.putInt("Mode", mode);
        tag.putInt("Fuzzy", fuzzy);
    }

    protected void loadTag(CompoundTag tag, HolderLookup.Provider registries) {
        items.fromTag(tag.getList("Items", Tag.TAG_COMPOUND), registries);
        mode = tag.getIntOr("Mode", 0);
        fuzzy = tag.getIntOr("Fuzzy", 0);
    }

    // ---- 1.21.6+: the game's ValueInput/ValueOutput over the CompoundTag code above

    @Override
    protected void saveAdditional(net.minecraft.world.level.storage.ValueOutput output) {
        super.saveAdditional(output);
        CompoundTag tag = new CompoundTag();
        saveTag(tag, com.golemlogix.system.NbtBridge.registries(this));
        com.golemlogix.system.NbtBridge.write(output, tag);
    }

    @Override
    protected void loadAdditional(net.minecraft.world.level.storage.ValueInput input) {
        super.loadAdditional(input);
        loadTag(com.golemlogix.system.NbtBridge.read(input), input.lookup());
    }
}
