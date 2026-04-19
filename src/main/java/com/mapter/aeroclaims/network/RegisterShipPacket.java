package com.mapter.aeroclaims.network;

import com.mapter.aeroclaims.Aeroclaims;
import com.mapter.aeroclaims.ship.RegisteredShipsManager;
import com.mapter.aeroclaims.ship.UnregisteredShipsManager;
import com.mapter.aeroclaims.ship.VSShipUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public record RegisterShipPacket(BlockPos pos) implements CustomPacketPayload {

    private static final Logger LOGGER = LogManager.getLogger("vsclaims/RegisterShipPacket");
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
            Object ship = VSShipUtils.getShipAt(level, msg.pos);
            LOGGER.debug("Ship at pos: {}", ship);

            if (ship == null) {
                player.sendSystemMessage(Component.translatable("message.vsclaims.ship_not_found_at_pos"));
                return;
            }

            // If ship == Boolean.TRUE, isBlockInShipyard succeeded but the object was not retrieved
            // We need to get the actual object via getLoadedShips
            if (ship instanceof Boolean) {
                LOGGER.debug("Ship is Boolean.TRUE - trying to find via getLoadedShips");
                ship = VSShipUtils.getShipObjectAt(level, msg.pos);
                LOGGER.debug("Ship object: {}", ship);
            }

            if (ship == null) {
                player.sendSystemMessage(Component.translatable("message.vsclaims.ship_object_not_found"));
                return;
            }

            String shipId = VSShipUtils.getShipId(ship);
            String slug = VSShipUtils.getShipSlug(ship);
            LOGGER.debug("shipId={} slug={}", shipId, slug);

            if (shipId == null) {
                player.sendSystemMessage(Component.translatable("message.vsclaims.ship_id_not_found"));
                return;
            }

            if (slug == null) slug = "ship";

            RegisteredShipsManager.registerShip(shipId, slug, player.getUUID(), player.getName().getString());
            UnregisteredShipsManager.removeShip(shipId);
            player.sendSystemMessage(Component.translatable("message.vsclaims.ship_registered", slug));
        });
    }
}