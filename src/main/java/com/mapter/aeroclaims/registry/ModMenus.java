package com.mapter.aeroclaims.registry;

import com.mapter.aeroclaims.screen.AeroClaimsMenu;
import com.mapter.aeroclaims.screen.ClaimBlockMenu;
import com.mapter.aeroclaims.Aeroclaims;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModMenus {

    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(BuiltInRegistries.MENU, Aeroclaims.MODID);

    public static final DeferredHolder<MenuType<?>, MenuType<ClaimBlockMenu>> CLAIM_SETTINGS_MENU =
            MENUS.register("claim_settings",
                    () -> IMenuTypeExtension.create(ClaimBlockMenu::new));

    public static final DeferredHolder<MenuType<?>, MenuType<AeroClaimsMenu>> AEROCLAIMS_MENU =
            MENUS.register("aeroclaims_menu",
                    () -> IMenuTypeExtension.create(AeroClaimsMenu::new));

    public static void register(IEventBus bus) {
        MENUS.register(bus);
    }
}