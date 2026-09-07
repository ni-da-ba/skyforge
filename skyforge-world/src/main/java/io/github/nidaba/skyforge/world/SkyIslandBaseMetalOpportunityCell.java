package io.github.nidaba.skyforge.world;

import java.util.Objects;

/** One AUTH-0033 host-material planning cell enriched with AUTH-0093 base-metal opportunity. */
public record SkyIslandBaseMetalOpportunityCell(
        SkyIslandMaterialFamilyCell sourceCell,
        double ironOpportunity,
        double copperOpportunity,
        double zincOpportunity) {

    public SkyIslandBaseMetalOpportunityCell {
        sourceCell = Objects.requireNonNull(sourceCell, "sourceCell");
        requireNormalized("ironOpportunity", ironOpportunity);
        requireNormalized("copperOpportunity", copperOpportunity);
        requireNormalized("zincOpportunity", zincOpportunity);

        double mineralSupport = sourceCell.mineralBearingStructuralHost();
        if (ironOpportunity > mineralSupport
                || copperOpportunity > mineralSupport
                || zincOpportunity > mineralSupport) {
            throw new IllegalArgumentException(
                    "base-metal opportunity cannot exceed accepted mineral-bearing structural support");
        }
        if (mineralSupport == 0.0
                && (ironOpportunity != 0.0
                        || copperOpportunity != 0.0
                        || zincOpportunity != 0.0)) {
            throw new IllegalArgumentException(
                    "base-metal opportunity requires accepted mineral-bearing structural support");
        }
    }

    public double opportunity(SkyIslandBaseMetalKind kind) {
        Objects.requireNonNull(kind, "kind");
        return switch (kind) {
            case IRON -> ironOpportunity;
            case COPPER -> copperOpportunity;
            case ZINC -> zincOpportunity;
        };
    }

    private static void requireNormalized(String name, double value) {
        if (!Double.isFinite(value) || value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException(name + " must be finite and in [0, 1]");
        }
    }
}
