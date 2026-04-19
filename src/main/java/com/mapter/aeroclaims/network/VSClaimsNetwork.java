package com.mapter.aeroclaims.network;

import com.mapter.aeroclaims.Aeroclaims;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

@EventBusSubscriber(modid = Aeroclaims.MODID, bus = EventBusSubscriber.Bus.MOD)
public class VSClaimsNetwork {

    private static final String PROTOCOL_VERSION = "1";

    @SubscribeEvent
    public static void register(final RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(PROTOCOL_VERSION);
        registrar.playToServer(UpdateClaimSettingsPacket.TYPE, UpdateClaimSettingsPacket.STREAM_CODEC, UpdateClaimSettingsPacket::handle);
        registrar.playToServer(RefreshClaimPacket.TYPE, RefreshClaimPacket.STREAM_CODEC, RefreshClaimPacket::handle);
        registrar.playToServer(RegisterShipPacket.TYPE, RegisterShipPacket.STREAM_CODEC, RegisterShipPacket::handle);
        registrar.playBidirectional(SyncClaimStatePacket.TYPE, SyncClaimStatePacket.STREAM_CODEC, SyncClaimStatePacket::handle);
    }
}
