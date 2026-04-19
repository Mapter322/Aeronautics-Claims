package com.mapter.aeroclaims.commands;

import com.mapter.aeroclaims.sublevel.UnregisteredSublevelManager;
import com.mapter.aeroclaims.sublevel.SableShipUtils;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;

public class AdminCommands {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("aeroclaims")
                        .then(Commands.literal("sublevels")
                                .then(Commands.literal("unclaimed")
                                    .requires(source -> source.hasPermission(2))
                                    .then(Commands.literal("clear")
                                            .executes(ctx -> {
                                                CommandSourceStack source = ctx.getSource();

                                                int total = UnregisteredSublevelManager.getCount();
                                                if (total == 0) {
                                                    source.sendSuccess(() -> Component.translatable("commands.aeroclaims.ships.clear.none"), true);
                                                    return 1;
                                                }

                                            int deleted = 0;
                                            int failed = 0;
                                            java.util.List<String> ids = new ArrayList<>(UnregisteredSublevelManager.getShipIds());
                                            for (String shipId : ids) {
                                                boolean ok = SableShipUtils.deleteShipById(source.getLevel(), shipId);
                                                if (ok) {
                                                    deleted++;
                                                    UnregisteredSublevelManager.removeShip(shipId);
                                                } else {
                                                    failed++;
                                                }
                                            }

                                            int finalDeleted = deleted;
                                            int finalFailed = failed;
                                            source.sendSuccess(
                                                    () -> Component.translatable("commands.aeroclaims.ships.clear.done", finalDeleted, finalFailed, total),
                                                    true
                                            );
                                            return 1;
                                        }))))
        );
    }
}
