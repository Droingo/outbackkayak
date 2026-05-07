package net.droingo.outbackkayak.item;

import net.droingo.outbackkayak.entity.KayakEntity;
import net.droingo.outbackkayak.registry.ModEntities;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.stat.Stats;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;

import java.util.List;
import java.util.function.Predicate;

public class KayakItem extends Item {
    private static final Predicate<Entity> RIDERS = Entity::canHit;

    public KayakItem(Settings settings) {
        super(settings);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        HitResult hitResult = Item.raycast(world, user, RaycastContext.FluidHandling.ANY);

        if (hitResult.getType() == HitResult.Type.MISS) {
            return TypedActionResult.pass(stack);
        }

        List<Entity> nearbyEntities = world.getOtherEntities(
                user,
                user.getBoundingBox()
                        .stretch(user.getRotationVec(1.0f).multiply(5.0))
                        .expand(1.0),
                RIDERS
        );

        if (!nearbyEntities.isEmpty()) {
            return TypedActionResult.pass(stack);
        }

        if (hitResult.getType() == HitResult.Type.BLOCK) {
            KayakEntity kayak = new KayakEntity(ModEntities.KAYAK, world);
            kayak.setPosition(hitResult.getPos().x, hitResult.getPos().y, hitResult.getPos().z);
            kayak.setYaw(user.getYaw());

            if (!world.isSpaceEmpty(kayak, kayak.getBoundingBox())) {
                return TypedActionResult.fail(stack);
            }

            if (!world.isClient()) {
                world.spawnEntity(kayak);

                if (!user.getAbilities().creativeMode) {
                    stack.decrement(1);
                }

                user.incrementStat(Stats.USED.getOrCreateStat(this));
            }

            return TypedActionResult.success(stack, world.isClient());
        }

        return TypedActionResult.pass(stack);
    }
}