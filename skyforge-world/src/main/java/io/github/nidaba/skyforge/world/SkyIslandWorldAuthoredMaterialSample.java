package io.github.nidaba.skyforge.world;

import io.github.nidaba.skyforge.kernel.coordinate.Coordinate3;
import java.util.Objects;
import java.util.Optional;

/**
 * AUTH-0047 result for one world-space authored-material query through one explicit AUTH-0046
 * association.
 */
public record SkyIslandWorldAuthoredMaterialSample(
        SkyIslandAuthoredRealizationAssociation association,
        Coordinate3 worldPosition,
        SkyIslandRealizedSubsurfacePosition realizedPosition,
        SkyIslandSubsurfacePosition semanticPosition,
        SkyIslandMaterialRealizationSelection realization,
        SkyIslandMaterialBindingApplication application) {

    public SkyIslandWorldAuthoredMaterialSample {
        association = Objects.requireNonNull(association, "association");
        worldPosition = Objects.requireNonNull(worldPosition, "worldPosition");

        if (semanticPosition == null) {
            if (realizedPosition != null || realization != null || application != null) {
                throw new IllegalArgumentException(
                        "outside-physical samples cannot contain realized, semantic, or material state");
            }
        } else {
            realizedPosition = Objects.requireNonNull(realizedPosition, "realizedPosition");
            realization = Objects.requireNonNull(realization, "realization");
            if (realization.materialPresent() != (application != null)) {
                throw new IllegalArgumentException(
                        "AUTH-0045 application presence must match AUTH-0044 material presence");
            }
            if (application != null && !application.realization().equals(realization)) {
                throw new IllegalArgumentException(
                        "AUTH-0045 application must retain the exact AUTH-0044 realization");
            }
        }
    }

    public static SkyIslandWorldAuthoredMaterialSample outsidePhysical(
            SkyIslandAuthoredRealizationAssociation association,
            Coordinate3 worldPosition) {
        return new SkyIslandWorldAuthoredMaterialSample(
                association, worldPosition, null, null, null, null);
    }

    public boolean physicalInterior() {
        return semanticPosition != null;
    }

    public boolean authoredOwned() {
        return realization != null
                && realization.expressionSample().source().owned();
    }

    public boolean materialPresent() {
        return realization != null && realization.materialPresent();
    }

    public boolean authoredVoid() {
        return physicalInterior() && authoredOwned() && !materialPresent();
    }

    public Optional<SkyIslandSubsurfacePosition> semantic() {
        return Optional.ofNullable(semanticPosition);
    }

    public Optional<SkyIslandMaterialRealizationSelection> materialRealization() {
        return Optional.ofNullable(realization);
    }

    public Optional<SkyIslandMaterialBindingApplication> materialApplication() {
        return Optional.ofNullable(application);
    }

    public Optional<SkyIslandSemanticPaletteBindingKey> applicationKey() {
        return application == null
                ? Optional.empty()
                : Optional.of(application.bindingKey());
    }
}
