package net.droingo.outbackkayak.entity;

import net.droingo.outbackkayak.network.PaddleStrokePayload;
import net.droingo.outbackkayak.registry.ModItems;
import net.minecraft.entity.*;
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
import net.minecraft.item.Items;
import org.jetbrains.annotations.Nullable;

import net.minecraft.fluid.FluidState;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.util.math.BlockPos;

public class KayakEntity extends Entity implements GeoEntity, Leashable {
    private static final RawAnimation IDLE_ANIMATION = RawAnimation.begin().thenLoop("rock");
    private static final RawAnimation ENTER_ANIMATION = RawAnimation.begin().thenPlay("enter");

    private static final int STROKE_COOLDOWN_TICKS = 2;

    private static final double WATER_SURFACE_OFFSET = 0.05;
    private static final double FLOAT_SEARCH_UP = 1.25;
    private static final double FLOAT_SEARCH_DOWN = 2.25;
    private static final double FLOAT_SPRING_STRENGTH = 0.18;
    private static final double FLOAT_VERTICAL_DAMPING = 0.55;
    private static final double MAX_FLOAT_VERTICAL_SPEED = 0.18;

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

    private static final double MIN_LEASH_ALIGN_SPEED = 0.035;
    private static final float LEASH_YAW_ALIGN_STRENGTH = 0.22f;
    private static final float MAX_LEASH_YAW_STEP = 6.0f;

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

