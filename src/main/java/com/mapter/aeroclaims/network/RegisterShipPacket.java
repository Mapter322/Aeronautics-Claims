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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// Client → server packet: player manually registers ship (sublevel) by block position.
// Server determines sublevel at position and adds to registered list
public record RegisterShipPacket(BlockPos pos) implements CustomPacketPayload {

    private static final Logger LOGGER = LoggerFactory.getLogger(RegisterShipPacket.class);

    public static final Type<RegisterShipPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Aeroclaims.MODID, "register_ship"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RegisterShipPacket> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, RegisterShipPacket::pos,
                    RegisterShipPacket::new
            );

    @Override
    public Type<RegisterShipPacket> type() { return TYPE; }

    public static void handle(RegisterShipPacket msg, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;

            ServerLevel level = player.serverLevel();
            SubLevel ship = SableShipUtils.getShipAt(level, msg.pos);

            if (ship == null) {
                player.sendSystemMessage(Component.translatable("message.aeroclaims.ship_not_found_at_pos"));
                return;
            }

            String shipId   = SableShipUtils.getShipId(ship);
            String shipName = SableShipUtils.getShipName(ship);

            if (shipId == null) {
                player.sendSystemMessage(Component.translatable("message.aeroclaims.ship_id_not_found"));
                return;
            }

            LOGGER.debug("Registering ship: id={} name={} by player={}", shipId, shipName, player.getName().getString());

            RegisteredSublevelManager.registerShip(shipId, shipName, player.getUUID(), player.getName().getString());
            UnregisteredSublevelManager.removeShip(shipId);
            player.sendSystemMessage(Component.translatable("message.aeroclaims.ship_registered", shipName));
        });
    }
}
