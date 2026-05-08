package net.droingo.outbackkayak.client.render;

import net.droingo.outbackkayak.OutbackKayak;
import net.droingo.outbackkayak.entity.KayakEntity;
import net.droingo.outbackkayak.network.PaddleStrokePayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.GeoModel;

public class KayakEntityModel extends GeoModel<KayakEntity> {
    private static final String ROOT_BONE = "kayak_root";
    private static final String TILT_BONE = "kayak_tilt";

    private static final float PADDLE_ACTION_DURATION_TICKS = 8.0f;
    private static final float STROKE_ROLL_DEGREES = 5.0f;
    private static final float RUDDER_ROLL_DEGREES = 3.0f;
    private static final float STROKE_PITCH_DEGREES = 2.0f;

    private static final float MODEL_WATERLINE_Y_OFFSET = -1.6f;

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
        GeoBone tiltBone = this.getAnimationProcessor().getBone(TILT_BONE);

        if (rootBone == null) {
            return;
        }

        float partialTick = animationState.getPartialTick();
        float renderYaw = MathHelper.lerpAngleDegrees(partialTick, animatable.prevYaw, animatable.getYaw());

        /*
         * Root bone handles world-facing yaw only.
         * Do not apply pitch/roll wobble to this bone.
         */
        rootBone.setRotY((float) Math.toRadians(-renderYaw));
        rootBone.setPosY(MODEL_WATERLINE_Y_OFFSET);

        if (tiltBone != null) {
            this.applyPaddleReaction(animatable, tiltBone);
        }
    }

    private void applyPaddleReaction(KayakEntity kayak, GeoBone tiltBone) {
        if (!kayak.hasActivePaddleAction()) {
            tiltBone.setRotX(0.0f);
            tiltBone.setRotZ(0.0f);
            return;
        }

        int side = kayak.getPaddleActionSide();
        int direction = kayak.getPaddleActionDirection();

        float actionProgress = 1.0f - ((float) kayak.getPaddleActionTicks() / PADDLE_ACTION_DURATION_TICKS);
        float wave = (float) Math.sin(actionProgress * Math.PI);

        float rollDegrees;
        float pitchDegrees = 0.0f;

        if (direction == PaddleStrokePayload.DIRECTION_RUDDER) {
            rollDegrees = -side * RUDDER_ROLL_DEGREES * wave;
        } else {
            rollDegrees = -side * STROKE_ROLL_DEGREES * wave;

            if (direction == PaddleStrokePayload.DIRECTION_FORWARD) {
                pitchDegrees = STROKE_PITCH_DEGREES * wave;
            } else if (direction == PaddleStrokePayload.DIRECTION_BACKWARD) {
                pitchDegrees = -STROKE_PITCH_DEGREES * wave;
            }
        }

        /*
         * This bone is a child of kayak_root, so these rotations are local
         * to the kayak and work in every compass direction.
         */
        tiltBone.setRotX((float) Math.toRadians(pitchDegrees));
        tiltBone.setRotZ((float) Math.toRadians(rollDegrees));
    }
}