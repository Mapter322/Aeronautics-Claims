package com.mapter.aeroclaims.sublevel;

import com.mapter.aeroclaims.Aeroclaims;
import com.mapter.aeroclaims.claim.Claim;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.SubLevelAccess;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

@EventBusSubscriber(modid = Aeroclaims.MODID)
public class SublevelWorldScanner {

    private static final Logger LOGGER = LoggerFactory.getLogger("aeroclaims/ShipWorldScanner");

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
                processSubLevel(level, subLevel);
            }
        }
    }

    private static void processSubLevel(ServerLevel level, SubLevelAccess subLevel) {
        String shipId   = subLevel.getUniqueId().toString();
        String shipName = SableShipUtils.getShipName(subLevel);

        if (RegisteredSublevelManager.getRegisteredName(shipId) != null
                || UnregisteredSublevelManager.contains(shipId)) {
            return;
        }

        Claim claim = SableSubLevelEventHandler.findClaimOnSubLevel(level, subLevel);

        if (claim != null) {
            String ownerName = null;
            if (claim.getShipId() != null) {
                RegisteredSublevelManager.ShipRegistration existing =
                        RegisteredSublevelManager.getRegistration(claim.getShipId());
                if (existing != null) ownerName = existing.owner;
            }
            RegisteredSublevelManager.registerShip(shipId, shipName, claim.getOwner(), ownerName);
            LOGGER.info("[Scanner] Discovered claimed ship: id={} name={} owner={}", shipId, shipName, claim.getOwner());
        } else {
            UnregisteredSublevelManager.addShip(shipId, shipName);
            LOGGER.info("[Scanner] Discovered unclaimed ship: id={} name={}", shipId, shipName);
        }
    }
}
