package com.mapter.aeroclaims.network;

import com.mapter.aeroclaims.Aeroclaims;
import com.mapter.aeroclaims.claim.AeroClaimManager;
import com.mapter.aeroclaims.claim.AeroClaimSavedData;
import com.mapter.aeroclaims.claim.Claim;
import com.mapter.aeroclaims.claim.ClaimManager;
import com.mapter.aeroclaims.claim.ClaimSavedData;
import com.mapter.aeroclaims.config.AeroClaimsConfig;
import com.mapter.aeroclaims.sublevel.RegisteredSublevelManager;
import com.mapter.aeroclaims.sublevel.SableShipUtils;
import com.mapter.aeroclaims.sublevel.SubLevelTicketManager;
import com.mapter.aeroclaims.sublevel.UnregisteredSublevelManager;

import java.util.UUID;
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

            ServerLevel level = player.serverLevel();
            Claim claim = ClaimManager.getClaimByCenter(level, msg.center);
            if (claim == null || !player.getUUID().equals(claim.getOwner())) return;

            if (!SableShipUtils.isOnShip(level, msg.center)) {
                player.sendSystemMessage(Component.translatable("message.aeroclaims.not_on_subclaim"));
                return;
            }

            AeroClaimSavedData data = AeroClaimSavedData.get(level);
            int blocksPerClaim = AeroClaimsConfig.BLOCKS_PER_CLAIM.get();
            int currentClaims = data.getClaimsForBlock(msg.center);
            Integer cachedCount = data.getCachedShipBlockCount(msg.center);

            int blockCount;
            if (cachedCount != null) {
                blockCount = cachedCount;
            } else if (currentClaims > 0) {
                int currentLimit = currentClaims * blocksPerClaim;
                blockCount = ClaimManager.countShipBlocks(level, msg.center, currentLimit + 1);
            } else {
                blockCount = ClaimManager.countShipBlocksExact(level, msg.center);
            }

            int neededClaims = blockCount > 0
                    ? Math.max(1, (blockCount + blocksPerClaim - 1) / blocksPerClaim)
                    : 0;
            int delta = neededClaims - currentClaims;

            if (delta > 0) {
                if (!AeroClaimManager.tryEnsureSlots(level, player, msg.center, delta)) {
                    int exact = cachedCount != null ? cachedCount
                            : ClaimManager.countShipBlocksExact(level, msg.center);
                    player.sendSystemMessage(Component.translatable(
                            "message.aeroclaims.ship_too_large", exact,
                            currentClaims * blocksPerClaim));
                    sync(player, msg.center, claim, level, exact);
                    return;
                }
            } else if (delta < 0) {
                AeroClaimManager.adjustClaimsForBlock(level, player.getUUID(), msg.center, delta);
            }

            int maxSize = AeroClaimManager.getBlockLimit(level, msg.center);

            if (!ClaimManager.activateClaim(level, msg.center)) {
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
        SubLevelTicketManager.add(level, UUID.fromString(shipId));
        ClaimSavedData.get(level).setDirty();
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
                shipBlockCount
        ));
    }
}
