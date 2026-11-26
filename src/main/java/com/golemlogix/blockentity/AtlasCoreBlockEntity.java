package com.golemlogix.blockentity;

import com.golemlogix.config.GolemConfig;
import com.golemlogix.init.ModBlockEntities;
import com.golemlogix.init.ModSounds;
import com.golemlogix.system.AtlasStructure;
import com.golemlogix.system.GolemNetwork;
import com.golemlogix.system.Ingredients;
import com.golemlogix.system.ItemKey;
import com.golemlogix.system.NbtBridge;
import com.golemlogix.system.SourceHelper;
import com.golemlogix.system.Transfers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.fluids.FluidStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Atlas Core: once a second validates its array structure, draws Source to power the network, refreshes pipe lights
 * on state changes, and periodically samples cargo items/fluids for client pipe-carrier visuals.
 */
public class AtlasCoreBlockEntity extends BlockEntity {
    private static final int CARGO_SECONDS = 15, CARGO_ITEMS = 12, CARGO_FLUIDS = 4;

    private boolean powered = false;
    private int channelsUsed = 0;
    private int channelCapacity = 0;
    private int devices = 0;
    private int sourcePerSecond = 0;
    private boolean infiniteSource = false;
    private boolean duplicate = false;
    private boolean wasDuplicate = false;
    private boolean formed = false;
    private boolean initialized = false;
    /** Client electron animation phase. */
    public float orbit = 0;
    /** Sampled items and fluid buckets synced to clients for pipe-carrier crates. */
    private List<ItemStack> cargoItems = List.of(), cargoFluids = List.of();
    private int secondsSinceCargo = CARGO_SECONDS;
    private GolemNetwork cargoNetwork;
    private GolemNetwork syncedNetwork;
    private int secondsSinceSync;

