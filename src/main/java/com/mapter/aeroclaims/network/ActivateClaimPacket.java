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
import com.mapter.aeroclaims.sublevel.RegisteredSublevelManager;
import com.mapter.aeroclaims.sublevel.SableShipUtils;
import com.mapter.aeroclaims.sublevel.SubLevelTicketManager;
import com.mapter.aeroclaims.sublevel.UnregisteredSublevelManager;
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

import java.util.ArrayList;

public record ActivateClaimPacket(BlockPos center) implements CustomPacketPayload {

    public static final Type<ActivateClaimPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Aeroclaims.MODID, "activate_claim"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ActivateClaimPacket> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, ActivateClaimPacket::center,
                    ActivateClaimPacket::new
            );

    @Override
    public Type<ActivateClaimPacket> type() { return TYPE; }

    public static void handle(ActivateClaimPacket msg, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            if (!ClaimActionCooldown.tryAcquire(player)) return;
            ServerLevel level = ClaimManager.getClaimLevel(player, msg.center);
            Claim claim = ClaimManager.getClaimByCenter(level, msg.center);
            if (claim == null || !player.getUUID().equals(claim.getOwner())) return;

            if (!SableShipUtils.isOnShip(level, msg.center)) {
                player.sendSystemMessage(Component.translatable("message.aeroclaims.not_on_sublevel"));
                return;
            }

            if (ClaimManager.hasDuplicateClaimBlock(level, msg.center)) {
                player.sendSystemMessage(Component.translatable("message.aeroclaims.duplicate_claim_block"));
                sync(player, msg.center, claim, level, SyncClaimStatePacket.SHIP_BLOCK_COUNT_UNKNOWN);
                return;
            }

            AeroClaimSavedData data = AeroClaimSavedData.get(level);
            int blocksPerClaim = AeroClaimsConfig.BLOCKS_PER_CLAIM.get();
            int currentClaims = data.getClaimsForBlock(msg.center);
            int blockCount = ClaimManager.countShipBlocksExact(level, msg.center);
            ClaimPreviewManager.invalidate(level, msg.center);

            int neededClaims = blockCount > 0
                    ? Math.max(1, (blockCount + blocksPerClaim - 1) / blocksPerClaim)
                    : 0;
            int delta = neededClaims - currentClaims;

            boolean useProvider = ClaimManager.isForceloadActive(claim) && AeroClaimsConfig.isProviderSlotsForceload();

            int claimNeed = 0;
            int forceloadNeed = 0;
            int flDelta = 0;
            int flDeltaNeed = 0;

            if (delta > 0) {
                int freeSlots = data.getFreeSlots(player.getUUID());
                claimNeed = Math.max(0, delta - freeSlots);
                if (claimNeed > 0) {
                    TransferResult r = AeroClaimManager.transferFromProvider(player, claimNeed);
                    if (r != TransferResult.SUCCESS) {
                        sendTooLargeMsg(player, msg.center, level, blockCount, currentClaims, blocksPerClaim);
                        sync(player, msg.center, claim, level, blockCount);
                        return;
                    }
                }

                if (useProvider) {
                    int freeFl = data.getFreeForceloads(player.getUUID());
                    forceloadNeed = Math.max(0, delta - freeFl);
                    if (forceloadNeed > 0) {
                        TransferResult r = AeroClaimManager.transferForceloadsFromProvider(player, forceloadNeed);
                        if (r != TransferResult.SUCCESS) {
                            if (claimNeed > 0) AeroClaimManager.transferToProvider(player, claimNeed);
                            sendTooLargeMsg(player, msg.center, level, blockCount, currentClaims, blocksPerClaim);
                            sync(player, msg.center, claim, level, blockCount);
                            return;
                        }
                    }
                }

                if (!AeroClaimManager.adjustClaimsForBlock(level, player.getUUID(), msg.center, delta)) {
                    if (claimNeed > 0) AeroClaimManager.transferToProvider(player, claimNeed);
                    if (forceloadNeed > 0) AeroClaimManager.transferForceloadsToProvider(player, forceloadNeed);
                    sendTooLargeMsg(player, msg.center, level, blockCount, currentClaims, blocksPerClaim);
                    sync(player, msg.center, claim, level, blockCount);
                    return;
                }

                if (useProvider) {
                    if (!AeroClaimManager.adjustForceloadsForBlock(level, player.getUUID(), msg.center, delta)) {
                        AeroClaimManager.adjustClaimsForBlock(level, player.getUUID(), msg.center, -delta);
                        if (claimNeed > 0) AeroClaimManager.transferToProvider(player, claimNeed);
                        if (forceloadNeed > 0) AeroClaimManager.transferForceloadsToProvider(player, forceloadNeed);
                        sendTooLargeMsg(player, msg.center, level, blockCount, currentClaims, blocksPerClaim);
                        sync(player, msg.center, claim, level, blockCount);
                        return;
                    }
                }
            } else if (delta < 0) {
                AeroClaimManager.adjustClaimsForBlock(level, player.getUUID(), msg.center, delta);
                if (useProvider) AeroClaimManager.adjustForceloadsForBlock(level, player.getUUID(), msg.center, delta);
            }

            if (useProvider && delta <= 0) {
                int currentFl = data.getForceloadsForBlock(msg.center);
                flDelta = neededClaims - currentFl;
                if (flDelta > 0) {
                    int freeFl = data.getFreeForceloads(player.getUUID());
                    flDeltaNeed = Math.max(0, flDelta - freeFl);
                    if (flDeltaNeed > 0) {
                        TransferResult r = AeroClaimManager.transferForceloadsFromProvider(player, flDeltaNeed);
                        if (r == TransferResult.SUCCESS) {
                            if (!AeroClaimManager.adjustForceloadsForBlock(level, player.getUUID(), msg.center, flDelta)) {
                                AeroClaimManager.transferForceloadsToProvider(player, flDeltaNeed);
                                flDelta = 0;
                                flDeltaNeed = 0;
                            }
                        } else {
                            flDelta = 0;
                            flDeltaNeed = 0;
                        }
                    } else {
                        if (!AeroClaimManager.adjustForceloadsForBlock(level, player.getUUID(), msg.center, flDelta)) {
                            flDelta = 0;
                        }
                    }
                }
            }

            int maxSize = AeroClaimManager.getBlockLimit(level, msg.center);

            if (!ClaimManager.activateClaim(level, msg.center)) {
                if (delta > 0) {
                    AeroClaimManager.adjustClaimsForBlock(level, player.getUUID(), msg.center, -delta);
                    if (claimNeed > 0) AeroClaimManager.transferToProvider(player, claimNeed);
                    if (useProvider) {
                        AeroClaimManager.adjustForceloadsForBlock(level, player.getUUID(), msg.center, -delta);
                        if (forceloadNeed > 0) AeroClaimManager.transferForceloadsToProvider(player, forceloadNeed);
                    }
                } else if (delta < 0) {
                    AeroClaimManager.adjustClaimsForBlock(level, player.getUUID(), msg.center, -delta);
                    if (useProvider) {
                        AeroClaimManager.adjustForceloadsForBlock(level, player.getUUID(), msg.center, -delta);
                    }
                }
                if (flDelta > 0) {
                    AeroClaimManager.adjustForceloadsForBlock(level, player.getUUID(), msg.center, -flDelta);
                    if (flDeltaNeed > 0) AeroClaimManager.transferForceloadsToProvider(player, flDeltaNeed);
                }

                player.sendSystemMessage(Component.translatable("message.aeroclaims.refresh_failed"));
                sync(player, msg.center, claim, level, blockCount);
                return;
            }

            player.sendSystemMessage(Component.translatable("message.aeroclaims.claim_refreshed"));

            registerShip(level, msg.center, claim, player, blockCount, maxSize);

            Claim updated = ClaimManager.getClaimByCenter(level, msg.center);
            if (updated != null) {
                int teamColor = TeamColorHelper.getTeamColor(player, updated.getOwner());
                PacketDistributor.sendToPlayer(player,
                        new ClaimRefreshParticlesPacket(new ArrayList<>(updated.getClaimedBlocks()), teamColor));
            }

            sync(player, msg.center, updated != null ? updated : claim, level, blockCount);
        });
    }

    private static void registerShip(ServerLevel level, BlockPos center, Claim claim, ServerPlayer player,
                                     int blockCount, int maxSize) {
        AeroClaimSavedData data = AeroClaimSavedData.get(level);
        String shipId = data.getCachedShipId(center);

        if (shipId == null) {
            var ship = SableShipUtils.getShipAt(level, center);
            shipId = SableShipUtils.getShipId(ship);
        }

        if (shipId == null) return;

        String shipName = SableShipUtils.getShipName(SableShipUtils.getShipAt(level, center));
        RegisteredSublevelManager.registerShip(shipId, shipName, player.getUUID(), player.getName().getString(),
                blockCount, maxSize);
        UnregisteredSublevelManager.removeShip(shipId);
        claim.setShipId(shipId);

        SubLevelTicketManager.sync(level, claim, shipId, ClaimManager.isForceloadActive(claim));
        ClaimSavedData.get(level).setDirty();
    }

    private static void sendTooLargeMsg(ServerPlayer player, BlockPos center, ServerLevel level,
                                         int exact, int currentClaims, int blocksPerClaim) {
        player.sendSystemMessage(Component.translatable(
                "message.aeroclaims.sublevel_too_large", exact,
                currentClaims * blocksPerClaim));
    }

    private static void sync(ServerPlayer player, BlockPos center, Claim claim,
                              ServerLevel level, int shipBlockCount) {
        AeroClaimSavedData data = AeroClaimSavedData.get(level);
        if (shipBlockCount != SyncClaimStatePacket.SHIP_BLOCK_COUNT_UNKNOWN) {
            data.cacheShipBlockCount(center, shipBlockCount);
        }
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
