package com.hitpo.doommc3d.doomai;

import com.hitpo.doommc3d.net.PlayDoomSfxPayload;
import com.hitpo.doommc3d.sound.ModSounds;
import com.hitpo.doommc3d.worldgen.DoomHitscan;
import com.hitpo.doommc3d.worldgen.DoomMobDrops;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.projectile.SmallFireballEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.hit.HitResult;
import net.minecraft.world.RaycastContext;

public final class DoomMobBrain {
    private static final double DOOM_TICS_PER_MC_TICK = 35.0 / 20.0;

    // Wake-up model (classic Doom): monsters start asleep. They become active when they
    // see the player, or when they hear a sound event (weapon fire/explosions). Doom's
    // sound propagation is sector-based (see Chocolate Doom's sound code), but we start
    // with a simple radius alert from player weapons and explosions.
    private boolean awake = false;
    private int wakeSightCheckTics = 0;

    private final DoomMobType type;
    private double doomTicAccumulator = 0.0;

    private int reactionTics = 0;
    // Base reaction tics used to compute randomized sight-delay when waking
    private int baseReactionTics = 0;
    private int attackCooldownTics = 0;
    private int jumpCooldownTics = 0;
    private int painTics = 0;  // Cooldown after pain reaction
    private float lastHealthCheck = -1.0f;  // Track health to detect damage
    private int projectileWindupTics = 0;  // Windup before firing projectile
    private int roamTics = 0;  // Doom-style roaming between attacks
    private int sightSoundCooldown = 0;  // Cooldown for seeing player sound

    public DoomMobBrain(DoomMobType type) {
        this.type = type;
    }

    public void alertBySound() {
        awake = true;
    }

    /**
     * Get the pain chance for this monster type.
     * Doom pain chance is typically: P_Random() < pain_chance_percent
     * Higher % = takes pain more often
     */
    private int getPainChance() {
        return switch (type) {
            // Values mirrored from Chocolate Doom's `mobjinfo` painchance entries
            case ZOMBIEMAN -> 200;      // MT_POSSESSED
            case SHOTGUN_GUY -> 170;    // MT_SHOTGUY
            case CHAINGUNNER -> 170;    // MT_CHAINGUY
            case IMP -> 200;            // MT_TROOP (imp-like)
            case DEMON -> 80;           // MT_FATSO / pinky-family approximate
            case SPECTRE -> 50;         // spectre is stealthy/tougher to pain
            case LOST_SOUL -> 256;      // MT_SKULL / lost soul - very high pain response
            case CACODEMON -> 128;      // MT_HEAD (cacodemon)
            case BARON -> 50;           // MT_BRUISER (baron)
        };
    }

    private int getAttackChance() {
        return switch (type) {
            case ZOMBIEMAN -> 200;
            case SHOTGUN_GUY -> 210;
            case CHAINGUNNER -> 190;
            case IMP -> 180;
            case DEMON -> 210;
            case SPECTRE -> 210;
            case LOST_SOUL -> 190;
            case CACODEMON -> 200;
            case BARON -> 170;
        };
    }

    /**
     * Check if the mob should react to pain this tick.
     * Doom uses: if (P_Random() < painchance) -> P_SetMobjState(actor, actor->info->painstate)
     */
    private boolean shouldReactToPain(net.minecraft.util.math.random.Random random) {
        // Doom RNG: P_Random() returns 0-255
        // We approximate: painChance out of 256
        int painChance = getPainChance();
        return random.nextInt(256) < painChance;
    }

