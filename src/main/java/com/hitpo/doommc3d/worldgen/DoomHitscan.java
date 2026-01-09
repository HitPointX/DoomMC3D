package com.hitpo.doommc3d.worldgen;

import com.hitpo.doommc3d.item.ModItems;
import com.hitpo.doommc3d.interact.DoomLevelBoundsRegistry;
import com.hitpo.doommc3d.interact.DoomLineTriggerSystem;
import com.hitpo.doommc3d.net.PlayDoomSfxPayload;
import com.hitpo.doommc3d.doomai.DoomMobSystem;
import com.hitpo.doommc3d.player.DoomAmmoAccess;
import com.hitpo.doommc3d.player.DoomAmmoType;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import com.hitpo.doommc3d.net.WeaponFiredPayload;
import net.minecraft.network.PacketByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.RaycastContext;
import net.minecraft.text.Text;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;
import com.hitpo.doommc3d.doomai.DoomMobTags;
import com.hitpo.doommc3d.doomai.DoomMobType;

public final class DoomHitscan {
    private DoomHitscan() {
    }

    // Doom barrel explosion is effectively a rocket-style blast (128 damage, 128 radius).
    // We keep Doom damage numbers, but convert radius to blocks using the same scale as geometry.
    private static final int BARREL_BLAST_DAMAGE = 128;
    private static final double DOOM_UNITS_PER_BLOCK = 64.0;
    private static final double BARREL_BLAST_RADIUS_BLOCKS = 128.0 / DOOM_UNITS_PER_BLOCK;

    // Vanilla Doom hitscan range is 2048 map units.
    // With our scale of 64 units per block, that is 32 blocks.
    private static final double DOOM_HITSCAN_RANGE_BLOCKS = 2048.0 / DOOM_UNITS_PER_BLOCK;

    // Doom BFG tracer range is 1024 map units.
    private static final double DOOM_BFG_SPRAY_RANGE_BLOCKS = 1024.0 / DOOM_UNITS_PER_BLOCK;

    // Doom spread uses: (P_Random() - P_Random()) << 18 in angle units.
    // Angle units are 0..2^32 mapping to 0..360 degrees.
    // (diff << 18) * 360 / 2^32 = diff * 360 / 2^14 = diff * 360 / 16384.
    private static final float DOOM_SPREAD_DEG_PER_RND_DIFF = 360.0f / 16384.0f;

    private static float doomSpreadYawDegrees(Random random) {
        int diff = random.nextInt(256) - random.nextInt(256);
        return diff * DOOM_SPREAD_DEG_PER_RND_DIFF;
    }

    private static Vec3d rotationVector(float yawDegrees, float pitchDegrees) {
        float yawRad = yawDegrees * ((float) Math.PI / 180.0f);
        float pitchRad = pitchDegrees * ((float) Math.PI / 180.0f);
        float cosPitch = MathHelper.cos(pitchRad);
        float sinPitch = MathHelper.sin(pitchRad);
        float cosYaw = MathHelper.cos(yawRad);
        float sinYaw = MathHelper.sin(yawRad);
        return new Vec3d(-sinYaw * cosPitch, -sinPitch, cosYaw * cosPitch);
    }

    private static final java.util.Map<java.util.UUID, Long> CHAINGUN_LAST_FIRE_TICK = new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.Map<java.util.UUID, Integer> CHAINGUN_REFIRE = new java.util.concurrent.ConcurrentHashMap<>();

    private static final java.util.Map<java.util.UUID, Long> PISTOL_LAST_FIRE_TICK = new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.Map<java.util.UUID, Integer> PISTOL_REFIRE = new java.util.concurrent.ConcurrentHashMap<>();

    // Doom runs at 35 tics/sec; Minecraft is 20 ticks/sec.
    private static final double DOOM_TIC_TO_MC_TICK = 20.0 / 35.0;

    // Fire intervals when holding the trigger, in Doom tics (using A_ReFire immediate behavior).
    // Pistol: S_PISTOL1(4) -> S_PISTOL2(6 fire) -> S_PISTOL3(4) then A_ReFire in S_PISTOL4 immediately restarts.
    private static final int DOOM_PISTOL_REFIRE_TICS = 14;
    // Chaingun: one bullet per 4 tics.
    private static final int DOOM_CHAINGUN_REFIRE_TICS = 4;
    // Shotgun: S_SGUN1..S_SGUN8 then A_ReFire in S_SGUN9 immediately restarts.
    private static final int DOOM_SHOTGUN_REFIRE_TICS = 37;
    // Rocket launcher: S_MISSILE1(8) + S_MISSILE2(12 fire) then A_ReFire immediately restarts.
    private static final int DOOM_ROCKET_REFIRE_TICS = 20;
    // Plasma: S_PLASMA1(3 fire) then A_ReFire in S_PLASMA2 immediately restarts.
    private static final int DOOM_PLASMA_REFIRE_TICS = 3;
    // BFG: S_BFG1(20 sound) + S_BFG2(10) + S_BFG3(10 fire) then A_ReFire immediately restarts.
    private static final int DOOM_BFG_REFIRE_TICS = 40;

