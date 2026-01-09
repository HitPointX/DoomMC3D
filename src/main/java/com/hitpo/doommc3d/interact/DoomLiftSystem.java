package com.hitpo.doommc3d.interact;

import com.hitpo.doommc3d.convert.PaletteMapper;
import com.hitpo.doommc3d.util.DebugLogger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.MovementType;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * Doom-style plats (lifts).
 *
 * Implements classic Doom "PlatDownWaitUpStay" behavior:
 * - On activate: go down to bottom
 * - Wait
 * - Go up to top
 * - Stop idle at top
 *
 * This version is hardened for Minecraft physics:
 * - END_WORLD_TICK updates
 * - atomic step (no partial placement)
 * - riders include players
 * - carry uses MovementType.PISTON
 * - snap uses destination surface top (toY) on successful step
 * - no "skip cell" holes (all-or-nothing)
 */
public final class DoomLiftSystem {
    private static final int DEFAULT_WAIT_TICKS = 60;
    private static final int SLOW_TICKS_PER_BLOCK = 4;
    private static final int NORMAL_TICKS_PER_BLOCK = 2;
    private static final int BLAZE_TICKS_PER_BLOCK = 1;

    private static final Map<RegistryKey<World>, Map<Integer, List<Lift>>> LIFTS_BY_TAG = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> COOLDOWN_UNTIL_TICK = new HashMap<>();

    private DoomLiftSystem() {}

    public static void register() {
        // End-of-world tick reduces client-side "one tick drop" artifacts
        // Move lifts before entity physics/gravity so riders remain on the floor
        ServerTickEvents.START_WORLD_TICK.register(DoomLiftSystem::tickWorld);
    }

    public static void clear(ServerWorld world) {
        LIFTS_BY_TAG.remove(world.getRegistryKey());
    }

    public static void registerLift(ServerWorld world, int tag, Lift lift) {
        if (tag == 0) return;
        LIFTS_BY_TAG.computeIfAbsent(world.getRegistryKey(), k -> new HashMap<>())
            .computeIfAbsent(tag, k -> new ArrayList<>())
            .add(lift);
    }

    public static void activateByTag(ServerWorld world, ServerPlayerEntity activator, int tag) {
        if (tag == 0) return;

        DebugLogger.debug("DoomLiftSystem.activate", () -> "[DoomMC3D] activateByTag called: tag=" + tag);

        Map<Integer, List<Lift>> byTag = LIFTS_BY_TAG.get(world.getRegistryKey());
        if (byTag == null) {
            DebugLogger.debug("DoomLiftSystem.activate", () -> "[DoomMC3D] ERROR: No lifts registered for this world!");
            return;
        }
        List<Lift> lifts = byTag.get(tag);
        if (lifts == null || lifts.isEmpty()) {
            DebugLogger.debug("DoomLiftSystem.activate",
                () -> "[DoomMC3D] ERROR: No lift found for tag " + tag + ". Available tags: " + byTag.keySet());
            return;
        }

        // Lightweight per-player cooldown so a single press doesn't spam-activate a tag.
        long now = world.getTime();
        long until = COOLDOWN_UNTIL_TICK.getOrDefault(activator.getUuid(), 0L);
        if (now < until) {
            DebugLogger.debugThrottled("DoomLiftSystem.activate.cooldown", 1000, () -> "[DoomMC3D] Lift activation on cooldown");
            return;
        }
        COOLDOWN_UNTIL_TICK.put(activator.getUuid(), now + 10);

        for (Lift lift : lifts) {
            DebugLogger.debug("DoomLiftSystem.activate.debug", () -> "[LiftDebug] playerY=" + activator.getY()
                + " baseY=" + lift.buildOrigin.getY()
                + " currentY=" + lift.currentY
                + " topY=" + lift.topY
                + " bottomY=" + lift.bottomY);
            lift.activate(world);
        }
    }