    public void applyTuning(MobEntity mob) {
        mob.setPersistent();

        // With vanilla AI disabled, mobs won't pathfind/jump. Give them Doom-ish mobility
        // so they don't get stuck on common 1-block steps in generated Doom geometry.
        setAttr(mob, EntityAttributes.STEP_HEIGHT, 1.0);

        switch (type) {
            case ZOMBIEMAN -> {
                setAttr(mob, EntityAttributes.SCALE, 1.1);
                setAttr(mob, EntityAttributes.MOVEMENT_SPEED, 0.20);
                setAttr(mob, EntityAttributes.MAX_HEALTH, 20.0);
                reactionTics = 0;
                baseReactionTics = 8;
            }
            case SHOTGUN_GUY -> {
                setAttr(mob, EntityAttributes.SCALE, 1.15);
                setAttr(mob, EntityAttributes.MOVEMENT_SPEED, 0.22);
                setAttr(mob, EntityAttributes.MAX_HEALTH, 30.0);
                reactionTics = 0;
                baseReactionTics = 8;
            }
            case CHAINGUNNER -> {
                setAttr(mob, EntityAttributes.SCALE, 1.15);
                setAttr(mob, EntityAttributes.MOVEMENT_SPEED, 0.24);
                setAttr(mob, EntityAttributes.MAX_HEALTH, 35.0);
                reactionTics = 0;
                baseReactionTics = 8;
            }
            case IMP -> {
                setAttr(mob, EntityAttributes.SCALE, 1.25);
                setAttr(mob, EntityAttributes.MOVEMENT_SPEED, 0.23);
                setAttr(mob, EntityAttributes.MAX_HEALTH, 60.0);
                reactionTics = 0;
                baseReactionTics = 8;
                mob.setNoGravity(true);
            }
            case DEMON -> {
                setAttr(mob, EntityAttributes.SCALE, 1.35);
                setAttr(mob, EntityAttributes.MOVEMENT_SPEED, 0.28);
                setAttr(mob, EntityAttributes.MAX_HEALTH, 120.0);
                reactionTics = 0;
                baseReactionTics = 8;
            }
            case SPECTRE -> {
                setAttr(mob, EntityAttributes.SCALE, 1.35);
                setAttr(mob, EntityAttributes.MOVEMENT_SPEED, 0.29);
                setAttr(mob, EntityAttributes.MAX_HEALTH, 120.0);
                reactionTics = 0;
                baseReactionTics = 8;
                // Vanilla can't do Doom's partial transparency without a custom renderer.
                // For now: full invisibility + subtle particle shimmer so it's still "readable".
                mob.addStatusEffect(new StatusEffectInstance(StatusEffects.INVISIBILITY, Integer.MAX_VALUE, 0, false, false));
            }
            case LOST_SOUL -> {
                setAttr(mob, EntityAttributes.SCALE, 0.9);
                setAttr(mob, EntityAttributes.MOVEMENT_SPEED, 0.32);
                setAttr(mob, EntityAttributes.FLYING_SPEED, 0.55);
                setAttr(mob, EntityAttributes.MAX_HEALTH, 30.0);
                reactionTics = 0;
                baseReactionTics = 8;
                mob.setNoGravity(true);
                mob.addStatusEffect(new StatusEffectInstance(StatusEffects.FIRE_RESISTANCE, Integer.MAX_VALUE, 0, false, false));
            }
            case CACODEMON -> {
                setAttr(mob, EntityAttributes.SCALE, 2.0);
                setAttr(mob, EntityAttributes.MOVEMENT_SPEED, 0.18);
                setAttr(mob, EntityAttributes.FLYING_SPEED, 0.08);
                setAttr(mob, EntityAttributes.MAX_HEALTH, 200.0);
                reactionTics = 0;
                baseReactionTics = 8;
                mob.setNoGravity(true);
            }
            case BARON -> {
                setAttr(mob, EntityAttributes.SCALE, 1.7);
                setAttr(mob, EntityAttributes.MOVEMENT_SPEED, 0.20);
                setAttr(mob, EntityAttributes.MAX_HEALTH, 300.0);
                reactionTics = 0;
                baseReactionTics = 8;
            }
        }

        mob.setHealth(mob.getMaxHealth());
    }

