package com.hitpo.doommc3d.net;

import com.hitpo.doommc3d.DoomMC3D;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketDecoder;
import net.minecraft.network.codec.ValueFirstEncoder;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record PickupPayload(String message, int color, int durationTicks) implements CustomPayload {
    public static final Id<PickupPayload> ID = new Id<>(Identifier.of(DoomMC3D.MOD_ID, "pickup_message"));

    public static final PacketCodec<RegistryByteBuf, PickupPayload> CODEC = CustomPayload.codecOf(
        (ValueFirstEncoder<RegistryByteBuf, PickupPayload>) (payload, buf) -> {
            buf.writeString(payload.message(), 256);
            buf.writeInt(payload.color());
            buf.writeInt(payload.durationTicks());
        },
        (PacketDecoder<RegistryByteBuf, PickupPayload>) buf -> new PickupPayload(
            buf.readString(256),
            buf.readInt(),
            buf.readInt()
        )
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
