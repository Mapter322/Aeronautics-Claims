package com.mapter.aeroclaims.commands;

import com.mapter.aeroclaims.ship.UnregisteredShipsManager;
import com.mapter.aeroclaims.ship.SableShipUtils;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;

public class AdminCommands {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("vsclaims")
                        .then(Commands.literal("ships")
                                .then(Commands.literal("unclaimed")
                                    .requires(source -> source.hasPermission(2))
                                    .then(Commands.literal("clear")
                                            .executes(ctx -> {
                                                CommandSourceStack source = ctx.getSource();

                                                int total = UnregisteredShipsManager.getCount();
                                                if (total == 0) {
                                                    source.sendSuccess(() -> Component.translatable("commands.vsclaims.ships.clear.none"), true);
                                                    return 1;
                                                }

                                            int deleted = 0;
                                            int failed = 0;
                                            java.util.List<String> ids = new ArrayList<>(UnregisteredShipsManager.getShipIds());
                                            for (String shipId : ids) {
                                                boolean ok = SableShipUtils.deleteShipById(source.getLevel(), shipId);
                                                if (ok) {
                                                    deleted++;
                                                    UnregisteredShipsManager.removeShip(shipId);
                                                } else {
                                                    failed++;
                                                }
                                            }

                                            int finalDeleted = deleted;
                                            int finalFailed = failed;
                                            source.sendSuccess(
                                                    () -> Component.translatable("commands.vsclaims.ships.clear.done", finalDeleted, finalFailed, total),
                                                    true
                                            );
                                            return 1;
                                        }))))
        );
    }
}
