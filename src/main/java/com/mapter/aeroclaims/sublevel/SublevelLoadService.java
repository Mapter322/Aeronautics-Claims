package com.mapter.aeroclaims.sublevel;

import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3dc;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.storage.HoldingSubLevel;
import dev.ryanhcode.sable.sublevel.storage.holding.GlobalSavedSubLevelPointer;
import dev.ryanhcode.sable.sublevel.storage.holding.SavedSubLevelPointer;
import dev.ryanhcode.sable.sublevel.storage.holding.SubLevelHoldingChunk;
import dev.ryanhcode.sable.sublevel.storage.holding.SubLevelHoldingChunkMap;
import dev.ryanhcode.sable.sublevel.storage.serialization.SubLevelData;
import dev.ryanhcode.sable.sublevel.storage.serialization.SubLevelStorage;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

public class SublevelLoadService {

    private static final Logger LOGGER = LoggerFactory.getLogger(SublevelLoadService.class);

    public static final TicketType<ChunkPos> TEMP_LOAD_TICKET = TicketType.create(
            "aeroclaims_temp_load",
            Comparator.comparingLong(ChunkPos::toLong)
    );

    public static final int MAX_STORAGE_SEARCH_RADIUS_CHUNKS = 20;
    public static final int MAX_BOUNDING_BOX_RADIUS_CHUNKS = 32;

    public static class SublevelLoadSession implements AutoCloseable {
        private final ServerLevel level;
        private final ServerSubLevel subLevel;
        private final List<ChunkPos> tickets;
        private final boolean wasAlreadyLoaded;
        private boolean closed = false;

        public SublevelLoadSession(ServerLevel level,
                                   @Nullable ServerSubLevel subLevel,
                                   List<ChunkPos> tickets,
                                   boolean wasAlreadyLoaded) {
            this.level = level;
            this.subLevel = subLevel;
            this.tickets = tickets != null ? tickets : Collections.emptyList();
            this.wasAlreadyLoaded = wasAlreadyLoaded;
        }

        @Nullable
        public ServerSubLevel getSubLevel() {
            return subLevel;
        }

        public boolean isLoaded() {
            return subLevel != null;
        }

        public boolean wasAlreadyLoaded() {
            return wasAlreadyLoaded;
        }

        public List<ChunkPos> getTickets() {
            return Collections.unmodifiableList(tickets);
        }

        @Override
        public void close() {
            if (closed) return;
            closed = true;
            if (level != null && !tickets.isEmpty()) {
                for (ChunkPos cp : tickets) {
                    level.getChunkSource().removeRegionTicket(TEMP_LOAD_TICKET, cp, 2, cp);
                }
            }
        }
    }

    private static SublevelLoadSession emptySession(ServerLevel level) {
        return new SublevelLoadSession(level, null, Collections.emptyList(), false);
    }

    public static boolean isSublevelLoaded(ServerLevel level, UUID uuid) {
        if (level == null || uuid == null) return false;
        SubLevelContainer container = SubLevelContainer.getContainer(level);
        return container instanceof ServerSubLevelContainer sc && sc.getSubLevel(uuid) != null;
    }

