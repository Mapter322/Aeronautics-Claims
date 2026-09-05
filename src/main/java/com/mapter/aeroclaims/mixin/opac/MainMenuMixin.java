package com.mapter.aeroclaims.mixin.opac;

import com.mapter.aeroclaims.client.AeroClaimsClientStats;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import xaero.pac.client.gui.MainMenu;
import xaero.pac.client.gui.component.CachedComponentSupplier;

@Mixin(value = MainMenu.class, remap = false)
public abstract class MainMenuMixin {

    @Redirect(
            method = "drawClaimsInfo",
            at = @At(
                    value = "INVOKE",
                    target = "Lxaero/pac/client/gui/component/CachedComponentSupplier;get([Ljava/lang/Object;)Lnet/minecraft/network/chat/Component;",
                    ordinal = 0
            )
    )
    private Component aeroclaims$appendClaimUsage(CachedComponentSupplier supplier, Object[] args) {
        return aeroclaims$appendUsage(supplier.get(args), AeroClaimsClientStats.getOpacClaims());
    }

    @Redirect(
            method = "drawClaimsInfo",
            at = @At(
                    value = "INVOKE",
                    target = "Lxaero/pac/client/gui/component/CachedComponentSupplier;get([Ljava/lang/Object;)Lnet/minecraft/network/chat/Component;",
                    ordinal = 1
            )
    )
    private Component aeroclaims$appendForceloadUsage(CachedComponentSupplier supplier, Object[] args) {
        return AeroClaimsClientStats.isProviderForceloads()
                ? aeroclaims$appendUsage(supplier.get(args), AeroClaimsClientStats.getOpacForceloads())
                : supplier.get(args);
    }

    private static Component aeroclaims$appendUsage(Component original, int usage) {
        if (usage < 0) return original;
        return original.copy().append(Component.literal(" (" + usage + ")"));
    }
}
