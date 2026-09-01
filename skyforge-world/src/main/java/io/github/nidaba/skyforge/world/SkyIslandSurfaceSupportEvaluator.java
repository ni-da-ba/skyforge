package io.github.nidaba.skyforge.world;

import io.github.nidaba.skyforge.kernel.coordinate.Coordinate2;
import io.github.nidaba.skyforge.kernel.coordinate.Coordinate3;
import io.github.nidaba.skyforge.kernel.evaluation.ReferenceEvaluator;
import io.github.nidaba.skyforge.kernel.field.ScalarField2;
import io.github.nidaba.skyforge.kernel.field.ScalarField3;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Deterministic backend-neutral evaluator for structure-sized support footprints.
 *
 * <p>Every candidate island is assessed independently. Overlapping X/Z projections from vertically
 * stacked islands therefore produce separate assessments and can never be fused into one phantom
 * foundation.
 */
public final class SkyIslandSurfaceSupportEvaluator {
    private static final double SURFACE_PROBE_DEPTH = 1.0e-6;

    /**
     * Assesses every conservatively relevant catalog volume in stable catalog order.
     *
     * <p>Horizontal bounds are used only for culling. Support itself is decided from the compiled
     * upper-surface and density fields.
     */
    public List<SurfaceSupportAssessment> assess(
            SkyIslandWorldCatalog catalog,
            SurfaceSupportRequirements requirements) {
        Objects.requireNonNull(catalog, "catalog");
        Objects.requireNonNull(requirements, "requirements");
        ArrayList<SurfaceSupportAssessment> result = new ArrayList<>();
        for (SkyIslandWorldVolume volume : catalog.volumes()) {
            if (horizontallyRelevant(volume.bounds(), requirements)) {
                result.add(assess(volume, requirements));
            }
        }
        return List.copyOf(result);
    }

    /** Assesses one island volume without consulting any other surface. */
    public SurfaceSupportAssessment assess(
            SkyIslandWorldVolume volume,
            SurfaceSupportRequirements requirements) {
        Objects.requireNonNull(volume, "volume");
        Objects.requireNonNull(requirements, "requirements");

        ReferenceEvaluator evaluator = new ReferenceEvaluator();
        ScalarField2 upperSurface = evaluator.field2(volume.compiledVolume().upperSurfaceGraph());
        ScalarField3 density = evaluator.field3(volume.compiledVolume().densityGraph());

        double[] xSamples = sampleAxis(
                requirements.minimumX(), requirements.maximumX(), requirements.sampleSpacing());
        double[] zSamples = sampleAxis(
                requirements.minimumZ(), requirements.maximumZ(), requirements.sampleSpacing());
        boolean[][] supported = new boolean[zSamples.length][xSamples.length];
        int supportedSampleCount = 0;
        double minimumSurfaceY = Double.POSITIVE_INFINITY;
        double maximumSurfaceY = Double.NEGATIVE_INFINITY;
        for (int zIndex = 0; zIndex < zSamples.length; zIndex++) {
            for (int xIndex = 0; xIndex < xSamples.length; xIndex++) {
                SupportSample sample = sampleSupport(
                        upperSurface, density, xSamples[xIndex], zSamples[zIndex]);
                if (sample.supported()) {
                    supported[zIndex][xIndex] = true;
                    supportedSampleCount++;
                    minimumSurfaceY = Math.min(minimumSurfaceY, sample.surfaceY());
                    maximumSurfaceY = Math.max(maximumSurfaceY, sample.surfaceY());
                }
            }
        }

        int sampleCount = xSamples.length * zSamples.length;
        double coverageFraction = fraction(supportedSampleCount, sampleCount);
        boolean crossesSurfaceBoundary = supportedSampleCount > 0 && supportedSampleCount < sampleCount;
        int surfaceComponentCount = componentCount(supported);
        boolean coherentSurface = supportedSampleCount > 0 && surfaceComponentCount == 1;

        int clearanceSampleCount = 0;
        int supportedClearanceSampleCount = 0;
        if (requirements.clearance() > 0.0) {
            double[] clearanceX = sampleAxis(
                    requirements.expandedMinimumX(),
                    requirements.expandedMaximumX(),
                    requirements.sampleSpacing());
            double[] clearanceZ = sampleAxis(
                    requirements.expandedMinimumZ(),
                    requirements.expandedMaximumZ(),
                    requirements.sampleSpacing());
            for (double z : clearanceZ) {
                for (double x : clearanceX) {
                    if (insideFootprint(x, z, requirements)) {
                        continue;
                    }
                    clearanceSampleCount++;
                    if (sampleSupport(upperSurface, density, x, z).supported()) {
                        supportedClearanceSampleCount++;
                    }
                }
            }
        }
        double clearanceCoverageFraction = clearanceSampleCount == 0
                ? 1.0
                : fraction(supportedClearanceSampleCount, clearanceSampleCount);

        double heightSpan;
        if (supportedSampleCount == 0) {
            minimumSurfaceY = Double.NaN;
            maximumSurfaceY = Double.NaN;
            heightSpan = Double.NaN;
        } else {
            heightSpan = maximumSurfaceY - minimumSurfaceY;
        }

        boolean accepted = supportedSampleCount > 0
                && coverageFraction >= requirements.minimumCoverageFraction()
                && clearanceCoverageFraction >= requirements.minimumClearanceCoverageFraction()
                && heightSpan <= requirements.maximumHeightSpan()
                && coherentSurface;

        return new SurfaceSupportAssessment(
                volume.id(),
                sampleCount,
                supportedSampleCount,
                coverageFraction,
                clearanceSampleCount,
                supportedClearanceSampleCount,
                clearanceCoverageFraction,
                minimumSurfaceY,
                maximumSurfaceY,
                heightSpan,
                crossesSurfaceBoundary,
                surfaceComponentCount,
                coherentSurface,
                accepted);
    }

