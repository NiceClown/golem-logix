package com.golemlogix.blockentity;

import com.golemlogix.config.GolemConfig;
import com.golemlogix.init.ModBlockEntities;
import com.golemlogix.init.ModSounds;
import com.golemlogix.system.AtlasStructure;
import com.golemlogix.system.GolemNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.fluids.FluidUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Atlas Core: once a second it rescans the network and draws Source to power it.
 * No Source means the whole network is off.
 */
public class AtlasCoreBlockEntity extends BlockEntity {
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
    /** Client electron animation. */
    public float orbit = 0;
    /**
     * A few items and fluid buckets of the network for the snowmen's crates in the pipes. The server picks them now and then and
     * sends them with the core's update, so client renderers never read the storage (which lives on the server thread).
     */
    private List<ItemStack> cargoItems = List.of(), cargoFluids = List.of();
    private int secondsSinceCargo = CARGO_SECONDS;
    private GolemNetwork cargoNetwork;
    private static final int CARGO_SECONDS = 15, CARGO_ITEMS = 12, CARGO_FLUIDS = 4;

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

    /** The Atlas Array is complete (synced to the client for rendering). */
    public boolean isFormed() {
        return formed;
    }

    public boolean isDuplicate() {
        return duplicate;
    }

    /** Sample items of the network (count 1), as last sent by the server; for client visuals. */
    public List<ItemStack> cargoItems() {
        return cargoItems;
    }

    /** Sample fluids of the network as filled buckets, as last sent by the server; for client visuals. */
    public List<ItemStack> cargoFluids() {
        return cargoFluids;
    }

    /** Picks up to CARGO_ITEMS item kinds spread over the whole list, and up to CARGO_FLUIDS fluids as buckets. Returns true on a change. */
    private boolean pickCargo(GolemNetwork net) {
        List<ItemStack> items = new ArrayList<>();
        Map<com.golemlogix.system.ItemKey, Long> all = net.listItems();
        int step = Math.max(1, all.size() / CARGO_ITEMS);
        int i = 0;
        for (com.golemlogix.system.ItemKey key : all.keySet()) {
            if (i++ % step == 0 && items.size() < CARGO_ITEMS && !com.golemlogix.system.Ingredients.isFluid(key)) {
                items.add(key.toStack(1));
            }
        }
        List<ItemStack> fluids = new ArrayList<>();
        for (FluidStack f : net.listFluids()) {
            ItemStack bucket = f.isEmpty() ? ItemStack.EMPTY : FluidUtil.getFilledBucket(f.copyWithAmount(FluidType.BUCKET_VOLUME));
            if (!bucket.isEmpty() && fluids.size() < CARGO_FLUIDS) {
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

    /** Forced rescan (right-click on the core, tests); the fresh network is shared with every device of it. */
    public void refresh(Level level, BlockPos pos) {
        update(level, pos, true);
    }

    /** The network whose lights were last set, and how many seconds ago (they are set again on a change and now and then). */
    private GolemNetwork syncedNetwork;
    private int secondsSinceSync;

    /**
     * Once a second: drains Source and lights the network. The walk itself is the one the devices share and is remembered until
     * something changes (it used to be repeated every second); the array is checked here, because casings and Atlants are not
     * network nodes and their changes do not reach the network on their own.
     */
    private void update(Level level, BlockPos pos, boolean force) {
        GolemNetwork net = force ? GolemNetwork.refresh(level, pos) : GolemNetwork.get(level, pos);
        if (!force && net.isPrimaryCore(pos) && AtlasStructure.check(level, pos).valid() != net.structureValid()) {
            GolemNetwork.nodesChanged(level, pos);
            net = GolemNetwork.refresh(level, pos);
        }

        boolean newPowered;
        int cost = 0;
        duplicate = !net.isPrimaryCore(pos);
        if (duplicate) {
            // a second core in the same network does not work (the network chose the other one, the same from every device)
            newPowered = false;
        } else {
            cost = GolemConfig.CORE_SOURCE_PER_SECOND.get()
                    + (int) Math.ceil(net.channelsUsed() * GolemConfig.CHANNEL_SOURCE_PER_SECOND.get())
                    + net.telegraphLinks() * GolemConfig.TELEGRAPH_SOURCE_PER_SECOND.get();
            newPowered = net.consumeSource(cost);
        }

        // the lights only change with the network or the power; walking every node every second for nothing was wasted work
        if (!duplicate && (force || net != syncedNetwork || newPowered != powered || ++secondsSinceSync >= 10)) {
            net.syncActiveVisuals(newPowered);
            syncedNetwork = net;
            secondsSinceSync = 0;
        }
        boolean newFormed = net.structure() != null && net.structure().valid();
        if (newFormed && !formed) {
            AtlasStructure.orientAtlants(level, pos);
        }
        boolean newInfinite = net.hasInfiniteSource() || !com.golemlogix.system.SourceHelper.sourceRequired();
        int newSps = newInfinite ? 0 : cost;
        boolean changed = newFormed != formed || newPowered != powered || net.channelsUsed() != channelsUsed
                || net.channelCapacity() != channelCapacity || net.deviceCount() != devices
                || newInfinite != infiniteSource || newSps != sourcePerSecond || duplicate != wasDuplicate;
        if (!duplicate && (force || net != cargoNetwork || ++secondsSinceCargo >= CARGO_SECONDS)) {
            cargoNetwork = net;
            secondsSinceCargo = 0;
            changed |= pickCargo(net);
        }
        wasDuplicate = duplicate;
        powered = newPowered;
        formed = newFormed;
        channelsUsed = net.channelsUsed();
        channelCapacity = net.channelCapacity();
        devices = net.deviceCount();
        infiniteSource = newInfinite;
        sourcePerSecond = newSps;
        if (changed) {
            setChanged();
            level.sendBlockUpdated(pos, getBlockState(), getBlockState(), 3);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putBoolean("Powered", powered);
        tag.putBoolean("Formed", formed);
        tag.putInt("ChannelsUsed", channelsUsed);
        tag.putInt("ChannelCapacity", channelCapacity);
        tag.putInt("Devices", devices);
        tag.putInt("SourcePerSecond", sourcePerSecond);
        tag.putBoolean("InfiniteSource", infiniteSource);
        tag.putBoolean("Duplicate", duplicate);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        powered = tag.getBoolean("Powered");
        formed = tag.getBoolean("Formed");
        channelsUsed = tag.getInt("ChannelsUsed");
        channelCapacity = tag.getInt("ChannelCapacity");
        devices = tag.getInt("Devices");
        sourcePerSecond = tag.getInt("SourcePerSecond");
        infiniteSource = tag.getBoolean("InfiniteSource");
        duplicate = tag.getBoolean("Duplicate");
        // only in update packets (client visuals), never saved with the world
        if (tag.contains("CargoItems")) {
            cargoItems = readStacks(tag.getList("CargoItems", Tag.TAG_COMPOUND), registries);
            cargoFluids = readStacks(tag.getList("CargoFluids", Tag.TAG_COMPOUND), registries);
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag, registries);
        tag.put("CargoItems", writeStacks(cargoItems, registries));
        tag.put("CargoFluids", writeStacks(cargoFluids, registries));
        return tag;
    }

    private static ListTag writeStacks(List<ItemStack> stacks, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (ItemStack s : stacks) {
            list.add(s.save(registries));
        }
        return list;
    }

    private static List<ItemStack> readStacks(ListTag list, HolderLookup.Provider registries) {
        List<ItemStack> out = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            ItemStack s = ItemStack.parseOptional(registries, list.getCompound(i));
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
}