            Leashable.tickLeash(this);
            this.applyKayakLeashPull();
            this.alignYawToLeashMovement();
        }
    }

    private void alignYawToLeashMovement() {
        if (!this.isLeashed()) {
            return;
        }

        if (this.hasPassengers()) {
            return;
        }

        Vec3d horizontalMomentum = new Vec3d(this.kayakMomentum.x, 0.0, this.kayakMomentum.z);

        if (horizontalMomentum.lengthSquared() < MIN_LEASH_ALIGN_SPEED * MIN_LEASH_ALIGN_SPEED) {
            return;
        }

        float targetYaw = (float) (MathHelper.atan2(horizontalMomentum.z, horizontalMomentum.x) * MathHelper.DEGREES_PER_RADIAN) - 90.0f;

        float yawDifference = MathHelper.wrapDegrees(targetYaw - this.getYaw());
        float yawStep = MathHelper.clamp(
                yawDifference * LEASH_YAW_ALIGN_STRENGTH,
                -MAX_LEASH_YAW_STEP,
                MAX_LEASH_YAW_STEP
        );

        this.prevYaw = this.getYaw();
        this.setYaw(this.getYaw() + yawStep);
        this.setRotation(this.getYaw(), this.getPitch());
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

    private void applyKayakLeashPull() {
        Entity leashHolder = this.getLeashHolder();

        if (leashHolder == null) {
            return;
        }

        Vec3d toHolder = leashHolder.getPos().subtract(this.getPos());
        double distance = toHolder.length();

        if (distance < 1.8) {
            return;
        }

        Vec3d pullDirection = toHolder.normalize();

        double pullStrength = Math.min((distance - 1.8) * 0.035, 0.12);

        if (!this.isNearWaterForKayakControl()) {
            pullStrength *= 0.45;
        }

        this.kayakMomentum = this.limitHorizontalMomentum(
                this.kayakMomentum.add(pullDirection.multiply(pullStrength))
        );

        this.setVelocity(this.kayakMomentum.x, this.getVelocity().y, this.kayakMomentum.z);
        this.velocityModified = true;
    }

    private void tickKayakMovement() {
        if (this.strokeCooldownTicks > 0) {
            this.strokeCooldownTicks--;
        }

        this.applySmoothKayakTurning();

        boolean nearWater = this.isNearWaterForKayakControl();
        double decay = nearWater ? WATER_MOMENTUM_DECAY : LAND_MOMENTUM_DECAY;

        this.kayakMomentum = this.kayakMomentum.multiply(decay);
        this.kayakMomentum = this.applyKayakTracking(this.kayakMomentum);
        this.kayakMomentum = this.limitHorizontalMomentum(this.kayakMomentum);

        if (this.kayakMomentum.horizontalLengthSquared() < 0.00008) {
            this.kayakMomentum = Vec3d.ZERO;
        }

        double yVelocity = this.calculateFloatingVelocity();

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

        if (!this.isNearWaterForKayakControl()) {
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

    private double calculateFloatingVelocity() {
        Double waterSurfaceY = this.findNearbyWaterSurfaceY();

        if (waterSurfaceY == null) {
            return this.getVelocity().y - 0.08;
        }

        double targetY = waterSurfaceY + WATER_SURFACE_OFFSET;
        double heightDifference = targetY - this.getY();

        double verticalVelocity = this.getVelocity().y;
        verticalVelocity += heightDifference * FLOAT_SPRING_STRENGTH;
        verticalVelocity *= FLOAT_VERTICAL_DAMPING;

        return MathHelper.clamp(
                verticalVelocity,
                -MAX_FLOAT_VERTICAL_SPEED,
                MAX_FLOAT_VERTICAL_SPEED
        );
    }

    private Double findNearbyWaterSurfaceY() {
        BlockPos basePos = this.getBlockPos();

        int up = (int) Math.ceil(FLOAT_SEARCH_UP);
        int down = (int) Math.ceil(FLOAT_SEARCH_DOWN);

        for (int yOffset = up; yOffset >= -down; yOffset--) {
            BlockPos pos = basePos.up(yOffset);
            FluidState fluidState = this.getWorld().getFluidState(pos);

            if (fluidState.isIn(FluidTags.WATER)) {
                return pos.getY() + (double) fluidState.getHeight(this.getWorld(), pos);
            }
        }

        return null;
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

        double sidewaysDecay = this.isNearWaterForKayakControl()
                ? WATER_SIDEWAYS_DRIFT_DECAY
                : LAND_SIDEWAYS_DRIFT_DECAY;

        sidewaysAmount *= sidewaysDecay;

        Vec3d correctedHorizontal = forward.multiply(forwardAmount)
                .add(right.multiply(sidewaysAmount));

        return new Vec3d(correctedHorizontal.x, momentum.y, correctedHorizontal.z);
    }

    private boolean isNearWaterForKayakControl() {
        return this.findNearbyWaterSurfaceY() != null;
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
        ItemStack stack = player.getStackInHand(hand);

        if (stack.isOf(Items.LEAD)) {
            return this.interactWithLead(player, stack);
        }

        if (player.isSneaking()) {
            return this.tryPickUpKayak(player);
        }

        if (player.shouldCancelInteraction()) {
            return ActionResult.PASS;
        }

        if (!this.getWorld().isClient()) {
            player.startRiding(this);
            this.triggerAnim("kayak_controller", "enter");
        }

        return ActionResult.SUCCESS;
    }

    private ActionResult tryPickUpKayak(PlayerEntity player) {
        if (this.getWorld().isClient()) {
            return ActionResult.SUCCESS;
        }

        if (this.hasPassengers()) {
            return ActionResult.FAIL;
        }

        if (this.isLeashed()) {
            return ActionResult.FAIL;
        }

        ItemStack headStack = player.getEquippedStack(EquipmentSlot.HEAD);

        if (!headStack.isEmpty()) {
            return ActionResult.FAIL;
        }

        player.equipStack(EquipmentSlot.HEAD, new ItemStack(ModItems.KAYAK));
        this.discard();

        return ActionResult.SUCCESS;
    }

    private ActionResult interactWithLead(PlayerEntity player, ItemStack stack) {
        if (this.getWorld().isClient()) {
            return ActionResult.SUCCESS;
        }

        this.setLeashAnchorFromPlayer(player);

        boolean wasAlreadyLeashed = this.isLeashed();

        this.attachLeash(player, true);

        if (!wasAlreadyLeashed && !player.getAbilities().creativeMode) {
            stack.decrement(1);
        }

        return ActionResult.SUCCESS;
    }

    private void setLeashAnchorFromPlayer(PlayerEntity player) {
        Vec3d toPlayer = player.getPos().subtract(this.getPos());
        Vec3d forward = this.getFlatForwardVector();

        double frontBackDot = toPlayer.dotProduct(forward);

        this.leashAnchor = frontBackDot >= 0.0
                ? LEASH_ANCHOR_FRONT
                : LEASH_ANCHOR_BACK;
    }

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return this.getPassengerList().isEmpty();
    }

    @Override
    @Nullable
    public Leashable.LeashData getLeashData() {
        return this.leashData;
    }

    @Override
    public void setLeashData(@Nullable Leashable.LeashData leashData) {
        this.leashData = leashData;
    }

    @Override
    public boolean canBeLeashed() {
        return !this.isRemoved();
    }

    @Override
    public boolean canLeashAttachTo() {
        return !this.isRemoved();
    }



    @Override
    protected Vec3d getLeashOffset() {
        /*
         * These match the Blockbench locators:
         *
         * front_handle: [0, 7.75, -23.25]
         * rear_handle:  [0, 7.75,  24.25]
         *
         * Divided by 16 to convert model pixels to Minecraft blocks.
         */
        double y = 7.75 / 16.0;

        double z = this.leashAnchor == LEASH_ANCHOR_BACK
                ? -23.25 / 16.0
                : 24.25 / 16.0;

        return new Vec3d(0.0, y, z);
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
        this.leashData = this.readLeashDataFromNbt(nbt);

        if (nbt.contains("KayakLeashAnchor")) {
            this.leashAnchor = nbt.getInt("KayakLeashAnchor");
        }
    }

    @Override
    protected void writeCustomDataToNbt(NbtCompound nbt) {
        this.writeLeashDataToNbt(nbt, this.leashData);
        nbt.putInt("KayakLeashAnchor", this.leashAnchor);
    }

    @Nullable
    private Leashable.LeashData leashData;

    private static final int LEASH_ANCHOR_FRONT = 0;
    private static final int LEASH_ANCHOR_BACK = 1;

    private int leashAnchor = LEASH_ANCHOR_FRONT;



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