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
 * Кодировщик хранит своё содержимое: запас чистых перфокарт, готовую карту и набранный рецепт (сетка и выходы) с режимом.
 * Раньше всё лежало в окне и возвращалось игроку при закрытии — стопку чистых карт приходилось носить с собой.
 */
public class PatternEncoderBlockEntity extends BlockEntity {
    private final OwnedContainer items = new OwnedContainer(PatternEncoderMenu.SLOT_COUNT, this, s -> true);
    private int mode;

    public PatternEncoderBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.PATTERN_ENCODER.get(), pos, state);
    }

    public OwnedContainer getItems() {
        return items;
    }

    /** Данные окна: 0 — режим (0 верстак, 1 обработка). */
    public ContainerData data() {
        return new ContainerData() {
            @Override
            public int get(int index) {
                return mode;
            }

            @Override
            public void set(int index, int value) {
                mode = value;
                setChanged();
            }

            @Override
            public int getCount() {
                return 1;
            }
        };
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("Items", items.createTag(registries));
        tag.putInt("Mode", mode);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        items.fromTag(tag.getList("Items", Tag.TAG_COMPOUND), registries);
        mode = tag.getInt("Mode");
    }
}
