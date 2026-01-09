package com.hitpo.doommc3d.net;

import com.hitpo.doommc3d.DoomMC3D;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record FireWeaponPayload() implements CustomPayload {
    public static final Id<FireWeaponPayload> ID = new Id<>(Identifier.of(DoomMC3D.MOD_ID, "fire_weapon"));
    public static final PacketCodec<RegistryByteBuf, FireWeaponPayload> CODEC = PacketCodec.unit(new FireWeaponPayload());

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}

