package com.mapter.aeroclaims.util;

import com.mapter.aeroclaims.config.AeroClaimsConfig;
import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import dev.ftb.mods.ftbteams.api.Team;
import dev.ftb.mods.ftbteams.api.TeamManager;
import net.minecraft.server.level.ServerPlayer;
import xaero.pac.common.server.api.OpenPACServerAPI;
import xaero.pac.common.server.player.config.api.v2.IPlayerConfigAPI;
import xaero.pac.common.server.player.config.api.v2.IPlayerConfigManagerAPI;
import xaero.pac.common.server.player.config.api.v2.PlayerConfigOptions;

import java.lang.reflect.Method;
import java.util.Optional;
import java.util.UUID;

public class TeamColorHelper {

    private static final int DEFAULT_COLOR = 0x33D9FF;

    public static int getTeamColor(ServerPlayer player, UUID ownerUuid) {
        if (AeroClaimsConfig.PARTY_PROVIDER.get() == AeroClaimsConfig.PartyProvider.FTB) {
            return getFtbTeamColor(ownerUuid);
        } else {
            return getOpacPartyColor(player, ownerUuid);
        }
    }

    private static int getFtbTeamColor(UUID ownerUuid) {
        if (!FTBTeamsAPI.api().isManagerLoaded()) {
            return DEFAULT_COLOR;
        }

        TeamManager manager = FTBTeamsAPI.api().getManager();
        Optional<Team> ownerTeamOpt = manager.getTeamForPlayerID(ownerUuid);
        if (ownerTeamOpt.isEmpty()) {
            return DEFAULT_COLOR;
        }

        try {
            Class<?> teamPropertiesClass = Class.forName("dev.ftb.mods.ftbteams.api.property.TeamProperties");
            Object colorProperty = teamPropertiesClass.getField("COLOR").get(null);
            Class<?> teamPropertyClass = Class.forName("dev.ftb.mods.ftbteams.api.property.TeamProperty");
            Method getPropertyMethod = ownerTeamOpt.get().getClass().getMethod("getProperty", teamPropertyClass);
            Object color = getPropertyMethod.invoke(ownerTeamOpt.get(), colorProperty);
            if (color != null) {
                int red = (int) color.getClass().getMethod("redi").invoke(color);
                int green = (int) color.getClass().getMethod("greeni").invoke(color);
                int blue = (int) color.getClass().getMethod("bluei").invoke(color);
                return (red << 16) | (green << 8) | blue;
            }
        } catch (Exception e) {
            return DEFAULT_COLOR;
        }
        return DEFAULT_COLOR;
    }

    private static int getOpacPartyColor(ServerPlayer player, UUID ownerUuid) {
        OpenPACServerAPI api = OpenPACServerAPI.get(player.server);
        if (api == null || api.getPartyManager() == null || api.getPlayerConfigManager() == null) {
            return DEFAULT_COLOR;
        }

        IPlayerConfigManagerAPI configManager = api.getPlayerConfigManager();
        IPlayerConfigAPI ownerConfig = configManager.getLoadedConfig(ownerUuid);
        if (ownerConfig == null) {
            return DEFAULT_COLOR;
        }

        int color = ownerConfig.getEffective(PlayerConfigOptions.CLAIMS_COLOR);
        return color == 0 ? DEFAULT_COLOR : color;
    }
}
