package com.mapter.aeroclaims.network;

import com.mapter.aeroclaims.Aeroclaims;
import com.mapter.aeroclaims.screen.ClaimSettingsMenu;
import com.mapter.aeroclaims.screen.ClaimSettingsScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record SyncClaimStatePacket(BlockPos center, boolean claimActive, boolean allowParty, boolean allowAllies, boolean allowOthers) implements CustomPacketPayload {

    public static final Type<SyncClaimStatePacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Aeroclaims.MODID, "sync_claim_state"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SyncClaimStatePacket> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, SyncClaimStatePacket::center,
            ByteBufCodecs.BOOL, SyncClaimStatePacket::claimActive,
            ByteBufCodecs.BOOL, SyncClaimStatePacket::allowParty,
            ByteBufCodecs.BOOL, SyncClaimStatePacket::allowAllies,
            ByteBufCodecs.BOOL, SyncClaimStatePacket::allowOthers,
            SyncClaimStatePacket::new
    );

    @Override
    public Type<SyncClaimStatePacket> type() {
        return TYPE;
    }

    public static void handle(SyncClaimStatePacket msg, IPayloadContext context) {
        context.enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.screen == null) return;
            if (!(mc.player != null && mc.player.containerMenu instanceof ClaimSettingsMenu menu)) return;
            if (!menu.getCenter().equals(msg.center)) return;

            menu.setClaimActive(msg.claimActive);
            menu.setAllowParty(msg.allowParty);
            menu.setAllowAllies(msg.allowAllies);
            menu.setAllowOthers(msg.allowOthers);


            if (mc.screen instanceof ClaimSettingsScreen screen) {
                screen.syncFromMenu();
            }
        });
    }
}
