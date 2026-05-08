package net.droingo.outbackkayak.mixin.client;

import net.droingo.outbackkayak.entity.KayakEntity;
import net.droingo.outbackkayak.network.PaddleStrokePayload;
import net.droingo.outbackkayak.registry.ModItems;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BipedEntityModel.class)
public abstract class BipedEntityModelKayakMixin<T extends LivingEntity> {
    @Unique
    private static final float PADDLE_ACTION_DURATION_TICKS = 8.0f;

    @Shadow
    @Final
    public ModelPart leftLeg;

    @Shadow
    @Final
    public ModelPart rightLeg;

    @Shadow
    @Final
    public ModelPart leftArm;

    @Shadow
    @Final
    public ModelPart rightArm;

    @Inject(method = "setAngles", at = @At("TAIL"))
    private void outbackKayak$applyKayakPlayerPose(
            T entity,
            float limbAngle,
            float limbDistance,
            float animationProgress,
            float headYaw,
            float headPitch,
            CallbackInfo ci
    ) {
        boolean ridingKayak = entity.getVehicle() instanceof KayakEntity;

        this.leftLeg.visible = !ridingKayak;
        this.rightLeg.visible = !ridingKayak;

        if (ridingKayak) {
            this.outbackKayak$applyPaddleActionPose(entity);
            return;
        }

        if (this.outbackKayak$isCarryingKayak(entity)) {
            this.outbackKayak$poseArmsForCarryingKayak();
        }
    }

    @Unique
    private boolean outbackKayak$isCarryingKayak(T entity) {
        return entity.getEquippedStack(EquipmentSlot.HEAD).isOf(ModItems.KAYAK);
    }

    @Unique
    private void outbackKayak$poseArmsForCarryingKayak() {
        /*
         * Arms up and out toward the kayak edges.
         */
        this.rightArm.pitch = -2.45f;
        this.rightArm.yaw = 0.75f;
        this.rightArm.roll = 0.15f;

        this.leftArm.pitch = -2.45f;
        this.leftArm.yaw = -0.75f;
        this.leftArm.roll = -0.15f;
    }

    @Unique
    private void outbackKayak$applyPaddleActionPose(T entity) {
        if (!(entity.getVehicle() instanceof KayakEntity kayak)) {
            return;
        }

        if (!kayak.hasActivePaddleAction()) {
            return;
        }

        int side = kayak.getPaddleActionSide();
        int direction = kayak.getPaddleActionDirection();

        float actionProgress = 1.0f - ((float) kayak.getPaddleActionTicks() / PADDLE_ACTION_DURATION_TICKS);
        float strokeWave = (float) Math.sin(actionProgress * Math.PI);

        if (direction == PaddleStrokePayload.DIRECTION_RUDDER) {
            this.outbackKayak$poseRudderAction(side, strokeWave);
            return;
        }

        this.outbackKayak$posePaddleStroke(side, direction, strokeWave);
    }

    @Unique
    private void outbackKayak$posePaddleStroke(int side, int direction, float strokeWave) {
        boolean leftSide = side == PaddleStrokePayload.SIDE_LEFT;
        boolean forwardStroke = direction == PaddleStrokePayload.DIRECTION_FORWARD;

        float activePitch = forwardStroke
                ? -0.95f + strokeWave * 0.65f
                : -0.25f - strokeWave * 0.65f;

        float passivePitch = -0.45f;

        float activeYaw = leftSide
                ? -0.85f
                : 0.85f;

        float passiveYaw = leftSide
                ? -0.25f
                : 0.25f;

        float activeRoll = leftSide
                ? -0.25f
                : 0.25f;

        if (leftSide) {
            this.leftArm.pitch = activePitch;
            this.leftArm.yaw = activeYaw;
            this.leftArm.roll = activeRoll;

            this.rightArm.pitch = passivePitch;
            this.rightArm.yaw = passiveYaw;
            this.rightArm.roll = -activeRoll * 0.45f;
        } else {
            this.rightArm.pitch = activePitch;
            this.rightArm.yaw = activeYaw;
            this.rightArm.roll = activeRoll;

            this.leftArm.pitch = passivePitch;
            this.leftArm.yaw = passiveYaw;
            this.leftArm.roll = -activeRoll * 0.45f;
        }
    }

    @Unique
    private void outbackKayak$poseRudderAction(int side, float strokeWave) {
        boolean leftSide = side == PaddleStrokePayload.SIDE_LEFT;

        float yaw = leftSide
                ? -0.95f
                : 0.95f;

        float roll = leftSide
                ? -0.35f
                : 0.35f;

        float pitch = -0.35f + strokeWave * 0.25f;

        if (leftSide) {
            this.leftArm.pitch = pitch;
            this.leftArm.yaw = yaw;
            this.leftArm.roll = roll;

            this.rightArm.pitch = -0.35f;
            this.rightArm.yaw = -0.15f;
            this.rightArm.roll = 0.1f;
        } else {
            this.rightArm.pitch = pitch;
            this.rightArm.yaw = yaw;
            this.rightArm.roll = roll;

            this.leftArm.pitch = -0.35f;
            this.leftArm.yaw = 0.15f;
            this.leftArm.roll = -0.1f;
        }
    }
}