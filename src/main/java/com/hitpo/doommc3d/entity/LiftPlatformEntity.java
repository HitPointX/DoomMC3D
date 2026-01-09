package com.hitpo.doommc3d.entity;

import com.hitpo.doommc3d.net.PlayDoomSfxPayload;
import com.hitpo.doommc3d.util.DebugLogger;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import java.util.List;
import net.minecraft.world.World;
import net.minecraft.entity.damage.DamageSource;

/**
 * Invisible, non-gravity platform entity used as a stable collider for lifts.
 * Moves smoothly between two Y positions and reports if it was blocked.
 */
public class LiftPlatformEntity extends Entity {
    private double minX, maxX, minZ, maxZ;
    private double targetY;
    private double speedPerTick;
    private double startY;
    private boolean blocked = false;
    private int crushTicks = 0;
    private static final float CRUSH_DAMAGE = 6.0f; // tunable

    private static final TrackedData<Float> TARGET_Y = DataTracker.registerData(LiftPlatformEntity.class, TrackedDataHandlerRegistry.FLOAT);

    // client-side smoothing value
    private double clientRenderY = Double.NaN;
    private static final int CRUSH_DELAY = 10;

    public LiftPlatformEntity(EntityType<? extends LiftPlatformEntity> type, World world) {
        super(type, world);
        this.noClip = false;
        this.setNoGravity(true);
        this.setInvisible(true);
        this.setSilent(true);
    }

    public void initBounds(
        double minX, double maxX,
        double minZ, double maxZ,
        double startY,
        double targetY,
        double speedPerTick
    ) {
        this.minX = minX;
        this.maxX = maxX;
        this.minZ = minZ;
        this.maxZ = maxZ;
        this.startY = startY;
        this.targetY = targetY;
        this.speedPerTick = speedPerTick;

        this.refreshPositionAndAngles((minX + maxX) * 0.5, startY, (minZ + maxZ) * 0.5, 0f, 0f);
        updateBoundingBox();
    }

