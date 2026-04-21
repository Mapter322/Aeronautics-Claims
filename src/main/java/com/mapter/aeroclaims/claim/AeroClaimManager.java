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

// Manages AeroClaims claim slots and integration with Open Parties and Claims (OPAC).
// Slots are claim "capacity" units. One slot protects blocksPerClaim ship blocks.
// Slots can be transferred between OPAC claims and aero claims
public class AeroClaimManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(AeroClaimManager.class);

    public enum TransferResult {
        SUCCESS,
        OPAC_NOT_LOADED,
        NOT_ENOUGH_FREE,
        API_ERROR
    }

    // Slot transfer between OPAC and Aero

    // Transfers specified number of slots from OPAC claims to aero claims.
    // Decreases OPAC bonus limit and records migrated slots.
    public static TransferResult transferFromOpac(ServerPlayer player, int amount) {
        if (amount <= 0) return TransferResult.API_ERROR;
        try {
            OpacContext ctx = buildOpacContext(player);
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

    // Returns specified number of slots back to OPAC claims.
    // Increases OPAC bonus limit and decreases migrated slots.
    public static TransferResult transferToOpac(ServerPlayer player, int amount) {
        if (amount <= 0) return TransferResult.API_ERROR;
        try {
            OpacContext ctx = buildOpacContext(player);
            if (ctx == null) return TransferResult.OPAC_NOT_LOADED;

            AeroClaimSavedData data = AeroClaimSavedData.get(player.serverLevel());
            if (data.getFreeSlots(ctx.playerId()) < amount) return TransferResult.NOT_ENOUGH_FREE;

            // Change local data first, then OPAC - rollback on error
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

    // Block slot management

    // Returns maximum allowed ship blocks for this claim block.
    public static int getBlockLimit(ServerLevel level, BlockPos pos) {
        return AeroClaimSavedData.get(level).getClaimsForBlock(pos)
                * AeroClaimsConfig.BLOCKS_PER_CLAIM.get();
    }

    // Changes allocated slots for claim block by delta.
    // Returns false if no free slots or result would go negative.
    public static boolean adjustClaimsForBlock(ServerLevel level, UUID owner, BlockPos pos, int delta) {
        AeroClaimSavedData data = AeroClaimSavedData.get(level);
        int newCount = data.getClaimsForBlock(pos) + delta;
        if (newCount < 0) return false;
        if (delta > 0 && data.getFreeSlots(owner) < delta) return false;

        data.setClaimsForBlock(pos, owner, newCount);
        return true;
    }

    // Releases all slots occupied by this claim block.
    public static void releaseAllClaimsForBlock(ServerLevel level, UUID owner, BlockPos pos) {
        AeroClaimSavedData.get(level).removeClaimsForBlock(pos, owner);
    }

    // Statistics queries

    public static int getMigratedSlots(ServerLevel level, UUID playerId) {
        return AeroClaimSavedData.get(level).getMigratedSlots(playerId);
    }

    public static int getUsedSlots(ServerLevel level, UUID playerId) {
        return AeroClaimSavedData.get(level).getUsedSlots(playerId);
    }

    public static int getFreeSlots(ServerLevel level, UUID playerId) {
        return AeroClaimSavedData.get(level).getFreeSlots(playerId);
    }

    // Returns player's free OPAC claim count.
    // Returns -1 if OPAC not loaded or error occurred.
    public static int getFreeOpacClaims(ServerPlayer player) {
        try {
            OpacContext ctx = buildOpacContext(player);
            return ctx == null ? -1 : ctx.freeClaims();
        } catch (Exception e) {
            LOGGER.error("[AeroClaims] getFreeOpacClaims failed", e);
            return -1;
        }
    }

    // Internal OPAC API work

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

    // Builds OPAC context for player. Returns null if OPAC unavailable.
    private static OpacContext buildOpacContext(ServerPlayer player) {
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