    private static boolean horizontallyRelevant(
            WorldBounds bounds,
            SurfaceSupportRequirements requirements) {
        return bounds.maximumX() >= requirements.expandedMinimumX()
                && bounds.minimumX() <= requirements.expandedMaximumX()
                && bounds.maximumZ() >= requirements.expandedMinimumZ()
                && bounds.minimumZ() <= requirements.expandedMaximumZ();
    }

    private static SupportSample sampleSupport(
            ScalarField2 upperSurface,
            ScalarField3 density,
            double x,
            double z) {
        double surfaceY = upperSurface.sample(new Coordinate2(x, z));
        if (!Double.isFinite(surfaceY)) {
            return new SupportSample(false, Double.NaN);
        }
        double supportDensity = density.sample(new Coordinate3(
                x, surfaceY - SURFACE_PROBE_DEPTH, z));
        return new SupportSample(Double.isFinite(supportDensity) && supportDensity > 0.0, surfaceY);
    }

    private static boolean insideFootprint(
            double x,
            double z,
            SurfaceSupportRequirements requirements) {
        return x >= requirements.minimumX()
                && x <= requirements.maximumX()
                && z >= requirements.minimumZ()
                && z <= requirements.maximumZ();
    }

    private static double[] sampleAxis(double minimum, double maximum, double spacing) {
        if (Double.doubleToLongBits(minimum) == Double.doubleToLongBits(maximum)) {
            return new double[] {minimum};
        }
        double intervalCount = Math.ceil((maximum - minimum) / spacing);
        if (!Double.isFinite(intervalCount) || intervalCount > Integer.MAX_VALUE - 1.0) {
            throw new IllegalArgumentException("surface-support sampling axis is too large");
        }
        int intervals = (int) intervalCount;
        double[] result = new double[intervals + 1];
        for (int index = 0; index <= intervals; index++) {
            result[index] = index == intervals ? maximum : minimum + spacing * index;
        }
        return result;
    }

    private static double fraction(int supported, int total) {
        return (double) supported / (double) total;
    }

    private static int componentCount(boolean[][] supported) {
        int zCount = supported.length;
        int xCount = supported[0].length;
        boolean[][] visited = new boolean[zCount][xCount];
        int components = 0;
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        for (int z = 0; z < zCount; z++) {
            for (int x = 0; x < xCount; x++) {
                if (!supported[z][x] || visited[z][x]) {
                    continue;
                }
                components++;
                visited[z][x] = true;
                queue.addLast(z * xCount + x);
                while (!queue.isEmpty()) {
                    int encoded = queue.removeFirst();
                    int currentZ = encoded / xCount;
                    int currentX = encoded % xCount;
                    enqueue(currentX - 1, currentZ, supported, visited, queue, xCount);
                    enqueue(currentX + 1, currentZ, supported, visited, queue, xCount);
                    enqueue(currentX, currentZ - 1, supported, visited, queue, xCount);
                    enqueue(currentX, currentZ + 1, supported, visited, queue, xCount);
                }
            }
        }
        return components;
    }

    private static void enqueue(
            int x,
            int z,
            boolean[][] supported,
            boolean[][] visited,
            ArrayDeque<Integer> queue,
            int xCount) {
        if (z < 0 || z >= supported.length || x < 0 || x >= xCount) {
            return;
        }
        if (!supported[z][x] || visited[z][x]) {
            return;
        }
        visited[z][x] = true;
        queue.addLast(z * xCount + x);
    }

    private record SupportSample(boolean supported, double surfaceY) {}
}