    private static final java.util.Map<java.util.UUID, Double> NEXT_PISTOL_TICK = new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.Map<java.util.UUID, Double> NEXT_SHOTGUN_TICK = new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.Map<java.util.UUID, Double> NEXT_CHAINGUN_TICK = new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.Map<java.util.UUID, Double> NEXT_ROCKET_TICK = new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.Map<java.util.UUID, Double> NEXT_PLASMA_TICK = new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.Map<java.util.UUID, Double> NEXT_BFG_TICK = new java.util.concurrent.ConcurrentHashMap<>();

    private static boolean tryConsumeFireWindow(ServerWorld world, ServerPlayerEntity player, java.util.Map<java.util.UUID, Double> nextMap, int doomTicsInterval) {
        double now = world.getTime();
        double next = nextMap.getOrDefault(player.getUuid(), 0.0);
        if (now + 1e-6 < next) {
            return false;
        }
        double interval = doomTicsInterval * DOOM_TIC_TO_MC_TICK;
        // Avoid any accidental zero/negative interval.
        interval = Math.max(0.05, interval);
        double scheduled = Math.max(next, now) + interval;
        nextMap.put(player.getUuid(), scheduled);
        return true;
    }

    private static int computeRefire(ServerWorld world, java.util.UUID playerId, java.util.Map<java.util.UUID, Long> lastTickMap, java.util.Map<java.util.UUID, Integer> refireMap) {
        long now = world.getTime();
        long last = lastTickMap.getOrDefault(playerId, -999L);
        int refire = (now - last) <= 1 ? (refireMap.getOrDefault(playerId, 0) + 1) : 0;
        lastTickMap.put(playerId, now);
        refireMap.put(playerId, refire);
        return refire;
    }

    private static int rollBulletDamage(Random random) {
        // Classic Doom hitscan bullet damage: 5 * (1..3)
        // This applies to pistol, shotgun, chaingun
        return 5 * (1 + random.nextInt(3));
    }

    /**
     * Doom damage formula for pistol/shotgun/chaingun shots.
     * In classic Doom, each bullet deals (1d3)*5 = 5, 10, or 15 points.
     */
    private static float getDamageForMonsterHitscan(Random random) {
        return rollBulletDamage(random);
    }

    /**
     * Monster damage when they fire at the player.
     * Slightly different spread than player weapons.
     */
    private static float getMonsterHitscanDamage(Random random) {
        // Default monster hitscan damage: bullets/pellets use rollBulletDamage
        return rollBulletDamage(random);
    }

    private static float getMonsterHitscanDamageForType(Random random, LivingEntity attacker) {
        // Map Doom mob types to their classic hitscan damage dice where applicable.
        // Falls back to rollBulletDamage for standard hitscan bullets.
        for (com.hitpo.doommc3d.doomai.DoomMobType t : com.hitpo.doommc3d.doomai.DoomMobType.values()) {
            if (attacker.getCommandTags().contains(com.hitpo.doommc3d.doomai.DoomMobTags.tagForType(t))) {
                return switch (t) {
                    // Zombieman / Shotgun guy / Chaingunner hitscan bullets use Doom's
                    // ((P_Random()%5)+1)*3 formula per pellet (values: 3,6,9,12,15)
                    case ZOMBIEMAN, SHOTGUN_GUY, CHAINGUNNER -> ((random.nextInt(5) + 1) * 3);
                    // Other monsters either use projectile attacks or different melee formulas;
                    // fall back to reasonable approximations when necessary.
                    case IMP -> rollBulletDamage(random);
                    case DEMON, SPECTRE -> 10 * (1 + random.nextInt(4));
                    case LOST_SOUL -> (random.nextInt(8) + 1) * 3; // 1d8*3 -> 3-24 approximating 3d8
                    case CACODEMON -> rollBulletDamage(random);
                    case BARON -> 8 * (1 + random.nextInt(8));
                };
            }
        }
        return rollBulletDamage(random);
    }

    private static void playBarrelExplosionSfx(ServerWorld world, BlockPos pos) {
        // Heard by nearby players; client handles attenuation based on (x,y,z).
        double x = pos.getX() + 0.5;
        double y = pos.getY() + 0.5;
        double z = pos.getZ() + 0.5;
        playDoomSfxNear(world, "DSBAREXP", new Vec3d(x, y, z), 64.0);
    }

