package com.mapter.aeroclaims.permission;

import com.mapter.aeroclaims.claim.Claim;
import net.minecraft.server.level.ServerPlayer;

public interface ClaimPermissionResolver {
    boolean canAccess(ServerPlayer player, Claim claim);
}