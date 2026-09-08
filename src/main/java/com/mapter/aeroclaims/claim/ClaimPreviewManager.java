package com.mapter.aeroclaims.claim;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.server.level.ServerLevel;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Provides short-lived, read-only ship size previews for the claim menu. */
public final class ClaimPreviewManager {

    private static final long PREVIEW_DEBOUNCE_MS = 1_500L;
    private static final Map<Key, Entry> PREVIEWS = new ConcurrentHashMap<>();

    private ClaimPreviewManager() {
    }

    public static int getShipBlockCount(ServerLevel level, BlockPos center) {
        Key key = new Key(level.dimension(), center.asLong());
        long now = System.currentTimeMillis();
        Entry cached = PREVIEWS.get(key);
        if (cached != null && now - cached.createdAt < PREVIEW_DEBOUNCE_MS) {
            return cached.blockCount;
        }

        int blockCount = ClaimManager.countShipBlocksExact(level, center);
        PREVIEWS.put(key, new Entry(now, blockCount));
        return blockCount;
    }

    public static void invalidate(ServerLevel level, BlockPos center) {
        PREVIEWS.remove(new Key(level.dimension(), center.asLong()));
    }

    private record Key(ResourceKey<Level> dimension, long center) {
    }

    private record Entry(long createdAt, int blockCount) {
    }
}