    public void tick(ServerWorld world, MobEntity mob) {
        // CRITICAL: Death check FIRST before ANY other logic
        // If the mob reached zero health but for some reason the die() mixin didn't
        // convert it to a static corpse, perform a safe fallback replacement here.
        if (mob.getHealth() <= 0.0f || mob.isDead() || mob.isRemoved() || mob.deathTime > 0) {
            // If it's a Doom mob and no corpse display exists nearby, spawn one and
            // remove the living entity to avoid continued AI/physics jitter.
            if (DoomMobDrops.isDoomMob(mob) && mob.getEntityWorld() instanceof ServerWorld sw) {
                var box = mob.getBoundingBox().expand(0.6, 0.6, 0.6);
                boolean hasCorpseNearby = !sw.getEntitiesByType(
                    net.minecraft.entity.EntityType.ITEM_DISPLAY,
                    box,
                    e -> e.getCommandTags().contains(com.hitpo.doommc3d.worldgen.DoomThingSpawner.TAG_SPAWNED) || e.getCommandTags().contains("doommc3d_corpse")
                ).isEmpty();

                if (!hasCorpseNearby) {
                    com.hitpo.doommc3d.worldgen.DoomMobDrops.spawnCorpseDisplay(sw, mob);
                }

                // Ensure the living mob is removed so it can't keep animating or be
                // subject to server-client corrections that cause jitter.
                mob.remove(net.minecraft.entity.Entity.RemovalReason.KILLED);
            }

            // Dead/dying mobs do absolutely nothing - stop all movement immediately
            mob.setVelocity(0, 0, 0);
            mob.setAiDisabled(true);
            // Decay timers only
            if (painTics > 0) painTics--;
            if (roamTics > 0) roamTics--;
            if (sightSoundCooldown > 0) sightSoundCooldown--;
            if (attackCooldownTics > 0) attackCooldownTics--;
            if (projectileWindupTics > 0) projectileWindupTics--;
            return;
        }

        if (type == DoomMobType.SPECTRE && mob.isAlive()) {
            // Tiny shimmer trail so the player can track it without being unfair.
            if (world.getTime() % 2 == 0) {
                world.spawnParticles(ParticleTypes.ASH, mob.getX(), mob.getBodyY(0.5), mob.getZ(), 2, 0.2, 0.2, 0.2, 0.0);
            }
        }
        if (type == DoomMobType.LOST_SOUL && mob.isAlive()) {
            if (world.getTime() % 2 == 0) {
                world.spawnParticles(ParticleTypes.FLAME, mob.getX(), mob.getBodyY(0.55), mob.getZ(), 2, 0.15, 0.15, 0.15, 0.0);
                world.spawnParticles(ParticleTypes.SMOKE, mob.getX(), mob.getBodyY(0.55), mob.getZ(), 1, 0.12, 0.12, 0.12, 0.0);
            }
        }

        // Check if mob took damage and should react with pain
        checkDamageAndPain(mob);

        doomTicAccumulator += DOOM_TICS_PER_MC_TICK;
        while (doomTicAccumulator >= 1.0) {
            doomTicAccumulator -= 1.0;
            stepDoomTic(world, mob);
        }
    }

    private void checkDamageAndPain(MobEntity mob) {
        if (!mob.isAlive() || painTics > 0) {
            return;
        }

        float currentHealth = mob.getHealth();
        if (lastHealthCheck < 0) {
            // First tick
            lastHealthCheck = currentHealth;
            return;
        }

        if (currentHealth < lastHealthCheck) {
            // Took damage!
            float damageAmount = lastHealthCheck - currentHealth;
            if (shouldReactToPain(mob.getRandom())) {
                // Trigger pain reaction
                triggerPainReaction(mob, damageAmount);
                // Pain cooldown: prevents spammy pain reactions
                painTics = 16;  // ~0.8 seconds
            }
        }

        lastHealthCheck = currentHealth;
    }

