package com.hitpo.doommc3d.convert;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;

import java.util.Locale;

public final class PaletteMapper {
    private static final BlockState DEFAULT_WALL = Blocks.POLISHED_ANDESITE.getDefaultState();
    private static final BlockState DEFAULT_FLOOR = Blocks.DEEPSLATE_TILES.getDefaultState();
    private static final BlockState DEFAULT_CEILING = Blocks.SMOOTH_STONE.getDefaultState();

    private PaletteMapper() {
    }

    public static BlockState mapWall(String texture) {
        if (texture == null || texture.isBlank() || texture.equals("-")) {
            return DEFAULT_WALL;
        }
        String t = texture.trim().toUpperCase(Locale.ROOT);

        // Doom tech/support textures -> clean tech blocks
        if (t.startsWith("SUPPORT") || t.contains("SUPPORT")) {
            return Blocks.POLISHED_ANDESITE.getDefaultState();
        }

        // E1 techbase vibe: STARTAN/STARGR/TEKWALL/etc.
        if (t.startsWith("STARTAN") || t.startsWith("STARGR") || t.startsWith("STARG")) {
            return Blocks.POLISHED_ANDESITE.getDefaultState();
        }

        // Tech panels and computers -> clean gray tech
        if (t.contains("TEKWALL") || t.contains("COMPUTE") || t.contains("COMP") || t.contains("PANEL")) {
            return Blocks.LIGHT_GRAY_CONCRETE.getDefaultState();
        }

        // Doors -> iron
        if (t.startsWith("DOOR") || t.contains("DOOR")) {
            return Blocks.IRON_BLOCK.getDefaultState();
        }

        // Metal and pipes -> iron
        if (t.contains("METAL") || t.contains("PIPE")) {
            return Blocks.IRON_BLOCK.getDefaultState();
        }

        // Bricks and stone -> stone bricks and deepslate bricks
        if (t.contains("BRICK")) {
            if (t.contains("DARK")) {
                return Blocks.DEEPSLATE_BRICKS.getDefaultState();
            }
            return Blocks.STONE_BRICKS.getDefaultState();
        }

        // Stone textures -> polished basalt or deepslate bricks
        if (t.contains("STONE") || t.startsWith("GRAY")) {
            if (t.contains("ROUGH") || t.contains("DARK")) {
                return Blocks.DEEPSLATE_TILES.getDefaultState();
            }
            return Blocks.POLISHED_BASALT.getDefaultState();
        }

        // Brown terracotta
        if (t.contains("BROWN")) {
            return Blocks.BROWN_TERRACOTTA.getDefaultState();
        }

        // Pillars and columns -> try to differentiate
        if (t.contains("PILLAR") || t.contains("COLUMN")) {
            if (t.contains("DARK")) {
                return Blocks.POLISHED_DEEPSLATE.getDefaultState();
            }
            return Blocks.SMOOTH_QUARTZ.getDefaultState();
        }

        // Walls -> use stone bricks for good Doom feel
        if (t.contains("WALL")) {
            if (t.contains("DARK") || t.contains("GRAY") || t.contains("SLATE")) {
                return Blocks.DEEPSLATE_BRICKS.getDefaultState();
            }
            return Blocks.STONE_BRICKS.getDefaultState();
        }

        return DEFAULT_WALL;
    }

    public static BlockState mapFloor(String flat) {
        if (flat == null || flat.isBlank() || flat.equals("-")) {
            return DEFAULT_FLOOR;
        }
        String t = flat.trim().toUpperCase(Locale.ROOT);

        // Tech/metallic lift floors -> consistent lift metal look
        if (t.contains("LIFTTECH") || t.contains("METALFLR") || t.contains("TECHFLR")) {
            return Blocks.IRON_BLOCK.getDefaultState();
        }

        // Teleport gates
        if (t.contains("GATE") || t.contains("TELE")) {
            return Blocks.CYAN_CONCRETE.getDefaultState();
        }

        // Nukage / slime pools
        if (t.contains("NUKAGE") || t.contains("SLIME")) {
            return Blocks.LIME_CONCRETE.getDefaultState();
        }

        // Industrial floors with tech vibe
        if (t.startsWith("FLOOR") || t.startsWith("FLAT") || t.startsWith("STEP")) {
            return Blocks.DEEPSLATE_TILES.getDefaultState();
        }

        // Metal grates and grids -> darker floor look
        if (t.contains("GRATE") || t.contains("GRN") || t.contains("GRID")) {
            return Blocks.POLISHED_BLACKSTONE.getDefaultState();
        }

        // Dark/slate floors
        if (t.contains("DARK") || t.contains("SLATE") || t.contains("DM")) {
            return Blocks.DEEPSLATE_TILES.getDefaultState();
        }

        // Default industrial Doom-ish look
        return DEFAULT_FLOOR;
    }

    public static BlockState mapCeiling(String flat) {
        if (flat == null || flat.isBlank() || flat.equals("-")) {
            return DEFAULT_CEILING;
        }
        String t = flat.trim().toUpperCase(Locale.ROOT);

        // Standard ceiling textures
        if (t.startsWith("CEIL") || t.contains("CEIL")) {
            if (t.contains("DARK") || t.contains("SLATE")) {
                return Blocks.POLISHED_DEEPSLATE.getDefaultState();
            }
            return Blocks.SMOOTH_STONE.getDefaultState();
        }

        // Dark/tech ceilings
        if (t.contains("DARK") || t.contains("TECH")) {
            return Blocks.POLISHED_DEEPSLATE.getDefaultState();
        }

        // Default industrial Doom ceiling
        return DEFAULT_CEILING;
    }

    // Backwards-compatible alias for existing callsites.
    public static BlockState map(String texture) {
        return mapWall(texture);
    }
}
