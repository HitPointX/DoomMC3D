package com.hitpo.doommc3d.worldgen;

import com.hitpo.doommc3d.convert.SectorRasterizer;
import com.hitpo.doommc3d.convert.DoomOrigin;
import com.hitpo.doommc3d.doommap.DoomMap;
import com.hitpo.doommc3d.wad.DoomMapParser;
import com.hitpo.doommc3d.wad.WadFile;
import com.hitpo.doommc3d.wad.WadLoader;
import com.hitpo.doommc3d.wad.WadRepository;
import com.hitpo.doommc3d.net.PlayMusicPayload;
import com.hitpo.doommc3d.interact.DoomSecretRegistry;
import com.hitpo.doommc3d.interact.DoomTeleporterRegistry;
import com.hitpo.doommc3d.interact.DoomLevelBoundsRegistry;
import com.hitpo.doommc3d.interact.DoomLevelState;
import com.hitpo.doommc3d.interact.DoomLevelStateRegistry;
import com.hitpo.doommc3d.interact.DoomSectorGraphRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.world.rule.GameRules;

import java.io.IOException;
import java.util.List;

public class DoomWorldBuilder {
    public static void build(ServerWorld world, ServerPlayerEntity player, String mapName) {
        build(world, player, mapName, null);
    }

    public static void build(ServerWorld world, ServerPlayerEntity player, String mapName, String wadOverride) {
        com.hitpo.doommc3d.util.DebugLogger.debug("DoomWorldBuilder.build", () -> "[DoomMC3D] Building map: " + mapName);
        var playerPos = player.getBlockPos();
        // Apply a global absolute Y offset so Doom levels are generated high above terrain to avoid clipping.
        // Use the BASE_Y value as an absolute world Y (not relative to the player's current Y).
        int baseY = com.hitpo.doommc3d.interact.DoomGenConfig.getBaseY();
        int maxHeight = com.hitpo.doommc3d.interact.DoomGenConfig.getMaxWorldHeight();
        int targetY = baseY;
        // If BASE_Y is unrealistically high, clamp to a safe room below max height
        if (targetY > maxHeight - 16) targetY = Math.max(16, maxHeight - 16);
        var buildOrigin = playerPos.withY(targetY);
        BlockPlacer placer = new BlockPlacer(world, buildOrigin);
        try {
            WadFile wad = WadRepository.getOrLoad(wadOverride);
            com.hitpo.doommc3d.util.DebugLogger.debug("DoomWorldBuilder.wad", () -> "[DoomMC3D] WAD search dirs: " + WadLoader.getWadsDirectories());
            com.hitpo.doommc3d.util.DebugLogger.debug("DoomWorldBuilder.wad", () -> "[DoomMC3D] Using WAD: " + wad.getSource());
            
            DoomMap doomMap = DoomMapParser.parse(wad, mapName);
            com.hitpo.doommc3d.util.DebugLogger.debug("DoomWorldBuilder.map", () -> "[DoomMC3D] Map '" + doomMap.name() + "' loaded with " + doomMap.vertices().length + " vertices");
            logTextureUsage(doomMap);
            player.sendMessage(Text.literal("[DoomMC3D] Rendering " + mapName + " from " + wad.getSource().getFileName()), false);
            DoomOrigin origin = DoomOrigin.fromMap(doomMap);

            DoomSectorGraphRegistry.set(world, DoomSectorGraphBuilder.build(doomMap, origin, buildOrigin));

            // Doom levels shouldn't have Minecraft ambient spawns.
            world.getGameRules().setValue(GameRules.DO_MOB_SPAWNING, false, world.getServer());
            world.getGameRules().setValue(GameRules.SPAWN_PATROLS, false, world.getServer());
            world.getGameRules().setValue(GameRules.SPAWN_WANDERING_TRADERS, false, world.getServer());
            world.getGameRules().setValue(GameRules.SPAWN_PHANTOMS, false, world.getServer());

            DoomLevelBoundsRegistry.set(world, DoomSpawnCleanup.computeBounds(world, doomMap, origin, buildOrigin));
            int cleared = DoomSpawnCleanup.clearSpawnedEntities(world, doomMap, origin, buildOrigin);
            if (cleared > 0) {
                com.hitpo.doommc3d.util.DebugLogger.debug("DoomWorldBuilder.clean", () -> "[DoomMC3D] Cleared " + cleared + " previously spawned entities");
            }
            SectorRasterizer rasterizer = new SectorRasterizer();
            rasterizer.rasterize(doomMap, placer, origin);
            // Place a solid roof above the generated map to block skylight leaks
            try {
                var bounds = DoomLevelBoundsRegistry.get(world);
                if (bounds != null) {
                    // Compute max ceiling used in map (block-space relative to origin)
                    int maxCeilRel = Integer.MIN_VALUE;
                    for (com.hitpo.doommc3d.doommap.Sector s : doomMap.sectors()) {
                        int c = com.hitpo.doommc3d.convert.DoomToMCScale.toBlock(s.ceilingHeight());
                        maxCeilRel = Math.max(maxCeilRel, c);
                    }
                    if (maxCeilRel != Integer.MIN_VALUE) {
                        int roofPadding = 3;
                        int roofWorldY = buildOrigin.getY() + maxCeilRel + roofPadding;
                        int minX = (int) Math.floor(bounds.minX);
                        int maxX = (int) Math.ceil(bounds.maxX);
                        int minZ = (int) Math.floor(bounds.minZ);
                        int maxZ = (int) Math.ceil(bounds.maxZ);
                        for (int wx = minX; wx <= maxX; wx++) {
                            for (int wz = minZ; wz <= maxZ; wz++) {
                                int relX = wx - buildOrigin.getX();
                                int relZ = wz - buildOrigin.getZ();
                                int relY = roofWorldY - buildOrigin.getY();
                                placer.placeBlock(relX, relY, relZ, net.minecraft.block.Blocks.POLISHED_DEEPSLATE.getDefaultState());
                            }
                        }
                    }
                }
            } catch (Exception ignored) {
            }
            int thingsPlaced = ThingPlacer.place(world, doomMap, origin, buildOrigin);
            int telepadsPlaced = DoomTeleporterPadPlacer.placePads(world, doomMap, origin, buildOrigin);
            List<com.hitpo.doommc3d.interact.DoomSecretTrigger> secrets = DoomSecretExtractor.extract(doomMap, origin, buildOrigin);
            DoomSecretRegistry.clear(world);
            DoomSecretRegistry.set(world, secrets);
            DoomSecretSystem.clearPlayerCache();
            List<com.hitpo.doommc3d.interact.DoomTeleporterTrigger> teleporters = DoomTeleporterExtractor.extract(doomMap, origin, buildOrigin);
            DoomTeleporterRegistry.clear(world);
            DoomTeleporterRegistry.set(world, teleporters);
            DoomTeleporterSystem.clearPlayerCache();
            DoorPlacer.placeDoors(world, doomMap, origin, buildOrigin);
            DoomTriggerPlacer.place(world, doomMap, origin, buildOrigin);
            DoomLiftPlacer.place(world, doomMap, origin, buildOrigin);
            DoomEventTriggerPlacer.place(world, doomMap, origin, buildOrigin);
            DoomLineTriggerPlacer.place(world, doomMap, origin, buildOrigin);
            var spawns = DoomThingSpawner.spawnFromThings(world, player, doomMap, origin, buildOrigin, mapName);
            ServerPlayNetworking.send(player, new PlayMusicPayload(mapName));

            DoomLevelStateRegistry.set(world, new DoomLevelState(mapName, wad.getSource().getFileName().toString(), buildOrigin.toImmutable()));
            com.hitpo.doommc3d.util.DebugLogger.debug("DoomWorldBuilder.place", () -> "[DoomMC3D] Placed " + thingsPlaced + " THINGS");
            com.hitpo.doommc3d.util.DebugLogger.debug("DoomWorldBuilder.place", () -> "[DoomMC3D] Placed " + telepadsPlaced + " teleporter pads");
            com.hitpo.doommc3d.util.DebugLogger.debug("DoomWorldBuilder.place", () -> "[DoomMC3D] Registered " + secrets.size() + " secrets");
            com.hitpo.doommc3d.util.DebugLogger.debug("DoomWorldBuilder.place", () -> "[DoomMC3D] Registered " + teleporters.size() + " teleporters");
            com.hitpo.doommc3d.util.DebugLogger.debug("DoomWorldBuilder.place", () -> "[DoomMC3D] Spawned " + spawns.enemiesSpawned() + " enemies and " + spawns.bossesSpawned() + " bosses");
        } catch (IOException | IllegalArgumentException e) {
            String message = "[DoomMC3D] Failed to load map (" + e.getMessage() + ")";
            player.sendMessage(Text.literal(message), false);
            com.hitpo.doommc3d.util.DebugLogger.debug("DoomWorldBuilder.error", () -> {
            e.printStackTrace();
            return "[DoomMC3D] Failed to load map (" + e.getMessage() + ")";
            });
        }
    }

