package com.golemlogix.blockentity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.HolderSet;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.Tool;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Pure tool, mining-speed, Plasma Temper, and crop-maturity helpers used by {@link AnnihilationBusBlockEntity}.
 */
final class AnnihilationTools {
    /** The iron tools a bare golem brings to every block (sword last for direct lookup). */
    private static final Item[] IRON_TOOLS = {
            Items.IRON_PICKAXE, Items.IRON_AXE, Items.IRON_SHOVEL, Items.IRON_HOE, Items.IRON_SWORD
    };
    private static final int SWORD_INDEX = IRON_TOOLS.length - 1;
    @Nullable
    private static ItemStack[] ironToolStacks;

    private AnnihilationTools() {
    }

    private static ItemStack[] ironToolStacks() {
        if (ironToolStacks == null) {
            ItemStack[] stacks = new ItemStack[IRON_TOOLS.length];
            for (int i = 0; i < IRON_TOOLS.length; i++) {
                stacks[i] = new ItemStack(IRON_TOOLS[i]);
            }
            ironToolStacks = stacks;
        }
        return ironToolStacks;
    }

    /** Cached unworn iron sword for read-only attribute inspection and copying when bare-handed. */
    static ItemStack ironSword() {
        return ironToolStacks()[SWORD_INDEX];
    }

    /** Ticks between melee strikes for the held weapon (or an iron sword when empty). */
    static int attackInterval(ItemStack held) {
        ItemStack weapon = held.isEmpty() ? ironSword() : held;
        double speed = 4.0 + mainHandBonus(weapon, Attributes.ATTACK_SPEED);
        return Math.max(1, (int) Math.ceil(20.0 / Math.max(0.25, speed)));
    }

    /** Sum of ADD_VALUE modifiers the item and its enchantments apply to an attribute in the main hand. */
    static double mainHandBonus(ItemStack stack, Holder<Attribute> attribute) {
        double[] sum = {0};
        stack.forEachModifier(EquipmentSlot.MAINHAND, (a, m) -> {
            if (a.equals(attribute) && m.operation() == AttributeModifier.Operation.ADD_VALUE) {
                sum[0] += m.amount();
            }
        });
        return sum[0];
    }

    /** Tool mining speed on a block, including Efficiency bonus when the tool suits the block. */
    static float mineSpeed(ItemStack with, BlockState state) {
        float speed = with.getDestroySpeed(state);
        if (speed > 1f) {
            speed += (float) mainHandBonus(with, Attributes.MINING_EFFICIENCY);
        }
        return speed;
    }

    /** Picks the slotted tool when present, otherwise the fastest cached iron tool for this block. */
    static ItemStack toolFor(ItemStack held, BlockState state, boolean tempered) {
        if (!held.isEmpty()) {
            return held;
        }
        ItemStack best = ItemStack.EMPTY;
        float bestSpeed = -1f;
        for (ItemStack candidate : ironToolStacks()) {
            boolean right = harvests(candidate, state, tempered);
            float speed = right ? candidate.getDestroySpeed(state) : 0f;
            if (speed > bestSpeed) {
                bestSpeed = speed;
                best = candidate;
            }
        }
        return best;
    }

    /** True when the block is breakable, harvestable with this tool, and durability rules allow using it. */
    static boolean canMine(ServerLevel level, BlockPos target, BlockState state, ItemStack with, boolean tempered, boolean refilling) {
        if (state.getDestroySpeed(level, target) < 0 || state.getBlock() instanceof LiquidBlock) {
            return false;
        }
        if (!harvests(with, state, tempered)) {
            return false;
        }
        // keep the last durability point for the player unless the slot refills automatically or is tempered
        return tempered || refilling || !(with.isDamageableItem() && with.getDamageValue() >= with.getMaxDamage() - 1);
    }

    static boolean harvests(ItemStack with, BlockState state, boolean tempered) {
        return !state.requiresCorrectToolForDrops() || with.isCorrectToolForDrops(state) || (tempered && temperLifts(with, state));
    }

    /**
     * Plasma Temper lifts any tool to netherite tier for blocks of its own kind (a pickaxe does not dig like a shovel).
     * Modded tools that override harvest checks outside the TOOL component stay unchanged.
     */
    static boolean temperLifts(ItemStack with, BlockState state) {
        return with.getDestroySpeed(state) > 1f
                && !state.is(BlockTags.INCORRECT_FOR_NETHERITE_TOOL)
                && lifted(with, state).isCorrectToolForDrops(state);
    }

    private static ItemStack lifted(ItemStack with, BlockState state) {
        ItemStack hand = with.copy();
        Tool tool = new Tool(List.of(Tool.Rule.minesAndDrops(
                HolderSet.direct(state.getBlock().builtInRegistryHolder()), with.getDestroySpeed(state))), 1f, 0, true);
        hand.set(DataComponents.TOOL, tool);
        return hand;
    }

    /** Returns the copy swung with Plasma Temper: lifted to netherite tier if applicable, plus Fortune/Looting blessings. */
    static ItemStack temperedHand(ItemStack with, BlockState state, HolderLookup.Provider registries) {
        ItemStack hand = !with.isCorrectToolForDrops(state) && temperLifts(with, state) ? lifted(with, state) : with.copy();
        return blessed(hand, registries);
    }

    /** Adds +1 Fortune (and +1 Looting unless Silk Touch is present) to the swung copy. */
    static ItemStack blessed(ItemStack hand, HolderLookup.Provider registries) {
        var lookup = registries.lookupOrThrow(Registries.ENCHANTMENT);
        var fortune = lookup.getOrThrow(Enchantments.FORTUNE);
        var looting = lookup.getOrThrow(Enchantments.LOOTING);
        var silkTouch = lookup.getOrThrow(Enchantments.SILK_TOUCH);
        // Looting and Silk Touch cannot coexist on one item
        boolean keepsBlock = EnchantmentHelper.getItemEnchantmentLevel(silkTouch, hand) > 0;
        EnchantmentHelper.updateEnchantments(hand, m -> {
            m.set(fortune, m.getLevel(fortune) + 1);
            if (!keepsBlock) {
                m.set(looting, m.getLevel(looting) + 1);
            }
        });
        return hand;
    }

    /** Restores pre-action durability when Plasma Temper is installed. */
    static ItemStack unworn(ItemStack before, ItemStack after, boolean tempered) {
        if (tempered && !before.isEmpty() && (after.isEmpty() || after.is(before.getItem())) && before.isDamageableItem()) {
            ItemStack kept = after.isEmpty() ? before.copy() : after;
            kept.setDamageValue(before.getDamageValue());
            return kept;
        }
        return after;
    }

    /** True for a crop or age-bearing block at its maximum age; blocks without an age property are always grown. */
    static boolean grown(BlockState state) {
        if (state.getBlock() instanceof CropBlock crop) {
            return crop.isMaxAge(state);
        }
        for (var property : state.getProperties()) {
            if (property instanceof IntegerProperty age && property.getName().equals("age")) {
                int max = 0;
                for (int v : age.getPossibleValues()) {
                    if (v > max) {
                        max = v;
                    }
                }
                return state.getValue(age) >= max;
            }
        }
        return true;
    }
}
