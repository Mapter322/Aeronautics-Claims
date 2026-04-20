package com.mapter.aeroclaims.mixin;

import com.mapter.aeroclaims.compat.CreateProtectionHelper;
import com.simibubi.create.content.contraptions.glue.SuperGlueRemovalPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;


@Mixin(SuperGlueRemovalPacket.class)
public class MixinSuperGlueRemovalPacket {

    @Shadow
    private int entityId;

    @Inject(method = "handle", remap = false, at = @At("HEAD"), cancellable = true)
    public void aeroclaims$onHandle(ServerPlayer player, CallbackInfo ci) {
        if (player == null) return;
        Entity entity = player.level().getEntity(entityId);
        if (entity == null) return;
        if (!CreateProtectionHelper.isBlockAccessAllowed(entity.blockPosition(), player))
            ci.cancel();
    }
}
