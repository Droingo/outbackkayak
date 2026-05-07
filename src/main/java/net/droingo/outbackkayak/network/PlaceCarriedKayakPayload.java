package net.droingo.outbackkayak.network;

import net.droingo.outbackkayak.OutbackKayak;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record PlaceCarriedKayakPayload() implements CustomPayload {
    public static final Id<PlaceCarriedKayakPayload> ID = new Id<>(
            Identifier.of(OutbackKayak.MOD_ID, "place_carried_kayak")
    );

    public static final PacketCodec<RegistryByteBuf, PlaceCarriedKayakPayload> CODEC =
            PacketCodec.unit(new PlaceCarriedKayakPayload());

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}