package com.mapter.aeroclaims.mixin;

import com.mapter.aeroclaims.protect.CreateProtectionHelper;
import dev.simulated_team.simulated.network.packets.AssemblePacket;
import foundry.veil.api.network.handler.ServerPacketContext;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AssemblePacket.class)
public class MixinAssemblePacket {

    @Shadow
    private BlockPos pos;

    @Inject(method = "handle", remap = false, at = @At("HEAD"), cancellable = true)
    public void aeroclaims$onHandle(ServerPacketContext context, CallbackInfo ci) {
        ServerPlayer player = context.player();
        if (!CreateProtectionHelper.isBlockAccessAllowed(pos, player))
            ci.cancel();
    }
}
