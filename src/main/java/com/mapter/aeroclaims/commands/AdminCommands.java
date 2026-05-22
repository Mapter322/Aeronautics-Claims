package com.mapter.aeroclaims.commands;

import com.mapter.aeroclaims.claim.AeroClaimManager;
import com.mapter.aeroclaims.claim.AeroClaimSavedData;
import com.mapter.aeroclaims.claim.Claim;
import com.mapter.aeroclaims.claim.ClaimManager;
import com.mapter.aeroclaims.claim.ClaimSavedData;
import com.mapter.aeroclaims.config.AeroClaimsConfig;
import com.mapter.aeroclaims.sublevel.RegisteredSublevelManager;
import com.mapter.aeroclaims.sublevel.SableShipUtils;
import com.mapter.aeroclaims.sublevel.UnregisteredSublevelManager;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;


public class AdminCommands {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("aeroclaims")

                .then(Commands.literal("sublevels")
                    .requires(source -> source.hasPermission(2))
                    .then(Commands.literal("dump")
                        .executes(ctx -> dumpAll(ctx.getSource())))
                    .then(Commands.literal("delete")
                        .then(Commands.literal("unclaimed")
                            .then(Commands.literal("confirm")
                                .executes(ctx -> deleteUnclaimed(ctx.getSource())))))
                    .then(Commands.literal("refresh")
                        .then(Commands.literal("all")
                            .executes(ctx -> adminRefreshAll(ctx.getSource())))
                        .then(Commands.literal("player")
                            .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> adminRefreshPlayer(
                                        ctx.getSource(),
                                        EntityArgument.getPlayer(ctx, "player")))))
                        .then(Commands.literal("sublevel")
                            .then(Commands.argument("shipUuid", StringArgumentType.word())
                                .executes(ctx -> adminRefreshByShipUuid(
                                        ctx.getSource(),
                                        StringArgumentType.getString(ctx, "shipUuid"))))))))
        ;
    }


    private static int adminRefreshAll(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        List<Claim> claims = List.copyOf(ClaimSavedData.get(level).getClaims());

        int refreshed = 0;
        int failed    = 0;

        for (Claim claim : claims) {
            if (doRefreshClaim(level, claim.getCenter())) refreshed++;
            else                                           failed++;
        }

        int fr = refreshed, ff = failed, total = claims.size();
        source.sendSuccess(
                () -> Component.translatable("commands.aeroclaims.admin.refresh.all.done", fr, ff, total),
                true);
        return fr > 0 ? 1 : 0;
    }


    private static int adminRefreshPlayer(CommandSourceStack source, ServerPlayer target) {
        ServerLevel level = source.getLevel();
        UUID owner = target.getUUID();
        String name = target.getGameProfile().getName();

        List<Claim> ownerClaims = new ArrayList<>();
        for (Claim claim : ClaimSavedData.get(level).getClaims()) {
            if (owner.equals(claim.getOwner())) ownerClaims.add(claim);
        }

        if (ownerClaims.isEmpty()) {
            source.sendSuccess(
                    () -> Component.translatable("commands.aeroclaims.admin.refresh.no_claims", name),
                    false);
            return 1;
        }

        int refreshed = 0, failed = 0;
        for (Claim claim : ownerClaims) {
            if (doRefreshClaim(level, claim.getCenter())) refreshed++;
            else                                           failed++;
        }

        int fr = refreshed, ff = failed, total = ownerClaims.size();
        source.sendSuccess(
                () -> Component.translatable(
                        "commands.aeroclaims.admin.refresh.player.done", name, fr, ff, total),
                true);
        return fr > 0 ? 1 : 0;
    }


    private static int adminRefreshByShipUuid(CommandSourceStack source, String shipUuidStr) {

        try {
            UUID.fromString(shipUuidStr);
        } catch (IllegalArgumentException e) {
            source.sendFailure(Component.translatable(
                    "commands.aeroclaims.admin.refresh.invalid_ship_uuid", shipUuidStr));
            return 0;
        }

        ServerLevel level = source.getLevel();


        Claim claim = ClaimManager.getClaimByShipId(level, shipUuidStr);

        if (claim == null) {
            source.sendFailure(Component.translatable(
                    "commands.aeroclaims.admin.refresh.ship_not_found", shipUuidStr));
            return 0;
        }

        BlockPos center = claim.getCenter();
        if (doRefreshClaim(level, center)) {

            String shipName = RegisteredSublevelManager.getRegisteredName(shipUuidStr);
            String displayName = shipName != null ? shipName : shipUuidStr;
            source.sendSuccess(
                    () -> Component.translatable(
                            "commands.aeroclaims.admin.refresh.ship.done", displayName),
                    true);
            return 1;
        } else {
            source.sendFailure(Component.translatable(
                    "commands.aeroclaims.admin.refresh.ship_failed", shipUuidStr));
            return 0;
        }
    }


    private static boolean doRefreshClaim(ServerLevel level, BlockPos center) {
        Claim claim = ClaimManager.getClaimByCenter(level, center);
        if (claim == null) return false;

        if (!SableShipUtils.isOnShip(level, center)) return false;

        int maxSize = AeroClaimManager.getBlockLimit(level, center);
        if (maxSize <= 0) return false;

        boolean deactivateOnOverflow = AeroClaimsConfig.DEACTIVATE_ON_OVERFLOW.get();
        int blockCount = ClaimManager.recountShipBlocks(level, center, deactivateOnOverflow);
        if (blockCount < 0) return false;

        var ship = SableShipUtils.getShipAt(level, center);
        String shipId = SableShipUtils.getShipId(ship);

        AeroClaimSavedData data = AeroClaimSavedData.get(level);
        data.cacheShipBlockCount(center, blockCount);
        if (shipId != null) {
            data.cacheShipId(center, shipId);
        }

        return true;
    }


    private static int deleteUnclaimed(CommandSourceStack source) {
        if (!AeroClaimsConfig.ENABLE_DELETE_COMMAND.get()) {
            source.sendFailure(Component.translatable("commands.aeroclaims.sublevels.delete.disabled"));
            return 0;
        }

        int total = UnregisteredSublevelManager.getCount();
        if (total == 0) {
            source.sendSuccess(() -> Component.translatable("commands.aeroclaims.sublevels.delete.none"), true);
            return 1;
        }

        int deleted = 0, failed = 0;
        for (String shipId : List.copyOf(UnregisteredSublevelManager.getShipIds())) {
            if (SableShipUtils.deleteShipById(source.getLevel(), shipId)) {
                deleted++;
                UnregisteredSublevelManager.removeShip(shipId);
            } else {
                failed++;
            }
        }

        int fd = deleted, ff = failed;
        source.sendSuccess(() -> Component.translatable(
                "commands.aeroclaims.ships.delete.done", fd, ff, total), true);
        return 1;
    }



    private static int dumpAll(CommandSourceStack source) {
        RegisteredSublevelManager.saveNow();
        UnregisteredSublevelManager.saveNow();
        int claimed   = RegisteredSublevelManager.getCount();
        int unclaimed = UnregisteredSublevelManager.getCount();
        source.sendSuccess(() -> Component.translatable(
                "commands.aeroclaims.sublevels.dump", claimed, unclaimed), true);
        return 1;
    }
}
