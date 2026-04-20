package com.mapter.aeroclaims.network;

import com.mapter.aeroclaims.Aeroclaims;
import com.mapter.aeroclaims.claim.Claim;
import com.mapter.aeroclaims.claim.ClaimManager;
import com.mapter.aeroclaims.claim.ClaimSavedData;
import com.mapter.aeroclaims.claim.AeroClaimManager;
import com.mapter.aeroclaims.config.AeroClaimsConfig;
import com.mapter.aeroclaims.sublevel.RegisteredSublevelManager;
import com.mapter.aeroclaims.sublevel.SableShipUtils;
import com.mapter.aeroclaims.sublevel.UnregisteredSublevelManager;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record RefreshClaimPacket(BlockPos center) implements CustomPacketPayload {

    public static final Type<RefreshClaimPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Aeroclaims.MODID, "refresh_claim"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RefreshClaimPacket> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, RefreshClaimPacket::center,
            RefreshClaimPacket::new
    );

    @Override
    public Type<RefreshClaimPacket> type() {
        return TYPE;
    }

    public static void handle(RefreshClaimPacket msg, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;

            Claim claim = ClaimManager.getClaimByCenter(player.serverLevel(), msg.center);
            if (claim == null) return;

            if (!player.getUUID().equals(claim.getOwner())) return;

            if (!SableShipUtils.isOnShip(player.serverLevel(), msg.center)) {
                player.sendSystemMessage(Component.translatable("message.aeroclaims.not_on_subclaim"));
                return;
            }

            SubLevel currentShip = SableShipUtils.getShipAt(player.serverLevel(), msg.center);
            String currentShipId = SableShipUtils.getShipId(currentShip);
            if (currentShipId != null) {
                for (Claim other : ClaimSavedData.get(player.serverLevel()).getClaims()) {
                    if (other.getCenter().equals(msg.center)) continue;
                    SubLevel otherShip = SableShipUtils.getShipAt(player.serverLevel(), other.getCenter());
                    String otherShipId = SableShipUtils.getShipId(otherShip);
                    if (currentShipId.equals(otherShipId)) {
                        player.sendSystemMessage(Component.translatable("message.aeroclaims.duplicate_claim_block"));
                        return;
                    }
                }
            }

            int maxSize = AeroClaimsConfig.MAX_SHIP_BLOCKS.get();
            if (ClaimManager.countShipBlocks(player.serverLevel(), msg.center, maxSize) > maxSize) {
                int exact = ClaimManager.countShipBlocksExact(player.serverLevel(), msg.center);
                if (claim.isActive()) {
                    AeroClaimManager.releaseShipClaimSlot(player.serverLevel(), claim.getOwner());
                }
                ClaimManager.deactivateClaim(player.serverLevel(), msg.center);
                player.sendSystemMessage(Component.translatable("message.aeroclaims.ship_too_large", exact, maxSize));
                PacketDistributor.sendToPlayer(player, new SyncClaimStatePacket(msg.center, false,
                        claim.isAllowParty(), claim.isAllowAllies(), claim.isAllowOthers()));
                return;
            }

            if (!claim.isActive()) {
                boolean consumed = AeroClaimManager.consumeShipClaimSlot(
                        player.serverLevel(), player.getUUID());
                if (!consumed) {
                    player.sendSystemMessage(Component.translatable("message.aeroclaims.no_ship_slots"));
                    return;
                }
            }

            ClaimManager.refreshClaim(player.serverLevel(), msg.center);
            player.sendSystemMessage(Component.translatable("message.aeroclaims.claim_refreshed"));

            SubLevel ship = SableShipUtils.getShipAt(player.serverLevel(), msg.center);
            String shipId = SableShipUtils.getShipId(ship);
            if (shipId != null) {
                String shipName = SableShipUtils.getShipName(ship);
                RegisteredSublevelManager.registerShip(shipId, shipName, player.getUUID(), player.getName().getString());
                UnregisteredSublevelManager.removeShip(shipId);
                claim.setShipId(shipId);
                ClaimSavedData.get(player.serverLevel()).setDirty();
            }

            PacketDistributor.sendToPlayer(player, new SyncClaimStatePacket(msg.center, claim.isActive(),
                    claim.isAllowParty(), claim.isAllowAllies(), claim.isAllowOthers()));
        });
    }
}