    private void triggerPainReaction(MobEntity mob, float damageAmount) {
        // Doom pain reaction:
        // 1. Interrupt current action (handled via reactionTics)
        // 2. Play pain sound
        // 3. Cancel current attack
        
        reactionTics = 8;  // Brief stun: prevents attacking immediately
        attackCooldownTics = Math.max(attackCooldownTics, 12);  // Reset attack timer
        projectileWindupTics = 0;  // Cancel any windup
        roamTics = 0;  // Cancel roaming
        
        // Play Doom pain sound directly from WAD (DMX format)
        String lumpName = switch (type) {
            case ZOMBIEMAN, SHOTGUN_GUY, CHAINGUNNER -> "DSPOPAIN";  // Former human pain
            case IMP -> "DSPOPAIN";  // Imp uses same as humans
            case DEMON, SPECTRE -> "DSDMPAIN";  // Demon pain
            case LOST_SOUL -> "DSDMPAIN";  // Lost soul uses demon sounds
            case CACODEMON -> "DSDMPAIN";  // Cacodemon pain
            case BARON -> "DSDMPAIN";  // Baron pain
        };

        ServerWorld world = (ServerWorld) mob.getEntityWorld();
        playDoomSound(world, new Vec3d(mob.getX(), mob.getY(), mob.getZ()), lumpName, 1.0f, 1.0f);
    }

    private void playSightSound(ServerWorld world, MobEntity mob) {
        // Play Doom sight/alert sound directly from WAD (DMX format)
        String lumpName = switch (type) {
            case ZOMBIEMAN -> "DSPOSIT1";  // Zombieman sight
            case SHOTGUN_GUY -> "DSPOSIT2";  // Shotgun guy sight
            case CHAINGUNNER -> "DSPOSIT2";  // Chaingunner uses shotgun guy
            case IMP -> "DSBGSIT1";  // Imp sight
            case DEMON, SPECTRE -> "DSSGTSIT";  // Demon sight
            case LOST_SOUL -> "DSDMACT";  // Lost soul active
            case CACODEMON -> "DSCACSIT";  // Cacodemon sight
            case BARON -> "DSBRSSIT";  // Baron sight
        };

        playDoomSound(world, new Vec3d(mob.getX(), mob.getY(), mob.getZ()), lumpName, 1.2f, 1.0f);
    }

