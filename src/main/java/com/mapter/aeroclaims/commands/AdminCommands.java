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
import com.mapter.aeroclaims.sublevel.SubLevelTicketManager;
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
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
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
                        .executes(ctx -> repairLoadedSublevels(ctx.getSource()))
                        .then(Commands.literal("claim-blocks")
                            .executes(ctx -> repairLoadedSublevels(ctx.getSource())))
                        .then(Commands.literal("phantoms")
                            .executes(ctx -> checkPhantoms(ctx.getSource()))
                            .then(Commands.literal("check")
                                .executes(ctx -> checkPhantoms(ctx.getSource())))
                            .then(Commands.literal("clear")
                                .executes(ctx -> {
                                    ctx.getSource().sendFailure(Component.translatable(
                                            "commands.aeroclaims.sublevels.repair.phantoms.clear_confirm_needed"));
                                    return 0;
                                })
                                .then(Commands.literal("confirm")
                                    .executes(ctx -> clearPhantoms(ctx.getSource()))))))
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

    private static final Logger LOGGER = LoggerFactory.getLogger(AdminCommands.class);

    private static long lastRefreshAllTime = 0L;
    private static final long REFRESH_ALL_COOLDOWN_MS = 5000L;

    private static long lastRepairTime = 0L;
    private static final long REPAIR_COOLDOWN_MS = 5000L;

    private static long lastPhantomScanTime = 0L;
    private static final long PHANTOM_COOLDOWN_MS = 5000L;

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
        long now = System.currentTimeMillis();
        long diff = now - lastRefreshAllTime;
        if (diff < REFRESH_ALL_COOLDOWN_MS) {
            long remainingSec = (REFRESH_ALL_COOLDOWN_MS - diff + 999) / 1000;
            source.sendFailure(Component.translatable(
                    "commands.aeroclaims.cooldown", remainingSec));
            return 0;
        }
        lastRefreshAllTime = now;

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
                case FAILED_NOT_FOUND -> "приват не найден в базе данных";
                case FAILED_NO_REGISTRATION -> "в реестре нет сохраненных мировых координат сублевела";
                case FAILED_SUBLEVEL_NOT_LOADED -> "сублевел не удалось загрузить в память (Sable вернул null)";
                case FAILED_NOT_ON_SHIP -> "блок привата в центре не опознан как находящийся на сублевеле";
                case FAILED_ZERO_LIMIT -> "лимит блоков для этого привата равен 0 (нет купленных слотов привата)";
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
        long now = System.currentTimeMillis();
        long diff = now - lastRepairTime;
        if (diff < REPAIR_COOLDOWN_MS) {
            long remainingSec = (REPAIR_COOLDOWN_MS - diff + 999) / 1000;
            source.sendFailure(Component.translatable(
                    "commands.aeroclaims.cooldown", remainingSec));
            return 0;
        }
        lastRepairTime = now;

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

    private record PhantomClaimEntry(ServerLevel level, Claim claim, String shipId, String displayName, String ownerName) {}

    private static List<PhantomClaimEntry> findPhantomClaims(MinecraftServer server) {
        List<PhantomClaimEntry> phantoms = new ArrayList<>();

        for (ServerLevel level : server.getAllLevels()) {
            List<Claim> claims = List.copyOf(ClaimSavedData.get(level).getClaims());
            for (Claim claim : claims) {
                String shipId = claim.getShipId();
                if (shipId == null) continue;

                RegisteredSublevelManager.ShipRegistration reg = RegisteredSublevelManager.getRegistration(shipId);
                String displayName = reg != null && reg.name != null ? reg.name : shipId;
                String ownerName = resolveOwnerName(server, claim.getOwner(), reg);

                if (isDefinitivePhantom(server, level, claim, shipId)) {
                    phantoms.add(new PhantomClaimEntry(level, claim, shipId, displayName, ownerName));
                }
            }
        }

        return phantoms;
    }

    private static boolean isDefinitivePhantom(MinecraftServer server, ServerLevel homeLevel, Claim claim, String shipId) {
        UUID uuid;
        try {
            uuid = UUID.fromString(shipId);
        } catch (IllegalArgumentException e) {
            return true;
        }

        for (ServerLevel anyLevel : server.getAllLevels()) {
            if (SublevelLoadService.isSublevelLoaded(anyLevel, uuid)) {
                return false;
            }
        }

        if (canLoadInLevel(homeLevel, shipId, claim.getCenter())) {
            return false;
        }

        for (ServerLevel otherLevel : server.getAllLevels()) {
            if (otherLevel == homeLevel) continue;
            if (canLoadInLevel(otherLevel, shipId, null)) {
                return false;
            }
        }

        // Retry pass: flush pending holding chunk changes to prevent race conditions from recent ticket unloads
        try {
            SubLevelContainer container = SubLevelContainer.getContainer(homeLevel);
            if (container instanceof ServerSubLevelContainer sc) {
                sc.getHoldingChunkMap().processChanges();
            }
        } catch (Throwable ignored) {}

        if (canLoadInLevel(homeLevel, shipId, claim.getCenter())) {
            return false;
        }

        return true;
    }

    private static boolean canLoadInLevel(ServerLevel level, String shipId, @Nullable BlockPos center) {
        try (SublevelLoadService.SublevelLoadSession session =
                     SublevelLoadService.loadTemporarily(level, shipId, center)) {
            return session.isLoaded();
        } catch (Throwable t) {
            // Never assume phantom on I/O or transient errors, protecting valid player claims
            return true;
        }
    }

    private static String resolveOwnerName(MinecraftServer server, UUID ownerUuid, RegisteredSublevelManager.ShipRegistration reg) {
        if (ownerUuid != null) {
            ServerPlayer player = server.getPlayerList().getPlayer(ownerUuid);
            if (player != null) {
                return player.getGameProfile().getName();
            }
        }
        if (reg != null && reg.owner != null && !reg.owner.isBlank()) {
            return reg.owner;
        }
        return ownerUuid != null ? ownerUuid.toString() : "unknown";
    }

    private static Component formatPhantomEntry(PhantomClaimEntry p) {
        String dimPath = p.level().dimension().location().getPath();
        String fullDim = p.level().dimension().location().toString();
        BlockPos pos = p.claim().getCenter();

        MutableComponent root = Component.literal(" §7- ")
                .append(Component.literal(p.displayName()).withStyle(ChatFormatting.YELLOW));

        MutableComponent uuidComp = Component.literal(p.shipId())
                .withStyle(style -> style
                        .withColor(ChatFormatting.AQUA)
                        .withUnderlined(true)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, p.shipId()))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                Component.translatable("commands.aeroclaims.sublevels.repair.phantoms.copy_uuid"))));

        root.append(Component.literal(" §7("))
                .append(uuidComp)
                .append(Component.literal("§7)"));

        root.append(Component.literal(" §7| "))
                .append(Component.translatable("commands.aeroclaims.sublevels.repair.phantoms.owner_prefix"))
                .append(Component.literal(p.ownerName()).withStyle(ChatFormatting.WHITE));

        String tpCoordsCmd = "/execute in " + fullDim + " run tp @s " + pos.getX() + " " + pos.getY() + " " + pos.getZ();
        MutableComponent coordsComp = Component.literal("[" + dimPath + ": " + pos.getX() + ", " + pos.getY() + ", " + pos.getZ() + "]")
                .withStyle(style -> style
                        .withColor(ChatFormatting.GREEN)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, tpCoordsCmd))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                Component.translatable("commands.aeroclaims.sublevels.repair.phantoms.tp_coords"))));

        root.append(Component.literal(" §7| ")).append(coordsComp);

        return root;
    }

    private static int checkPhantoms(CommandSourceStack source) {
        long now = System.currentTimeMillis();
        long diff = now - lastPhantomScanTime;
        if (diff < PHANTOM_COOLDOWN_MS) {
            long remainingSec = (PHANTOM_COOLDOWN_MS - diff + 999) / 1000;
            source.sendFailure(Component.translatable(
                    "commands.aeroclaims.cooldown", remainingSec));
            return 0;
        }
        lastPhantomScanTime = now;

        MinecraftServer server = source.getServer();
        List<PhantomClaimEntry> phantoms = findPhantomClaims(server);

        if (phantoms.isEmpty()) {
            source.sendSuccess(() -> Component.translatable("commands.aeroclaims.sublevels.repair.phantoms.none"), false);
            return 1;
        }

        int count = phantoms.size();
        source.sendSuccess(() -> Component.translatable(
                "commands.aeroclaims.sublevels.repair.phantoms.header", count), false);

        for (PhantomClaimEntry p : phantoms) {
            source.sendSuccess(() -> formatPhantomEntry(p), false);
        }

        source.sendSuccess(() -> Component.translatable("commands.aeroclaims.sublevels.repair.phantoms.clear_hint"), false);
        return count;
    }

    private static int clearPhantoms(CommandSourceStack source) {
        long now = System.currentTimeMillis();
        long diff = now - lastPhantomScanTime;
        if (diff < PHANTOM_COOLDOWN_MS) {
            long remainingSec = (PHANTOM_COOLDOWN_MS - diff + 999) / 1000;
            source.sendFailure(Component.translatable(
                    "commands.aeroclaims.cooldown", remainingSec));
            return 0;
        }
        lastPhantomScanTime = now;

        MinecraftServer server = source.getServer();
        List<PhantomClaimEntry> phantoms = findPhantomClaims(server);

        if (phantoms.isEmpty()) {
            source.sendSuccess(() -> Component.translatable("commands.aeroclaims.sublevels.repair.phantoms.none"), false);
            return 1;
        }

        int cleared = 0;
        for (PhantomClaimEntry p : phantoms) {
            if (!isDefinitivePhantom(server, p.level(), p.claim(), p.shipId())) {
                LOGGER.warn("[AeroClaims] Sublevel {} was loaded/found during clear pass; skipping purge to protect data.", p.shipId());
                continue;
            }
            purgePhantomClaim(p.level(), p.claim());
            cleared++;
        }

        RegisteredSublevelManager.saveNow();
        UnregisteredSublevelManager.saveNow();

        int count = cleared;
        source.sendSuccess(() -> Component.translatable(
                "commands.aeroclaims.sublevels.repair.phantoms.cleared", count), true);
        return cleared;
    }

    private static void purgePhantomClaim(ServerLevel level, Claim claim) {
        String shipId = claim.getShipId();
        UUID ownerId = claim.getOwner();
        BlockPos center = claim.getCenter();

        AeroClaimManager.releaseAllClaimsForBlock(level, ownerId, center);
        AeroClaimManager.releaseAllForceloadsForBlock(level, ownerId, center);
        ClaimManager.removeClaim(level, center);

        if (shipId != null) {
            RegisteredSublevelManager.unregisterShip(shipId);
            UnregisteredSublevelManager.removeShip(shipId);
            try {
                SubLevelTicketManager.remove(level, UUID.fromString(shipId));
            } catch (IllegalArgumentException ignored) {}
        }

        if (level.getBlockState(center).is(ModBlocks.CLAIM_BLOCK.get())) {
            level.removeBlock(center, false);
        }

        AeroClaimSavedData data = AeroClaimSavedData.get(level);
        data.clearCachedShipBlockCount(center);
        data.clearCachedShipId(center);
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
