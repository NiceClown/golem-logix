package com.golemlogix.blockentity;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.function.Predicate;

/**
 * Контейнер, принадлежащий блок-сущности: помечает её изменённой и проверяет дистанцию до игрока.
 * Хранит номера ячеек (SimpleContainer.createTag их теряет и «сжимает» предметы к началу — фильтры шин и диски шкафа
 * после перезагрузки мира смещались бы).
 */
public class OwnedContainer extends SimpleContainer {
    private final BlockEntity owner;
    private final Predicate<ItemStack> filter;

    public OwnedContainer(int size, BlockEntity owner, Predicate<ItemStack> filter) {
        super(size);
        this.owner = owner;
        this.filter = filter;
    }

    @Override
    public void setChanged() {
        super.setChanged();
        owner.setChanged();
    }

    @Override
    public boolean stillValid(Player player) {
        if (owner.isRemoved() || owner.getLevel() != player.level()) {
            return false;
        }
        return player.distanceToSqr(owner.getBlockPos().getX() + 0.5, owner.getBlockPos().getY() + 0.5,
                owner.getBlockPos().getZ() + 0.5) <= 64.0;
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return filter.test(stack);
    }

    @Override
    public ListTag createTag(HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (int i = 0; i < getContainerSize(); i++) {
            ItemStack stack = getItem(i);
            if (!stack.isEmpty()) {
                CompoundTag entry = new CompoundTag();
                if (getContainerSize() > Byte.MAX_VALUE) {
                    entry.putInt("Slot", i);
                } else {
                    entry.putByte("Slot", (byte) i);
                }
                list.add(stack.save(registries, entry));
            }
        }
        return list;
    }

    /** Читает и новый формат (с номером ячейки), и старый (без него: предметы идут подряд с начала). */
    @Override
    public void fromTag(ListTag tag, HolderLookup.Provider registries) {
        clearContent();
        for (int i = 0; i < tag.size(); i++) {
            CompoundTag entry = tag.getCompound(i);
            ItemStack stack = ItemStack.parse(registries, entry).orElse(ItemStack.EMPTY);
            if (stack.isEmpty()) {
                continue;
            }
            int slot = entry.contains("Slot", Tag.TAG_ANY_NUMERIC) ? entry.getInt("Slot") : -1;
            if (slot >= 0 && slot < getContainerSize() && getItem(slot).isEmpty()) {
                setItem(slot, stack);
            } else {
                addItem(stack);
            }
        }
    }
}