    public static void playDoomSfxNear(ServerWorld world, String lump, Vec3d pos, double maxDistanceBlocks) {
        double maxDistSq = maxDistanceBlocks * maxDistanceBlocks;
        for (ServerPlayerEntity player : world.getPlayers()) {
            if (player.squaredDistanceTo(pos.x, pos.y, pos.z) <= maxDistSq) {
                ServerPlayNetworking.send(player, new PlayDoomSfxPayload(lump, pos.x, pos.y, pos.z, 1.0f, 1.0f));
            }
        }
    }

    private static void explodeBarrel(ServerWorld world, Entity source, BlockPos pos) {
        // Remove the block first so it never drops as an item.
        world.setBlockState(pos, Blocks.AIR.getDefaultState(), 3);

        // Barrel explosions are loud in Doom; wake nearby monsters.
        DoomMobSystem.alertSound(world, new Vec3d(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5), 64.0);

        playBarrelExplosionSfx(world, pos);
        world.spawnParticles(ParticleTypes.EXPLOSION_EMITTER, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 1, 0, 0, 0, 0);

        // Doom barrels should damage entities but not carve the level.
        world.createExplosion(
            source,
            pos.getX() + 0.5,
            pos.getY() + 0.5,
            pos.getZ() + 0.5,
            3.0f,
            false,
            net.minecraft.world.World.ExplosionSourceType.NONE
        );

        // Chain reaction + deterministic barrel damage (Doom-like radius damage).
        // We do our own radial damage so barrels don't depend on vanilla TNT logic.
        double radius = BARREL_BLAST_RADIUS_BLOCKS;
        if (radius <= 0.01) {
            return;
        }

        Vec3d center = new Vec3d(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
        Box aabb = new Box(
            center.x - radius,
            center.y - radius,
            center.z - radius,
            center.x + radius,
            center.y + radius,
            center.z + radius
        );

        // Damage entities.
        for (Entity e : world.getOtherEntities(null, aabb)) {
            if (!(e instanceof LivingEntity living) || !living.isAlive()) {
                continue;
            }
            Vec3d victimPos = new Vec3d(living.getX(), living.getY(), living.getZ());
            double dist = Math.max(0.0, center.distanceTo(victimPos));
            double t = 1.0 - (dist / radius);
            if (t <= 0.0) {
                continue;
            }
            float dmg = (float) MathHelper.clamp(BARREL_BLAST_DAMAGE * t, 0.0, BARREL_BLAST_DAMAGE);
            living.damage(world, living.getDamageSources().explosion(null, source), dmg);
        }

        // Damage nearby barrels; use BFS to safely recurse.
        int minX = MathHelper.floor(center.x - radius);
        int maxX = MathHelper.floor(center.x + radius);
        int minY = MathHelper.floor(center.y - radius);
        int maxY = MathHelper.floor(center.y + radius);
        int minZ = MathHelper.floor(center.z - radius);
        int maxZ = MathHelper.floor(center.z + radius);

        ArrayDeque<BlockPos> pendingExplosions = new ArrayDeque<>();
        Set<Long> visited = new HashSet<>();

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    BlockPos p = new BlockPos(x, y, z);
                    if (!world.getBlockState(p).isOf(Blocks.TNT)) {
                        continue;
                    }
                    double dist = center.distanceTo(new Vec3d(x + 0.5, y + 0.5, z + 0.5));
                    double t = 1.0 - (dist / radius);
                    if (t <= 0.0) {
                        continue;
                    }
                    int dmg = (int) Math.floor(BARREL_BLAST_DAMAGE * t);
                    if (dmg <= 0) {
                        continue;
                    }
                    int hp = DoomBarrelRegistry.applyDamage(world, p, dmg);
                    if (hp == 0) {
                        pendingExplosions.add(p);
                    }
                }
            }
        }

        while (!pendingExplosions.isEmpty()) {
            BlockPos next = pendingExplosions.removeFirst();
            long key = next.asLong();
            if (!visited.add(key)) {
                continue;
            }
            // If it was already removed by another explosion, skip.
            if (!world.getBlockState(next).isOf(Blocks.TNT)) {
                continue;
            }
            explodeBarrel(world, source, next);
        }
    }

    private static boolean tryDamageBarrel(ServerWorld world, Entity source, BlockHitResult blockHit, int damage) {
        BlockPos pos = blockHit.getBlockPos();
        Box bounds = DoomLevelBoundsRegistry.get(world);
        if (bounds != null && !bounds.contains(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5)) {
            return false;
        }

        BlockState state = world.getBlockState(pos);
        if (!state.isOf(Blocks.TNT)) {
            return false;
        }

        int hp = DoomBarrelRegistry.applyDamage(world, pos, damage);
        if (hp > 0) {
            // Small feedback so players understand it's being damaged.
            Vec3d p = blockHit.getPos();
            world.spawnParticles(ParticleTypes.SMOKE, p.x, p.y, p.z, 2, 0.02, 0.02, 0.02, 0.005);
            return true;
        }

        explodeBarrel(world, source, pos);
        return true;
    }

    public static void firePlayerWeapon(ServerPlayerEntity player) {
        if (!player.getCommandTags().contains("doommc3d_active")) {
            return;
        }

        if (player.getMainHandStack().isOf(ModItems.DOOM_PISTOL)) {
            if (firePistol(player)) {
                ServerPlayNetworking.send(player, new WeaponFiredPayload());
            }
            return;
        }
        if (player.getMainHandStack().isOf(ModItems.DOOM_SHOTGUN)) {
            if (fireShotgun(player)) {
                ServerPlayNetworking.send(player, new WeaponFiredPayload());
            }
            return;
        }
        if (player.getMainHandStack().isOf(ModItems.DOOM_CHAINGUN)) {
            if (fireChaingun(player)) {
                ServerPlayNetworking.send(player, new WeaponFiredPayload());
            }
            return;
        }
        if (player.getMainHandStack().isOf(ModItems.DOOM_ROCKET_LAUNCHER)) {
            if (fireRocket(player)) {
                ServerPlayNetworking.send(player, new WeaponFiredPayload());
            }
            return;
        }
        if (player.getMainHandStack().isOf(ModItems.DOOM_PLASMA_RIFLE)) {
            if (firePlasma(player)) {
                ServerPlayNetworking.send(player, new WeaponFiredPayload());
            }
            return;
        }
        if (player.getMainHandStack().isOf(ModItems.DOOM_BFG)) {
            if (fireBfg(player)) {
                ServerPlayNetworking.send(player, new WeaponFiredPayload());
            }
        }
    }

    private static boolean tryConsumeAmmo(ServerPlayerEntity player, DoomAmmoType type, int amount) {
        if (!(player instanceof DoomAmmoAccess ammo)) {
            return true;
        }
        if (!ammo.consumeDoomAmmo(type, amount)) {
            // Doom "click" when attempting to fire without ammo.
            ServerPlayNetworking.send(player, new PlayDoomSfxPayload("DSCLIK", player.getX(), player.getY(), player.getZ(), 0.9f, 1.0f));
            player.sendMessage(Text.literal("No ammo"), true);
            return false;
        }
        return true;
    }

    public static void fireMonsterHitscan(ServerWorld world, LivingEntity attacker, LivingEntity target, int bullets, double range) {
        fireMonsterHitscan(world, attacker, target, bullets, range, 0.03);
    }

    public static void fireMonsterHitscan(ServerWorld world, LivingEntity attacker, LivingEntity target, int bullets, double range, double spread) {
        if (attacker == null || target == null) {
            return;
        }
        Vec3d start = attacker.getEyePos();
        Vec3d toTarget = target.getEyePos().subtract(start);
        if (toTarget.lengthSquared() < 0.0001) {
            return;
        }
        // Determine attacker accuracy (degrees) based on Doom mob type tag
        double accuracyDeg = 0.0;
        for (DoomMobType t : DoomMobType.values()) {
            if (attacker.getCommandTags().contains(DoomMobTags.tagForType(t))) {
                switch (t) {
                    case ZOMBIEMAN -> accuracyDeg = 7.5;   // ~6-8 deg
                    case SHOTGUN_GUY -> accuracyDeg = 8.5; // ~7-10 deg
                    case CHAINGUNNER -> accuracyDeg = 4.0; // ~3-5 deg
                    case IMP -> accuracyDeg = 6.0;
                    case DEMON, SPECTRE -> accuracyDeg = 5.0;
                    case LOST_SOUL -> accuracyDeg = 6.0;
                    case CACODEMON -> accuracyDeg = 5.5;
                    case BARON -> accuracyDeg = 4.5;
                }
                break;
            }
        }

        for (int i = 0; i < bullets; i++) {
            // Compute base yaw/pitch toward target
            Vec3d dirTo = toTarget.normalize();
            double dx = dirTo.x;
            double dy = dirTo.y;
            double dz = dirTo.z;
            double yawDeg = Math.toDegrees(Math.atan2(dz, dx)) - 90.0;
            double pitchDeg = -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));

            // Apply Doom-like angle jitter using P_Random()-based triangular distribution
            // This mirrors: angle += (P_Random() - P_Random()) << 20
            double yawJitter = doomSpreadYawDegrees(attacker.getRandom());
            double pitchJitter = doomSpreadYawDegrees(attacker.getRandom()) * 0.5;

            double finalYaw = yawDeg + yawJitter;
            double finalPitch = pitchDeg + pitchJitter;

            Vec3d dir = rotationVector((float) finalYaw, (float) finalPitch).normalize();

            // Distance-based accuracy scaling: farther targets suffer larger angular misses.
            double dist = toTarget.length();
            double rangeClamped = Math.max(1.0, range);
            double distanceFactor = MathHelper.clamp(1.0 - (dist / rangeClamped), 0.15, 1.0);
            // Reduce effective accuracy (increase jitter) at longer ranges
            if (distanceFactor < 1.0 && accuracyDeg > 0.0) {
                double scale = 1.0 / distanceFactor; // larger at long range
                // Reapply jitter scaled by distance
                double scaledYawJitter = yawJitter * scale;
                double scaledPitchJitter = pitchJitter * scale;
                double finalYawScaled = yawDeg + scaledYawJitter;
                double finalPitchScaled = pitchDeg + scaledPitchJitter;
                dir = rotationVector((float) finalYawScaled, (float) finalPitchScaled).normalize();
            }

            Vec3d end = start.add(dir.multiply(range));
            BlockHitResult blockHit = world.raycast(new RaycastContext(start, end, RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, attacker));
            double maxDistSq = range * range;
            if (blockHit.getType() != HitResult.Type.MISS) {
                maxDistSq = start.squaredDistanceTo(blockHit.getPos());
                end = blockHit.getPos();
            }
            Box box = attacker.getBoundingBox().stretch(dir.multiply(range)).expand(1.0);
            EntityHitResult entityHit = ProjectileUtil.raycast(attacker, start, end, box, entity -> entity != attacker && entity instanceof LivingEntity living && living.isAlive(), maxDistSq);
                if (entityHit != null && entityHit.getEntity() instanceof LivingEntity living) {
                float damage = getMonsterHitscanDamageForType(attacker.getRandom(), attacker);
                // Simple damage application (limb/gib system disabled)
                living.damage(world, attacker.getDamageSources().mobAttack(attacker), damage);
                // Visual feedback for hit
                world.spawnParticles(ParticleTypes.CRIT, entityHit.getPos().x, entityHit.getPos().y, entityHit.getPos().z, 6, 0.1, 0.1, 0.1, 0.05);
            } else if (blockHit.getType() == HitResult.Type.BLOCK) {
                int damage = (int) getDamageForMonsterHitscan(attacker.getRandom());
                if (tryDamageBarrel(world, attacker, blockHit, damage)) {
                    continue;
                }
                Vec3d p = blockHit.getPos();
                world.spawnParticles(ParticleTypes.SMOKE, p.x, p.y, p.z, 3, 0.03, 0.03, 0.03, 0.01);
            }
        }
    }
        

    public static boolean firePistol(ServerPlayerEntity player) {
        if (!player.getCommandTags().contains("doommc3d_active")) {
            return false;
        }
        if (!player.getMainHandStack().isOf(ModItems.DOOM_PISTOL)) {
            return false;
        }
        ServerWorld world = player.getEntityWorld();
        if (!tryConsumeFireWindow(world, player, NEXT_PISTOL_TICK, DOOM_PISTOL_REFIRE_TICS)) {
            return false;
        }

        if (!tryConsumeAmmo(player, DoomAmmoType.BULLET, 1)) {
            return false;
        }

        // Doom's sound system wakes monsters even without LOS.
        DoomMobSystem.alertSound(world, player.getEyePos(), 64.0);
        double range = DOOM_HITSCAN_RANGE_BLOCKS;
        Vec3d start = player.getEyePos();

        // Doom: first shot in a burst is accurate (no spread). Subsequent shots get spread.
        int refire = computeRefire(world, player.getUuid(), PISTOL_LAST_FIRE_TICK, PISTOL_REFIRE);

        float yaw = player.getYaw();
        float pitch = player.getPitch();
        float yawJitter = refire == 0 ? 0.0f : doomSpreadYawDegrees(player.getRandom());
        Vec3d dir = rotationVector(yaw + yawJitter, pitch).normalize();
        Vec3d end = start.add(dir.multiply(range));

        BlockHitResult blockHit = world.raycast(new RaycastContext(start, end, RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, player));
        double maxDistSq = range * range;
        if (blockHit.getType() != HitResult.Type.MISS) {
            maxDistSq = start.squaredDistanceTo(blockHit.getPos());
            end = blockHit.getPos();
        }

        Box box = player.getBoundingBox().stretch(dir.multiply(range)).expand(1.0);
        EntityHitResult entityHit = ProjectileUtil.raycast(player, start, end, box, entity -> entity != player && isValidTarget(entity), maxDistSq);

        // Doom SFX (loaded client-side from user-provided WAD)
        ServerPlayNetworking.send(player, new PlayDoomSfxPayload("DSPISTOL", player.getX(), player.getY(), player.getZ(), 0.9f, 1.0f));

        if (entityHit != null) {
            Entity target = entityHit.getEntity();
            if (target instanceof LivingEntity living) {
                DamageSource source = player.getDamageSources().playerAttack(player);
                living.damage(world, source, rollBulletDamage(player.getRandom()));
                world.spawnParticles(ParticleTypes.CRIT, entityHit.getPos().x, entityHit.getPos().y, entityHit.getPos().z, 8, 0.2, 0.2, 0.2, 0.1);
            }
            return true;
        }

        if (blockHit.getType() == HitResult.Type.BLOCK) {
            DoomLineTriggerSystem.tryShootLine(world, player, start, end);
            if (tryDamageBarrel(world, player, blockHit, rollBulletDamage(player.getRandom()))) {
                return true;
            }
            Vec3d p = blockHit.getPos();
            world.spawnParticles(ParticleTypes.SMOKE, p.x, p.y, p.z, 6, 0.05, 0.05, 0.05, 0.01);
        }
        return true;
    }

    private static boolean fireShotgun(ServerPlayerEntity player) {
        ServerWorld world = player.getEntityWorld();
        if (!tryConsumeFireWindow(world, player, NEXT_SHOTGUN_TICK, DOOM_SHOTGUN_REFIRE_TICS)) {
            return false;
        }

        if (!tryConsumeAmmo(player, DoomAmmoType.SHELL, 1)) {
            return false;
        }

        DoomMobSystem.alertSound(world, player.getEyePos(), 64.0);
        fireShotgunPellets(world, player);
        ServerPlayNetworking.send(player, new PlayDoomSfxPayload("DSSHOTGN", player.getX(), player.getY(), player.getZ(), 1.0f, 1.0f));
        return true;
    }

    private static boolean fireChaingun(ServerPlayerEntity player) {
        ServerWorld world = player.getEntityWorld();
        if (!tryConsumeFireWindow(world, player, NEXT_CHAINGUN_TICK, DOOM_CHAINGUN_REFIRE_TICS)) {
            return false;
        }

        if (!tryConsumeAmmo(player, DoomAmmoType.BULLET, 1)) {
            return false;
        }

        DoomMobSystem.alertSound(world, player.getEyePos(), 64.0);

        // Doom: first shot in a burst is accurate (no spread). Subsequent shots get spread.
        int refire = computeRefire(world, player.getUuid(), CHAINGUN_LAST_FIRE_TICK, CHAINGUN_REFIRE);
        float yawJitter = refire == 0 ? 0.0f : doomSpreadYawDegrees(player.getRandom());

        fireSinglePlayerHitscan(world, player, yawJitter);
        // Many IWADs use the pistol shot sound for chaingun bursts.
        ServerPlayNetworking.send(player, new PlayDoomSfxPayload("DSPISTOL", player.getX(), player.getY(), player.getZ(), 0.8f, 1.0f));
        return true;
    }

    private static void fireShotgunPellets(ServerWorld world, ServerPlayerEntity player) {
        Vec3d start = player.getEyePos();
        float yaw = player.getYaw();
        float pitch = player.getPitch();

        for (int i = 0; i < 7; i++) {
            float yawJitter = doomSpreadYawDegrees(player.getRandom());
            Vec3d dir = rotationVector(yaw + yawJitter, pitch).normalize();
            fireHitscanRay(world, player, start, dir, DOOM_HITSCAN_RANGE_BLOCKS);
        }
    }

    private static void fireSinglePlayerHitscan(ServerWorld world, ServerPlayerEntity player, float yawJitterDegrees) {
        Vec3d start = player.getEyePos();
        float yaw = player.getYaw();
        float pitch = player.getPitch();
        Vec3d dir = rotationVector(yaw + yawJitterDegrees, pitch).normalize();
        fireHitscanRay(world, player, start, dir, DOOM_HITSCAN_RANGE_BLOCKS);
    }

    private static void fireHitscanRay(ServerWorld world, ServerPlayerEntity player, Vec3d start, Vec3d dir, double range) {
        Vec3d end = start.add(dir.multiply(range));
        BlockHitResult blockHit = world.raycast(new RaycastContext(start, end, RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, player));
        double maxDistSq = range * range;
        if (blockHit.getType() != HitResult.Type.MISS) {
            maxDistSq = start.squaredDistanceTo(blockHit.getPos());
            end = blockHit.getPos();
        }

        Box box = player.getBoundingBox().stretch(dir.multiply(range)).expand(1.0);
        EntityHitResult entityHit = ProjectileUtil.raycast(player, start, end, box, entity -> entity != player && isValidTarget(entity), maxDistSq);

        if (entityHit != null && entityHit.getEntity() instanceof LivingEntity living) {
            int damage = rollBulletDamage(player.getRandom());
            living.damage(world, player.getDamageSources().playerAttack(player), damage);
            world.spawnParticles(ParticleTypes.CRIT, entityHit.getPos().x, entityHit.getPos().y, entityHit.getPos().z, 6, 0.1, 0.1, 0.1, 0.05);
            return;
        }

        if (blockHit.getType() == HitResult.Type.BLOCK) {
            DoomLineTriggerSystem.tryShootLine(world, player, start, end);
            int damage = rollBulletDamage(player.getRandom());
            if (tryDamageBarrel(world, player, blockHit, damage)) {
                return;
            }
            Vec3d p = blockHit.getPos();
            world.spawnParticles(ParticleTypes.SMOKE, p.x, p.y, p.z, 3, 0.03, 0.03, 0.03, 0.01);
        }
    }

    private static boolean fireBfg(ServerPlayerEntity player) {
        ServerWorld world = player.getEntityWorld();
        if (!tryConsumeFireWindow(world, player, NEXT_BFG_TICK, DOOM_BFG_REFIRE_TICS)) {
            return false;
        }

        if (!tryConsumeAmmo(player, DoomAmmoType.CELL, 40)) {
            return false;
        }

        DoomMobSystem.alertSound(world, player.getEyePos(), 64.0);
        Vec3d start = player.getEyePos().add(player.getRotationVec(1.0f).multiply(0.6));
        Vec3d vel = player.getRotationVec(1.0f).multiply(0.55);

        com.hitpo.doommc3d.entity.projectile.DoomBfgBallEntity bfg = new com.hitpo.doommc3d.entity.projectile.DoomBfgBallEntity(world, player);
        bfg.setPosition(start.x, start.y, start.z);
        bfg.setVelocity(vel);
        bfg.setShooterSnapshot(player.getEyePos(), player.getYaw(), player.getPitch());
        world.spawnEntity(bfg);

        ServerPlayNetworking.send(player, new PlayDoomSfxPayload("DSBFG", player.getX(), player.getY(), player.getZ(), 1.0f, 1.0f));
        return true;
    }

    private static boolean fireRocket(ServerPlayerEntity player) {
        ServerWorld world = player.getEntityWorld();
        if (!tryConsumeFireWindow(world, player, NEXT_ROCKET_TICK, DOOM_ROCKET_REFIRE_TICS)) {
            return false;
        }

        if (!tryConsumeAmmo(player, DoomAmmoType.ROCKET, 1)) {
            return false;
        }

        DoomMobSystem.alertSound(world, player.getEyePos(), 64.0);
        Vec3d dir = player.getRotationVec(1.0f).normalize();
        Vec3d start = player.getEyePos().add(dir.multiply(0.6));
        Vec3d vel = dir.multiply(doomMissileSpeedBlocksPerTick(20.0));

        com.hitpo.doommc3d.entity.projectile.DoomRocketEntity rocket = new com.hitpo.doommc3d.entity.projectile.DoomRocketEntity(world, player);
        rocket.setPosition(start.x, start.y, start.z);
        rocket.setVelocity(vel);
        world.spawnEntity(rocket);
        ServerPlayNetworking.send(player, new PlayDoomSfxPayload("DSRLAUNC", player.getX(), player.getY(), player.getZ(), 1.0f, 1.0f));
        return true;
    }

    private static boolean firePlasma(ServerPlayerEntity player) {
        ServerWorld world = player.getEntityWorld();
        if (!tryConsumeFireWindow(world, player, NEXT_PLASMA_TICK, DOOM_PLASMA_REFIRE_TICS)) {
            return false;
        }

        if (!tryConsumeAmmo(player, DoomAmmoType.CELL, 1)) {
            return false;
        }

        DoomMobSystem.alertSound(world, player.getEyePos(), 64.0);
        Vec3d dir = player.getRotationVec(1.0f).normalize();
        Vec3d start = player.getEyePos().add(dir.multiply(0.6));
        Vec3d vel = dir.multiply(doomMissileSpeedBlocksPerTick(25.0));

        com.hitpo.doommc3d.entity.projectile.DoomPlasmaEntity plasma = new com.hitpo.doommc3d.entity.projectile.DoomPlasmaEntity(world, player);
        plasma.setPosition(start.x, start.y, start.z);
        plasma.setVelocity(vel);
        world.spawnEntity(plasma);
        ServerPlayNetworking.send(player, new PlayDoomSfxPayload("DSPLASMA", player.getX(), player.getY(), player.getZ(), 0.9f, 1.0f));
        return true;
    }

    private static double doomMissileSpeedBlocksPerTick(double doomSpeedUnitsPerTic) {
        // Doom speed is in map units per tic (35 tics/sec). We scale 64 units = 1 block.
        // Minecraft runs 20 ticks/sec.
        // In one Minecraft tick (1/20s), Doom advances 35/20 tics.
        return (doomSpeedUnitsPerTic / DOOM_UNITS_PER_BLOCK) * (35.0 / 20.0);
    }

    @SuppressWarnings("unused")
    private static void firePlayerHitscan(ServerWorld world, ServerPlayerEntity player, int bullets, double range, double spread) {
        Vec3d start = player.getEyePos();
        Vec3d baseDir = player.getRotationVec(1.0f);
        for (int i = 0; i < bullets; i++) {
            Vec3d dir = baseDir.add(
                (player.getRandom().nextDouble() - 0.5) * spread,
                (player.getRandom().nextDouble() - 0.5) * spread,
                (player.getRandom().nextDouble() - 0.5) * spread
            ).normalize();

            Vec3d end = start.add(dir.multiply(range));
            BlockHitResult blockHit = world.raycast(new RaycastContext(start, end, RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, player));
            double maxDistSq = range * range;
            if (blockHit.getType() != HitResult.Type.MISS) {
                maxDistSq = start.squaredDistanceTo(blockHit.getPos());
                end = blockHit.getPos();
            }

            Box box = player.getBoundingBox().stretch(dir.multiply(range)).expand(1.0);
            EntityHitResult entityHit = ProjectileUtil.raycast(player, start, end, box, entity -> entity != player && entity instanceof LivingEntity living && living.isAlive(), maxDistSq);

            if (entityHit != null && entityHit.getEntity() instanceof LivingEntity living) {
                float damage = rollBulletDamage(player.getRandom());
                living.damage(world, player.getDamageSources().playerAttack(player), damage);
                world.spawnParticles(ParticleTypes.CRIT, entityHit.getPos().x, entityHit.getPos().y, entityHit.getPos().z, 6, 0.1, 0.1, 0.1, 0.05);
            } else if (blockHit.getType() == HitResult.Type.BLOCK) {
                DoomLineTriggerSystem.tryShootLine(world, player, start, end);
                int damage = rollBulletDamage(player.getRandom());
                if (tryDamageBarrel(world, player, blockHit, damage)) {
                    continue;
                }
                Vec3d p = blockHit.getPos();
                world.spawnParticles(ParticleTypes.SMOKE, p.x, p.y, p.z, 3, 0.03, 0.03, 0.03, 0.01);
            }
        }
    }

    private static boolean isValidTarget(Entity entity) {
        return entity instanceof LivingEntity living && living.isAlive() && entity.isAttackable();
    }

    public static void fireBfgSprayRay(ServerWorld world, Entity source, Vec3d origin, float yawDegrees, float pitchDegrees, int damage) {
        Vec3d dir = rotationVector(yawDegrees, pitchDegrees).normalize();
        double range = DOOM_BFG_SPRAY_RANGE_BLOCKS;
        Vec3d end = origin.add(dir.multiply(range));

        BlockHitResult blockHit = world.raycast(new RaycastContext(origin, end, RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, source));
        double maxDistSq = range * range;
        if (blockHit.getType() != HitResult.Type.MISS) {
            maxDistSq = origin.squaredDistanceTo(blockHit.getPos());
            end = blockHit.getPos();
        }

        Box box = new Box(origin.x, origin.y, origin.z, origin.x, origin.y, origin.z).stretch(dir.multiply(range)).expand(1.0);
        EntityHitResult entityHit = ProjectileUtil.raycast(source, origin, end, box, entity -> entity != source && isValidTarget(entity), maxDistSq);

        if (entityHit != null && entityHit.getEntity() instanceof LivingEntity living) {
            Entity owner = source;
            if (source instanceof net.minecraft.entity.projectile.ProjectileEntity projectile && projectile.getOwner() != null) {
                owner = projectile.getOwner();
            }

            DamageSource damageSource;
            if (owner instanceof ServerPlayerEntity player) {
                damageSource = player.getDamageSources().playerAttack(player);
            } else if (owner instanceof LivingEntity livingOwner) {
                damageSource = livingOwner.getDamageSources().mobAttack(livingOwner);
            } else {
                damageSource = living.getDamageSources().generic();
            }

            living.damage(world, damageSource, damage);
            world.spawnParticles(ParticleTypes.CRIT, entityHit.getPos().x, entityHit.getPos().y, entityHit.getPos().z, 6, 0.12, 0.12, 0.12, 0.06);
        }
    }
}
