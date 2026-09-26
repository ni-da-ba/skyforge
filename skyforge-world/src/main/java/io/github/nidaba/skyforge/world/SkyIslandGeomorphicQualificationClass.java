package io.github.nidaba.skyforge.world;

import java.util.List;
import java.util.Objects;

/** Calibration class for aggregate D1 diagnostics over a semantic reach or finite ordinary span. */
public enum SkyIslandGeomorphicQualificationClass {
    ALLUVIAL,
    INCISED,
    CASCADE,
    MIXED;

    public static SkyIslandGeomorphicQualificationClass classify(
            SkyIslandSemanticChannelReach reach) {
        Objects.requireNonNull(reach, "reach");
        return classifyKinds(reach.profiles().stream()
                .map(SkyIslandChannelProfile::kind)
                .toList());
    }

    public static SkyIslandGeomorphicQualificationClass classifyKinds(
            List<SkyIslandChannelProfileKind> kinds) {
        kinds = List.copyOf(kinds);
        if (kinds.isEmpty()) {
            throw new IllegalArgumentException("profile-kind classification requires at least one kind");
        }
        kinds.forEach(kind -> Objects.requireNonNull(kind, "profile kind"));
        SkyIslandChannelProfileKind first = kinds.getFirst();
        boolean mixed = kinds.stream().anyMatch(kind -> kind != first);
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
