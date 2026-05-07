package net.droingo.outbackkayak.item;

import net.droingo.outbackkayak.entity.KayakEntity;
import net.droingo.outbackkayak.registry.ModEntities;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.fluid.Fluids;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.stat.Stats;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;

public class KayakItem extends Item {
    private static final double PLACE_REACH = 5.0;
    private static final double WATER_RAY_STEP = 0.15;
    private static final double WATER_PLACE_Y_OFFSET = 0.35;

    public KayakItem(Settings settings) {
        super(settings);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);

        BlockPos waterPos = findWaterBlockAlongLookRay(user, world);

        if (waterPos == null) {
            return TypedActionResult.pass(stack);
        }

        if (world.isClient()) {
            return TypedActionResult.success(stack);
        }

        placeKayakFromItem(user, world, stack, waterPos, true);

        return TypedActionResult.success(stack);
    }

    @Override
    public ActionResult useOnBlock(ItemUsageContext context) {
        World world = context.getWorld();

        PlayerEntity player = context.getPlayer();

        if (player == null) {
            return ActionResult.FAIL;
        }

        ItemStack stack = context.getStack();

        /*
         * Water first.
         * This catches clicking a water block/edge before falling back to solid block placement.
         */
        BlockPos waterPos = findWaterBlockAlongLookRay(player, world);

        if (waterPos != null) {
            if (!world.isClient()) {
                placeKayakFromItem(player, world, stack, waterPos, true);
            }

            return ActionResult.SUCCESS;
        }

        if (world.isClient()) {
            return ActionResult.SUCCESS;
        }

        BlockPos clickedPos = context.getBlockPos();
        BlockPos placePos = clickedPos.offset(context.getSide());

        if (world.getFluidState(clickedPos).isIn(FluidTags.WATER)) {
            placePos = clickedPos;
        }

        placeKayakFromItem(player, world, stack, placePos, world.getFluidState(placePos).isOf(Fluids.WATER));

        return ActionResult.SUCCESS;
    }

    @Override
    public void inventoryTick(ItemStack stack, World world, net.minecraft.entity.Entity entity, int slot, boolean selected) {
        if (world.isClient()) {
            return;
        }

        if (!(entity instanceof LivingEntity livingEntity)) {
            return;
        }

        if (!livingEntity.getEquippedStack(EquipmentSlot.HEAD).isOf(this)) {
            return;
        }

        livingEntity.addStatusEffect(new net.minecraft.entity.effect.StatusEffectInstance(
                net.minecraft.entity.effect.StatusEffects.SLOWNESS,
                40,
                1,
                true,
                false,
                true
        ));
    }

    private static void placeKayakFromItem(
            PlayerEntity player,
            World world,
            ItemStack stack,
            BlockPos placePos,
            boolean placingInWater
    ) {
        if (!(world instanceof ServerWorld)) {
            return;
        }

        double y = placingInWater
                ? placePos.getY() + WATER_PLACE_Y_OFFSET
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

        if (!player.getAbilities().creativeMode) {
            stack.decrement(1);
        }

        player.incrementStat(Stats.USED.getOrCreateStat(stack.getItem()));
    }

    private static BlockPos findWaterBlockAlongLookRay(PlayerEntity player, World world) {
        Vec3d start = player.getCameraPosVec(1.0f);
        Vec3d direction = player.getRotationVec(1.0f);

        for (double distance = 0.0; distance <= PLACE_REACH; distance += WATER_RAY_STEP) {
            Vec3d sample = start.add(direction.multiply(distance));
            BlockPos pos = BlockPos.ofFloored(sample);

            if (world.getFluidState(pos).isOf(Fluids.WATER)) {
                return pos;
            }
        }

        return null;
    }
}