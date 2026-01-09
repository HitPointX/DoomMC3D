package com.hitpo.doommc3d.entity;

import com.hitpo.doommc3d.DoomMC3D;
import com.hitpo.doommc3d.entity.projectile.DoomBfgBallEntity;
import com.hitpo.doommc3d.entity.projectile.DoomPlasmaEntity;
import com.hitpo.doommc3d.entity.projectile.DoomRocketEntity;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityTypeBuilder;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;

public final class ModEntities {
    private ModEntities() {
    }

    private static final Identifier DOOM_BFG_BALL_ID = Identifier.of(DoomMC3D.MOD_ID, "doom_bfg_ball");
    private static final RegistryKey<EntityType<?>> DOOM_BFG_BALL_KEY = RegistryKey.of(RegistryKeys.ENTITY_TYPE, DOOM_BFG_BALL_ID);

    public static final EntityType<DoomBfgBallEntity> DOOM_BFG_BALL = Registry.register(
        Registries.ENTITY_TYPE,
        DOOM_BFG_BALL_ID,
        FabricEntityTypeBuilder.<DoomBfgBallEntity>create(SpawnGroup.MISC, DoomBfgBallEntity::new)
            .dimensions(EntityDimensions.fixed(0.25f, 0.25f))
            .trackRangeBlocks(64)
            .trackedUpdateRate(10)
            .build(DOOM_BFG_BALL_KEY)
    );

    private static final Identifier DOOM_ROCKET_ID = Identifier.of(DoomMC3D.MOD_ID, "doom_rocket");
    private static final RegistryKey<EntityType<?>> DOOM_ROCKET_KEY = RegistryKey.of(RegistryKeys.ENTITY_TYPE, DOOM_ROCKET_ID);

    public static final EntityType<DoomRocketEntity> DOOM_ROCKET = Registry.register(
        Registries.ENTITY_TYPE,
        DOOM_ROCKET_ID,
        FabricEntityTypeBuilder.<DoomRocketEntity>create(SpawnGroup.MISC, DoomRocketEntity::new)
            .dimensions(EntityDimensions.fixed(0.25f, 0.25f))
            .trackRangeBlocks(96)
            .trackedUpdateRate(10)
            .build(DOOM_ROCKET_KEY)
    );

    private static final Identifier DOOM_PLASMA_ID = Identifier.of(DoomMC3D.MOD_ID, "doom_plasma");
    private static final RegistryKey<EntityType<?>> DOOM_PLASMA_KEY = RegistryKey.of(RegistryKeys.ENTITY_TYPE, DOOM_PLASMA_ID);

    public static final EntityType<DoomPlasmaEntity> DOOM_PLASMA = Registry.register(
        Registries.ENTITY_TYPE,
        DOOM_PLASMA_ID,
        FabricEntityTypeBuilder.<DoomPlasmaEntity>create(SpawnGroup.MISC, DoomPlasmaEntity::new)
            .dimensions(EntityDimensions.fixed(0.25f, 0.25f))
            .trackRangeBlocks(96)
            .trackedUpdateRate(10)
            .build(DOOM_PLASMA_KEY)
    );

    private static final Identifier DOOM_LIFT_PLATFORM_ID = Identifier.of(DoomMC3D.MOD_ID, "lift_platform");
    private static final RegistryKey<EntityType<?>> DOOM_LIFT_PLATFORM_KEY = RegistryKey.of(RegistryKeys.ENTITY_TYPE, DOOM_LIFT_PLATFORM_ID);

    public static final EntityType<com.hitpo.doommc3d.entity.LiftPlatformEntity> LIFT_PLATFORM = Registry.register(
        Registries.ENTITY_TYPE,
        DOOM_LIFT_PLATFORM_ID,
        FabricEntityTypeBuilder.<com.hitpo.doommc3d.entity.LiftPlatformEntity>create(SpawnGroup.MISC, com.hitpo.doommc3d.entity.LiftPlatformEntity::new)
            .dimensions(EntityDimensions.fixed(1.0f, 0.25f))
            .trackRangeBlocks(64)
            .trackedUpdateRate(10)
            .build(DOOM_LIFT_PLATFORM_KEY)
    );


    public static void init() {
        // Force class-load.
    }
}
