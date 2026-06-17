package com.mapter.aeroclaims.network;

import com.mapter.aeroclaims.Aeroclaims;
import com.mapter.aeroclaims.screen.AeroClaimsMenu;
import net.minecraft.client.Minecraft;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record SyncMenuStatsPacket(int providerFree, int aeroTotal, int aeroUsed) implements CustomPacketPayload {

    public static final Type<SyncMenuStatsPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Aeroclaims.MODID, "sync_menu_stats"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SyncMenuStatsPacket> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.INT, SyncMenuStatsPacket::providerFree,
                    ByteBufCodecs.INT, SyncMenuStatsPacket::aeroTotal,
                    ByteBufCodecs.INT, SyncMenuStatsPacket::aeroUsed,
                    SyncMenuStatsPacket::new
            );

    @Override
    public Type<SyncMenuStatsPacket> type() { return TYPE; }

    public static void handle(SyncMenuStatsPacket msg, IPayloadContext context) {
        context.enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null && mc.player.containerMenu instanceof AeroClaimsMenu menu) {
                menu.setStats(msg.providerFree(), msg.aeroTotal(), msg.aeroUsed());
            }
        });
    }
}
