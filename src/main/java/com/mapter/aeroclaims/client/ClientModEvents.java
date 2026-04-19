package com.mapter.aeroclaims.client;

import com.mapter.aeroclaims.Aeroclaims;
import com.mapter.aeroclaims.registry.ModMenus;
import com.mapter.aeroclaims.screen.ClaimSettingsScreen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

@EventBusSubscriber(modid = Aeroclaims.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ClientModEvents {

    @SubscribeEvent
    public static void onRegisterScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenus.CLAIM_SETTINGS_MENU.get(), ClaimSettingsScreen::new);
    }
}
