package io.github.nidaba.skyforge.world;

import java.util.Objects;
import java.util.Optional;

/**
 * Physical-Y query view of one AUTH-0026 cave-volume field.
 *
 * <p>The authored cave field remains authoritative. This adapter performs only semantic-depth
 * realization through supplied physical columns and never changes cave topology or geometry.
 */
public final class SkyIslandRealizedCaveVolumeField {
    private final SkyIslandCaveVolumeField semanticField;
    private final SkyIslandSemanticDepthRealizationTransform transform;

    public SkyIslandRealizedCaveVolumeField(
            SkyIslandCaveVolumeField semanticField,
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

    public SkyIslandCaveVolumeField semanticField() {
        return semanticField;
    }

    public SkyIslandSemanticDepthRealizationTransform transform() {
        return transform;
    }

    public SkyIslandCaveVolumeSample sample(SkyIslandRealizedSubsurfacePosition physicalPosition) {
        Objects.requireNonNull(physicalPosition, "physicalPosition");
        Optional<SkyIslandSubsurfacePosition> semantic = transform.toSemantic(physicalPosition);
        return semantic.isEmpty()
                ? SkyIslandCaveVolumeSample.outside(-1.0)
                : semanticField.sample(semantic.orElseThrow());
    }

    public boolean contains(SkyIslandRealizedSubsurfacePosition physicalPosition) {
        return sample(physicalPosition).inside();
    }
}
