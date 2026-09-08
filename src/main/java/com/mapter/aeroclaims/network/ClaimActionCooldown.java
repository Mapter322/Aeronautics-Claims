package com.mapter.aeroclaims.network;

import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ClaimActionCooldown {

    private static final long COOLDOWN_MILLIS = 5_000L;
    private static final Map<UUID, Long> LAST_ACTION = new ConcurrentHashMap<>();

    private ClaimActionCooldown() {}

    public static boolean tryAcquire(ServerPlayer player) {
        long now = System.currentTimeMillis();
        UUID playerId = player.getUUID();
        Long lastAction = LAST_ACTION.get(playerId);
        if (lastAction != null && now - lastAction < COOLDOWN_MILLIS) return false;

        LAST_ACTION.put(playerId, now);
        LAST_ACTION.entrySet().removeIf(entry -> now - entry.getValue() >= COOLDOWN_MILLIS);
        return true;
    }
}
