package com.mapter.aeroclaims.sublevel;

import com.mapter.aeroclaims.Aeroclaims;
import com.mapter.aeroclaims.claim.Claim;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;


@EventBusSubscriber(modid = Aeroclaims.MODID)
public class SublevelWorldScanner {

    private static final Logger LOGGER = LogManager.getLogger("aeroclaims/ShipWorldScanner");

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        scanAllLevels(event.getServer());
    }

    public static void scanAllLevels(MinecraftServer server) {
        for (ServerLevel level : server.getAllLevels()) {
            SubLevelContainer container = SubLevelContainer.getContainer(level);
            if (!(container instanceof ServerSubLevelContainer serverContainer)) continue;

            List<ServerSubLevel> subLevels = serverContainer.getAllSubLevels();
            if (subLevels == null || subLevels.isEmpty()) continue;

            for (ServerSubLevel subLevel : subLevels) {
                processSubLevel(level, serverContainer, subLevel);
            }
        }
    }

    private static void processSubLevel(ServerLevel level,
                                        ServerSubLevelContainer container,
                                        ServerSubLevel subLevel) {
        String shipId = subLevel.getUniqueId().toString();
        String shipName = subLevel.getName() != null ? subLevel.getName() : "ship";

        if (RegisteredSublevelManager.getRegisteredName(shipId) != null
                || UnregisteredSublevelManager.contains(shipId)) {
            return;
        }

        Claim claim = SableSubLevelEventHandler.findClaimOnSubLevel(level, container, subLevel);

        if (claim != null) {
            java.util.UUID ownerUuid = claim.getOwner();
            String ownerName = null;
            if (claim.getShipId() != null) {
                RegisteredSublevelManager.ShipRegistration existingReg =
                        RegisteredSublevelManager.getRegistration(claim.getShipId());
                if (existingReg != null) ownerName = existingReg.owner;
            }
            RegisteredSublevelManager.registerShip(shipId, shipName, ownerUuid, ownerName);
            LOGGER.info("[Scanner] Discovered claimed ship: id={} name={} owner={}", shipId, shipName, ownerUuid);
        } else {
            UnregisteredSublevelManager.addShip(shipId, shipName);
            LOGGER.info("[Scanner] Discovered unclaimed ship: id={} name={}", shipId, shipName);
        }
    }
}
