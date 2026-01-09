package com.hitpo.doommc3d.entity.projectile;

import com.hitpo.doommc3d.worldgen.DoomHitscan;
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
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;

/**
 * Doom rocket projectile: direct-hit damage plus radius attack (A_Explode) with base damage 128.
 */
public final class DoomRocketEntity extends ThrownItemEntity {
    private static final double DOOM_UNITS_PER_BLOCK = 64.0;

    private double desiredSpeed = -1.0;

    // Doom rocket impact damage: ((1..8) * 20)
    private static final int IMPACT_DAMAGE_MULT = 20;

    // Doom A_Explode radius-attack base damage.
    private static final int RADIUS_DAMAGE = 128;
    private static final int MAXRADIUS = 32;

    public DoomRocketEntity(EntityType<? extends ThrownItemEntity> type, World world) {
        super(type, world);
        setNoGravity(true);
    }

    public DoomRocketEntity(World world, LivingEntity owner) {
        super(com.hitpo.doommc3d.entity.ModEntities.DOOM_ROCKET, owner, world, new ItemStack(Items.FIRE_CHARGE));
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
        return Items.FIRE_CHARGE;
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
        }

        explode(world);
    }

    @Override
    protected void onBlockHit(BlockHitResult blockHitResult) {
        super.onBlockHit(blockHitResult);
        if (getEntityWorld() instanceof ServerWorld world) {
            explode(world);
        }
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

    private DamageSource getExplosionDamageSource(ServerWorld world) {
        Entity owner = getOwner();
        return world.getDamageSources().explosion(null, owner);
    }

    private static int rollDoomMissileDamage(Random random, int damageMult) {
        // Doom missile impact: ((P_Random()%8)+1) * info.damage
        return (random.nextInt(8) + 1) * damageMult;
    }

    private void explode(ServerWorld world) {
        Vec3d center = new Vec3d(getX(), getY(), getZ());

        // Visual feedback only; do Doom-style damage ourselves.
        world.spawnParticles(ParticleTypes.EXPLOSION_EMITTER, center.x, center.y, center.z, 1, 0, 0, 0, 0);

        // Doom radius-attack range is (damage + MAXRADIUS) map units.
        double radiusBlocks = (RADIUS_DAMAGE + MAXRADIUS) / DOOM_UNITS_PER_BLOCK;
        Box aabb = new Box(
            center.x - radiusBlocks,
            center.y - radiusBlocks,
            center.z - radiusBlocks,
            center.x + radiusBlocks,
            center.y + radiusBlocks,
            center.z + radiusBlocks
        );

        DamageSource explosionSource = getExplosionDamageSource(world);
        for (Entity e : world.getOtherEntities(null, aabb)) {
            if (!(e instanceof LivingEntity living) || !living.isAlive()) {
                continue;
            }

            Vec3d victimPos = new Vec3d(living.getX(), living.getY(), living.getZ());
            double distUnits = Math.max(0.0, center.distanceTo(victimPos) * DOOM_UNITS_PER_BLOCK);
            int dmg = RADIUS_DAMAGE - (int) Math.floor(distUnits);
            if (dmg <= 0) {
                continue;
            }

            living.damage(world, explosionSource, dmg);
        }

        // Doom explosion sound.
        DoomHitscan.playDoomSfxNear(world, "DSBAREXP", center, 64.0);

        discard();
    }
}
