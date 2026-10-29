package com.golemlogix.init;

import com.golemlogix.GolemLogixMod;
import com.golemlogix.data.PatternData;
import com.mojang.serialization.Codec;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.UUID;
import java.util.function.Supplier;

public class ModDataComponents {
    public static final DeferredRegister.DataComponents COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, GolemLogixMod.MOD_ID);

    /** Disk id: the contents are stored on the server in DiskStore. */
    public static final Supplier<DataComponentType<UUID>> DISK_ID = COMPONENTS.registerComponentType(
            "disk_id", b -> b.persistent(UUIDUtil.CODEC).networkSynchronized(UUIDUtil.STREAM_CODEC));

    /** How many units (items / mB) are written to the disk, synced for tooltips and the fill bar. */
    public static final Supplier<DataComponentType<Integer>> STORED = COMPONENTS.registerComponentType(
            "stored", b -> b.persistent(Codec.INT).networkSynchronized(ByteBufCodecs.VAR_INT));

    /** The encoded recipe of a punch card. */
    public static final Supplier<DataComponentType<PatternData>> PATTERN = COMPONENTS.registerComponentType(
            "pattern", b -> b.persistent(PatternData.CODEC).networkSynchronized(PatternData.STREAM_CODEC));

    /** Binding of a Portable Chronometer to an Atlas Core. */
    public static final Supplier<DataComponentType<GlobalPos>> BOUND_CORE = COMPONENTS.registerComponentType(
            "bound_core", b -> b.persistent(GlobalPos.CODEC).networkSynchronized(GlobalPos.STREAM_CODEC));

    /** Chronometer range tier: 0 local, 1 within the dimension, 2 interdimensional. */
    public static final Supplier<DataComponentType<Integer>> WIRELESS_TIER = COMPONENTS.registerComponentType(
            "wireless_tier", b -> b.persistent(Codec.intRange(0, 2)).networkSynchronized(ByteBufCodecs.VAR_INT));

    /** Source charge of the Chronometer. */
    public static final Supplier<DataComponentType<Integer>> SOURCE_CHARGE = COMPONENTS.registerComponentType(
            "source_charge", b -> b.persistent(Codec.INT).networkSynchronized(ByteBufCodecs.VAR_INT));

    /** The fluid and amount a fluid ghost stands for (bus filters, interface stock, pattern inputs). */
    public static final Supplier<DataComponentType<net.neoforged.neoforge.fluids.SimpleFluidContent>> GHOST_FLUID = COMPONENTS.registerComponentType(
            "ghost_fluid", b -> b.persistent(net.neoforged.neoforge.fluids.SimpleFluidContent.CODEC)
                    .networkSynchronized(net.neoforged.neoforge.fluids.SimpleFluidContent.STREAM_CODEC));

    /** The telegraph an Aetherial Linkage card records (written in a telegraph's Write slot). */
    /**
     * Fuzzy mode: the item tag chosen on a ghost (a bus or interface filter with a Fuzzy Card, a processing pattern input in the
     * encoder's fuzzy mode). Without it a fuzzy ghost matches the same item with any components.
     */
    public static final Supplier<DataComponentType<net.minecraft.tags.TagKey<net.minecraft.world.item.Item>>> FUZZY_TAG =
            COMPONENTS.registerComponentType("fuzzy_tag", b -> b
                    .persistent(net.minecraft.tags.TagKey.codec(net.minecraft.core.registries.Registries.ITEM))
                    .networkSynchronized(net.minecraft.resources.Identifier.STREAM_CODEC.map(
                            id -> net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.ITEM, id),
                            net.minecraft.tags.TagKey::location)));

    public static final Supplier<DataComponentType<com.golemlogix.items.TelegraphLinkCardItem.Link>> TELEGRAPH_LINK =
            COMPONENTS.registerComponentType("telegraph_link", b -> b.persistent(com.golemlogix.items.TelegraphLinkCardItem.Link.CODEC)
                    .networkSynchronized(com.golemlogix.items.TelegraphLinkCardItem.Link.STREAM_CODEC));

    /** On an Export Bus filter ghost: how much of it the container in front should hold (items, or mB for a fluid). */
    public static final Supplier<DataComponentType<Integer>> KEEP_AMOUNT = COMPONENTS.registerComponentType(
            "keep_amount", b -> b.persistent(Codec.intRange(1, Integer.MAX_VALUE)).networkSynchronized(ByteBufCodecs.VAR_INT));

    /** The fluid in a Copper Tank (as an item; the block keeps it in its block entity). */
    public static final Supplier<DataComponentType<net.neoforged.neoforge.fluids.SimpleFluidContent>> TANK_FLUID = COMPONENTS.registerComponentType(
            "tank_fluid", b -> b.persistent(net.neoforged.neoforge.fluids.SimpleFluidContent.CODEC)
                    .networkSynchronized(net.neoforged.neoforge.fluids.SimpleFluidContent.STREAM_CODEC));

    /** How much a Copper Tank holds (mB) when tanks were joined in a crafting grid; without it, one tank's worth. */
    public static final Supplier<DataComponentType<Integer>> TANK_CAPACITY = COMPONENTS.registerComponentType(
            "tank_capacity", b -> b.persistent(Codec.intRange(1, Integer.MAX_VALUE)).networkSynchronized(ByteBufCodecs.VAR_INT));

    /** A Copper Tank in bucket mode: a right-click scoops up or pours out a bucket's worth instead of placing the tank. */
    public static final Supplier<DataComponentType<Boolean>> BUCKET_MODE = COMPONENTS.registerComponentType(
            "bucket_mode", b -> b.persistent(Codec.BOOL).networkSynchronized(ByteBufCodecs.BOOL));

    /** The Chronometer was last powered by the Eternal Frost Resonator of its network (for the tooltip). */
    public static final Supplier<DataComponentType<Boolean>> RESONATOR_LINK = COMPONENTS.registerComponentType(
            "resonator_link", b -> b.persistent(Codec.BOOL).networkSynchronized(ByteBufCodecs.BOOL));

    /** The bound player UUID of an Identity Punch Card. */
    public static final Supplier<DataComponentType<UUID>> IDENTITY_OWNER_ID = COMPONENTS.registerComponentType(
            "identity_owner_id", b -> b.persistent(UUIDUtil.CODEC).networkSynchronized(UUIDUtil.STREAM_CODEC));

    /** The bound player name of an Identity Punch Card. */
    public static final Supplier<DataComponentType<String>> IDENTITY_OWNER_NAME = COMPONENTS.registerComponentType(
            "identity_owner_name", b -> b.persistent(Codec.STRING).networkSynchronized(ByteBufCodecs.STRING_UTF8));

    /** Permissions bitmask of an Identity Punch Card (1: insert, 2: extract, 4: craft, 8: build). */
    public static final Supplier<DataComponentType<Integer>> IDENTITY_PERMISSIONS = COMPONENTS.registerComponentType(
            "identity_permissions", b -> b.persistent(Codec.INT).networkSynchronized(ByteBufCodecs.VAR_INT));
}
