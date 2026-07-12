package com.mapter.aeroclaims.claim;

import net.minecraft.server.level.ServerPlayer;

public interface IClaimProvider {

    AeroClaimManager.TransferResult transferToAero(ServerPlayer player, int amount);
    AeroClaimManager.TransferResult transferFromAero(ServerPlayer player, int amount);
    int getFreeClaims(ServerPlayer player);

    AeroClaimManager.TransferResult transferForceloadsToAero(ServerPlayer player, int amount);
    AeroClaimManager.TransferResult transferForceloadsFromAero(ServerPlayer player, int amount);
    int getFreeForceloads(ServerPlayer player);
}
