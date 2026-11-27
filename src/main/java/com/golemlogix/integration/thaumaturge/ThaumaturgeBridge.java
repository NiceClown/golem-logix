package com.golemlogix.integration.thaumaturge;

import com.golemlogix.blockentity.EssentiaCauldronBlockEntity;
import com.golemlogix.config.GolemConfig;
import com.leclowndu93150.thaumaturge.api.aspect.AspectInstance;
import com.leclowndu93150.thaumaturge.api.aspect.AspectList;
import com.leclowndu93150.thaumaturge.api.aspect.IAspect;
import com.leclowndu93150.thaumaturge.api.aspect.TTAspects;
import com.leclowndu93150.thaumaturge.api.aura.AuraHelper;
import com.leclowndu93150.thaumaturge.api.essentia.EssentiaAccess;
import com.leclowndu93150.thaumaturge.api.essentia.EssentiaCapabilities;
import com.leclowndu93150.thaumaturge.api.essentia.EssentiaTransferFeedback;
import com.leclowndu93150.thaumaturge.api.essentia.IEssentiaItemStorage;
import com.leclowndu93150.thaumaturge.api.essentia.IEssentiaTransport;
import com.leclowndu93150.thaumaturge.api.essentia.ItemEssentiaTransferResult;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * Direct Thaumaturge API calls. Only ever loaded when {@link ThaumaturgeCompat#isLoaded()} is true.
 */
final class ThaumaturgeBridge {
    static final int CAULDRON_SUCTION = 128;

    private ThaumaturgeBridge() {
    }

    static ThaumaturgeCompat.AuraSnapshot readAura(Level level, BlockPos pos) {
        if (level == null) {
            return ThaumaturgeCompat.AuraSnapshot.EMPTY;
        }
        return new ThaumaturgeCompat.AuraSnapshot(
                AuraHelper.getVis(level, pos),
                AuraHelper.getAuraBase(level, pos),
                AuraHelper.getFlux(level, pos));
    }

    static float drainVis(Level level, BlockPos pos, float amount, boolean simulate) {
        if (level == null || amount <= 0f) {
            return 0f;
        }
        return AuraHelper.drainVis(level, pos, amount, simulate);
    }

    static void polluteAura(Level level, BlockPos pos, float flux, boolean showEffect) {
        if (level != null && flux > 0f) {
            AuraHelper.polluteAura(level, pos, flux, showEffect);
        }
    }

    @Nullable
    static Holder<IAspect> primaryAspect(@Nullable Level level) {
        return resolveAspect(level, GolemConfig.CAULDRON_PRIMARY_ASPECT.get(), TTAspects.AURAM);
    }

    @Nullable
    static Holder<IAspect> coolantAspect(@Nullable Level level) {
        return resolveAspect(level, GolemConfig.CAULDRON_COOLANT_ASPECT.get(), TTAspects.GELUM);
    }

    static boolean isPrimary(@Nullable Level level, @Nullable Holder<IAspect> aspect) {
        return sameAspect(aspect, primaryAspect(level));
    }

    static boolean isCoolant(@Nullable Level level, @Nullable Holder<IAspect> aspect) {
        return sameAspect(aspect, coolantAspect(level));
    }

    static boolean sameAspect(@Nullable Holder<IAspect> a, @Nullable Holder<IAspect> b) {
        if (a == null || b == null) {
            return false;
        }
        if (a.equals(b)) {
            return true;
        }
        ResourceKey<IAspect> ka = a.unwrapKey().orElse(null);
        ResourceKey<IAspect> kb = b.unwrapKey().orElse(null);
        return ka != null && Objects.equals(ka, kb);
    }

    @Nullable
    private static Holder<IAspect> resolveAspect(@Nullable Level level, String configured, ResourceKey<IAspect> fallback) {
        if (level == null) {
            return null;
        }
        Registry<IAspect> reg = level.registryAccess().lookup(IAspect.REGISTRY_KEY).orElse(null);
        if (reg == null) {
            return null;
        }
        Identifier id = Identifier.tryParse(configured);
        if (id != null) {
            Holder<IAspect> resolved = reg.get(ResourceKey.create(IAspect.REGISTRY_KEY, id)).orElse(null);
            if (resolved != null) {
                return resolved;
            }
        }
        return reg.get(fallback).orElse(null);
    }

    /** Actively pulls matching Essentia from an adjacent {@link IEssentiaTransport} (tube, jar, buffer, etc.). */
    static int pullEssentia(Level level, BlockPos cauldronPos, Direction face, boolean primary, int maxAmount) {
        if (level == null || maxAmount <= 0) {
            return 0;
        }
        BlockPos neighborPos = cauldronPos.relative(face);
        if (!level.isLoaded(neighborPos)) {
            return 0;
        }
        Direction neighborFace = face.getOpposite();
        IEssentiaTransport neighbor = EssentiaAccess.transport(level, neighborPos, neighborFace);
        if (neighbor == null || !neighbor.canOutputTo(neighborFace)) {
            return 0;
        }
        Holder<IAspect> want = primary ? primaryAspect(level) : coolantAspect(level);
        if (want == null) {
            return 0;
        }
        int neighborSuction = neighbor.getSuctionAmount(neighborFace);
        if (neighborSuction >= CAULDRON_SUCTION || CAULDRON_SUCTION < neighbor.getMinimumSuction()) {
            return 0;
        }
        Holder<IAspect> availableType = neighbor.getEssentiaType(neighborFace);
        int available = neighbor.getEssentiaAmount(neighborFace);
        if (available <= 0 || !sameAspect(availableType, want)) {
            return 0;
        }
        int ask = Math.min(maxAmount, available);
        return Math.max(0, neighbor.takeEssentia(want, ask, neighborFace));
    }

    /** Drains matching Auram or Gelum from a Thaumaturge Essentia Phial (or other {@link IEssentiaItemStorage}) into the cauldron. */
    static InteractionResult interactPhial(ItemStack stack, Level level, EssentiaCauldronBlockEntity be,
                                           Player player, InteractionHand hand) {
        if (stack.isEmpty()) {
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        }
        ItemStack single = stack.copyWithCount(1);
        IEssentiaItemStorage itemStorage = single.getCapability(EssentiaCapabilities.ITEM_STORAGE);
        if (itemStorage == null) {
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        }
        AspectList contents = itemStorage.contents();
        if (contents.isEmpty()) {
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        }
        Holder<IAspect> primary = primaryAspect(level);
        Holder<IAspect> coolant = coolantAspect(level);
        for (AspectInstance entry : contents.entries()) {
            boolean isPrimary = sameAspect(entry.aspect(), primary);
            boolean isCoolant = !isPrimary && sameAspect(entry.aspect(), coolant);
            if (!isPrimary && !isCoolant) {
                continue;
            }
            int space = isPrimary ? be.addPrimary(entry.amount(), true) : be.addCoolant(entry.amount(), true);
            if (space <= 0) {
                return InteractionResult.CONSUME;
            }
            ItemEssentiaTransferResult preview = itemStorage.extract(entry.aspect(), space);
            if (preview.amountMoved() <= 0) {
                return InteractionResult.CONSUME;
            }
            if (level.isClientSide()) {
                player.swing(hand);
                return InteractionResult.SUCCESS;
            }
            int added = isPrimary ? be.addPrimary(preview.amountMoved(), false) : be.addCoolant(preview.amountMoved(), false);
            if (added <= 0) {
                return InteractionResult.CONSUME;
            }
            if (!player.getAbilities().instabuild) {
                ItemStack resulting = preview.resultingStack();
                if (stack.getCount() == 1) {
                    player.setItemInHand(hand, resulting);
                } else {
                    stack.shrink(1);
                    if (!resulting.isEmpty() && !player.addItem(resulting)) {
                        player.drop(resulting, false);
                    }
                }
            }
            EssentiaTransferFeedback.playDrain(player, single, added);
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.CONSUME;
    }
}
