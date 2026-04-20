package com.mapter.aeroclaims.network;

import com.mapter.aeroclaims.Aeroclaims;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.List;

public record ClaimRefreshParticlesPacket(List<BlockPos> claimedBlocks) implements CustomPacketPayload {

    public static final Type<ClaimRefreshParticlesPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Aeroclaims.MODID, "claim_refresh_particles"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ClaimRefreshParticlesPacket> STREAM_CODEC = StreamCodec.of(
            (buf, packet) -> {
                buf.writeVarInt(packet.claimedBlocks.size());
                for (BlockPos pos : packet.claimedBlocks) {
                    BlockPos.STREAM_CODEC.encode(buf, pos);
                }
            },
            buf -> {
                int size = buf.readVarInt();
                List<BlockPos> blocks = new java.util.ArrayList<>(size);
                for (int i = 0; i < size; i++) {
                    blocks.add(BlockPos.STREAM_CODEC.decode(buf));
                }
                return new ClaimRefreshParticlesPacket(blocks);
            }
    );

    @Override
    public Type<ClaimRefreshParticlesPacket> type() {
        return TYPE;
    }

    public static void handle(ClaimRefreshParticlesPacket msg, IPayloadContext context) {
        context.enqueueWork(() -> {
            Level level = context.player().level();
            if (level.isClientSide) {
                for (BlockPos pos : msg.claimedBlocks) {
                    for (int i = 0; i < 10; i++) {
                        level.addParticle(ParticleTypes.TOTEM_OF_UNDYING,
                                pos.getX() + 0.5,
                                pos.getY() + 0.5 + (i * 0.1),
                                pos.getZ() + 0.5,
                                0, 0.02, 0);
                    }
                }
            }
        });
    }
}
