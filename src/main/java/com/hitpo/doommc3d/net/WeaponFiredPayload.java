package com.hitpo.doommc3d.net;

import com.hitpo.doommc3d.DoomMC3D;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketDecoder;
import net.minecraft.network.codec.ValueFirstEncoder;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record WeaponFiredPayload() implements CustomPayload {
    public static final Id<WeaponFiredPayload> ID = new Id<>(Identifier.of(DoomMC3D.MOD_ID, "weapon_fired"));

    public static final PacketCodec<RegistryByteBuf, WeaponFiredPayload> CODEC = CustomPayload.codecOf(
        (ValueFirstEncoder<RegistryByteBuf, WeaponFiredPayload>) (payload, buf) -> {
            // no data
        },
        (PacketDecoder<RegistryByteBuf, WeaponFiredPayload>) buf -> new WeaponFiredPayload()
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
