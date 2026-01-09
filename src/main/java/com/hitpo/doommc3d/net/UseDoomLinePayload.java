package com.hitpo.doommc3d.net;

import com.hitpo.doommc3d.DoomMC3D;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * Client request to "use" a Doom special line in front of the player.
 *
 * We keep this payload empty and compute the ray on the server using the player's
 * current eye position and rotation.
 */
public record UseDoomLinePayload() implements CustomPayload {
    public static final Id<UseDoomLinePayload> ID = new Id<>(Identifier.of(DoomMC3D.MOD_ID, "use_doom_line"));
    public static final PacketCodec<RegistryByteBuf, UseDoomLinePayload> CODEC = PacketCodec.unit(new UseDoomLinePayload());

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