    @Override
    public void tick() {
        super.tick();
        // Client-side smoothing: run only on client
        if (getEntityWorld().isClient()) {
            try {
                float t = this.dataTracker.get(TARGET_Y);
                if (Double.isNaN(clientRenderY)) clientRenderY = this.getY();
                clientRenderY += (t - clientRenderY) * 0.35; // smoothing factor
                this.refreshPositionAndAngles(this.getX(), clientRenderY, this.getZ(), this.getYaw(), this.getPitch());
            } catch (Throwable ignored) {}
            return;
        }

        // Server-side: sync target Y to clients
        try { this.dataTracker.set(TARGET_Y, (float) this.targetY); } catch (Throwable ignored) {}

        double remaining = targetY - this.getY();
        double abs = Math.abs(remaining);

        double step = speedPerTick;
        if (abs < 0.5) step *= Math.max(0.25, abs / 0.5);

        double dy = Math.signum(remaining) * step;
        if (abs <= step) dy = remaining;

        // Capture riders BEFORE the platform moves (piston semantics).
        // Treat 'touching' as a rider by using epsilon comparisons and feet checks.
        Box beforeBox = this.getBoundingBox();
        Box riderQuery = beforeBox.expand(0.0, 0.6, 0.0);
        final double eps = 1e-3;
        List<Entity> riders = getEntityWorld().getOtherEntities(this, riderQuery, e -> {
            try {
                Box eb = e.getBoundingBox();

                // Require XZ overlap (strict overlap, not just touching edges)
                boolean xzOverlap = eb.maxX > beforeBox.minX && eb.minX < beforeBox.maxX
                    && eb.maxZ > beforeBox.minZ && eb.minZ < beforeBox.maxZ;
                if (!xzOverlap) return false;

                // Feet-on-top check: allow slight penetration/touching to count as standing
                double feet = eb.minY;
                double top = beforeBox.maxY;
                boolean onTop = feet >= top - 0.2 && feet <= top + eps;

                return onTop || e.isOnGround();
            } catch (Throwable t) {
                return false;
            }
        });

        // Move platform (authoritative)
        try { this.setPos(this.getX(), this.getY() + dy, this.getZ()); } catch (Throwable ignored) {}
        updateBoundingBox();

        // Carry the captured riders by the same dy
        for (Entity e : riders) {
            try { e.move(MovementType.PISTON, new Vec3d(0, dy, 0)); } catch (Throwable ignored) {}
            e.fallDistance = 0.0f;

            // Ensure feet are not inside the platform slab - snap up if needed
            double platformTop = this.getBoundingBox().maxY;
            double feet = e.getBoundingBox().minY;
            if (feet < platformTop) {
                double push = (platformTop - feet) + 1e-3;
                try { e.move(MovementType.PISTON, new Vec3d(0, push, 0)); } catch (Throwable ignored) {}
            }
        }

        // Crush detection (after carrying)
        boolean crushing = false;
        for (Entity e : getEntityWorld().getOtherEntities(this, this.getBoundingBox())) {
            if (e.getBoundingBox().intersects(this.getBoundingBox())) {
                crushing = true;
                break;
            }
        }

        if (crushing) {
            crushTicks++;
            if (crushTicks >= CRUSH_DELAY) {
                // apply damage and send sfx to overlapping players
                try {
                    for (Entity e : getEntityWorld().getOtherEntities(this, this.getBoundingBox())) {
                        try {
                            if (getEntityWorld() instanceof ServerWorld sw) {
                                e.damage(sw, sw.getDamageSources().generic(), CRUSH_DAMAGE);
                            }
                        } catch (Throwable ignored) {}
                        if (e instanceof ServerPlayerEntity sp) {
                            try { ServerPlayNetworking.send(sp, new PlayDoomSfxPayload("pstart", sp.getX(), sp.getY(), sp.getZ(), 1.0f, 1.0f)); } catch (Throwable ignored) {}
                        }
                    }
                } catch (Throwable ignored) {}

                blocked = true;
                DebugLogger.debug("LiftPlatform", () -> "blocked and discarding at y=" + this.getY());
                this.discard();
                return;
            }
        } else {
            crushTicks = 0;
        }

        DebugLogger.debugThrottled("LiftPlatform", 20, () -> "y=" + getY() + " target=" + targetY);

        // Recompute remaining after move and discard when close enough
        double newRemaining = targetY - this.getY();
        if (Math.abs(newRemaining) <= 1e-4) {
            this.refreshPositionAndAngles(this.getX(), targetY, this.getZ(), this.getYaw(), this.getPitch());
            DebugLogger.debug("LiftPlatform", () -> "reached target, discarding at y=" + this.getY());
            // play arrival sound for nearby players
            try {
                ServerWorld sw = (ServerWorld) getEntityWorld();
                for (ServerPlayerEntity p : sw.getPlayers()) {
                    double dx = p.getX() - this.getX();
                    double dz = p.getZ() - this.getZ();
                    if (Math.hypot(dx, dz) <= 32.0) {
                        try { ServerPlayNetworking.send(p, new PlayDoomSfxPayload("pstop", this.getX(), this.getY(), this.getZ(), 1.0f, 1.0f)); } catch (Throwable ignored) {}
                    }
                }
            } catch (Throwable ignored) {}
            this.discard();
        }
    }

    public boolean wasBlocked() {
        return blocked;
    }

    // Some mappings expect an entity damage handler with ServerWorld/DamageSource signature.
    public boolean damage(ServerWorld world, net.minecraft.entity.damage.DamageSource source, float amount) {
        // Platforms are immune to damage; ignore and report not-damaged.
        return false;
    }

    // Fallback (non-overriding) signature kept for compatibility with other code paths.
    public boolean damage(DamageSource source, float amount) {
        return false;
    }

    private void updateBoundingBox() {
        this.setBoundingBox(new Box(
            minX, this.getY(),
            minZ,
            maxX, this.getY() + 0.51,
            maxZ
        ));
    }

    // DataTracker initialization: register tracked TARGET_Y for client smoothing
    protected void initDataTracker(DataTracker.Builder builder) {
        builder.add(TARGET_Y, 0.0f);
    }

    protected void readCustomDataFromNbt(NbtCompound nbt) {}
    protected void writeCustomDataToNbt(NbtCompound nbt) {}

    // Mappings may require ReadView/WriteView methods; implement them as no-ops to satisfy
    public void writeCustomData(net.minecraft.storage.WriteView view) {}
    public void readCustomData(net.minecraft.storage.ReadView view) {}
}
