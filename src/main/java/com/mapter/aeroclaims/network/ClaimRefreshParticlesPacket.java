package com.mapter.aeroclaims.network;

import com.mapter.aeroclaims.Aeroclaims;
import com.mapter.aeroclaims.client.ClaimOutlineRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.List;

public record ClaimRefreshParticlesPacket(List<BlockPos> claimedBlocks, int color) implements CustomPacketPayload {

    public static final Type<ClaimRefreshParticlesPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Aeroclaims.MODID, "claim_refresh_particles"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ClaimRefreshParticlesPacket> STREAM_CODEC = StreamCodec.of(
            (buf, packet) -> {
                buf.writeVarInt(packet.claimedBlocks.size());
                for (BlockPos pos : packet.claimedBlocks) {
                    BlockPos.STREAM_CODEC.encode(buf, pos);
                }
                buf.writeInt(packet.color);
            },
            buf -> {
                int size = buf.readVarInt();
                List<BlockPos> blocks = new java.util.ArrayList<>(size);
                for (int i = 0; i < size; i++) {
                    blocks.add(BlockPos.STREAM_CODEC.decode(buf));
                }
                int color = buf.readInt();
                return new ClaimRefreshParticlesPacket(blocks, color);
            }
    );

    @Override
    public Type<ClaimRefreshParticlesPacket> type() {
        return TYPE;
    }

    public static void handle(ClaimRefreshParticlesPacket msg, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player().level().isClientSide) {
                ClaimOutlineRenderer.setOutline(msg.claimedBlocks, msg.color);
            }
        });
    }
}
