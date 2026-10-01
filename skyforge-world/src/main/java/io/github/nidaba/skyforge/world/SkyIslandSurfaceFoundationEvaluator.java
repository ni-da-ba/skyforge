package io.github.nidaba.skyforge.world;

import io.github.nidaba.skyforge.kernel.coordinate.Coordinate2;
import io.github.nidaba.skyforge.kernel.evaluation.ReferenceEvaluator;
import io.github.nidaba.skyforge.kernel.field.ScalarField2;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Deterministic backend-neutral feasibility evaluator for bounded fill-only foundations.
 *
 * <p>A foundation may only be accepted when every sampled footprint point is already supported by
 * one independently compiled island, no sampled surface rises above the caller-authorized natural
 * surface ceiling, at least one footprint column actually requires fill below the requested
 * foundation plane, and the deepest required fill remains within the caller-owned bound. Empty
 * space between rectangles in a composite footprint is never interpreted as terrain to fill.
 */
public final class SkyIslandSurfaceFoundationEvaluator {
    private static final double VERTICAL_EPSILON = 1.0e-9;
    private final SkyIslandSurfaceSupportEvaluator supportEvaluator = new SkyIslandSurfaceSupportEvaluator();

    /** Assesses every conservatively relevant catalog volume in stable catalog order. */
    public List<SurfaceFoundationAssessment> assess(
            SkyIslandWorldCatalog catalog,
            SurfaceFoundationRequirements requirements) {
        Objects.requireNonNull(catalog, "catalog");
        Objects.requireNonNull(requirements, "requirements");
        ArrayList<SurfaceFoundationAssessment> result = new ArrayList<>();
        for (SurfaceSupportAssessment supportAssessment :
                supportEvaluator.assess(catalog, requirements.supportRequirements())) {
            SkyIslandWorldVolume volume = catalog.volumes().stream()
                    .filter(candidate -> candidate.id().equals(supportAssessment.supportingVolumeId()))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("surface-support assessment references unknown volume"));
            result.add(assess(volume, requirements, supportAssessment));
        }
        return List.copyOf(result);
    }

    /** Assesses one island volume without consulting or combining any other surface. */
    public SurfaceFoundationAssessment assess(
            SkyIslandWorldVolume volume,
            SurfaceFoundationRequirements requirements) {
        Objects.requireNonNull(volume, "volume");
        Objects.requireNonNull(requirements, "requirements");
        SurfaceSupportAssessment supportAssessment =
                supportEvaluator.assess(volume, requirements.supportRequirements());
        return assess(volume, requirements, supportAssessment);
    }

    private static SurfaceFoundationAssessment assess(
            SkyIslandWorldVolume volume,
            SurfaceFoundationRequirements requirements,
            SurfaceSupportAssessment supportAssessment) {
        if (supportAssessment.supportedSampleCount() != supportAssessment.sampleCount()) {
            return new SurfaceFoundationAssessment(supportAssessment, 0, 0, 0.0, false);
        }

        ReferenceEvaluator evaluator = new ReferenceEvaluator();
        ScalarField2 upperSurface = evaluator.field2(volume.compiledVolume().upperSurfaceGraph());
        SurfaceSupportRequirements supportRequirements = requirements.supportRequirements();
        SurfaceFootprint footprint = supportRequirements.footprint();
        double[] xSamples = sampleAxis(
                supportRequirements.minimumX(),
                supportRequirements.maximumX(),
                supportRequirements.sampleSpacing());
        double[] zSamples = sampleAxis(
                supportRequirements.minimumZ(),
                supportRequirements.maximumZ(),
                supportRequirements.sampleSpacing());

        int fillSampleCount = 0;
        int surfaceAboveFoundationSampleCount = 0;
        double maximumRequiredFillDepth = 0.0;
        for (double z : zSamples) {
            for (double x : xSamples) {
                if (!footprint.contains(x, z)) {
                    continue;
                }
                double surfaceY = upperSurface.sample(new Coordinate2(x, z));
                if (!Double.isFinite(surfaceY)) {
                    throw new IllegalStateException("fully supported footprint produced a non-finite upper surface");
                }
                if (surfaceY > requirements.maximumSurfaceY() + VERTICAL_EPSILON) {
                    surfaceAboveFoundationSampleCount++;
                    continue;
                }

                double fillDepth = requirements.foundationTopY() - surfaceY;
                if (fillDepth > VERTICAL_EPSILON) {
                    fillSampleCount++;
                    maximumRequiredFillDepth = Math.max(maximumRequiredFillDepth, fillDepth);
                }
            }
        }

        boolean accepted = supportAssessment.accepted()
                && supportAssessment.coverageFraction() == 1.0
                && surfaceAboveFoundationSampleCount == 0
                && fillSampleCount > 0
                && maximumRequiredFillDepth <= requirements.maximumFillDepth();
        return new SurfaceFoundationAssessment(
                supportAssessment,
                fillSampleCount,
                surfaceAboveFoundationSampleCount,
                maximumRequiredFillDepth,
                accepted);
    }

    private static double[] sampleAxis(double minimum, double maximum, double spacing) {
        if (Double.doubleToLongBits(minimum) == Double.doubleToLongBits(maximum)) {
            return new double[] {minimum};
        }
        double intervalCount = Math.ceil((maximum - minimum) / spacing);
        if (!Double.isFinite(intervalCount) || intervalCount > Integer.MAX_VALUE - 1.0) {
            throw new IllegalArgumentException("surface-foundation sampling axis is too large");
        }
        int intervals = (int) intervalCount;
        double[] result = new double[intervals + 1];
        for (int index = 0; index <= intervals; index++) {
            result[index] = index == intervals ? maximum : minimum + spacing * index;
        }
        return result;
    }
}