    private static void tickWorld(ServerWorld world) {
        Map<Integer, List<Lift>> byTag = LIFTS_BY_TAG.get(world.getRegistryKey());
        if (byTag == null || byTag.isEmpty()) return;
        for (List<Lift> lifts : byTag.values()) {
            for (Lift lift : lifts) {
                lift.tick(world);
            }
        }
    }

    public enum LiftSpeed { SLOW, NORMAL, BLAZE }

    public static final class Lift {
        private static final double SEAT_EPS = 1e-3;
        private static final int ESCALATE_TICKS = 12; // 0.6s at 20 TPS

        // 3 = notify clients + neighbor updates (safer for collision immediacy)
        // If you want fewer neighbor updates later, split flags carefully. For now: correctness > micro perf.
        private static final int PLACE_FLAGS = 3;

        private final List<FloorCell> floor;
        private final List<BoundaryColumn> boundaries;
        private final List<com.hitpo.doommc3d.doommap.Vertex> sectorPolygon;
        private final int originBlockX;
        private final int originBlockZ;

        private final BlockPos buildOrigin;
        private final BlockState floorState;
        private final int ceilingY;

        private final int topY;
        private final int bottomY;
        private final int waitTicks;
        private final int ticksPerBlock;

        private int currentY;
        private LiftState state = LiftState.IDLE;
        private long waitUntilTick = 0;
        private int blockedTicks = 0;
        // Active platform entity for this lift while moving; null when idle.
        private com.hitpo.doommc3d.entity.LiftPlatformEntity activePlatform = null;
        private boolean newFloorPlaced = false;
        private int platformArrivedTicks = 0;
        // Align Doom floor 0 to the in-world walkable block. Tune if needed.
        private static final int WORLD_FLOOR_OFFSET = 1;

        public Lift(
            List<FloorCell> floor,
            List<BoundaryColumn> boundaries,
            List<com.hitpo.doommc3d.doommap.Vertex> sectorPolygon,
            int originBlockX,
            int originBlockZ,
            BlockPos buildOrigin,
            BlockState floorState,
            int ceilingY,
            int topY,
            int bottomY,
            LiftSpeed speed
        ) {
            this.floor = floor;
            this.boundaries = boundaries;
            this.sectorPolygon = sectorPolygon;
            this.originBlockX = originBlockX;
            this.originBlockZ = originBlockZ;
            this.buildOrigin = buildOrigin.toImmutable();
            this.floorState = floorState;
            this.ceilingY = ceilingY;
            this.topY = topY;
            this.bottomY = bottomY;

            this.waitTicks = DEFAULT_WAIT_TICKS;
            this.ticksPerBlock =
                speed == LiftSpeed.BLAZE ? BLAZE_TICKS_PER_BLOCK :
                speed == LiftSpeed.SLOW ? SLOW_TICKS_PER_BLOCK :
                NORMAL_TICKS_PER_BLOCK;

            this.currentY = topY; // starts at top
        }

        public void activate(ServerWorld world) {
            if (state != LiftState.IDLE) return;
            if (topY <= bottomY) return;
            state = LiftState.MOVING_DOWN;
        }

        

