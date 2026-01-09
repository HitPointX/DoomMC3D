package com.hitpo.doommc3d.worldgen;

import com.hitpo.doommc3d.convert.DoomOrigin;
import com.hitpo.doommc3d.convert.DoomToMCScale;
import com.hitpo.doommc3d.doomai.DoomBossSystem;
import com.hitpo.doommc3d.doomai.DoomBossType;
import com.hitpo.doommc3d.doomai.DoomMobSystem;
import com.hitpo.doommc3d.doomai.DoomMobType;
import com.hitpo.doommc3d.doommap.DoomMap;
import com.hitpo.doommc3d.doommap.Thing;
import com.hitpo.doommc3d.doommap.Vertex;
import java.util.Locale;
import java.util.Set;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.network.packet.s2c.play.PositionFlag;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

public final class DoomThingSpawner {
    public static final String TAG_SPAWNED = "doommc3d_spawned";
    public static final String TAG_AMBUSH = "doommc3d_ambush";

    private DoomThingSpawner() {
    }

    public static SpawnResult spawnFromThings(ServerWorld world, ServerPlayerEntity player, DoomMap map, DoomOrigin origin, BlockPos buildOrigin, String mapName) {
        int enemies = 0;
        int bosses = 0;
        int starts = 0;

        String mapTag = mapTag(mapName);

        // Player start (type 1-4), prefer Player 1.
        Thing start = findStart(map);
        if (start != null) {
            Vec3d startPos = toWorldPos(map, origin, buildOrigin, start);
            float yaw = doomAngleToMinecraftYaw(start.angle());
            player.teleport(world, startPos.x, startPos.y, startPos.z, Set.<PositionFlag>of(), yaw, player.getPitch(), false);
            starts = 1;
        }

        for (int i = 0; i < map.things().length; i++) {
            Thing thing = map.things()[i];
            SpawnSpec spec = enemyForThingType(thing.type());
            if (spec == null) {
                continue;
            }
            if (!shouldSpawnInSinglePlayerNormal(thing.flags())) {
                continue;
            }

            MobEntity body = spec.bodyType.create(world, SpawnReason.COMMAND);
            if (body == null) {
                continue;
            }

            Vec3d pos = toWorldPos(map, origin, buildOrigin, thing);
            float yaw = doomAngleToMinecraftYaw(thing.angle());
            body.refreshPositionAndAngles(pos.x, pos.y, pos.z, yaw, 0.0f);

            tagSpawn(body, mapTag, i);
            if (isAmbush(thing.flags())) {
                body.addCommandTag(TAG_AMBUSH);
            }

            world.spawnEntity(body);

            if (spec.bossType != null) {
                DoomBossSystem.attach(body, spec.bossType);
                bosses++;
            } else {
                DoomMobSystem.attach(body, spec.mobType);
                enemies++;
            }
        }

        return new SpawnResult(starts, enemies, bosses);
    }

    private static void tagSpawn(MobEntity mob, String mapTag, int thingIndex) {
        mob.addCommandTag(TAG_SPAWNED);
        mob.addCommandTag(mapTag);
        mob.addCommandTag("doommc3d_thing_" + thingIndex);
    }

    private static boolean isAmbush(int flags) {
        return (flags & 0x0008) != 0;
    }

    // Doom THINGS flags (Doom format):
    // 0x0001 = Skill 1-2, 0x0002 = Skill 3, 0x0004 = Skill 4-5, 0x0008 = ambush, 0x0010 = multiplayer only.
    // For now we treat the dev run as singleplayer Skill 3 ("Hurt Me Plenty") for deterministic spawns.
    private static boolean shouldSpawnInSinglePlayerNormal(int flags) {
        if ((flags & 0x0010) != 0) {
            return false;
        }
        int skillMask = flags & 0x0007;
        if (skillMask == 0) {
            return true;
        }
        return (flags & 0x0002) != 0;
    }

