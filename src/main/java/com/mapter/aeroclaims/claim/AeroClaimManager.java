package com.mapter.aeroclaims.claim;

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
            OpenPACServerAPI api = OpenPACServerAPI.get(player.server);
            if (api == null) return TransferResult.OPAC_NOT_LOADED;

            UUID playerId = player.getUUID();

            IServerClaimsManagerAPI claimsManager = api.getServerClaimsManager();
            IPlayerConfigManagerAPI configManager = api.getPlayerConfigs();

            if (claimsManager == null || configManager == null) return TransferResult.OPAC_NOT_LOADED;

            IPlayerConfigAPI config = configManager.getLoadedConfig(playerId);
            if (config == null) return TransferResult.API_ERROR;

            int baseLimit  = claimsManager.getPlayerBaseClaimLimit(playerId);
            int bonusLimit = config.getRaw(PlayerConfigOptions.BONUS_CHUNK_CLAIMS);

            IServerPlayerClaimInfoAPI playerInfo = claimsManager.getPlayerInfo(playerId);
            int usedClaims = playerInfo != null ? playerInfo.getClaimCount() : 0;
            int totalLimit  = baseLimit + bonusLimit;
            int freeClaims  = Math.max(0, totalLimit - usedClaims);

            if (freeClaims < amount) {
                return TransferResult.NOT_ENOUGH_FREE;
            }
            int newBonus = bonusLimit - amount;
            IPlayerConfigAPI.SetResult setResult = config.tryToSet(PlayerConfigOptions.BONUS_CHUNK_CLAIMS, newBonus);
            if (setResult != IPlayerConfigAPI.SetResult.SUCCESS) {
                return TransferResult.API_ERROR;
            }
            AeroClaimSavedData data = AeroClaimSavedData.get(player.serverLevel());
            data.addMigratedSlots(playerId, amount);

            return TransferResult.SUCCESS;
        } catch (Exception e) {
            LOGGER.error("[Aeroclaims] transfer: exception during transfer", e);
            return TransferResult.API_ERROR;
        }
    }

    public static boolean consumeShipClaimSlot(ServerLevel level, UUID playerId) {
        AeroClaimSavedData data = AeroClaimSavedData.get(level);
        if (data.getFreeSlots(playerId) <= 0) {
            return false;
        }
        data.incrementUsedSlots(playerId);
        return true;
    }

    public static void releaseShipClaimSlot(ServerLevel level, UUID playerId) {
        AeroClaimSavedData data = AeroClaimSavedData.get(level);
        data.decrementUsedSlots(playerId);
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
            OpenPACServerAPI api = OpenPACServerAPI.get(player.server);
            if (api == null) return -1;

            UUID playerId = player.getUUID();
            IServerClaimsManagerAPI claimsManager = api.getServerClaimsManager();
            IPlayerConfigManagerAPI configManager = api.getPlayerConfigs();
            if (claimsManager == null || configManager == null) return -1;

            IPlayerConfigAPI config = configManager.getLoadedConfig(playerId);
            if (config == null) return -1;

            int baseLimit  = claimsManager.getPlayerBaseClaimLimit(playerId);
            int bonusLimit = config.getRaw(PlayerConfigOptions.BONUS_CHUNK_CLAIMS);

            IServerPlayerClaimInfoAPI playerInfo = claimsManager.getPlayerInfo(playerId);
            int usedClaims = playerInfo != null ? playerInfo.getClaimCount() : 0;

            return Math.max(0, baseLimit + bonusLimit - usedClaims);
        } catch (Exception e) {
            LOGGER.error("[Aeroclaims] getFreeOpacClaims: exception", e);
            return -1;
        }
    }

    public static TransferResult transferToOpac(ServerPlayer player, int amount) {
        if (amount <= 0) return TransferResult.API_ERROR;

        try {
            OpenPACServerAPI api = OpenPACServerAPI.get(player.server);
            if (api == null) return TransferResult.OPAC_NOT_LOADED;

            UUID playerId = player.getUUID();

            IServerClaimsManagerAPI claimsManager = api.getServerClaimsManager();
            IPlayerConfigManagerAPI configManager = api.getPlayerConfigs();

            if (claimsManager == null || configManager == null) return TransferResult.OPAC_NOT_LOADED;

            IPlayerConfigAPI config = configManager.getLoadedConfig(playerId);
            if (config == null) return TransferResult.API_ERROR;

            AeroClaimSavedData data = AeroClaimSavedData.get(player.serverLevel());
            int freeShipClaims = data.getFreeSlots(playerId);

            if (freeShipClaims < amount) {
                return TransferResult.NOT_ENOUGH_FREE;
            }
            int currentMigrated = data.getMigratedSlots(playerId);
            data.setMigratedSlots(playerId, currentMigrated - amount);
            int bonusLimit = config.getRaw(PlayerConfigOptions.BONUS_CHUNK_CLAIMS);
            int newBonus = bonusLimit + amount;
            IPlayerConfigAPI.SetResult setResult = config.tryToSet(PlayerConfigOptions.BONUS_CHUNK_CLAIMS, newBonus);
            if (setResult != IPlayerConfigAPI.SetResult.SUCCESS) {
                data.setMigratedSlots(playerId, currentMigrated);
                return TransferResult.API_ERROR;
            }

            return TransferResult.SUCCESS;
        } catch (Exception e) {
            LOGGER.error("[Aeroclaims] transfer back: exception during transfer to OPAC", e);
            return TransferResult.API_ERROR;
        }
    }
}
