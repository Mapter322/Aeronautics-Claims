package com.mapter.aeroclaims.network;

import com.mapter.aeroclaims.Aeroclaims;
import com.mapter.aeroclaims.claim.AeroClaimSavedData;
import com.mapter.aeroclaims.claim.Claim;
import com.mapter.aeroclaims.claim.ClaimManager;
import com.mapter.aeroclaims.sublevel.RegisteredSublevelManager;
import com.mapter.aeroclaims.sublevel.SableShipUtils;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

public record RenameShipPacket(BlockPos center, String newName) implements CustomPacketPayload {

    private static final int MAX_NAME_LENGTH = 48;

    public static final Type<RenameShipPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Aeroclaims.MODID, "rename_ship"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RenameShipPacket> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, RenameShipPacket::center,
            ByteBufCodecs.STRING_UTF8, RenameShipPacket::newName,
            RenameShipPacket::new
    );

    @Override
    public Type<RenameShipPacket> type() {
        return TYPE;
    }

    public static void handle(RenameShipPacket msg, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;

            Claim claim = ClaimManager.getClaimByCenter(player.serverLevel(), msg.center);
            if (claim == null) return;

            if (!player.getUUID().equals(claim.getOwner())) return;

            AeroClaimSavedData data = AeroClaimSavedData.get(player.serverLevel());
            String shipId = data.getCachedShipId(msg.center);
            if (shipId == null) shipId = claim.getShipId();
            if (shipId == null) {
                var ship = SableShipUtils.getShipAt(player.serverLevel(), msg.center);
                shipId = SableShipUtils.getShipId(ship);
            }
            if (shipId == null) return;

            String name = msg.newName.trim();
            if (name.length() > MAX_NAME_LENGTH) name = name.substring(0, MAX_NAME_LENGTH);
            if (name.isEmpty()) return;

            RegisteredSublevelManager.ShipRegistration reg = RegisteredSublevelManager.getRegistration(shipId);
            if (reg == null) return;

            reg.name = name;
            RegisteredSublevelManager.saveNow();

            var container = SubLevelContainer.getContainer(player.serverLevel());
            if (container == null) return;
            var subLevel = container.getSubLevel(UUID.fromString(shipId));
            if (subLevel instanceof ServerSubLevel serverSubLevel) {
                serverSubLevel.setName(name);
            }
        });
    }
}