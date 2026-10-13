package com.golemlogix.secret;

import com.golemlogix.init.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.AbstractGolem;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.animal.SnowGolem;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;

/**
 * The Dispel method (Ars Nouveau): a Dispel cast on a battered golem unbinds its mind, and the golem turns into a raw mind
 * of its kind. "Battered" is the same health window as in the anvil ritual. The method is known only after the player's first
 * plate made on an anvil ({@link SecretKnowledge#plateMade}).
 */
public final class GolemMinds {
    public enum Kind {IRON, COPPER, SNOW}

    private GolemMinds() {
    }

    /** Which golem the entity is, or null if it is no golem of the mod's rituals. */
    @Nullable
    public static Kind kind(Entity entity) {
        if (entity instanceof IronGolem) {
            return Kind.IRON;
        }
        if (entity instanceof SnowGolem) {
            return Kind.SNOW;
        }
        String path = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).getPath();
        if (path.contains("copper_golem") || (entity instanceof AbstractGolem && path.contains("copper"))) {
            return Kind.COPPER;
        }
        return null;
    }

    /** Battered enough, as for the anvil ritual: iron and copper golems at 4 HP or less, a snow golem below its full 4 HP. */
    public static boolean battered(LivingEntity golem, Kind kind) {
        float hp = golem.getHealth();
        return hp >= 0.5f && (kind == Kind.SNOW ? hp <= 3.9f : hp <= 4.0f);
    }

    public static Item rawMind(Kind kind) {
        return switch (kind) {
            case IRON -> ModItems.RAW_IRON_MIND.get();
            case COPPER -> ModItems.RAW_COPPER_MIND.get();
            case SNOW -> ModItems.RAW_SNOW_MIND.get();
        };
    }

    /**
     * A Dispel by caster hit target. Returns true if it was a golem of the rituals (the Dispel's own effect is then skipped):
     * a battered golem turns into its raw mind if the caster knows the method, otherwise the caster gets a hint.
     */
    public static boolean dispel(@Nullable ServerPlayer caster, Entity target) {
        return attempt(caster, target, caster != null && SecretKnowledge.knowsMind(caster));
    }

    /** The Dispel on target, by a caster who knows the method or not. */
    public static boolean attempt(@Nullable ServerPlayer caster, Entity target, boolean knows) {
        Kind kind = kind(target);
        if (kind == null || !(target instanceof LivingEntity golem) || !(target.level() instanceof ServerLevel level)) {
            return false;
        }
        if (caster == null || !knows) {
            if (caster != null) {
                caster.displayClientMessage(Component.translatable("message.golem_logix.mind.unknown").withStyle(ChatFormatting.GRAY), true);
            }
            return true;
        }
        if (!battered(golem, kind)) {
            caster.displayClientMessage(Component.translatable("message.golem_logix.mind.too_strong",
                    String.format("%.0f", golem.getHealth())).withStyle(ChatFormatting.YELLOW), true);
            level.sendParticles(ParticleTypes.CRIT, golem.getX(), golem.getY() + 1.0, golem.getZ(), 12, 0.3, 0.4, 0.3, 0.1);
            return true;
        }
        Item mind = rawMind(kind);
        level.addFreshEntity(new ItemEntity(level, golem.getX(), golem.getY() + 0.5, golem.getZ(), new ItemStack(mind)));
        level.sendParticles(ParticleTypes.ENCHANT, golem.getX(), golem.getY() + 1.0, golem.getZ(), 60, 0.4, 0.7, 0.4, 0.6);
        level.sendParticles(ParticleTypes.SNOWFLAKE, golem.getX(), golem.getY() + 0.8, golem.getZ(), 30, 0.3, 0.5, 0.3, 0.04);
        level.sendParticles(ParticleTypes.SOUL, golem.getX(), golem.getY() + 1.2, golem.getZ(), 12, 0.2, 0.3, 0.2, 0.03);
        level.playSound(null, golem.getX(), golem.getY(), golem.getZ(), SoundEvents.ILLUSIONER_MIRROR_MOVE, SoundSource.PLAYERS, 1.0f, 0.8f);
        level.playSound(null, golem.getX(), golem.getY(), golem.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.0f, 0.6f);
        caster.displayClientMessage(Component.translatable("message.golem_logix.mind.unbound", mind.getName(new ItemStack(mind)))
                .withStyle(ChatFormatting.AQUA), true);
        golem.discard();
        return true;
    }
}
