package com.mapter.aeroclaims.permission;

import com.mapter.aeroclaims.claim.Claim;
import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import dev.ftb.mods.ftbteams.api.Team;
import dev.ftb.mods.ftbteams.api.TeamManager;
import dev.ftb.mods.ftbteams.api.TeamRank;
import net.minecraft.server.level.ServerPlayer;

import java.util.Optional;
import java.util.UUID;

public class FtbTeamsPermissionResolver implements ClaimPermissionResolver {

    @Override
    public boolean canAccess(ServerPlayer player, Claim claim) {
        UUID playerUuid = player.getUUID();
        UUID ownerUuid  = claim.getOwner();

        if (playerUuid.equals(ownerUuid)) return true;

        if (claim.isAllowOthers()) return true;

        if (!FTBTeamsAPI.api().isManagerLoaded()) return false;

        TeamManager manager = FTBTeamsAPI.api().getManager();

        Optional<Team> ownerTeamOpt = manager.getTeamForPlayerID(ownerUuid);
        if (ownerTeamOpt.isEmpty()) return false;

        Team ownerTeam = ownerTeamOpt.get();
        TeamRank rank = ownerTeam.getRankForPlayer(playerUuid);

        if (claim.isAllowParty() && rank.isMemberOrBetter()) return true;
        if (claim.isAllowAllies() && rank.isAllyOrBetter() && !rank.isMemberOrBetter()) return true;

        return false;
    }
}
