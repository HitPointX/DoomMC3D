package com.hitpo.doommc3d.worldgen;

import com.hitpo.doommc3d.convert.DoomOrigin;
import com.hitpo.doommc3d.convert.DoomToMCScale;
import com.hitpo.doommc3d.DoomConstants;
import com.hitpo.doommc3d.DoomGameRules;
import com.hitpo.doommc3d.doommap.DoomMap;
import com.hitpo.doommc3d.doommap.Linedef;
import com.hitpo.doommc3d.doommap.Sector;
import com.hitpo.doommc3d.doommap.Sidedef;
import com.hitpo.doommc3d.doommap.Thing;
import com.hitpo.doommc3d.doommap.Vertex;
import com.hitpo.doommc3d.item.ModItems;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.item.ItemDisplayContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

public final class ThingPlacer {
    private ThingPlacer() {
    }

    private static final double DOOM_UNITS_PER_BLOCK = (double) DoomConstants.DOOM_TO_MC_SCALE;

    public static int place(ServerWorld world, DoomMap map, DoomOrigin origin, BlockPos buildOrigin) {
        // Things can be rebuilt (or swapped) during development; clear per-level registries.
        DoomBarrelRegistry.clear(world);

        boolean allowDeathmatchWeapons = DoomGameRules.allowDeathmatchWeapons(world);

        BlockPlacer placer = new BlockPlacer(world, buildOrigin);
        int placed = 0;
        for (Thing thing : map.things()) {
            Placement placement = mapThing(thing.type());
            if (placement == null) {
                continue;
            }

            // Doom THINGS flag 0x0010 = multiplayer only (deathmatch/coop). We only apply this gate to weapon pickups
            // so maps stay playable in singleplayer by default.
            if (!allowDeathmatchWeapons
                && placement.displayKind == Placement.DisplayKind.WEAPON
                && isMultiplayerOnlyThing(thing.flags())) {
                continue;
            }

            int sectorIndex = findSectorForThing(map, thing);
            int floorY = sectorIndex >= 0 ? DoomToMCScale.toBlock(map.sectors()[sectorIndex].floorHeight()) : 0;

            int x = DoomToMCScale.toBlock(thing.x()) - origin.originBlockX();
            int z = origin.originBlockZ() - DoomToMCScale.toBlock(thing.y());
            int y = floorY + placement.yOffsetBlocks;

            BlockPos pos = buildOrigin.add(x, y, z);
            if (placement.displayItem != null) {
                if (placement.displayKind == Placement.DisplayKind.WEAPON) {
                    spawnWeaponDisplay(world, pos, placement.displayItem, placement.displayTag);
                } else if (placement.displayKind == Placement.DisplayKind.KEY) {
                    spawnKeyDisplay(world, pos, placement.displayItem, placement.displayTag);
                } else if (placement.displayKind == Placement.DisplayKind.CORPSE) {
                    // Corpses should never block movement like a placed block.
                    // Place with sub-block precision so it doesn't "snap" into doorways.
                    double wx = buildOrigin.getX() + ((thing.x() / DOOM_UNITS_PER_BLOCK) - origin.originBlockX()) + 0.5;
                    double wz = buildOrigin.getZ() + (origin.originBlockZ() - (thing.y() / DOOM_UNITS_PER_BLOCK)) + 0.5;
                    double wy = buildOrigin.getY() + floorY + 1.05;

                    // Clean up any legacy skull blocks at the old snapped location.
                    BlockPos legacy = pos;
                    if (world.getBlockState(legacy).isOf(Blocks.SKELETON_SKULL) || world.getBlockState(legacy).isOf(Blocks.SKELETON_WALL_SKULL)) {
                        world.setBlockState(legacy, Blocks.AIR.getDefaultState(), 3);
                    }
                    BlockPos legacyUp = legacy.up();
                    if (world.getBlockState(legacyUp).isOf(Blocks.SKELETON_SKULL) || world.getBlockState(legacyUp).isOf(Blocks.SKELETON_WALL_SKULL)) {
                        world.setBlockState(legacyUp, Blocks.AIR.getDefaultState(), 3);
                    }

                    spawnCorpseDisplay(world, wx, wy, wz, placement.displayItem, placement.displayTag);
                }
                placed++;
            } else {
                if (!world.getBlockState(pos).isAir()) {
                    continue;
                }
                BlockState state = placement.blockStateForAngle(thing.angle());
                placer.placeBlock(x, y, z, state);
                placed++;
            }
        }
        return placed;
    }

    private static boolean isMultiplayerOnlyThing(int flags) {
        return (flags & 0x0010) != 0;
    }

