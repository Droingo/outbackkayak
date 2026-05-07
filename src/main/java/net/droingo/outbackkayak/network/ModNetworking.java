package net.droingo.outbackkayak.network;

import net.droingo.outbackkayak.entity.KayakEntity;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.Entity;

public final class ModNetworking {
    private ModNetworking() {
    }

    public static void register() {
        PayloadTypeRegistry.playC2S().register(PaddleStrokePayload.ID, PaddleStrokePayload.CODEC);

        ServerPlayNetworking.registerGlobalReceiver(PaddleStrokePayload.ID, (payload, context) -> {
            context.server().execute(() -> {
                Entity vehicle = context.player().getVehicle();

                if (vehicle instanceof KayakEntity kayak) {
                    kayak.applyPaddleStroke(context.player(), payload.side(), payload.direction());
                }
            });
        });
    }
}