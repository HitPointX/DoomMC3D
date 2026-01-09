package com.hitpo.doommc3d.doomai;

import com.hitpo.doommc3d.worldgen.DoomHitscan;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.projectile.WitherSkullEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Vec3d;

public final class DoomBossBrain {
    private static final double DOOM_TICS_PER_MC_TICK = 35.0 / 20.0;

    private final DoomBossType type;
    private double doomTicAccumulator = 0.0;

    private int attackCooldownTics = 0;
    private int burstRemaining = 0;
    private int burstSpacingTics = 0;

    public DoomBossBrain(DoomBossType type) {
        this.type = type;
    }

    public void tick(ServerWorld world, MobEntity mob) {
        doomTicAccumulator += DOOM_TICS_PER_MC_TICK;
        while (doomTicAccumulator >= 1.0) {
            doomTicAccumulator -= 1.0;
            stepDoomTic(world, mob);
        }
    }

    public void applyBossTuning(MobEntity mob) {
        mob.setPersistent();
        setAttr(mob, EntityAttributes.KNOCKBACK_RESISTANCE, 0.8);
        setAttr(mob, EntityAttributes.STEP_HEIGHT, 1.0);
        setAttr(mob, EntityAttributes.SCALE, type == DoomBossType.CYBERDEMON ? 2.6 : 3.0);
        setAttr(mob, EntityAttributes.MOVEMENT_SPEED, type == DoomBossType.CYBERDEMON ? 0.18 : 0.22);
        setAttr(mob, EntityAttributes.MAX_HEALTH, type == DoomBossType.CYBERDEMON ? 400.0 : 300.0);
        mob.setHealth(mob.getMaxHealth());
    }

    private void stepDoomTic(ServerWorld world, MobEntity mob) {
        if (!mob.isAlive()) {
            return;
        }
        if (attackCooldownTics > 0) {
            attackCooldownTics--;
        }
        if (burstSpacingTics > 0) {
            burstSpacingTics--;
        }

        var closest = world.getClosestPlayer(mob, 64.0);
        if (!(closest instanceof net.minecraft.server.network.ServerPlayerEntity target)) {
            return;
        }
        mob.setTarget(target);
        faceTarget(mob, target);

        double distSq = mob.squaredDistanceTo(target);
        boolean hasLos = mob.canSee(target);

        // Move unless we're in a burst.
        if (burstRemaining == 0) {
            if (distSq > 6.0 * 6.0) {
                Vec3d to = target.getEntityPos().subtract(mob.getEntityPos());
                Vec3d vel = new Vec3d(to.x, 0, to.z);
                if (vel.lengthSquared() > 0.0001) {
                    vel = vel.normalize().multiply(0.12);
                    mob.setVelocity(vel.x, mob.getVelocity().y, vel.z);
                }
            } else {
                mob.setVelocity(0, mob.getVelocity().y, 0);
            }
        }

        if (!hasLos) {
            return;
        }

        switch (type) {
            case CYBERDEMON -> tickCyber(world, mob, target);
            case SPIDER_MASTERMIND -> tickSpider(world, mob, target);
        }
    }

    private void tickCyber(ServerWorld world, MobEntity mob, LivingEntity target) {
        if (burstRemaining > 0) {
            if (burstSpacingTics > 0) {
                return;
            }
            burstRemaining--;
            burstSpacingTics = 7; // ~0.2s at 35hz
            fireRocket(world, mob, target);
            if (burstRemaining == 0) {
                attackCooldownTics = 70; // ~2s
            }
            return;
        }

        if (attackCooldownTics > 0) {
            return;
        }

        burstRemaining = 3;
        burstSpacingTics = 0;
    }

    private void tickSpider(ServerWorld world, MobEntity mob, LivingEntity target) {
        if (attackCooldownTics > 0) {
            return;
        }
        // Sustained hitscan burst.
        DoomHitscan.fireMonsterHitscan(world, mob, target, 6, 48.0);
        world.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.ENTITY_SKELETON_SHOOT, SoundCategory.HOSTILE, 0.9f, 0.75f);
        attackCooldownTics = 5; // rapid fire
    }

    private void fireRocket(ServerWorld world, MobEntity mob, LivingEntity target) {
        Vec3d start = mob.getEyePos().add(mob.getRotationVec(1.0f).multiply(0.6));
        Vec3d aim = target.getEyePos().subtract(start).normalize().multiply(0.9);
        WitherSkullEntity rocket = new WitherSkullEntity(world, mob, aim);
        rocket.setPosition(start.x, start.y, start.z);
        rocket.setCharged(false);
        world.spawnEntity(rocket);
        world.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.ENTITY_GHAST_SHOOT, SoundCategory.HOSTILE, 1.0f, 0.9f);
    }

    private static void faceTarget(MobEntity mob, Entity target) {
        Vec3d from = mob.getEyePos();
        Vec3d to = target.getEyePos();
        Vec3d delta = to.subtract(from);
        double dx = delta.x;
        double dz = delta.z;
        float yaw = (float) (Math.atan2(dz, dx) * (180.0 / Math.PI)) - 90.0f;
        mob.setYaw(yaw);
        mob.setHeadYaw(yaw);
    }

    private static void setAttr(MobEntity mob, net.minecraft.registry.entry.RegistryEntry<net.minecraft.entity.attribute.EntityAttribute> attr, double value) {
        EntityAttributeInstance inst = mob.getAttributeInstance(attr);
        if (inst != null) {
            inst.setBaseValue(value);
        }
    }
}
