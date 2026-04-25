package com.mapter.aeroclaims.commands;

import com.mapter.aeroclaims.claim.AeroClaimManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;


final class CommandUtils {

    private CommandUtils() {}


    @Nullable
    static ServerPlayer requirePlayer(CommandSourceStack source) {
        if (source.getEntity() instanceof ServerPlayer player) {
            return player;
        }
        source.sendFailure(Component.translatable("commands.aeroclaims.only_player"));
        return null;
    }


    static int toResult(AeroClaimManager.TransferResult result) {
        return result == AeroClaimManager.TransferResult.SUCCESS ? 1 : 0;
    }
}
