package com.mapter.aeroclaims.sublevel;

import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3dc;

public class SableShipUtils {
    public static boolean isOnShip(ServerLevel level, BlockPos pos) {
        return getShipAt(level, pos) != null;
    }

    public static @Nullable SubLevel getShipAt(ServerLevel level, BlockPos pos) {
        SubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) return null;
        if (!container.inBounds(pos)) return null;
        var plot = container.getPlot(pos.getX() >> 4, pos.getZ() >> 4);
        if (plot == null) return null;
        return plot.getSubLevel();
    }

    public static @Nullable String getShipId(@Nullable SubLevel ship) {
        if (ship == null) return null;
        return ship.getUniqueId().toString();
    }

    public static @Nullable String getShipName(@Nullable SubLevel ship) {
        if (ship == null) return null;
        String name = ship.getName();
        return name != null ? name : "ship";
    }

    public static @Nullable double[] getShipWorldPos(@Nullable SubLevel ship) {
        if (ship == null) return null;
        Pose3dc pose = ship.logicalPose();
        if (pose == null) return null;
        Vector3dc pos = pose.position();
        return new double[]{pos.x(), pos.y(), pos.z()};
    }

    public static boolean deleteShipById(ServerLevel level, String shipId) {
        SubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) return false;
        try {
            java.util.UUID uuid = java.util.UUID.fromString(shipId);
            SubLevel subLevel = container.getSubLevel(uuid);
            if (subLevel == null) return false;
            container.removeSubLevel(subLevel, dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason.REMOVED);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}