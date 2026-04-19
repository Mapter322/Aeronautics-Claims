package com.mapter.aeroclaims.ship;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.reflect.Method;

public class VSShipUtils {

    private static final Logger LOGGER = LogManager.getLogger("vsclaims/VSShipUtils");

    private static Object getShipWorld(ServerLevel level) throws Exception {
        Method getWorld = level.getClass().getMethod("getShipObjectWorld");
        return getWorld.invoke(level);
    }

    public static Object getShipAt(ServerLevel level, BlockPos pos) {
        try {
            Object shipWorld = getShipWorld(level);
            if (shipWorld == null) return null;

            String dimId = level.dimension().location().toString();
            Method isInShipyard = shipWorld.getClass()
                    .getMethod("isBlockInShipyard", int.class, int.class, int.class, String.class);
            boolean inShipyard = (boolean) isInShipyard.invoke(
                    shipWorld, pos.getX(), pos.getY(), pos.getZ(), dimId);

            if (!inShipyard) return null;

            // Try to get the actual ship object
            Object ship = findShipObject(shipWorld, pos);
            return ship != null ? ship : Boolean.TRUE;

        } catch (Exception e) {
            LOGGER.warn("VSShipUtils.getShipAt error: {}", e.toString());
        }
        return null;
    }


    public static Object getShipObjectAt(ServerLevel level, BlockPos pos) {
        try {
            Object shipWorld = getShipWorld(level);
            if (shipWorld == null) return null;
            return findShipObject(shipWorld, pos);
        } catch (Exception e) {
            LOGGER.warn("VSShipUtils.getShipObjectAt error: {}", e.toString());
        }
        return null;
    }

    private static Object findShipObject(Object shipWorld, BlockPos pos) {
        net.minecraft.world.level.ChunkPos cp = new net.minecraft.world.level.ChunkPos(pos);
        long packedChunk = net.minecraft.world.level.ChunkPos.asLong(cp.x, cp.z);

        try {
            Iterable<?> ships = (Iterable<?>) shipWorld.getClass().getMethod("getAllShips").invoke(shipWorld);
            for (Object ship : ships) {
                try {
                    Object chunkClaim = ship.getClass().getMethod("getChunkClaim").invoke(ship);
                    for (Method m : chunkClaim.getClass().getMethods()) {
                        String name = m.getName();
                        if (!name.equals("contains") && !name.equals("containsChunk")) continue;
                        Class<?>[] pt = m.getParameterTypes();
                        boolean match = false;
                        if (pt.length == 2 && pt[0] == int.class) {
                            match = (boolean) m.invoke(chunkClaim, cp.x, cp.z);
                        } else if (pt.length == 1 && pt[0] == long.class) {
                            match = (boolean) m.invoke(chunkClaim, packedChunk);
                        }
                        if (match) return ship;
                    }
                } catch (Exception ignored) {}
            }
        } catch (Exception e) {
            LOGGER.warn("findShipObject error: {}", e.toString());
        }

        return null;
    }

    public static boolean isOnShip(ServerLevel level, BlockPos pos) {
        return getShipAt(level, pos) != null;
    }

    public static String getShipId(Object ship) {
        if (ship == null || ship instanceof Boolean) return null;
        try { return ship.getClass().getMethod("getId").invoke(ship).toString(); }
        catch (ReflectiveOperationException ignored) { return null; }
    }

    public static String getShipSlug(Object ship) {
        if (ship == null || ship instanceof Boolean) return null;
        try {
            Object slug = ship.getClass().getMethod("getSlug").invoke(ship);
            return slug == null ? null : slug.toString();
        } catch (ReflectiveOperationException ignored) { return null; }
    }

    public static boolean deleteShipById(ServerLevel level, String shipId) {
        if (level == null || shipId == null) return false;

        try {
            Object shipWorld = getShipWorld(level);
            if (shipWorld == null) return false;

            Object shipObj = null;
            try {
                Iterable<?> ships = (Iterable<?>) shipWorld.getClass().getMethod("getAllShips").invoke(shipWorld);
                for (Object ship : ships) {
                    String id = getShipId(ship);
                    if (shipId.equals(id)) {
                        shipObj = ship;
                        break;
                    }
                }
            } catch (Exception ignored) {}

            Long shipIdLong = null;
            try { shipIdLong = Long.parseLong(shipId); } catch (NumberFormatException ignored) {}

            String[] methodNames = new String[]{"deleteShip", "removeShip", "destroyShip", "killShip"};

            for (String name : methodNames) {
                for (Method m : shipWorld.getClass().getMethods()) {
                    if (!m.getName().equals(name)) continue;
                    Class<?>[] pt = m.getParameterTypes();
                    try {
                        if (pt.length == 1 && pt[0] == String.class) {
                            m.invoke(shipWorld, shipId);
                            return true;
                        }
                        if (shipIdLong != null && pt.length == 1 && (pt[0] == long.class || pt[0] == Long.class)) {
                            m.invoke(shipWorld, shipIdLong);
                            return true;
                        }
                        if (shipObj != null && pt.length == 1 && pt[0].isInstance(shipObj)) {
                            m.invoke(shipWorld, shipObj);
                            return true;
                        }
                    } catch (Exception ignored) {}
                }
            }

            if (shipObj != null) {
                for (String name : methodNames) {
                    for (Method m : shipObj.getClass().getMethods()) {
                        if (!m.getName().equals(name)) continue;
                        if (m.getParameterCount() != 0) continue;
                        try {
                            m.invoke(shipObj);
                            return true;
                        } catch (Exception ignored) {}
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.warn("VSShipUtils.deleteShipById error: {}", e.toString());
        }

        return false;
    }
}