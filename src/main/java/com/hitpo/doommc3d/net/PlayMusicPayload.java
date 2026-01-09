package com.hitpo.doommc3d.net;

import com.hitpo.doommc3d.DoomMC3D;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketDecoder;
import net.minecraft.network.codec.ValueFirstEncoder;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record PlayMusicPayload(String mapName) implements CustomPayload {
    public static final Id<PlayMusicPayload> ID = new Id<>(Identifier.of(DoomMC3D.MOD_ID, "play_music"));
    public static final PacketCodec<RegistryByteBuf, PlayMusicPayload> CODEC = CustomPayload.codecOf(
        (ValueFirstEncoder<RegistryByteBuf, PlayMusicPayload>) (payload, buf) -> buf.writeString(payload.mapName(), 32),
        (PacketDecoder<RegistryByteBuf, PlayMusicPayload>) buf -> new PlayMusicPayload(buf.readString(32))
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
