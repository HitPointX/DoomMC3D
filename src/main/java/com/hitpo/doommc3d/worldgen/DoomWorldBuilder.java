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
        System.out.println("[DoomMC3D] Building map: " + mapName);
        var buildOrigin = player.getBlockPos();
        BlockPlacer placer = new BlockPlacer(world, buildOrigin);
        try {
            WadFile wad = WadRepository.getOrLoad(wadOverride);
            System.out.println("[DoomMC3D] WAD search dirs: " + WadLoader.getWadsDirectories());
            System.out.println("[DoomMC3D] Using WAD: " + wad.getSource());
            
            DoomMap doomMap = DoomMapParser.parse(wad, mapName);
            System.out.println("[DoomMC3D] Map '" + doomMap.name() + "' loaded with " + doomMap.vertices().length + " vertices");
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
                System.out.println("[DoomMC3D] Cleared " + cleared + " previously spawned entities");
            }
            SectorRasterizer rasterizer = new SectorRasterizer();
            rasterizer.rasterize(doomMap, placer, origin);
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
            System.out.println("[DoomMC3D] Placed " + thingsPlaced + " THINGS");
            System.out.println("[DoomMC3D] Placed " + telepadsPlaced + " teleporter pads");
            System.out.println("[DoomMC3D] Registered " + secrets.size() + " secrets");
            System.out.println("[DoomMC3D] Registered " + teleporters.size() + " teleporters");
            System.out.println("[DoomMC3D] Spawned " + spawns.enemiesSpawned() + " enemies and " + spawns.bossesSpawned() + " bosses");
        } catch (IOException | IllegalArgumentException e) {
            String message = "[DoomMC3D] Failed to load map (" + e.getMessage() + ")";
            player.sendMessage(Text.literal(message), false);
            e.printStackTrace();
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
        System.out.println("[DoomMC3D] === TEXTURE USAGE TOP 10 ===");
        System.out.println("[DoomMC3D] Floor textures:");
        logTop10(floorTextures);
        System.out.println("[DoomMC3D] Ceiling textures:");
        logTop10(ceilingTextures);
        System.out.println("[DoomMC3D] Upper textures:");
        logTop10(upperTextures);
        System.out.println("[DoomMC3D] Lower textures:");
        logTop10(lowerTextures);
        System.out.println("[DoomMC3D] Mid textures:");
        logTop10(midTextures);
        System.out.println("[DoomMC3D] === END TEXTURE USAGE ===");
    }

    private static void logTop10(java.util.Map<String, Integer> textures) {
        textures.entrySet().stream()
            .sorted((a, b) -> b.getValue().compareTo(a.getValue()))
            .limit(10)
            .forEach(e -> System.out.println("[DoomMC3D]   " + e.getKey() + ": " + e.getValue()));
    }

    private static String normalizeTextureName(String texture) {
        if (texture == null || texture.isBlank() || texture.equals("-")) {
            return "-";
        }
        return texture.trim().toUpperCase();
    }
}
