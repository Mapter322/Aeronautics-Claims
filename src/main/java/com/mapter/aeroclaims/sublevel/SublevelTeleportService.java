package com.mapter.aeroclaims.sublevel;

import com.mapter.aeroclaims.claim.Claim;
import com.mapter.aeroclaims.claim.ClaimManager;
import com.mapter.aeroclaims.config.AeroClaimsConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import java.util.Set;
import java.util.UUID;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class SublevelTeleportService {

    public enum Result {
        SUCCESS,
        INVALID_ID,
        CLAIM_NOT_FOUND,
        NOT_OWNER,
        DISABLED,
        COOLDOWN,
        POSITION_UNKNOWN
    }

    private static final Map<UUID, Long> LAST_TELEPORT = new ConcurrentHashMap<>();

    public static Result teleport(ServerPlayer player, String shipId) {
        if (!AeroClaimsConfig.TELEPORT_ENABLE.get()) {
            return Result.DISABLED;
        }

        final UUID uuid;
        try {
            uuid = UUID.fromString(shipId);
        } catch (IllegalArgumentException e) {
            return Result.INVALID_ID;
        }

        for (ServerLevel level : player.server.getAllLevels()) {
            Claim claim = ClaimManager.getClaimByShipId(level, shipId);
            if (claim == null) continue;

            if (!player.getUUID().equals(claim.getOwner())) {
                return Result.NOT_OWNER;
            }

            if (getRemainingCooldownSeconds(player) > 0) {
                return Result.COOLDOWN;
            }

            Result result = teleportToClaim(player, level, claim, uuid);
            if (result == Result.SUCCESS) {
                LAST_TELEPORT.put(player.getUUID(), System.currentTimeMillis());
            }
            return result;
        }

        return Result.CLAIM_NOT_FOUND;
    }

    public static Result teleportAsAdmin(ServerPlayer player, String shipId) {
        final UUID uuid;
        try {
            uuid = UUID.fromString(shipId);
        } catch (IllegalArgumentException e) {
            return Result.INVALID_ID;
        }

        for (ServerLevel level : player.server.getAllLevels()) {
            Claim claim = ClaimManager.getClaimByShipId(level, shipId);
            if (claim == null) continue;

            return teleportToClaim(player, level, claim, uuid);
        }

        return Result.CLAIM_NOT_FOUND;
    }

    public static int getRemainingCooldownSeconds(ServerPlayer player) {
        long cooldownMillis = AeroClaimsConfig.TELEPORT_COOLDOWN_SECONDS.get().longValue() * 1000L;
        if (cooldownMillis <= 0) return 0;

        Long lastTeleport = LAST_TELEPORT.get(player.getUUID());
        if (lastTeleport == null) return 0;

        long remainingMillis = cooldownMillis - (System.currentTimeMillis() - lastTeleport);
        return remainingMillis <= 0 ? 0 : (int) ((remainingMillis + 999L) / 1000L);
    }

    public static Component resultMessage(Result result, String shipId, int cooldownSeconds) {
        return switch (result) {
            case SUCCESS -> Component.translatable("commands.aeroclaims.teleport.success", shipId);
            case INVALID_ID -> Component.translatable("commands.aeroclaims.teleport.invalid_uuid", shipId);
            case CLAIM_NOT_FOUND -> Component.translatable("commands.aeroclaims.teleport.not_found", shipId);
            case NOT_OWNER -> Component.translatable("commands.aeroclaims.teleport.not_owner");
            case DISABLED -> Component.translatable("commands.aeroclaims.teleport.disabled");
            case COOLDOWN -> Component.translatable("commands.aeroclaims.teleport.cooldown", cooldownSeconds);
            case POSITION_UNKNOWN -> Component.translatable("commands.aeroclaims.teleport.position_unknown");
        };
    }

    private static Result teleportToClaim(ServerPlayer player, ServerLevel level, Claim claim, UUID shipId) {
        BlockPos center = claim.getCenter();
        String loadedShipId = SableShipUtils.getShipId(SableShipUtils.getShipAt(level, center));
        if (shipId.toString().equals(loadedShipId)) {
            Vec3 localPosition = Vec3.atBottomCenterOf(center.above());
            player.teleportTo(level, localPosition.x, localPosition.y, localPosition.z,
                    Set.of(), player.getYRot(), player.getXRot());
            player.setDeltaMovement(Vec3.ZERO);
            player.fallDistance = 0.0F;
            return Result.SUCCESS;
        }

        RegisteredSublevelManager.ShipRegistration registration =
                RegisteredSublevelManager.getRegistration(shipId.toString());
        if (registration == null
                || registration.worldX == null
                || registration.worldY == null
                || registration.worldZ == null) {
            return Result.POSITION_UNKNOWN;
        }

        player.teleportTo(level, registration.worldX, registration.worldY, registration.worldZ,
                Set.of(), player.getYRot(), player.getXRot());
        player.setDeltaMovement(Vec3.ZERO);
        player.fallDistance = 0.0F;
        return Result.SUCCESS;
    }

    private SublevelTeleportService() {
    }
}
