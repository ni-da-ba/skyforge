package io.github.nidaba.skyforge.world;

import java.util.Objects;

/** One active host-material planning cell interpreted through the AUTH-0033 semantic vocabulary. */
public record SkyIslandMaterialFamilyCell(
        int index,
        int xIndex,
        int depthIndex,
        int zIndex,
        SkyIslandSubsurfacePosition position,
        double coherentMassiveHost,
        double layeredFabricRichHost,
        double stronglyAlteredHost,
        double waterConditionedHost,
        double mineralBearingStructuralHost) {

    public SkyIslandMaterialFamilyCell {
        if (index < 0 || xIndex < 0 || depthIndex < 0 || zIndex < 0) {
            throw new IllegalArgumentException("material-family cell indices must be non-negative");
        }
        position = Objects.requireNonNull(position, "position");
        requireNormalized("coherentMassiveHost", coherentMassiveHost);
        requireNormalized("layeredFabricRichHost", layeredFabricRichHost);
        requireNormalized("stronglyAlteredHost", stronglyAlteredHost);
        requireNormalized("waterConditionedHost", waterConditionedHost);
        requireNormalized("mineralBearingStructuralHost", mineralBearingStructuralHost);
        if (Math.max(coherentMassiveHost, layeredFabricRichHost) <= 0.0) {
            throw new IllegalArgumentException(
                    "active host material requires at least one host-fabric interpretation");
        }
    }

    public double membership(SkyIslandMaterialFamilyKind kind) {
        Objects.requireNonNull(kind, "kind");
        return switch (kind) {
            case COHERENT_MASSIVE_HOST -> coherentMassiveHost;
            case LAYERED_FABRIC_RICH_HOST -> layeredFabricRichHost;
            case STRONGLY_ALTERED_HOST -> stronglyAlteredHost;
            case WATER_CONDITIONED_HOST -> waterConditionedHost;
            case MINERAL_BEARING_STRUCTURAL_HOST -> mineralBearingStructuralHost;
        };
    }

    public double strongestHostFabric() {
        return Math.max(coherentMassiveHost, layeredFabricRichHost);
    }

    private static void requireNormalized(String name, double value) {
        if (!Double.isFinite(value) || value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException(name + " must be finite and in [0, 1]");
        }
    }
}