    public static boolean isSublevelLoaded(ServerLevel level, String shipId) {
        if (shipId == null) return false;
        try {
            return isSublevelLoaded(level, UUID.fromString(shipId));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    public static SublevelLoadSession loadTemporarily(ServerLevel level, String shipId) {
        return loadTemporarily(level, shipId, (BlockPos) null);
    }

    public static SublevelLoadSession loadTemporarily(ServerLevel level, String shipId, @Nullable BlockPos hintPos) {
        if (shipId == null) return emptySession(level);
        UUID uuid;
        try {
            uuid = UUID.fromString(shipId);
        } catch (IllegalArgumentException e) {
            return emptySession(level);
        }

        Integer chunkX = null;
        Integer chunkZ = null;

        RegisteredSublevelManager.ShipRegistration reg =
                RegisteredSublevelManager.getRegistration(shipId);
        if (reg != null && reg.worldX != null && reg.worldZ != null) {
            chunkX = SectionPos.blockToSectionCoord(reg.worldX);
            chunkZ = SectionPos.blockToSectionCoord(reg.worldZ);
        } else if (hintPos != null) {
            chunkX = SectionPos.blockToSectionCoord(hintPos.getX());
            chunkZ = SectionPos.blockToSectionCoord(hintPos.getZ());
        }

        if (chunkX == null || chunkZ == null) {
            return emptySession(level);
        }

        return loadTemporarily(level, uuid, chunkX, chunkZ);
    }

    public static SublevelLoadSession loadTemporarily(ServerLevel level, UUID uuid) {
        return loadTemporarily(level, uuid != null ? uuid.toString() : null);
    }

    public static SublevelLoadSession loadTemporarily(ServerLevel level, UUID uuid, int centerChunkX, int centerChunkZ) {
        if (level == null || uuid == null) {
            return emptySession(level);
        }

        SubLevelContainer container = SubLevelContainer.getContainer(level);
        if (!(container instanceof ServerSubLevelContainer serverContainer)) {
            return emptySession(level);
        }

        // Already loaded in memory
        if (serverContainer.getSubLevel(uuid) instanceof ServerSubLevel sl) {
            return new SublevelLoadSession(level, sl, Collections.emptyList(), true);
        }

        List<ChunkPos> tickets = new ArrayList<>();
        Set<ChunkPos> addedTickets = new HashSet<>();

        BiConsumer<Integer, Integer> loadChunk = (cx, cz) -> {
            ChunkPos cp = new ChunkPos(cx, cz);
            if (addedTickets.add(cp)) {
                level.getChunkSource().addRegionTicket(TEMP_LOAD_TICKET, cp, 2, cp);
                level.getChunkSource().getChunk(cp.x, cp.z, ChunkStatus.FULL, true);
                serverContainer.getHoldingChunkMap().updateChunkStatus(cp, true);
                tickets.add(cp);
            }
        };

        try {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    loadChunk.accept(centerChunkX + dx, centerChunkZ + dz);
                }
            }

            serverContainer.getHoldingChunkMap().processChanges();

            if (serverContainer.getSubLevel(uuid) instanceof ServerSubLevel sl) {
                return new SublevelLoadSession(level, sl, tickets, false);
            }

            // Find holding chunk in storage to resolve actual bounds for large sub-levels
            HoldingSubLevel holding = findHoldingSubLevel(serverContainer, uuid, centerChunkX, centerChunkZ, loadChunk);

            if (holding != null) {
                BoundingBox3dc bounds = holding.data().bounds();
                int minChunkX = Mth.floor(bounds.minX() - 1.0) >> 4;
                int maxChunkX = Mth.floor(bounds.maxX() + 1.0) >> 4;
                int minChunkZ = Mth.floor(bounds.minZ() - 1.0) >> 4;
                int maxChunkZ = Mth.floor(bounds.maxZ() + 1.0) >> 4;

                for (UUID depId : holding.data().dependencies()) {
                    HoldingSubLevel depHolding = serverContainer.getHoldingChunkMap().getHoldingSubLevel(depId);
                    if (depHolding != null) {
                        BoundingBox3dc depBounds = depHolding.data().bounds();
                        minChunkX = Math.min(minChunkX, Mth.floor(depBounds.minX() - 1.0) >> 4);
                        maxChunkX = Math.max(maxChunkX, Mth.floor(depBounds.maxX() + 1.0) >> 4);
                        minChunkZ = Math.min(minChunkZ, Mth.floor(depBounds.minZ() - 1.0) >> 4);
                        maxChunkZ = Math.max(maxChunkZ, Mth.floor(depBounds.maxZ() + 1.0) >> 4);
                    }
                }

                int clampedMinX = Math.max(centerChunkX - MAX_BOUNDING_BOX_RADIUS_CHUNKS, minChunkX);
                int clampedMaxX = Math.min(centerChunkX + MAX_BOUNDING_BOX_RADIUS_CHUNKS, maxChunkX);
                int clampedMinZ = Math.max(centerChunkZ - MAX_BOUNDING_BOX_RADIUS_CHUNKS, minChunkZ);
                int clampedMaxZ = Math.min(centerChunkZ + MAX_BOUNDING_BOX_RADIUS_CHUNKS, maxChunkZ);

                for (int cx = clampedMinX; cx <= clampedMaxX; cx++) {
                    for (int cz = clampedMinZ; cz <= clampedMaxZ; cz++) {
                        loadChunk.accept(cx, cz);
                    }
                }

                serverContainer.getHoldingChunkMap().processChanges();

                // Explicit fallback if not auto-woken
                if (serverContainer.getSubLevel(uuid) == null && holding.pointer() != null) {
                    serverContainer.getHoldingChunkMap().snatchAndLoad(holding.pointer(), uuid);
                }
            }

            ServerSubLevel finalSubLevel = (ServerSubLevel) serverContainer.getSubLevel(uuid);
            return new SublevelLoadSession(level, finalSubLevel, tickets, false);

        } catch (Exception e) {
            LOGGER.error("Failed to temporarily load sublevel {}", uuid, e);
            for (ChunkPos cp : tickets) {
                level.getChunkSource().removeRegionTicket(TEMP_LOAD_TICKET, cp, 2, cp);
            }
            return emptySession(level);
        }
    }

