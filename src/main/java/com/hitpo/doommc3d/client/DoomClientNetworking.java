package com.hitpo.doommc3d.client;

import com.hitpo.doommc3d.client.audio.DoomMusicPlayer;
import com.hitpo.doommc3d.client.audio.DoomSfxPlayer;
import com.hitpo.doommc3d.net.PlayMusicPayload;
import com.hitpo.doommc3d.net.PlayDoomSfxPayload;
import com.hitpo.doommc3d.net.WeaponFiredPayload;
import com.hitpo.doommc3d.client.weapon.DoomWeaponClientAnim;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

public final class DoomClientNetworking {
    private DoomClientNetworking() {
    }

    public static void init() {
        ClientPlayNetworking.registerGlobalReceiver(PlayMusicPayload.ID, (payload, context) -> {
            context.client().execute(() -> DoomMusicPlayer.playForMap(payload.mapName()));
        });

        ClientPlayNetworking.registerGlobalReceiver(PlayDoomSfxPayload.ID, (payload, context) -> {
            context.client().execute(() -> DoomSfxPlayer.playAt(payload.lumpName(), payload.x(), payload.y(), payload.z(), payload.volume(), payload.pitch()));
        });

        ClientPlayNetworking.registerGlobalReceiver(WeaponFiredPayload.ID, (payload, context) -> {
            context.client().execute(() -> DoomWeaponClientAnim.onLocalFire(context.client()));
        });
    }
}

