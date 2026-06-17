package com.mapter.aeroclaims.network;

import com.mapter.aeroclaims.Aeroclaims;
import com.mapter.aeroclaims.claim.AeroClaimManager;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record TransferSlotsPacket(boolean toAero) implements CustomPacketPayload {

    public static final Type<TransferSlotsPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Aeroclaims.MODID, "transfer_slots"));

    public static final StreamCodec<RegistryFriendlyByteBuf, TransferSlotsPacket> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.BOOL, TransferSlotsPacket::toAero,
                    TransferSlotsPacket::new
            );

    @Override
    public Type<TransferSlotsPacket> type() { return TYPE; }

    public static void handle(TransferSlotsPacket msg, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;

            AeroClaimManager.TransferResult result = msg.toAero()
                    ? AeroClaimManager.transferFromProvider(player, 1)
                    : AeroClaimManager.transferToProvider(player, 1);

            if (result == AeroClaimManager.TransferResult.NOT_ENOUGH_FREE) {
                player.sendSystemMessage(msg.toAero()
                        ? Component.translatable("commands.aeroclaims.transfer.not_enough",
                            AeroClaimManager.getFreeProviderClaims(player), 1)
                        : Component.translatable("commands.aeroclaims.transfer_back.not_enough",
                            AeroClaimManager.getFreeSlots(player.serverLevel(), player.getUUID()), 1));
            } else if (result == AeroClaimManager.TransferResult.CLAIM_PROVIDER_UNAVAILABLE) {
                player.sendSystemMessage(Component.translatable("commands.aeroclaims.transfer.opac_not_loaded"));
            }

            int providerFree = AeroClaimManager.getFreeProviderClaims(player);
            int aeroTotal = AeroClaimManager.getMigratedSlots(player.serverLevel(), player.getUUID());
            int aeroUsed = AeroClaimManager.getUsedSlots(player.serverLevel(), player.getUUID());
            PacketDistributor.sendToPlayer(player, new SyncMenuStatsPacket(providerFree, aeroTotal, aeroUsed));
        });
    }
}
