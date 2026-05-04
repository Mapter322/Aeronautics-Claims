package com.mapter.aeroclaims.commands;

import com.mapter.aeroclaims.sublevel.RegisteredSublevelManager;
import com.mapter.aeroclaims.sublevel.SableShipUtils;
import com.mapter.aeroclaims.sublevel.UnregisteredSublevelManager;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

import java.util.List;


public class AdminCommands {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("aeroclaims")

                .then(Commands.literal("sublevels")
                    .requires(source -> source.hasPermission(2))
                    .then(Commands.literal("unclaimed")
                        .then(Commands.literal("clear")
                            .executes(ctx -> clearUnclaimed(ctx.getSource())))
                        .then(Commands.literal("dump")
                            .executes(ctx -> dumpUnclaimed(ctx.getSource()))))
                    .then(Commands.literal("claimed")
                        .then(Commands.literal("dump")
                            .executes(ctx -> dumpClaimed(ctx.getSource()))))
                    .then(Commands.literal("all")
                        .then(Commands.literal("dump")
                            .executes(ctx -> dumpAll(ctx.getSource())))))
        );
    }



    private static int clearUnclaimed(CommandSourceStack source) {
        int total = UnregisteredSublevelManager.getCount();

        if (total == 0) {
            source.sendSuccess(() -> Component.translatable("commands.aeroclaims.sublevels.clear.none"), true);
            return 1;
        }

        int deleted = 0;
        int failed  = 0;

        for (String shipId : List.copyOf(UnregisteredSublevelManager.getShipIds())) {
            if (SableShipUtils.deleteShipById(source.getLevel(), shipId)) {
                deleted++;
                UnregisteredSublevelManager.removeShip(shipId);
            } else {
                failed++;
            }
        }

        int finalDeleted = deleted;
        int finalFailed  = failed;
        source.sendSuccess(() -> Component.translatable(
                "commands.aeroclaims.ships.clear.done", finalDeleted, finalFailed, total
        ), true);
        return 1;
    }

    private static int dumpUnclaimed(CommandSourceStack source) {
        UnregisteredSublevelManager.saveNow();
        int count = UnregisteredSublevelManager.getCount();
        source.sendSuccess(() -> Component.translatable("commands.aeroclaims.sublevels.dump", count), true);
        return 1;
    }

    private static int dumpClaimed(CommandSourceStack source) {
        RegisteredSublevelManager.saveNow();
        int count = RegisteredSublevelManager.getCount();
        source.sendSuccess(() -> Component.translatable("commands.aeroclaims.sublevels.dump", count), true);
        return 1;
    }

    private static int dumpAll(CommandSourceStack source) {
        RegisteredSublevelManager.saveNow();
        UnregisteredSublevelManager.saveNow();
        int claimed   = RegisteredSublevelManager.getCount();
        int unclaimed = UnregisteredSublevelManager.getCount();
        source.sendSuccess(() -> Component.translatable(
                "commands.aeroclaims.sublevels.dump", claimed, unclaimed
        ), true);
        return 1;
    }
}
