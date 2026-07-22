package com.mapter.aeroclaims.network;

import com.mapter.aeroclaims.Aeroclaims;
import com.mapter.aeroclaims.claim.AeroClaimManager;
import com.mapter.aeroclaims.claim.Claim;
import com.mapter.aeroclaims.claim.ClaimManager;
import com.mapter.aeroclaims.claim.ClaimSavedData;
import com.mapter.aeroclaims.sublevel.SubLevelTicketManager;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record SetForceloadEnabledPacket(BlockPos center, boolean enabled) implements CustomPacketPayload {

    public static final Type<SetForceloadEnabledPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Aeroclaims.MODID, "set_forceload_enabled"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SetForceloadEnabledPacket> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, SetForceloadEnabledPacket::center,
                    ByteBufCodecs.BOOL,     SetForceloadEnabledPacket::enabled,
                    SetForceloadEnabledPacket::new
            );

    @Override
    public Type<SetForceloadEnabledPacket> type() { return TYPE; }

    public static void handle(SetForceloadEnabledPacket msg, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;

            ServerLevel level = player.serverLevel();
            Claim claim = ClaimManager.getClaimByCenter(level, msg.center);
            if (claim == null || !player.getUUID().equals(claim.getOwner())) return;

            claim.setForceloadEnabled(msg.enabled);
            ClaimSavedData.get(level).setDirty();

            if (!msg.enabled) {
                // Refund any provider forceload slots already consumed by this claim block.
                AeroClaimManager.releaseAllForceloadsForBlock(level, player, msg.center);
            }

            // Keep the Sable forceload ticket in sync immediately, don't wait for the next activation.
            if (claim.isActive()) {
                SubLevelTicketManager.sync(level, claim, claim.getShipId(), msg.enabled);
            }
        });
    }
}
