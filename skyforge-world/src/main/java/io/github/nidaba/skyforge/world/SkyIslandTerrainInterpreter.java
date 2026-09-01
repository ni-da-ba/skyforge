package io.github.nidaba.skyforge.world;

import io.github.nidaba.skyforge.kernel.coordinate.Coordinate2;
import io.github.nidaba.skyforge.kernel.coordinate.Coordinate3;
import io.github.nidaba.skyforge.kernel.evaluation.ReferenceEvaluator;
import io.github.nidaba.skyforge.kernel.field.ScalarField2;
import io.github.nidaba.skyforge.kernel.field.ScalarField3;
import io.github.nidaba.skyforge.recipes.skyisland.CompiledSkyIslandVolume;
import java.util.Objects;

/**
 * Continuous backend-neutral interpreter from compiled geometry to terrain/material roles.
 *
 * <p>Classification is derived from the authoritative compiled density plus upper and underside
 * surfaces. It does not inspect voxel neighbors and is therefore independent of backend sampling
 * resolution, tile size, or generation order.
 */
public final class SkyIslandTerrainInterpreter {
    private final SkyIslandTerrainProfile profile;
    private final ScalarField2 upperSurface;
    private final ScalarField2 undersideSurface;
    private final ScalarField3 density;

    public SkyIslandTerrainInterpreter(
            CompiledSkyIslandVolume volume,
            SkyIslandTerrainProfile profile) {
        Objects.requireNonNull(volume, "volume");
        this.profile = Objects.requireNonNull(profile, "profile");
        ReferenceEvaluator evaluator = new ReferenceEvaluator();
        this.upperSurface = evaluator.field2(volume.upperSurfaceGraph());
        this.undersideSurface = evaluator.field2(volume.undersideSurfaceGraph());
        this.density = evaluator.field3(volume.densityGraph());
    }

    /** Returns the authoritative compiled upper-surface height at one finite horizontal coordinate. */
    public double upperSurfaceHeight(double x, double z) {
        Coordinate2 point = new Coordinate2(x, z);
        double value = upperSurface.sample(point);
        if (!Double.isFinite(value)) {
            throw new IllegalStateException("compiled upper surface produced a non-finite value");
        }
        return value;
    }

    /** Returns the authoritative compiled underside height at one finite horizontal coordinate. */
    public double undersideSurfaceHeight(double x, double z) {
        Coordinate2 point = new Coordinate2(x, z);
        double value = undersideSurface.sample(point);
        if (!Double.isFinite(value)) {
            throw new IllegalStateException("compiled underside surface produced a non-finite value");
        }
        return value;
    }

    /** Returns the authoritative signed density at one finite world-space coordinate. */
    public double density(double x, double y, double z) {
        double value = density.sample(new Coordinate3(x, y, z));
        if (!Double.isFinite(value)) {
            throw new IllegalStateException("compiled density produced a non-finite value");
        }
        return value;
    }

    /** Classifies one finite world-space coordinate. */
    public SkyIslandTerrainSemantic classify(Coordinate3 point) {
        Objects.requireNonNull(point, "point");
        if (!(density.sample(point) > 0.0)) {
            return SkyIslandTerrainSemantic.AIR;
        }

        double upper = upperSurfaceHeight(point.x(), point.z());
        double underside = undersideSurfaceHeight(point.x(), point.z());
        double depthFromUpper = upper - point.y();
        double depthFromUnderside = point.y() - underside;
        if (!(depthFromUpper > 0.0) || !(depthFromUnderside > 0.0)) {
            throw new IllegalStateException(
                    "compiled density is positive outside the compiled upper/underside surfaces");
        }

        double columnThickness = upper - underside;
        if (columnThickness <= profile.edgeMaximumColumnThickness()) {
            return SkyIslandTerrainSemantic.EDGE_SHELL;
        }
        if (depthFromUpper <= profile.surfaceMantleDepth()) {
            return SkyIslandTerrainSemantic.SURFACE_MANTLE;
        }
        if (depthFromUnderside <= profile.undersideShellDepth()) {
            return SkyIslandTerrainSemantic.UNDERSIDE_SHELL;
        }
        if (Math.min(depthFromUpper, depthFromUnderside) <= profile.shallowInteriorDepth()) {
            return SkyIslandTerrainSemantic.SHALLOW_INTERIOR;
        }
        return SkyIslandTerrainSemantic.DEEP_MASS;
    }

    /** Convenience overload for scalar world coordinates. */
    public SkyIslandTerrainSemantic classify(double x, double y, double z) {
        return classify(new Coordinate3(x, y, z));
    }
}
