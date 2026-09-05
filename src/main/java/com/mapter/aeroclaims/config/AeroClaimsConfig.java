package com.mapter.aeroclaims.config;

import net.neoforged.neoforge.common.ModConfigSpec;

public class AeroClaimsConfig {

    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.EnumValue<PartyProvider> PARTY_PROVIDER;
    public static final ModConfigSpec.EnumValue<ClaimProvider> CLAIM_PROVIDER;
    public static final ModConfigSpec.IntValue BLOCKS_PER_CLAIM;
    public static final ModConfigSpec.BooleanValue DEACTIVATE_ON_OVERFLOW;
    public static final ModConfigSpec.IntValue CLAIM_MARGIN_BLOCKS;
    public static final ModConfigSpec.BooleanValue EXPLOSION_PROTECTION;
    public static final ModConfigSpec.BooleanValue KINETIC_BLOCK_PROTECTION;
    public static final ModConfigSpec.BooleanValue ENABLE_DELETE_COMMAND;
    public static final ModConfigSpec.BooleanValue FORCELOAD_ENABLE;
    public static final ModConfigSpec.BooleanValue PROVIDER_SLOTS_FORCELOAD;

    public enum PartyProvider { FTB_TEAMS, OPAC }
    public enum ClaimProvider { OPAC, FTB_CHUNKS }

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        builder.push("general");
        PARTY_PROVIDER = builder
                .comment(
                    "Which party/teams mod to use for claim access checks. FTB Teams or Open Parties and CLaims.",
                    "Default: OPAC"
                )
                .defineEnum("partyProvider", PartyProvider.OPAC);
        CLAIM_PROVIDER = builder
                .comment(
                    "Which external claim mod supplies claim slots for AeroClaims.",
                    "Supported: OPAC (Open Parties and Claims), FTB_CHUNKS (FTB Chunks).",
                    "Default: OPAC"
                )
                .defineEnum("claimProvider", ClaimProvider.OPAC);
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
                .comment(
                    "If true, explosions and Create: Big Cannons projectiles cannot destroy or damage blocks inside active claims.",
                    "Default: true"
                )
                .define("explosionProtection", true);
        KINETIC_BLOCK_PROTECTION = builder
                .comment("If true, Create drills and saws can only break claimed blocks if placed by a player with permission. Default: true.")
                .define("kineticBlockProtection", true);
        ENABLE_DELETE_COMMAND = builder
                .comment(
                    "WARNING: This command deletes ALL unclaimed sublevels from the world.",
                    "Only enable this if you know what you're doing.",
                    "Default: false"
                )
                .define("enableDeleteCommand", false);
        FORCELOAD_ENABLE = builder
                .comment(
                    "Master switch for sublevel forceloading. If false, no claim will ever forceload its sublevel,",
                    "regardless of the per-claim forceload toggle, and providerSlotsForceload is ignored entirely.",
                    "Default: true"
                )
                .define("forceloadEnable", true);
        PROVIDER_SLOTS_FORCELOAD = builder
                .comment(
                    "If true, sublevel forceloading consumes OPAC/FTB forceload slots.",
                    "If false, all activated claims get a free sub-level forceload.",
                    "Has no effect if forceloadEnable is false.",
                    "Default: false"
                )
                .define("providerSlotsForceload", false);
        builder.pop();

        SPEC = builder.build();
    }

    /**
     * Whether providerSlotsForceload should actually be honored. Always false when the
     * master forceloadEnable switch is off, ignoring the raw providerSlotsForceload value.
     */
    public static boolean isProviderSlotsForceload() {
        return FORCELOAD_ENABLE.get() && PROVIDER_SLOTS_FORCELOAD.get();
    }

    private AeroClaimsConfig() {
    }
}
