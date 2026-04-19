package com.mapter.aeroclaims.ship;

import dev.ryanhcode.sable.api.event.SableSubLevelContainerReadyEvent;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelObserver;
import dev.ryanhcode.sable.platform.SableEventPlatform;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import net.minecraft.server.level.ServerLevel;

public class SableShipEventHandler {

    public static void register() {

        SableEventPlatform.INSTANCE.onSubLevelContainerReady(
                SableShipEventHandler::onContainerReady
        );
    }

    private static void onContainerReady(
            net.minecraft.world.level.Level level,
            SubLevelContainer container) {

        if (!(level instanceof ServerLevel)) return;

        container.addObserver(new SubLevelObserver() {
            @Override
            public void onSubLevelAdded(SubLevel subLevel) {
                String shipId = subLevel.getUniqueId().toString();
                String shipName = subLevel.getName() != null ? subLevel.getName() : "ship";

                if (UnregisteredShipsManager.contains(shipId)) return;
                if (RegisteredShipsManager.getRegisteredName(shipId) != null) return;

                UnregisteredShipsManager.addShip(shipId, shipName);
            }

            @Override
            public void onSubLevelRemoved(SubLevel subLevel, SubLevelRemovalReason reason) {
                String shipId = subLevel.getUniqueId().toString();
                UnregisteredShipsManager.removeShip(shipId);

                var reg = RegisteredShipsManager.getRegistration(shipId);
                RegisteredShipsManager.unregisterShip(shipId);

                if (reg != null && reg.ownerUuid != null) {
                    try {
                        var ownerId = java.util.UUID.fromString(reg.ownerUuid);
                        var claim = com.mapter.aeroclaims.claim.ClaimManager
                                .getClaimByShipId((ServerLevel) level, shipId);
                        if (claim != null && claim.isActive()) {
                            com.mapter.aeroclaims.claim.VsClaimManager
                                    .releaseShipClaimSlot((ServerLevel) level, ownerId);
                        }
                        if (claim != null) {
                            com.mapter.aeroclaims.claim.ClaimManager
                                    .removeClaim((ServerLevel) level, claim.getCenter());
                        }
                    } catch (Exception ignored) {}
                }
            }
        });
    }
}