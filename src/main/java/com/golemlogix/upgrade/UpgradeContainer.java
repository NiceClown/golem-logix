package com.golemlogix.upgrade;

import com.golemlogix.GolemLogixMod;
import com.golemlogix.blockentity.OwnedContainer;
import com.golemlogix.items.UpgradeItem;
import com.golemlogix.system.NbtBridge;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * A machine's upgrade slots: one upgrade in each (speed upgrades may share one, see UpgradeSpec), every slot for one kind (UpgradeSpec.typeAt; the window shows its ghost).
 * Upgrades from an older save are put into their own slots on loading; one with no slot left (the Annihilation Bus took speed
 * upgrades once) waits in strays until the machine drops it.
 */
@EventBusSubscriber(modid = GolemLogixMod.MOD_ID)
public class UpgradeContainer extends OwnedContainer {
    private final UpgradeSpec spec;
    private final Runnable onChange;
    private final List<ItemStack> strays = new ArrayList<>();

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

    /** Whether an upgrade can go into slot (considering what is already in the others). */
    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return accepts(spec, stack, this, slot);
    }

    /** The shared check (the client copy of the menu uses it too): the slot is for this kind of upgrade. */
    public static boolean accepts(UpgradeSpec spec, ItemStack stack, Container current, int slot) {
        return stack.getItem() instanceof UpgradeItem up && spec.typeAt(slot) == up.type();
    }

    /** Loads every upgrade into a slot of its own kind, where it was if that slot is for it (saves from before slots had kinds). */
    @Override
    public void fromTag(ListTag tag, HolderLookup.Provider registries) {
        clearContent();
        strays.clear();
        List<ItemStack> rest = new ArrayList<>();
        for (int i = 0; i < tag.size(); i++) {
            CompoundTag entry = tag.getCompoundOrEmpty(i);
            ItemStack stack = NbtBridge.loadItem(registries, entry).orElse(ItemStack.EMPTY);
            if (stack.isEmpty()) {
                continue;
            }
            int slot = entry.getIntOr("Slot", -1);
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
        packRest(rest);
        if (!strays.isEmpty() && (owner().getLevel() == null || !owner().getLevel().isClientSide())) {
            PENDING.add(this);
        }
    }

    /** Packs unplaced or migrated upgrades into matching slots; anything that cannot fit is preserved in strays. */
    private void packRest(List<ItemStack> rest) {
        for (ItemStack s : rest) {
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
    }

    /** The slots, and the strays after them without a slot: a stray is kept by a save until it can be dropped, never lost. */
    @Override
    public ListTag createTag(HolderLookup.Provider registries) {
        ListTag list = super.createTag(registries);
        for (ItemStack s : strays) {
            CompoundTag entry = new CompoundTag();
            entry.putByte("Slot", (byte) -1);
            list.add(NbtBridge.saveItemInto(s, registries, entry));
        }
        return list;
    }

    /**
     * Containers that loaded strays: on the next server tick they are dropped in front of their machine, whatever the machine is
     * (only the Annihilation Bus used to drop them; a bus or tower from an old save lost what did not fit its one speed slot).
     */
    private static final Set<UpgradeContainer> PENDING = Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (PENDING.isEmpty()) {
            return;
        }
        List<UpgradeContainer> now;
        synchronized (PENDING) {
            now = List.copyOf(PENDING);
        }
        for (UpgradeContainer c : now) {
            BlockEntity owner = c.owner();
            if (owner.getLevel() == null && !owner.isRemoved()) {
                continue; // Not placed in a level yet
            }
            if (!owner.isRemoved() && owner.getLevel() instanceof ServerLevel level) {
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

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        PENDING.clear();
    }

    /** Strays still waiting to be dropped (tests). */
    public int strayCount() {
        return strays.size();
    }

    /** Drops the upgrades that found no slot on loading, in front of the machine. */
    public void dropStrays(Level level, BlockPos pos) {
        if (!strays.isEmpty()) {
            for (ItemStack s : strays) {
                Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, s);
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
