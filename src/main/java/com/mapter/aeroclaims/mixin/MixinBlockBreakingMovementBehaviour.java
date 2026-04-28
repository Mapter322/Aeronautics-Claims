package com.mapter.aeroclaims.mixin;

import com.mapter.aeroclaims.claim.Claim;
import com.mapter.aeroclaims.claim.ClaimManager;
import com.mapter.aeroclaims.config.AeroClaimsConfig;
import com.mapter.aeroclaims.protect.CreateProtectionHelper;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import com.simibubi.create.content.kinetics.base.BlockBreakingMovementBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.UUID;

@Mixin(value = BlockBreakingMovementBehaviour.class, remap = false)
public class MixinBlockBreakingMovementBehaviour {

    private static final ThreadLocal<BlockPos> CAPTURED_POS = new ThreadLocal<>();

    @ModifyArg(
            method = "tickBreaker",
            remap = false,
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/Level;getBlockState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;"
            )
    )
    private BlockPos aeroclaims$captureBreakingPos(BlockPos pos) {
        CAPTURED_POS.set(pos);
        return pos;
    }

    @ModifyVariable(
            method = "tickBreaker",
            remap = false,
            at = @At(
                    value = "INVOKE_ASSIGN",
                    target = "Lnet/minecraft/world/level/Level;getBlockState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;"
            ),
            name = "stateToBreak"
    )
    private BlockState aeroclaims$protectClaimedBlock(BlockState original, MovementContext context) {
        BlockPos pos = CAPTURED_POS.get();
        if (pos == null || context.world == null || context.world.isClientSide()) {
            return original;
        }
        if (!AeroClaimsConfig.KINETIC_BLOCK_PROTECTION.get()) {
            return original;
        }
        if (!(context.world instanceof ServerLevel serverLevel)) {
            return original;
        }

        Claim claim = ClaimManager.getClaimAt(serverLevel, pos);
        if (claim == null || !claim.isActive()) {
            return original;
        }

        UUID placerUUID = null;
        if (context.blockEntityData != null && context.blockEntityData.hasUUID("aeroclaims:Placer")) {
            placerUUID = context.blockEntityData.getUUID("aeroclaims:Placer");
        }

        if (CreateProtectionHelper.isBreakingAllowedForPlacer(placerUUID, serverLevel, claim)) {
            return original;
        }

        return Blocks.BEDROCK.defaultBlockState();
    }
}