    private static void logTextureUsage(DoomMap doomMap) {
        java.util.Map<String, Integer> floorTextures = new java.util.HashMap<>();
        java.util.Map<String, Integer> ceilingTextures = new java.util.HashMap<>();
        java.util.Map<String, Integer> upperTextures = new java.util.HashMap<>();
        java.util.Map<String, Integer> lowerTextures = new java.util.HashMap<>();
        java.util.Map<String, Integer> midTextures = new java.util.HashMap<>();

        // Count floor and ceiling textures
        for (com.hitpo.doommc3d.doommap.Sector sector : doomMap.sectors()) {
            String floor = normalizeTextureName(sector.floorTexture());
            String ceiling = normalizeTextureName(sector.ceilingTexture());
            floorTextures.put(floor, floorTextures.getOrDefault(floor, 0) + 1);
            ceilingTextures.put(ceiling, ceilingTextures.getOrDefault(ceiling, 0) + 1);
        }

        // Count sidedef textures
        for (com.hitpo.doommc3d.doommap.Sidedef side : doomMap.sidedefs()) {
            String upper = normalizeTextureName(side.upperTexture());
            String lower = normalizeTextureName(side.lowerTexture());
            String mid = normalizeTextureName(side.middleTexture());
            if (!upper.equals("-")) upperTextures.put(upper, upperTextures.getOrDefault(upper, 0) + 1);
            if (!lower.equals("-")) lowerTextures.put(lower, lowerTextures.getOrDefault(lower, 0) + 1);
            if (!mid.equals("-")) midTextures.put(mid, midTextures.getOrDefault(mid, 0) + 1);
        }

        // Log top 10 of each
        com.hitpo.doommc3d.util.DebugLogger.debug("DoomWorldBuilder.textures", () -> "[DoomMC3D] === TEXTURE USAGE TOP 10 ===");
        com.hitpo.doommc3d.util.DebugLogger.debug("DoomWorldBuilder.textures", () -> "[DoomMC3D] Floor textures:");
        logTop10(floorTextures);
        com.hitpo.doommc3d.util.DebugLogger.debug("DoomWorldBuilder.textures", () -> "[DoomMC3D] Ceiling textures:");
        logTop10(ceilingTextures);
        com.hitpo.doommc3d.util.DebugLogger.debug("DoomWorldBuilder.textures", () -> "[DoomMC3D] Upper textures:");
        logTop10(upperTextures);
        com.hitpo.doommc3d.util.DebugLogger.debug("DoomWorldBuilder.textures", () -> "[DoomMC3D] Lower textures:");
        logTop10(lowerTextures);
        com.hitpo.doommc3d.util.DebugLogger.debug("DoomWorldBuilder.textures", () -> "[DoomMC3D] Mid textures:");
        logTop10(midTextures);
        com.hitpo.doommc3d.util.DebugLogger.debug("DoomWorldBuilder.textures", () -> "[DoomMC3D] === END TEXTURE USAGE ===");
    }

    private static void logTop10(java.util.Map<String, Integer> textures) {
        textures.entrySet().stream()
            .sorted((a, b) -> b.getValue().compareTo(a.getValue()))
            .limit(10)
            .forEach(e -> com.hitpo.doommc3d.util.DebugLogger.debug("DoomWorldBuilder.textures", () -> "[DoomMC3D]   " + e.getKey() + ": " + e.getValue()));
    }

    private static String normalizeTextureName(String texture) {
        if (texture == null || texture.isBlank() || texture.equals("-")) {
            return "-";
        }
        return texture.trim().toUpperCase();
    }
}
