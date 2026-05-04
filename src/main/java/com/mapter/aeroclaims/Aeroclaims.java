package com.mapter.aeroclaims;

import com.mapter.aeroclaims.claim.ClaimManager;
import com.mapter.aeroclaims.config.AeroClaimsConfig;
import com.mapter.aeroclaims.registry.ModBlocks;
import com.mapter.aeroclaims.registry.ModMenus;
import com.mapter.aeroclaims.sublevel.SableSubLevelEventHandler;
import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;

@Mod(Aeroclaims.MODID)
public class Aeroclaims {
    public static final String MODID = "aeroclaims";

    public Aeroclaims(IEventBus modBus, ModContainer modContainer) {

        ModBlocks.register(modBus);
        ModMenus.register(modBus);

        modBus.addListener(Aeroclaims::addCreative);
        modBus.addListener(Aeroclaims::onCommonSetup);

        modContainer.registerConfig(ModConfig.Type.SERVER, AeroClaimsConfig.SPEC);
    }

    private static void onCommonSetup(FMLCommonSetupEvent event) {
        SableSubLevelEventHandler.register();

        boolean ftbLoaded  = ModList.get().isLoaded("ftbteams");
        boolean opacLoaded = ModList.get().isLoaded("openpartiesandclaims");

        ClaimManager.init(ftbLoaded, opacLoaded);
    }

    private static void addCreative(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS) {
            event.accept(ModBlocks.CLAIM_BLOCK_ITEM.get());
        }
    }
}
