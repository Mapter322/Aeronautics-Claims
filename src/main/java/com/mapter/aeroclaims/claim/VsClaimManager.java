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


public class VsClaimManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(VsClaimManager.class);

    public enum TransferResult {
        SUCCESS,
        OPAC_NOT_LOADED,
        NOT_ENOUGH_FREE,
        API_ERROR
    }

     //Attempts to transfer amount OPAC claims to ship claims in this mod.
     //Bonus claims are consumed first; if insufficient, base claims are used too.
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

            // Total free claims (base + bonus - used)
            int totalLimit  = baseLimit + bonusLimit;
            int freeClaims  = Math.max(0, totalLimit - usedClaims);

            if (freeClaims < amount) {
                return TransferResult.NOT_ENOUGH_FREE;
            }

            // Decrease BONUS_CHUNK_CLAIMS (may go negative, borrowing from base)
            int newBonus = bonusLimit - amount;
            IPlayerConfigAPI.SetResult setResult = config.tryToSet(PlayerConfigOptions.BONUS_CHUNK_CLAIMS, newBonus);
            if (setResult != IPlayerConfigAPI.SetResult.SUCCESS) {
                return TransferResult.API_ERROR;
            }

            // Add claims to our mod
            VsClaimSavedData data = VsClaimSavedData.get(player.serverLevel());
            data.addMigratedSlots(playerId, amount);

            return TransferResult.SUCCESS;
        } catch (Exception e) {
            LOGGER.error("[VSClaims] transfer: exception during transfer", e);
            return TransferResult.API_ERROR;
        }
    }


     //Attempts to consume 1 ship claim (on claim block activation).
     //Returns true if the claim was successfully consumed.
    public static boolean consumeShipClaimSlot(ServerLevel level, UUID playerId) {
        VsClaimSavedData data = VsClaimSavedData.get(level);
        if (data.getFreeSlots(playerId) <= 0) {
            return false;
        }
        data.incrementUsedSlots(playerId);
        return true;
    }


     //Release 1 ship claim (on claim block removal).
    public static void releaseShipClaimSlot(ServerLevel level, UUID playerId) {
        VsClaimSavedData data = VsClaimSavedData.get(level);
        data.decrementUsedSlots(playerId);
    }

     //Get the number of transferred claims (maximum).
    public static int getMigratedSlots(ServerLevel level, UUID playerId) {
        return VsClaimSavedData.get(level).getMigratedSlots(playerId);
    }

     //Get the number of used claims.
    public static int getUsedSlots(ServerLevel level, UUID playerId) {
        return VsClaimSavedData.get(level).getUsedSlots(playerId);
    }

     //Get the number of free claims.
    public static int getFreeSlots(ServerLevel level, UUID playerId) {
        return VsClaimSavedData.get(level).getFreeSlots(playerId);
    }


     //Get the number of free OPAC claims available for transfer (base + bonus - used).
     //Returns -1 if OPAC is not loaded.
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
            LOGGER.error("[VSClaims] getFreeOpacClaims: exception", e);
            return -1;
        }
    }

    //Transfer ship claims back to OPAC bonus claims.
    //Only free ship claims (transferred - used) can be transferred back.
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

            VsClaimSavedData data = VsClaimSavedData.get(player.serverLevel());
            int freeShipClaims = data.getFreeSlots(playerId);

            if (freeShipClaims < amount) {
                return TransferResult.NOT_ENOUGH_FREE;
            }

            // Decrease transferred slots in our mod
            int currentMigrated = data.getMigratedSlots(playerId);
            data.setMigratedSlots(playerId, currentMigrated - amount);

            // Increase BONUS_CHUNK_CLAIMS in OPAC
            int bonusLimit = config.getRaw(PlayerConfigOptions.BONUS_CHUNK_CLAIMS);
            int newBonus = bonusLimit + amount;
            IPlayerConfigAPI.SetResult setResult = config.tryToSet(PlayerConfigOptions.BONUS_CHUNK_CLAIMS, newBonus);
            if (setResult != IPlayerConfigAPI.SetResult.SUCCESS) {
                // Rollback on failure
                data.setMigratedSlots(playerId, currentMigrated);
                return TransferResult.API_ERROR;
            }

            return TransferResult.SUCCESS;
        } catch (Exception e) {
            LOGGER.error("[VSClaims] transfer back: exception during transfer to OPAC", e);
            return TransferResult.API_ERROR;
        }
    }
}
