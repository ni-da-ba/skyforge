package io.github.nidaba.skyforge.reference.evidence;

import io.github.nidaba.skyforge.kernel.coordinate.Coordinate2;
import io.github.nidaba.skyforge.kernel.evaluation.ReferenceEvaluator;
import io.github.nidaba.skyforge.kernel.field.ScalarField2;
import io.github.nidaba.skyforge.model.island.IslandDescriptor;
import io.github.nidaba.skyforge.recipes.island.CompiledIsland;
import io.github.nidaba.skyforge.reference.sampling.DeterministicGridSampler;
import io.github.nidaba.skyforge.reference.sampling.GridSpec;
import io.github.nidaba.skyforge.reference.sampling.SamplingOrder;
import io.github.nidaba.skyforge.reference.sampling.ScalarGrid;
import java.util.ArrayDeque;
import java.util.Objects;

/** Generates deterministic numerical evidence from a compiled island. */
public final class IslandEvidenceGenerator {
    /** Half-width of the standard square in nominal-radius units. */
    public static final double STANDARD_HALF_WIDTH_FACTOR = 1.5;

    /** Golden evidence resolution required by the v0.1 baseline. */
    public static final int STANDARD_RESOLUTION = 1024;

    private final ReferenceEvaluator evaluator = new ReferenceEvaluator();
    private final DeterministicGridSampler sampler = new DeterministicGridSampler();

    /** Creates the standard centered square for an island descriptor. */
    public GridSpec standardGrid(IslandDescriptor descriptor) {
        Objects.requireNonNull(descriptor, "descriptor");
        double halfWidth = STANDARD_HALF_WIDTH_FACTOR * descriptor.nominalRadius();
        if (!Double.isFinite(halfWidth)
                || !Double.isFinite(descriptor.centerX() - halfWidth)
                || !Double.isFinite(descriptor.centerX() + halfWidth)
                || !Double.isFinite(descriptor.centerZ() - halfWidth)
                || !Double.isFinite(descriptor.centerZ() + halfWidth)) {
            throw new IllegalArgumentException("standard evidence bounds are not representable");
        }
        return new GridSpec(
                descriptor.centerX() - halfWidth,
                descriptor.centerX() + halfWidth,
                descriptor.centerZ() - halfWidth,
                descriptor.centerZ() + halfWidth,
                STANDARD_RESOLUTION,
                STANDARD_RESOLUTION);
    }

    /** Samples all numerical evidence under the requested evaluation schedule. */
    public IslandEvidence generate(
            CompiledIsland compiledIsland, GridSpec grid, SamplingOrder order) {
        Objects.requireNonNull(compiledIsland, "compiledIsland");
        Objects.requireNonNull(grid, "grid");
        ScalarField2 field = evaluator.field2(compiledIsland.heightGraph());
        ScalarGrid height = sampler.sample(field, grid, Objects.requireNonNull(order, "order"));
        ScalarGrid mask = landMask(height);
        ScalarGrid slope = slope(height);
        CrossSection eastWest = sampleEastWest(field, compiledIsland.descriptor(), grid);
        CrossSection northSouth = sampleNorthSouth(field, compiledIsland.descriptor(), grid);
        return new IslandEvidence(
                compiledIsland,
                height,
                mask,
                slope,
                eastWest,
                northSouth,
                GridStatistics.from(height),
                GridStatistics.from(slope),
                measure(mask));
    }

    private static ScalarGrid landMask(ScalarGrid height) {
        double[] values = height.values();
        for (int index = 0; index < values.length; index++) {
            values[index] = values[index] > 0.0 ? 1.0 : 0.0;
        }
        return new ScalarGrid(height.specification(), values);
    }

