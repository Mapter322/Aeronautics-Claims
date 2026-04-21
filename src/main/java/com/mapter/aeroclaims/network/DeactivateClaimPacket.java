package com.mapter.aeroclaims.network;

import com.mapter.aeroclaims.Aeroclaims;
import com.mapter.aeroclaims.claim.AeroClaimSavedData;
import com.mapter.aeroclaims.claim.Claim;
import com.mapter.aeroclaims.claim.ClaimManager;
import com.mapter.aeroclaims.config.AeroClaimsConfig;
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

public record DeactivateClaimPacket(BlockPos center) implements CustomPacketPayload {

    public static final Type<DeactivateClaimPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Aeroclaims.MODID, "deactivate_claim"));

    public static final StreamCodec<RegistryFriendlyByteBuf, DeactivateClaimPacket> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, DeactivateClaimPacket::center,
                    DeactivateClaimPacket::new
            );

    @Override
    public Type<DeactivateClaimPacket> type() { return TYPE; }

    public static void handle(DeactivateClaimPacket msg, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;

            ServerLevel level = player.serverLevel();
            Claim claim = ClaimManager.getClaimByCenter(level, msg.center);
            if (claim == null || !player.getUUID().equals(claim.getOwner())) return;

            ClaimManager.deactivateClaim(level, msg.center);
            player.sendSystemMessage(Component.translatable("message.aeroclaims.claim_deactivated"));

            AeroClaimSavedData data = AeroClaimSavedData.get(level);
            Integer cachedCount = data.getCachedShipBlockCount(msg.center);
            int shipBlockCount = cachedCount != null ? cachedCount : SyncClaimStatePacket.SHIP_BLOCK_COUNT_UNKNOWN;
            PacketDistributor.sendToPlayer(player, new SyncClaimStatePacket(
                    msg.center,
                    false,
                    claim.isAllowParty(),
                    claim.isAllowAllies(),
                    claim.isAllowOthers(),
                    data.getClaimsForBlock(msg.center),
                    data.getFreeSlots(player.getUUID()),
                    AeroClaimsConfig.BLOCKS_PER_CLAIM.get(),
                    shipBlockCount
            ));
        });
    }
}
