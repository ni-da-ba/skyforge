package io.github.nidaba.skyforge.world;

import java.util.Objects;

/**
 * One sample of the exterior-connected authored cave-volume field.
 *
 * <p>The sign convention is inherited from AUTH-0026: positive is authored cave void, zero is the
 * authored cave boundary, and negative is exterior. Provenance distinguishes unchanged AUTH-0026
 * cave volume from AUTH-0029 exposure-connection volume.
 */
public record SkyIslandExteriorConnectedCaveVolumeSample(
        double signedClearance,
        SourceKind sourceKind,
        int systemId,
        SkyIslandCaveVolumeSample.PrimitiveKind sourcePrimitiveKind,
        int sourcePrimitiveId,
        SkyIslandCaveExposureSide exposureSide) {

    public SkyIslandExteriorConnectedCaveVolumeSample {
        if (!Double.isFinite(signedClearance)) {
            throw new IllegalArgumentException("signedClearance must be finite");
        }
        sourceKind = Objects.requireNonNull(sourceKind, "sourceKind");
        sourcePrimitiveKind = Objects.requireNonNull(sourcePrimitiveKind, "sourcePrimitiveKind");

        switch (sourceKind) {
            case NONE -> {
                if (systemId != -1
                        || sourcePrimitiveId != -1
                        || sourcePrimitiveKind != SkyIslandCaveVolumeSample.PrimitiveKind.NONE
                        || exposureSide != null) {
                    throw new IllegalArgumentException("NONE cave-volume provenance must be empty");
                }
            }
            case BASE_CAVE -> {
                if (systemId < 0
                        || sourcePrimitiveId < 0
                        || sourcePrimitiveKind == SkyIslandCaveVolumeSample.PrimitiveKind.NONE
                        || exposureSide != null) {
                    throw new IllegalArgumentException("BASE_CAVE provenance is incomplete");
                }
            }
            case EXPOSURE_CONNECTION -> {
                if (systemId < 0
                        || sourcePrimitiveId < 0
                        || sourcePrimitiveKind == SkyIslandCaveVolumeSample.PrimitiveKind.NONE
                        || exposureSide == null) {
                    throw new IllegalArgumentException("EXPOSURE_CONNECTION provenance is incomplete");
                }
            }
        }
    }

    public boolean inside() {
        return signedClearance > 0.0;
    }

    public enum SourceKind {
        NONE,
        BASE_CAVE,
        EXPOSURE_CONNECTION
    }

    public static SkyIslandExteriorConnectedCaveVolumeSample outside(double signedClearance) {
        return new SkyIslandExteriorConnectedCaveVolumeSample(
                Math.min(0.0, signedClearance),
                SourceKind.NONE,
                -1,
                SkyIslandCaveVolumeSample.PrimitiveKind.NONE,
                -1,
                null);
    }

    public static SkyIslandExteriorConnectedCaveVolumeSample fromBase(
            SkyIslandCaveVolumeSample base) {
        Objects.requireNonNull(base, "base");
        if (base.primitiveKind() == SkyIslandCaveVolumeSample.PrimitiveKind.NONE) {
            return outside(base.signedClearance());
        }
        return new SkyIslandExteriorConnectedCaveVolumeSample(
                base.signedClearance(),
                SourceKind.BASE_CAVE,
                base.systemId(),
                base.primitiveKind(),
                base.primitiveId(),
                null);
    }
}
