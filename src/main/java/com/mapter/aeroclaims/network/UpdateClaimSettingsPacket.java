package com.mapter.aeroclaims.network;

import com.mapter.aeroclaims.Aeroclaims;
import com.mapter.aeroclaims.claim.Claim;
import com.mapter.aeroclaims.claim.ClaimManager;
import com.mapter.aeroclaims.claim.ClaimSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record UpdateClaimSettingsPacket(BlockPos center, boolean allowParty, boolean allowAllies, boolean allowOthers) implements CustomPacketPayload {

    public static final Type<UpdateClaimSettingsPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Aeroclaims.MODID, "update_claim_settings"));
    public static final StreamCodec<RegistryFriendlyByteBuf, UpdateClaimSettingsPacket> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, UpdateClaimSettingsPacket::center,
            ByteBufCodecs.BOOL, UpdateClaimSettingsPacket::allowParty,
            ByteBufCodecs.BOOL, UpdateClaimSettingsPacket::allowAllies,
            ByteBufCodecs.BOOL, UpdateClaimSettingsPacket::allowOthers,
            UpdateClaimSettingsPacket::new
    );

    @Override
    public Type<UpdateClaimSettingsPacket> type() {
        return TYPE;
    }

    public static void handle(UpdateClaimSettingsPacket msg, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player))
                return;
            if (player == null)
                return;

            Claim claim = ClaimManager.getClaimByCenter(player.serverLevel(), msg.center);
            if (claim == null)
                return;

            if (!player.getUUID().equals(claim.getOwner()))
                return;

            claim.setAllowParty(msg.allowParty);
            claim.setAllowAllies(msg.allowAllies);
            claim.setAllowOthers(msg.allowOthers);
            ClaimSavedData.get(player.serverLevel()).setDirty();
        });
    }
}
