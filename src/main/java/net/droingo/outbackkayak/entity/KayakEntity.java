package net.droingo.outbackkayak.entity;

import net.droingo.outbackkayak.network.PaddleStrokePayload;
import net.droingo.outbackkayak.registry.ModItems;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

public class KayakEntity extends Entity implements GeoEntity {
    private static final RawAnimation IDLE_ANIMATION = RawAnimation.begin().thenLoop("rock");
    private static final RawAnimation ENTER_ANIMATION = RawAnimation.begin().thenPlay("enter");

    private static final int STROKE_COOLDOWN_TICKS = 2;

    private static final double FORWARD_STROKE_POWER = 0.22;
    private static final double BACKWARD_STROKE_POWER = -0.14;

    private static final double MAX_FORWARD_SPEED = 0.72;
    private static final double MAX_BACKWARD_SPEED = 0.34;

    private static final double WATER_MOMENTUM_DECAY = 0.972;
    private static final double LAND_MOMENTUM_DECAY = 0.82;

    private static final double WATER_SIDEWAYS_DRIFT_DECAY = 0.42;
    private static final double LAND_SIDEWAYS_DRIFT_DECAY = 0.75;
    private static final double MIN_TRACKING_SPEED = 0.04;

    private static final float FORWARD_TURN_IMPULSE = 5.5f;
    private static final float BACKWARD_TURN_IMPULSE = 4.0f;
    private static final float RUDDER_TURN_IMPULSE = 1.2f;
    private static final float MAX_YAW_VELOCITY = 7.0f;
    private static final float YAW_DECAY = 0.78f;

    private static final double MIN_RUDDER_SPEED = 0.08;

    private final AnimatableInstanceCache animatableInstanceCache = GeckoLibUtil.createInstanceCache(this);

    private int strokeCooldownTicks;
    private float yawVelocity;
    private Vec3d kayakMomentum = Vec3d.ZERO;

    private int clientInterpolationTicks;
    private double clientTargetX;
    private double clientTargetY;
    private double clientTargetZ;
    private float clientTargetYaw;
    private float clientTargetPitch;

    public KayakEntity(EntityType<? extends KayakEntity> entityType, World world) {
        super(entityType, world);
    }

    @Override
    protected void initDataTracker(DataTracker.Builder builder) {
        // Later: synced wobble, rapid intensity, stability, capsize state.
    }

    @Override
    public void tick() {
        super.tick();

        if (this.getWorld().isClient()) {
            this.tickClientInterpolation();
        } else {
            this.tickKayakMovement();
        }
    }

    private void tickClientInterpolation() {
        if (this.clientInterpolationTicks <= 0) {
            return;
        }

        double progress = 1.0 / this.clientInterpolationTicks;

        double nextX = this.getX() + (this.clientTargetX - this.getX()) * progress;
        double nextY = this.getY() + (this.clientTargetY - this.getY()) * progress;
        double nextZ = this.getZ() + (this.clientTargetZ - this.getZ()) * progress;

        float nextYaw = this.getYaw()
                + MathHelper.wrapDegrees(this.clientTargetYaw - this.getYaw()) * (float) progress;

        float nextPitch = this.getPitch()
                + (this.clientTargetPitch - this.getPitch()) * (float) progress;

        this.setPosition(nextX, nextY, nextZ);
        this.setRotation(nextYaw, nextPitch);

        this.clientInterpolationTicks--;
    }

    private void tickKayakMovement() {
        if (this.strokeCooldownTicks > 0) {
            this.strokeCooldownTicks--;
        }

        this.applySmoothKayakTurning();

        double decay = this.isTouchingWater() ? WATER_MOMENTUM_DECAY : LAND_MOMENTUM_DECAY;

        this.kayakMomentum = this.kayakMomentum.multiply(decay);
        this.kayakMomentum = this.applyKayakTracking(this.kayakMomentum);
        this.kayakMomentum = this.limitHorizontalMomentum(this.kayakMomentum);

        if (this.kayakMomentum.horizontalLengthSquared() < 0.00008) {
            this.kayakMomentum = Vec3d.ZERO;
        }

        double yVelocity = this.getVelocity().y;

        if (this.isTouchingWater()) {
            yVelocity = 0.0;
        } else {
            yVelocity -= 0.08;
        }

        this.setVelocity(this.kayakMomentum.x, yVelocity, this.kayakMomentum.z);
        this.move(MovementType.SELF, this.getVelocity());
        this.velocityModified = true;
    }

