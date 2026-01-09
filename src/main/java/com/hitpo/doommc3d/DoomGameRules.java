package com.hitpo.doommc3d;

import net.fabricmc.fabric.api.gamerule.v1.GameRuleBuilder;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.world.rule.GameRule;
import net.minecraft.world.rule.GameRuleCategory;

public final class DoomGameRules {
    public static final GameRule<Boolean> DOOM_DM_WEAPONS = GameRuleBuilder
        .forBoolean(false)
        .category(GameRuleCategory.MISC)
        .buildAndRegister(Identifier.of(DoomConstants.MOD_ID, "doomdmweapons"));

    private DoomGameRules() {
    }

    public static boolean allowDeathmatchWeapons(ServerWorld world) {
        return world.getGameRules().getValue(DOOM_DM_WEAPONS);
    }

    public static void init() {
        // Forces class loading so the gamerule gets registered during mod init.
    }
}
