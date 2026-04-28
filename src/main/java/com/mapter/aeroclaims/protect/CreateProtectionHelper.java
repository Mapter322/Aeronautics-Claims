package com.mapter.aeroclaims.protect;

import com.mapter.aeroclaims.claim.Claim;
import com.mapter.aeroclaims.claim.ClaimManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;


public class CreateProtectionHelper {


    public static boolean isBlockAccessAllowed(BlockPos pos, ServerPlayer player) {
        if (pos == null || player == null) return true;
        Claim claim = ClaimManager.getClaimAt(player.serverLevel(), pos);
        return claim == null || ClaimManager.getPermissionResolver().canAccess(player, claim);
    }


    public static boolean isBreakingAllowedForPlacer(@Nullable UUID placerUUID, ServerLevel level, Claim claim) {
        if (placerUUID == null) return false;

        ServerPlayer player = level.getServer().getPlayerList().getPlayer(placerUUID);
        if (player != null) {
            return ClaimManager.getPermissionResolver().canAccess(player, claim);
        }

        if (placerUUID.equals(claim.getOwner())) return true;
        if (claim.isAllowOthers()) return true;

        return false;
    }
}