    private void stepDoomTic(ServerWorld world, MobEntity mob) {
        // CRITICAL: Check if mob is dead or has 0 health
        if (!mob.isAlive() || mob.getHealth() <= 0.0f || mob.isDead()) {
            // Dead mobs: don't act, but decay pain timer
            if (painTics > 0) {
                painTics--;
            }
            return;
        }

        // Pain cooldown tick down
        if (painTics > 0) {
            painTics--;
        }

        if (reactionTics > 0) {
            reactionTics--;
            return;
        }
        if (attackCooldownTics > 0) {
            attackCooldownTics--;
        }
        if (jumpCooldownTics > 0) {
            jumpCooldownTics--;
        }
        if (roamTics > 0) {
            roamTics--;
        }
        if (sightSoundCooldown > 0) {
            sightSoundCooldown--;
        }

        // Wake gating: until awake, monsters do not choose targets.
        // Doom's "ambush" flag (aka "deaf") means: ignore sound wake-ups.
        if (!awake) {
            if (wakeSightCheckTics > 0) {
                wakeSightCheckTics--;
            }

            if (wakeSightCheckTics == 0) {
                if (tryWakeBySight(world, mob)) {
                    awake = true;
                    // Vanilla Doom: set a fixed reaction delay on first sight
                    reactionTics = 8;
                    // Play sight sound when first seeing player (Doom behavior)
                    if (sightSoundCooldown == 0) {
                        playSightSound(world, mob);
                        sightSoundCooldown = 100;  // Don't spam sight sounds
                    }
                }
                // Re-check sight periodically to avoid expensive LOS checks every tic.
                wakeSightCheckTics = 8;
            }

            if (!awake) {
                mob.setVelocity(0, mob.getVelocity().y, 0);
                return;
            }
        }

        ServerPlayerEntity target = findTarget(world, mob);
        if (target == null) {
            mob.setVelocity(0, mob.getVelocity().y, 0);
            return;
        }
        mob.setTarget(target);
        faceTarget(mob, target);

        double distSq = mob.squaredDistanceTo(target);
        boolean hasLos = hasLineOfSight(world, mob, target);

        // Simple chase movement (Doom-ish: only steer toward target).
        if (type == DoomMobType.LOST_SOUL) {
            // Face-rush charger: accelerate in 3D and ram.
            Vec3d desired = target.getEyePos();
            Vec3d delta = desired.subtract(mob.getEntityPos());
            if (delta.lengthSquared() > 0.0001) {
                double speed = 0.55;
                Vec3d vel = delta.normalize().multiply(speed);
                // Keep a bit of vertical control so it doesn't slam into ceilings forever.
                vel = new Vec3d(vel.x, Math.max(-0.45, Math.min(0.45, vel.y)), vel.z);
                mob.setVelocity(vel);
            }
        } else if (type == DoomMobType.CACODEMON) {
            // Floaty approach: hover slightly above the player's eye height.
            Vec3d desired = target.getEyePos().add(0, 1.0, 0);
            Vec3d delta = desired.subtract(mob.getEntityPos());
            if (distSq > 3.0 * 3.0 && delta.lengthSquared() > 0.0001) {
                double speed = Math.min(0.18, mob.getAttributeValue(EntityAttributes.MOVEMENT_SPEED));
                Vec3d vel = delta.normalize().multiply(speed);
                // Keep vertical speed tame so it "drifts" instead of rockets upward.
                vel = new Vec3d(vel.x, Math.max(-0.08, Math.min(0.08, vel.y)), vel.z);
                mob.setVelocity(vel);
            } else {
                mob.setVelocity(mob.getVelocity().multiply(0.4, 0.4, 0.4));
            }
        } else {
            if (distSq > 3.5 * 3.5) {
                Vec3d to = target.getEntityPos().subtract(mob.getEntityPos());
                Vec3d vel = new Vec3d(to.x, 0, to.z);
                if (vel.lengthSquared() > 0.0001) {
                    double speed = mob.getAttributeValue(EntityAttributes.MOVEMENT_SPEED);
                    vel = vel.normalize().multiply(Math.min(0.20, speed));
                    mob.setVelocity(vel.x, mob.getVelocity().y, vel.z);
                }
            } else {
                mob.setVelocity(0, mob.getVelocity().y, 0);
            }

            // Basic "unstick" hop: helps Doom mobs clear lips/door thresholds when their
            // vanilla navigation/jump logic is disabled.
            if (jumpCooldownTics == 0 && mob.isOnGround() && mob.horizontalCollision) {
                mob.setVelocity(mob.getVelocity().x, 0.42, mob.getVelocity().z);
                jumpCooldownTics = 12;
            }
        }

        if (!hasLos || attackCooldownTics > 0) {
            return;
        }

        // Enforce reaction delay: if still in reaction tics after waking, do not attack yet.
        if (reactionTics > 0) {
            return;
        }

        // Vanilla: attack decision is RNG-gated per tick. Each enemy uses
        // if (P_Random() < attackThreshold) then attack.

        // Vanilla Doom behavior: after cooldown expires, monsters don't fire immediately
        // They enter a "roam" state for a bit, then attack
        // This prevents continuous spam and feels more natural
        
        switch (type) {
            case ZOMBIEMAN -> {
                if (roamTics > 0) {
                    return;  // Still roaming, don't fire yet
                }
                if (mob.getRandom().nextInt(256) >= getAttackChance()) {
                    return; // RNG gate: do not attack this tick
                }
                // Fire hitscan (1 pellet pistol)
                DoomHitscan.fireMonsterHitscan(world, mob, target, 1, 48.0, 0.025);
                playDoomSound(world, new Vec3d(mob.getX(), mob.getY(), mob.getZ()), "DSPISTOL", 1.2f, 1.0f);
                attackCooldownTics = 40;  // ~2 seconds before next shot consideration
                roamTics = 25;  // Roam for ~1.25 seconds after cooldown expires
            }
            case SHOTGUN_GUY -> {
                if (roamTics > 0) {
                    return;
                }
                if (mob.getRandom().nextInt(256) >= getAttackChance()) {
                    return;
                }
                // Classic shotgun guy: 7 pellets, wide spread.
                DoomHitscan.fireMonsterHitscan(world, mob, target, 7, 48.0, 0.08);
                playDoomSound(world, new Vec3d(mob.getX(), mob.getY(), mob.getZ()), "DSSHTGN", 1.0f, 1.0f);
                attackCooldownTics = 50;  // Slightly slower than zombieman
                roamTics = 30;  // Roam a bit longer
            }
            case CHAINGUNNER -> {
                if (roamTics > 0) {
                    return;
                }
                if (mob.getRandom().nextInt(256) >= getAttackChance()) {
                    return;
                }
                // Chaingunner: bursts of 2 shots, then pauses
                DoomHitscan.fireMonsterHitscan(world, mob, target, 2, 48.0, 0.035);
                playDoomSound(world, new Vec3d(mob.getX(), mob.getY(), mob.getZ()), "DSPISTOL", 1.0f, 1.5f);
                attackCooldownTics = 8;  // Quick burst
                roamTics = 4;  // Brief pause between shots in burst
            }
            case IMP -> {
                // Doom Imp: 20 tics windup, then fires
                if (mob.getRandom().nextInt(256) >= getAttackChance()) {
                    return;
                }
                if (projectileWindupTics == 0) {
                    projectileWindupTics = 20;
                    attackCooldownTics = 60;  // Next attack in 60 tics
                } else if (projectileWindupTics == 1) {
                    // Fire on last windup tic
                    fireFireball(world, mob, target, 0.55);
                    playDoomSound(world, new Vec3d(mob.getX(), mob.getY(), mob.getZ()), "DSFIRSHT", 1.0f, 1.0f);
                }
                projectileWindupTics--;
                if (projectileWindupTics < 0) {
                    projectileWindupTics = 0;
                }
            }
            case DEMON -> {
                if (distSq <= 2.2 * 2.2) {
                    if (mob.getRandom().nextInt(256) >= getAttackChance()) {
                        return;
                    }
                    // Doom Demon melee: 10 damage
                    target.damage(world, mob.getDamageSources().mobAttack(mob), 10.0f);
                    playDoomSound(world, new Vec3d(mob.getX(), mob.getY(), mob.getZ()), "DSSGTATK", 0.8f, 1.0f);
                    attackCooldownTics = 20;
                }
            }
            case SPECTRE -> {
                if (distSq <= 2.2 * 2.2) {
                    if (mob.getRandom().nextInt(256) >= getAttackChance()) {
                        return;
                    }
                    // Spectre = Demon twin, same attack
                    target.damage(world, mob.getDamageSources().mobAttack(mob), 10.0f);
                    playDoomSound(world, new Vec3d(mob.getX(), mob.getY(), mob.getZ()), "DSSGTATK", 0.8f, 1.2f);
                    attackCooldownTics = 20;
                }
            }
            case LOST_SOUL -> {
                if (distSq <= 1.25 * 1.25) {
                    if (mob.getRandom().nextInt(256) >= getAttackChance()) {
                        return;
                    }
                    // Doom Lost Soul melee: 3d8 (3-24)
                    int damage = (mob.getRandom().nextInt(8) + 1) + (mob.getRandom().nextInt(8) + 1) + (mob.getRandom().nextInt(8) + 1);
                    target.damage(world, mob.getDamageSources().mobAttack(mob), damage);
                    playDoomSound(world, new Vec3d(mob.getX(), mob.getY(), mob.getZ()), "DSSKLATK", 0.9f, 1.4f);
                    // Bounce off after the hit like Doom's skulls.
                    Vec3d away = mob.getEntityPos().subtract(target.getEyePos());
                    if (away.lengthSquared() > 0.0001) {
                        mob.setVelocity(away.normalize().multiply(0.55));
                    }
                    attackCooldownTics = 18;
                }
            }
            case CACODEMON -> {
                if (distSq <= 2.5 * 2.5) {
                    // Doom Caco melee: 20 damage
                    target.damage(world, mob.getDamageSources().mobAttack(mob), 20.0f);
                    playDoomSound(world, new Vec3d(mob.getX(), mob.getY(), mob.getZ()), "DSDMPAIN", 0.7f, 0.8f);
                    attackCooldownTics = 25;
                    projectileWindupTics = 0;
                } else {
                    // Projectile attack with windup: 30 tics
                    if (projectileWindupTics == 0) {
                        projectileWindupTics = 30;
                        attackCooldownTics = 70;
                    } else if (projectileWindupTics == 1) {
                        fireFireball(world, mob, target, 0.45);
                        playDoomSound(world, new Vec3d(mob.getX(), mob.getY(), mob.getZ()), "DSFIRSHT", 0.8f, 0.7f);
                    }
                    projectileWindupTics--;
                    if (projectileWindupTics < 0) {
                        projectileWindupTics = 0;
                    }
                }
            }
            case BARON -> {
                // Baron: 40 tics windup before firing
                if (projectileWindupTics == 0) {
                    projectileWindupTics = 40;
                    attackCooldownTics = 90;
                } else if (projectileWindupTics == 1) {
                    fireFireball(world, mob, target, 0.5);
                    playDoomSound(world, new Vec3d(mob.getX(), mob.getY(), mob.getZ()), "DSFIRSHT", 0.7f, 0.8f);
                }
                projectileWindupTics--;
                if (projectileWindupTics < 0) {
                    projectileWindupTics = 0;
                }
            }
        }
    }

