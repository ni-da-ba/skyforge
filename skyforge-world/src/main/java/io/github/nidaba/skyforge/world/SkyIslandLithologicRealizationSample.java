package io.github.nidaba.skyforge.world;

import java.util.Objects;

/**
 * Stable backend-neutral AUTH-0036 lithologic realization contract at one semantic position.
 *
 * <p>The five realization channels remain compositional. Contact provenance and assemblage blend
 * state are carried explicitly rather than being collapsed into a named rock or backend material.
 */
public record SkyIslandLithologicRealizationSample(
        boolean owned,
        boolean materialPresent,
        int localAssemblageId,
        SkyIslandLithologicAssemblageKind localAssemblageKind,
        int contactId,
        SkyIslandLithologicContactKind contactKind,
        int firstAssemblageId,
        SkyIslandLithologicAssemblageKind firstAssemblageKind,
        double firstAssemblageWeight,
        int secondAssemblageId,
        SkyIslandLithologicAssemblageKind secondAssemblageKind,
        double secondAssemblageWeight,
        double massiveMatrix,
        double fabricRichMatrix,
        double alterationOverprint,
        double waterConditioning,
        double mineralBearingStructure) {

    public SkyIslandLithologicRealizationSample {
        requireNormalized("firstAssemblageWeight", firstAssemblageWeight);
        requireNormalized("secondAssemblageWeight", secondAssemblageWeight);
        requireNormalized("massiveMatrix", massiveMatrix);
        requireNormalized("fabricRichMatrix", fabricRichMatrix);
        requireNormalized("alterationOverprint", alterationOverprint);
        requireNormalized("waterConditioning", waterConditioning);
        requireNormalized("mineralBearingStructure", mineralBearingStructure);

        if (!owned && materialPresent) {
            throw new IllegalArgumentException("unowned realization sample cannot contain material");
        }

        if (!materialPresent) {
            if (localAssemblageId != -1
                    || localAssemblageKind != null
                    || contactId != -1
                    || contactKind != null
                    || firstAssemblageId != -1
                    || firstAssemblageKind != null
                    || firstAssemblageWeight != 0.0
                    || secondAssemblageId != -1
                    || secondAssemblageKind != null
                    || secondAssemblageWeight != 0.0
                    || massiveMatrix != 0.0
                    || fabricRichMatrix != 0.0
                    || alterationOverprint != 0.0
                    || waterConditioning != 0.0
                    || mineralBearingStructure != 0.0) {
                throw new IllegalArgumentException(
                        "non-material realization samples must contain empty provenance and zero channels");
            }
        } else {
            if (localAssemblageId < 0 || localAssemblageKind == null) {
                throw new IllegalArgumentException(
                        "material realization requires local AUTH-0034 assemblage provenance");
            }
            if (Math.max(massiveMatrix, fabricRichMatrix) <= 0.0) {
                throw new IllegalArgumentException(
                        "material realization requires at least one host-matrix channel");
            }

            if (contactId < 0) {
                if (contactKind != null
                        || firstAssemblageId != localAssemblageId
                        || firstAssemblageKind != localAssemblageKind
                        || firstAssemblageWeight != 1.0
                        || secondAssemblageId != -1
                        || secondAssemblageKind != null
                        || secondAssemblageWeight != 0.0) {
                    throw new IllegalArgumentException(
                            "non-contact material must resolve completely to its local assemblage");
                }
            } else if (contactKind == null
                    || firstAssemblageId < 0
                    || secondAssemblageId < 0
                    || firstAssemblageKind == null
                    || secondAssemblageKind == null
                    || firstAssemblageId >= secondAssemblageId
                    || Math.abs(firstAssemblageWeight + secondAssemblageWeight - 1.0) > 1.0e-9) {
                throw new IllegalArgumentException(
                        "contact realization requires ordered parent assemblages and normalized blend weights");
            }
        }
    }

    public double channel(SkyIslandLithologicRealizationChannel channel) {
        Objects.requireNonNull(channel, "channel");
        return switch (channel) {
            case MASSIVE_MATRIX -> massiveMatrix;
            case FABRIC_RICH_MATRIX -> fabricRichMatrix;
            case ALTERATION_OVERPRINT -> alterationOverprint;
            case WATER_CONDITIONING -> waterConditioning;
            case MINERAL_BEARING_STRUCTURE -> mineralBearingStructure;
        };
    }

    public boolean contactActive() {
        return contactId >= 0;
    }

    public static SkyIslandLithologicRealizationSample outside() {
        return empty(false, false);
    }

    public static SkyIslandLithologicRealizationSample authoredVoid() {
        return empty(true, false);
    }

    private static SkyIslandLithologicRealizationSample empty(
            boolean owned, boolean materialPresent) {
        return new SkyIslandLithologicRealizationSample(
                owned,
                materialPresent,
                -1,
                null,
                -1,
                null,
                -1,
                null,
                0.0,
                -1,
                null,
                0.0,
                0.0,
                0.0,
                0.0,
                0.0,
                0.0);
    }

    private static void requireNormalized(String name, double value) {
        if (!Double.isFinite(value) || value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException(name + " must be finite and in [0, 1]");
        }
    }
}
