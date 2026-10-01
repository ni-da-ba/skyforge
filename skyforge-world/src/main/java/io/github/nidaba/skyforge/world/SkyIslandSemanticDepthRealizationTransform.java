package io.github.nidaba.skyforge.world;

import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;

/**
 * Exact column-relative transform between semantic subsurface depth and realized physical Y.
 *
 * <p>This transform owns no underside model and no backend coordinate system. It consumes
 * authoritative upper/underside columns supplied by a {@link SkyIslandVerticalColumnField}.
 */
public final class SkyIslandSemanticDepthRealizationTransform {
    private final SkyIslandVerticalColumnField columns;

    public SkyIslandSemanticDepthRealizationTransform(SkyIslandVerticalColumnField columns) {
        this.columns = Objects.requireNonNull(columns, "columns");
        if (!Double.isFinite(columns.nominalRadius()) || columns.nominalRadius() <= 0.0) {
            throw new IllegalArgumentException("column field nominalRadius must be positive and finite");
        }
    }

    public SkyIslandVerticalColumnField columns() {
        return columns;
    }

    public Optional<SkyIslandRealizedSubsurfacePosition> toPhysical(
            SkyIslandSubsurfacePosition semanticPosition) {
        Objects.requireNonNull(semanticPosition, "semanticPosition");
        return columns.columnAt(semanticPosition.surfacePosition())
                .map(column -> new SkyIslandRealizedSubsurfacePosition(
                        semanticPosition.surfacePosition(),
                        column.physicalYAt(semanticPosition.depthFraction())));
    }

    public Optional<SkyIslandSubsurfacePosition> toSemantic(
            SkyIslandRealizedSubsurfacePosition physicalPosition) {
        Objects.requireNonNull(physicalPosition, "physicalPosition");
        Optional<SkyIslandVerticalColumn> column =
                columns.columnAt(physicalPosition.horizontalPosition());
        if (column.isEmpty()) {
            return Optional.empty();
        }
        OptionalDouble depth = column.orElseThrow().depthFractionAt(physicalPosition.physicalY());
        return depth.isEmpty()
                ? Optional.empty()
                : Optional.of(new SkyIslandSubsurfacePosition(
                        physicalPosition.horizontalPosition(),
                        depth.orElseThrow()));
    }
}
