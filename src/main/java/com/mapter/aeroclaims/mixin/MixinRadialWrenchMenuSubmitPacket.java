package com.mapter.aeroclaims.mixin;

import com.mapter.aeroclaims.protect.CreateProtectionHelper;
import com.simibubi.create.content.contraptions.wrench.RadialWrenchMenuSubmitPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;


@Mixin(RadialWrenchMenuSubmitPacket.class)
public class MixinRadialWrenchMenuSubmitPacket {

    @Shadow
    private BlockPos blockPos;

    @Inject(method = "handle", remap = false, at = @At("HEAD"), cancellable = true)
    public void aeroclaims$onHandle(ServerPlayer player, CallbackInfo ci) {
        if (player == null) return;
        if (!CreateProtectionHelper.isBlockAccessAllowed(blockPos, player))
            ci.cancel();
    }
}
