package com.mapter.aeroclaims.client;

public final class AeroClaimsClientStats {

    private static int opacClaims = -1;
    private static int opacForceloads = -1;
    private static int ftbClaims = -1;
    private static int ftbForceloads = -1;
    private static boolean providerForceloads;

    private AeroClaimsClientStats() {}

    public static void update(int opacClaims, int opacForceloads, int ftbClaims, int ftbForceloads,
                              boolean providerForceloads) {
        AeroClaimsClientStats.opacClaims = opacClaims;
        AeroClaimsClientStats.opacForceloads = opacForceloads;
        AeroClaimsClientStats.ftbClaims = ftbClaims;
        AeroClaimsClientStats.ftbForceloads = ftbForceloads;
        AeroClaimsClientStats.providerForceloads = providerForceloads;
    }

    public static int getOpacClaims() {
        return opacClaims;
    }

    public static int getOpacForceloads() {
        return opacForceloads;
    }

    public static int getFtbClaims() {
        return ftbClaims;
    }

    public static int getFtbForceloads() {
        return ftbForceloads;
    }

    public static boolean isProviderForceloads() {
        return providerForceloads;
    }
}
