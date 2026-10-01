package io.github.nidaba.skyforge.reference.evidence;

import io.github.nidaba.skyforge.kernel.evaluation.ReferenceEvaluator;
import io.github.nidaba.skyforge.kernel.field.ScalarField2;
import io.github.nidaba.skyforge.kernel.field.ScalarField3;
import io.github.nidaba.skyforge.recipes.skyisland.CompiledSkyIslandVolume;
import io.github.nidaba.skyforge.reference.sampling.DeterministicGridSampler;
import io.github.nidaba.skyforge.reference.sampling.DeterministicVolumeSampler;
import io.github.nidaba.skyforge.reference.sampling.GridSpec;
import io.github.nidaba.skyforge.reference.sampling.OccupancyVolumeGrid;
import io.github.nidaba.skyforge.reference.sampling.SamplingOrder;
import io.github.nidaba.skyforge.reference.sampling.ScalarGrid;
import io.github.nidaba.skyforge.reference.sampling.ScalarVolumeGrid;
import io.github.nidaba.skyforge.reference.sampling.VolumeGridSpec;
import java.util.Objects;

/** Generates deterministic 3D evidence from a compiled suspended sky-island volume. */
public final class SuspendedVolumeEvidenceGenerator {
    private final ReferenceEvaluator evaluator = new ReferenceEvaluator();
    private final DeterministicVolumeSampler volumeSampler = new DeterministicVolumeSampler();
    private final DeterministicGridSampler surfaceSampler = new DeterministicGridSampler();

    /**
     * Samples the suspended-volume contract and derives all exact numerical evidence.
     *
     * <p>The canonical forward path samples the X/Z upper and underside surfaces once and then
     * evaluates the exact density identity {@code min(upper - y, y - underside)} for each Y level.
     * This is bit-equivalent to the recipe density graph while avoiding redundant reevaluation of
     * Y-invariant surface subgraphs. Non-forward schedules retain direct density-graph sampling so
     * schedule-invariance validation continues to exercise the normative graph evaluator.
     */
    public SuspendedVolumeEvidence generate(
            CompiledSkyIslandVolume compiled,
            VolumeGridSpec grid,
            SamplingOrder order) {
        Objects.requireNonNull(compiled, "compiled");
        Objects.requireNonNull(grid, "grid");
        Objects.requireNonNull(order, "order");

        GridSpec horizontalGrid = new GridSpec(
                grid.minimumX(),
                grid.maximumX(),
                grid.minimumZ(),
                grid.maximumZ(),
                grid.xSamples(),
                grid.zSamples());
        ScalarField2 upperField = evaluator.field2(compiled.upperSurfaceGraph());
        ScalarField2 undersideField = evaluator.field2(compiled.undersideSurfaceGraph());
        ScalarGrid upper = surfaceSampler.sample(upperField, horizontalGrid, order);
        ScalarGrid underside = surfaceSampler.sample(undersideField, horizontalGrid, order);

        ScalarVolumeGrid density;
        if (order == SamplingOrder.FORWARD) {
            density = forwardDensityFromSurfaces(upper, underside, grid);
        } else {
            ScalarField3 densityField = evaluator.field3(compiled.densityGraph());
            density = volumeSampler.sample(densityField, grid, order);
        }
        OccupancyVolumeGrid occupancy = OccupancyVolumeGrid.fromDensity(density);

        int suspensionY = nearestIndex(
                compiled.descriptor().suspensionElevation(),
                grid.minimumY(),
                grid.spacingY(),
                grid.ySamples());
        ScalarGrid horizontalDensity = horizontalSlice(density, suspensionY, horizontalGrid);
        int centerX = nearestIndex(
                compiled.descriptor().centerX(),
                grid.minimumX(),
                grid.spacingX(),
                grid.xSamples());
        int centerZ = nearestIndex(
                compiled.descriptor().centerZ(),
                grid.minimumZ(),
                grid.spacingZ(),
                grid.zSamples());
        VolumeSlice eastWest = eastWestSlice(density, centerZ);
        VolumeSlice northSouth = northSouthSlice(density, centerX);
        return new SuspendedVolumeEvidence(
                compiled,
                density,
                occupancy,
                upper,
                underside,
                horizontalDensity,
                eastWest,
                northSouth,
                measure(occupancy));
    }

