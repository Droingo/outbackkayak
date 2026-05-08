package net.droingo.outbackkayak.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.HorizontalFacingBlock;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.Direction;
import org.jetbrains.annotations.Nullable;

public class RapidControllerBlock extends HorizontalFacingBlock {
    public static final MapCodec<RapidControllerBlock> CODEC = createCodec(RapidControllerBlock::new);

    public RapidControllerBlock(Settings settings) {
        super(settings);
        this.setDefaultState(this.getDefaultState().with(Properties.HORIZONTAL_FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends HorizontalFacingBlock> getCodec() {
        return CODEC;
    }

    @Override
    @Nullable
    public BlockState getPlacementState(ItemPlacementContext context) {
        return this.getDefaultState().with(
                Properties.HORIZONTAL_FACING,
                context.getHorizontalPlayerFacing()
        );
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(Properties.HORIZONTAL_FACING);
    }

    public static Direction getFlowDirection(BlockState state) {
        return state.get(Properties.HORIZONTAL_FACING);
    }
}