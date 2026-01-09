package com.hitpo.doommc3d.interact;

import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;

public final class DoomCorpsePhysics {
    private DoomCorpsePhysics() {}

    public static void startSliding(ServerWorld world, DisplayEntity.ItemDisplayEntity display, Vec3d initialVelocity) {
        // Default: 60 ticks (~3 seconds) with gentle horizontal drag
        startSliding(world, display, initialVelocity, 60, 0.92);
    }

    public static void startSliding(ServerWorld world, DisplayEntity.ItemDisplayEntity display, Vec3d initialVelocity, int ticks, double drag) {
        if (world == null || display == null) return;
        final Vec3d vel0 = initialVelocity == null ? Vec3d.ZERO : initialVelocity;
        final Vec3d[] vel = new Vec3d[] { vel0 };
        final int[] remaining = new int[] { Math.max(0, ticks) };

        // Store slide state on the display entity so it persists and can be inspected.
        if (display instanceof com.hitpo.doommc3d.interact.DisplayCorpseAccess access) {
            access.setSlideVx(vel0.x);
            access.setSlideVy(vel0.y);
            access.setSlideVz(vel0.z);
            access.setSlideRemaining(remaining[0]);
            access.setSlideDrag(drag);
        }

        Runnable tick = new Runnable() {
            @Override
            public void run() {
                if (!display.isAlive() || remaining[0] <= 0) {
                    if (display instanceof com.hitpo.doommc3d.interact.DisplayCorpseAccess access) {
                        access.setSlideRemaining(0);
                    }
                    return;
                }
                // Move the display by the current velocity and notify clients
                display.setPosition(display.getX() + vel[0].x, display.getY() + vel[0].y, display.getZ() + vel[0].z);
                display.setVelocity(vel[0]);

                // Apply horizontal drag only (keep vertical subtlety)
                vel[0] = new Vec3d(vel[0].x * drag, vel[0].y * 0.9, vel[0].z * drag);
                if (display instanceof com.hitpo.doommc3d.interact.DisplayCorpseAccess access) {
                    access.setSlideVx(vel[0].x);
                    access.setSlideVy(vel[0].y);
                    access.setSlideVz(vel[0].z);
                    access.setSlideRemaining(remaining[0]);
                    access.setSlideDrag(drag);
                }

                // Stop early if nearly stationary
                if (vel[0].lengthSquared() < 0.0009) {
                    remaining[0] = 0;
                    return;
                }

                remaining[0]--;
                if (remaining[0] > 0) {
                    DoomScheduler.schedule(world, 1, this);
                }
            }
        };

        DoomScheduler.schedule(world, 1, tick);
    }
}
