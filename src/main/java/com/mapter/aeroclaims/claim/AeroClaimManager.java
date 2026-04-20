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

    private record OpacContext(
            UUID playerId,
            IServerClaimsManagerAPI claimsManager,
            IPlayerConfigAPI config,
            IServerPlayerClaimInfoAPI playerInfo
    ) {
        int getFreeClaims() {
            int baseLimit = claimsManager.getPlayerBaseClaimLimit(playerId);
            int bonusLimit = config.getRaw(PlayerConfigOptions.BONUS_CHUNK_CLAIMS);
            int usedClaims = playerInfo != null ? playerInfo.getClaimCount() : 0;
            return Math.max(0, baseLimit + bonusLimit - usedClaims);
        }

        int getBonusLimit() {
            return config.getRaw(PlayerConfigOptions.BONUS_CHUNK_CLAIMS);
        }
    }

    public enum TransferResult {
        SUCCESS,
        OPAC_NOT_LOADED,
        NOT_ENOUGH_FREE,
        API_ERROR
    }

    public static TransferResult transferFromOpac(ServerPlayer player, int amount) {
        if (amount <= 0) return TransferResult.API_ERROR;

        try {
            OpacContext opac = getOpacContext(player);
            if (opac == null) return TransferResult.OPAC_NOT_LOADED;

            if (opac.getFreeClaims() < amount) {
                return TransferResult.NOT_ENOUGH_FREE;
            }
            int newBonus = opac.getBonusLimit() - amount;
            IPlayerConfigAPI.SetResult setResult = opac.config().tryToSet(PlayerConfigOptions.BONUS_CHUNK_CLAIMS, newBonus);
            if (setResult != IPlayerConfigAPI.SetResult.SUCCESS) {
                return TransferResult.API_ERROR;
            }
            AeroClaimSavedData data = AeroClaimSavedData.get(player.serverLevel());
            data.addMigratedSlots(opac.playerId(), amount);

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
            OpacContext opac = getOpacContext(player);
            return opac == null ? -1 : opac.getFreeClaims();
        } catch (Exception e) {
            LOGGER.error("[Aeroclaims] getFreeOpacClaims: exception", e);
            return -1;
        }
    }

    public static TransferResult transferToOpac(ServerPlayer player, int amount) {
        if (amount <= 0) return TransferResult.API_ERROR;

        try {
            OpacContext opac = getOpacContext(player);
            if (opac == null) return TransferResult.OPAC_NOT_LOADED;

            AeroClaimSavedData data = AeroClaimSavedData.get(player.serverLevel());
            int freeShipClaims = data.getFreeSlots(opac.playerId());

            if (freeShipClaims < amount) {
                return TransferResult.NOT_ENOUGH_FREE;
            }
            int currentMigrated = data.getMigratedSlots(opac.playerId());
            data.setMigratedSlots(opac.playerId(), currentMigrated - amount);
            int bonusLimit = opac.getBonusLimit();
            int newBonus = bonusLimit + amount;
            IPlayerConfigAPI.SetResult setResult = opac.config().tryToSet(PlayerConfigOptions.BONUS_CHUNK_CLAIMS, newBonus);
            if (setResult != IPlayerConfigAPI.SetResult.SUCCESS) {
                data.setMigratedSlots(opac.playerId(), currentMigrated);
                return TransferResult.API_ERROR;
            }

            return TransferResult.SUCCESS;
        } catch (Exception e) {
            LOGGER.error("[Aeroclaims] transfer back: exception during transfer to OPAC", e);
            return TransferResult.API_ERROR;
        }
    }

    private static OpacContext getOpacContext(ServerPlayer player) {
        OpenPACServerAPI api = OpenPACServerAPI.get(player.server);
        if (api == null) {
            return null;
        }

        UUID playerId = player.getUUID();
        IServerClaimsManagerAPI claimsManager = api.getServerClaimsManager();
        IPlayerConfigManagerAPI configManager = api.getPlayerConfigs();
        if (claimsManager == null || configManager == null) {
            return null;
        }

        IPlayerConfigAPI config = configManager.getLoadedConfig(playerId);
        if (config == null) {
            return null;
        }

        IServerPlayerClaimInfoAPI playerInfo = claimsManager.getPlayerInfo(playerId);
        return new OpacContext(playerId, claimsManager, config, playerInfo);
    }
}
