package com.hitpo.doommc3d.net;

import com.hitpo.doommc3d.DoomMC3D;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketDecoder;
import net.minecraft.network.codec.ValueFirstEncoder;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * Network packet to play Doom sound effects on the client.
 * Similar to PlayMusicPayload but for SFX.
 */
public record PlaySoundPayload(
    String soundName,  // e.g., "PISTOL", "SHOTGN", "POPAIN"
    double x,
    double y,
    double z,
    float volume,
    float pitch
) implements CustomPayload {
    
    public static final Id<PlaySoundPayload> ID = new Id<>(Identifier.of(DoomMC3D.MOD_ID, "play_sound"));
    
    public static final PacketCodec<RegistryByteBuf, PlaySoundPayload> CODEC = CustomPayload.codecOf(
        (ValueFirstEncoder<RegistryByteBuf, PlaySoundPayload>) (payload, buf) -> {
            buf.writeString(payload.soundName(), 32);
            buf.writeDouble(payload.x());
            buf.writeDouble(payload.y());
            buf.writeDouble(payload.z());
            buf.writeFloat(payload.volume());
            buf.writeFloat(payload.pitch());
        },
        (PacketDecoder<RegistryByteBuf, PlaySoundPayload>) buf -> new PlaySoundPayload(
            buf.readString(32),
            buf.readDouble(),
            buf.readDouble(),
            buf.readDouble(),
            buf.readFloat(),
            buf.readFloat()
        )
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
