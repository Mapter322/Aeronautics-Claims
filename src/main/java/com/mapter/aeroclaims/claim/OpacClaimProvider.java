package com.mapter.aeroclaims.claim;

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

public class OpacClaimProvider implements IClaimProvider {

    private static final Logger LOGGER = LoggerFactory.getLogger(OpacClaimProvider.class);

    @Override
    public AeroClaimManager.TransferResult transferToAero(ServerPlayer player, int amount) {
        if (amount <= 0) return AeroClaimManager.TransferResult.API_ERROR;
        try {
            OpacContext ctx = buildOpacContext(player);
            if (ctx == null) return AeroClaimManager.TransferResult.CLAIM_PROVIDER_UNAVAILABLE;
            if (ctx.freeClaims() < amount) return AeroClaimManager.TransferResult.NOT_ENOUGH_FREE;

            IPlayerConfigAPI.SetResult result = ctx.config().tryToSet(
                    PlayerConfigOptions.BONUS_CHUNK_CLAIMS, ctx.bonusLimit() - amount);
            if (result != IPlayerConfigAPI.SetResult.SUCCESS) return AeroClaimManager.TransferResult.API_ERROR;

            AeroClaimSavedData.get(player.serverLevel()).addMigratedSlots(ctx.playerId(), amount);
            return AeroClaimManager.TransferResult.SUCCESS;
        } catch (Exception e) {
            LOGGER.error("[AeroClaims] transferToAero (OPAC) failed", e);
            return AeroClaimManager.TransferResult.API_ERROR;
        }
    }

    @Override
    public AeroClaimManager.TransferResult transferFromAero(ServerPlayer player, int amount) {
        if (amount <= 0) return AeroClaimManager.TransferResult.API_ERROR;
        try {
            OpacContext ctx = buildOpacContext(player);
            if (ctx == null) return AeroClaimManager.TransferResult.CLAIM_PROVIDER_UNAVAILABLE;

            AeroClaimSavedData data = AeroClaimSavedData.get(player.serverLevel());
            if (data.getFreeSlots(ctx.playerId()) < amount) return AeroClaimManager.TransferResult.NOT_ENOUGH_FREE;

            int previousMigrated = data.getMigratedSlots(ctx.playerId());
            data.setMigratedSlots(ctx.playerId(), previousMigrated - amount);

            IPlayerConfigAPI.SetResult result = ctx.config().tryToSet(
                    PlayerConfigOptions.BONUS_CHUNK_CLAIMS, ctx.bonusLimit() + amount);
            if (result != IPlayerConfigAPI.SetResult.SUCCESS) {
                data.setMigratedSlots(ctx.playerId(), previousMigrated);
                return AeroClaimManager.TransferResult.API_ERROR;
            }
            return AeroClaimManager.TransferResult.SUCCESS;
        } catch (Exception e) {
            LOGGER.error("[AeroClaims] transferFromAero (OPAC) failed", e);
            return AeroClaimManager.TransferResult.API_ERROR;
        }
    }

    @Override
    public int getFreeClaims(ServerPlayer player) {
        try {
            OpacContext ctx = buildOpacContext(player);
            return ctx == null ? -1 : ctx.freeClaims();
        } catch (Exception e) {
            LOGGER.error("[AeroClaims] getFreeClaims (OPAC) failed", e);
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
