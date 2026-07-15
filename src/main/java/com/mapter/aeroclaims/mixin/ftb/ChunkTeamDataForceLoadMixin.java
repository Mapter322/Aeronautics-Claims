package com.mapter.aeroclaims.mixin.ftb;

import com.mapter.aeroclaims.claim.AeroClaimSavedData;
import dev.ftb.mods.ftbteams.api.Team;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.UUID;

@Mixin(targets = "dev.ftb.mods.ftbchunks.data.ChunkTeamDataImpl", remap = false)
public abstract class ChunkTeamDataForceLoadMixin {

    private static final Logger LOGGER = LoggerFactory.getLogger(ChunkTeamDataForceLoadMixin.class);

    @Inject(method = "getMaxForceLoadChunks", at = @At("RETURN"), cancellable = true)
    private void aeroclaims$reduceForceloadByMigrated(CallbackInfoReturnable<Integer> cir) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;

        int migrated = aeroclaims$totalMigratedForceloads(server);
        if (migrated > 0) {
            cir.setReturnValue(Math.max(0, cir.getReturnValue() - migrated));
        }
    }

    @Unique
    private int aeroclaims$totalMigratedForceloads(MinecraftServer server) {
        try {
            Team team = (Team) this.getClass().getMethod("getTeam").invoke(this);
            int total = 0;
            for (UUID id : team.getMembers()) {
                total += AeroClaimSavedData.get(server.overworld()).getMigratedForceloads(id);
            }
            return total;
        } catch (Exception e) {
            LOGGER.error("[AeroClaims] Failed to get total migrated forceload slots", e);
            return 0;
        }
    }
}
