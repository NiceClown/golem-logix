package com.golemlogix.event;

import com.golemlogix.GolemLogixMod;
import com.golemlogix.init.ModItems;
import com.golemlogix.init.ModSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.animal.SnowGolem;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AnvilBlock;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

import java.util.List;

@EventBusSubscriber(modid = GolemLogixMod.MOD_ID, bus = EventBusSubscriber.Bus.GAME)
public class AnvilCrushingHandler {
    /** A freshly spawned Wither is invulnerable for 220 ticks; the anvil "collapses" it during the first 4 seconds (80 ticks). */
    public static final int WITHER_SPAWN_TICKS = 220;
    public static final int WITHER_WINDOW_TICKS = 80;

    /**
     * While the Wither is invulnerable (just summoned) it takes no damage at all, and no damage event happens.
     * So the falling anvil is caught by intersecting its hitbox every tick of the window.
     */
    @SubscribeEvent
    public static void onEntityTick(EntityTickEvent.Pre event) {
        if (!(event.getEntity() instanceof WitherBoss wither) || !(wither.level() instanceof ServerLevel serverLevel) || wither.isRemoved()) {
            return;
        }
        int invul = wither.getInvulnerableTicks();
        if (invul < WITHER_SPAWN_TICKS - WITHER_WINDOW_TICKS || invul > WITHER_SPAWN_TICKS) {
            return;
        }
        for (FallingBlockEntity fb : serverLevel.getEntitiesOfClass(FallingBlockEntity.class, wither.getBoundingBox().inflate(0.4))) {
            if (fb.getBlockState().getBlock() instanceof AnvilBlock) {
                implodeWither(serverLevel, wither);
                fb.discard();
                return;
            }
        }
    }

    private static void implodeWither(ServerLevel serverLevel, WitherBoss wither) {
        ItemEntity drop = new ItemEntity(serverLevel, wither.getX(), wither.getY() + 0.5, wither.getZ(),
                new ItemStack(ModItems.IMPLODED_WITHER_PLASMA.get()));
        serverLevel.addFreshEntity(drop);

        serverLevel.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, wither.getX(), wither.getY() + 1.0, wither.getZ(),
                60, 0.4, 0.4, 0.4, 0.08);
        serverLevel.sendParticles(ParticleTypes.ENCHANTED_HIT, wither.getX(), wither.getY() + 1.0, wither.getZ(),
                40, 0.5, 0.5, 0.5, 0.1);

        serverLevel.playSound(null, wither.getX(), wither.getY(), wither.getZ(),
                ModSounds.ANVIL_CRUSH.get(), SoundSource.BLOCKS, 1.5f, 0.7f);
        serverLevel.playSound(null, wither.getX(), wither.getY(), wither.getZ(),
                SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 1.0f, 1.8f);

