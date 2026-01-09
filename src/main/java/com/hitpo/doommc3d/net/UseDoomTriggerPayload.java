package com.hitpo.doommc3d.net;

import com.hitpo.doommc3d.DoomMC3D;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketDecoder;
import net.minecraft.network.codec.ValueFirstEncoder;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

public record UseDoomTriggerPayload(BlockPos pos) implements CustomPayload {
    public static final Id<UseDoomTriggerPayload> ID = new Id<>(Identifier.of(DoomMC3D.MOD_ID, "use_doom_trigger"));

    public static final PacketCodec<RegistryByteBuf, UseDoomTriggerPayload> CODEC = CustomPayload.codecOf(
        (ValueFirstEncoder<RegistryByteBuf, UseDoomTriggerPayload>) (payload, buf) -> buf.writeBlockPos(payload.pos()),
        (PacketDecoder<RegistryByteBuf, UseDoomTriggerPayload>) buf -> new UseDoomTriggerPayload(buf.readBlockPos())
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