        public void tick(ServerWorld world) {
            // If a platform entity is active, wait for it to finish or report blocked.
            if (activePlatform != null) {
                if (activePlatform.wasBlocked()) {
                    // Platform reported blocked during motion: avoid soft-locks by settling to IDLE
                    activePlatform = null;
                    newFloorPlaced = false;
                    if (state == LiftState.MOVING_UP || state == LiftState.MOVING_DOWN) {
                        state = LiftState.IDLE;
                    }
                    return;
                }

                // If the platform has arrived at its target, place the new floor layer now
                // but do NOT clear the old floor until the platform is discarded. This
                // avoids a one-tick hole where riders might fall through.
                if (activePlatform.isArrived()) {
                    int nextY = (state == LiftState.MOVING_DOWN) ? currentY - 1 : currentY + 1;
                    if (!newFloorPlaced) {
                        placeFloorLayer(world, nextY);
                        // Snap riders onto the newly-placed floor so clients see them stable.
                        List<Entity> ridersAfter = findRiders(world, nextY);
                        snapPlayersToSurface(world, ridersAfter, nextY);
                        newFloorPlaced = true;
                    }

                    // Instead of waiting for the platform to self-discard, give it a short
                    // stability window, then explicitly discard so the Lift state machine
                    // can finalize the step (clear old floor and advance currentY).
                    platformArrivedTicks++;
                    if (platformArrivedTicks >= 6) {
                        try { activePlatform.discard(); } catch (Throwable ignored) {}
                    }
                    return;
                }

                // Platform finished and was discarded: now clear the old floor layer and advance state.
                if (!activePlatform.isAlive()) {
                    int prevY = currentY;
                    int nextY = (state == LiftState.MOVING_DOWN) ? currentY - 1 : currentY + 1;

                    // Clear old floor after new floor was already placed on arrival.
                    clearFloorLayer(world, prevY);
                    currentY = nextY;

                    if (state == LiftState.MOVING_DOWN) {
                        if (currentY <= bottomY) {
                            state = LiftState.WAITING;
                            waitUntilTick = world.getTime() + waitTicks;
                            clearInteriorAir(world, currentY);
                            updateBoundaryWalls(world);
                        }
                    } else if (state == LiftState.MOVING_UP) {
                        if (currentY >= topY) {
                            state = LiftState.IDLE;
                            clearInteriorAir(world, currentY);
                            updateBoundaryWalls(world);
                        }
                    }

                    List<Entity> ridersAfter = findRiders(world, currentY);
                    snapPlayersToSurface(world, ridersAfter, currentY);

                    activePlatform = null;
                    newFloorPlaced = false;
                    platformArrivedTicks = 0;
                }
                return;
            }

            // Auto-activate: if idle at top and a player steps onto the platform area, go down.
            if (state == LiftState.IDLE) {
                if (activePlatform == null && currentY >= topY) {
                    List<Entity> riders = findRiders(world, currentY);
                    if (!riders.isEmpty()) {
                        boolean hasPlayer = false;
                        for (Entity e : riders) {
                            if (e instanceof ServerPlayerEntity) { hasPlayer = true; break; }
                        }
                        if (hasPlayer) {
                            DebugLogger.debug("DoomLiftSystem.auto", () -> "[DoomLiftSystem] auto-activate by rider at y=" + currentY);
                            state = LiftState.MOVING_DOWN;
                            return;
                        }
                    }
                }
                return;
            }

            long now = world.getTime();
            if (state == LiftState.WAITING) {
                if (now >= waitUntilTick) state = LiftState.MOVING_UP;
                return;
            }

            if (ticksPerBlock > 1 && (now % ticksPerBlock) != 0) return;

            if (state == LiftState.MOVING_DOWN) {
                if (currentY <= bottomY) {
                    state = LiftState.WAITING;
                    waitUntilTick = now + waitTicks;
                    return;
                }
                if (step(world, currentY, currentY - 1)) {
                    if (currentY <= bottomY) {
                        state = LiftState.WAITING;
                        waitUntilTick = now + waitTicks;
                        clearInteriorAir(world, currentY);
                        updateBoundaryWalls(world);
                    }
                }
                return;
            }

            if (state == LiftState.MOVING_UP) {
                if (currentY >= topY) {
                    state = LiftState.IDLE;
                    return;
                }
                if (step(world, currentY, currentY + 1)) {
                    // actual currentY update happens when platform entity completes
                }
            }
        }

        private void finalizeAt(ServerWorld world, int y) {
            placeFloorLayer(world, y);
            clearFloorLayer(world, y - 1);
            clearInteriorAir(world, y);
            updateBoundaryWalls(world);
        }

        /**
         * Atomic single-block step. Returns true only if the full step was applied.
         */
        private boolean step(ServerWorld world, int fromY, int toY) {
            int delta = toY - fromY;
            if (delta == 0) return true;

            // Riders are determined from the CURRENT floor height (fromY) only.
            List<Entity> riders = findRiders(world, fromY);

            // If moving up/down would push riders into blocks, abort the step.
            if (!preflightRiders(world, riders, delta, fromY, toY)) {
                handleBlocked(world, riders, fromY);
                return false;
            }

            // Success path: spawn an entity platform to carry riders reliably, then finalize blocks when it completes.
            // If a platform is already active, do not start another step this tick.
            if (activePlatform != null) return false; // already running
            activePlatform = spawnLiftPlatform(world, fromY, toY);
            blockedTicks = 0;
            newFloorPlaced = false;
            platformArrivedTicks = 0;

            return true;
        }

