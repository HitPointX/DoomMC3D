package com.hitpo.doommc3d.interact;

import java.util.List;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;

public final class DoomLineTriggerSystem {
    private static final double USE_RANGE_BLOCKS = 4.0;
    private static final double USE_HIT_THRESHOLD = 0.35; // how close the use-ray must pass to a line

    private DoomLineTriggerSystem() {
    }

    public static void tryUseLine(ServerWorld world, ServerPlayerEntity player) {
        Vec3d eye = player.getEyePos();
        Vec3d dir = player.getRotationVec(1.0f);
        tryUseLine(world, player, eye, dir, USE_RANGE_BLOCKS);
    }

    public static void tryUseLine(ServerWorld world, ServerPlayerEntity player, Vec3d eye, Vec3d dir, double maxDist) {
        List<DoomLineTrigger> triggers = DoomLineTriggerRegistry.getAll(world);
        DoomLineTrigger best = null;
        double bestT = Double.POSITIVE_INFINITY;

        double ox = eye.x;
        double oz = eye.z;
        double dx = dir.x;
        double dz = dir.z;
        double dLenSq = dx * dx + dz * dz;
        if (dLenSq < 1e-6) {
            return;
        }

        for (DoomLineTrigger t : triggers) {
            if (t.type() != DoomLineTrigger.Type.USE) {
                continue;
            }
            if (DoomLineTriggerRegistry.isConsumed(world, t.id())) {
                continue;
            }

            ClosestRaySegment c = closestRayToSegment2D(ox, oz, dx, dz, t.x1(), t.z1(), t.x2(), t.z2());
            if (c == null) {
                continue;
            }
            if (c.rayT < 0.0 || c.rayT > maxDist) {
                continue;
            }
            if (c.dist > USE_HIT_THRESHOLD) {
                continue;
            }
            if (c.rayT < bestT) {
                bestT = c.rayT;
                best = t;
            }
        }

        if (best != null) {
            fire(world, player, best);
        }
    }

    public static void tryShootLine(ServerWorld world, ServerPlayerEntity player, Vec3d start, Vec3d end) {
        List<DoomLineTrigger> triggers = DoomLineTriggerRegistry.getAll(world);
        double ax = start.x;
        double az = start.z;
        double bx = end.x;
        double bz = end.z;

        DoomLineTrigger best = null;
        double bestT = Double.POSITIVE_INFINITY;

        for (DoomLineTrigger t : triggers) {
            if (t.type() != DoomLineTrigger.Type.SHOOT) {
                continue;
            }
            if (DoomLineTriggerRegistry.isConsumed(world, t.id())) {
                continue;
            }

            Double hitT = segmentIntersectionParam(ax, az, bx, bz, t.x1(), t.z1(), t.x2(), t.z2());
            if (hitT == null) {
                continue;
            }
            if (hitT >= 0.0 && hitT <= 1.0 && hitT < bestT) {
                bestT = hitT;
                best = t;
            }
        }

        if (best != null) {
            fire(world, player, best);
        }
    }

    private static void fire(ServerWorld world, ServerPlayerEntity player, DoomLineTrigger trigger) {
        int now = (int) world.getTime();
        int last = DoomLineTriggerRegistry.getLastFiredTick(world, trigger.id());
        int cd = Math.max(0, trigger.cooldownTicks());
        if (cd > 0 && now - last < cd) {
            return;
        }

        DoomTriggerInteractions.executeAction(world, player, trigger.action());
        DoomLineTriggerRegistry.setLastFiredTick(world, trigger.id(), now);
        if (trigger.once()) {
            DoomLineTriggerRegistry.markConsumed(world, trigger.id());
        }
    }

    private static Double segmentIntersectionParam(
        double ax, double az, double bx, double bz,
        double cx, double cz, double dx, double dz
    ) {
        // Intersect AB with CD in 2D; return t along AB if intersects.
        double rX = bx - ax;
        double rZ = bz - az;
        double sX = dx - cx;
        double sZ = dz - cz;

        double denom = cross(rX, rZ, sX, sZ);
        if (Math.abs(denom) < 1e-9) {
            return null;
        }

        double qpx = cx - ax;
        double qpz = cz - az;
        double t = cross(qpx, qpz, sX, sZ) / denom;
        double u = cross(qpx, qpz, rX, rZ) / denom;
        if (t >= 0.0 && t <= 1.0 && u >= 0.0 && u <= 1.0) {
            return t;
        }
        return null;
    }

    private static double cross(double ax, double az, double bx, double bz) {
        return ax * bz - az * bx;
    }

    private record ClosestRaySegment(double rayT, double dist) {
    }

    private static ClosestRaySegment closestRayToSegment2D(
        double ox, double oz, double dx, double dz,
        double x1, double z1, double x2, double z2
    ) {
        // Solve closest points between a ray (O + tD, t>=0) and a segment (P1 + u(P2-P1), u in [0,1]).
        double sx = x2 - x1;
        double sz = z2 - z1;

        double a = dx * dx + dz * dz;
        double e = sx * sx + sz * sz;
        if (a < 1e-9 || e < 1e-9) {
            return null;
        }

        double rx = ox - x1;
        double rz = oz - z1;

        double b = dx * sx + dz * sz;
        double c = dx * rx + dz * rz;
        double f = sx * rx + sz * rz;

        double denom = a * e - b * b;

        double t;
        double u;

        if (Math.abs(denom) > 1e-9) {
            t = (b * f - c * e) / denom;
            u = (a * f - b * c) / denom;
        } else {
            // Nearly parallel: pick u=clamp and t from projection.
            u = clamp01(f / e);
            double px = x1 + sx * u;
            double pz = z1 + sz * u;
            t = ((px - ox) * dx + (pz - oz) * dz) / a;
        }

        if (t < 0.0) {
            t = 0.0;
        }
        u = clamp01(u);

        double closestRayX = ox + dx * t;
        double closestRayZ = oz + dz * t;
        double closestSegX = x1 + sx * u;
        double closestSegZ = z1 + sz * u;

        double dist = Math.hypot(closestRayX - closestSegX, closestRayZ - closestSegZ);
        return new ClosestRaySegment(t, dist);
    }

    private static double clamp01(double v) {
        if (v < 0.0) {
            return 0.0;
        }
        if (v > 1.0) {
            return 1.0;
        }
        return v;
    }
}
