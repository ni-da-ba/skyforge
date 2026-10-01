package io.github.nidaba.skyforge.world;

import java.util.Objects;

/**
 * Shared exact-realization physical sampling over the accepted AUTH watershed square lattice.
 *
 * <p>This is an implementation detail for Authorship capability profilers. It deliberately samples
 * the same grid size/spacing already owned by the supplied watershed plan rather than defining a
 * second structure/access grid.
 */
final class SkyIslandWatershedPhysicalSurfaceGrid {
    private final SkyIslandAuthoredRealizationAssociation association;
    private final SkyIslandWatershedPlan watershed;
    private final double radius;
    private final SkyIslandVerticalColumn[] columns;

    private SkyIslandWatershedPhysicalSurfaceGrid(
            SkyIslandAuthoredRealizationAssociation association,
            SkyIslandWatershedPlan watershed) {
        this.association = Objects.requireNonNull(association, "association");
        this.watershed = Objects.requireNonNull(watershed, "watershed");
        if (!watershed.descriptor().equals(association.authoredDescriptor())) {
            throw new IllegalArgumentException(
                    "physical watershed lattice must belong to the exact associated authored island");
        }

        this.radius = association.authoredDescriptor().nominalRadius();
        double expectedSpan = 2.0 * radius;
        double actualSpan = watershed.spacing() * (watershed.gridSize() - 1.0);
        double tolerance = Math.max(1.0, expectedSpan) * 1.0e-12;
        if (Math.abs(expectedSpan - actualSpan) > tolerance) {
            throw new IllegalArgumentException(
                    "accepted watershed lattice must span exactly the authored nominal diameter");
        }

        SkyIslandCompiledVolumeColumnField physical =
                new SkyIslandCompiledVolumeColumnField(
                        association.realizedVolume().compiledVolume());
        int gridSize = watershed.gridSize();
        this.columns = new SkyIslandVerticalColumn[Math.multiplyExact(gridSize, gridSize)];
        for (int z = 0; z < gridSize; z++) {
            for (int x = 0; x < gridSize; x++) {
                int index = index(x, z);
                columns[index] = physical.columnAt(position(x, z)).orElse(null);
            }
        }
    }

    static SkyIslandWatershedPhysicalSurfaceGrid create(
            SkyIslandAuthoredRealizationAssociation association,
            SkyIslandWatershedPlan watershed) {
        return new SkyIslandWatershedPhysicalSurfaceGrid(association, watershed);
    }

    SkyIslandAuthoredRealizationAssociation association() {
        return association;
    }

    SkyIslandWatershedPlan watershed() {
        return watershed;
    }

    int gridSize() {
        return watershed.gridSize();
    }

    double spacing() {
        return watershed.spacing();
    }

    double radius() {
        return radius;
    }

    SkyIslandVerticalColumn columnOrNull(int index) {
        if (index < 0 || index >= columns.length) {
            throw new IndexOutOfBoundsException("physical lattice index outside grid");
        }
        return columns[index];
    }

    SkyIslandVerticalColumn columnOrNull(int x, int z) {
        requireCoordinates(x, z);
        return columns[index(x, z)];
    }

    SkyIslandLocalPosition position(int x, int z) {
        requireCoordinates(x, z);
        double localX = x == gridSize() - 1
                ? radius
                : -radius + x * spacing();
        double localZ = z == gridSize() - 1
                ? radius
                : -radius + z * spacing();
        return new SkyIslandLocalPosition(localX, localZ);
    }

    private int index(int x, int z) {
        return z * gridSize() + x;
    }

    private void requireCoordinates(int x, int z) {
        if (x < 0 || z < 0 || x >= gridSize() || z >= gridSize()) {
            throw new IndexOutOfBoundsException("physical lattice coordinates outside grid");
        }
    }
}