    private static ScalarGrid slope(ScalarGrid height) {
        GridSpec grid = height.specification();
        double[] values = new double[grid.sampleCount()];
        for (int z = 0; z < grid.height(); z++) {
            for (int x = 0; x < grid.width(); x++) {
                double derivativeX;
                if (x == 0) {
                    derivativeX = (height.valueAt(1, z) - height.valueAt(0, z)) / grid.spacingX();
                } else if (x == grid.width() - 1) {
                    derivativeX = (height.valueAt(x, z) - height.valueAt(x - 1, z)) / grid.spacingX();
                } else {
                    derivativeX = (height.valueAt(x + 1, z) - height.valueAt(x - 1, z))
                            / (2.0 * grid.spacingX());
                }

                double derivativeZ;
                if (z == 0) {
                    derivativeZ = (height.valueAt(x, 1) - height.valueAt(x, 0)) / grid.spacingZ();
                } else if (z == grid.height() - 1) {
                    derivativeZ = (height.valueAt(x, z) - height.valueAt(x, z - 1)) / grid.spacingZ();
                } else {
                    derivativeZ = (height.valueAt(x, z + 1) - height.valueAt(x, z - 1))
                            / (2.0 * grid.spacingZ());
                }
                values[z * grid.width() + x] = Math.hypot(derivativeX, derivativeZ);
            }
        }
        return new ScalarGrid(grid, values);
    }

    private static CrossSection sampleEastWest(
            ScalarField2 field, IslandDescriptor descriptor, GridSpec grid) {
        double[] coordinates = new double[grid.width()];
        double[] heights = new double[grid.width()];
        for (int index = 0; index < grid.width(); index++) {
            coordinates[index] = grid.xAt(index);
            heights[index] = field.sample(new Coordinate2(coordinates[index], descriptor.centerZ()));
        }
        return new CrossSection(CrossSection.Axis.EAST_WEST, descriptor.centerZ(), coordinates, heights);
    }

    private static CrossSection sampleNorthSouth(
            ScalarField2 field, IslandDescriptor descriptor, GridSpec grid) {
        double[] coordinates = new double[grid.height()];
        double[] heights = new double[grid.height()];
        for (int index = 0; index < grid.height(); index++) {
            coordinates[index] = grid.zAt(index);
            heights[index] = field.sample(new Coordinate2(descriptor.centerX(), coordinates[index]));
        }
        return new CrossSection(CrossSection.Axis.NORTH_SOUTH, descriptor.centerX(), coordinates, heights);
    }

    private static IslandMetrics measure(ScalarGrid mask) {
        GridSpec grid = mask.specification();
        boolean[] land = new boolean[grid.sampleCount()];
        int landCount = 0;
        int boundaryCount = 0;
        double sumX = 0.0;
        double sumZ = 0.0;
        for (int z = 0; z < grid.height(); z++) {
            for (int x = 0; x < grid.width(); x++) {
                int index = z * grid.width() + x;
                if (mask.valueAt(x, z) > 0.0) {
                    land[index] = true;
                    landCount++;
                    sumX += grid.xAt(x);
                    sumZ += grid.zAt(z);
                    if (x == 0 || z == 0 || x == grid.width() - 1 || z == grid.height() - 1) {
                        boundaryCount++;
                    }
                }
            }
        }
        if (landCount == 0) {
            throw new IllegalArgumentException("evidence grid contains no land samples");
        }
        double area = landCount * grid.spacingX() * grid.spacingZ();
        return new IslandMetrics(
                landCount,
                connectedComponents(land, grid.width(), grid.height()),
                boundaryCount,
                area,
                sumX / landCount,
                sumZ / landCount);
    }

    private static int connectedComponents(boolean[] land, int width, int height) {
        boolean[] visited = new boolean[land.length];
        int components = 0;
        for (int index = 0; index < land.length; index++) {
            if (land[index] && !visited[index]) {
                components++;
                visitComponent(index, land, visited, width, height);
            }
        }
        return components;
    }

    private static void visitComponent(
            int start, boolean[] land, boolean[] visited, int width, int height) {
        ArrayDeque<Integer> pending = new ArrayDeque<>();
        pending.add(start);
        visited[start] = true;
        while (!pending.isEmpty()) {
            int index = pending.removeFirst();
            int x = index % width;
            int z = index / width;
            if (x > 0) {
                enqueue(index - 1, land, visited, pending);
            }
            if (x + 1 < width) {
                enqueue(index + 1, land, visited, pending);
            }
            if (z > 0) {
                enqueue(index - width, land, visited, pending);
            }
            if (z + 1 < height) {
                enqueue(index + width, land, visited, pending);
            }
        }
    }

    private static void enqueue(
            int index, boolean[] land, boolean[] visited, ArrayDeque<Integer> pending) {
        if (land[index] && !visited[index]) {
            visited[index] = true;
            pending.add(index);
        }
    }
}
