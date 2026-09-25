package io.github.nidaba.skyforge.world;

/** Calibration class for one macro reach's aggregate D1 diagnostics. */
public enum SkyIslandGeomorphicQualificationClass {
    ALLUVIAL,
    INCISED,
    CASCADE,
    MIXED;

    public static SkyIslandGeomorphicQualificationClass classify(
            SkyIslandSemanticChannelReach reach) {
        SkyIslandChannelProfileKind first = reach.profiles().getFirst().kind();
        boolean mixed = reach.profiles().stream().anyMatch(profile -> profile.kind() != first);
        if (mixed) {
            return MIXED;
        }
        return switch (first) {
            case ALLUVIAL -> ALLUVIAL;
            case INCISED -> INCISED;
            case CASCADE -> CASCADE;
        };
    }
}
