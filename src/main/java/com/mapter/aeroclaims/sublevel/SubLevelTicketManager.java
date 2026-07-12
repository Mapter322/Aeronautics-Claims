package com.mapter.aeroclaims.sublevel;

import com.mapter.aeroclaims.Aeroclaims;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.ticket.SubLevelLoadingTicketType;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Unit;

import java.util.UUID;

public class SubLevelTicketManager {

    public static final SubLevelLoadingTicketType<Unit> CLAIM =
            SubLevelLoadingTicketType.create(
                    ResourceLocation.fromNamespaceAndPath(Aeroclaims.MODID, "claim"),
                    Unit.CODEC);

    public static void add(ServerLevel level, UUID subLevelId) {
        ServerSubLevelContainer c = SubLevelContainer.getContainer(level);
        if (c == null) return;
        SubLevel s = c.getSubLevel(subLevelId);
        if (s instanceof ServerSubLevel sl) {
            c.addForceLoadTicket(sl, CLAIM, Unit.INSTANCE);
        }
    }

    public static void remove(ServerLevel level, UUID subLevelId) {
        ServerSubLevelContainer c = SubLevelContainer.getContainer(level);
        if (c == null) return;
        SubLevel s = c.getSubLevel(subLevelId);
        if (s instanceof ServerSubLevel sl) {
            c.removeForceLoadTicket(sl, CLAIM, Unit.INSTANCE);
        }
    }
}
