package com.mapter.aeroclaims.network;

import com.mapter.aeroclaims.Aeroclaims;
import com.mapter.aeroclaims.client.AeroClaimsClientStats;
import net.minecraft.client.Minecraft;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record SyncProviderUsagePacket(int opacClaims, int opacForceloads,
                                      int ftbClaims, int ftbForceloads,
                                      boolean providerForceloads) implements CustomPacketPayload {

    public static final Type<SyncProviderUsagePacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Aeroclaims.MODID, "sync_provider_usage"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SyncProviderUsagePacket> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.INT, SyncProviderUsagePacket::opacClaims,
                    ByteBufCodecs.INT, SyncProviderUsagePacket::opacForceloads,
                    ByteBufCodecs.INT, SyncProviderUsagePacket::ftbClaims,
                    ByteBufCodecs.INT, SyncProviderUsagePacket::ftbForceloads,
                    ByteBufCodecs.BOOL, SyncProviderUsagePacket::providerForceloads,
                    SyncProviderUsagePacket::new
            );

    @Override
    public Type<SyncProviderUsagePacket> type() {
        return TYPE;
    }

    public static void handle(SyncProviderUsagePacket msg, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (Minecraft.getInstance().player != null) {
                AeroClaimsClientStats.update(
                        msg.opacClaims(), msg.opacForceloads(), msg.ftbClaims(), msg.ftbForceloads(),
                        msg.providerForceloads());
            }
        });
    }
}
