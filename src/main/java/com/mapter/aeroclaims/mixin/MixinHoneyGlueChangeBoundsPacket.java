package com.mapter.aeroclaims.mixin;

import com.mapter.aeroclaims.protect.CreateProtectionHelper;
import dev.simulated_team.simulated.network.packets.honey_glue.HoneyGlueChangeBoundsPacket;
import foundry.veil.api.network.handler.PacketContext;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

@Mixin(HoneyGlueChangeBoundsPacket.class)
public class MixinHoneyGlueChangeBoundsPacket {

    @Shadow
    private UUID honeyGlue;

    @Inject(method = "handle", remap = false, at = @At("HEAD"), cancellable = true)
    public void aeroclaims$onHandle(PacketContext context, CallbackInfo ci) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        Entity entity = ((ServerLevel) player.level()).getEntity(this.honeyGlue);
        if (entity == null) return;
        if (!CreateProtectionHelper.isBlockAccessAllowed(entity.blockPosition(), player))
            ci.cancel();
    }
}
