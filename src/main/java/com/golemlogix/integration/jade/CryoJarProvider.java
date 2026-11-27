package com.golemlogix.integration.jade;

import com.golemlogix.GolemLogixMod;
import com.golemlogix.blockentity.CryoJarBlockEntity;
import com.golemlogix.blocks.CryoJarBlock;
import com.golemlogix.blocks.JarForm;
import com.golemlogix.config.GolemConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

/** Lines over a Cryo-Source Jar, Giga Jar, or Antigravity Reactor: stored Source, capacity, and network bonus. */
public enum CryoJarProvider implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {
    INSTANCE;

    private static final ResourceLocation UID = ResourceLocation.fromNamespaceAndPath(GolemLogixMod.MOD_ID, "cryo_jar");
    private static final String TAG = "golem_logix_cryo_jar";

    @Override
    public void appendServerData(CompoundTag data, BlockAccessor accessor) {
        BlockPos lower = CryoJarBlock.lowerOf(accessor.getPosition(), accessor.getBlockState());
        if (!accessor.getLevel().isLoaded(lower)
                || !(accessor.getLevel().getBlockEntity(lower) instanceof CryoJarBlockEntity jar)) {
            return;
        }
        CompoundTag t = new CompoundTag();
        t.putInt("source", jar.available());
        t.putInt("cap", jar.capacity());
        t.putString("form", jar.form().name());
        t.putInt("gigaDiscount", GolemConfig.GIGA_SOURCE_DISCOUNT.get());
        t.putInt("reactorBonus", GolemConfig.REACTOR_CHANNEL_BONUS.get());
        data.put(TAG, t);
    }

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        int source;
        int cap;
        JarForm form;
        int gigaDiscount;
        int reactorBonus;
        if (accessor.getServerData().contains(TAG)) {
            CompoundTag t = accessor.getServerData().getCompound(TAG);
            source = t.getInt("source");
            cap = t.getInt("cap");
            form = parseForm(t.getString("form"));
            gigaDiscount = t.getInt("gigaDiscount");
            reactorBonus = t.getInt("reactorBonus");
        } else if (accessor.getBlockEntity() instanceof CryoJarBlockEntity be) {
            CryoJarBlockEntity owner = be.owner();
            if (owner == null) {
                return;
            }
            source = owner.clientSource();
            cap = owner.capacity();
            form = owner.form();
            gigaDiscount = GolemConfig.GIGA_SOURCE_DISCOUNT.get();
            reactorBonus = GolemConfig.REACTOR_CHANNEL_BONUS.get();
        } else {
            return;
        }

        switch (form) {
            case GIGA -> {
                tooltip.add(Component.translatable("jade.golem_logix.cryo_jar.giga", source, cap)
                        .withStyle(ChatFormatting.AQUA));
                if (gigaDiscount > 0) {
                    tooltip.add(Component.translatable("jade.golem_logix.cryo_jar.giga_bonus", gigaDiscount)
                            .withStyle(ChatFormatting.GREEN));
                }
            }
            case SUPER -> {
                tooltip.add(Component.translatable("jade.golem_logix.cryo_jar.reactor", source, cap)
                        .withStyle(ChatFormatting.AQUA));
                if (reactorBonus > 0) {
                    tooltip.add(Component.translatable("jade.golem_logix.cryo_jar.reactor_bonus", reactorBonus)
                            .withStyle(ChatFormatting.LIGHT_PURPLE));
                }
            }
            default -> tooltip.add(Component.translatable("jade.golem_logix.cryo_jar.source", source, cap)
                    .withStyle(ChatFormatting.AQUA));
        }
    }

    private static JarForm parseForm(String raw) {
        try {
            return JarForm.valueOf(raw);
        } catch (IllegalArgumentException e) {
            return JarForm.SINGLE;
        }
    }

    @Override
    public ResourceLocation getUid() {
        return UID;
    }
}
