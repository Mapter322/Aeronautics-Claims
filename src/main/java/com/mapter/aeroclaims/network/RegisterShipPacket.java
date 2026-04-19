package com.mapter.aeroclaims.network;

import com.mapter.aeroclaims.Aeroclaims;
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
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public record RegisterShipPacket(BlockPos pos) implements CustomPacketPayload {

    private static final Logger LOGGER = LogManager.getLogger("aeroclaims/RegisterShipPacket");
    public static final Type<RegisterShipPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Aeroclaims.MODID, "register_ship"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RegisterShipPacket> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, RegisterShipPacket::pos,
            RegisterShipPacket::new
    );

    @Override
    public Type<RegisterShipPacket> type() {
        return TYPE;
    }

    public static void handle(RegisterShipPacket msg, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                LOGGER.warn("Player is null");
                return;
            }

            LOGGER.debug("RegisterShipPacket received from {} at {}", player.getName().getString(), msg.pos);

            ServerLevel level = player.serverLevel();
            SubLevel ship = SableShipUtils.getShipAt(level, msg.pos);
            LOGGER.debug("Ship at pos: {}", ship);

            if (ship == null) {
                player.sendSystemMessage(Component.translatable("message.aeroclaims.ship_not_found_at_pos"));
                return;
            }

            String shipId = SableShipUtils.getShipId(ship);
            String shipName = SableShipUtils.getShipName(ship);
            LOGGER.debug("shipId={} shipName={}", shipId, shipName);

            if (shipId == null) {
                player.sendSystemMessage(Component.translatable("message.aeroclaims.ship_id_not_found"));
                return;
            }

            RegisteredSublevelManager.registerShip(shipId, shipName, player.getUUID(), player.getName().getString());
            UnregisteredSublevelManager.removeShip(shipId);
            player.sendSystemMessage(Component.translatable("message.aeroclaims.ship_registered", shipName));
        });
    }
}