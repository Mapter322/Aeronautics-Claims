package com.mapter.aeroclaims.commands;

import com.mapter.aeroclaims.claim.AeroClaimManager;
import com.mapter.aeroclaims.sublevel.RegisteredSublevelManager;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.UUID;


public class PlayerCommands {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("aeroclaims")
                .then(Commands.literal("info")
                    .executes(ctx -> executeInfo(ctx.getSource(), null, null)))

                .then(Commands.literal("transfer")
                    .then(Commands.literal("to")
                        .then(Commands.literal("opac")
                            .then(Commands.argument("amount", IntegerArgumentType.integer(1))
                                .executes(ctx -> executeTransferToOpac(
                                        ctx.getSource(),
                                        IntegerArgumentType.getInteger(ctx, "amount")
                                ))))
                        .then(Commands.literal("aero")
                            .then(Commands.argument("amount", IntegerArgumentType.integer(1))
                                .executes(ctx -> executeTransferFromOpac(
                                        ctx.getSource(),
                                        IntegerArgumentType.getInteger(ctx, "amount")
                                ))))))
        );
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

        final UUID   finalUuid = targetUuid;
        final String finalName = targetName;

        source.sendSuccess(() -> Component.translatable("commands.aeroclaims.info.header", finalName), false);
        source.sendSuccess(() -> Component.translatable("commands.aeroclaims.info.ship_slots", usedSlots, migratedSlots), false);

        if (source.getEntity() instanceof ServerPlayer caller && caller.getUUID().equals(finalUuid)) {
            int freeOpac = AeroClaimManager.getFreeOpacClaims(caller);
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
            for (Map.Entry<String, String> entry : ships.entrySet()) {
                String shipId   = entry.getKey();
                String shipName = entry.getValue();
                source.sendSuccess(() -> buildShipEntry(shipName, shipId), false);
            }
        }

        return 1;
    }


    private static MutableComponent buildShipEntry(String shipName, String shipId) {
        return Component.translatable("commands.aeroclaims.info.entry", shipName)
                .withStyle(style -> style
                        .withHoverEvent(new HoverEvent(
                                HoverEvent.Action.SHOW_TEXT,
                                Component.translatable("commands.aeroclaims.info.entry.hover", shipId)
                        ))
                        .withClickEvent(new ClickEvent(
                                ClickEvent.Action.COPY_TO_CLIPBOARD,
                                shipId
                        ))
                );
    }


    private static int executeTransferFromOpac(CommandSourceStack source, int amount) {
        ServerPlayer player = CommandUtils.requirePlayer(source);
        if (player == null) return 0;

        int freeOpac = AeroClaimManager.getFreeOpacClaims(player);
        AeroClaimManager.TransferResult result = AeroClaimManager.transferFromOpac(player, amount);

        switch (result) {
            case SUCCESS -> {
                UUID id         = player.getUUID();
                int newMigrated = AeroClaimManager.getMigratedSlots(player.serverLevel(), id);
                int newUsed     = AeroClaimManager.getUsedSlots(player.serverLevel(), id);
                source.sendSuccess(() -> Component.translatable(
                        "commands.aeroclaims.transfer.success", amount, newUsed, newMigrated
                ), false);
            }
            case OPAC_NOT_LOADED ->
                    source.sendFailure(Component.translatable("commands.aeroclaims.transfer.opac_not_loaded"));
            case NOT_ENOUGH_FREE ->
                    source.sendFailure(Component.translatable(
                            "commands.aeroclaims.transfer.not_enough", freeOpac, amount
                    ));
            case API_ERROR ->
                    source.sendFailure(Component.translatable("commands.aeroclaims.transfer.error"));
        }

        return CommandUtils.toResult(result);
    }

    private static int executeTransferToOpac(CommandSourceStack source, int amount) {
        ServerPlayer player = CommandUtils.requirePlayer(source);
        if (player == null) return 0;

        int freeShipClaims = AeroClaimManager.getFreeSlots(player.serverLevel(), player.getUUID());
        AeroClaimManager.TransferResult result = AeroClaimManager.transferToOpac(player, amount);

        switch (result) {
            case SUCCESS -> {
                UUID id         = player.getUUID();
                int newMigrated = AeroClaimManager.getMigratedSlots(player.serverLevel(), id);
                int newUsed     = AeroClaimManager.getUsedSlots(player.serverLevel(), id);
                source.sendSuccess(() -> Component.translatable(
                        "commands.aeroclaims.transfer_back.success", amount, newUsed, newMigrated
                ), false);
            }
            case OPAC_NOT_LOADED ->
                    source.sendFailure(Component.translatable("commands.aeroclaims.transfer.opac_not_loaded"));
            case NOT_ENOUGH_FREE ->
                    source.sendFailure(Component.translatable(
                            "commands.aeroclaims.transfer_back.not_enough", freeShipClaims, amount
                    ));
            case API_ERROR ->
                    source.sendFailure(Component.translatable("commands.aeroclaims.transfer.error"));
        }

        return CommandUtils.toResult(result);
    }
}
