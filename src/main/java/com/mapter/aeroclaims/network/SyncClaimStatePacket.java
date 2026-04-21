package com.mapter.aeroclaims.network;

import com.mapter.aeroclaims.Aeroclaims;
import com.mapter.aeroclaims.screen.ClaimSettingsMenu;
import com.mapter.aeroclaims.screen.ClaimSettingsScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record SyncClaimStatePacket(
        BlockPos center,
        boolean claimActive,
        boolean allowParty,
        boolean allowAllies,
        boolean allowOthers,
        int claimsForBlock,
        int freeSlots,
        int blocksPerClaim,
        int shipBlockCount
) implements CustomPacketPayload {

    public static final int SHIP_BLOCK_COUNT_UNKNOWN = -1;

    public static final Type<SyncClaimStatePacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Aeroclaims.MODID, "sync_claim_state"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SyncClaimStatePacket> STREAM_CODEC =
            StreamCodec.of(
                    (buf, p) -> {
                        BlockPos.STREAM_CODEC.encode(buf, p.center());
                        buf.writeBoolean(p.claimActive());
                        buf.writeBoolean(p.allowParty());
                        buf.writeBoolean(p.allowAllies());
                        buf.writeBoolean(p.allowOthers());
                        buf.writeInt(p.claimsForBlock());
                        buf.writeInt(p.freeSlots());
                        buf.writeInt(p.blocksPerClaim());
                        buf.writeInt(p.shipBlockCount());
                    },
                    buf -> new SyncClaimStatePacket(
                            BlockPos.STREAM_CODEC.decode(buf),
                            buf.readBoolean(),
                            buf.readBoolean(),
                            buf.readBoolean(),
                            buf.readBoolean(),
                            buf.readInt(),
                            buf.readInt(),
                            buf.readInt(),
                            buf.readInt()
                    )
            );

    @Override
    public Type<SyncClaimStatePacket> type() { return TYPE; }

    public static void handle(SyncClaimStatePacket msg, IPayloadContext context) {
        context.enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.screen == null) return;
            if (!(mc.player != null && mc.player.containerMenu instanceof ClaimSettingsMenu menu)) return;
            if (!menu.getCenter().equals(msg.center)) return;

            menu.setClaimActive(msg.claimActive);
            menu.setAllowParty(msg.allowParty);
            menu.setAllowAllies(msg.allowAllies);
            menu.setAllowOthers(msg.allowOthers);
            menu.setClaimsForBlock(msg.claimsForBlock);
            menu.setFreeSlots(msg.freeSlots);
            menu.setBlocksPerClaim(msg.blocksPerClaim);
            if (msg.shipBlockCount != SHIP_BLOCK_COUNT_UNKNOWN) {
                menu.setShipBlockCount(msg.shipBlockCount);
            }

            if (mc.screen instanceof ClaimSettingsScreen screen) {
                screen.syncFromMenu();
            }
        });
    }
}
