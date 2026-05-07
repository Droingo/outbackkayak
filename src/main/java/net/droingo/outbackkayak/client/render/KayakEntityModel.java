package net.droingo.outbackkayak.client.render;

import net.droingo.outbackkayak.OutbackKayak;
import net.droingo.outbackkayak.entity.KayakEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.GeoModel;

public class KayakEntityModel extends GeoModel<KayakEntity> {
    private static final String ROOT_BONE = "kayak_root";

    @Override
    public Identifier getModelResource(KayakEntity animatable) {
        return Identifier.of(OutbackKayak.MOD_ID, "geo/kayak.geo.json");
    }

    @Override
    public Identifier getTextureResource(KayakEntity animatable) {
        return Identifier.of(OutbackKayak.MOD_ID, "textures/entity/kayak.png");
    }

    @Override
    public Identifier getAnimationResource(KayakEntity animatable) {
        return Identifier.of(OutbackKayak.MOD_ID, "animations/kayak.animation.json");
    }

    @Override
    public void setCustomAnimations(
            KayakEntity animatable,
            long instanceId,
            AnimationState<KayakEntity> animationState
    ) {
        super.setCustomAnimations(animatable, instanceId, animationState);

        GeoBone rootBone = this.getAnimationProcessor().getBone(ROOT_BONE);

        if (rootBone == null) {
            return;
        }

        /*
         * Smooth render-frame yaw.
         *
         * animatable.getYaw() updates at game tick rate.
         * Interpolating prevYaw -> getYaw using GeckoLib's partial tick removes
         * the visible stepping/ticking from the visual model rotation.
         */
        float partialTick = animationState.getPartialTick();
        float renderYaw = MathHelper.lerpAngleDegrees(partialTick, animatable.prevYaw, animatable.getYaw());

        /*
         * Rotate only the GeckoLib kayak model, not the whole renderer pose stack.
         * This keeps vanilla leash rendering stable.
         */
        rootBone.setRotY((float) Math.toRadians(-renderYaw));
    }
}