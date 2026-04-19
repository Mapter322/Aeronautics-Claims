package com.mapter.aeroclaims.ship;

import com.mapter.aeroclaims.Aeroclaims;
import com.mapter.aeroclaims.claim.Claim;
import com.mapter.aeroclaims.claim.ClaimManager;
import com.mapter.aeroclaims.claim.VsClaimManager;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.LevelEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@EventBusSubscriber(modid = Aeroclaims.MODID)
public class VSShipEventHandler {

    private static final Logger LOGGER = LogManager.getLogger("vsclaims/VSShipEventHandler");

    // Known ship IDs to avoid adding the same ship twice
    private static final Set<String> knownShipIds =
            java.util.Collections.synchronizedSet(new HashSet<>());

    private static int tickCounter = 0;
    private static final int CHECK_INTERVAL = 200; // every 10 seconds (20 TPS)


    // Reset when the world loads

    @SubscribeEvent
    public static void onLevelLoad(LevelEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        if (!level.dimension().equals(net.minecraft.world.level.Level.OVERWORLD)) return;
        knownShipIds.clear();
        cachedGetAllShips = null;

        // Pre-populate knownShipIds with all already existing ships
        // so the first tick does not add all of them as "new"
        try {
            Object shipWorld = level.getClass().getMethod("getShipObjectWorld").invoke(level);
            if (shipWorld != null) {
                cachedGetAllShips = shipWorld.getClass().getMethod("getAllShips");
                Iterable<?> ships = (Iterable<?>) cachedGetAllShips.invoke(shipWorld);
                int count = 0;
                for (Object ship : ships) {
                    try {
                        knownShipIds.add(ship.getClass().getMethod("getId").invoke(ship).toString());
                        count++;
                    } catch (Exception ignored) {}
                }
                LOGGER.debug("VSShipEventHandler: preloaded {} existing ships", count);
            }
        } catch (Exception e) {
            LOGGER.warn("VSShipEventHandler: preload error: {}", e.toString());
        }
    }


    // Periodic check for new ships


    public static void onServerTick(net.minecraft.server.MinecraftServer server) {
        if (++tickCounter < CHECK_INTERVAL) return;
        tickCounter = 0;
        checkForNewShips(server.overworld());
    }


    // Main logic for detecting new ships

    private static java.lang.reflect.Method cachedGetAllShips = null;

    public static void checkForNewShips(ServerLevel level) {
        try {
            Object shipWorld = level.getClass().getMethod("getShipObjectWorld").invoke(level);
            if (shipWorld == null) return;

            if (cachedGetAllShips == null) {
                cachedGetAllShips = shipWorld.getClass().getMethod("getAllShips");
            }

            Iterable<?> ships = (Iterable<?>) cachedGetAllShips.invoke(shipWorld);
            Set<String> currentShipIds = new HashSet<>();

            for (Object ship : ships) {
                try {
                    String shipId = ship.getClass().getMethod("getId").invoke(ship).toString();
                    currentShipIds.add(shipId);
                    if (knownShipIds.contains(shipId)) continue;
                    knownShipIds.add(shipId);

                    // Skip ships that are already tracked as unregistered
                    if (UnregisteredShipsManager.contains(shipId)) continue;
                    // Skip ships that are already registered
                    if (RegisteredShipsManager.getRegisteredName(shipId) != null) continue;

                    String slug = getSlug(ship);

                    UnregisteredShipsManager.addShip(shipId, slug);
                    // ship logged by UnregisteredShipsManager.addShip

                } catch (Exception e) {
                    LOGGER.warn("Failed to process ship: {}", e.toString());
                }
            }

            for (String shipId : UnregisteredShipsManager.getShipIds()) {
                if (currentShipIds.contains(shipId)) continue;
                UnregisteredShipsManager.removeShip(shipId);
                knownShipIds.remove(shipId);
                LOGGER.debug("Ship disappeared from world, removed from unregistered: {}", shipId);
            }


            // Clean up registered ships deleted via VS
            java.util.List<String> registeredIds = new java.util.ArrayList<>(RegisteredShipsManager.getAllRegisteredShips().keySet());
            for (String registeredShipId : registeredIds) {
                if (currentShipIds.contains(registeredShipId)) continue;
                RegisteredShipsManager.ShipRegistration reg = RegisteredShipsManager.getRegistration(registeredShipId);
                RegisteredShipsManager.unregisterShip(registeredShipId);
                knownShipIds.remove(registeredShipId);
                if (reg != null && reg.ownerUuid != null) {
                    try {
                        UUID ownerId = UUID.fromString(reg.ownerUuid);
                        Claim claim = ClaimManager.getClaimByShipId(level, registeredShipId);
                        if (claim != null && claim.isActive()) {
                            VsClaimManager.releaseShipClaimSlot(level, ownerId);
                        }
                        if (claim != null) {
                            ClaimManager.removeClaim(level, claim.getCenter());
                        }
                    } catch (Exception ex) {
                        LOGGER.warn("cleanupRegistered: {}", ex.toString());
                    }
                }
                LOGGER.info("Registered ship deleted via VS, cleaned up: {}", registeredShipId);
            }
        } catch (Exception e) {
            LOGGER.warn("checkForNewShips: {}", e.toString());
        }
    }


    private static String getSlug(Object ship) {
        try {
            Object slug = ship.getClass().getMethod("getSlug").invoke(ship);
            return slug != null ? slug.toString() : "ship";
        } catch (Exception ignored) {
            return "ship";
        }
    }

}