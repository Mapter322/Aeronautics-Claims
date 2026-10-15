package com.mapter.aeroclaims.commands;

import com.mapter.aeroclaims.claim.AeroClaimManager;
import com.mapter.aeroclaims.claim.AeroClaimSavedData;
import com.mapter.aeroclaims.claim.Claim;
import com.mapter.aeroclaims.claim.ClaimManager;
import com.mapter.aeroclaims.claim.ClaimSavedData;
import com.mapter.aeroclaims.block.ClaimBlockEntity;
import com.mapter.aeroclaims.config.AeroClaimsConfig;
import com.mapter.aeroclaims.registry.ModBlocks;
import com.mapter.aeroclaims.sublevel.RegisteredSublevelManager;
import com.mapter.aeroclaims.sublevel.SableShipUtils;
import com.mapter.aeroclaims.sublevel.SublevelLoadService;
import com.mapter.aeroclaims.sublevel.SublevelTeleportService;
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
                    .then(Commands.literal("teleport")
                        .then(Commands.argument("shipUuid", StringArgumentType.word())
                            .executes(ctx -> adminTeleport(
                                    ctx.getSource(),
                                    StringArgumentType.getString(ctx, "shipUuid")))))
                    .then(Commands.literal("repair")
                        .executes(ctx -> repairLoadedSublevels(ctx.getSource())))
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
                                        StringArgumentType.getString(ctx, "shipUuid"))))))));

    }

    private enum RefreshResult {
        SUCCESS_TEMP_LOADED,
        SUCCESS_ALREADY_LOADED,
        FAILED_NOT_FOUND,
        FAILED_NO_REGISTRATION,
        FAILED_SUBLEVEL_NOT_LOADED,
        FAILED_NOT_ON_SHIP,
        FAILED_ZERO_LIMIT,
        FAILED_RECOUNT_FAILED
    }

    private static int adminRefreshAll(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        List<Claim> claims = List.copyOf(ClaimSavedData.get(level).getClaims());

        int refreshed = 0;
        int tempLoaded = 0;
        int failed    = 0;

        for (Claim claim : claims) {
            RefreshResult res = doRefreshClaim(level, claim.getCenter());
            if (res == RefreshResult.SUCCESS_TEMP_LOADED) {
                refreshed++;
                tempLoaded++;
            } else if (res == RefreshResult.SUCCESS_ALREADY_LOADED) {
                refreshed++;
            } else {
                failed++;
            }
        }

        int fr = refreshed, ft = tempLoaded, ff = failed, total = claims.size();
        source.sendSuccess(
                () -> Component.translatable("commands.aeroclaims.admin.refresh.all.done", fr, ft, ff, total),
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

        int refreshed = 0, tempLoaded = 0, failed = 0;
        for (Claim claim : ownerClaims) {
            RefreshResult res = doRefreshClaim(level, claim.getCenter());
            if (res == RefreshResult.SUCCESS_TEMP_LOADED) {
                refreshed++;
                tempLoaded++;
            } else if (res == RefreshResult.SUCCESS_ALREADY_LOADED) {
                refreshed++;
            } else {
                failed++;
            }
        }

        int fr = refreshed, ft = tempLoaded, ff = failed, total = ownerClaims.size();
        source.sendSuccess(
                () -> Component.translatable(
                        "commands.aeroclaims.admin.refresh.player.done", name, fr, ft, ff, total),
                true);
        return fr > 0 ? 1 : 0;
    }

    private static int adminRefreshByShipUuid(CommandSourceStack source, String shipUuidStr) {
        try {
            UUID.fromString(shipUuidStr);
        } catch (IllegalArgumentException e) {
            source.sendFailure(Component.translatable(
                    "commands.aeroclaims.admin.refresh.invalid_sublevel_uuid", shipUuidStr));
            return 0;
        }

        Claim claim = null;
        ServerLevel targetLevel = null;
        for (ServerLevel lvl : source.getServer().getAllLevels()) {
            claim = ClaimManager.getClaimByShipId(lvl, shipUuidStr);
            if (claim != null) {
                targetLevel = lvl;
                break;
            }
        }

        if (claim == null || targetLevel == null) {
            source.sendFailure(Component.translatable(
                    "commands.aeroclaims.admin.refresh.sublevel_not_found", shipUuidStr));
            return 0;
        }

        BlockPos center = claim.getCenter();
        RefreshResult result = doRefreshClaim(targetLevel, center);
        String shipName = RegisteredSublevelManager.getRegisteredName(shipUuidStr);
        String displayName = shipName != null ? shipName : shipUuidStr;

        if (result == RefreshResult.SUCCESS_TEMP_LOADED) {
            source.sendSuccess(
                    () -> Component.translatable(
                            "commands.aeroclaims.admin.refresh.sublevel.done_temp_loaded", displayName),
                    true);
            return 1;
        } else if (result == RefreshResult.SUCCESS_ALREADY_LOADED) {
            source.sendSuccess(
                    () -> Component.translatable(
                            "commands.aeroclaims.admin.refresh.sublevel.done_already_loaded", displayName),
                    true);
            return 1;
        } else {
            String errorDetail = switch (result) {
                case FAILED_NOT_FOUND -> "клейм не найден в базе данных";
                case FAILED_NO_REGISTRATION -> "в реестре нет сохраненных мировых координат сублевела";
                case FAILED_SUBLEVEL_NOT_LOADED -> "сублевел не удалось загрузить в память (Sable вернул null)";
                case FAILED_NOT_ON_SHIP -> "блок привата в центре не опознан как находящийся на сублевеле";
                case FAILED_ZERO_LIMIT -> "лимит блоков для этого клейма равен 0 (нет купленных слотов привата)";
                case FAILED_RECOUNT_FAILED -> "ошибка при подсчете блоков (recountShipBlocks < 0)";
                default -> "ошибка обновления";
            };
            source.sendFailure(Component.literal(
                    "§c[AeroClaims] Не удалось обновить " + displayName + ": " + errorDetail));
            return 0;
        }
    }

    private static RefreshResult doRefreshClaim(ServerLevel level, BlockPos center) {
        Claim claim = ClaimManager.getClaimByCenter(level, center);
        if (claim == null) return RefreshResult.FAILED_NOT_FOUND;

        String shipId = claim.getShipId();
        if (shipId == null) return RefreshResult.FAILED_NOT_ON_SHIP;

        RegisteredSublevelManager.ShipRegistration reg =
                RegisteredSublevelManager.getRegistration(shipId);
        if (reg == null || reg.worldX == null || reg.worldZ == null) {
            return RefreshResult.FAILED_NO_REGISTRATION;
        }

        try (SublevelLoadService.SublevelLoadSession session =
                     SublevelLoadService.loadTemporarily(level, shipId, center)) {
            if (!session.isLoaded()) {
                return RefreshResult.FAILED_SUBLEVEL_NOT_LOADED;
            }

            if (!SableShipUtils.isOnShip(level, center)) return RefreshResult.FAILED_NOT_ON_SHIP;

            int maxSize = AeroClaimManager.getBlockLimit(level, center);
            if (maxSize <= 0) return RefreshResult.FAILED_ZERO_LIMIT;

            boolean deactivateOnOverflow = AeroClaimsConfig.DEACTIVATE_ON_OVERFLOW.get();
            int blockCount = ClaimManager.recountShipBlocks(level, center, deactivateOnOverflow);
            if (blockCount < 0) return RefreshResult.FAILED_RECOUNT_FAILED;

            var ship = SableShipUtils.getShipAt(level, center);
            String currentShipId = SableShipUtils.getShipId(ship);

            AeroClaimSavedData data = AeroClaimSavedData.get(level);
            data.cacheShipBlockCount(center, blockCount);
            if (currentShipId != null) {
                data.cacheShipId(center, currentShipId);
            }

            return session.wasAlreadyLoaded() ? RefreshResult.SUCCESS_ALREADY_LOADED : RefreshResult.SUCCESS_TEMP_LOADED;
        }
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
                "commands.aeroclaims.sublevels.delete.done", fd, ff, total), true);
        return 1;
    }

    private static int adminTeleport(CommandSourceStack source, String shipId) {
        ServerPlayer player = CommandUtils.requirePlayer(source);
        if (player == null) return 0;

        SublevelTeleportService.Result result =
                SublevelTeleportService.teleportAsAdmin(player, shipId);
        if (result == SublevelTeleportService.Result.SUCCESS) {
            source.sendSuccess(
                    () -> SublevelTeleportService.resultMessage(result, shipId, 0), true);
            return 1;
        }

        source.sendFailure(SublevelTeleportService.resultMessage(result, shipId, 0));
        return 0;
    }

    private static int repairLoadedSublevels(CommandSourceStack source) {
        int checked = 0;
        int repaired = 0;
        int tempLoaded = 0;

        for (ServerLevel level : source.getServer().getAllLevels()) {
            List<Claim> claims = List.copyOf(ClaimSavedData.get(level).getClaims());
            for (Claim claim : claims) {
                String shipId = claim.getShipId();
                if (shipId == null) continue;

                try (SublevelLoadService.SublevelLoadSession session =
                             SublevelLoadService.loadTemporarily(level, shipId, claim.getCenter())) {
                    if (session.isLoaded()) {
                        if (!session.wasAlreadyLoaded()) {
                            tempLoaded++;
                        }
                        checked++;
                        BlockPos center = claim.getCenter();
                        var state = level.getBlockState(center);
                        boolean repairedThisClaim = false;

                        if (!state.is(ModBlocks.CLAIM_BLOCK.get())) {
                            level.setBlock(center, ModBlocks.CLAIM_BLOCK.get().defaultBlockState(), 3);
                            repairedThisClaim = true;
                        }

                        if (level.getBlockEntity(center) instanceof ClaimBlockEntity blockEntity
                                && !claim.getOwner().equals(blockEntity.getOwner())) {
                            blockEntity.setOwner(claim.getOwner());
                            repairedThisClaim = true;
                        }

                        if (repairedThisClaim) repaired++;
                    }
                }
            }
        }

        int checkedCount = checked;
        int repairedCount = repaired;
        int tempLoadedCount = tempLoaded;
        source.sendSuccess(() -> Component.translatable(
                "commands.aeroclaims.sublevels.repair.done", checkedCount, tempLoadedCount, repairedCount), true);
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
