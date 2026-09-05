package com.mapter.aeroclaims.mixin.ftb;

import com.mapter.aeroclaims.client.AeroClaimsClientStats;
import dev.ftb.mods.ftbchunks.client.FTBChunksClient;
import dev.ftb.mods.ftbchunks.net.SendGeneralDataPacket;
import dev.ftb.mods.ftblibrary.ui.Theme;
import net.minecraft.network.chat.Component;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "dev.ftb.mods.ftbchunks.client.gui.ChunkScreen$CustomBottomPanel", remap = false)
public abstract class CustomBottomPanelMixin {

    @org.spongepowered.asm.mixin.Unique
    private boolean aeroclaims$showClaimsUsage;
    @org.spongepowered.asm.mixin.Unique
    private boolean aeroclaims$showForceloadUsage;

    @Inject(method = "drawBackground", at = @At("HEAD"))
    private void aeroclaims$checkUsageFits(GuiGraphics graphics, Theme theme, int x, int y,
                                            int width, int height, CallbackInfo ci) {
        int claims = AeroClaimsClientStats.getFtbClaims();
        SendGeneralDataPacket.GeneralChunkData data = FTBChunksClient.INSTANCE.getGeneralChunkData();

        if (claims < 0 || data == null) {
            aeroclaims$showClaimsUsage = false;
            aeroclaims$showForceloadUsage = false;
            return;
        }

        String claimsText = aeroclaims$lineText(
                "ftbchunks.gui.claimed", data.claimed(), data.maxClaimChunks(), claims);
        String forceloadsText = aeroclaims$lineText(
                "ftbchunks.gui.force_loaded", data.loaded(), data.maxForceLoadChunks(),
                AeroClaimsClientStats.isProviderForceloads() ? AeroClaimsClientStats.getFtbForceloads() : -1);
        boolean fits = theme.getStringWidth(claimsText) + theme.getStringWidth(forceloadsText) + 10 <= width;
        aeroclaims$showClaimsUsage = fits;
        aeroclaims$showForceloadUsage = fits && AeroClaimsClientStats.isProviderForceloads();
    }

    @Redirect(
            method = "drawBackground",
            at = @At(
                    value = "INVOKE",
                    target = "Ldev/ftb/mods/ftblibrary/ui/Theme;drawString(Lnet/minecraft/client/gui/GuiGraphics;Ljava/lang/Object;II)I",
                    ordinal = 0
            )
    )
    private int aeroclaims$appendClaimUsage(Theme theme, GuiGraphics graphics, Object value, int x, int y) {
        return theme.drawString(graphics,
                aeroclaims$appendUsage(value, AeroClaimsClientStats.getFtbClaims(), false), x, y);
    }

    @Redirect(
            method = "drawBackground",
            at = @At(
                    value = "INVOKE",
                    target = "Ldev/ftb/mods/ftblibrary/ui/Theme;drawString(Lnet/minecraft/client/gui/GuiGraphics;Ljava/lang/Object;II)I",
                    ordinal = 1
            )
    )
    private int aeroclaims$appendForceloadUsage(Theme theme, GuiGraphics graphics, Object value, int x, int y) {
        return theme.drawString(graphics,
                aeroclaims$appendUsage(value, AeroClaimsClientStats.getFtbForceloads(), true), x, y);
    }

    @Redirect(
            method = "drawBackground",
            at = @At(
                    value = "INVOKE",
                    target = "Ldev/ftb/mods/ftblibrary/ui/Theme;getStringWidth(Ljava/lang/String;)I",
                    ordinal = 0
            )
    )
    private int aeroclaims$includeForceloadUsageInWidth(Theme theme, String value) {
        if (aeroclaims$showForceloadUsage) {
            value += " (" + AeroClaimsClientStats.getFtbForceloads() + ")";
        }
        return theme.getStringWidth(value);
    }

    private Object aeroclaims$appendUsage(Object value, int usage, boolean forceload) {
        if ((forceload ? !aeroclaims$showForceloadUsage : !aeroclaims$showClaimsUsage)
                || usage < 0 || !(value instanceof Component component)) return value;
        return component.copy().append(Component.literal(" (" + usage + ")"));
    }

    private static String aeroclaims$lineText(String key, int current, int maximum, int usage) {
        String suffix = usage >= 0 ? " (" + usage + ")" : "";
        return Component.translatable(key)
                .append(Component.literal(":"))
                .append(Component.literal(current + "/" + maximum + suffix))
                .getString();
    }
}