        private void handleBlocked(ServerWorld world, List<Entity> riders, int fromY) {
            blockedTicks++;

            // Clamp riders to current surface to prevent tiny dips while blocked.
            for (Entity r : riders) {
                double targetFeet = computeSurfaceTop(world, r, fromY) + SEAT_EPS;
                if (r.getY() < targetFeet) r.setPos(r.getX(), targetFeet, r.getZ());
                r.fallDistance = 0.0f;
                Vec3d v = r.getVelocity();
                if (v.y < 0.0) r.setVelocity(v.x, 0.0, v.z);
                if (r instanceof ServerPlayerEntity sp) {
                    safeTeleport(sp, sp.getX(), targetFeet, sp.getZ());
                }
            }

            // Escalation: if we stay blocked a while, re-seat riders precisely on current surface.
            // NOTE: We do NOT move them upward. This avoids any "ratchet climb" exploit.
            if (blockedTicks >= ESCALATE_TICKS) {
                DebugLogger.debug("DoomLiftSystem.escalate",
                    () -> "[DoomLiftSystem] blocked " + blockedTicks + " ticks - re-seating riders on current surface");
                for (Entity r : riders) {
                    double targetFeet = computeSurfaceTop(world, r, fromY) + SEAT_EPS;
                    if (r instanceof ServerPlayerEntity sp) {
                        safeTeleport(sp, sp.getX(), targetFeet, sp.getZ());
                        sp.fallDistance = 0.0f;
                        Vec3d vv = sp.getVelocity();
                        if (vv.y < 0.0) sp.setVelocity(vv.x, 0.0, vv.z);
                    } else {
                        r.setPos(r.getX(), targetFeet, r.getZ());
                        r.fallDistance = 0.0f;
                        Vec3d vv = r.getVelocity();
                        if (vv.y < 0.0) r.setVelocity(vv.x, 0.0, vv.z);
                    }
                }
                blockedTicks = 0;
            }
        }

        private boolean preflightRiders(ServerWorld world, List<Entity> riders, int delta, int fromY, int toY) {
            if (riders.isEmpty()) return true;

            // 1) Entity-space check: riders' AABB at destination must be empty of solid blocks.
            for (Entity r : riders) {
                Box nextBox = r.getBoundingBox().offset(0.0, delta, 0.0);
                if (intersectsBlocks(world, nextBox)) {
                    DebugLogger.debug("DoomLiftSystem.preflight",
                        () -> "[DoomLiftSystem] preflight blocked by world collision entity=" + r.getUuid() + " nextBox=" + nextBox);
                    return false;
                }
            }

            // 2) Lift-floor check: on upward motion, ensure we will not place destination blocks inside riderNext.
            if (delta > 0) {
                double dy = delta;
                for (FloorCell cell : floor) {
                    BlockPos to = buildOrigin.add(cell.dx, toY, cell.dz);
                    Box blockBox = new Box(to.getX(), to.getY(), to.getZ(), to.getX() + 1.0, to.getY() + 1.0, to.getZ() + 1.0);
                    for (Entity r : riders) {
                        Box riderNext = r.getBoundingBox().offset(0.0, dy, 0.0);
                        if (riderNext.intersects(blockBox)) {
                            DebugLogger.debug("DoomLiftSystem.preflight",
                                () -> "[DoomLiftSystem] preflight blocked by rider overlap entity=" + r.getUuid() + " cell=" + cell.dx + "," + cell.dz);
                            return false;
                        }
                    }
                }
            }

            // 3) Ceiling headroom check (cheap)
            if (ceilingY > 0 && delta > 0) {
                for (Entity r : riders) {
                    double headY = r.getBoundingBox().maxY + delta;
                    if (headY >= ceilingY - 0.05) return false;
                }
            }

            return true;
        }

