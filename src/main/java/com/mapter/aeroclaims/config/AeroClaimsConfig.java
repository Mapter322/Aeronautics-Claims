package com.mapter.aeroclaims.config;

import net.neoforged.neoforge.common.ModConfigSpec;

public class AeroClaimsConfig {

    public static final ModConfigSpec SPEC;


    public static final ModConfigSpec.IntValue BLOCKS_PER_CLAIM;
    public static final ModConfigSpec.IntValue SCAN_INTERVAL_TICKS;
    public static final ModConfigSpec.BooleanValue DEACTIVATE_ON_OVERFLOW;
    public static final ModConfigSpec.IntValue CLAIM_MARGIN_BLOCKS;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        builder.push("general");
        BLOCKS_PER_CLAIM = builder
                .comment("How many ship blocks one aero claim covers. Example: 100 means 1 claim = 100 block limit.")
                .defineInRange("blocksPerClaim", 100, 1, Integer.MAX_VALUE);
        SCAN_INTERVAL_TICKS = builder
                .comment("How often (in ticks) to scan all ships in the world and update claimed/unclaimed lists. 20 ticks = 1 second.")
                .defineInRange("scanIntervalTicks", 100, 20, Integer.MAX_VALUE);
        DEACTIVATE_ON_OVERFLOW = builder
                .comment("If true, the claim will be deactivated when a refresh finds the ship exceeds its block limit. Default: false.")
                .define("deactivateOnOverflow", false);
        CLAIM_MARGIN_BLOCKS = builder
                .comment("Additional blocks of protection margin around claimed blocks. 0 = no margin, 1 = 1 block buffer, etc. Default: 0.")
                .defineInRange("claimMarginBlocks", 0, 0, 100);
        builder.pop();

        SPEC = builder.build();
    }

    private AeroClaimsConfig() {
    }
}
