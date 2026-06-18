package com.mapter.aeroclaims.claim;

import com.mapter.aeroclaims.config.AeroClaimsConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

public class AeroClaimManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(AeroClaimManager.class);

    private static IClaimProvider CLAIM_PROVIDER = null;
    private static boolean opacLoaded = false;
    private static boolean ftbChunksLoaded = false;

    public enum TransferResult {
        SUCCESS,
        CLAIM_PROVIDER_UNAVAILABLE,
        NOT_ENOUGH_FREE,
        API_ERROR
    }

    public static void init(boolean opacLoaded, boolean ftbChunksLoaded) {
        AeroClaimManager.opacLoaded = opacLoaded;
        AeroClaimManager.ftbChunksLoaded = ftbChunksLoaded;
        CLAIM_PROVIDER = null;

        if (!opacLoaded && !ftbChunksLoaded) {
            LOGGER.warn("[AeroClaims] No claim provider available.");
        }
    }

    public static IClaimProvider getClaimProvider() {
        if (CLAIM_PROVIDER == null) {
            CLAIM_PROVIDER = buildProvider();
        }
        return CLAIM_PROVIDER;
    }

    private static IClaimProvider buildProvider() {
        AeroClaimsConfig.ClaimProvider configProvider = AeroClaimsConfig.CLAIM_PROVIDER.get();

        return switch (configProvider) {
            case OPAC -> {
                if (opacLoaded) {
                    LOGGER.info("[AeroClaims] Using Open Parties and Claims as claim provider.");
                    yield new OpacClaimProvider();
                } else {
                    yield null;
                }
            }
            case FTB_CHUNKS -> {
                if (ftbChunksLoaded) {
                    LOGGER.info("[AeroClaims] Using FTB Chunks as claim provider.");
                    yield new FtbChunksClaimProvider();
                } else {
                    yield null;
                }
            }
        };
    }

    // Slot transfer

    public static TransferResult transferFromProvider(ServerPlayer player, int amount) {
        IClaimProvider provider = getClaimProvider();
        if (provider == null) return TransferResult.CLAIM_PROVIDER_UNAVAILABLE;
        return provider.transferToAero(player, amount);
    }

    public static TransferResult transferToProvider(ServerPlayer player, int amount) {
        IClaimProvider provider = getClaimProvider();
        if (provider == null) return TransferResult.CLAIM_PROVIDER_UNAVAILABLE;
        return provider.transferFromAero(player, amount);
    }

    // Slots per claim block

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

    public static int getFreeProviderClaims(ServerPlayer player) {
        IClaimProvider provider = getClaimProvider();
        if (provider == null) return -1;
        return provider.getFreeClaims(player);
    }
}