        private com.hitpo.doommc3d.entity.LiftPlatformEntity spawnLiftPlatform(ServerWorld world, int fromY, int toY) {
            // compute bounds from FloorCell list
            int minDx = Integer.MAX_VALUE, maxDx = Integer.MIN_VALUE;
            int minDz = Integer.MAX_VALUE, maxDz = Integer.MIN_VALUE;

            for (FloorCell c : floor) {
                minDx = Math.min(minDx, c.dx);
                maxDx = Math.max(maxDx, c.dx);
                minDz = Math.min(minDz, c.dz);
                maxDz = Math.max(maxDz, c.dz);
            }

            double minX = buildOrigin.getX() + minDx;
            double maxX = buildOrigin.getX() + maxDx + 1;
            double minZ = buildOrigin.getZ() + minDz;
            double maxZ = buildOrigin.getZ() + maxDz + 1;

            com.hitpo.doommc3d.entity.LiftPlatformEntity platform =
                new com.hitpo.doommc3d.entity.LiftPlatformEntity(com.hitpo.doommc3d.entity.ModEntities.LIFT_PLATFORM, world);

            double speed = 1.0 / Math.max(1, ticksPerBlock);

            double startYWorld = worldY(fromY);
            double targetYWorld = worldY(toY);

            DebugLogger.debug("DoomLiftSystem.spawn.debug", () -> "spawnLiftPlatform: state=" + state + " currentY=" + currentY + " fromY=" + fromY + " toY=" + toY + " activePlatform==null=" + (activePlatform==null));

            platform.initBounds(
                minX, maxX,
                minZ, maxZ,
                startYWorld,
                targetYWorld,
                speed
            );

            DebugLogger.debug("DoomLiftSystem.spawn", () -> "[DoomLiftSystem] Spawn platform fromY=" + fromY + " toY=" + toY + " worldStartY=" + startYWorld + " worldTargetY=" + targetYWorld);
            try {
                world.spawnEntity(platform);
            } catch (Throwable ignored) {}

            return platform;
        }

        // Convert a Doom-relative floor Y to world Y using the build origin.
        private double worldY(int relY) {
            return this.buildOrigin.getY() + WORLD_FLOOR_OFFSET + relY;
        }

        private void carryRiders(ServerWorld world, List<Entity> riders, int delta, int toY) {
            if (delta == 0 || riders.isEmpty()) return;

            for (Entity e : riders) {
                try {
                    e.move(MovementType.SHULKER, new Vec3d(0.0, delta, 0.0));
                } catch (Throwable ignored) {}

                double targetFeet = computeSurfaceTop(world, e, toY) + SEAT_EPS;
                if (e.getY() < targetFeet) {
                    e.setPos(e.getX(), targetFeet, e.getZ());
                }

                e.fallDistance = 0.0f;
                Vec3d v = e.getVelocity();
                if (v.y < 0.0) e.setVelocity(v.x, 0.0, v.z);
            }
        }

        private void snapPlayersToSurface(ServerWorld world, List<Entity> riders, int toY) {
            if (riders.isEmpty()) return;
            for (Entity e : riders) {
                if (!(e instanceof ServerPlayerEntity sp)) continue;
                double targetFeet = computeSurfaceTop(world, sp, toY) + SEAT_EPS;
                if (sp.getY() < targetFeet - 1e-3) {
                    safeTeleport(sp, sp.getX(), targetFeet, sp.getZ());
                }
                sp.fallDistance = 0.0f;
                Vec3d v = sp.getVelocity();
                if (v.y < 0.0) sp.setVelocity(v.x, 0.0, v.z);
            }
        }

        private static void safeTeleport(ServerPlayerEntity sp, double x, double y, double z) {
            try {
                sp.networkHandler.requestTeleport(x, y, z, sp.getYaw(), sp.getPitch());
            } catch (Throwable t) {
                sp.setPos(x, y, z);
            }
        }