    public void applyPaddleStroke(ServerPlayerEntity player, int side, int direction) {
        if (this.getWorld().isClient()) {
            return;
        }

        if (player.getVehicle() != this) {
            return;
        }

        if (!this.isTouchingWater()) {
            return;
        }

        if (!this.playerHasPaddle(player)) {
            return;
        }

        if (!this.isValidStroke(side, direction)) {
            return;
        }

        if (this.strokeCooldownTicks > 0) {
            return;
        }

        this.strokeCooldownTicks = STROKE_COOLDOWN_TICKS;

        if (direction == PaddleStrokePayload.DIRECTION_RUDDER) {
            this.applyRudderStroke(side);
            this.spawnStrokeSplash(side, direction);
            return;
        }

        boolean forwardStroke = direction == PaddleStrokePayload.DIRECTION_FORWARD;

        Vec3d forward = this.getFlatForwardVector();
        double power = forwardStroke ? FORWARD_STROKE_POWER : BACKWARD_STROKE_POWER;

        this.kayakMomentum = this.kayakMomentum.add(forward.multiply(power));
        this.kayakMomentum = this.limitHorizontalMomentum(this.kayakMomentum);

        float turnImpulse = forwardStroke ? FORWARD_TURN_IMPULSE : BACKWARD_TURN_IMPULSE;
        float turnAmount = -side * direction * turnImpulse;

        this.yawVelocity += turnAmount;
        this.yawVelocity = MathHelper.clamp(this.yawVelocity, -MAX_YAW_VELOCITY, MAX_YAW_VELOCITY);

        this.kayakMomentum = this.rotateHorizontalMomentum(this.kayakMomentum, turnAmount * 0.25f);

        this.setVelocity(this.kayakMomentum.x, this.getVelocity().y, this.kayakMomentum.z);
        this.velocityModified = true;

        this.spawnStrokeSplash(side, direction);
    }

    private void applyRudderStroke(int side) {
        double speed = new Vec3d(this.kayakMomentum.x, 0.0, this.kayakMomentum.z).length();

        if (speed < MIN_RUDDER_SPEED) {
            return;
        }

        /*
         * Rudder stroke:
         * - does not add forward speed
         * - only works while already moving
         * - look/click right turns right, look/click left turns left
         */
        float turnAmount = side * RUDDER_TURN_IMPULSE;

        this.yawVelocity += turnAmount;
        this.yawVelocity = MathHelper.clamp(this.yawVelocity, -MAX_YAW_VELOCITY, MAX_YAW_VELOCITY);

        this.kayakMomentum = this.rotateHorizontalMomentum(this.kayakMomentum, turnAmount * 0.18f);

        this.setVelocity(this.kayakMomentum.x, this.getVelocity().y, this.kayakMomentum.z);
        this.velocityModified = true;
    }

    private void applySmoothKayakTurning() {
        if (Math.abs(this.yawVelocity) < 0.05f) {
            this.yawVelocity = 0.0f;
            return;
        }

        float oldYaw = this.getYaw();
        float newYaw = oldYaw + this.yawVelocity;

        this.prevYaw = oldYaw;
        this.setYaw(newYaw);
        this.setRotation(newYaw, this.getPitch());

        this.yawVelocity *= YAW_DECAY;
    }

    private Vec3d limitHorizontalMomentum(Vec3d momentum) {
        Vec3d horizontal = new Vec3d(momentum.x, 0.0, momentum.z);

        double speed = horizontal.length();

        if (speed <= 0.001) {
            return momentum;
        }

        Vec3d forward = this.getFlatForwardVector();
        double forwardAmount = horizontal.dotProduct(forward);

        double maxSpeed = forwardAmount >= 0.0 ? MAX_FORWARD_SPEED : MAX_BACKWARD_SPEED;

        if (speed <= maxSpeed) {
            return momentum;
        }

        Vec3d limited = horizontal.normalize().multiply(maxSpeed);

        return new Vec3d(limited.x, momentum.y, limited.z);
    }

    private Vec3d applyKayakTracking(Vec3d momentum) {
        Vec3d horizontal = new Vec3d(momentum.x, 0.0, momentum.z);

        double speed = horizontal.length();

        if (speed < MIN_TRACKING_SPEED) {
            return momentum;
        }

        Vec3d forward = this.getFlatForwardVector();
        Vec3d right = new Vec3d(forward.z, 0.0, -forward.x).normalize();

        double forwardAmount = horizontal.dotProduct(forward);
        double sidewaysAmount = horizontal.dotProduct(right);

        double sidewaysDecay = this.isTouchingWater()
                ? WATER_SIDEWAYS_DRIFT_DECAY
                : LAND_SIDEWAYS_DRIFT_DECAY;

        sidewaysAmount *= sidewaysDecay;

        Vec3d correctedHorizontal = forward.multiply(forwardAmount)
                .add(right.multiply(sidewaysAmount));

        return new Vec3d(correctedHorizontal.x, momentum.y, correctedHorizontal.z);
    }

    private Vec3d rotateHorizontalMomentum(Vec3d momentum, float degrees) {
        double radians = degrees * MathHelper.RADIANS_PER_DEGREE;

        double cos = Math.cos(radians);
        double sin = Math.sin(radians);

        double rotatedX = momentum.x * cos - momentum.z * sin;
        double rotatedZ = momentum.x * sin + momentum.z * cos;

        return new Vec3d(rotatedX, momentum.y, rotatedZ);
    }

