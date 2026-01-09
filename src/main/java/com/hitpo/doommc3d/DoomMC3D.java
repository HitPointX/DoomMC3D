package com.hitpo.doommc3d;

import net.fabricmc.api.ModInitializer;
import com.hitpo.doommc3d.command.DoomGenCommand;
import com.hitpo.doommc3d.command.DoomGiveAllCommand;
import com.hitpo.doommc3d.wad.WadLoader;
import com.hitpo.doommc3d.interact.DoomDoorInteractions;
import com.hitpo.doommc3d.interact.DoomSwitchInteractions;
import com.hitpo.doommc3d.interact.DoomTriggerInteractions;
import com.hitpo.doommc3d.interact.DoomScheduler;
import com.hitpo.doommc3d.interact.DoomLiftSystem;
import com.hitpo.doommc3d.interact.DoomWalkTriggerSystem;
import com.hitpo.doommc3d.item.ModItems;
import com.hitpo.doommc3d.entity.ModEntities;
import com.hitpo.doommc3d.net.DoomNetworking;
import com.hitpo.doommc3d.sound.ModSounds;
import com.hitpo.doommc3d.worldgen.DoomCombatHooks;
import com.hitpo.doommc3d.worldgen.DoomKeySystem;
import com.hitpo.doommc3d.doomai.DoomBossSystem;
import com.hitpo.doommc3d.doomai.DoomMobSystem;
import com.hitpo.doommc3d.command.DoomBossCommand;
import com.hitpo.doommc3d.command.DoomMobCommand;
import com.hitpo.doommc3d.worldgen.DoomSecretSystem;
import com.hitpo.doommc3d.worldgen.DoomWeaponPickupSystem;
import com.hitpo.doommc3d.worldgen.DoomTeleporterSystem;
import com.hitpo.doommc3d.worldgen.DoomVanillaSpawnSuppressor;
import com.hitpo.doommc3d.worldgen.DoomPickupSystem;
import com.hitpo.doommc3d.state.DoomAutoLoader;

public class DoomMC3D implements ModInitializer {
    public static final String MOD_ID = "doommc3d";

    @Override
    public void onInitialize() {
        DoomGameRules.init();
        DoomAutoLoader.register();
        ModItems.init();
        ModEntities.init();
        // ModSounds.init();  // Disabled - using WAD sounds directly via DoomSfxPlayer
        DoomNetworking.init();
        DoomScheduler.register();
        DoomLiftSystem.register();
        DoomWalkTriggerSystem.register();
        DoomCombatHooks.register();
        DoomKeySystem.register();
        DoomPickupSystem.register();
        DoomSecretSystem.register();
        DoomTeleporterSystem.register();
        DoomWeaponPickupSystem.register();
        DoomVanillaSpawnSuppressor.register();
        DoomBossSystem.register();
        DoomMobSystem.register();
        com.hitpo.doommc3d.worldgen.DoomDeathWatcher.register();
        DoomGenCommand.register();
        DoomGiveAllCommand.register();
        DoomBossCommand.register();
        DoomMobCommand.register();
        com.hitpo.doommc3d.command.DoomDebugCommand.register();
        DoomDoorInteractions.register();
        DoomSwitchInteractions.register();
        DoomTriggerInteractions.register();
        try {
            java.nio.file.Files.createDirectories(WadLoader.getWadsDirectory());
        } catch (Exception e) {
            e.printStackTrace();
        }
        // Ensure a sample external corpse config exists for easier tuning by users
        try {
            java.nio.file.Path cfgDir = java.nio.file.Path.of("config");
            java.nio.file.Files.createDirectories(cfgDir);
            java.nio.file.Path cfgFile = cfgDir.resolve("doommc3d_corpse.properties");
            if (!java.nio.file.Files.exists(cfgFile)) {
                try (var in = DoomMC3D.class.getClassLoader().getResourceAsStream("doommc3d_corpse.properties")) {
                    if (in != null) java.nio.file.Files.copy(in, cfgFile);
                }
            }
        } catch (Exception e) {
            // non-fatal
        }
        com.hitpo.doommc3d.util.DebugLogger.debug("DoomMC3D.init", () -> "[DoomMC3D] Initialized");
    }
}
