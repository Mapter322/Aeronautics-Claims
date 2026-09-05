package com.mapter.aeroclaims.compat.cbc;

import com.mapter.aeroclaims.claim.Claim;
import com.mapter.aeroclaims.claim.ClaimManager;
import com.mapter.aeroclaims.config.AeroClaimsConfig;
import dev.ryanhcode.sable.companion.SableCompanion;
import dev.ryanhcode.sable.companion.SubLevelAccess;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rbasamoyai.createbigcannons.events.ProjectileDamageEvent;

/** Integrates CBC terrain damage with AeroClaims ship claims. */
public final class CBCProtectionEvents {

    private static final Logger LOGGER = LoggerFactory.getLogger("aeroclaims/CBCProtectionEvents");

    public static void register() {
        NeoForge.EVENT_BUS.register(CBCProtectionEvents.class);
        LOGGER.info("[AeroClaims] CBC projectile protection enabled.");
    }

    @SubscribeEvent
    public static void onCBCProjectileDamage(ProjectileDamageEvent event) {
        if (!AeroClaimsConfig.EXPLOSION_PROTECTION.get()) return;
        if (!(event.getLevel() instanceof ServerLevel level)) return;

        if (isProtectedPosition(level, event.getPos())) event.setCanceled(true);
    }

    public static boolean isProtectedPosition(Level level, BlockPos pos) {
        if (!(level instanceof ServerLevel serverLevel)) return false;

        Claim claim = ClaimManager.getClaimAtWithMargin(serverLevel, pos);
        if (claim != null && claim.isActive()) return true;

        SubLevelAccess ship = findShipAtWorldPos(serverLevel, pos);
        if (ship == null) return false;

        Claim shipClaim = ClaimManager.getClaimByShipId(serverLevel, ship.getUniqueId().toString());
        return shipClaim != null && shipClaim.isActive();
    }

    /** Finds a ship intersecting a world-space block position. */
    private static SubLevelAccess findShipAtWorldPos(ServerLevel level, BlockPos worldPos) {
        BoundingBox3d box = new BoundingBox3d(
                worldPos.getX(), worldPos.getY(), worldPos.getZ(),
                worldPos.getX() + 1.0, worldPos.getY() + 1.0, worldPos.getZ() + 1.0
        );
        for (var subLevel : SableCompanion.INSTANCE.getAllIntersecting(level, box)) {
            return subLevel;
        }
        return null;
    }

    private CBCProtectionEvents() {
    }
}
