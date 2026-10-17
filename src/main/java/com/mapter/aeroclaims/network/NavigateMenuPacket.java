package com.mapter.aeroclaims.network;

import com.mapter.aeroclaims.Aeroclaims;
import com.mapter.aeroclaims.block.ClaimBlock;
import com.mapter.aeroclaims.claim.AeroClaimManager;
import com.mapter.aeroclaims.claim.AeroClaimSavedData;
import com.mapter.aeroclaims.claim.Claim;
import com.mapter.aeroclaims.claim.ClaimBriefInfo;
import com.mapter.aeroclaims.claim.ClaimManager;
import com.mapter.aeroclaims.claim.ClaimPreviewManager;
import com.mapter.aeroclaims.config.AeroClaimsConfig;
import com.mapter.aeroclaims.screen.AeroClaimsMenu;
import com.mapter.aeroclaims.screen.ClaimBlockMenu;
import com.mapter.aeroclaims.sublevel.RegisteredSublevelManager;
import com.mapter.aeroclaims.sublevel.SableShipUtils;
import com.mapter.aeroclaims.sublevel.SublevelLoadService;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Optional;

public record NavigateMenuPacket(Direction direction, Optional<BlockPos> claimPos, Optional<String> shipId)
        implements CustomPacketPayload {

    public enum Direction {
        TO_AERO_MENU,
        BACK_TO_CLAIM_SETTINGS,
        OPEN_CLAIM_FOR_SHIP;

        public static final StreamCodec<RegistryFriendlyByteBuf, Direction> STREAM_CODEC =
                ByteBufCodecs.<Direction>idMapper(id -> Direction.values()[id], Direction::ordinal)
                        .cast();
    }

    public static final Type<NavigateMenuPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Aeroclaims.MODID, "navigate_menu"));

    public static final StreamCodec<RegistryFriendlyByteBuf, NavigateMenuPacket> STREAM_CODEC =
            StreamCodec.composite(
                    Direction.STREAM_CODEC, NavigateMenuPacket::direction,
                    BlockPos.STREAM_CODEC.apply(ByteBufCodecs::optional), NavigateMenuPacket::claimPos,
                    ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs::optional), NavigateMenuPacket::shipId,
                    NavigateMenuPacket::new
            );

    @Override
    public Type<NavigateMenuPacket> type() { return TYPE; }

    public static NavigateMenuPacket toAeroMenu() {
        return new NavigateMenuPacket(Direction.TO_AERO_MENU, Optional.empty(), Optional.empty());
    }

    public static NavigateMenuPacket backToClaim(BlockPos pos) {
        return new NavigateMenuPacket(Direction.BACK_TO_CLAIM_SETTINGS, Optional.of(pos), Optional.empty());
    }

    public static NavigateMenuPacket openClaimForShip(String shipId) {
        return new NavigateMenuPacket(Direction.OPEN_CLAIM_FOR_SHIP, Optional.empty(), Optional.of(shipId));
    }

    public static void handle(NavigateMenuPacket msg, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            switch (msg.direction()) {
                case TO_AERO_MENU             -> handleOpenAeroMenu(player);
                case BACK_TO_CLAIM_SETTINGS   -> msg.claimPos().ifPresent(pos -> handleReturnToClaim(player, pos));
                case OPEN_CLAIM_FOR_SHIP      -> msg.shipId().ifPresent(id -> handleOpenClaimForShip(player, id));
            }
        });
    }


    private static void handleOpenAeroMenu(ServerPlayer player) {
        if (player.containerMenu instanceof ClaimBlockMenu claimMenu) {
            claimMenu.setNavigatingAway(true);
        }

        Map<String, String> ships = RegisteredSublevelManager.getRegisteredShips(player.getUUID());
        ServerLevel level = player.serverLevel();

        @Nullable BlockPos returnPos = (player.containerMenu instanceof ClaimBlockMenu m)
                ? m.getCenter() : null;

        player.openMenu(
                new SimpleMenuProvider(
                        (id, inv, p) -> new AeroClaimsMenu(id, inv),
                        Component.translatable("screen.aeroclaims.menu.title")),
                buf -> {
                    buf.writeVarInt(ships.size());
                    for (Map.Entry<String, String> entry : ships.entrySet()) {
                        String shipId   = entry.getKey();
                        String shipName = entry.getValue();
                        Claim claim = null;
                        ServerLevel shipLevel = null;
                        for (ServerLevel lvl : player.server.getAllLevels()) {
                            claim = ClaimManager.getClaimByShipId(lvl, shipId);
                            if (claim != null) {
                                shipLevel = lvl;
                                break;
                            }
                        }
                        ClaimBriefInfo info = ClaimBriefInfo.ofShip(shipLevel != null ? shipLevel : level, shipId);
                        RegisteredSublevelManager.ShipRegistration reg = RegisteredSublevelManager.getRegistration(shipId);
                        boolean loaded = claim != null && shipLevel != null && SableShipUtils.isOnShip(shipLevel, claim.getCenter());
                        buf.writeUtf(shipName);
                        buf.writeUtf(shipId);
                        buf.writeBoolean(info != null && info.isActive());
                        buf.writeInt(info != null ? info.getClaimsForBlock() : 0);
                        buf.writeInt(info != null && info.isBlockCountKnown() ? info.getBlockCount() : -1);
                        buf.writeInt(info != null ? info.getBlockLimit() : 0);
                        boolean hasCoords = reg != null && reg.worldX != null && reg.worldY != null && reg.worldZ != null;
                        buf.writeBoolean(hasCoords);
                        buf.writeInt(hasCoords ? reg.worldX.intValue() : 0);
                        buf.writeInt(hasCoords ? reg.worldY.intValue() : 0);
                        buf.writeInt(hasCoords ? reg.worldZ.intValue() : 0);
                        buf.writeBoolean(info != null && info.isForceloadEnabled());
                        buf.writeBoolean(loaded);
                    }
                    buf.writeInt(AeroClaimManager.getFreeProviderClaims(player));
                    buf.writeInt(AeroClaimManager.getMigratedSlots(level, player.getUUID()));
                    buf.writeInt(AeroClaimManager.getUsedSlots(level, player.getUUID()));
                    buf.writeInt(AeroClaimManager.getMigratedForceloads(level, player.getUUID()));
                    buf.writeInt(AeroClaimSavedData.get(level).getUsedForceloads(player.getUUID()));
                    buf.writeBoolean(returnPos != null);
                    if (returnPos != null) buf.writeBlockPos(returnPos);
                });
    }

    private static void handleReturnToClaim(ServerPlayer player, BlockPos pos) {
        Claim claim = null;
        ServerLevel targetLevel = null;
        for (ServerLevel lvl : player.server.getAllLevels()) {
            claim = ClaimManager.getClaimByCenter(lvl, pos);
            if (claim != null) {
                targetLevel = lvl;
                break;
            }
        }

        if (claim == null || !player.getUUID().equals(claim.getOwner())) {
            player.closeContainer();
            return;
        }

        openClaimBlockMenu(player, targetLevel, pos, claim, false, null);
    }

    private static void handleOpenClaimForShip(ServerPlayer player, String shipId) {
        Claim claim = null;
        ServerLevel targetLevel = null;
        for (ServerLevel lvl : player.server.getAllLevels()) {
            claim = ClaimManager.getClaimByShipId(lvl, shipId);
            if (claim != null) {
                targetLevel = lvl;
                break;
            }
        }

        if (claim == null || !player.getUUID().equals(claim.getOwner())) {
            player.closeContainer();
            return;
        }

        BlockPos pos = claim.getCenter();
        boolean alreadyLoaded = SableShipUtils.isOnShip(targetLevel, pos);
        SublevelLoadService.SublevelLoadSession tempSession = null;

        if (!alreadyLoaded) {
            tempSession = SublevelLoadService.loadTemporarily(targetLevel, shipId, pos);
            if (!tempSession.isLoaded() || !SableShipUtils.isOnShip(targetLevel, pos)) {
                if (tempSession != null) tempSession.close();
                player.sendSystemMessage(Component.translatable("message.aeroclaims.sublevel_not_loaded"));
                return;
            }
        }

        openClaimBlockMenu(player, targetLevel, pos, claim, true, tempSession);
    }

    private static void openClaimBlockMenu(ServerPlayer player, ServerLevel level, BlockPos pos, Claim claim,
                                            boolean returnToShipList,
                                            @Nullable SublevelLoadService.SublevelLoadSession tempLoadSession) {
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof ClaimBlock
                && state.hasProperty(ClaimBlock.OPEN)
                && !state.getValue(ClaimBlock.OPEN)) {
            level.setBlock(pos, state.setValue(ClaimBlock.OPEN, true), 3);
        }

        var ship = SableShipUtils.getShipAt(level, pos);
        boolean onShip = ship != null;
        String shipId = SableShipUtils.getShipId(ship);
        String registeredName = shipId != null ? RegisteredSublevelManager.getRegisteredName(shipId) : null;
        String shipName = registeredName != null ? registeredName : SableShipUtils.getShipName(ship);

        AeroClaimSavedData data = AeroClaimSavedData.get(level);
        int claimsForBlock      = data.getClaimsForBlock(pos);
        int freeSlots           = data.getFreeSlots(player.getUUID());
        int forceloadsForBlock  = data.getForceloadsForBlock(pos);
        int initialBlockCount = onShip
                ? ClaimPreviewManager.getShipBlockCount(level, pos)
                : SyncClaimStatePacket.SHIP_BLOCK_COUNT_UNKNOWN;
        String finalShipName  = shipName != null ? shipName : "";

        player.openMenu(
                new SimpleMenuProvider(
                        (id, inv, p) -> {
                            ClaimBlockMenu menu = new ClaimBlockMenu(
                                    id, inv, pos, claim.getOwner(), finalShipName,
                                    onShip, claim.isActive(),
                                    claim.isAllowParty(), claim.isAllowAllies(), claim.isAllowOthers(),
                                    claimsForBlock, freeSlots, AeroClaimsConfig.BLOCKS_PER_CLAIM.get(), initialBlockCount,
                                    forceloadsForBlock, ClaimManager.isForceloadActive(claim), returnToShipList,
                                    level.dimension());
                            if (tempLoadSession != null) {
                                menu.setTempLoadSession(tempLoadSession);
                            }
                            return menu;
                        },
                        Component.translatable("screen.aeroclaims.claim_settings.title")),
                buf -> {
                    buf.writeBlockPos(pos);
                    buf.writeUUID(claim.getOwner());
                    buf.writeUtf(finalShipName);
                    buf.writeBoolean(onShip);
                    buf.writeBoolean(claim.isActive());
                    buf.writeBoolean(claim.isAllowParty());
                    buf.writeBoolean(claim.isAllowAllies());
                    buf.writeBoolean(claim.isAllowOthers());
                    buf.writeInt(claimsForBlock);
                    buf.writeInt(freeSlots);
                    buf.writeInt(AeroClaimsConfig.BLOCKS_PER_CLAIM.get());
                    buf.writeInt(initialBlockCount);
                    buf.writeInt(forceloadsForBlock);
                    buf.writeBoolean(ClaimManager.isForceloadActive(claim));
                    buf.writeBoolean(returnToShipList);
                });

        if (tempLoadSession != null && !(player.containerMenu instanceof ClaimBlockMenu cbm && cbm.hasTempLoadSession())) {
            tempLoadSession.close();
        }
    }
}
