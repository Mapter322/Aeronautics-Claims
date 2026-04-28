package com.mapter.aeroclaims.mixin;

import com.mapter.aeroclaims.claim.Claim;
import com.mapter.aeroclaims.claim.ClaimManager;
import com.mapter.aeroclaims.config.AeroClaimsConfig;
import com.mapter.aeroclaims.protect.CreateProtectionHelper;
import com.mapter.aeroclaims.protect.IPlacerTracked;
import com.simibubi.create.content.kinetics.base.BlockBreakingKineticBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.UUID;

@Mixin(value = BlockBreakingKineticBlockEntity.class, remap = false)
public class MixinBlockBreakingKineticBlockEntity {

    @Shadow(remap = false)
    protected BlockPos breakingPos;

    @ModifyVariable(
            method = "tick",
            remap = false,
            at = @At(
                    value = "INVOKE_ASSIGN",
                    target = "Lnet/minecraft/world/level/Level;getBlockState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;"
            ),
            name = "stateToBreak"
    )
    private BlockState aeroclaims$protectClaimedBlock(BlockState original) {
        BlockBreakingKineticBlockEntity self = (BlockBreakingKineticBlockEntity) (Object) this;
        Level level = self.getLevel();

        if (level == null || level.isClientSide() || breakingPos == null) {
            return original;
        }
        if (!AeroClaimsConfig.KINETIC_BLOCK_PROTECTION.get()) {
            return original;
        }
        if (!(level instanceof ServerLevel serverLevel)) {
            return original;
        }

        Claim claim = ClaimManager.getClaimAt(serverLevel, breakingPos);
        if (claim == null || !claim.isActive()) {
            return original;
        }

        UUID placerUUID = null;
        if (self instanceof IPlacerTracked tracked) {
            placerUUID = tracked.aeroclaims$getPlacerUUID();
        }

        if (CreateProtectionHelper.isBreakingAllowedForPlacer(placerUUID, serverLevel, claim)) {
            return original;
        }

        return Blocks.BEDROCK.defaultBlockState();
    }
}
