package com.hitpo.doommc3d.interact;

import com.hitpo.doommc3d.convert.PaletteMapper;
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
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * Doom-style plats (lifts).
 *
 * Currently implements the classic "PlatDownWaitUpStay" behavior used by Doom lifts.
 */
public final class DoomLiftSystem {
    private static final int DEFAULT_WAIT_TICKS = 60;
    private static final int SLOW_TICKS_PER_BLOCK = 4;
    private static final int NORMAL_TICKS_PER_BLOCK = 2;
    private static final int BLAZE_TICKS_PER_BLOCK = 1;

    private static final Map<RegistryKey<World>, Map<Integer, List<Lift>>> LIFTS_BY_TAG = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> COOLDOWN_UNTIL_TICK = new HashMap<>();

    private DoomLiftSystem() {
    }

    public static void register() {
        ServerTickEvents.END_WORLD_TICK.register(DoomLiftSystem::tickWorld);
    }

    public static void clear(ServerWorld world) {
        LIFTS_BY_TAG.remove(world.getRegistryKey());
    }

    public static void registerLift(ServerWorld world, int tag, Lift lift) {
        if (tag == 0) {
            return;
        }
        LIFTS_BY_TAG.computeIfAbsent(world.getRegistryKey(), k -> new HashMap<>())
            .computeIfAbsent(tag, k -> new ArrayList<>())
            .add(lift);
    }

    public static void activateByTag(ServerWorld world, ServerPlayerEntity activator, int tag) {
        if (tag == 0) {
            return;
        }
        System.out.println("[DoomMC3D] activateByTag called: tag=" + tag);
        Map<Integer, List<Lift>> byTag = LIFTS_BY_TAG.get(world.getRegistryKey());
        if (byTag == null) {
            System.out.println("[DoomMC3D] ERROR: No lifts registered for this world!");
            return;
        }
        List<Lift> lifts = byTag.get(tag);
        if (lifts == null || lifts.isEmpty()) {
            System.out.println("[DoomMC3D] ERROR: No lift found for tag " + tag + ". Available tags: " + byTag.keySet());
            return;
        }
        System.out.println("[DoomMC3D] Found " + lifts.size() + " lift(s) for tag " + tag);

        // Lightweight per-player cooldown so a single press doesn't spam-activate a tag.
        long now = world.getTime();
        long until = COOLDOWN_UNTIL_TICK.getOrDefault(activator.getUuid(), 0L);
        if (now < until) {
            System.out.println("[DoomMC3D] Lift activation on cooldown");
            return;
        }
        COOLDOWN_UNTIL_TICK.put(activator.getUuid(), now + 10);

        for (Lift lift : lifts) {
            System.out.println("[DoomMC3D] Activating lift");
            lift.activate(world);
        }
    }

    private static void tickWorld(ServerWorld world) {
        Map<Integer, List<Lift>> byTag = LIFTS_BY_TAG.get(world.getRegistryKey());
        if (byTag == null || byTag.isEmpty()) {
            return;
        }
        for (List<Lift> lifts : byTag.values()) {
            for (Lift lift : lifts) {
                lift.tick(world);
            }
        }
    }

    public enum LiftSpeed {
        SLOW,
        NORMAL,
        BLAZE
    }

    public static final class Lift {
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
            this.ticksPerBlock = speed == LiftSpeed.BLAZE ? BLAZE_TICKS_PER_BLOCK :
                                 speed == LiftSpeed.SLOW ? SLOW_TICKS_PER_BLOCK :
                                 NORMAL_TICKS_PER_BLOCK;
            this.currentY = topY;
        }

        public void tick(ServerWorld world) {
            if (state == LiftState.IDLE) {
                return;
            }

            long now = world.getTime();
            if (state == LiftState.WAITING) {
                if (now >= waitUntilTick) {
                    state = LiftState.MOVING_UP;
                }
                return;
            }

            if (ticksPerBlock > 1 && (now % ticksPerBlock) != 0) {
                return;
            }

            if (state == LiftState.MOVING_DOWN) {
                if (currentY <= bottomY) {
                    state = LiftState.WAITING;
                    waitUntilTick = now + waitTicks;
                    return;
                }
                step(world, currentY, currentY - 1);
                currentY -= 1;
                if (currentY <= bottomY) {
                    state = LiftState.WAITING;
                    waitUntilTick = now + waitTicks;
                }
                return;
            }

            if (state == LiftState.MOVING_UP) {
                if (currentY >= topY) {
                    state = LiftState.IDLE;
                    return;
                }
                step(world, currentY, currentY + 1);
                currentY += 1;
                if (currentY >= topY) {
                    state = LiftState.IDLE;
                }
            }
        }

        public void activate(ServerWorld world) {
            if (state != LiftState.IDLE) {
                return;
            }
            if (topY <= bottomY) {
                return;
            }
            state = LiftState.MOVING_DOWN;
        }

        private void step(ServerWorld world, int fromY, int toY) {
            // CRITICAL: Carry entities BEFORE moving the floor to prevent clipping
            carryEntities(world, fromY, toY);
            moveFloorLayer(world, fromY, toY);
            clearInteriorAir(world, toY);
            updateBoundaryWalls(world);
        }