    private static ScalarVolumeGrid forwardDensityFromSurfaces(
            ScalarGrid upper,
            ScalarGrid underside,
            VolumeGridSpec grid) {
        double[] upperValues = upper.values();
        double[] undersideValues = underside.values();
        double[] density = new double[grid.sampleCount()];
        int width = grid.xSamples();
        int horizontalLayer = width * grid.zSamples();
        for (int y = 0; y < grid.ySamples(); y++) {
            double worldY = grid.yAt(y);
            int layerOffset = y * horizontalLayer;
            for (int z = 0; z < grid.zSamples(); z++) {
                int rowOffset = z * width;
                for (int x = 0; x < width; x++) {
                    int horizontal = rowOffset + x;
                    density[layerOffset + horizontal] = Math.min(
                            upperValues[horizontal] - worldY,
                            worldY - undersideValues[horizontal]);
                }
            }
        }
        return new ScalarVolumeGrid(grid, density);
    }

    private static ScalarGrid horizontalSlice(
            ScalarVolumeGrid density, int yIndex, GridSpec horizontalGrid) {
        VolumeGridSpec grid = density.specification();
        double[] values = new double[horizontalGrid.sampleCount()];
        for (int z = 0; z < grid.zSamples(); z++) {
            for (int x = 0; x < grid.xSamples(); x++) {
                values[z * grid.xSamples() + x] = density.valueAt(x, yIndex, z);
            }
        }
        return new ScalarGrid(horizontalGrid, values);
    }

    private static VolumeSlice eastWestSlice(ScalarVolumeGrid density, int zIndex) {
        VolumeGridSpec grid = density.specification();
        double[] horizontal = coordinates(grid.xSamples(), grid::xAt);
        double[] vertical = coordinates(grid.ySamples(), grid::yAt);
        double[] values = new double[grid.xSamples() * grid.ySamples()];
        for (int y = 0; y < grid.ySamples(); y++) {
            for (int x = 0; x < grid.xSamples(); x++) {
                values[y * grid.xSamples() + x] = density.valueAt(x, y, zIndex);
            }
        }
        return new VolumeSlice(
                VolumeSlice.Axis.EAST_WEST, grid.zAt(zIndex), horizontal, vertical, values);
    }

    private static VolumeSlice northSouthSlice(ScalarVolumeGrid density, int xIndex) {
        VolumeGridSpec grid = density.specification();
        double[] horizontal = coordinates(grid.zSamples(), grid::zAt);
        double[] vertical = coordinates(grid.ySamples(), grid::yAt);
        double[] values = new double[grid.zSamples() * grid.ySamples()];
        for (int y = 0; y < grid.ySamples(); y++) {
            for (int z = 0; z < grid.zSamples(); z++) {
                values[y * grid.zSamples() + z] = density.valueAt(xIndex, y, z);
            }
        }
        return new VolumeSlice(
                VolumeSlice.Axis.NORTH_SOUTH, grid.xAt(xIndex), horizontal, vertical, values);
    }

    private static double[] coordinates(int count, CoordinateLookup lookup) {
        double[] result = new double[count];
        for (int index = 0; index < count; index++) {
            result[index] = lookup.coordinateAt(index);
        }
        return result;
    }

    private static int nearestIndex(
            double coordinate, double minimum, double spacing, int samples) {
        long rounded = Math.round((coordinate - minimum) / spacing);
        return (int) Math.max(0L, Math.min(samples - 1L, rounded));
    }

