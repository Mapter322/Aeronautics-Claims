package com.mapter.aeroclaims.claim;

import com.mapter.aeroclaims.Aeroclaims;
import com.mapter.aeroclaims.config.AeroClaimsConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

@EventBusSubscriber(modid = Aeroclaims.MODID)
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

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        ClaimManager.getPermissionResolver();
        getClaimProvider();
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
                    LOGGER.warn("[AeroClaims] claimProvider = OPAC, but 'openpartiesandclaims' is not loaded! " +
                            "Claim slot transfers will be unavailable.");
                    yield null;
                }
            }
            case FTB_CHUNKS -> {
                if (ftbChunksLoaded) {
                    LOGGER.info("[AeroClaims] Using FTB Chunks as claim provider.");
                    yield new FtbChunksClaimProvider();
                } else {
                    LOGGER.warn("[AeroClaims] claimProvider = FTB_CHUNKS, but 'ftbchunks' is not loaded! " +
                            "Claim slot transfers will be unavailable.");
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
        int current = AeroClaimSavedData.get(level).getClaimsForBlock(pos);
        if (current == 0) return;

        AeroClaimSavedData data = AeroClaimSavedData.get(level);
        data.removeClaimsForBlock(pos, owner);

        ServerPlayer player = level.getServer().getPlayerList().getPlayer(owner);
        if (player != null) {
            transferToProvider(player, current);
        }

    }

    public static void releaseAllClaimsForBlock(ServerLevel level, ServerPlayer player, BlockPos pos) {
        int current = AeroClaimSavedData.get(level).getClaimsForBlock(pos);
        if (current == 0) return;

        AeroClaimSavedData.get(level).removeClaimsForBlock(pos, player.getUUID());
        transferToProvider(player, current);
    }

    public static boolean tryEnsureSlots(ServerLevel level, ServerPlayer player, BlockPos pos, int needed) {
        if (needed <= 0) return true;

        AeroClaimSavedData data = AeroClaimSavedData.get(level);
        UUID owner = player.getUUID();
        int free = data.getFreeSlots(owner);

        if (free < needed) {
            int transferAmount = needed - free;
            TransferResult result = transferFromProvider(player, transferAmount);
            if (result != TransferResult.SUCCESS) {
                return false;
            }
        }

        return adjustClaimsForBlock(level, owner, pos, needed);
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

    // Forceloads

    public static TransferResult transferForceloadsFromProvider(ServerPlayer player, int amount) {
        IClaimProvider provider = getClaimProvider();
        if (provider == null) return TransferResult.CLAIM_PROVIDER_UNAVAILABLE;
        return provider.transferForceloadsToAero(player, amount);
    }

    public static TransferResult transferForceloadsToProvider(ServerPlayer player, int amount) {
        IClaimProvider provider = getClaimProvider();
        if (provider == null) return TransferResult.CLAIM_PROVIDER_UNAVAILABLE;
        return provider.transferForceloadsFromAero(player, amount);
    }

    public static boolean adjustForceloadsForBlock(ServerLevel level, UUID owner, BlockPos pos, int delta) {
        AeroClaimSavedData data = AeroClaimSavedData.get(level);
        int newCount = data.getForceloadsForBlock(pos) + delta;
        if (newCount < 0) return false;
        if (delta > 0 && data.getFreeForceloads(owner) < delta) return false;
        data.setForceloadsForBlock(pos, owner, newCount);
        return true;
    }

    public static void releaseAllForceloadsForBlock(ServerLevel level, UUID owner, BlockPos pos) {
        int current = AeroClaimSavedData.get(level).getForceloadsForBlock(pos);
        if (current == 0) return;
        AeroClaimSavedData data = AeroClaimSavedData.get(level);
        data.removeForceloadsForBlock(pos, owner);
        ServerPlayer player = level.getServer().getPlayerList().getPlayer(owner);
        if (player != null) transferForceloadsToProvider(player, current);
    }

    public static void releaseAllForceloadsForBlock(ServerLevel level, ServerPlayer player, BlockPos pos) {
        int current = AeroClaimSavedData.get(level).getForceloadsForBlock(pos);
        if (current == 0) return;
        AeroClaimSavedData.get(level).removeForceloadsForBlock(pos, player.getUUID());
        transferForceloadsToProvider(player, current);
    }

    public static boolean tryEnsureForceloads(ServerLevel level, ServerPlayer player, BlockPos pos, int needed) {
        if (needed <= 0) return true;
        AeroClaimSavedData data = AeroClaimSavedData.get(level);
        UUID owner = player.getUUID();
        int free = data.getFreeForceloads(owner);
        if (free < needed) {
            TransferResult result = transferForceloadsFromProvider(player, needed - free);
            if (result != TransferResult.SUCCESS) return false;
        }
        return adjustForceloadsForBlock(level, owner, pos, needed);
    }

    public static int getFreeForceloads(ServerLevel level, UUID playerId) {
        return AeroClaimSavedData.get(level).getFreeForceloads(playerId);
    }

    public static int getMigratedForceloads(ServerLevel level, UUID playerId) {
        return AeroClaimSavedData.get(level).getMigratedForceloads(playerId);
    }

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        ServerLevel level = player.serverLevel();
        UUID id = player.getUUID();
        int freeSlots = getFreeSlots(level, id);
        int freeForceloads = getFreeForceloads(level, id);

        if (freeSlots > 0) transferToProvider(player, freeSlots);
        if (freeForceloads > 0) transferForceloadsToProvider(player, freeForceloads);
    }
}
