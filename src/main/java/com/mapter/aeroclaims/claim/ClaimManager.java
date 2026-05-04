package com.mapter.aeroclaims.claim;

import com.mapter.aeroclaims.config.AeroClaimsConfig;
import com.mapter.aeroclaims.permission.ClaimPermissionResolver;
import com.mapter.aeroclaims.permission.DefaultPermissionResolver;
import com.mapter.aeroclaims.permission.FtbTeamsPermissionResolver;
import com.mapter.aeroclaims.permission.OpacPermissionResolver;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

public class ClaimManager {

    private static final Logger LOGGER = LogManager.getLogger();

    private static ClaimPermissionResolver PERMISSION_RESOLVER = null;
    private static boolean ftbLoaded  = false;
    private static boolean opacLoaded = false;


    public static void init(boolean ftbLoaded, boolean opacLoaded) {
        ClaimManager.ftbLoaded  = ftbLoaded;
        ClaimManager.opacLoaded = opacLoaded;

        PERMISSION_RESOLVER = null;
    }

    public static ClaimPermissionResolver getPermissionResolver() {
        if (PERMISSION_RESOLVER == null) {
            PERMISSION_RESOLVER = buildResolver();
        }
        return PERMISSION_RESOLVER;
    }

    private static ClaimPermissionResolver buildResolver() {
        AeroClaimsConfig.PartyProvider provider = AeroClaimsConfig.PARTY_PROVIDER.get();

        return switch (provider) {
            case FTB -> {
                if (ftbLoaded) {
                    LOGGER.info("[AeroClaims] Using FTB Teams for party permission checks.");
                    yield new FtbTeamsPermissionResolver();
                } else {
                    LOGGER.warn("[AeroClaims] partyProvider = FTB, but 'ftbteams' is not loaded! " +
                            "Falling back to owner-only resolver.");
                    yield new DefaultPermissionResolver();
                }
            }
            case OPAC -> {
                if (opacLoaded) {
                    LOGGER.info("[AeroClaims] Using Open Parties and Claims for party permission checks.");
                    yield new OpacPermissionResolver();
                } else {
                    LOGGER.warn("[AeroClaims] partyProvider = OPAC, but 'openpartiesandclaims' is not loaded! " +
                            "Falling back to owner-only resolver.");
                    yield new DefaultPermissionResolver();
                }
            }
        };
    }


    public static void addClaim(ServerLevel level, BlockPos pos, UUID owner) {
        ClaimSavedData data = ClaimSavedData.get(level);
        data.getClaims().add(new Claim(pos, owner, new HashSet<>(), false, true, false, false));
        data.setDirty();
    }

    public static void removeClaim(ServerLevel level, BlockPos pos) {
        ClaimSavedData data = ClaimSavedData.get(level);
        data.getClaims().removeIf(c -> c.getCenter().equals(pos));
        data.setDirty();
    }

    public static void deactivateClaim(ServerLevel level, BlockPos center) {
        Claim claim = getClaimByCenter(level, center);
        if (claim == null) return;
        claim.setActive(false);
        ClaimSavedData.get(level).setDirty();
    }

    public static boolean activateClaim(ServerLevel level, BlockPos center) {
        Claim claim = getClaimByCenter(level, center);
        if (claim == null) return false;

        int limit = AeroClaimManager.getBlockLimit(level, center);
        if (limit <= 0) return false;

        if (countShipBlocks(level, center, limit + 1) > limit) return false;

        updateClaimedBlocks(level, claim, center, limit);
        claim.setActive(true);
        ClaimSavedData.get(level).setDirty();
        return true;
    }

    public static boolean refreshClaim(ServerLevel level, BlockPos center) {
        Claim claim = getClaimByCenter(level, center);
        if (claim == null) return false;

        int limit = AeroClaimManager.getBlockLimit(level, center);
        if (limit <= 0) return false;

        if (countShipBlocks(level, center, limit + 1) > limit) return false;

        updateClaimedBlocks(level, claim, center, limit);
        ClaimSavedData.get(level).setDirty();
        return true;
    }

    public static int recountShipBlocks(ServerLevel level, BlockPos center, boolean deactivateOnOverflow) {
        Claim claim = getClaimByCenter(level, center);
        if (claim == null) return -1;

        int limit = AeroClaimManager.getBlockLimit(level, center);
        if (limit <= 0) return -1;

        int blockCount = countShipBlocks(level, center, limit + 1);

        if (blockCount > limit) {
            if (deactivateOnOverflow) {
                claim.setActive(false);
                ClaimSavedData.get(level).setDirty();
            }
            return countShipBlocksExact(level, center);
        }

        updateClaimedBlocks(level, claim, center, limit);
        ClaimSavedData.get(level).setDirty();
        return blockCount;
    }

    public static Claim getClaimAt(ServerLevel level, BlockPos pos) {
        return ClaimSavedData.get(level).getBlockIndex().get(pos);
    }

    public static Claim getClaimByCenter(ServerLevel level, BlockPos center) {
        return findClaim(level, claim -> claim.getCenter().equals(center));
    }

    public static Claim getClaimByShipId(ServerLevel level, String shipId) {
        if (shipId == null) return null;
        return findClaim(level, claim -> shipId.equals(claim.getShipId()));
    }

    public static int countShipBlocks(ServerLevel level, BlockPos start, int hardLimit) {
        if (hardLimit <= 0) return 0;
        return traverse(level, start, hardLimit).count();
    }

    public static int countShipBlocksExact(ServerLevel level, BlockPos start) {
        return traverse(level, start, Integer.MAX_VALUE).count();
    }


    private static void updateClaimedBlocks(ServerLevel level, Claim claim, BlockPos center, int limit) {
        Set<BlockPos> blocks = floodFill(level, center, limit);
        claim.getClaimedBlocks().clear();
        claim.getClaimedBlocks().addAll(blocks);
    }

    private static Set<BlockPos> floodFill(ServerLevel level, BlockPos start, int limit) {
        return traverse(level, start, limit + 1).visitedBlocks();
    }

    private static Claim findClaim(ServerLevel level, Predicate<Claim> predicate) {
        for (Claim claim : ClaimSavedData.get(level).getClaims()) {
            if (predicate.test(claim)) return claim;
        }
        return null;
    }

    private record TraversalResult(int count, Set<BlockPos> visitedBlocks) {}

    private static TraversalResult traverse(ServerLevel level, BlockPos start, int limit) {
        Set<Long> visited = new HashSet<>();
        ArrayDeque<Long> queue = new ArrayDeque<>();

        long startLong = start.asLong();
        visited.add(startLong);
        queue.add(startLong);

        int count = 0;
        while (!queue.isEmpty()) {
            long current = queue.poll();
            BlockPos currentPos = BlockPos.of(current);

            if (!isSolid(level, currentPos)) continue;
            if (++count > limit) break;

            for (Direction dir : Direction.values()) {
                long neighborLong = BlockPos.offset(current, dir);
                if (visited.add(neighborLong)) {
                    BlockPos neighborPos = BlockPos.of(neighborLong);
                    if (isSolid(level, neighborPos)) {
                        queue.add(neighborLong);
                    }
                }
            }
        }

        Set<BlockPos> visitedBlocks = new HashSet<>(visited.size());
        for (long packed : visited) {
            visitedBlocks.add(BlockPos.of(packed));
        }
        return new TraversalResult(count, visitedBlocks);
    }

    private static boolean isSolid(ServerLevel level, BlockPos pos) {
        var state = level.getBlockState(pos);
        return !state.isAir() && !state.is(Blocks.WATER) && !state.is(Blocks.SNOW);
    }
}
