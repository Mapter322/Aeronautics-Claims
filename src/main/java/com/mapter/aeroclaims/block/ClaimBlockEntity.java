package com.mapter.aeroclaims.block;

import com.mapter.aeroclaims.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class ClaimBlockEntity extends BlockEntity {

    public ClaimBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.CLAIM_BE.get(), pos, state);
    }
}