    public AtlasCoreBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ATLAS_CORE.get(), pos, state);
    }

    public boolean isPowered() {
        return powered;
    }

    public int getChannelsUsed() {
        return channelsUsed;
    }

    public int getChannelCapacity() {
        return channelCapacity;
    }

    public int getDevices() {
        return devices;
    }

    public int getSourcePerSecond() {
        return sourcePerSecond;
    }

    public boolean isInfiniteSource() {
        return infiniteSource;
    }

    public boolean isFormed() {
        return formed;
    }

    public boolean isDuplicate() {
        return duplicate;
    }

    public List<ItemStack> cargoItems() {
        return cargoItems;
    }

    public List<ItemStack> cargoFluids() {
        return cargoFluids;
    }

    /** Samples up to CARGO_ITEMS items and CARGO_FLUIDS fluid buckets for client visuals. Returns true on change. */
    private boolean pickCargo(GolemNetwork net) {
        List<ItemStack> items = new ArrayList<>();
        Map<ItemKey, Long> all = net.listItems();
        int step = Math.max(1, all.size() / CARGO_ITEMS);
        int i = 0;
        for (ItemKey key : all.keySet()) {
            if (i++ % step == 0 && !Ingredients.isFluid(key)) {
                items.add(key.toStack(1));
                if (items.size() >= CARGO_ITEMS) {
                    break;
                }
            }
        }
        List<ItemStack> fluids = new ArrayList<>();
        for (FluidStack f : net.listFluids()) {
            if (fluids.size() >= CARGO_FLUIDS) {
                break;
            }
            ItemStack bucket = Transfers.filledBucket(f);
            if (!bucket.isEmpty()) {
                fluids.add(bucket);
            }
        }
        boolean changed = !ItemStack.listMatches(items, cargoItems) || !ItemStack.listMatches(fluids, cargoFluids);
        cargoItems = List.copyOf(items);
        cargoFluids = List.copyOf(fluids);
        return changed;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, AtlasCoreBlockEntity be) {
        if (be.powered && (level.getGameTime() + pos.asLong()) % 100 == 0) {
            level.playSound(null, pos, ModSounds.VACUUM_HUM.get(), SoundSource.BLOCKS, 0.35f, 1.0f);
        }
        if (be.initialized && (level.getGameTime() + pos.asLong()) % 20 != 0) {
            return;
        }
        boolean first = !be.initialized;
        be.initialized = true;
        be.update(level, pos, first);
    }

    /** Forced rescan (right-click on the core, tests); shared with every device on the network. */
    public void refresh(Level level, BlockPos pos) {
        update(level, pos, true);
    }

    private void update(Level level, BlockPos pos, boolean force) {
        GolemNetwork net = resolveNetwork(level, pos, force);
        duplicate = !net.isPrimaryCore(pos);
        int cost = duplicate ? 0 : powerCost(net);
        boolean newPowered = !duplicate && net.consumeSource(cost);

        if (!duplicate && (force || net != syncedNetwork || newPowered != powered || ++secondsSinceSync >= 10)) {
            net.syncActiveVisuals(newPowered);
            syncedNetwork = net;
            secondsSinceSync = 0;
        }
        boolean newFormed = duplicate ? AtlasStructure.check(level, pos).valid()
                : net.structure() != null && net.structure().valid();
        if (newFormed && !formed) {
            AtlasStructure.orientAtlants(level, pos);
        }
        AtlasStructure.dress(level, pos, newFormed);

        boolean newInfinite = net.hasInfiniteSource() || !SourceHelper.sourceRequired();
        int newSps = newInfinite ? 0 : cost;
        boolean persistedChanged = newFormed != formed || newPowered != powered || net.channelsUsed() != channelsUsed
                || net.channelCapacity() != channelCapacity || net.deviceCount() != devices
                || newInfinite != infiniteSource || newSps != sourcePerSecond || duplicate != wasDuplicate;
        boolean cargoChanged = false;
        if (!duplicate && (force || net != cargoNetwork || ++secondsSinceCargo >= CARGO_SECONDS)) {
            cargoNetwork = net;
            secondsSinceCargo = 0;
            cargoChanged = pickCargo(net);
        }
        wasDuplicate = duplicate;
        powered = newPowered;
        formed = newFormed;
        channelsUsed = net.channelsUsed();
        channelCapacity = net.channelCapacity();
        devices = net.deviceCount();
        infiniteSource = newInfinite;
        sourcePerSecond = newSps;
        if (persistedChanged) {
            setChanged();
        }
        if (persistedChanged || cargoChanged) {
            BlockEntities.sendUpdate(this);
        }
    }

    private static GolemNetwork resolveNetwork(Level level, BlockPos pos, boolean force) {
        GolemNetwork net = force ? GolemNetwork.refresh(level, pos) : GolemNetwork.get(level, pos);
        // casings and Atlants are not GolemNodes, so recheck structure validity here
        if (!force && net.isPrimaryCore(pos) && AtlasStructure.check(level, pos).valid() != net.structureValid()) {
            GolemNetwork.nodesChanged(level, pos);
            net = GolemNetwork.refresh(level, pos);
        }
        return net;
    }

    private static int powerCost(GolemNetwork net) {
        return GolemConfig.CORE_SOURCE_PER_SECOND.get()
                + (int) Math.ceil(net.channelsUsed() * GolemConfig.CHANNEL_SOURCE_PER_SECOND.get())
                + net.telegraphLinks() * GolemConfig.TELEGRAPH_SOURCE_PER_SECOND.get();
    }

    protected void saveTag(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putBoolean("Powered", powered);
        tag.putBoolean("Formed", formed);
        tag.putInt("ChannelsUsed", channelsUsed);
        tag.putInt("ChannelCapacity", channelCapacity);
        tag.putInt("Devices", devices);
        tag.putInt("SourcePerSecond", sourcePerSecond);
        tag.putBoolean("InfiniteSource", infiniteSource);
        tag.putBoolean("Duplicate", duplicate);
    }

    protected void loadTag(CompoundTag tag, HolderLookup.Provider registries) {
        powered = tag.getBooleanOr("Powered", false);
        formed = tag.getBooleanOr("Formed", false);
        channelsUsed = tag.getIntOr("ChannelsUsed", 0);
        channelCapacity = tag.getIntOr("ChannelCapacity", 0);
        devices = tag.getIntOr("Devices", 0);
        sourcePerSecond = tag.getIntOr("SourcePerSecond", 0);
        infiniteSource = tag.getBooleanOr("InfiniteSource", false);
        duplicate = tag.getBooleanOr("Duplicate", false);
        wasDuplicate = duplicate;
        // only in update packets (client visuals), never saved with the world
        if (tag.contains("CargoItems")) {
            cargoItems = readStacks(tag.getListOrEmpty("CargoItems"), registries);
            cargoFluids = readStacks(tag.getListOrEmpty("CargoFluids"), registries);
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        saveTag(tag, registries);
        tag.put("CargoItems", writeStacks(cargoItems, registries));
        tag.put("CargoFluids", writeStacks(cargoFluids, registries));
        return tag;
    }

    private static ListTag writeStacks(List<ItemStack> stacks, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (ItemStack s : stacks) {
            list.add(NbtBridge.saveItem(s, registries));
        }
        return list;
    }

    private static List<ItemStack> readStacks(ListTag list, HolderLookup.Provider registries) {
        List<ItemStack> out = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            ItemStack s = NbtBridge.loadItem(registries, list.getCompoundOrEmpty(i)).orElse(ItemStack.EMPTY);
            if (!s.isEmpty()) {
                out.add(s);
            }
        }
        return List.copyOf(out);
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        BlockEntities.save(this, output, this::saveTag);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        BlockEntities.load(input, this::loadTag);
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (level != null && !level.isClientSide()) {
            AtlasStructure.dress(level, pos, false);
        }
        BlockEntities.onPreRemove(this, pos, state);
    }
}