    private Vec3d getFlatForwardVector() {
        float yawRadians = this.getYaw() * MathHelper.RADIANS_PER_DEGREE;

        return new Vec3d(
                -MathHelper.sin(yawRadians),
                0.0,
                MathHelper.cos(yawRadians)
        ).normalize();
    }

    private boolean playerHasPaddle(LivingEntity player) {
        return player.getMainHandStack().isOf(ModItems.PADDLE)
                || player.getOffHandStack().isOf(ModItems.PADDLE);
    }

    private boolean isValidStroke(int side, int direction) {
        boolean validSide = side == PaddleStrokePayload.SIDE_LEFT
                || side == PaddleStrokePayload.SIDE_RIGHT;

        boolean validDirection = direction == PaddleStrokePayload.DIRECTION_FORWARD
                || direction == PaddleStrokePayload.DIRECTION_BACKWARD
                || direction == PaddleStrokePayload.DIRECTION_RUDDER;

        return validSide && validDirection;
    }
    private void spawnStrokeSplash(int side, int direction) {
        if (!(this.getWorld() instanceof ServerWorld serverWorld)) {
            return;
        }

        Vec3d forward = this.getFlatForwardVector();
        Vec3d right = new Vec3d(forward.z, 0.0, -forward.x).normalize();

        Vec3d sideOffset = right.multiply(side * 0.65);
        double length = switch (direction) {
            case PaddleStrokePayload.DIRECTION_FORWARD -> -0.25;
            case PaddleStrokePayload.DIRECTION_BACKWARD -> 0.45;
            default -> 0.15;
        };

        Vec3d lengthOffset = forward.multiply(length);

        Vec3d splashPos = this.getPos()
                .add(sideOffset)
                .add(lengthOffset)
                .add(0.0, 0.15, 0.0);

        serverWorld.spawnParticles(
                ParticleTypes.SPLASH,
                splashPos.x,
                splashPos.y,
                splashPos.z,
                10,
                0.12,
                0.04,
                0.12,
                0.06
        );
    }

    @Override
    public void updateTrackedPositionAndAngles(
            double x,
            double y,
            double z,
            float yaw,
            float pitch,
            int interpolationSteps
    ) {
        if (this.getWorld().isClient()) {
            this.clientTargetX = x;
            this.clientTargetY = y;
            this.clientTargetZ = z;
            this.clientTargetYaw = yaw;
            this.clientTargetPitch = pitch;

            this.clientInterpolationTicks = Math.max(3, interpolationSteps);
        } else {
            super.updateTrackedPositionAndAngles(x, y, z, yaw, pitch, interpolationSteps);
        }
    }

    @Override
    public ActionResult interact(PlayerEntity player, Hand hand) {
        if (player.shouldCancelInteraction()) {
            return ActionResult.PASS;
        }

        if (!this.getWorld().isClient()) {
            player.startRiding(this);

            /*
             * This requires the controller to register "enter" as a triggerable animation.
             */
            this.triggerAnim("kayak_controller", "enter");
        }

        return ActionResult.SUCCESS;
    }

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return this.getPassengerList().isEmpty();
    }

    @Override
    protected void updatePassengerPosition(Entity passenger, PositionUpdater positionUpdater) {
        if (!this.hasPassenger(passenger)) {
            return;
        }

        Vec3d seatOffset = this.getFlatForwardVector().multiply(-0.08);

        positionUpdater.accept(
                passenger,
                this.getX() + seatOffset.x,
                this.getY() - 0.42,
                this.getZ() + seatOffset.z
        );

        // Do not force passenger yaw, body yaw, or head yaw.
        // The rider needs free camera movement for look-left/look-right paddle strokes.
    }



    @Override
    public boolean canHit() {
        return true;
    }

    @Override
    public boolean isCollidable() {
        return !this.isRemoved();
    }

    @Override
    public boolean damage(DamageSource source, float amount) {
        if (this.getWorld().isClient() || this.isRemoved()) {
            return true;
        }

        this.dropStack(new ItemStack(ModItems.KAYAK));
        this.discard();

        return true;
    }

    @Override
    public ItemStack getPickBlockStack() {
        return new ItemStack(ModItems.KAYAK);
    }

    @Override
    protected void readCustomDataFromNbt(NbtCompound nbt) {
        // Later: kayak damage, storage, variant.
    }

    @Override
    protected void writeCustomDataToNbt(NbtCompound nbt) {
        // Later: kayak damage, storage, variant.
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "kayak_controller", 0, state -> {
            return state.setAndContinue(IDLE_ANIMATION);
        }).triggerableAnim("enter", ENTER_ANIMATION));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.animatableInstanceCache;
    }
}