    private static Placement mapThing(int doomType) {
        // DOOM barrels
        if (doomType == 2035) {
            return Placement.block(Blocks.TNT.getDefaultState(), 1);
        }

        // Keycards (Doom 1)
        if (doomType == 13 || doomType == 38) { // red key / skull
            return Placement.key(new ItemStack(ModItems.DOOM_KEY_RED), 2, "doommc3d_key_red");
        }
        if (doomType == 6 || doomType == 39) { // yellow key / skull
            return Placement.key(new ItemStack(ModItems.DOOM_KEY_YELLOW), 2, "doommc3d_key_yellow");
        }
        if (doomType == 5 || doomType == 40) { // blue key / skull
            return Placement.key(new ItemStack(ModItems.DOOM_KEY_BLUE), 2, "doommc3d_key_blue");
        }

        // Weapons (pickups)
        if (doomType == 2001) { // shotgun
            return Placement.weapon(new ItemStack(ModItems.DOOM_SHOTGUN), 2, "doommc3d_weapon_shotgun");
        }
        if (doomType == 2002) { // chaingun
            return Placement.weapon(new ItemStack(ModItems.DOOM_CHAINGUN), 2, "doommc3d_weapon_chaingun");
        }
        if (doomType == 2003) { // rocket launcher
            return Placement.weapon(new ItemStack(ModItems.DOOM_ROCKET_LAUNCHER), 2, "doommc3d_weapon_rocket_launcher");
        }
        if (doomType == 2004) { // plasma rifle
            return Placement.weapon(new ItemStack(ModItems.DOOM_PLASMA_RIFLE), 2, "doommc3d_weapon_plasma_rifle");
        }
        if (doomType == 2006) { // BFG9000
            return Placement.weapon(new ItemStack(ModItems.DOOM_BFG), 2, "doommc3d_weapon_bfg");
        }

        // Ammo (temporary mapping to vanilla items with custom model later)
        if (doomType == 2007) { // ammo clip (pistol ammo)
            return Placement.weapon(new ItemStack(Items.IRON_NUGGET), 1, "doommc3d_ammo_clip");
        }
        if (doomType == 2048) { // box of bullets
            return Placement.weapon(new ItemStack(Items.IRON_INGOT), 1, "doommc3d_ammo_box_bullets");
        }
        if (doomType == 2008) { // shotgun shells
            return Placement.weapon(new ItemStack(Items.GOLD_NUGGET), 1, "doommc3d_ammo_shell");
        }
        if (doomType == 2049) { // box of shotgun shells
            return Placement.weapon(new ItemStack(Items.GOLD_INGOT), 1, "doommc3d_ammo_box_shells");
        }
        if (doomType == 2010) { // rocket ammo
            return Placement.weapon(new ItemStack(Items.FIRE_CHARGE), 1, "doommc3d_ammo_rocket");
        }
        if (doomType == 2046) { // box of rockets
            return Placement.weapon(new ItemStack(Items.TNT), 1, "doommc3d_ammo_box_rockets");
        }
        if (doomType == 2047) { // energy cell
            return Placement.weapon(new ItemStack(Items.PRISMARINE_CRYSTALS), 1, "doommc3d_ammo_cell");
        }
        if (doomType == 17) { // energy cell pack
            return Placement.weapon(new ItemStack(Items.PRISMARINE), 1, "doommc3d_ammo_cell_pack");
        }

        // Health pickups (medkit, stimpack)
        if (doomType == 2011) { // stimpack (10 HP)
            return Placement.weapon(new ItemStack(Items.POTION), 1, "doommc3d_stimpack");
        }
        if (doomType == 2012) { // medkit (25 HP)
            return Placement.weapon(new ItemStack(Items.GLISTERING_MELON_SLICE), 1, "doommc3d_medkit");
        }

        // Bonus items
        // Chocolate Doom info.c:
        // - Health bonus (vial): doomednum 2014 (S_BON1)
        // - Armor bonus (helmet): doomednum 2015 (S_BON2)
        if (doomType == 2014) { // health bonus (+1 up to 200)
            return Placement.weapon(new ItemStack(Items.GLASS_BOTTLE), 1, "doommc3d_health_bonus");
        }
        if (doomType == 2015) { // armor bonus (+1 up to 200)
            return Placement.weapon(new ItemStack(Items.IRON_HELMET), 1, "doommc3d_armor_bonus");
        }

        // Armor pickups
        // Chocolate Doom info.c:
        // - Green armor: doomednum 2018 (S_ARM1)
        // - Blue armor: doomednum 2019 (S_ARM2)
        if (doomType == 2018) { // green armor (100 pts)
            return Placement.weapon(new ItemStack(Items.CHAINMAIL_CHESTPLATE), 1, "doommc3d_green_armor");
        }
        if (doomType == 2019) { // blue armor (200 pts)
            return Placement.weapon(new ItemStack(Items.DIAMOND_CHESTPLATE), 1, "doommc3d_blue_armor");
        }

        // Power-ups
        if (doomType == 2013) { // soul sphere (100 HP, max 200)
            return Placement.weapon(new ItemStack(Items.HEART_OF_THE_SEA), 1, "doommc3d_soul_sphere");
        }
        if (doomType == 83) { // megasphere (200 HP, 200 armor) - Doom II only
            return Placement.weapon(new ItemStack(Items.NETHER_STAR), 1, "doommc3d_megasphere");
        }

        // Ammo backpack
        if (doomType == 8) { // backpack (doubles max ammo, gives +1 clip of each ammo)
            return Placement.weapon(new ItemStack(Items.CHEST), 1, "doommc3d_backpack");
        }

        // Corpses / gore (placeholder: skull on floor)
        if (isCorpseType(doomType)) {
            return Placement.corpse(new ItemStack(Items.SKELETON_SKULL), 0, "doommc3d_corpse");
        }

        // Lamps / torches (placeholder lighting)
        if (doomType == 2028) { // floor lamp
            return Placement.block(Blocks.LANTERN.getDefaultState(), 2);
        }
        if (doomType == 2029) { // hanging lamp
            return Placement.block(Blocks.SOUL_LANTERN.getDefaultState(), 2);
        }

        return null;
    }

