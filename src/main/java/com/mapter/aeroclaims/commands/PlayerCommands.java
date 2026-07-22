package com.mapter.aeroclaims.commands;

import com.mapter.aeroclaims.claim.AeroClaimManager;
import com.mapter.aeroclaims.claim.ClaimBriefInfo;
import com.mapter.aeroclaims.config.AeroClaimsConfig;
import com.mapter.aeroclaims.screen.AeroClaimsMenu;
import com.mapter.aeroclaims.sublevel.RegisteredSublevelManager;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;

import java.util.Map;
import java.util.UUID;


public class PlayerCommands {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("aeroclaims")
                .then(Commands.literal("info")
                    .executes(ctx -> executeInfo(ctx.getSource(), null, null))
                    .then(Commands.argument("player", EntityArgument.player())
                        .requires(source -> source.hasPermission(2))
                        .executes(ctx -> {
                            ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                            return executeInfo(
                                    ctx.getSource(),
                                    target.getUUID(),
                                    target.getGameProfile().getName()
                            );
                        })))

                .then(Commands.literal("menu")
                    .executes(ctx -> executeOpenMenu(ctx.getSource())))
        );
    }


    static int executeOpenMenu(CommandSourceStack source) {
        ServerPlayer player = CommandUtils.requirePlayer(source);
        if (player == null) return 0;

        Map<String, String> ships = RegisteredSublevelManager.getRegisteredShips(player.getUUID());
        ServerLevel level = player.serverLevel();

        player.openMenu(new SimpleMenuProvider(
                (id, inv, p) -> new AeroClaimsMenu(id, inv),
                Component.translatable("screen.aeroclaims.menu.title")),
                buf -> {
                    buf.writeVarInt(ships.size());
                    for (Map.Entry<String, String> entry : ships.entrySet()) {
                        String shipId = entry.getKey();
                        String shipName = entry.getValue();
                        ClaimBriefInfo info = ClaimBriefInfo.ofShip(level, shipId);
                        RegisteredSublevelManager.ShipRegistration reg = RegisteredSublevelManager.getRegistration(shipId);
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
                    }
                    buf.writeInt(AeroClaimManager.getFreeProviderClaims(player));
                    buf.writeInt(AeroClaimManager.getMigratedSlots(level, player.getUUID()));
                    buf.writeInt(AeroClaimManager.getUsedSlots(level, player.getUUID()));
                    buf.writeInt(AeroClaimManager.getMigratedForceloads(level, player.getUUID()));
                    buf.writeInt(AeroClaimManager.getUsedForceloads(level, player.getUUID()));
                });
        return 1;
    }

    static int executeInfo(CommandSourceStack source, UUID targetUuid, String targetName) {

        if (targetUuid == null) {
            ServerPlayer player = CommandUtils.requirePlayer(source);
            if (player == null) return 0;
            targetUuid  = player.getUUID();
            targetName  = player.getGameProfile().getName();
        }

        Map<String, String> ships = RegisteredSublevelManager.getRegisteredShips(targetUuid);
        int migratedSlots = AeroClaimManager.getMigratedSlots(source.getLevel(), targetUuid);
        int usedSlots     = AeroClaimManager.getUsedSlots(source.getLevel(), targetUuid);
        int freeSlots     = AeroClaimManager.getFreeSlots(source.getLevel(), targetUuid);
        int migratedFl    = AeroClaimManager.getMigratedForceloads(source.getLevel(), targetUuid);
        int freeFl        = AeroClaimManager.getFreeForceloads(source.getLevel(), targetUuid);
        int usedFl        = migratedFl - freeFl;

        final UUID   finalUuid = targetUuid;
        final String finalName = targetName;

        source.sendSuccess(() -> Component.translatable("commands.aeroclaims.info.header", finalName), false);
        source.sendSuccess(() -> Component.translatable("commands.aeroclaims.info.sublevel_slots", usedSlots), false);
        if (AeroClaimsConfig.PROVIDER_SLOTS_FORCELOAD.get()) {
            source.sendSuccess(() -> Component.translatable("commands.aeroclaims.info.forceload_slots", usedFl), false);
            source.sendSuccess(() -> Component.translatable("commands.aeroclaims.info.forceload_free", freeFl), false);
        }

        if (source.getEntity() instanceof ServerPlayer caller && caller.getUUID().equals(finalUuid)) {
            int freeOpac = AeroClaimManager.getFreeProviderClaims(caller);
            if (freeOpac >= 0) {
                source.sendSuccess(() -> Component.translatable("commands.aeroclaims.claim_info.opac_free", freeOpac), false);
            } else {
                source.sendSuccess(() -> Component.translatable("commands.aeroclaims.claim_info.opac_unavailable"), false);
            }
        }

        source.sendSuccess(() -> Component.translatable("commands.aeroclaims.info.registered_count", ships.size()), false);

        if (ships.isEmpty()) {
            source.sendSuccess(() -> Component.translatable("commands.aeroclaims.info.empty"), false);
        } else {
            ServerLevel level = source.getLevel();
            for (Map.Entry<String, String> entry : ships.entrySet()) {
                String shipId   = entry.getKey();
                String shipName = entry.getValue();
                ClaimBriefInfo info = ClaimBriefInfo.ofShip(level, shipId);
                RegisteredSublevelManager.ShipRegistration reg = RegisteredSublevelManager.getRegistration(shipId);
                source.sendSuccess(() -> buildShipEntry(shipName, shipId, info, reg), false);
            }
        }

        return 1;
    }


    private static MutableComponent buildShipEntry(String shipName, String shipId, ClaimBriefInfo info,
                                                       RegisteredSublevelManager.ShipRegistration reg) {
        MutableComponent hover = Component.translatable("commands.aeroclaims.info.entry.hover.name", shipName)
                .append(Component.literal("\n"))
                .append(Component.translatable("commands.aeroclaims.info.entry.hover", shipId));
        if (info != null) {
            hover = hover.append(Component.literal("\n"))
                    .append(Component.translatable("commands.aeroclaims.info.entry.hover.claims",
                            info.getClaimsForBlock()));
            if (AeroClaimsConfig.PROVIDER_SLOTS_FORCELOAD.get()) {
                hover = hover.append(Component.literal("\n"))
                        .append(Component.translatable("commands.aeroclaims.info.entry.hover.forceloads",
                                info.getForceloadsForBlock()));
            }
            if (info.isBlockCountKnown()) {
                hover = hover.append(Component.literal("\n"))
                        .append(Component.translatable("commands.aeroclaims.info.entry.hover.blocks",
                                info.getBlockCount(), info.getBlockLimit()));
            } else {
                hover = hover.append(Component.literal("\n"))
                        .append(Component.translatable("commands.aeroclaims.info.entry.hover.blocks_unknown"));
            }
        }
        if (reg != null && reg.worldX != null && reg.worldY != null && reg.worldZ != null) {
            hover = hover.append(Component.literal("\n"))
                    .append(Component.translatable("commands.aeroclaims.info.entry.hover.coords",
                            reg.worldX.intValue(), reg.worldY.intValue(), reg.worldZ.intValue()));
        }
        final MutableComponent finalHover = hover;
        boolean isActive = info != null && info.isActive();
        return Component.literal("- ")
                .withStyle(style -> style.withColor(isActive ? 0x00FF00 : 0xFF0000))
                .append(Component.literal(shipName).withStyle(style -> style.withColor(0xFFFFFF)))
                .withStyle(style -> style
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, finalHover))
                        .withClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, shipId))
                );
    }
}
