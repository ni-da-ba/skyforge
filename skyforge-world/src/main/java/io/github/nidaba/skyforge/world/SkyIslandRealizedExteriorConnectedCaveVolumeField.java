package io.github.nidaba.skyforge.world;

import java.util.Objects;
import java.util.Optional;

/**
 * Physical-Y query view of the AUTH-0030 exterior-connected cave field.
 *
 * <p>This adapter composes AUTH-0030 with the accepted AUTH-0027 semantic-depth realization
 * transform. It performs no backend carving and no coordinate placement beyond physical Y.
 */
public final class SkyIslandRealizedExteriorConnectedCaveVolumeField {
    private final SkyIslandExteriorConnectedCaveVolumeField semanticField;
    private final SkyIslandSemanticDepthRealizationTransform transform;

    public SkyIslandRealizedExteriorConnectedCaveVolumeField(
            SkyIslandExteriorConnectedCaveVolumeField semanticField,
            SkyIslandVerticalColumnField columns) {
        this.semanticField = Objects.requireNonNull(semanticField, "semanticField");
        Objects.requireNonNull(columns, "columns");
        if (Double.doubleToLongBits(semanticField.descriptor().nominalRadius())
                != Double.doubleToLongBits(columns.nominalRadius())) {
            throw new IllegalArgumentException(
                    "cave authorship and physical column field must share nominalRadius");
        }
        this.transform = new SkyIslandSemanticDepthRealizationTransform(columns);
    }

    public SkyIslandExteriorConnectedCaveVolumeField semanticField() {
        return semanticField;
    }

    public SkyIslandSemanticDepthRealizationTransform transform() {
        return transform;
    }

    public SkyIslandExteriorConnectedCaveVolumeSample sample(
            SkyIslandRealizedSubsurfacePosition physicalPosition) {
        Objects.requireNonNull(physicalPosition, "physicalPosition");
        Optional<SkyIslandSubsurfacePosition> semantic = transform.toSemantic(physicalPosition);
        return semantic.isEmpty()
                ? SkyIslandExteriorConnectedCaveVolumeSample.outside(-1.0)
                : semanticField.sample(semantic.orElseThrow());
    }

    public boolean contains(SkyIslandRealizedSubsurfacePosition physicalPosition) {
        return sample(physicalPosition).inside();
    }
}