        private void moveFloorLayer(ServerWorld world, int fromY, int toY) {
            for (FloorCell cell : floor) {
                BlockPos from = buildOrigin.add(cell.dx, fromY, cell.dz);
                BlockPos to = buildOrigin.add(cell.dx, toY, cell.dz);

                // Clear old floor (but avoid nuking non-floor blocks if someone placed something).
                BlockState prev = world.getBlockState(from);
                if (prev.isOf(floorState.getBlock())) {
                    world.setBlockState(from, Blocks.AIR.getDefaultState(), 3);
                } else if (prev.isAir()) {
                    // no-op
                } else {
                    // If the floor got modified by gameplay, still clear it to keep the platform moving.
                    world.setBlockState(from, Blocks.AIR.getDefaultState(), 3);
                }

                world.setBlockState(to, floorState, 3);
            }
        }

        private void clearInteriorAir(ServerWorld world, int newFloorY) {
            int start = Math.min(newFloorY + 1, ceilingY - 1);
            int end = Math.max(newFloorY + 1, ceilingY - 1);
            if (end < start) {
                return;
            }
            for (FloorCell cell : floor) {
                int x = cell.dx;
                int z = cell.dz;
                for (int y = start; y <= end; y++) {
                    BlockPos p = buildOrigin.add(x, y, z);
                    if (!world.getBlockState(p).isAir()) {
                        world.setBlockState(p, Blocks.AIR.getDefaultState(), 3);
                    }
                }
            }
        }

        private void updateBoundaryWalls(ServerWorld world) {
            for (BoundaryColumn column : boundaries) {
                int neighborFloorY = column.neighborFloorY;
                int floorMin = Math.min(currentY, neighborFloorY);
                int floorMax = Math.max(currentY, neighborFloorY);

                // First clear the possible gap region, then re-fill what we need.
                int clearBottom = Math.min(bottomY, neighborFloorY);
                int clearTop = Math.max(topY, neighborFloorY);

                for (int y = clearBottom; y <= clearTop; y++) {
                    BlockPos p = buildOrigin.add(column.dx, y, column.dz);
                    world.setBlockState(p, Blocks.AIR.getDefaultState(), 3);
                }

                if (floorMin < floorMax) {
                    for (int y = floorMin; y <= (floorMax - 1); y++) {
                        BlockPos p = buildOrigin.add(column.dx, y, column.dz);
                        world.setBlockState(p, column.wallState, 3);
                    }
                }
            }
        }

        private void carryEntities(ServerWorld world, int fromY, int toY) {
            int delta = toY - fromY;
            if (delta == 0) {
                return;
            }

            // Bounding box in world blocks (cheap broad-phase).
            int minDx = Integer.MAX_VALUE;
            int maxDx = Integer.MIN_VALUE;
            int minDz = Integer.MAX_VALUE;
            int maxDz = Integer.MIN_VALUE;
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

            // Expand bounding box slightly to catch entities on edges
            var box = new net.minecraft.util.math.Box(minX - 0.1, fromY - 0.5, minZ - 0.1, maxX + 0.1, fromY + 2.0, maxZ + 0.1);
            List<Entity> entities = world.getOtherEntities(null, box);
            
            for (Entity e : entities) {
                // Check if entity is actually standing on the current floor
                BlockPos under = e.getBlockPos().down();
                if (under.getY() != fromY) {
                    continue;
                }
                
                // Double-check entity is within bounds more precisely
                double entityX = e.getX();
                double entityZ = e.getZ();
                if (entityX < minX || entityX > maxX || entityZ < minZ || entityZ > maxZ) {
                    continue;
                }
                
                // Convert block coords to Doom coords and point-in-polygon test.
                double doomX = toDoomX((int) Math.floor(entityX));
                double doomZ = toDoomZ((int) Math.floor(entityZ));
                if (!containsPoint(sectorPolygon, doomX, doomZ)) {
                    continue;
                }

                // CRUSH DETECTION: Check if moving up and ceiling would crush entity
                if (delta > 0) { // Moving up
                    double newY = e.getY() + delta;
                    int ceilingBlockY = ceilingY;
                    // If entity's head + epsilon > ceiling, crush it
                    if (newY + 1.8 > ceilingBlockY) {
                        // Crush the entity with damage proportional to crush distance
                        float crushDamage = Math.min(20.0f, (float) (newY + 1.8 - ceilingBlockY) * 5);
                        e.damage(world, world.getDamageSources().generic(), crushDamage);
                        // Don't carry crushed entities; they stay in place to get hurt
                        continue;
                    }
                }

                // Safe to carry entity
                e.setPos(entityX, e.getY() + delta, entityZ);
            }
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

        private static boolean containsPoint(List<com.hitpo.doommc3d.doommap.Vertex> polygon, double x, double y) {
            boolean inside = false;
            for (int i = 0, j = polygon.size() - 1; i < polygon.size(); j = i++) {
                var vi = polygon.get(i);
                var vj = polygon.get(j);
                boolean intersect = ((vi.y() > y) != (vj.y() > y))
                    && (x < (vj.x() - vi.x()) * (y - vi.y()) / (double) (vj.y() - vi.y()) + vi.x());
                if (intersect) {
                    inside = !inside;
                }
            }
            return inside;
        }

        public static BlockState floorStateFromFlat(String flat) {
            BlockState state = PaletteMapper.mapFloor(flat);
            if (state == null) {
                state = Blocks.DEEPSLATE_TILES.getDefaultState();
            }
            return state;
        }

        public static BlockState wallStateFromTexture(String tex) {
            BlockState state = PaletteMapper.mapWall(tex);
            if (state == null) {
                state = Blocks.POLISHED_ANDESITE.getDefaultState();
            }
            return state;
        }
    }

    public record FloorCell(int dx, int dz) {
    }

    public record BoundaryColumn(int dx, int dz, int neighborFloorY, BlockState wallState) {
    }

    private enum LiftState {
        IDLE,
        MOVING_DOWN,
        WAITING,
        MOVING_UP
    }
}