    private static Thing findStart(DoomMap map) {
        Thing fallback = null;
        for (Thing thing : map.things()) {
            int t = thing.type();
            if (t == 1) {
                return thing;
            }
            if (t >= 1 && t <= 4) {
                fallback = thing;
            }
        }
        return fallback;
    }

    private static Vec3d toWorldPos(DoomMap map, DoomOrigin origin, BlockPos buildOrigin, Thing thing) {
        int sectorIndex = findSectorForThing(map, thing);
        int floorY = sectorIndex >= 0 ? DoomToMCScale.toBlock(map.sectors()[sectorIndex].floorHeight()) : 0;

        int x = DoomToMCScale.toBlock(thing.x()) - origin.originBlockX();
        int z = origin.originBlockZ() - DoomToMCScale.toBlock(thing.y());
        int y = floorY + 1;

        return new Vec3d(buildOrigin.getX() + x + 0.5, buildOrigin.getY() + y, buildOrigin.getZ() + z + 0.5);
    }

    private static float doomAngleToMinecraftYaw(int doomAngleDeg) {
        // Doom: 0=East,90=North,180=West,270=South
        // MC: -90=East,180=North,90=West,0=South
        return (float) (-doomAngleDeg - 90.0);
    }

    private static String mapTag(String mapName) {
        String safe = mapName == null ? "unknown" : mapName.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_\\-]", "_");
        return "doommc3d_map_" + safe;
    }

    private static SpawnSpec enemyForThingType(int doomType) {
        // Doom 1 (classic) thing IDs.
        return switch (doomType) {
            case 3004 -> SpawnSpec.mob(DoomMobType.ZOMBIEMAN, EntityType.HUSK);
            case 9 -> SpawnSpec.mob(DoomMobType.SHOTGUN_GUY, EntityType.PILLAGER);
            case 65 -> SpawnSpec.mob(DoomMobType.CHAINGUNNER, EntityType.PILLAGER);
            case 3001 -> SpawnSpec.mob(DoomMobType.IMP, EntityType.BLAZE);
            case 3002 -> SpawnSpec.mob(DoomMobType.DEMON, EntityType.HOGLIN);
            case 58 -> SpawnSpec.mob(DoomMobType.SPECTRE, EntityType.HOGLIN);
            case 3006 -> SpawnSpec.mob(DoomMobType.LOST_SOUL, EntityType.VEX);
            case 3005 -> SpawnSpec.mob(DoomMobType.CACODEMON, EntityType.GHAST);
            case 3003 -> SpawnSpec.mob(DoomMobType.BARON, EntityType.WITHER_SKELETON);
            // Bosses.
            case 16 -> SpawnSpec.boss(DoomBossType.CYBERDEMON, EntityType.IRON_GOLEM);
            case 7 -> SpawnSpec.boss(DoomBossType.SPIDER_MASTERMIND, EntityType.SPIDER);
            default -> null;
        };
    }

    // Copied from ThingPlacer: sector lookup by point-in-polygon (good enough for now).
    private static int findSectorForThing(DoomMap map, Thing thing) {
        double x = thing.x();
        double y = thing.y();
        for (int sectorIndex = 0; sectorIndex < map.sectors().length; sectorIndex++) {
            var polygon = ThingPlacerSectorGeometry.buildSectorPolygon(map, sectorIndex);
            if (polygon.isEmpty()) {
                continue;
            }
            if (ThingPlacerSectorGeometry.containsPoint(polygon, x, y)) {
                return sectorIndex;
            }
        }
        return -1;
    }

    private record SpawnSpec(DoomMobType mobType, DoomBossType bossType, EntityType<? extends MobEntity> bodyType) {
        static SpawnSpec mob(DoomMobType type, EntityType<? extends MobEntity> body) {
            return new SpawnSpec(type, null, body);
        }

        static SpawnSpec boss(DoomBossType boss, EntityType<? extends MobEntity> body) {
            return new SpawnSpec(null, boss, body);
        }
    }

    public record SpawnResult(int playerStarts, int enemiesSpawned, int bossesSpawned) {
    }
}