    private static void spawnKeyDisplay(ServerWorld world, BlockPos pos, ItemStack stack, String keyTag) {
        DisplayEntity.ItemDisplayEntity display = new DisplayEntity.ItemDisplayEntity(EntityType.ITEM_DISPLAY, world);
        display.setPosition(pos.getX() + 0.5, pos.getY() + 0.35, pos.getZ() + 0.5);
        display.setItemStack(stack);
        display.setItemDisplayContext(ItemDisplayContext.GROUND);
        display.setBillboardMode(DisplayEntity.BillboardMode.CENTER);
        display.setDisplayWidth(0.6f);
        display.setDisplayHeight(0.6f);
        display.setViewRange(24.0f);
        display.setShadowRadius(0.0f);
        display.setShadowStrength(0.0f);
        display.addCommandTag(DoomThingSpawner.TAG_SPAWNED);
        display.addCommandTag("doommc3d_key");
        display.addCommandTag(keyTag);
        world.spawnEntity(display);
    }

    private static void spawnWeaponDisplay(ServerWorld world, BlockPos pos, ItemStack stack, String weaponTag) {
        DisplayEntity.ItemDisplayEntity display = new DisplayEntity.ItemDisplayEntity(EntityType.ITEM_DISPLAY, world);
        display.setPosition(pos.getX() + 0.5, pos.getY() + 0.35, pos.getZ() + 0.5);
        display.setItemStack(stack);
        display.setItemDisplayContext(ItemDisplayContext.GROUND);
        display.setBillboardMode(DisplayEntity.BillboardMode.CENTER);
        display.setDisplayWidth(0.75f);
        display.setDisplayHeight(0.75f);
        display.setViewRange(28.0f);
        display.setShadowRadius(0.0f);
        display.setShadowStrength(0.0f);
        display.addCommandTag(DoomThingSpawner.TAG_SPAWNED);
        display.addCommandTag("doommc3d_weapon_pickup");
        display.addCommandTag(weaponTag);
        world.spawnEntity(display);
    }

    private static void spawnCorpseDisplay(ServerWorld world, double x, double y, double z, ItemStack stack, String corpseTag) {
        DisplayEntity.ItemDisplayEntity display = new DisplayEntity.ItemDisplayEntity(EntityType.ITEM_DISPLAY, world);
        display.setPosition(x, y, z);
        display.setItemStack(stack);
        display.setItemDisplayContext(ItemDisplayContext.GROUND);
        // Doom sprites always face the player.
        display.setBillboardMode(DisplayEntity.BillboardMode.CENTER);
        display.setDisplayWidth(0.8f);
        display.setDisplayHeight(0.8f);
        display.setViewRange(24.0f);
        display.setShadowRadius(0.0f);
        display.setShadowStrength(0.0f);
        display.addCommandTag(DoomThingSpawner.TAG_SPAWNED);
        display.addCommandTag(corpseTag);
        world.spawnEntity(display);
    }

    private static boolean isCorpseType(int doomType) {
        return switch (doomType) {
            case 10, 12, 15, 18, 19, 20, 22, 23, 24, 25, 26 -> true;
            default -> false;
        };
    }

    private static int findSectorForThing(DoomMap map, Thing thing) {
        double x = thing.x();
        double y = thing.y();
        for (int sectorIndex = 0; sectorIndex < map.sectors().length; sectorIndex++) {
            List<Vertex> polygon = buildSectorPolygon(map, sectorIndex);
            if (polygon.isEmpty()) {
                continue;
            }
            if (containsPoint(polygon, x, y)) {
                return sectorIndex;
            }
        }
        return -1;
    }

