package com.mapter.aeroclaims.config;

import net.neoforged.neoforge.common.ModConfigSpec;

public class AeroClaimsConfig {

    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.IntValue MAX_SHIP_BLOCKS;

    // Interval for scanning all ships in the world (in ticks).
    // 20 ticks = 1 second. Default 100 (every 5 seconds).
    public static final ModConfigSpec.IntValue SCAN_INTERVAL_TICKS;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        builder.push("general");
        MAX_SHIP_BLOCKS = builder
                .defineInRange("maxShipBlocks", 10000, 1, Integer.MAX_VALUE);
        SCAN_INTERVAL_TICKS = builder
                .comment("How often (in ticks) to scan all ships in the world and update claimed/unclaimed lists. 20 ticks = 1 second.")
                .defineInRange("scanIntervalTicks", 100, 20, Integer.MAX_VALUE);
        builder.pop();

        SPEC = builder.build();
    }

    private AeroClaimsConfig() {
    }
}
