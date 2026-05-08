package net.droingo.outbackkayak.client;

import net.droingo.outbackkayak.registry.ModItems;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.EquipmentSlot;

public final class CarryLookSlowdown {
    private static final double CARRY_MOUSE_SENSITIVITY_MULTIPLIER = 0.65;

    private static Double originalMouseSensitivity;
    private static Boolean originalSmoothCameraEnabled;

    private CarryLookSlowdown() {
    }

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(CarryLookSlowdown::onClientTick);
        ClientLifecycleEvents.CLIENT_STOPPING.register(CarryLookSlowdown::restoreCarryCameraSettings);
    }

    private static void onClientTick(MinecraftClient client) {
        boolean shouldUseCarryCamera = shouldUseCarryCamera(client);

        if (shouldUseCarryCamera) {
            applyCarryCameraSettings(client);
        } else {
            restoreCarryCameraSettings(client);
        }
    }

    private static boolean shouldUseCarryCamera(MinecraftClient client) {
        if (client.player == null) {
            return false;
        }

        if (client.currentScreen != null) {
            return false;
        }

        return client.player
                .getEquippedStack(EquipmentSlot.HEAD)
                .isOf(ModItems.KAYAK);
    }

    private static void applyCarryCameraSettings(MinecraftClient client) {
        if (originalMouseSensitivity == null) {
            double currentSensitivity = client.options.getMouseSensitivity().getValue();
            originalMouseSensitivity = currentSensitivity;

            client.options.getMouseSensitivity().setValue(
                    currentSensitivity * CARRY_MOUSE_SENSITIVITY_MULTIPLIER
            );
        }

        if (originalSmoothCameraEnabled == null) {
            boolean currentSmoothCamera = client.options.smoothCameraEnabled;
            originalSmoothCameraEnabled = currentSmoothCamera;

            client.options.smoothCameraEnabled = true;
        }
    }

    private static void restoreCarryCameraSettings(MinecraftClient client) {
        if (originalMouseSensitivity != null) {
            client.options.getMouseSensitivity().setValue(originalMouseSensitivity);
            originalMouseSensitivity = null;
        }

        if (originalSmoothCameraEnabled != null) {
            client.options.smoothCameraEnabled = originalSmoothCameraEnabled;
            originalSmoothCameraEnabled = null;
        }
    }
}