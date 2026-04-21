package com.mapter.aeroclaims.claim;

import com.mapter.aeroclaims.config.AeroClaimsConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import xaero.pac.common.server.api.OpenPACServerAPI;
import xaero.pac.common.server.claims.api.IServerClaimsManagerAPI;
import xaero.pac.common.server.claims.player.api.IServerPlayerClaimInfoAPI;
import xaero.pac.common.server.player.config.api.IPlayerConfigAPI;
import xaero.pac.common.server.player.config.api.IPlayerConfigManagerAPI;
import xaero.pac.common.server.player.config.api.PlayerConfigOptions;

import java.util.UUID;

public class AeroClaimManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(AeroClaimManager.class);

    public enum TransferResult {
        SUCCESS,
        OPAC_NOT_LOADED,
        NOT_ENOUGH_FREE,
        API_ERROR
    }


    public static TransferResult transferFromOpac(ServerPlayer player, int amount) {
        if (amount <= 0) return TransferResult.API_ERROR;
        try {
            OpacContext ctx = opacContext(player);
            if (ctx == null) return TransferResult.OPAC_NOT_LOADED;
            if (ctx.freeClaims() < amount) return TransferResult.NOT_ENOUGH_FREE;

            IPlayerConfigAPI.SetResult result = ctx.config().tryToSet(
                    PlayerConfigOptions.BONUS_CHUNK_CLAIMS, ctx.bonusLimit() - amount);
            if (result != IPlayerConfigAPI.SetResult.SUCCESS) return TransferResult.API_ERROR;

            AeroClaimSavedData.get(player.serverLevel()).addMigratedSlots(ctx.playerId(), amount);
            return TransferResult.SUCCESS;
        } catch (Exception e) {
            LOGGER.error("[AeroClaims] transferFromOpac failed", e);
            return TransferResult.API_ERROR;
        }
    }

    public static TransferResult transferToOpac(ServerPlayer player, int amount) {
        if (amount <= 0) return TransferResult.API_ERROR;
        try {
            OpacContext ctx = opacContext(player);
            if (ctx == null) return TransferResult.OPAC_NOT_LOADED;

            AeroClaimSavedData data = AeroClaimSavedData.get(player.serverLevel());
            if (data.getFreeSlots(ctx.playerId()) < amount) return TransferResult.NOT_ENOUGH_FREE;

            int previousMigrated = data.getMigratedSlots(ctx.playerId());
            data.setMigratedSlots(ctx.playerId(), previousMigrated - amount);

            IPlayerConfigAPI.SetResult result = ctx.config().tryToSet(
                    PlayerConfigOptions.BONUS_CHUNK_CLAIMS, ctx.bonusLimit() + amount);
            if (result != IPlayerConfigAPI.SetResult.SUCCESS) {
                data.setMigratedSlots(ctx.playerId(), previousMigrated); // rollback
                return TransferResult.API_ERROR;
            }
            return TransferResult.SUCCESS;
        } catch (Exception e) {
            LOGGER.error("[AeroClaims] transferToOpac failed", e);
            return TransferResult.API_ERROR;
        }
    }


    public static int getBlockLimit(ServerLevel level, BlockPos pos) {
        return AeroClaimSavedData.get(level).getClaimsForBlock(pos)
                * AeroClaimsConfig.BLOCKS_PER_CLAIM.get();
    }


    public static boolean adjustClaimsForBlock(ServerLevel level, UUID owner, BlockPos pos, int delta) {
        AeroClaimSavedData data = AeroClaimSavedData.get(level);
        int newCount = data.getClaimsForBlock(pos) + delta;
        if (newCount < 0) return false;
        if (delta > 0 && data.getFreeSlots(owner) < delta) return false;

        data.setClaimsForBlock(pos, owner, newCount);
        return true;
    }


    public static void releaseAllClaimsForBlock(ServerLevel level, UUID owner, BlockPos pos) {
        AeroClaimSavedData.get(level).removeClaimsForBlock(pos, owner);
    }


    public static int getMigratedSlots(ServerLevel level, UUID playerId) {
        return AeroClaimSavedData.get(level).getMigratedSlots(playerId);
    }

    public static int getUsedSlots(ServerLevel level, UUID playerId) {
        return AeroClaimSavedData.get(level).getUsedSlots(playerId);
    }

    public static int getFreeSlots(ServerLevel level, UUID playerId) {
        return AeroClaimSavedData.get(level).getFreeSlots(playerId);
    }

    public static int getFreeOpacClaims(ServerPlayer player) {
        try {
            OpacContext ctx = opacContext(player);
            return ctx == null ? -1 : ctx.freeClaims();
        } catch (Exception e) {
            LOGGER.error("[AeroClaims] getFreeOpacClaims failed", e);
            return -1;
        }
    }


    private record OpacContext(
            UUID playerId,
            IServerClaimsManagerAPI claimsManager,
            IPlayerConfigAPI config,
            IServerPlayerClaimInfoAPI playerInfo
    ) {
        int freeClaims() {
            int base  = claimsManager.getPlayerBaseClaimLimit(playerId);
            int bonus = config.getRaw(PlayerConfigOptions.BONUS_CHUNK_CLAIMS);
            int used  = playerInfo != null ? playerInfo.getClaimCount() : 0;
            return Math.max(0, base + bonus - used);
        }

        int bonusLimit() {
            return config.getRaw(PlayerConfigOptions.BONUS_CHUNK_CLAIMS);
        }
    }

    private static OpacContext opacContext(ServerPlayer player) {
        OpenPACServerAPI api = OpenPACServerAPI.get(player.server);
        if (api == null) return null;

        IServerClaimsManagerAPI claims = api.getServerClaimsManager();
        IPlayerConfigManagerAPI configs = api.getPlayerConfigs();
        if (claims == null || configs == null) return null;

        UUID id = player.getUUID();
        IPlayerConfigAPI config = configs.getLoadedConfig(id);
        if (config == null) return null;

        return new OpacContext(id, claims, config, claims.getPlayerInfo(id));
    }
}
