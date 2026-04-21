package com.mapter.aeroclaims.network;

import com.mapter.aeroclaims.Aeroclaims;
import com.mapter.aeroclaims.claim.AeroClaimManager;
import com.mapter.aeroclaims.claim.AeroClaimSavedData;
import com.mapter.aeroclaims.claim.Claim;
import com.mapter.aeroclaims.claim.ClaimManager;
import com.mapter.aeroclaims.claim.ClaimSavedData;
import com.mapter.aeroclaims.config.AeroClaimsConfig;
import com.mapter.aeroclaims.sublevel.SableShipUtils;
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

            int maxSize = AeroClaimManager.getBlockLimit(level, msg.center);
            if (maxSize <= 0) {
                player.sendSystemMessage(Component.translatable("message.aeroclaims.no_claims_allocated"));
                sync(player, msg.center, claim, level, SyncClaimStatePacket.SHIP_BLOCK_COUNT_UNKNOWN);
                return;
            }

            int blockCount = ClaimManager.countShipBlocks(level, msg.center, maxSize + 1);
            if (blockCount > maxSize) {
                int exact = ClaimManager.countShipBlocksExact(level, msg.center);
                player.sendSystemMessage(Component.translatable("message.aeroclaims.ship_too_large", exact, maxSize));
                sync(player, msg.center, claim, level, exact);
                return;
            }

            if (!ClaimManager.activateClaim(level, msg.center)) {
                player.sendSystemMessage(Component.translatable("message.aeroclaims.refresh_failed"));
                sync(player, msg.center, claim, level, blockCount);
                return;
            }

            player.sendSystemMessage(Component.translatable("message.aeroclaims.claim_refreshed"));

            Claim updated = ClaimManager.getClaimByCenter(level, msg.center);
            if (updated != null) {
                PacketDistributor.sendToPlayer(player,
                        new ClaimRefreshParticlesPacket(new ArrayList<>(updated.getClaimedBlocks())));
            }

            sync(player, msg.center, updated != null ? updated : claim, level, blockCount);
        });
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
