package com.mapter.aeroclaims.network;

import com.mapter.aeroclaims.Aeroclaims;
import com.mapter.aeroclaims.claim.AeroClaimManager;
import com.mapter.aeroclaims.claim.AeroClaimSavedData;
import com.mapter.aeroclaims.claim.Claim;
import com.mapter.aeroclaims.claim.ClaimManager;
import com.mapter.aeroclaims.claim.ClaimSavedData;
import com.mapter.aeroclaims.config.AeroClaimsConfig;
import com.mapter.aeroclaims.sublevel.SubLevelTicketManager;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
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

            ServerLevel level = ClaimManager.getClaimLevel(player, msg.center);
            Claim claim = ClaimManager.getClaimByCenter(level, msg.center);
            if (claim == null || !player.getUUID().equals(claim.getOwner())) return;

            AeroClaimSavedData data = AeroClaimSavedData.get(level);

            if (msg.enabled) {
                if (!AeroClaimsConfig.FORCELOAD_ENABLE.get()) {
                    sync(player, msg.center, claim, level);
                    return;
                }

                if (claim.isActive() && AeroClaimsConfig.isProviderSlotsForceload()) {
                    int neededForceloads = data.getClaimsForBlock(msg.center);
                    int currentForceloads = data.getForceloadsForBlock(msg.center);
                    int forceloadDelta = neededForceloads - currentForceloads;

                    if (forceloadDelta > 0) {
                        int freeFl = data.getFreeForceloads(player.getUUID());
                        int transferNeed = Math.max(0, forceloadDelta - freeFl);
                        if (transferNeed > 0) {
                            AeroClaimManager.TransferResult result =
                                    AeroClaimManager.transferForceloadsFromProvider(player, transferNeed);
                            if (result != AeroClaimManager.TransferResult.SUCCESS) {
                                player.sendSystemMessage(Component.translatable("message.aeroclaims.no_sublevel_slots"));
                                sync(player, msg.center, claim, level);
                                return;
                            }
                        }
                        if (!AeroClaimManager.adjustForceloadsForBlock(level, player.getUUID(), msg.center, forceloadDelta)) {
                            if (transferNeed > 0) AeroClaimManager.transferForceloadsToProvider(player, transferNeed);
                            player.sendSystemMessage(Component.translatable("message.aeroclaims.no_sublevel_slots"));
                            sync(player, msg.center, claim, level);
                            return;
                        }
                    }
                }

                claim.setForceloadEnabled(true);
                ClaimSavedData.get(level).setDirty();

                if (claim.isActive()) {
                    SubLevelTicketManager.sync(level, claim, claim.getShipId(), ClaimManager.isForceloadActive(claim));
                }
            } else {
                claim.setForceloadEnabled(false);
                ClaimSavedData.get(level).setDirty();

                AeroClaimManager.releaseAllForceloadsForBlock(level, player, msg.center);

                if (claim.isActive()) {
                    SubLevelTicketManager.sync(level, claim, claim.getShipId(), false);
                }
            }

            sync(player, msg.center, claim, level);
        });
    }

    private static void sync(ServerPlayer player, BlockPos center, Claim claim, ServerLevel level) {
        AeroClaimSavedData data = AeroClaimSavedData.get(level);
        Integer cachedCount = data.getCachedShipBlockCount(center);
        int shipBlockCount = cachedCount != null ? cachedCount : SyncClaimStatePacket.SHIP_BLOCK_COUNT_UNKNOWN;

        PacketDistributor.sendToPlayer(player, new SyncClaimStatePacket(
                center,
                claim.isActive(),
                claim.isAllowParty(),
                claim.isAllowAllies(),
                claim.isAllowOthers(),
                data.getClaimsForBlock(center),
                data.getFreeSlots(player.getUUID()),
                AeroClaimsConfig.BLOCKS_PER_CLAIM.get(),
                shipBlockCount,
                data.getForceloadsForBlock(center),
                ClaimManager.isForceloadActive(claim)
        ));
    }
}
