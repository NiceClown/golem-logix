package com.golemlogix.integration.thaumaturge;

import com.golemlogix.blockentity.EssentiaCauldronBlockEntity;
import com.golemlogix.blocks.EssentiaCauldronBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

/**
 * Safe soft-dependency entry point for Thaumaturge. Contains ZERO references to Thaumaturge classes so Golem Logix
 * boots and runs with {@code -PnoThaumaturge} without any {@link NoClassDefFoundError}.
 */
public final class ThaumaturgeCompat {
    public static final String MOD_ID = "thaumaturge";

    public record AuraSnapshot(float vis, int base, float flux) {
        public static final AuraSnapshot EMPTY = new AuraSnapshot(0f, 0, 0f);
    }

    private ThaumaturgeCompat() {
    }

    public static boolean isLoaded() {
        return ModList.get().isLoaded(MOD_ID);
    }

    /** Creates the Thaumaturge {@code IEssentiaStreamPort} subclass when loaded, or plain {@link EssentiaCauldronBlock}. */
    public static EssentiaCauldronBlock createCauldronBlock(BlockBehaviour.Properties properties) {
        return isLoaded() ? ThaumaturgeCauldronFactory.create(properties) : new EssentiaCauldronBlock(properties);
    }

    /** Registers Thaumaturge essentia capabilities when Thaumaturge is loaded. */
    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        if (isLoaded()) {
            ThaumaturgeCapabilities.register(event);
        }
    }

    public static AuraSnapshot readAura(Level level, BlockPos pos) {
        return isLoaded() ? ThaumaturgeBridge.readAura(level, pos) : AuraSnapshot.EMPTY;
    }

    public static float drainVis(Level level, BlockPos pos, float amount, boolean simulate) {
        return isLoaded() ? ThaumaturgeBridge.drainVis(level, pos, amount, simulate) : 0f;
    }

    public static void polluteAura(Level level, BlockPos pos, float flux, boolean showEffect) {
        if (isLoaded()) {
            ThaumaturgeBridge.polluteAura(level, pos, flux, showEffect);
        }
    }

    public static int pullEssentia(Level level, BlockPos cauldronPos, Direction face, boolean primary, int maxAmount) {
        return isLoaded() ? ThaumaturgeBridge.pullEssentia(level, cauldronPos, face, primary, maxAmount) : 0;
    }

    public static ItemInteractionResult interactPhial(ItemStack stack, Level level, EssentiaCauldronBlockEntity be,
                                                      Player player, InteractionHand hand) {
        return isLoaded()
                ? ThaumaturgeBridge.interactPhial(stack, level, be, player, hand)
                : ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }
}
