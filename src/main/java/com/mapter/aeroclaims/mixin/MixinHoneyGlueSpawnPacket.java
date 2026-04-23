package com.mapter.aeroclaims.mixin;

import com.mapter.aeroclaims.protect.CreateProtectionHelper;
import dev.simulated_team.simulated.network.packets.honey_glue.HoneyGlueSpawnPacket;
import foundry.veil.api.network.handler.PacketContext;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HoneyGlueSpawnPacket.class)
public class MixinHoneyGlueSpawnPacket {

    @Shadow
    private BlockPos from;

    @Shadow
    private BlockPos to;

    @Inject(method = "handle", remap = false, at = @At("HEAD"), cancellable = true)
    public void aeroclaims$onHandle(PacketContext context, CallbackInfo ci) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        if (!CreateProtectionHelper.isBlockAccessAllowed(from, player)
                || !CreateProtectionHelper.isBlockAccessAllowed(to, player))
            ci.cancel();
    }
}
