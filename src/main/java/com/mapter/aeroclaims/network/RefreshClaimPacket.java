package com.mapter.aeroclaims.network;

import com.mapter.aeroclaims.Aeroclaims;
import com.mapter.aeroclaims.claim.AeroClaimManager;
import com.mapter.aeroclaims.claim.AeroClaimSavedData;
import com.mapter.aeroclaims.claim.Claim;
import com.mapter.aeroclaims.claim.ClaimManager;
import com.mapter.aeroclaims.claim.ClaimSavedData;
import com.mapter.aeroclaims.config.AeroClaimsConfig;
import com.mapter.aeroclaims.config.AeroClaimsConfig;
import com.mapter.aeroclaims.sublevel.RegisteredSublevelManager;
import com.mapter.aeroclaims.sublevel.SableShipUtils;
import com.mapter.aeroclaims.sublevel.UnregisteredSublevelManager;
import dev.ryanhcode.sable.sublevel.SubLevel;
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
                player.sendSystemMessage(Component.translatable("message.aeroclaims.not_on_subclaim"));
                return;
            }

            if (hasDuplicateClaimBlock(level, msg.center)) {
                player.sendSystemMessage(Component.translatable("message.aeroclaims.duplicate_claim_block"));
                return;
            }

            int maxSize = AeroClaimManager.getBlockLimit(level, msg.center);
            if (maxSize <= 0) {
                player.sendSystemMessage(Component.translatable("message.aeroclaims.no_claims_allocated"));
                sync(player, msg.center, claim, level, SyncClaimStatePacket.SHIP_BLOCK_COUNT_UNKNOWN);
                return;
            }

            boolean deactivateOnOverflow = AeroClaimsConfig.DEACTIVATE_ON_OVERFLOW.get();
            int blockCount = ClaimManager.recountShipBlocks(level, msg.center, deactivateOnOverflow);

            if (blockCount < 0) {
                player.sendSystemMessage(Component.translatable("message.aeroclaims.refresh_failed"));
                sync(player, msg.center, claim, level, SyncClaimStatePacket.SHIP_BLOCK_COUNT_UNKNOWN);
                return;
            }

            Claim updated = ClaimManager.getClaimByCenter(level, msg.center);
            if (blockCount > maxSize) {
                if (deactivateOnOverflow) {
                    player.sendSystemMessage(Component.translatable("message.aeroclaims.ship_too_large_deactivated", blockCount, maxSize));
                } else {
                    player.sendSystemMessage(Component.translatable("message.aeroclaims.ship_too_large", blockCount, maxSize));
                }
            } else {
                player.sendSystemMessage(Component.translatable("message.aeroclaims.claim_recounted"));
            }

            registerShip(level, msg.center, claim, player);
            sync(player, msg.center, updated != null ? updated : claim, level, blockCount);
        });
    }

    private static boolean hasDuplicateClaimBlock(ServerLevel level, BlockPos center) {
        SubLevel ship = SableShipUtils.getShipAt(level, center);
        String shipId = SableShipUtils.getShipId(ship);
        if (shipId == null) return false;

        for (Claim other : ClaimSavedData.get(level).getClaims()) {
            if (other.getCenter().equals(center)) continue;
            String otherId = SableShipUtils.getShipId(SableShipUtils.getShipAt(level, other.getCenter()));
            if (shipId.equals(otherId)) return true;
        }
        return false;
    }

    private static void registerShip(ServerLevel level, BlockPos center, Claim claim, ServerPlayer player) {
        SubLevel ship = SableShipUtils.getShipAt(level, center);
        String shipId = SableShipUtils.getShipId(ship);
        if (shipId == null) return;

        String shipName = SableShipUtils.getShipName(ship);
        RegisteredSublevelManager.registerShip(shipId, shipName, player.getUUID(), player.getName().getString());
        UnregisteredSublevelManager.removeShip(shipId);
        claim.setShipId(shipId);
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
