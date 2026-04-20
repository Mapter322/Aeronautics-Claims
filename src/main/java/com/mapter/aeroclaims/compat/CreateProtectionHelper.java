package com.mapter.aeroclaims.compat;

import com.mapter.aeroclaims.claim.Claim;
import com.mapter.aeroclaims.claim.ClaimManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;


public class CreateProtectionHelper {


    public static boolean isBlockAccessAllowed(BlockPos pos, ServerPlayer player) {
        if (pos == null || player == null) return true;
        Claim claim = ClaimManager.getClaimAt(player.serverLevel(), pos);
        return claim == null || ClaimManager.PERMISSION_RESOLVER.canAccess(player, claim);
    }
}