        private void placeFloorLayer(ServerWorld world, int y) {
            for (FloorCell cell : floor) {
                BlockPos to = buildOrigin.add(cell.dx, y, cell.dz);
                world.setBlockState(to, floorState, PLACE_FLAGS);
            }
        }

        private void clearFloorLayer(ServerWorld world, int y) {
            for (FloorCell cell : floor) {
                BlockPos from = buildOrigin.add(cell.dx, y, cell.dz);
                BlockState prev = world.getBlockState(from);
                if (!prev.isAir()) {
                    world.setBlockState(from, Blocks.AIR.getDefaultState(), PLACE_FLAGS);
                }
            }
        }

        private void clearInteriorAir(ServerWorld world, int newFloorY) {
            int start = Math.min(newFloorY + 1, ceilingY - 1);
            int end = Math.max(newFloorY + 1, ceilingY - 1);
            if (end < start) return;

            for (FloorCell cell : floor) {
                for (int y = start; y <= end; y++) {
                    BlockPos p = buildOrigin.add(cell.dx, y, cell.dz);
                    if (!world.getBlockState(p).isAir()) {
                        world.setBlockState(p, Blocks.AIR.getDefaultState(), PLACE_FLAGS);
                    }
                }
            }
        }

        private void updateBoundaryWalls(ServerWorld world) {
            for (BoundaryColumn column : boundaries) {
                int neighborFloorY = column.neighborFloorY;
                int floorMin = Math.min(currentY, neighborFloorY);
                int floorMax = Math.max(currentY, neighborFloorY);

                int clearBottom = Math.min(bottomY, neighborFloorY);
                int clearTop = Math.max(topY, neighborFloorY);

                for (int y = clearBottom; y <= clearTop; y++) {
                    BlockPos p = buildOrigin.add(column.dx, y, column.dz);
                    world.setBlockState(p, Blocks.AIR.getDefaultState(), PLACE_FLAGS);
                }

                if (floorMin < floorMax) {
                    for (int y = floorMin; y <= (floorMax - 1); y++) {
                        BlockPos p = buildOrigin.add(column.dx, y, column.dz);
                        world.setBlockState(p, column.wallState, PLACE_FLAGS);
                    }
                }
            }
        }

        private List<Entity> findRiders(ServerWorld world, int fromY) {
            int minDx = Integer.MAX_VALUE, maxDx = Integer.MIN_VALUE;
            int minDz = Integer.MAX_VALUE, maxDz = Integer.MIN_VALUE;
            for (FloorCell cell : floor) {
                minDx = Math.min(minDx, cell.dx);
                maxDx = Math.max(maxDx, cell.dx);
                minDz = Math.min(minDz, cell.dz);
                maxDz = Math.max(maxDz, cell.dz);
            }

            int minX = buildOrigin.getX() + minDx;
            int maxX = buildOrigin.getX() + maxDx + 1;
            int minZ = buildOrigin.getZ() + minDz;
            int maxZ = buildOrigin.getZ() + maxDz + 1;

            double platformTop = worldY(fromY) + 1.0;

            Box box = new Box(
                minX - 0.6, worldY(fromY) - 0.6, minZ - 0.6,
                maxX + 0.6, platformTop + 1.0, maxZ + 0.6
            );

            List<Entity> entities = world.getEntitiesByClass(Entity.class, box, e -> true);

            List<Entity> riders = new ArrayList<>();
            for (Entity e : entities) {
                double feet = e.getBoundingBox().minY;
                double eps = 1e-3;
                boolean nearSurface = (feet <= platformTop + eps) && (feet >= platformTop - 0.75);
                if (!nearSurface) continue;

                double entityX = e.getX();
                double entityZ = e.getZ();
                if (entityX < minX - 0.05 || entityX > maxX + 0.05 || entityZ < minZ - 0.05 || entityZ > maxZ + 0.05) continue;

                double doomX = toDoomX((int) Math.floor(entityX));
                double doomZ = toDoomZ((int) Math.floor(entityZ));
                if (!containsPoint(sectorPolygon, doomX, doomZ)) continue;

                riders.add(e);
            }
            return riders;
        }

