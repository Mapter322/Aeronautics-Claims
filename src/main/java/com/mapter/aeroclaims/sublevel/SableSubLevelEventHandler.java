package com.mapter.aeroclaims.sublevel;

import com.mapter.aeroclaims.claim.Claim;
import com.mapter.aeroclaims.claim.ClaimManager;
import com.mapter.aeroclaims.claim.ClaimSavedData;
import com.mapter.aeroclaims.claim.AeroClaimManager;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelObserver;
import dev.ryanhcode.sable.platform.SableEventPlatform;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.plot.LevelPlot;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import net.minecraft.server.level.ServerLevel;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class SableSubLevelEventHandler {

    private static final Logger LOGGER = LogManager.getLogger("aeroclaims/SableShipEventHandler");

    public static void register() {
        SableEventPlatform.INSTANCE.onSubLevelContainerReady(
                SableSubLevelEventHandler::onContainerReady
        );
    }

    private static void onContainerReady(
            net.minecraft.world.level.Level level,
            SubLevelContainer container) {

        if (!(level instanceof ServerLevel serverLevel)) return;

        container.addObserver(new SubLevelObserver() {

            @Override
            public void onSubLevelAdded(SubLevel subLevel) {
                String shipId = subLevel.getUniqueId().toString();
                String shipName = subLevel.getName() != null ? subLevel.getName() : "ship";

                if (RegisteredSublevelManager.getRegisteredName(shipId) != null
                        || UnregisteredSublevelManager.contains(shipId)) {
                    return;
                }

                Claim matchedClaim = findClaimOnSubLevel(serverLevel, container, subLevel);

                if (matchedClaim != null) {
                    String oldId = matchedClaim.getShipId();
                    if (!shipId.equals(oldId)) {
                        LOGGER.info("Ship reassembled: claim at {} updated shipId {} -> {}",
                                matchedClaim.getCenter(), oldId, shipId);
                        if (oldId != null) {
                            RegisteredSublevelManager.ShipRegistration oldReg =
                                    RegisteredSublevelManager.getRegistration(oldId);
                            if (oldReg != null) {
                                RegisteredSublevelManager.unregisterShip(oldId);
                                RegisteredSublevelManager.registerShip(shipId, oldReg.name,
                                        oldReg.ownerUuid != null
                                                ? java.util.UUID.fromString(oldReg.ownerUuid)
                                                : null,
                                        oldReg.owner);
                            }
                        }
                        matchedClaim.setShipId(shipId);
                        ClaimSavedData.get(serverLevel).setDirty();
                    }
                    if (RegisteredSublevelManager.getRegisteredName(shipId) == null) {
                        RegisteredSublevelManager.registerShip(shipId, shipName,
                                matchedClaim.getOwner(), null);
                        LOGGER.info("Claimed ship loaded: id={} name={}", shipId, shipName);
                    }
                    return;
                }

                UnregisteredSublevelManager.addShip(shipId, shipName);
            }

            @Override
            public void onSubLevelRemoved(SubLevel subLevel, SubLevelRemovalReason reason) {
                String shipId = subLevel.getUniqueId().toString();

                if (reason != SubLevelRemovalReason.REMOVED) {
                    LOGGER.debug("Ship {} temporarily unloaded (reason={}), keeping claim", shipId, reason);
                    UnregisteredSublevelManager.removeShip(shipId);
                    return;
                }

                UnregisteredSublevelManager.removeShip(shipId);

                RegisteredSublevelManager.ShipRegistration reg =
                        RegisteredSublevelManager.getRegistration(shipId);
                RegisteredSublevelManager.unregisterShip(shipId);

                if (reg != null && reg.ownerUuid != null) {
                    try {
                        java.util.UUID ownerId = java.util.UUID.fromString(reg.ownerUuid);
                        Claim claim = ClaimManager.getClaimByShipId(serverLevel, shipId);
                        if (claim != null) {

                            AeroClaimManager.releaseAllClaimsForBlock(serverLevel, ownerId, claim.getCenter());
                            ClaimManager.removeClaim(serverLevel, claim.getCenter());
                        }
                    } catch (Exception e) {
                        LOGGER.error("Error cleaning up claim for ship {}: {}", shipId, e.getMessage());
                    }
                }
            }
        });
    }

    static Claim findClaimOnSubLevel(ServerLevel level, SubLevelContainer container, SubLevel subLevel) {
        String shipId = subLevel.getUniqueId().toString();

        for (Claim claim : ClaimSavedData.get(level).getClaims()) {
            if (shipId.equals(claim.getShipId())) {
                return claim;
            }

            net.minecraft.core.BlockPos center = claim.getCenter();
            if (container.inBounds(center)) {
                LevelPlot plot = container.getPlot(center.getX() >> 4, center.getZ() >> 4);
                if (plot != null && plot.getSubLevel() == subLevel) {
                    return claim;
                }
            }
        }
        return null;
    }
}
