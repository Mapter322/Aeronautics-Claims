package com.mapter.aeroclaims;

import com.mapter.aeroclaims.ship.VSShipEventHandler;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@EventBusSubscriber
public class ServerTickHandler {

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        VSShipEventHandler.onServerTick(event.getServer());
    }
}