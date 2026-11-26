package com.golemlogix.blockentity;

import com.golemlogix.init.ModBlockEntities;
import com.golemlogix.init.ModDataComponents;
import com.golemlogix.items.FluidGhostItem;
import com.golemlogix.system.Fuzzy;
import com.golemlogix.system.GolemNetwork;
import com.golemlogix.system.Ingredients;
import com.golemlogix.system.ItemKey;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.FluidStack;

import java.util.Arrays;

/**
 * Stock Requester: maintains target stock levels for up to 9 configured items or fluids by dispatching
 * autocraft orders whenever network stock drops below the slot's keep amount.
 */
public class StockRequesterBlockEntity extends BlockEntity {
    public static final int FILTER_SLOTS = 9;
    public static final int STATUS_OK = 0;
    public static final int STATUS_CRAFTING = 1;
    public static final int STATUS_NO_PATTERN = 2;
    public static final int STATUS_MISSING_INGREDIENTS = 3;

    /** Synced to clients so the ledger pages render the configured entries. */
    private final OwnedContainer filters = new OwnedContainer(FILTER_SLOTS, this, s -> true) {
        @Override
        public void setChanged() {
            super.setChanged();
            BlockEntities.sendUpdate(StockRequesterBlockEntity.this);
        }
    };

    private final int[] status = new int[FILTER_SLOTS];

    public StockRequesterBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.STOCK_REQUESTER.get(), pos, state);
    }

    public Container getFilters() {
        return filters;
    }

    public int getSlotStatus(int slot) {
        return slot >= 0 && slot < FILTER_SLOTS ? status[slot] : 0;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, StockRequesterBlockEntity be) {
        if ((level.getGameTime() + pos.asLong()) % 10 != 0) {
            return;
        }
        GolemNetwork net = GolemNetwork.get(level, pos);
        if (!net.isOnline() || !net.isDeviceActive(pos)) {
            be.resetOffline(level, pos, state);
            return;
        }

        boolean changed = false;
        boolean noCpu = false;
        for (int i = 0; i < FILTER_SLOTS; i++) {
            int oldStatus = be.status[i];
            int newStatus = be.evaluateSlot(net, pos, i, noCpu);
            if (newStatus < 0) {
                noCpu = true;
                newStatus = STATUS_CRAFTING;
            }
            if (newStatus != oldStatus) {
                be.status[i] = newStatus;
                changed = true;
            }
        }
        if (changed) {
            be.setChanged();
            level.sendBlockUpdated(pos, state, state, 3);
        }
    }

    private void resetOffline(Level level, BlockPos pos, BlockState state) {
        boolean reset = false;
        for (int i = 0; i < FILTER_SLOTS; i++) {
            if (status[i] != STATUS_OK) {
                status[i] = STATUS_OK;
                reset = true;
            }
        }
        if (reset) {
            setChanged();
            level.sendBlockUpdated(pos, state, state, 3);
        }
    }

    /** Evaluates one filter slot; returns the new status, or {@code -1} to signal NO_CPU (stored as STATUS_CRAFTING). */
    private int evaluateSlot(GolemNetwork net, BlockPos pos, int slot, boolean noCpu) {
        ItemStack ghost = filters.getItem(slot);
        if (ghost.isEmpty()) {
            return STATUS_OK;
        }
        int target = ghost.getOrDefault(ModDataComponents.KEEP_AMOUNT.get(), 0);
        if (target <= 0) {
            return STATUS_OK;
        }
        ItemStack plainGhost = Fuzzy.plain(ghost);
        ItemKey key = Ingredients.key(plainGhost);
        long stock;
        if (FluidGhostItem.is(plainGhost) || Ingredients.isFluid(key)) {
            FluidStack fluid = FluidGhostItem.is(plainGhost) ? FluidGhostItem.fluid(plainGhost) : Ingredients.fluid(key, 1);
            stock = net.fluidCountOf(fluid);
        } else {
            stock = net.itemCountOf(key);
        }
        if (stock >= target) {
            return STATUS_OK;
        }
        if (net.busCraftPending(pos, key)) {
            return STATUS_CRAFTING;
        }
        if (!net.canCraft(key)) {
            return STATUS_NO_PATTERN;
        }
        if (noCpu) {
            return STATUS_CRAFTING;
        }
        long batch = Math.min(target - stock, Ingredients.isFluid(key) ? 16_000 : 64);
        GolemNetwork.StartType result = BlockEntities.requestCraft(net, pos, key, batch).type();
        return switch (result) {
            case STARTED -> STATUS_CRAFTING;
            case MISSING, RESERVE_FAILED -> STATUS_MISSING_INGREDIENTS;
            case NO_PATTERN -> STATUS_NO_PATTERN;
            case NO_CPU -> -1;
            default -> STATUS_CRAFTING;
        };
    }

    public ContainerData menuData() {
        return new ContainerData() {
            @Override
            public int get(int index) {
                return index >= 0 && index < FILTER_SLOTS ? status[index] : 0;
            }

            @Override
            public void set(int index, int value) {
                if (index >= 0 && index < FILTER_SLOTS) {
                    status[index] = value;
                }
            }

            @Override
            public int getCount() {
                return FILTER_SLOTS;
            }
        };
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("Filters", filters.createTag(registries));
        tag.putIntArray("Status", status);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        filters.fromTag(tag.getList("Filters", Tag.TAG_COMPOUND), registries);
        Arrays.fill(status, STATUS_OK);
        int[] st = tag.getIntArray("Status");
        System.arraycopy(st, 0, status, 0, Math.min(st.length, FILTER_SLOTS));
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        tag.put("Filters", filters.createTag(registries));
        tag.putIntArray("Status", status);
        return tag;
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
