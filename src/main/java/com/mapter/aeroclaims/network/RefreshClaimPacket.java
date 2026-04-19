package com.mapter.aeroclaims.network;

import com.mapter.aeroclaims.Aeroclaims;
import com.mapter.aeroclaims.claim.Claim;
import com.mapter.aeroclaims.claim.ClaimManager;
import com.mapter.aeroclaims.claim.ClaimSavedData;
import com.mapter.aeroclaims.claim.VsClaimManager;
import com.mapter.aeroclaims.config.AeroClaimsConfig;
import com.mapter.aeroclaims.ship.RegisteredShipsManager;
import com.mapter.aeroclaims.ship.UnregisteredShipsManager;
import com.mapter.aeroclaims.ship.VSShipUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.chat.Component;
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

            if (!VSShipUtils.isOnShip(player.serverLevel(), msg.center)) {
                player.sendSystemMessage(Component.translatable("message.vsclaims.refresh_only_on_ship"));
                return;
            }

            int maxSize = AeroClaimsConfig.MAX_SHIP_BLOCKS.get();
            if (ClaimManager.countShipBlocks(player.serverLevel(), msg.center, maxSize) > maxSize) {
                int exact = ClaimManager.countShipBlocksExact(player.serverLevel(), msg.center);
                if (claim.isActive()) {
                    VsClaimManager.releaseShipClaimSlot(player.serverLevel(), claim.getOwner());
                }
                ClaimManager.deactivateClaim(player.serverLevel(), msg.center);
                player.sendSystemMessage(Component.translatable("message.vsclaims.ship_too_large", exact, maxSize));
                // Sync deactivation to client
                PacketDistributor.sendToPlayer(player, new SyncClaimStatePacket(msg.center, false,
                        claim.isAllowParty(), claim.isAllowAllies(), claim.isAllowOthers()));
                return;
            }

            // If claim is not yet active — need to consume ship claim
            if (!claim.isActive()) {
                boolean consumed = VsClaimManager.consumeShipClaimSlot(
                        player.serverLevel(), player.getUUID());
                if (!consumed) {
                    player.sendSystemMessage(Component.translatable("message.vsclaims.no_ship_slots"));
                    return;
                }
            }

            ClaimManager.refreshClaim(player.serverLevel(), msg.center);
            player.sendSystemMessage(Component.translatable("message.vsclaims.claim_refreshed"));

            Object ship = VSShipUtils.getShipAt(player.serverLevel(), msg.center);
            if (ship instanceof Boolean) ship = VSShipUtils.getShipObjectAt(player.serverLevel(), msg.center);
            String shipId = VSShipUtils.getShipId(ship);
            if (shipId != null) {
                String shipName = VSShipUtils.getShipSlug(ship);
                if (shipName == null) shipName = "ship";
                RegisteredShipsManager.registerShip(shipId, shipName, player.getUUID(), player.getName().getString());
                UnregisteredShipsManager.removeShip(shipId);
                claim.setShipId(shipId);
                ClaimSavedData.get(player.serverLevel()).setDirty();
            }

            // Sync activation to client in real time
            PacketDistributor.sendToPlayer(player, new SyncClaimStatePacket(msg.center, claim.isActive(),
                    claim.isAllowParty(), claim.isAllowAllies(), claim.isAllowOthers()));
        });
    }
}
