package com.mapter.aeroclaims.mixin;

import com.mapter.aeroclaims.protect.CreateProtectionHelper;
import dev.simulated_team.simulated.network.packets.PlaceMergingGluePacket;
import foundry.veil.api.network.handler.ServerPacketContext;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlaceMergingGluePacket.class)
public class MixinPlaceMergingGluePacket {

    @Shadow
    private BlockPos parentPos;

    @Shadow
    private BlockPos childPos;

    @Inject(method = "handle", remap = false, at = @At("HEAD"), cancellable = true)
    public void aeroclaims$onHandle(ServerPacketContext ctx, CallbackInfo ci) {
        ServerPlayer player = ctx.player();
        if (player == null) return;
        if (!CreateProtectionHelper.isBlockAccessAllowed(parentPos, player)
                || !CreateProtectionHelper.isBlockAccessAllowed(childPos, player))
            ci.cancel();
    }
}
