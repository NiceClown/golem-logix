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

    /** Идентификатор диска: содержимое хранится на сервере в DiskStore. */
    public static final Supplier<DataComponentType<UUID>> DISK_ID = COMPONENTS.registerComponentType(
            "disk_id", b -> b.persistent(UUIDUtil.CODEC).networkSynchronized(UUIDUtil.STREAM_CODEC));

    /** Сколько единиц (предметов / mB) записано на диск — синхронизируется для подсказок и полосы заполнения. */
    public static final Supplier<DataComponentType<Integer>> STORED = COMPONENTS.registerComponentType(
            "stored", b -> b.persistent(Codec.INT).networkSynchronized(ByteBufCodecs.VAR_INT));

    /** Закодированный рецепт перфокарты. */
    public static final Supplier<DataComponentType<PatternData>> PATTERN = COMPONENTS.registerComponentType(
            "pattern", b -> b.persistent(PatternData.CODEC).networkSynchronized(PatternData.STREAM_CODEC));

    /** Привязка Портативного Хронометра к Ядру Атланта. */
    public static final Supplier<DataComponentType<GlobalPos>> BOUND_CORE = COMPONENTS.registerComponentType(
            "bound_core", b -> b.persistent(GlobalPos.CODEC).networkSynchronized(GlobalPos.STREAM_CODEC));

    /** Уровень дальности Хронометра: 0 локальный, 1 в пределах измерения, 2 межмерный. */
    public static final Supplier<DataComponentType<Integer>> WIRELESS_TIER = COMPONENTS.registerComponentType(
            "wireless_tier", b -> b.persistent(Codec.intRange(0, 2)).networkSynchronized(ByteBufCodecs.VAR_INT));

    /** Запас Source в Хронометре. */
    public static final Supplier<DataComponentType<Integer>> SOURCE_CHARGE = COMPONENTS.registerComponentType(
            "source_charge", b -> b.persistent(Codec.INT).networkSynchronized(ByteBufCodecs.VAR_INT));

    /** Хронометр последний раз питался от Вечного Морозного Резонатора своей сети (для подсказки). */
    public static final Supplier<DataComponentType<Boolean>> RESONATOR_LINK = COMPONENTS.registerComponentType(
            "resonator_link", b -> b.persistent(Codec.BOOL).networkSynchronized(ByteBufCodecs.BOOL));
}
