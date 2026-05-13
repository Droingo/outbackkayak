package net.droingo.outbackkayak.event;

import net.droingo.outbackkayak.block.RapidControllerBlock;
import net.droingo.outbackkayak.registry.ModBlocks;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.BlockState;
import net.minecraft.fluid.FluidState;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

public final class RapidControllerParticleEvents {
    private static final int PARTICLE_SCAN_INTERVAL_TICKS = 4;

    /*
     * How far from a player rapid controllers are allowed to spawn visible current particles.
     * Raise this for cinematic/map-making visibility.
     * Lower it later if performance becomes a problem in huge rapid fields.
     */
    private static final int HORIZONTAL_SCAN_RADIUS = 64;
    private static final int VERTICAL_SCAN_RADIUS = 8;

    private static final int WATER_SEARCH_UP = 8;

    private static int tickCounter;

    private RapidControllerParticleEvents() {
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            tickCounter++;

            if (tickCounter % PARTICLE_SCAN_INTERVAL_TICKS != 0) {
                return;
            }

            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                ServerWorld world = player.getServerWorld();
                spawnNearbyRapidParticles(world, player);
            }
        });
    }

    private static void spawnNearbyRapidParticles(ServerWorld world, ServerPlayerEntity player) {
        BlockPos playerPos = player.getBlockPos();

        for (int yOffset = -VERTICAL_SCAN_RADIUS; yOffset <= VERTICAL_SCAN_RADIUS; yOffset++) {
            for (int xOffset = -HORIZONTAL_SCAN_RADIUS; xOffset <= HORIZONTAL_SCAN_RADIUS; xOffset++) {
                for (int zOffset = -HORIZONTAL_SCAN_RADIUS; zOffset <= HORIZONTAL_SCAN_RADIUS; zOffset++) {
                    BlockPos controllerPos = playerPos.add(xOffset, yOffset, zOffset);
                    BlockState state = world.getBlockState(controllerPos);

                    if (!state.isOf(ModBlocks.RAPID_CONTROLLER)) {
                        continue;
                    }

                    spawnControllerFlowParticles(world, controllerPos, state);
                }
            }
        }
    }

    private static void spawnControllerFlowParticles(ServerWorld world, BlockPos controllerPos, BlockState state) {
        Direction flowDirection = RapidControllerBlock.getFlowDirection(state);

        Vec3d flow = new Vec3d(
                flowDirection.getOffsetX(),
                0.0,
                flowDirection.getOffsetZ()
        );

        if (flow.horizontalLengthSquared() <= 0.0) {
            return;
        }

        flow = flow.normalize();

        Double waterSurfaceY = findWaterSurfaceYAbove(world, controllerPos);

        if (waterSurfaceY == null) {
            return;
        }

        int rapidLevel = RapidControllerBlock.getRapidLevel(state);

        int cloudStreakCount = switch (rapidLevel) {
            case 0 -> 1; // Calm Current
            case 1 -> 2; // Fast Current
            case 2 -> 3; // Light Rapids
            case 3 -> 5; // Heavy Rapids
            default -> 2;
        };

        float splashChance = switch (rapidLevel) {
            case 0 -> 0.25f;
            case 1 -> 0.55f;
            case 2 -> 0.80f;
            case 3 -> 1.00f;
            default -> 0.55f;
        };

        float bubbleChance = switch (rapidLevel) {
            case 0 -> 0.15f;
            case 1 -> 0.40f;
            case 2 -> 0.65f;
            case 3 -> 0.90f;
            default -> 0.40f;
        };

        double minVelocity = switch (rapidLevel) {
            case 0 -> 0.045;
            case 1 -> 0.080;
            case 2 -> 0.115;
            case 3 -> 0.150;
            default -> 0.080;
        };

        double randomVelocity = switch (rapidLevel) {
            case 0 -> 0.030;
            case 1 -> 0.060;
            case 2 -> 0.080;
            case 3 -> 0.105;
            default -> 0.060;
        };

        double centerX = controllerPos.getX() + 0.5;
        double centerY = waterSurfaceY + 0.08;
        double centerZ = controllerPos.getZ() + 0.5;

        Vec3d right = new Vec3d(flow.z, 0.0, -flow.x).normalize();

        /*
         * Surface current streaks.
         * Higher presets spawn more particles and move them faster downstream.
         */
        for (int i = 0; i < cloudStreakCount; i++) {
            double upstreamDistance = 0.35 + world.random.nextDouble() * 0.45;
            double sideJitter = (world.random.nextDouble() - 0.5) * 0.65;

            double x = centerX - flow.x * upstreamDistance + right.x * sideJitter;
            double y = centerY + world.random.nextDouble() * 0.05;
            double z = centerZ - flow.z * upstreamDistance + right.z * sideJitter;

            double velocity = minVelocity + world.random.nextDouble() * randomVelocity;

            world.spawnParticles(
                    ParticleTypes.CLOUD,
                    x,
                    y,
                    z,
                    0,
                    flow.x * velocity,
                    0.0,
                    flow.z * velocity,
                    1.0
            );
        }

        /*
         * Surface splash.
         */
        if (world.random.nextFloat() < splashChance) {
            double sideJitter = (world.random.nextDouble() - 0.5) * 0.55;

            double x = centerX - flow.x * 0.45 + right.x * sideJitter;
            double y = centerY;
            double z = centerZ - flow.z * 0.45 + right.z * sideJitter;

            double splashVelocity = minVelocity + 0.035;

            int splashCount = switch (rapidLevel) {
                case 0 -> 0;
                case 1 -> 0;
                case 2 -> 1;
                case 3 -> 2;
                default -> 0;
            };

            world.spawnParticles(
                    ParticleTypes.SPLASH,
                    x,
                    y,
                    z,
                    splashCount,
                    0.035,
                    0.015,
                    0.035,
                    0.015
            );

            world.spawnParticles(
                    ParticleTypes.SPLASH,
                    x,
                    y,
                    z,
                    0,
                    flow.x * splashVelocity,
                    0.02,
                    flow.z * splashVelocity,
                    1.0
            );
        }

        /*
         * Underwater bubbles.
         */
        if (world.random.nextFloat() < bubbleChance) {
            double sideJitter = (world.random.nextDouble() - 0.5) * 0.65;

            double x = centerX - flow.x * 0.2 + right.x * sideJitter;
            double y = centerY - 0.15;
            double z = centerZ - flow.z * 0.2 + right.z * sideJitter;

            double bubbleVelocity = minVelocity * 0.7;

            world.spawnParticles(
                    ParticleTypes.BUBBLE,
                    x,
                    y,
                    z,
                    0,
                    flow.x * bubbleVelocity,
                    0.02,
                    flow.z * bubbleVelocity,
                    1.0
            );
        }
    }

    private static Double findWaterSurfaceYAbove(ServerWorld world, BlockPos controllerPos) {
        Double highestWaterSurface = null;

        for (int yOffset = 0; yOffset <= WATER_SEARCH_UP; yOffset++) {
            BlockPos checkPos = controllerPos.up(yOffset);
            FluidState fluidState = world.getFluidState(checkPos);

            if (!fluidState.isIn(FluidTags.WATER)) {
                continue;
            }

            highestWaterSurface = (double) checkPos.getY() + (double) fluidState.getHeight(world, checkPos);
        }

        return highestWaterSurface;
    }
}