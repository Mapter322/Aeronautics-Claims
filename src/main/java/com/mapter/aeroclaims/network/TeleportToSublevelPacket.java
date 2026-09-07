package com.mapter.aeroclaims.network;

import com.mapter.aeroclaims.Aeroclaims;
import com.mapter.aeroclaims.sublevel.SublevelTeleportService;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record TeleportToSublevelPacket(String shipId) implements CustomPacketPayload {

    public static final Type<TeleportToSublevelPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Aeroclaims.MODID, "teleport_to_sublevel"));

    public static final StreamCodec<RegistryFriendlyByteBuf, TeleportToSublevelPacket> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8, TeleportToSublevelPacket::shipId,
                    TeleportToSublevelPacket::new
            );

    @Override
    public Type<TeleportToSublevelPacket> type() {
        return TYPE;
    }

    public static void handle(TeleportToSublevelPacket message, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;

            SublevelTeleportService.Result result =
                    SublevelTeleportService.teleport(player, message.shipId());
            if (result != SublevelTeleportService.Result.SUCCESS) {
                player.sendSystemMessage(SublevelTeleportService.resultMessage(
                        result,
                        message.shipId(),
                        SublevelTeleportService.getRemainingCooldownSeconds(player)));
            }
        });
    }
}
