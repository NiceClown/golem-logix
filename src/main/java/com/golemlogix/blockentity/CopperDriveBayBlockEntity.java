package com.golemlogix.blockentity;

import com.golemlogix.init.ModBlockEntities;
import com.golemlogix.items.MagneticDiskItem;
import com.golemlogix.menu.Priority;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.ContainerData;
import com.golemlogix.system.DiskStorage;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Медный дисковый шкаф (многоблок 2x2): 10 ячеек (2 столбца x 5 рядов) под магнитные кассеты — диски предметов и жидкостей.
 * Живёт в главной (нижней левой) части шкафа.
 */
public class CopperDriveBayBlockEntity extends BlockEntity {
    public static final int SLOTS = 10;

    private final OwnedContainer container = new OwnedContainer(SLOTS, this, DiskStorage::isDisk) {
        @Override
        public void setChanged() {
            super.setChanged();
            updateMask();
            refreshVisuals();
        }

        /** Окно открыли — стеклянная дверца витрины распахивается (как у сундука: по числу смотрящих). */
        @Override
        public void startOpen(net.minecraft.world.entity.player.Player player) {
            if (!player.isSpectator()) {
                viewers++;
                signalViewers(viewers == 1);
            }
        }

        @Override
        public void stopOpen(net.minecraft.world.entity.player.Player player) {
            if (!player.isSpectator()) {
                viewers = Math.max(0, viewers - 1);
                signalViewers(viewers == 0);
            }
        }
    };

    /** Событие блока: сколько игроков смотрят в окно (клиенты открывают и закрывают дверцу витрины). */
    public static final int EVENT_VIEWERS = 1;
    private int viewers = 0;
    private boolean clientOpen = false;
    /** Раскрытие дверцы 0..1 (клиент). */
    public float door, doorPrev;

    public int viewers() {
        return viewers;
    }

    private void signalViewers(boolean edge) {
        if (level == null || level.isClientSide()) {
            return;
        }
        if (edge) {
            level.playSound(null, worldPosition, viewers > 0 ? net.minecraft.sounds.SoundEvents.COPPER_DOOR_OPEN
                    : net.minecraft.sounds.SoundEvents.COPPER_DOOR_CLOSE, net.minecraft.sounds.SoundSource.BLOCKS, 0.6f, 1.25f);
        }
        level.blockEvent(worldPosition, getBlockState().getBlock(), EVENT_VIEWERS, viewers);
    }

    @Override
    public boolean triggerEvent(int id, int param) {
        if (id == EVENT_VIEWERS) {
            clientOpen = param > 0;
            return true;
        }
        return super.triggerEvent(id, param);
    }

    public static void clientTick(net.minecraft.world.level.Level level, BlockPos pos, BlockState state, CopperDriveBayBlockEntity be) {
        be.doorPrev = be.door;
        be.door = Math.max(0f, Math.min(1f, be.door + (be.clientOpen ? 0.1f : -0.08f)));
    }

    /** Битовая маска занятых ячеек — нужна клиенту для отрисовки. */
    private int mask = 0;
    /** Подпись видимого состояния (ячейки + уровень заполнения), чтобы не рассылать лишнего. */
    private int visualSig = 0;
    /** Приоритет шкафа: больше число — диски этого шкафа заполняются раньше и опустошаются позже. */
    private int priority = 0;

    public CopperDriveBayBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.COPPER_DRIVE_BAY.get(), pos, state);
    }

    public int getPriority() {
        return priority;
    }

    public void setPriority(int value) {
        this.priority = Mth.clamp(value, -Priority.MAX, Priority.MAX);
        setChanged();
    }

    public ContainerData priorityData() {
        return Priority.data(this::getPriority, this::setPriority);
    }

    public Container getContainer() {
        return container;
    }

    /** Живой список дисков (изменения в стеках сохраняются). */
    public NonNullList<ItemStack> getDisks() {
        return container.getItems();
    }

    public int getMask() {
        return mask;
    }

    /** Пересчитывает, как шкаф выглядит снаружи, и при изменении отправляет клиентам. */
    public void refreshVisuals() {
        int sig = 0;
        for (int i = 0; i < SLOTS; i++) {
            ItemStack d = container.getItem(i);
            if (!d.isEmpty()) {
                int cap = Math.max(1, DiskStorage.capacity(d));
                // счётчик на самом диске (его видит и клиент)
                long stored = MagneticDiskItem.getStoredCount(d);
                int level = (int) Math.min(4, stored * 4 / cap + (stored > 0 ? 1 : 0));
                sig = sig * 31 + (i + 1) * 8 + level + 977 * BuiltInRegistries.ITEM.getId(d.getItem());
            }
        }
        if (sig != visualSig) {
            visualSig = sig;
            if (level != null && !level.isClientSide()) {
                level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
            }
        }
    }

    private void updateMask() {
        int m = 0;
        for (int i = 0; i < SLOTS; i++) {
            if (!container.getItem(i).isEmpty()) {
                m |= 1 << i;
            }
        }
        if (m != mask) {
            mask = m;
            if (level != null && !level.isClientSide()) {
                level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
            }
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("Disks", container.createTag(registries));
        tag.putInt("Priority", priority);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        container.fromTag(tag.getList("Disks", Tag.TAG_COMPOUND), registries);
        priority = tag.getInt("Priority");
        int m = 0;
        for (int i = 0; i < SLOTS; i++) {
            if (!container.getItem(i).isEmpty()) {
                m |= 1 << i;
            }
        }
        mask = m;
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        tag.putInt("Mask", mask);
        tag.put("Disks", container.createTag(registries));
        tag.putBoolean("Open", viewers > 0);
        return tag;
    }

    @Override
    public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider lookupProvider) {
        mask = tag.getInt("Mask");
        clientOpen = tag.getBoolean("Open");
        container.fromTag(tag.getList("Disks", Tag.TAG_COMPOUND), lookupProvider);
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