    @Nullable
    public static HoldingSubLevel findHoldingSubLevel(
            ServerSubLevelContainer serverContainer,
            UUID uuid,
            int centerChunkX,
            int centerChunkZ,
            BiConsumer<Integer, Integer> loadChunkNotifier) {

        SubLevelHoldingChunkMap holdingChunkMap = serverContainer.getHoldingChunkMap();
        HoldingSubLevel holding = holdingChunkMap.getHoldingSubLevel(uuid);
        if (holding != null) return holding;

        SubLevelStorage storage = holdingChunkMap.getStorage();
        if (storage == null) return null;

        for (int r = 0; r <= MAX_STORAGE_SEARCH_RADIUS_CHUNKS; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.abs(dx) != r && Math.abs(dz) != r) continue;
                    int cx = centerChunkX + dx;
                    int cz = centerChunkZ + dz;
                    ChunkPos cp = new ChunkPos(cx, cz);
                    SubLevelHoldingChunk chunk = storage.attemptLoadHoldingChunk(cp);
                    if (chunk != null) {
                        for (SavedSubLevelPointer ptr : chunk.getSubLevelPointers()) {
                            SubLevelData data = storage.attemptLoadSubLevel(cp, ptr);
                            if (data != null && uuid.equals(data.uuid())) {
                                GlobalSavedSubLevelPointer globalPtr =
                                        new GlobalSavedSubLevelPointer(cp, ptr.storageIndex(), ptr.subLevelIndex());
                                HoldingSubLevel found = new HoldingSubLevel(data, globalPtr);
                                chunk.acceptHoldingSubLevel(found);
                                loadChunkNotifier.accept(cx, cz);
                                return found;
                            }
                        }
                    }
                }
            }
        }

        return null;
    }

    public static <T> Optional<T> withLoadedSublevel(ServerLevel level, UUID uuid, Function<ServerSubLevel, T> action) {
        try (SublevelLoadSession session = loadTemporarily(level, uuid)) {
            if (session.isLoaded()) {
                return Optional.ofNullable(action.apply(session.getSubLevel()));
            }
            return Optional.empty();
        }
    }

    public static <T> Optional<T> withLoadedSublevel(ServerLevel level, String shipId, Function<ServerSubLevel, T> action) {
        try (SublevelLoadSession session = loadTemporarily(level, shipId)) {
            if (session.isLoaded()) {
                return Optional.ofNullable(action.apply(session.getSubLevel()));
            }
            return Optional.empty();
        }
    }

    public static boolean runWithLoadedSublevel(ServerLevel level, UUID uuid, Consumer<ServerSubLevel> action) {
        try (SublevelLoadSession session = loadTemporarily(level, uuid)) {
            if (session.isLoaded()) {
                action.accept(session.getSubLevel());
                return true;
            }
            return false;
        }
    }

    public static boolean runWithLoadedSublevel(ServerLevel level, String shipId, Consumer<ServerSubLevel> action) {
        try (SublevelLoadSession session = loadTemporarily(level, shipId)) {
            if (session.isLoaded()) {
                action.accept(session.getSubLevel());
                return true;
            }
            return false;
        }
    }
}
