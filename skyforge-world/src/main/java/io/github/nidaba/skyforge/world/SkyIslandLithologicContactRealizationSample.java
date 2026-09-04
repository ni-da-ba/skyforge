package io.github.nidaba.skyforge.world;

/**
 * One continuous AUTH-0035 sample around a realized lithologic contact.
 *
 * <p>When no realized contact influences the position, contact provenance is empty and all
 * transition channels are zero. Authored cave void likewise carries no material transition.
 */
public record SkyIslandLithologicContactRealizationSample(
        boolean owned,
        boolean materialPresent,
        int contactId,
        SkyIslandLithologicContactKind contactKind,
        int firstAssemblageId,
        int secondAssemblageId,
        double contactInfluence,
        double firstAssemblageWeight,
        double secondAssemblageWeight,
        double hostFabricTransition,
        double alterationTransition,
        double hydrologicTransition,
        double mineralizationTransition,
        double caveExposureCoupling) {

    public SkyIslandLithologicContactRealizationSample {
        requireNormalized("contactInfluence", contactInfluence);
        requireNormalized("firstAssemblageWeight", firstAssemblageWeight);
        requireNormalized("secondAssemblageWeight", secondAssemblageWeight);
        requireNormalized("hostFabricTransition", hostFabricTransition);
        requireNormalized("alterationTransition", alterationTransition);
        requireNormalized("hydrologicTransition", hydrologicTransition);
        requireNormalized("mineralizationTransition", mineralizationTransition);
        requireNormalized("caveExposureCoupling", caveExposureCoupling);

        if (!owned && materialPresent) {
            throw new IllegalArgumentException("unowned contact sample cannot contain material");
        }

        boolean hasContact = contactId >= 0;
        if (!hasContact) {
            if (contactKind != null
                    || firstAssemblageId != -1
                    || secondAssemblageId != -1
                    || contactInfluence != 0.0
                    || firstAssemblageWeight != 0.0
                    || secondAssemblageWeight != 0.0
                    || hostFabricTransition != 0.0
                    || alterationTransition != 0.0
                    || hydrologicTransition != 0.0
                    || mineralizationTransition != 0.0
                    || caveExposureCoupling != 0.0) {
                throw new IllegalArgumentException("empty contact provenance must contain zero signals");
            }
        } else {
            if (contactKind == null
                    || firstAssemblageId < 0
                    || secondAssemblageId < 0
                    || firstAssemblageId >= secondAssemblageId
                    || contactInfluence <= 0.0) {
                throw new IllegalArgumentException("realized contact provenance is incomplete");
            }
            if (Math.abs(firstAssemblageWeight + secondAssemblageWeight - 1.0) > 1.0e-9) {
                throw new IllegalArgumentException("assemblage blend weights must sum to one");
            }
        }

        if (!materialPresent && hasContact) {
            throw new IllegalArgumentException("authored cave void cannot contain contact material");
        }
    }

    public static SkyIslandLithologicContactRealizationSample outside() {
        return empty(false, false);
    }

    public static SkyIslandLithologicContactRealizationSample authoredVoid() {
        return empty(true, false);
    }

    public static SkyIslandLithologicContactRealizationSample hostWithoutContact() {
        return empty(true, true);
    }

    private static SkyIslandLithologicContactRealizationSample empty(
            boolean owned, boolean materialPresent) {
        return new SkyIslandLithologicContactRealizationSample(
                owned,
                materialPresent,
                -1,
                null,
                -1,
                -1,
                0.0,
                0.0,
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