    private static VolumeMetrics measure(OccupancyVolumeGrid occupancy) {
        VolumeGridSpec grid = occupancy.specification();
        byte[] solid = occupancy.values();
        int solidCount = 0;
        double sumX = 0.0;
        double sumY = 0.0;
        double sumZ = 0.0;
        int minimumX = grid.xSamples();
        int maximumX = -1;
        int minimumY = grid.ySamples();
        int maximumY = -1;
        int minimumZ = grid.zSamples();
        int maximumZ = -1;
        int minimumXContacts = 0;
        int maximumXContacts = 0;
        int minimumYContacts = 0;
        int maximumYContacts = 0;
        int minimumZContacts = 0;
        int maximumZContacts = 0;

        for (int y = 0; y < grid.ySamples(); y++) {
            for (int z = 0; z < grid.zSamples(); z++) {
                for (int x = 0; x < grid.xSamples(); x++) {
                    if (solid[grid.linearIndex(x, y, z)] == 0) {
                        continue;
                    }
                    solidCount++;
                    sumX += grid.xAt(x);
                    sumY += grid.yAt(y);
                    sumZ += grid.zAt(z);
                    minimumX = Math.min(minimumX, x);
                    maximumX = Math.max(maximumX, x);
                    minimumY = Math.min(minimumY, y);
                    maximumY = Math.max(maximumY, y);
                    minimumZ = Math.min(minimumZ, z);
                    maximumZ = Math.max(maximumZ, z);
                    minimumXContacts += x == 0 ? 1 : 0;
                    maximumXContacts += x == grid.xSamples() - 1 ? 1 : 0;
                    minimumYContacts += y == 0 ? 1 : 0;
                    maximumYContacts += y == grid.ySamples() - 1 ? 1 : 0;
                    minimumZContacts += z == 0 ? 1 : 0;
                    maximumZContacts += z == grid.zSamples() - 1 ? 1 : 0;
                }
            }
        }
        if (solidCount == 0) {
            throw new IllegalArgumentException("evidence volume contains no solid samples");
        }

        VolumeMetrics.Bounds bounds = new VolumeMetrics.Bounds(
                grid.xAt(minimumX),
                grid.xAt(maximumX),
                grid.yAt(minimumY),
                grid.yAt(maximumY),
                grid.zAt(minimumZ),
                grid.zAt(maximumZ));
        VolumeMetrics.FaceContacts contacts = new VolumeMetrics.FaceContacts(
                minimumXContacts,
                maximumXContacts,
                minimumYContacts,
                maximumYContacts,
                minimumZContacts,
                maximumZContacts);
        VolumeMetrics.AirClearance clearance = new VolumeMetrics.AirClearance(
                bounds.minimumX() - grid.minimumX(),
                grid.maximumX() - bounds.maximumX(),
                bounds.minimumY() - grid.minimumY(),
                grid.maximumY() - bounds.maximumY(),
                bounds.minimumZ() - grid.minimumZ(),
                grid.maximumZ() - bounds.maximumZ());
        double cellVolume = grid.spacingX() * grid.spacingY() * grid.spacingZ();
        return new VolumeMetrics(
                solidCount,
                connectedComponents(solid, grid),
                solidCount * cellVolume,
                sumX / solidCount,
                sumY / solidCount,
                sumZ / solidCount,
                bounds,
                contacts,
                clearance);
    }

    private static int connectedComponents(byte[] solid, VolumeGridSpec grid) {
        boolean[] visited = new boolean[solid.length];
        int[] queue = new int[solid.length];
        int components = 0;
        int layer = grid.xSamples() * grid.zSamples();
        for (int start = 0; start < solid.length; start++) {
            if (solid[start] == 0 || visited[start]) {
                continue;
            }
            components++;
            int head = 0;
            int tail = 0;
            queue[tail++] = start;
            visited[start] = true;
            while (head < tail) {
                int index = queue[head++];
                int y = index / layer;
                int horizontal = index % layer;
                int z = horizontal / grid.xSamples();
                int x = horizontal % grid.xSamples();
                if (x > 0) {
                    tail = enqueue(index - 1, solid, visited, queue, tail);
                }
                if (x + 1 < grid.xSamples()) {
                    tail = enqueue(index + 1, solid, visited, queue, tail);
                }
                if (z > 0) {
                    tail = enqueue(index - grid.xSamples(), solid, visited, queue, tail);
                }
                if (z + 1 < grid.zSamples()) {
                    tail = enqueue(index + grid.xSamples(), solid, visited, queue, tail);
                }
                if (y > 0) {
                    tail = enqueue(index - layer, solid, visited, queue, tail);
                }
                if (y + 1 < grid.ySamples()) {
                    tail = enqueue(index + layer, solid, visited, queue, tail);
                }
            }
        }
        return components;
    }

    private static int enqueue(
            int index, byte[] solid, boolean[] visited, int[] queue, int tail) {
        if (solid[index] == 1 && !visited[index]) {
            visited[index] = true;
            queue[tail++] = index;
        }
        return tail;
    }

    @FunctionalInterface
    private interface CoordinateLookup {
        double coordinateAt(int index);
    }
}
