package com.mapter.aeroclaims.config;

import net.neoforged.neoforge.common.ModConfigSpec;

public class AeroClaimsConfig {

    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.IntValue MAX_SHIP_BLOCKS;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        builder.push("general");
        MAX_SHIP_BLOCKS = builder
                .defineInRange("maxShipBlocks", 10000, 1, Integer.MAX_VALUE);
        builder.pop();

        SPEC = builder.build();
    }

    private AeroClaimsConfig() {
    }
}