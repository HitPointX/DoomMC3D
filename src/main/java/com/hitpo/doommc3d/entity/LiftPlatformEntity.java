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
    private boolean arrived = false;
    private boolean stopSfxPlayed = false;

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

        // If we've already arrived, play the stop SFX once and remain as a static
        // collider for a short window. Do NOT immediately discard here; the
        // lift system will finalize lifecycle when appropriate.
        if (!getEntityWorld().isClient() && arrived) {
            if (!stopSfxPlayed) {
                stopSfxPlayed = true;
                try {
                    ServerWorld ssw = (ServerWorld) getEntityWorld();
                    for (ServerPlayerEntity p : ssw.getPlayers()) {
                        double dx = p.getX() - this.getX();
                        double dz = p.getZ() - this.getZ();
                        if (Math.hypot(dx, dz) <= 32.0) {
                            try { ServerPlayNetworking.send(p, new PlayDoomSfxPayload("DSPSTOP", this.getX(), this.getY(), this.getZ(), 1.0f, 1.0f)); } catch (Throwable ignored) {}
                        }
                    }
                } catch (Throwable ignored) {}
            }
            // keep the platform alive and stationary while riders re-ground
            return;
        }

        double remaining = targetY - this.getY();
        double abs = Math.abs(remaining);

        double step = speedPerTick;
        if (abs < 0.5) step *= Math.max(0.25, abs / 0.5);

        double dy = Math.signum(remaining) * step;
        if (abs <= step) dy = remaining;

        // Capture riders BEFORE the platform moves (piston semantics).
        // Treat 'touching' as a rider by using epsilon comparisons and feet checks.
        Box beforeBox = this.getBoundingBox();
        Box riderQuery = beforeBox.expand(0.05, 0.75, 0.05);
        final double eps = 1e-3;
        List<Entity> riders = getEntityWorld().getOtherEntities(this, riderQuery, e -> {
            try {
                Box eb = e.getBoundingBox();

                // Require XZ overlap (strict overlap, not just touching edges)
                boolean xzOverlap = eb.maxX > beforeBox.minX && eb.minX < beforeBox.maxX
                    && eb.maxZ > beforeBox.minZ && eb.minZ < beforeBox.maxZ;
                if (!xzOverlap) return false;

                // Feet-on-top check: allow significant penetration below top to count as standing
                double feet = eb.minY;
                double top = beforeBox.maxY;
                boolean onTop = feet >= top - 0.75 && feet <= top + eps;

                return onTop;
            } catch (Throwable t) {
                return false;
            }
        });

        // Move platform (authoritative)
        try { this.setPos(this.getX(), this.getY() + dy, this.getZ()); } catch (Throwable ignored) {}
        updateBoundingBox();

        // Explicitly move captured riders by dy. For players use network teleport to avoid desync.
        for (Entity e : riders) {
            try {
                if (e instanceof ServerPlayerEntity sp) {
                    try { sp.networkHandler.requestTeleport(sp.getX(), sp.getY() + dy, sp.getZ(), sp.getYaw(), sp.getPitch()); } catch (Throwable ignored) {}
                } else {
                    try { e.setPos(e.getX(), e.getY() + dy, e.getZ()); } catch (Throwable ignored) {}
                }
            } catch (Throwable ignored) {}
            try { e.fallDistance = 0.0f; } catch (Throwable ignored) {}
            try { e.getClass().getMethod("setOnGround", boolean.class).invoke(e, true); } catch (Throwable ignored) {}

            // Ensure feet are not inside the platform slab - snap up if needed
            double platformTop = this.getBoundingBox().maxY;
            double feet = e.getBoundingBox().minY;
            if (feet < platformTop) {
                double push = (platformTop - feet) + 1e-3;
                try {
                    if (e instanceof ServerPlayerEntity sp) {
                        try { sp.networkHandler.requestTeleport(sp.getX(), sp.getY() + push, sp.getZ(), sp.getYaw(), sp.getPitch()); } catch (Throwable ignored) {}
                    } else {
                        try { e.setPos(e.getX(), e.getY() + push, e.getZ()); } catch (Throwable ignored) {}
                    }
                } catch (Throwable ignored) {}
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
                                try { ServerPlayNetworking.send(sp, new PlayDoomSfxPayload("DSPSTART", sp.getX(), sp.getY(), sp.getZ(), 1.0f, 1.0f)); } catch (Throwable ignored) {}
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

        // Recompute remaining after move and mark arrived so we remain alive a short time
        double newRemaining = targetY - this.getY();
        if (Math.abs(newRemaining) <= 1e-4) {
            // Snap to exact target position and mark arrived.
            this.refreshPositionAndAngles(this.getX(), targetY, this.getZ(), this.getYaw(), this.getPitch());
            if (!arrived) {
                arrived = true;
                stopSfxPlayed = false;
                DebugLogger.debug("LiftPlatform", () -> "arrived at target, will remain alive for a couple ticks y=" + this.getY());
            }
        }
    }

    public boolean wasBlocked() {
        return blocked;
    }

    public boolean isArrived() {
        return arrived;
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
        // Represent the platform as a thin slab around the surface top so
        // rider "feet near top" logic behaves predictably. Doom lift surface
        // at block layer Y corresponds to entity Y + 1.0.
        double surfaceY = this.getY() + 1.0;
        double halfThickness = 0.05; // thin slice (10cm)
        this.setBoundingBox(new Box(
            minX, surfaceY - halfThickness,
            minZ,
            maxX, surfaceY + halfThickness,
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
