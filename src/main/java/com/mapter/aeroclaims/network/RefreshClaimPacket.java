package com.mapter.aeroclaims.network;

import com.mapter.aeroclaims.Aeroclaims;
import com.mapter.aeroclaims.claim.AeroClaimManager;
import com.mapter.aeroclaims.claim.AeroClaimManager.TransferResult;
import com.mapter.aeroclaims.claim.AeroClaimSavedData;
import com.mapter.aeroclaims.claim.Claim;
import com.mapter.aeroclaims.claim.ClaimManager;
import com.mapter.aeroclaims.claim.ClaimPreviewManager;
import com.mapter.aeroclaims.claim.ClaimSavedData;
import com.mapter.aeroclaims.config.AeroClaimsConfig;
import com.mapter.aeroclaims.sublevel.SubLevelTicketManager;
import com.mapter.aeroclaims.sublevel.SableShipUtils;
import com.mapter.aeroclaims.util.TeamColorHelper;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record RefreshClaimPacket(BlockPos center) implements CustomPacketPayload {

    public static final Type<RefreshClaimPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Aeroclaims.MODID, "refresh_claim"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RefreshClaimPacket> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, RefreshClaimPacket::center,
                    RefreshClaimPacket::new
            );

    @Override
    public Type<RefreshClaimPacket> type() { return TYPE; }

    public static void handle(RefreshClaimPacket msg, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;

            ServerLevel level = player.serverLevel();
            Claim claim = ClaimManager.getClaimByCenter(level, msg.center);
            if (claim == null || !player.getUUID().equals(claim.getOwner())) return;

            if (!SableShipUtils.isOnShip(level, msg.center)) {
                player.sendSystemMessage(Component.translatable("message.aeroclaims.not_on_sublevel"));
                return;
            }

            if (hasDuplicateClaimBlock(level, msg.center)) {
                player.sendSystemMessage(Component.translatable("message.aeroclaims.duplicate_claim_block"));
                return;
            }

            boolean deactivateOnOverflow = AeroClaimsConfig.DEACTIVATE_ON_OVERFLOW.get();
            int blockCount = ClaimManager.countShipBlocksExact(level, msg.center);
            ClaimPreviewManager.invalidate(level, msg.center);

            int blocksPerClaim = AeroClaimsConfig.BLOCKS_PER_CLAIM.get();
            int neededClaims = (blockCount + blocksPerClaim - 1) / blocksPerClaim;
            AeroClaimSavedData data = AeroClaimSavedData.get(level);
            int currentClaims = data.getClaimsForBlock(msg.center);
            int delta = neededClaims - currentClaims;
            boolean useProvider = ClaimManager.isForceloadActive(claim) && AeroClaimsConfig.PROVIDER_SLOTS_FORCELOAD.get();

            if (delta > 0) {
                int freeSlots = data.getFreeSlots(player.getUUID());
                int claimNeed = Math.max(0, delta - freeSlots);
                if (claimNeed > 0) {
                    TransferResult r = AeroClaimManager.transferFromProvider(player, claimNeed);
                    if (r != TransferResult.SUCCESS) {
                        handleInsufficientClaims(player, level, msg.center, claim, blockCount,
                                currentClaims, deactivateOnOverflow);
                        return;
                    }
                    freeSlots += claimNeed;
                }

                if (freeSlots < delta) {
                    if (claimNeed > 0) AeroClaimManager.transferToProvider(player, claimNeed);
                    handleInsufficientClaims(player, level, msg.center, claim, blockCount,
                            currentClaims, deactivateOnOverflow);
                    return;
                }

                if (useProvider) {
                    int freeForceloads = data.getFreeForceloads(player.getUUID());
                    int forceloadNeed = Math.max(0, delta - freeForceloads);
                    if (forceloadNeed > 0) {
                        TransferResult r = AeroClaimManager.transferForceloadsFromProvider(player, forceloadNeed);
                        if (r != TransferResult.SUCCESS) {
                            if (claimNeed > 0) AeroClaimManager.transferToProvider(player, claimNeed);
                            handleInsufficientClaims(player, level, msg.center, claim, blockCount,
                                    currentClaims, deactivateOnOverflow);
                            return;
                        }
                    }
                }

                if (!AeroClaimManager.adjustClaimsForBlock(level, player.getUUID(), msg.center, delta)) {
                    if (claimNeed > 0) AeroClaimManager.transferToProvider(player, claimNeed);
                    handleInsufficientClaims(player, level, msg.center, claim, blockCount,
                            currentClaims, deactivateOnOverflow);
                    return;
                }
                if (useProvider) {
                    AeroClaimManager.adjustForceloadsForBlock(level, player.getUUID(), msg.center, delta);
                }
            } else if (delta < 0) {
                AeroClaimManager.adjustClaimsForBlock(level, player.getUUID(), msg.center, delta);
                if (useProvider) AeroClaimManager.adjustForceloadsForBlock(level, player.getUUID(), msg.center, delta);
            }

            if (!ClaimManager.refreshClaim(level, msg.center)) {
                player.sendSystemMessage(Component.translatable("message.aeroclaims.refresh_failed"));
                sync(player, msg.center, claim, level, blockCount);
                return;
            }

            cacheShipStructure(level, msg.center, blockCount);

            Claim updated = ClaimManager.getClaimByCenter(level, msg.center);
            player.sendSystemMessage(Component.translatable("message.aeroclaims.claim_recounted"));
            if (updated != null) {
                int teamColor = TeamColorHelper.getTeamColor(player, updated.getOwner());
                PacketDistributor.sendToPlayer(player,
                        new ClaimRefreshParticlesPacket(new java.util.ArrayList<>(updated.getClaimedBlocks()), teamColor));
            }
            sync(player, msg.center, updated != null ? updated : claim, level, blockCount);
        });
    }

    private static void handleInsufficientClaims(ServerPlayer player, ServerLevel level, BlockPos center,
                                                 Claim claim, int blockCount, int currentClaims,
                                                 boolean deactivateOnOverflow) {
        int currentLimit = currentClaims * AeroClaimsConfig.BLOCKS_PER_CLAIM.get();
        if (deactivateOnOverflow && blockCount > currentLimit) {
            AeroClaimManager.releaseAllClaimsForBlock(level, player, center);
            if (AeroClaimsConfig.isProviderSlotsForceload()) {
                AeroClaimManager.releaseAllForceloadsForBlock(level, player, center);
            }
            SubLevelTicketManager.sync(level, claim, claim.getShipId(), false);
            ClaimManager.deactivateClaim(level, center);
            player.sendSystemMessage(Component.translatable(
                    "message.aeroclaims.sublevel_too_large_deactivated", blockCount, currentLimit));
        } else {
            player.sendSystemMessage(Component.translatable(
                    "message.aeroclaims.sublevel_too_large", blockCount, currentLimit));
        }

        sync(player, center, claim, level, blockCount);
    }

    private static boolean hasDuplicateClaimBlock(ServerLevel level, BlockPos center) {
        var ship = SableShipUtils.getShipAt(level, center);
        String shipId = SableShipUtils.getShipId(ship);
        if (shipId == null) return false;

        for (Claim other : ClaimSavedData.get(level).getClaims()) {
            if (other.getCenter().equals(center)) continue;
            String otherId = SableShipUtils.getShipId(SableShipUtils.getShipAt(level, other.getCenter()));
            if (shipId.equals(otherId)) return true;
        }
        return false;
    }

    private static void cacheShipStructure(ServerLevel level, BlockPos center, int blockCount) {
        var ship = SableShipUtils.getShipAt(level, center);
        String shipId = SableShipUtils.getShipId(ship);

        AeroClaimSavedData data = AeroClaimSavedData.get(level);
        data.cacheShipBlockCount(center, blockCount);
        if (shipId != null) {
            data.cacheShipId(center, shipId);
        }
    }

    private static void sync(ServerPlayer player, BlockPos center, Claim claim,
                              ServerLevel level, int shipBlockCount) {
        AeroClaimSavedData data = AeroClaimSavedData.get(level);
        PacketDistributor.sendToPlayer(player, new SyncClaimStatePacket(
                center,
                claim.isActive(),
                claim.isAllowParty(),
                claim.isAllowAllies(),
                claim.isAllowOthers(),
                data.getClaimsForBlock(center),
                data.getFreeSlots(player.getUUID()),
                AeroClaimsConfig.BLOCKS_PER_CLAIM.get(),
                shipBlockCount,
                data.getForceloadsForBlock(center),
                ClaimManager.isForceloadActive(claim)
        ));
    }
}
