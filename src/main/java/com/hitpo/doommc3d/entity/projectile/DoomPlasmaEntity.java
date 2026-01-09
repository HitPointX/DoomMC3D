package com.hitpo.doommc3d.entity.projectile;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.projectile.thrown.ThrownItemEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;

/**
 * Doom plasma projectile: direct-hit only, no radius explosion.
 */
public final class DoomPlasmaEntity extends ThrownItemEntity {
    // Doom plasma impact damage: ((1..8) * 5)
    private static final int IMPACT_DAMAGE_MULT = 5;

    private double desiredSpeed = -1.0;

    public DoomPlasmaEntity(EntityType<? extends ThrownItemEntity> type, World world) {
        super(type, world);
        setNoGravity(true);
    }

    public DoomPlasmaEntity(World world, LivingEntity owner) {
        super(com.hitpo.doommc3d.entity.ModEntities.DOOM_PLASMA, owner, world, new ItemStack(Items.ENDER_PEARL));
        setNoGravity(true);
    }

    @Override
    public void setVelocity(Vec3d velocity) {
        super.setVelocity(velocity);
        if (desiredSpeed < 0.0) {
            desiredSpeed = velocity.length();
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (isRemoved() || desiredSpeed <= 0.0) {
            return;
        }
        Vec3d v = getVelocity();
        double len = v.length();
        if (len > 1e-6) {
            super.setVelocity(v.multiply(desiredSpeed / len));
        }
    }

    @Override
    protected Item getDefaultItem() {
        return Items.ENDER_PEARL;
    }

    @Override
    protected void onEntityHit(EntityHitResult entityHitResult) {
        super.onEntityHit(entityHitResult);
        if (!(getEntityWorld() instanceof ServerWorld world)) {
            return;
        }

        Entity hit = entityHitResult.getEntity();
        if (hit instanceof LivingEntity living && living.isAlive()) {
            int dmg = rollDoomMissileDamage(getRandom(), IMPACT_DAMAGE_MULT);
            living.damage(world, getImpactDamageSource(world), dmg);
            Vec3d p = entityHitResult.getPos();
            world.spawnParticles(ParticleTypes.CRIT, p.x, p.y, p.z, 6, 0.12, 0.12, 0.12, 0.06);
        }

        discard();
    }

    @Override
    protected void onBlockHit(BlockHitResult blockHitResult) {
        super.onBlockHit(blockHitResult);
        if (getEntityWorld() instanceof ServerWorld world) {
            Vec3d p = blockHitResult.getPos();
            world.spawnParticles(ParticleTypes.SMOKE, p.x, p.y, p.z, 3, 0.03, 0.03, 0.03, 0.01);
        }
        discard();
    }

    private DamageSource getImpactDamageSource(ServerWorld world) {
        Entity owner = getOwner();
        if (owner instanceof ServerPlayerEntity player) {
            return player.getDamageSources().playerAttack(player);
        }
        if (owner instanceof LivingEntity living) {
            return living.getDamageSources().mobAttack(living);
        }
        return world.getDamageSources().generic();
    }

    private static int rollDoomMissileDamage(Random random, int damageMult) {
        return (random.nextInt(8) + 1) * damageMult;
    }
}