        notifyNearbyPlayers(serverLevel, wither.blockPosition(),
                Component.translatable("message.golem_logix.wither_imploded").withStyle(ChatFormatting.DARK_PURPLE));
        wither.discard();
    }

    @SubscribeEvent
    public static void onLivingDamage(LivingDamageEvent.Pre event) {
        Entity victim = event.getEntity();
        Level level = victim.level();

        if (level.isClientSide()) {
            return;
        }

        // Comprehensive anvil detection
        boolean isAnvil = false;
        Entity directSource = event.getSource().getDirectEntity();

        if (directSource instanceof FallingBlockEntity fallingBlock) {
            if (fallingBlock.getBlockState().getBlock() instanceof AnvilBlock) {
                isAnvil = true;
            }
        }

        if (!isAnvil && (event.getSource().is(DamageTypes.FALLING_ANVIL) || event.getSource().is(DamageTypes.FALLING_BLOCK))) {
            // Check if there is an anvil block or falling anvil nearby
            BlockPos pos = victim.blockPosition();
            if (level.getBlockState(pos).getBlock() instanceof AnvilBlock ||
                level.getBlockState(pos.above()).getBlock() instanceof AnvilBlock ||
                level.getBlockState(pos.above(2)).getBlock() instanceof AnvilBlock) {
                isAnvil = true;
            } else if (directSource instanceof FallingBlockEntity) {
                isAnvil = true;
            }
        }

        if (!isAnvil) {
            // Check direct vicinity for falling anvil entities hitting this tick
            AABB box = victim.getBoundingBox().inflate(1.0, 2.0, 1.0);
            List<FallingBlockEntity> nearbyFalling = level.getEntitiesOfClass(FallingBlockEntity.class, box);
            for (FallingBlockEntity fb : nearbyFalling) {
                if (fb.getBlockState().getBlock() instanceof AnvilBlock) {
                    isAnvil = true;
                    break;
                }
            }
        }

        if (!isAnvil) {
            return;
        }

        float currentHealth = event.getEntity().getHealth();
        ServerLevel serverLevel = (ServerLevel) level;

        // 1. The Wither in its spawn window (in case damage did get through)
        if (victim instanceof WitherBoss wither) {
            int invulTicks = wither.getInvulnerableTicks();
            if (invulTicks >= WITHER_SPAWN_TICKS - WITHER_WINDOW_TICKS) {
                implodeWither(serverLevel, wither);
                event.setNewDamage(0.0f);
                return;
            }
        }

        // 2. Golem Crushing with HP check
        Item rewardItem = null;
        boolean isCandidateGolem = false;
        boolean healthTooHigh = false;

        if (victim instanceof IronGolem) {
            isCandidateGolem = true;
            if (currentHealth >= 0.5f && currentHealth <= 4.0f) {
                rewardItem = ModItems.FLATTENED_IRON_CORE.get();
            } else if (currentHealth > 4.0f) {
                healthTooHigh = true;
            }
        } else if (victim instanceof SnowGolem) {
            isCandidateGolem = true;
            // Snow Golem max HP is 4.0. Must NOT be full HP (<= 3.9f)
            if (currentHealth >= 0.5f && currentHealth <= 3.9f) {
                rewardItem = ModItems.FLATTENED_CRYO_SUBSTRATE.get();
            } else if (currentHealth >= 4.0f) {
                healthTooHigh = true;
            }
        } else if (isCopperGolem(victim)) {
            isCandidateGolem = true;
            if (currentHealth >= 0.5f && currentHealth <= 4.0f) {
                rewardItem = ModItems.FLATTENED_COPPER_MATRIX.get();
            } else if (currentHealth > 4.0f) {
                healthTooHigh = true;
            }
        }

        if (healthTooHigh) {
            // Helpful in-game guidance so the player knows what to do!
            if (victim instanceof SnowGolem) {
                notifyNearbyPlayers(serverLevel, victim.blockPosition(),
                        Component.translatable("message.golem_logix.snow_golem_full_hp").withStyle(ChatFormatting.YELLOW));
            } else {
                notifyNearbyPlayers(serverLevel, victim.blockPosition(),
                        Component.translatable("message.golem_logix.golem_hp_too_high", String.format("%.0f", currentHealth))
                                .withStyle(ChatFormatting.YELLOW));
            }
            // Spawn warning clink sparks
            serverLevel.sendParticles(ParticleTypes.CRIT, victim.getX(), victim.getY() + 1.0, victim.getZ(),
                    15, 0.3, 0.3, 0.3, 0.1);
            return;
        }

        if (rewardItem != null) {
            // Successful crush!
            ItemEntity drop = new ItemEntity(serverLevel, victim.getX(), victim.getY() + 0.2, victim.getZ(),
                    new ItemStack(rewardItem));
            serverLevel.addFreshEntity(drop);

            serverLevel.sendParticles(ParticleTypes.SNOWFLAKE, victim.getX(), victim.getY() + 0.5, victim.getZ(),
                    35, 0.3, 0.3, 0.3, 0.05);
            serverLevel.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, victim.getX(), victim.getY() + 0.5, victim.getZ(),
                    25, 0.2, 0.2, 0.2, 0.03);

            serverLevel.playSound(null, victim.getX(), victim.getY(), victim.getZ(),
                    ModSounds.ANVIL_CRUSH.get(), SoundSource.BLOCKS, 1.2f, 1.0f);
            serverLevel.playSound(null, victim.getX(), victim.getY(), victim.getZ(),
                    SoundEvents.ANVIL_LAND, SoundSource.BLOCKS, 1.0f, 0.9f);

            notifyNearbyPlayers(serverLevel, victim.blockPosition(),
                    Component.translatable("message.golem_logix.crush_success", rewardItem.getName(new ItemStack(rewardItem)).getString())
                            .withStyle(ChatFormatting.AQUA));
            // the first plate made on an anvil opens the Dispel method to everyone who saw it
            for (Player p : serverLevel.getEntitiesOfClass(Player.class, new AABB(victim.blockPosition()).inflate(12.0))) {
                if (p instanceof net.minecraft.server.level.ServerPlayer sp) {
                    com.golemlogix.secret.SecretKnowledge.plateMade(sp);
                }
            }

            victim.discard();
            event.setNewDamage(0.0f);
        }
    }

    /**
     * The copper golem comes from Copper Age Backport (minecraft:copper_golem, as in 1.21.9) or other mods: any golem
     * with "copper" in its id will do.
     */
    private static boolean isCopperGolem(Entity entity) {
        String path = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).getPath();
        return path.contains("copper_golem") || (entity instanceof net.minecraft.world.entity.animal.AbstractGolem && path.contains("copper"));
    }

    private static void notifyNearbyPlayers(ServerLevel level, BlockPos pos, Component message) {
        AABB searchBox = new AABB(pos).inflate(12.0);
        List<Player> players = level.getEntitiesOfClass(Player.class, searchBox);
        for (Player p : players) {
            p.displayClientMessage(message, true); // Displays directly in Action Bar!
        }
    }
}
