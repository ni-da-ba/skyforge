package io.github.nidaba.skyforge.world;

/**
 * One sample of the authored cave-volume field.
 *
 * <p>signedClearance is positive inside authored cave void, zero on the authored cave boundary,
 * and negative outside. It is a normalized semantic clearance, not a physical distance in blocks.
 */
public record SkyIslandCaveVolumeSample(
        double signedClearance,
        int systemId,
        PrimitiveKind primitiveKind,
        int primitiveId) {

    public SkyIslandCaveVolumeSample {
        if (!Double.isFinite(signedClearance)) {
            throw new IllegalArgumentException("signedClearance must be finite");
        }
        if (systemId < -1 || primitiveId < -1) {
            throw new IllegalArgumentException("cave volume provenance identifiers must be >= -1");
        }
        if ((systemId < 0 || primitiveId < 0) && primitiveKind != PrimitiveKind.NONE) {
            throw new IllegalArgumentException("unowned cave-volume sample must use NONE provenance");
        }
        if (primitiveKind == PrimitiveKind.NONE && (systemId >= 0 || primitiveId >= 0)) {
            throw new IllegalArgumentException("NONE provenance must not carry identifiers");
        }
    }

    public boolean inside() {
        return signedClearance > 0.0;
    }

    public enum PrimitiveKind {
        NONE,
        CHAMBER,
        PASSAGE
    }

    public static SkyIslandCaveVolumeSample outside(double signedClearance) {
        return new SkyIslandCaveVolumeSample(
                Math.min(0.0, signedClearance),
                -1,
                PrimitiveKind.NONE,
                -1);
    }
}
