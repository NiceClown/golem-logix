package com.golemlogix.upgrade;

import com.golemlogix.blockentity.OwnedContainer;
import com.golemlogix.items.UpgradeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * A machine's upgrade slots: one upgrade in each (speed upgrades may share one, see UpgradeSpec), every slot for one kind (UpgradeSpec.typeAt; the window shows its ghost).
 * Upgrades from an older save are put into their own slots on loading; one with no slot left (the Annihilation Bus took speed
 * upgrades once) waits in strays until the machine drops it.
 */
@net.neoforged.fml.common.EventBusSubscriber(modid = com.golemlogix.GolemLogixMod.MOD_ID)
public class UpgradeContainer extends OwnedContainer {
    private final UpgradeSpec spec;
    private final Runnable onChange;
    private final java.util.List<ItemStack> strays = new java.util.ArrayList<>();

    public UpgradeContainer(UpgradeSpec spec, BlockEntity owner, Runnable onChange) {
        super(spec.slots(), owner, s -> true);
        this.spec = spec;
        this.onChange = onChange;
    }

    public UpgradeContainer(UpgradeSpec spec, BlockEntity owner) {
        this(spec, owner, () -> {
        });
    }

    public UpgradeSpec spec() {
        return spec;
    }

    @Override
    public int getMaxStackSize() {
        return Math.max(1, spec.perSlot(UpgradeType.SPEED));
    }

    @Override
    public int getMaxStackSize(ItemStack stack) {
        return stack.getItem() instanceof UpgradeItem up ? spec.perSlot(up.type()) : 1;
    }

    /** Whether an upgrade can go into slot slot (considering what is already in the others). */
    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return accepts(spec, stack, this, slot);
    }

    /** The shared check (the client copy of the menu uses it too): the slot is for this kind of upgrade. */
    public static boolean accepts(UpgradeSpec spec, ItemStack stack, net.minecraft.world.Container current, int slot) {
        return stack.getItem() instanceof UpgradeItem up && spec.typeAt(slot) == up.type();
    }

    /** Loads every upgrade into a slot of its own kind, where it was if that slot is for it (saves from before slots had kinds). */
    @Override
    public void fromTag(net.minecraft.nbt.ListTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        clearContent();
        strays.clear();
        java.util.List<ItemStack> rest = new java.util.ArrayList<>();
        for (int i = 0; i < tag.size(); i++) {
            net.minecraft.nbt.CompoundTag entry = tag.getCompound(i);
            ItemStack stack = ItemStack.parse(registries, entry).orElse(ItemStack.EMPTY);
            if (stack.isEmpty()) {
                continue;
            }
            int slot = entry.contains("Slot", net.minecraft.nbt.Tag.TAG_ANY_NUMERIC) ? entry.getInt("Slot") : -1;
            if (slot >= 0 && slot < getContainerSize() && getItem(slot).isEmpty() && accepts(spec, stack, this, slot)) {
                int max = getMaxStackSize(stack);
                if (stack.getCount() <= max) {
                    setItem(slot, stack);
                } else {
                    setItem(slot, stack.copyWithCount(max));
                    rest.add(stack.copyWithCount(stack.getCount() - max));
                }
            } else {
                rest.add(stack);
            }
        }
        for (ItemStack s : rest) {
            // a save from when a bus had four speed slots: they come together in the one slot; what does not fit stays a stray
            for (int i = 0; i < getContainerSize() && !s.isEmpty(); i++) {
                if (!accepts(spec, s, this, i)) {
                    continue;
                }
                ItemStack in = getItem(i);
                int room = getMaxStackSize(s) - in.getCount();
                if (room <= 0 || !in.isEmpty() && !ItemStack.isSameItemSameComponents(in, s)) {
                    continue;
                }
                int n = Math.min(room, s.getCount());
                if (in.isEmpty()) {
                    setItem(i, s.copyWithCount(n));
                } else {
                    in.grow(n);
                }
                s.shrink(n);
            }
            if (!s.isEmpty()) {
                strays.add(s);
            }
        }
        if (!strays.isEmpty() && (owner().getLevel() == null || !owner().getLevel().isClientSide())) {
            PENDING.add(this);
        }
    }

    /** The slots, and the strays after them without a slot: a stray is kept by a save until it can be dropped, never lost. */
    @Override
    public net.minecraft.nbt.ListTag createTag(net.minecraft.core.HolderLookup.Provider registries) {
        net.minecraft.nbt.ListTag list = super.createTag(registries);
        for (ItemStack s : strays) {
            net.minecraft.nbt.CompoundTag entry = new net.minecraft.nbt.CompoundTag();
            entry.putByte("Slot", (byte) -1);
            list.add(s.save(registries, entry));
        }
        return list;
    }

    /**
     * Containers that loaded strays: on the next server tick they are dropped in front of their machine, whatever the machine is
     * (only the Annihilation Bus used to drop them; a bus or tower from an old save lost what did not fit its one speed slot).
     */
    private static final java.util.Set<UpgradeContainer> PENDING = java.util.Collections.synchronizedSet(java.util.Collections.newSetFromMap(new java.util.WeakHashMap<>()));

    @net.neoforged.bus.api.SubscribeEvent
    public static void onServerTick(net.neoforged.neoforge.event.tick.ServerTickEvent.Post event) {
        if (PENDING.isEmpty()) {
            return;
        }
        java.util.List<UpgradeContainer> now;
        synchronized (PENDING) {
            now = java.util.List.copyOf(PENDING);
        }
        for (UpgradeContainer c : now) {
            BlockEntity owner = c.owner();
            if (owner.getLevel() == null && !owner.isRemoved()) {
                continue;   // not placed in a level yet
            }
            if (!owner.isRemoved() && owner.getLevel() instanceof net.minecraft.server.level.ServerLevel level) {
                if (!level.isLoaded(owner.getBlockPos())) {
                    continue;
                }
                PENDING.remove(c);
                c.dropStrays(level, owner.getBlockPos());
            } else {
                PENDING.remove(c);
            }
        }
    }

    @net.neoforged.bus.api.SubscribeEvent
    public static void onServerStopped(net.neoforged.neoforge.event.server.ServerStoppedEvent event) {
        PENDING.clear();
    }

    /** Strays still waiting to be dropped (tests). */
    public int strayCount() {
        return strays.size();
    }

    /** Drops the upgrades that found no slot on loading, in front of the machine. */
    public void dropStrays(net.minecraft.world.level.Level level, net.minecraft.core.BlockPos pos) {
        if (!strays.isEmpty()) {
            for (ItemStack s : strays) {
                net.minecraft.world.Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, s);
            }
            strays.clear();
            setChanged();
        }
    }

    public int count(UpgradeType type) {
        int n = 0;
        for (int i = 0; i < getContainerSize(); i++) {
            if (getItem(i).getItem() instanceof UpgradeItem up && up.type() == type) {
                n += getItem(i).getCount();
            }
        }
        return Math.min(n, spec.max(type));
    }

    public boolean has(UpgradeType type) {
        return count(type) > 0;
    }

    @Override
    public void setChanged() {
        super.setChanged();
        onChange.run();
    }
}
