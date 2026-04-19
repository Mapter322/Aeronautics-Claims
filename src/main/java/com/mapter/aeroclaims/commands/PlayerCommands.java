package com.mapter.aeroclaims.commands;

import com.mapter.aeroclaims.claim.VsClaimManager;
import com.mapter.aeroclaims.ship.RegisteredShipsManager;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.UUID;

public class PlayerCommands {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("aeroclaims")
                        // /aeroclaims info
                        .then(Commands.literal("info")
                                .executes(ctx -> {
                                    CommandSourceStack source = ctx.getSource();
                                    if (!(source.getEntity() instanceof ServerPlayer player)) {
                                        source.sendFailure(Component.translatable("commands.aeroclaims.only_player"));
                                        return 0;
                                    }

                                    Map<String, String> ships = RegisteredShipsManager.getRegisteredShips(player.getUUID());
                                    int current = ships.size();
                                    UUID playerId = player.getUUID();

                                    int migratedSlots = VsClaimManager.getMigratedSlots(player.serverLevel(), playerId);
                                    int usedSlots     = VsClaimManager.getUsedSlots(player.serverLevel(), playerId);
                                    int freeOpac = VsClaimManager.getFreeOpacClaims(player);

                                    source.sendSuccess(() -> Component.translatable("commands.aeroclaims.info.ship_slots", usedSlots, migratedSlots), false);
                                    if (freeOpac >= 0) {
                                        source.sendSuccess(() -> Component.translatable(
                                                "commands.aeroclaims.claim_info.opac_free",
                                                freeOpac
                                        ), false);
                                    } else {
                                        source.sendSuccess(() -> Component.translatable(
                                                "commands.aeroclaims.claim_info.opac_unavailable"
                                        ), false);
                                    }

                                    source.sendSuccess(() -> Component.translatable("commands.aeroclaims.info.registered_count", current), false);
                                    if (ships.isEmpty()) {
                                        source.sendSuccess(() -> Component.translatable("commands.aeroclaims.info.empty"), false);
                                    } else {
                                        for (String name : ships.values()) {
                                            source.sendSuccess(() -> Component.translatable("commands.aeroclaims.info.entry", name), false);
                                        }
                                    }

                                    return 1;
                                }))

                        // /aeroclaims transfer vs <amount>
                        .then(Commands.literal("transfer")
                                .then(Commands.literal("vs")
                                                .then(Commands.argument("amount", IntegerArgumentType.integer(1))
                                                        .executes(ctx -> {
                                                            CommandSourceStack source = ctx.getSource();
                                                            if (!(source.getEntity() instanceof ServerPlayer player)) {
                                                                source.sendFailure(Component.translatable("commands.aeroclaims.only_player"));
                                                                return 0;
                                                            }

                                                            int amount = IntegerArgumentType.getInteger(ctx, "amount");
                                                            int freeOpac = VsClaimManager.getFreeOpacClaims(player);

                                                            VsClaimManager.TransferResult result =
                                                                    VsClaimManager.transferFromOpac(player, amount);

                                                            switch (result) {
                                                                case SUCCESS -> {
                                                                    UUID playerId = player.getUUID();
                                                                    int newMigrated = VsClaimManager.getMigratedSlots(player.serverLevel(), playerId);
                                                                    int newUsed     = VsClaimManager.getUsedSlots(player.serverLevel(), playerId);
                                                                    source.sendSuccess(() -> Component.translatable(
                                                                            "commands.aeroclaims.transfer.success",
                                                                            amount, newUsed, newMigrated
                                                                    ), false);
                                                                }
                                                                case OPAC_NOT_LOADED ->
                                                                        source.sendFailure(Component.translatable("commands.aeroclaims.transfer.opac_not_loaded"));
                                                                case NOT_ENOUGH_FREE ->
                                                                        source.sendFailure(Component.translatable(
                                                                                "commands.aeroclaims.transfer.not_enough",
                                                                                freeOpac, amount
                                                                        ));
                                                                case API_ERROR ->
                                                                        source.sendFailure(Component.translatable("commands.aeroclaims.transfer.error"));
                                                            }

                                                            return result == VsClaimManager.TransferResult.SUCCESS ? 1 : 0;
                                                        })))

                                // /aeroclaims transfer opac <amount>
                                .then(Commands.literal("opac")
                                        .then(Commands.argument("amount", IntegerArgumentType.integer(1))
                                                .executes(ctx -> {
                                                    CommandSourceStack source = ctx.getSource();
                                                    if (!(source.getEntity() instanceof ServerPlayer player)) {
                                                        source.sendFailure(Component.translatable("commands.aeroclaims.only_player"));
                                                        return 0;
                                                    }

                                                    int amount = IntegerArgumentType.getInteger(ctx, "amount");
                                                    int freeShipClaims = VsClaimManager.getFreeSlots(player.serverLevel(), player.getUUID());

                                                    VsClaimManager.TransferResult result =
                                                            VsClaimManager.transferToOpac(player, amount);

                                                    switch (result) {
                                                        case SUCCESS -> {
                                                            UUID playerId = player.getUUID();
                                                            int newMigrated = VsClaimManager.getMigratedSlots(player.serverLevel(), playerId);
                                                            int newUsed     = VsClaimManager.getUsedSlots(player.serverLevel(), playerId);
                                                            source.sendSuccess(() -> Component.translatable(
                                                                    "commands.aeroclaims.transfer_back.success",
                                                                    amount, newUsed, newMigrated
                                                            ), false);
                                                        }
                                                        case OPAC_NOT_LOADED ->
                                                                source.sendFailure(Component.translatable("commands.aeroclaims.transfer.opac_not_loaded"));
                                                        case NOT_ENOUGH_FREE ->
                                                                source.sendFailure(Component.translatable(
                                                                        "commands.aeroclaims.transfer_back.not_enough",
                                                                        freeShipClaims, amount
                                                                ));
                                                        case API_ERROR ->
                                                                source.sendFailure(Component.translatable("commands.aeroclaims.transfer.error"));
                                                    }

                                                    return result == VsClaimManager.TransferResult.SUCCESS ? 1 : 0;
                                                }))))

        );
    }
}
