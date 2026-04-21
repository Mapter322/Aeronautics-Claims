package com.mapter.aeroclaims.network;

import com.mapter.aeroclaims.Aeroclaims;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

@EventBusSubscriber(modid = Aeroclaims.MODID, bus = EventBusSubscriber.Bus.MOD)
public class AeroClaimsNetwork {

    private static final String PROTOCOL_VERSION = "3";

    @SubscribeEvent
    public static void register(final RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(PROTOCOL_VERSION);
        registrar.playToServer(UpdateClaimSettingsPacket.TYPE, UpdateClaimSettingsPacket.STREAM_CODEC, UpdateClaimSettingsPacket::handle);
        registrar.playToServer(RefreshClaimPacket.TYPE, RefreshClaimPacket.STREAM_CODEC, RefreshClaimPacket::handle);
        registrar.playToServer(ActivateClaimPacket.TYPE, ActivateClaimPacket.STREAM_CODEC, ActivateClaimPacket::handle);
        registrar.playToServer(DeactivateClaimPacket.TYPE, DeactivateClaimPacket.STREAM_CODEC, DeactivateClaimPacket::handle);
        registrar.playToServer(RegisterShipPacket.TYPE, RegisterShipPacket.STREAM_CODEC, RegisterShipPacket::handle);
        registrar.playToServer(AdjustBlockClaimsPacket.TYPE, AdjustBlockClaimsPacket.STREAM_CODEC, AdjustBlockClaimsPacket::handle);
        registrar.playBidirectional(SyncClaimStatePacket.TYPE, SyncClaimStatePacket.STREAM_CODEC, SyncClaimStatePacket::handle);
        registrar.playToClient(ClaimRefreshParticlesPacket.TYPE, ClaimRefreshParticlesPacket.STREAM_CODEC, ClaimRefreshParticlesPacket::handle);
    }
}
