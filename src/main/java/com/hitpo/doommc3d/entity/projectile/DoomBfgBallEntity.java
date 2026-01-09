package com.hitpo.doommc3d.entity.projectile;

import com.hitpo.doommc3d.worldgen.DoomHitscan;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.projectile.thrown.ThrownItemEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;

/**
 * BFG ball projectile. On impact, does a Doom-like 40-ray spray originating from the shooter's firing position.
 */
public final class DoomBfgBallEntity extends ThrownItemEntity {
    private Vec3d shooterPos = null;
    private float shooterYaw;
    private float shooterPitch;

    public DoomBfgBallEntity(EntityType<? extends ThrownItemEntity> type, World world) {
        super(type, world);
    }

    public DoomBfgBallEntity(World world, LivingEntity owner) {
        super(com.hitpo.doommc3d.entity.ModEntities.DOOM_BFG_BALL, owner, world, new ItemStack(Items.NETHER_STAR));
    }

    public void setShooterSnapshot(Vec3d pos, float yaw, float pitch) {
        this.shooterPos = pos;
        this.shooterYaw = yaw;
        this.shooterPitch = pitch;
    }

    @Override
    protected Item getDefaultItem() {
        // Visible projectile without custom renderer.
        return Items.NETHER_STAR;
    }

    @Override
    protected void onEntityHit(EntityHitResult entityHitResult) {
        super.onEntityHit(entityHitResult);
        if (!getEntityWorld().isClient() && getEntityWorld() instanceof ServerWorld world) {
            doImpact(world);
        }
    }

    @Override
    protected void onBlockHit(BlockHitResult blockHitResult) {
        super.onBlockHit(blockHitResult);
        if (!getEntityWorld().isClient() && getEntityWorld() instanceof ServerWorld world) {
            doImpact(world);
        }
    }

    private void doImpact(ServerWorld world) {
        // Small explosion feedback; Doom damage comes primarily from spray.
        world.createExplosion(this, getX(), getY(), getZ(), 1.5f, false, World.ExplosionSourceType.NONE);

        Vec3d origin = shooterPos != null ? shooterPos : new Vec3d(getX(), getY(), getZ());
        float baseYaw = shooterYaw;
        float pitch = shooterPitch;

        // Doom BFG spray: 40 rays across 90 degrees.
        Random random = getRandom();
        for (int i = 0; i < 40; i++) {
            float yaw = baseYaw - 45.0f + (i * (90.0f / 40.0f));
            // Vanilla Doom: sum 15 rolls of (1..8) => 15d8.
            int damage = 0;
            for (int j = 0; j < 15; j++) {
                damage += (random.nextInt(8) + 1);
            }
            DoomHitscan.fireBfgSprayRay(world, this, origin, yaw, pitch, damage);
        }

        discard();
    }
}
