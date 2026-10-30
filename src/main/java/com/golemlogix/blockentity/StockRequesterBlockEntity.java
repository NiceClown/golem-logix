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
import net.minecraft.nbt.ListTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.LinkedHashMap;
import java.util.Map;

public class StockRequesterBlockEntity extends BlockEntity {
    public static final int FILTER_SLOTS = 9;

    public static final int STATUS_OK = 0;
    public static final int STATUS_CRAFTING = 1;
    public static final int STATUS_NO_PATTERN = 2;
    public static final int STATUS_MISSING_INGREDIENTS = 3;

    private final OwnedContainer filters = new OwnedContainer(FILTER_SLOTS, this, s -> true);

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
        boolean active = net.isOnline() && net.isDeviceActive(pos);
        if (!active) {
            for (int i = 0; i < FILTER_SLOTS; i++) be.status[i] = STATUS_OK;
            return;
        }

        boolean changed = false;
        for (int i = 0; i < FILTER_SLOTS; i++) {
            ItemStack ghost = be.filters.getItem(i);
            if (ghost.isEmpty()) {
                if (be.status[i] != STATUS_OK) {
                    be.status[i] = STATUS_OK;
                    changed = true;
                }
                continue;
            }

            // no amount set (or 0): the slot only names the item and orders nothing
            int target = ghost.getOrDefault(ModDataComponents.KEEP_AMOUNT.get(), 0);
            if (target <= 0) {
                if (be.status[i] != STATUS_OK) {
                    be.status[i] = STATUS_OK;
                    changed = true;
                }
                continue;
            }
            ItemStack plainGhost = Fuzzy.plain(ghost);
            // a fluid's key is the fluid with 1 mB, as the patterns index it (Ingredients.key)
            ItemKey key = Ingredients.key(plainGhost);

            long stock;
            if (FluidGhostItem.is(plainGhost) || Ingredients.isFluid(key)) {
                var fluid = FluidGhostItem.is(plainGhost) ? FluidGhostItem.fluid(plainGhost).getFluid() : Ingredients.fluid(key, 1).getFluid();
                stock = net.fluidCountOf(fluid);
            } else {
                stock = net.itemCountOf(key);
            }

            int oldStatus = be.status[i];
            if (stock >= target) {
                be.status[i] = STATUS_OK;
            } else {
                if (net.busCraftPending(pos, key)) {
                    be.status[i] = STATUS_CRAFTING;
                } else if (!net.canCraft(key)) {
                    be.status[i] = STATUS_NO_PATTERN;
                } else {
                    long need = target - stock;
                    // a batch: up to a stack of items, up to 16 buckets of a fluid (in mB)
                    long batch = Math.min(need, Ingredients.isFluid(key) ? 16_000 : 64);
                    Map<ItemKey, Long> order = new LinkedHashMap<>();
                    order.put(key, batch);
                    var result = net.startCrafting(order, null, pos);
                    if (result.type() == GolemNetwork.StartType.STARTED) {
                        be.status[i] = STATUS_CRAFTING;
                    } else if (result.type() == GolemNetwork.StartType.MISSING || result.type() == GolemNetwork.StartType.RESERVE_FAILED) {
                        be.status[i] = STATUS_MISSING_INGREDIENTS;
                    } else if (result.type() == GolemNetwork.StartType.NO_PATTERN) {
                        be.status[i] = STATUS_NO_PATTERN;
                    } else {
                        be.status[i] = STATUS_CRAFTING;
                    }
                }
            }
            if (be.status[i] != oldStatus) {
                changed = true;
            }
        }

        if (changed) {
            be.setChanged();
            level.sendBlockUpdated(pos, state, state, 3);
        }
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

    protected void saveTag(CompoundTag tag, HolderLookup.Provider registries) {
        tag.put("Filters", filters.createTag(registries));
        tag.putIntArray("Status", status);
    }

    protected void loadTag(CompoundTag tag, HolderLookup.Provider registries) {
        filters.fromTag(tag.getListOrEmpty("Filters"), registries);
        int[] st = tag.getIntArray("Status").orElse(new int[0]);
        for (int i = 0; i < FILTER_SLOTS && i < st.length; i++) {
            status[i] = st[i];
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        tag.putIntArray("Status", status);
        return tag;
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
