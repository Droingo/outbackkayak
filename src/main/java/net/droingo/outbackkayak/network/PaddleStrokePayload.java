package net.droingo.outbackkayak.network;

import net.droingo.outbackkayak.OutbackKayak;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record PaddleStrokePayload(int side, int direction) implements CustomPayload {
    public static final int SIDE_LEFT = -1;
    public static final int SIDE_RIGHT = 1;

    public static final int DIRECTION_FORWARD = 1;
    public static final int DIRECTION_BACKWARD = -1;
    public static final int DIRECTION_RUDDER = 0;

    public static final Id<PaddleStrokePayload> ID = new Id<>(
            Identifier.of(OutbackKayak.MOD_ID, "paddle_stroke")
    );

    public static final PacketCodec<RegistryByteBuf, PaddleStrokePayload> CODEC = PacketCodec.tuple(
            PacketCodecs.INTEGER,
            PaddleStrokePayload::side,
            PacketCodecs.INTEGER,
            PaddleStrokePayload::direction,
            PaddleStrokePayload::new
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}