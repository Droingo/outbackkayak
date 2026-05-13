package net.droingo.outbackkayak.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.HorizontalFacingBlock;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.IntProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

public class RapidControllerBlock extends HorizontalFacingBlock {
    public static final MapCodec<RapidControllerBlock> CODEC = createCodec(RapidControllerBlock::new);

    public static final IntProperty RAPID_LEVEL = IntProperty.of("rapid_level", 0, 3);

    public RapidControllerBlock(Settings settings) {
        super(settings);
        this.setDefaultState(this.getDefaultState()
                .with(Properties.HORIZONTAL_FACING, Direction.NORTH)
                .with(RAPID_LEVEL, 1));
    }

    @Override
    protected MapCodec<? extends HorizontalFacingBlock> getCodec() {
        return CODEC;
    }

    @Override
    @Nullable
    public BlockState getPlacementState(ItemPlacementContext context) {
        return this.getDefaultState()
                .with(Properties.HORIZONTAL_FACING, context.getHorizontalPlayerFacing())
                .with(RAPID_LEVEL, 1);
    }

    @Override
    protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
        if (!player.isSneaking()) {
            return ActionResult.PASS;
        }

        if (!world.isClient()) {
            BlockState newState = state.cycle(RAPID_LEVEL);
            world.setBlockState(pos, newState, Block.NOTIFY_ALL);

            player.sendMessage(
                    Text.literal("Rapid preset: " + getPresetName(newState)),
                    true
            );
        }

        return ActionResult.SUCCESS;
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(Properties.HORIZONTAL_FACING, RAPID_LEVEL);
    }

    public static Direction getFlowDirection(BlockState state) {
        return state.get(Properties.HORIZONTAL_FACING);
    }

    public static int getRapidLevel(BlockState state) {
        return state.get(RAPID_LEVEL);
    }

    public static double getPushMultiplier(BlockState state) {
        return switch (getRapidLevel(state)) {
            case 0 -> 0.45; // Calm Current
            case 1 -> 1.00; // Fast Current
            case 2 -> 1.45; // Light Rapids
            case 3 -> 2.00; // Heavy Rapids
            default -> 1.00;
        };
    }

    public static String getPresetName(BlockState state) {
        return switch (getRapidLevel(state)) {
            case 0 -> "Calm Current";
            case 1 -> "Fast Current";
            case 2 -> "Light Rapids";
            case 3 -> "Heavy Rapids";
            default -> "Fast Current";
        };
    }
}