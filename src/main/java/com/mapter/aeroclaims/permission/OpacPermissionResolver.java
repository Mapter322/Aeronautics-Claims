package com.mapter.aeroclaims.permission;

import com.mapter.aeroclaims.claim.Claim;
import net.minecraft.server.level.ServerPlayer;
import xaero.pac.common.server.api.OpenPACServerAPI;
import xaero.pac.common.server.parties.party.api.IPartyManagerAPI;
import xaero.pac.common.server.parties.party.api.IServerPartyAPI;

import java.util.UUID;

public class OpacPermissionResolver implements ClaimPermissionResolver {

    @Override
    public boolean canAccess(ServerPlayer player, Claim claim) {

        UUID ownerUUID = claim.getOwner();

        UUID playerUUID = player.getUUID();

        if (playerUUID.equals(ownerUUID))
            return true;

        if (claim.isAllowOthers())
            return true;

        OpenPACServerAPI api = OpenPACServerAPI.get(player.server);

        if (api == null || api.getPartyManager() == null)
            return false;

        IPartyManagerAPI partyManager = api.getPartyManager();

        IServerPartyAPI playerParty = partyManager.getPartyByMember(playerUUID);
        IServerPartyAPI ownerParty = partyManager.getPartyByMember(ownerUUID);

        if (playerParty == null || ownerParty == null)
            return false;

        if (claim.isAllowParty() && playerParty.equals(ownerParty))
            return true;

        if (claim.isAllowAllies() && ownerParty.isAlly(playerParty.getId()))
            return true;

        return false;
    }
}