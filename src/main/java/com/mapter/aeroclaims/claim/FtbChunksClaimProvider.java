package com.mapter.aeroclaims.claim;

import dev.ftb.mods.ftbchunks.api.ClaimedChunkManager;
import dev.ftb.mods.ftbchunks.api.ChunkTeamData;
import dev.ftb.mods.ftbchunks.api.FTBChunksAPI;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FtbChunksClaimProvider implements IClaimProvider {

    private static final Logger LOGGER = LoggerFactory.getLogger(FtbChunksClaimProvider.class);

    @Override
    public AeroClaimManager.TransferResult transferToAero(ServerPlayer player, int amount) {
        if (amount <= 0) return AeroClaimManager.TransferResult.API_ERROR;
        try {
            ClaimedChunkManager mgr = getManager();
            if (mgr == null) return AeroClaimManager.TransferResult.CLAIM_PROVIDER_UNAVAILABLE;

            ChunkTeamData team = mgr.getOrCreateData(player);
            if (team == null) return AeroClaimManager.TransferResult.CLAIM_PROVIDER_UNAVAILABLE;

            int free = Math.max(0, team.getMaxClaimChunks() - team.getClaimedChunks().size());
            if (free < amount) return AeroClaimManager.TransferResult.NOT_ENOUGH_FREE;

            AeroClaimSavedData.get(player.serverLevel()).addMigratedSlots(player.getUUID(), amount);
            return AeroClaimManager.TransferResult.SUCCESS;
        } catch (Exception e) {
            LOGGER.error("[AeroClaims] transferToAero failed", e);
            return AeroClaimManager.TransferResult.API_ERROR;
        }
    }

    @Override
    public AeroClaimManager.TransferResult transferFromAero(ServerPlayer player, int amount) {
        if (amount <= 0) return AeroClaimManager.TransferResult.API_ERROR;
        try {
            AeroClaimSavedData data = AeroClaimSavedData.get(player.serverLevel());
            var id = player.getUUID();
            if (data.getFreeSlots(id) < amount)
                return AeroClaimManager.TransferResult.NOT_ENOUGH_FREE;

            data.setMigratedSlots(id, data.getMigratedSlots(id) - amount);
            return AeroClaimManager.TransferResult.SUCCESS;
        } catch (Exception e) {
            LOGGER.error("[AeroClaims] transferFromAero failed", e);
            return AeroClaimManager.TransferResult.API_ERROR;
        }
    }

    @Override
    public int getFreeClaims(ServerPlayer player) {
        try {
            ClaimedChunkManager mgr = getManager();
            if (mgr == null) return -1;
            ChunkTeamData team = mgr.getOrCreateData(player);
            if (team == null) return -1;
            return Math.max(0, team.getMaxClaimChunks() - team.getClaimedChunks().size());
        } catch (Exception e) {
            LOGGER.error("[AeroClaims] getFreeClaims failed", e);
            return -1;
        }
    }

    @Override
    public AeroClaimManager.TransferResult transferForceloadsToAero(ServerPlayer player, int amount) {
        if (amount <= 0) return AeroClaimManager.TransferResult.API_ERROR;
        try {
            ClaimedChunkManager mgr = getManager();
            if (mgr == null) return AeroClaimManager.TransferResult.CLAIM_PROVIDER_UNAVAILABLE;

            ChunkTeamData team = mgr.getOrCreateData(player);
            if (team == null) return AeroClaimManager.TransferResult.CLAIM_PROVIDER_UNAVAILABLE;

            int free = Math.max(0, team.getMaxForceLoadChunks() - team.getForceLoadedChunks().size());
            if (free < amount) return AeroClaimManager.TransferResult.NOT_ENOUGH_FREE;

            AeroClaimSavedData.get(player.serverLevel()).addMigratedForceloads(player.getUUID(), amount);
            return AeroClaimManager.TransferResult.SUCCESS;
        } catch (Exception e) {
            LOGGER.error("[AeroClaims] transferForceloadsToAero failed", e);
            return AeroClaimManager.TransferResult.API_ERROR;
        }
    }

    @Override
    public AeroClaimManager.TransferResult transferForceloadsFromAero(ServerPlayer player, int amount) {
        if (amount <= 0) return AeroClaimManager.TransferResult.API_ERROR;
        try {
            AeroClaimSavedData data = AeroClaimSavedData.get(player.serverLevel());
            var id = player.getUUID();
            if (data.getFreeForceloads(id) < amount)
                return AeroClaimManager.TransferResult.NOT_ENOUGH_FREE;

            data.setMigratedForceloads(id, data.getMigratedForceloads(id) - amount);
            return AeroClaimManager.TransferResult.SUCCESS;
        } catch (Exception e) {
            LOGGER.error("[AeroClaims] transferForceloadsFromAero failed", e);
            return AeroClaimManager.TransferResult.API_ERROR;
        }
    }

    @Override
    public int getFreeForceloads(ServerPlayer player) {
        try {
            ClaimedChunkManager mgr = getManager();
            if (mgr == null) return -1;
            ChunkTeamData team = mgr.getOrCreateData(player);
            if (team == null) return -1;
            return Math.max(0, team.getMaxForceLoadChunks() - team.getForceLoadedChunks().size());
        } catch (Exception e) {
            LOGGER.error("[AeroClaims] getFreeForceloads failed", e);
            return -1;
        }
    }

    private static ClaimedChunkManager getManager() {
        if (!FTBChunksAPI.api().isManagerLoaded()) return null;
        return FTBChunksAPI.api().getManager();
    }
}
