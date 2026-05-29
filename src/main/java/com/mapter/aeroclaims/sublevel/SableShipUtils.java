package com.mapter.aeroclaims.sublevel;

import dev.ryanhcode.sable.companion.SableCompanion;
import dev.ryanhcode.sable.companion.SubLevelAccess;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3dc;

public class SableShipUtils {

    public static boolean isOnShip(Level level, BlockPos pos) {
        return getShipAt(level, pos) != null;
    }

    public static @Nullable SubLevelAccess getShipAt(Level level, BlockPos pos) {
        return SableCompanion.INSTANCE.getContaining(level, pos);
    }

    public static @Nullable String getShipId(@Nullable SubLevelAccess ship) {
        if (ship == null) return null;
        return ship.getUniqueId().toString();
    }

    public static @Nullable String getShipName(@Nullable SubLevelAccess ship) {
        if (ship == null) return null;
        String name = ship.getName();
        return name != null ? name : "ship";
    }

    public static @Nullable double[] getShipWorldPos(@Nullable SubLevelAccess ship) {
        if (ship == null) return null;
        Pose3dc pose = ship.logicalPose();
        if (pose == null) return null;
        Vector3dc pos = pose.position();
        return new double[]{pos.x(), pos.y(), pos.z()};
    }


    public static boolean deleteShipById(net.minecraft.server.level.ServerLevel level, String shipId) {
        dev.ryanhcode.sable.api.sublevel.SubLevelContainer container =
                dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(level);
        if (container == null) return false;
        try {
            java.util.UUID uuid = java.util.UUID.fromString(shipId);
            dev.ryanhcode.sable.sublevel.SubLevel subLevel = container.getSubLevel(uuid);
            if (subLevel == null) return false;
            container.removeSubLevel(subLevel, dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason.REMOVED);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
