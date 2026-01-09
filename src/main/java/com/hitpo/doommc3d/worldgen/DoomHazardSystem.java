package com.hitpo.doommc3d.worldgen;

import com.hitpo.doommc3d.interact.DoomLevelBoundsRegistry;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;

public final class DoomHazardSystem {
    private static final Map<UUID, Integer> NEXT_DAMAGE_TICK = new HashMap<>();

    private DoomHazardSystem() {
    }

    public static void register() {
        ServerTickEvents.END_WORLD_TICK.register(DoomHazardSystem::tickWorld);
    }

    private static void tickWorld(ServerWorld world) {
        Box bounds = DoomLevelBoundsRegistry.get(world);
        if (bounds == null) {
            return;
        }

        int now = (int) world.getTime();
        for (ServerPlayerEntity player : world.getPlayers()) {
            if (!bounds.contains(player.getX(), player.getY(), player.getZ())) {
                continue;
            }
            if (player.isCreative() || player.isSpectator()) {
                continue;
            }
            applyHazardAtFeet(world, player, now);
        }

        // Optionally damage Doom mobs too (keeps it Doom-authentic and avoids "safe" nukage).
        for (LivingEntity entity : world.getEntitiesByClass(LivingEntity.class, bounds, DoomHazardSystem::isDoomDamageableEntity)) {
            if (entity instanceof PlayerEntity) {
                continue;
            }
            applyHazardAtFeet(world, entity, now);
        }
    }

    private static boolean isDoomDamageableEntity(LivingEntity entity) {
        if (!entity.isAlive()) {
            return false;
        }
        // Only entities we spawned/own.
        var tags = entity.getCommandTags();
        return tags.contains(DoomThingSpawner.TAG_SPAWNED);
    }

    private static void applyHazardAtFeet(ServerWorld world, LivingEntity entity, int now) {
        int next = NEXT_DAMAGE_TICK.getOrDefault(entity.getUuid(), 0);
        if (now < next) {
            return;
        }

        BlockPos feet = entity.getBlockPos();
        BlockState below = world.getBlockState(feet.down());

        Hazard hazard = hazardForBlock(below);
        if (hazard == Hazard.NONE) {
            return;
        }

        NEXT_DAMAGE_TICK.put(entity.getUuid(), now + hazard.intervalTicks);

        entity.damage(world, hazard.damageSource(world, entity), hazard.damage);
        if (hazard.igniteSeconds > 0) {
            entity.setOnFireFor(hazard.igniteSeconds);
        }

        if (now % 20 == 0) {
            playFx(world, feet, hazard);
        }
    }

    private static Hazard hazardForBlock(BlockState state) {
        // Support old palette (lime concrete) and new palette (glass).
        if (state.isOf(Blocks.LIME_STAINED_GLASS) || state.isOf(Blocks.LIME_CONCRETE)) {
            return Hazard.NUKAGE;
        }
        if (state.isOf(Blocks.ORANGE_STAINED_GLASS) || state.isOf(Blocks.MAGMA_BLOCK) || state.isOf(Blocks.ORANGE_CONCRETE)) {
            return Hazard.LAVA;
        }
        return Hazard.NONE;
    }

    private static void playFx(ServerWorld world, BlockPos pos, Hazard hazard) {
        double x = pos.getX() + 0.5;
        double y = pos.getY() + 0.1;
        double z = pos.getZ() + 0.5;
        if (hazard == Hazard.NUKAGE) {
            world.spawnParticles(ParticleTypes.ITEM_SLIME, x, y, z, 4, 0.25, 0.05, 0.25, 0.0);
            world.playSound(null, x, y, z, SoundEvents.BLOCK_SLIME_BLOCK_STEP, SoundCategory.PLAYERS, 0.25f, 0.8f);
        } else if (hazard == Hazard.LAVA) {
            world.spawnParticles(ParticleTypes.LAVA, x, y, z, 3, 0.25, 0.02, 0.25, 0.0);
            world.spawnParticles(ParticleTypes.SMOKE, x, y + 0.2, z, 2, 0.15, 0.05, 0.15, 0.0);
            world.playSound(null, x, y, z, SoundEvents.BLOCK_LAVA_POP, SoundCategory.PLAYERS, 0.35f, 1.0f);
        }
    }

    private enum Hazard {
        NONE(0, 0.0f, 0, 0) {
            @Override
            net.minecraft.entity.damage.DamageSource damageSource(ServerWorld world, LivingEntity entity) {
                return entity.getDamageSources().generic();
            }
        },
        NUKAGE(20, 2.0f, 0, 0) {
            @Override
            net.minecraft.entity.damage.DamageSource damageSource(ServerWorld world, LivingEntity entity) {
                return entity.getDamageSources().magic();
            }
        },
        LAVA(10, 3.0f, 2, 0) {
            @Override
            net.minecraft.entity.damage.DamageSource damageSource(ServerWorld world, LivingEntity entity) {
                return entity.getDamageSources().lava();
            }
        };

        final int intervalTicks;
        final float damage;
        final int igniteSeconds;
        final int unused;

        Hazard(int intervalTicks, float damage, int igniteSeconds, int unused) {
            this.intervalTicks = intervalTicks;
            this.damage = damage;
            this.igniteSeconds = igniteSeconds;
            this.unused = unused;
        }

        abstract net.minecraft.entity.damage.DamageSource damageSource(ServerWorld world, LivingEntity entity);
    }
}

