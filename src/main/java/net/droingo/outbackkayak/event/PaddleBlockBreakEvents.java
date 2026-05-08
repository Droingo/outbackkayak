package net.droingo.outbackkayak.event;

import net.droingo.outbackkayak.entity.KayakEntity;
import net.droingo.outbackkayak.registry.ModItems;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;

public final class PaddleBlockBreakEvents {
    private PaddleBlockBreakEvents() {
    }

    public static void register() {
        AttackBlockCallback.EVENT.register((player, world, hand, pos, direction) -> {
            if (!(player.getVehicle() instanceof KayakEntity)) {
                return ActionResult.PASS;
            }

            ItemStack stack = player.getStackInHand(hand);

            if (!stack.isOf(ModItems.PADDLE)) {
                return ActionResult.PASS;
            }

            /*
             * While kayaking, left-click with the paddle is for paddling,
             * not mining. Returning FAIL cancels the block attack.
             */
            return ActionResult.FAIL;
        });
    }
}