    private static void fireFireball(ServerWorld world, MobEntity mob, LivingEntity target, double speed) {
        Vec3d start = mob.getEyePos().add(mob.getRotationVec(1.0f).multiply(0.6));
        Vec3d aim = target.getEyePos().subtract(start).normalize().multiply(speed);
        SmallFireballEntity fireball = new SmallFireballEntity(world, mob, aim);
        fireball.setPosition(start.x, start.y, start.z);
        world.spawnEntity(fireball);
    }

    private static ServerPlayerEntity findTarget(ServerWorld world, MobEntity mob) {
        var p = world.getClosestPlayer(mob, 64.0);
        return p instanceof ServerPlayerEntity sp ? sp : null;
    }

    private static boolean tryWakeBySight(ServerWorld world, MobEntity mob) {
        // Doom's wake-by-sight is fundamentally LOS-based.
        // We keep it cheap: check the closest player within our target range.
        var p = world.getClosestPlayer(mob, 64.0);
        if (!(p instanceof ServerPlayerEntity sp)) {
            return false;
        }
        return hasLineOfSight(world, mob, sp);
    }

    private static boolean hasLineOfSight(ServerWorld world, LivingEntity from, Entity to) {
        Vec3d start = from.getEyePos();
        Vec3d end = to.getEyePos();
        HitResult hit = world.raycast(new RaycastContext(
            start,
            end,
            RaycastContext.ShapeType.COLLIDER,
            RaycastContext.FluidHandling.NONE,
            from
        ));
        return hit.getType() == HitResult.Type.MISS;
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

    /**
     * Play a Doom sound effect from WAD by sending packet to nearby clients.
     * Uses OpenAL to play DMX sounds directly from WAD (same as music system).
     */
    private static void playDoomSound(ServerWorld world, Vec3d position, String lumpName, float volume, float pitch) {
        PlayDoomSfxPayload payload = new PlayDoomSfxPayload(
            lumpName,
            position.x,
            position.y,
            position.z,
            volume,
            pitch
        );
        
        // Send to all players in range (32 blocks)
        for (ServerPlayerEntity player : world.getPlayers()) {
            if (new Vec3d(player.getX(), player.getY(), player.getZ()).distanceTo(position) < 32.0) {
                ServerPlayNetworking.send(player, payload);
            }
        }
    }

    private static void setAttr(MobEntity mob, net.minecraft.registry.entry.RegistryEntry<net.minecraft.entity.attribute.EntityAttribute> attr, double value) {
        EntityAttributeInstance inst = mob.getAttributeInstance(attr);
        if (inst != null) {
            inst.setBaseValue(value);
        }
    }
}
