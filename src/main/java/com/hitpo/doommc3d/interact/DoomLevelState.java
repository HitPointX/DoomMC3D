package com.hitpo.doommc3d.interact;

import net.minecraft.util.math.BlockPos;

public record DoomLevelState(String mapName, String wadFileName, BlockPos buildOrigin) {
}