        private double toDoomX(int worldBlockX) {
            int relativeBlockX = worldBlockX - buildOrigin.getX();
            return (relativeBlockX + originBlockX) * (double) com.hitpo.doommc3d.DoomConstants.DOOM_TO_MC_SCALE
                + com.hitpo.doommc3d.DoomConstants.DOOM_TO_MC_SCALE / 2.0;
        }

        private double toDoomZ(int worldBlockZ) {
            int relativeBlockZ = worldBlockZ - buildOrigin.getZ();
            return (originBlockZ - relativeBlockZ) * (double) com.hitpo.doommc3d.DoomConstants.DOOM_TO_MC_SCALE
                + com.hitpo.doommc3d.DoomConstants.DOOM_TO_MC_SCALE / 2.0;
        }

        private double computeSurfaceTop(ServerWorld world, Entity e, int platformY) {
            int bx = (int) Math.floor(e.getX());
            int bz = (int) Math.floor(e.getZ());
            int worldYInt = (int) Math.floor(worldY(platformY));
            BlockPos below = new BlockPos(bx, worldYInt, bz);
            try {
                var shape = world.getBlockState(below).getCollisionShape(world, below);
                if (shape.isEmpty()) return platformY + 1.0;
                double max = shape.getMax(Direction.Axis.Y);
                return below.getY() + max;
            } catch (Throwable t) {
                return worldY(platformY) + 1.0;
            }
        }

        private boolean intersectsBlocks(ServerWorld world, Box box) {
            try {
                Iterable<net.minecraft.util.shape.VoxelShape> shapes = world.getBlockCollisions(null, box);
                if (shapes != null) {
                    for (net.minecraft.util.shape.VoxelShape s : shapes) {
                        if (s != null && !s.isEmpty()) return true;
                    }
                }
            } catch (Throwable ignored) {
                int minX = (int) Math.floor(box.minX);
                int maxX = (int) Math.floor(box.maxX);
                int minY = (int) Math.floor(box.minY);
                int maxY = (int) Math.floor(box.maxY);
                int minZ = (int) Math.floor(box.minZ);
                int maxZ = (int) Math.floor(box.maxZ);
                for (int x = minX; x <= maxX; x++) {
                    for (int y = minY; y <= maxY; y++) {
                        for (int z = minZ; z <= maxZ; z++) {
                            BlockPos p = new BlockPos(x, y, z);
                            try {
                                var s = world.getBlockState(p).getCollisionShape(world, p);
                                if (!s.isEmpty()) return true;
                            } catch (Throwable ignored2) {}
                        }
                    }
                }
            }
            return false;
        }

        private static boolean containsPoint(List<com.hitpo.doommc3d.doommap.Vertex> polygon, double x, double y) {
            boolean inside = false;
            for (int i = 0, j = polygon.size() - 1; i < polygon.size(); j = i++) {
                var vi = polygon.get(i);
                var vj = polygon.get(j);
                boolean intersect =
                    ((vi.y() > y) != (vj.y() > y))
                        && (x < (vj.x() - vi.x()) * (y - vi.y()) / (double) (vj.y() - vi.y()) + vi.x());
                if (intersect) inside = !inside;
            }
            return inside;
        }

        public static BlockState floorStateFromFlat(String flat) {
            BlockState state = PaletteMapper.mapFloor(flat);
            return state != null ? state : Blocks.DEEPSLATE_TILES.getDefaultState();
        }

        public static BlockState wallStateFromTexture(String tex) {
            BlockState state = PaletteMapper.mapWall(tex);
            return state != null ? state : Blocks.POLISHED_ANDESITE.getDefaultState();
        }
    }

    public record FloorCell(int dx, int dz) {}
    public record BoundaryColumn(int dx, int dz, int neighborFloorY, BlockState wallState) {}

    private enum LiftState {
        IDLE,
        MOVING_DOWN,
        WAITING,
        MOVING_UP
    }
}