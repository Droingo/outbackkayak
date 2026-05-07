package net.droingo.outbackkayak.client;

import net.droingo.outbackkayak.entity.KayakEntity;
import net.droingo.outbackkayak.network.PaddleStrokePayload;
import net.droingo.outbackkayak.registry.ModItems;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;

public final class ModKeybinds {
    private static final int RUDDER_REPEAT_TICKS = 2;

    private static KeyBinding paddleStrokeKey;
    private static boolean wasPaddleInputDown;
    private static int rudderRepeatCooldown;

    private ModKeybinds() {
    }

    public static void register() {
        paddleStrokeKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.outbackkayak.paddle_stroke",
                InputUtil.Type.MOUSE,
                GLFW.GLFW_MOUSE_BUTTON_LEFT,
                "category.outbackkayak.kayaking"
        ));

        ClientTickEvents.END_CLIENT_TICK.register(ModKeybinds::handleClientTick);
    }

    private static void handleClientTick(MinecraftClient client) {
        boolean paddleInputDown = isPaddleInputDown(client);

        if (rudderRepeatCooldown > 0) {
            rudderRepeatCooldown--;
        }

        if (!paddleInputDown) {
            wasPaddleInputDown = false;
            rudderRepeatCooldown = 0;
            return;
        }

        if (!isValidKayakPaddleContext(client)) {
            wasPaddleInputDown = paddleInputDown;
            return;
        }

        boolean pressingForward = client.options.forwardKey.isPressed();
        boolean pressingBack = client.options.backKey.isPressed();

        if (pressingForward && pressingBack) {
            wasPaddleInputDown = paddleInputDown;
            return;
        }

        boolean isRudderInput = !pressingForward && !pressingBack;

        if (isRudderInput) {
            if (rudderRepeatCooldown <= 0) {
                sendStrokeIfValid(client, PaddleStrokePayload.DIRECTION_RUDDER);
                rudderRepeatCooldown = RUDDER_REPEAT_TICKS;
            }
        } else if (!wasPaddleInputDown) {
            int direction = pressingForward
                    ? PaddleStrokePayload.DIRECTION_FORWARD
                    : PaddleStrokePayload.DIRECTION_BACKWARD;

            sendStrokeIfValid(client, direction);
        }

        wasPaddleInputDown = paddleInputDown;
    }

    private static boolean isPaddleInputDown(MinecraftClient client) {
        if (paddleStrokeKey != null && paddleStrokeKey.isPressed()) {
            return true;
        }

        return client.options.attackKey.isPressed();
    }

    private static boolean isValidKayakPaddleContext(MinecraftClient client) {
        if (client.player == null) {
            return false;
        }

        if (!(client.player.getVehicle() instanceof KayakEntity)) {
            return false;
        }

        return client.player.getMainHandStack().isOf(ModItems.PADDLE)
                || client.player.getOffHandStack().isOf(ModItems.PADDLE);
    }

    private static void sendStrokeIfValid(MinecraftClient client, int direction) {
        if (client.player == null) {
            return;
        }

        if (!(client.player.getVehicle() instanceof KayakEntity kayak)) {
            return;
        }

        int side = getLookSide(client, kayak);

        if (ClientPlayNetworking.canSend(PaddleStrokePayload.ID)) {
            ClientPlayNetworking.send(new PaddleStrokePayload(side, direction));
        }
    }

    private static int getLookSide(MinecraftClient client, KayakEntity kayak) {
        float playerYaw = client.player.getYaw();
        float kayakYaw = kayak.getYaw();

        float yawDifference = MathHelper.wrapDegrees(playerYaw - kayakYaw);

        if (yawDifference < 0.0f) {
            return PaddleStrokePayload.SIDE_LEFT;
        }

        return PaddleStrokePayload.SIDE_RIGHT;
    }
}