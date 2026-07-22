package com.mapter.aeroclaims.sublevel;

import com.mapter.aeroclaims.Aeroclaims;
import com.mapter.aeroclaims.claim.Claim;
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


    private static final int HARD_CLEAR_ATTEMPTS = 8;

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


    private static void hardClear(ServerLevel level, UUID subLevelId) {
        for (int i = 0; i < HARD_CLEAR_ATTEMPTS; i++) {
            remove(level, subLevelId);
        }
    }

    public static void sync(ServerLevel level, Claim claim, String currentShipId, boolean wantHeld) {
        String heldShipId = claim.getForceloadTicketShipId();
        boolean believeHeld = claim.isForceloadTicketHeld() && heldShipId != null;

        if (believeHeld && (!wantHeld || !heldShipId.equals(currentShipId))) {
            hardClear(level, UUID.fromString(heldShipId));
            claim.setForceloadTicketHeld(false);
            claim.setForceloadTicketShipId(null);
            believeHeld = false;
        }

        if (wantHeld && !believeHeld && currentShipId != null) {
            add(level, UUID.fromString(currentShipId));
            claim.setForceloadTicketHeld(true);
            claim.setForceloadTicketShipId(currentShipId);
        } else if (!wantHeld && currentShipId != null) {

            hardClear(level, UUID.fromString(currentShipId));
        }
    }
}
