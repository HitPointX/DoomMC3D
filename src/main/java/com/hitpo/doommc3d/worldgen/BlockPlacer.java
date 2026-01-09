package com.hitpo.doommc3d.worldgen;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

public class BlockPlacer {
    private final ServerWorld world;
    private final BlockPos origin;

    public BlockPlacer(ServerWorld world, BlockPos origin) {
        this.world = world;
        this.origin = origin;
    }

    public void placeDefault(int x, int y, int z) {
        placeBlock(x, y, z, Blocks.STONE.getDefaultState());
    }

    public void placeBlock(int x, int y, int z, BlockState state) {
        BlockPos target = origin.add(x, y, z);
        world.setBlockState(target, state, 3);
    }

    public void placeColumn(int x, int z, int startY, int endY, BlockState state) {
        int bottom = Math.min(startY, endY);
        int top = Math.max(startY, endY);
        for (int y = bottom; y <= top; y++) {
            placeBlock(x, y, z, state);
        }
    }
}
