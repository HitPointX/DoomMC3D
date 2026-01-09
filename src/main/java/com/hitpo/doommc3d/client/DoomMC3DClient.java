package com.hitpo.doommc3d.client;

import com.hitpo.doommc3d.client.hud.DoomHudRenderer;
import com.hitpo.doommc3d.client.audio.DoomSfxPlayer;
import com.hitpo.doommc3d.client.weapon.DoomWeaponClientAnim;
import com.hitpo.doommc3d.client.weapon.DoomWeaponClientInput;
import com.hitpo.doommc3d.entity.ModEntities;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.render.entity.FlyingItemEntityRenderer;
import net.minecraft.sound.SoundCategory;

public class DoomMC3DClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        HudRenderCallback.EVENT.register(new DoomHudRenderer());
        DoomWeaponClientAnim.register();
        DoomWeaponClientInput.register();
        DoomSfxPlayer.register();
        DoomClientNetworking.init();

        // Prevent crashes when custom projectile entities spawn (must have renderers on client).
        EntityRendererRegistry.register(ModEntities.DOOM_ROCKET, FlyingItemEntityRenderer::new);
        EntityRendererRegistry.register(ModEntities.DOOM_PLASMA, FlyingItemEntityRenderer::new);
        EntityRendererRegistry.register(ModEntities.DOOM_BFG_BALL, FlyingItemEntityRenderer::new);

        // Kill vanilla music when entering a world so Doom music can own the soundtrack.
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            if (client.getMusicTracker() != null) {
                client.getMusicTracker().stop();
            }

            var musicOption = client.options.getSoundVolumeOption(SoundCategory.MUSIC);
            if (musicOption != null && musicOption.getValue() > 0.0) {
                musicOption.setValue(0.0);
                client.options.write();
            }
        });
    }
}
