package com.mapter.aeroclaims.config;

import net.neoforged.neoforge.common.ModConfigSpec;

public class AeroClaimsConfig {

    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.EnumValue<PartyProvider> PARTY_PROVIDER;
    public static final ModConfigSpec.IntValue BLOCKS_PER_CLAIM;
    public static final ModConfigSpec.BooleanValue DEACTIVATE_ON_OVERFLOW;
    public static final ModConfigSpec.IntValue CLAIM_MARGIN_BLOCKS;
    public static final ModConfigSpec.BooleanValue EXPLOSION_PROTECTION;
    public static final ModConfigSpec.BooleanValue KINETIC_BLOCK_PROTECTION;


    public enum PartyProvider {
        FTB,
        OPAC
    }

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        builder.push("general");
        PARTY_PROVIDER = builder
                .comment(
                    "Which party/teams mod to use for claim access checks. FTB Teams or Open Parties and CLaims.",
                    "Default: OPAC"
                )
                .defineEnum("partyProvider", PartyProvider.OPAC);
        BLOCKS_PER_CLAIM = builder
                .comment("How many ship blocks one aero claim covers. Example: 100 means 1 claim = 100 block limit.")
                .defineInRange("blocksPerClaim", 250, 1, Integer.MAX_VALUE);
        DEACTIVATE_ON_OVERFLOW = builder
                .comment("If true, the claim will be deactivated when a refresh finds the ship exceeds its block limit. Default: false.")
                .define("deactivateOnOverflow", false);
        CLAIM_MARGIN_BLOCKS = builder
                .comment("Additional blocks of protection margin around claimed blocks. 0 = no margin, 1 = 1 block buffer, etc. Default: 0.")
                .defineInRange("claimMarginBlocks", 0, 0, 100);
        EXPLOSION_PROTECTION = builder
                .comment("If true, explosions cannot destroy or damage blocks inside active claims. Default: true.")
                .define("explosionProtection", true);
        KINETIC_BLOCK_PROTECTION = builder
                .comment("If true, Create drills and saws can only break claimed blocks if placed by a player with permission. Default: true.")
                .define("kineticBlockProtection", true);
        builder.pop();

        SPEC = builder.build();
    }

    private AeroClaimsConfig() {
    }
}
