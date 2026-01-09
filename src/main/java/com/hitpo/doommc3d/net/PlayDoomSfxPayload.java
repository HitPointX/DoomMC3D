package com.hitpo.doommc3d.net;

import com.hitpo.doommc3d.DoomMC3D;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketDecoder;
import net.minecraft.network.codec.ValueFirstEncoder;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record PlayDoomSfxPayload(String lumpName, double x, double y, double z, float volume, float pitch) implements CustomPayload {
    public static final Id<PlayDoomSfxPayload> ID = new Id<>(Identifier.of(DoomMC3D.MOD_ID, "play_doom_sfx"));

    public static final PacketCodec<RegistryByteBuf, PlayDoomSfxPayload> CODEC = CustomPayload.codecOf(
        (ValueFirstEncoder<RegistryByteBuf, PlayDoomSfxPayload>) (payload, buf) -> {
            buf.writeString(payload.lumpName(), 16);
            buf.writeDouble(payload.x());
            buf.writeDouble(payload.y());
            buf.writeDouble(payload.z());
            buf.writeFloat(payload.volume());
            buf.writeFloat(payload.pitch());
        },
        (PacketDecoder<RegistryByteBuf, PlayDoomSfxPayload>) buf -> new PlayDoomSfxPayload(
            buf.readString(16),
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
