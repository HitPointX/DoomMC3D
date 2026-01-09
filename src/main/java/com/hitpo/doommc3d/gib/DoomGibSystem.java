package com.hitpo.doommc3d.gib;

/**
 * Limb/gib system disabled — stubbed to no-op to simplify death behavior.
 * Kept as a placeholder to avoid refactor churn elsewhere.
 */
public final class DoomGibSystem {
    private DoomGibSystem() {}

    public static void initLimbHP(net.minecraft.entity.mob.MobEntity mob, com.hitpo.doommc3d.doomai.DoomMobType type) {
        // No-op: limb system disabled.
    }

    public static void applyLimbDamage(net.minecraft.server.world.ServerWorld world, net.minecraft.entity.LivingEntity target, net.minecraft.util.math.Vec3d hitPos, float damage, net.minecraft.entity.Entity attacker) {
        // No-op: limb system disabled.
    }
}
