package net.droingo.outbackkayak.event;

import net.droingo.outbackkayak.entity.KayakEntity;
import net.droingo.outbackkayak.registry.ModEntities;
import net.droingo.outbackkayak.registry.ModItems;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.fluid.Fluids;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;

public final class ModInteractionEvents {
    private ModInteractionEvents() {
    }

    public static void register() {
        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (!canPlaceCarriedKayak(player, hand)) {
                return ActionResult.PASS;
            }

            if (world.isClient()) {
                return ActionResult.SUCCESS;
            }

            BlockPos placePos = getBlockPlacementPos(world, hitResult);

            placeCarriedKayak(player, world, placePos);

            return ActionResult.SUCCESS;
        });

        UseItemCallback.EVENT.register((player, world, hand) -> {
            if (!canPlaceCarriedKayak(player, hand)) {
                return TypedActionResult.pass(player.getStackInHand(hand));
            }

            BlockPos waterPos = findWaterBlockAlongLookRay(player, world);

            if (waterPos != null) {
                if (world.isClient()) {
                    return TypedActionResult.success(player.getStackInHand(hand));
                }

                placeCarriedKayak(player, world, waterPos);

                return TypedActionResult.success(player.getStackInHand(hand));
            }

            BlockHitResult hitResult = raycastForGround(player, world);

            if (hitResult.getType() == HitResult.Type.MISS) {
                return TypedActionResult.pass(player.getStackInHand(hand));
            }

            if (world.isClient()) {
                return TypedActionResult.success(player.getStackInHand(hand));
            }

            BlockPos placePos = getBlockPlacementPos(world, hitResult);

            placeCarriedKayak(player, world, placePos);

            return TypedActionResult.success(player.getStackInHand(hand));
        });
    }

    private static boolean canPlaceCarriedKayak(PlayerEntity player, Hand hand) {
        if (hand != Hand.MAIN_HAND) {
            return false;
        }

        if (!player.isSneaking()) {
            return false;
        }

        return player.getEquippedStack(EquipmentSlot.HEAD).isOf(ModItems.KAYAK);
    }

    private static BlockHitResult raycastForGround(PlayerEntity player, World world) {
        Vec3d start = player.getCameraPosVec(1.0f);
        Vec3d rotation = player.getRotationVec(1.0f);
        Vec3d end = start.add(rotation.multiply(5.0));

        RaycastContext context = new RaycastContext(
                start,
                end,
                RaycastContext.ShapeType.OUTLINE,
                RaycastContext.FluidHandling.NONE,
                player
        );

        return world.raycast(context);
    }

    private static BlockPos getBlockPlacementPos(World world, BlockHitResult hitResult) {
        BlockPos hitPos = hitResult.getBlockPos();

        /*
         * If the raycast hit water, place directly in that water block.
         * Do not offset by the hit side or it can jump to the bank/air.
         */
        if (world.getFluidState(hitPos).isOf(Fluids.WATER)) {
            return hitPos;
        }

        BlockPos adjacentPos = hitPos.offset(hitResult.getSide());

        /*
         * If the clicked block face points into water, place in that water too.
         * This helps when clicking the side/top edge of a water block.
         */
        if (world.getFluidState(adjacentPos).isOf(Fluids.WATER)) {
            return adjacentPos;
        }

        return adjacentPos;
    }

    private static void placeCarriedKayak(PlayerEntity player, World world, BlockPos placePos) {
        if (!(world instanceof ServerWorld)) {
            return;
        }

        ItemStack headStack = player.getEquippedStack(EquipmentSlot.HEAD);

        if (!headStack.isOf(ModItems.KAYAK)) {
            return;
        }

        boolean placingInWater = world.getFluidState(placePos).isOf(Fluids.WATER);

        double y = placingInWater
                ? placePos.getY() + 0.82
                : placePos.getY();

        Vec3d position = new Vec3d(
                placePos.getX() + 0.5,
                y,
                placePos.getZ() + 0.5
        );

        KayakEntity kayak = new KayakEntity(ModEntities.KAYAK, world);
        kayak.refreshPositionAndAngles(
                position.x,
                position.y,
                position.z,
                player.getYaw(),
                0.0f
        );

        world.spawnEntity(kayak);

        player.equipStack(EquipmentSlot.HEAD, ItemStack.EMPTY);
    }

    public static boolean tryPlaceCarriedKayakFromLook(PlayerEntity player, World world) {
        if (!player.isSneaking()) {
            return false;
        }

        if (!player.getEquippedStack(EquipmentSlot.HEAD).isOf(ModItems.KAYAK)) {
            return false;
        }

        /*
         * Water first.
         * Normal block raycasts often hit the solid riverbed below the water,
         * so we manually scan along the look ray for the first water block.
         */
        BlockPos waterPos = findWaterBlockAlongLookRay(player, world);

        if (waterPos != null) {
            placeCarriedKayak(player, world, waterPos);
            return true;
        }

        /*
         * Fallback to normal ground placement.
         */
        BlockHitResult hitResult = raycastForGround(player, world);

        if (hitResult.getType() == HitResult.Type.MISS) {
            return false;
        }

        BlockPos placePos = getBlockPlacementPos(world, hitResult);
        placeCarriedKayak(player, world, placePos);

        return true;
    }

    private static BlockPos findWaterBlockAlongLookRay(PlayerEntity player, World world) {
        Vec3d start = player.getCameraPosVec(1.0f);
        Vec3d direction = player.getRotationVec(1.0f);
        double reach = 5.0;

        /*
         * Small increments so shallow water and angled views are caught reliably.
         */
        double stepSize = 0.15;

        for (double distance = 0.0; distance <= reach; distance += stepSize) {
            Vec3d sample = start.add(direction.multiply(distance));
            BlockPos pos = BlockPos.ofFloored(sample);

            if (world.getFluidState(pos).isOf(Fluids.WATER)) {
                return pos;
            }
        }

        return null;
    }
}