    private static boolean containsPoint(List<Vertex> polygon, double x, double y) {
        boolean inside = false;
        for (int i = 0, j = polygon.size() - 1; i < polygon.size(); j = i++) {
            Vertex vi = polygon.get(i);
            Vertex vj = polygon.get(j);
            boolean intersect = ((vi.y() > y) != (vj.y() > y))
                && (x < (vj.x() - vi.x()) * (y - vi.y()) / (double) (vj.y() - vi.y()) + vi.x());
            if (intersect) {
                inside = !inside;
            }
        }
        return inside;
    }

    private static List<Vertex> buildSectorPolygon(DoomMap map, int sectorIndex) {
        List<Edge> edges = collectSectorEdges(map, sectorIndex);
        if (edges.isEmpty()) {
            return List.of();
        }
        List<Edge> ordered = orderEdges(edges);
        Vertex[] vertices = map.vertices();
        List<Vertex> polygon = new ArrayList<>();
        for (Edge edge : ordered) {
            polygon.add(vertices[edge.start]);
        }
        return polygon;
    }

    private static List<Edge> collectSectorEdges(DoomMap map, int sectorIndex) {
        List<Edge> edges = new ArrayList<>();
        Linedef[] linedefs = map.linedefs();
        Sidedef[] sidedefs = map.sidedefs();
        for (Linedef linedef : linedefs) {
            int rightSector = getSectorFromSide(sidedefs, linedef.rightSidedef());
            int leftSector = getSectorFromSide(sidedefs, linedef.leftSidedef());
            if (rightSector == sectorIndex) {
                edges.add(new Edge(linedef.startVertex(), linedef.endVertex()));
            } else if (leftSector == sectorIndex) {
                edges.add(new Edge(linedef.endVertex(), linedef.startVertex()));
            }
        }
        return edges;
    }

    private static List<Edge> orderEdges(List<Edge> edges) {
        List<Edge> ordered = new ArrayList<>();
        Set<Edge> used = new HashSet<>();
        ordered.add(edges.get(0));
        used.add(edges.get(0));
        while (ordered.size() < edges.size()) {
            Edge current = ordered.get(ordered.size() - 1);
            Edge next = findNextEdge(edges, used, current.end);
            if (next == null) {
                break;
            }
            ordered.add(next);
            used.add(next);
        }
        return ordered;
    }

    private static Edge findNextEdge(List<Edge> edges, Set<Edge> used, int startVertex) {
        for (Edge edge : edges) {
            if (!used.contains(edge) && edge.start == startVertex) {
                return edge;
            }
        }
        return null;
    }

    private static int getSectorFromSide(Sidedef[] sidedefs, int sideIndex) {
        if (sideIndex < 0 || sideIndex >= sidedefs.length) {
            return -1;
        }
        return sidedefs[sideIndex].sector();
    }

    private record Edge(int start, int end) {
    }

    private static final class Placement {
        private final BlockState baseState;
        private final int yOffsetBlocks;
        private final boolean rotateSkull;
        private final ItemStack displayItem;
        private final DisplayKind displayKind;
        private final String displayTag;

        private Placement(BlockState baseState, int yOffsetBlocks, boolean rotateSkull, ItemStack displayItem, DisplayKind displayKind, String displayTag) {
            this.baseState = baseState;
            this.yOffsetBlocks = yOffsetBlocks;
            this.rotateSkull = rotateSkull;
            this.displayItem = displayItem;
            this.displayKind = displayKind;
            this.displayTag = displayTag;
        }

        private enum DisplayKind {
            KEY,
            WEAPON,
            CORPSE
        }

        static Placement block(BlockState state, int yOffsetBlocks) {
            return new Placement(state, yOffsetBlocks, false, null, null, null);
        }

        static Placement skull(BlockState state, int yOffsetBlocks) {
            return new Placement(state, yOffsetBlocks, true, null, null, null);
        }

        static Placement key(ItemStack stack, int yOffsetBlocks, String keyTag) {
            return new Placement(Blocks.AIR.getDefaultState(), yOffsetBlocks, false, stack, DisplayKind.KEY, keyTag);
        }

        static Placement weapon(ItemStack stack, int yOffsetBlocks, String weaponTag) {
            return new Placement(Blocks.AIR.getDefaultState(), yOffsetBlocks, false, stack, DisplayKind.WEAPON, weaponTag);
        }

        static Placement corpse(ItemStack stack, int yOffsetBlocks, String corpseTag) {
            return new Placement(Blocks.AIR.getDefaultState(), yOffsetBlocks, false, stack, DisplayKind.CORPSE, corpseTag);
        }

        BlockState blockStateForAngle(int doomAngleDegrees) {
            if (!rotateSkull) {
                return baseState;
            }
            return baseState;
        }
    }
}
