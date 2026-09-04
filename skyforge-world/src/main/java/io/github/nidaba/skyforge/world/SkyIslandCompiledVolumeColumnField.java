package io.github.nidaba.skyforge.world;

import io.github.nidaba.skyforge.kernel.coordinate.Coordinate2;
import io.github.nidaba.skyforge.kernel.evaluation.ReferenceEvaluator;
import io.github.nidaba.skyforge.kernel.field.ScalarField2;
import io.github.nidaba.skyforge.recipes.skyisland.CompiledSkyIslandVolume;
import java.util.Objects;
import java.util.Optional;

/**
 * Adapter exposing an existing compiled Skyforge physical volume as local vertical columns.
 *
 * <p>The compiled volume descriptor owns physical center placement. Callers continue to use
 * authored island-local x/z; this adapter translates those coordinates before evaluating the
 * authoritative compiled upper and underside graphs.
 */
public final class SkyIslandCompiledVolumeColumnField implements SkyIslandVerticalColumnField {
    private final CompiledSkyIslandVolume volume;
    private final ScalarField2 upperSurface;
    private final ScalarField2 undersideSurface;

    public SkyIslandCompiledVolumeColumnField(CompiledSkyIslandVolume volume) {
        this.volume = Objects.requireNonNull(volume, "volume");
        ReferenceEvaluator evaluator = new ReferenceEvaluator();
        this.upperSurface = evaluator.field2(volume.upperSurfaceGraph());
        this.undersideSurface = evaluator.field2(volume.undersideSurfaceGraph());
    }

    public CompiledSkyIslandVolume volume() {
        return volume;
    }

    @Override
    public double nominalRadius() {
        return volume.descriptor().nominalRadius();
    }

    @Override
    public Optional<SkyIslandVerticalColumn> columnAt(SkyIslandLocalPosition position) {
        Objects.requireNonNull(position, "position");
        double worldX = volume.descriptor().centerX() + position.x();
        double worldZ = volume.descriptor().centerZ() + position.z();
        Coordinate2 coordinate = new Coordinate2(worldX, worldZ);
        double upper = upperSurface.sample(coordinate);
        double underside = undersideSurface.sample(coordinate);
        if (!Double.isFinite(upper) || !Double.isFinite(underside)) {
            throw new IllegalStateException("compiled physical surface produced a non-finite column");
        }
        return upper > underside
                ? Optional.of(new SkyIslandVerticalColumn(upper, underside))
                : Optional.empty();
    }
}
