package io.github.nidaba.skyforge.world;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.OptionalDouble;

/**
 * AUTH-0097 deterministic directional surface-access profiler over exact AUTH-0096 evidence.
 *
 * <p>The profiler samples no new grid and applies no site-role threshold. It inspects exact realized
 * physical support on the same watershed lattice already consumed by AUTH-0096.
 */
public final class SkyIslandSurfaceAccessCapabilityProfiler {

    public SkyIslandSurfaceAccessCapabilityProfile profile(
            SkyIslandSurfaceSiteCapabilityProfile sourceProfile) {
        Objects.requireNonNull(sourceProfile, "sourceProfile");
        SkyIslandWatershedPhysicalSurfaceGrid grid =
                SkyIslandWatershedPhysicalSurfaceGrid.create(
                        sourceProfile.association(), sourceProfile.watershed());

        ArrayList<SkyIslandSurfaceAccessCapabilityCell> cells =
                new ArrayList<>(sourceProfile.cells().size());
        for (SkyIslandSurfaceSiteCapabilityCell sourceCell : sourceProfile.cells()) {
            if (!sourceCell.physicalSurfacePresent()) {
                cells.add(new SkyIslandSurfaceAccessCapabilityCell(sourceCell, List.of()));
                continue;
            }

            int index = sourceCell.watershedCellIndex();
            int x = index % grid.gridSize();
            int z = index / grid.gridSize();
            SkyIslandVerticalColumn anchor = grid.columnOrNull(index);
            if (anchor == null) {
                throw new IllegalStateException(
                        "AUTH-0096 physical surface anchor disappeared from shared physical lattice");
            }

            ArrayList<SkyIslandSurfaceAccessRay> rays =
                    new ArrayList<>(SkyIslandSurfaceAccessDirection.values().length);
            for (SkyIslandSurfaceAccessDirection direction :
                    SkyIslandSurfaceAccessDirection.values()) {
                rays.add(ray(grid, x, z, anchor, direction));
            }
            cells.add(new SkyIslandSurfaceAccessCapabilityCell(sourceCell, rays));
        }

        return new SkyIslandSurfaceAccessCapabilityProfile(sourceProfile, cells);
    }

    private static SkyIslandSurfaceAccessRay ray(
            SkyIslandWatershedPhysicalSurfaceGrid grid,
            int anchorX,
            int anchorZ,
            SkyIslandVerticalColumn anchor,
            SkyIslandSurfaceAccessDirection direction) {
        int available = availableSteps(grid.gridSize(), anchorX, anchorZ, direction);
        double physicalStepDistance = grid.spacing() * direction.latticeStepLength();
        double normalizedStepDistance = physicalStepDistance / grid.radius();

        int consecutiveSupported = 0;
        OptionalDouble firstOpenDistance = OptionalDouble.empty();
        double furthestSupportedDistance = 0.0;
        int transitions = 0;
        double maxRise = 0.0;
        double maxFall = 0.0;
        double gradeSum = 0.0;
        int gradeCount = 0;

        boolean previousSupported = true;
        SkyIslandVerticalColumn previousColumn = anchor;
        boolean[] supported = new boolean[available + 1];
        supported[0] = true;

        for (int step = 1; step <= available; step++) {
            int x = anchorX + direction.xStep() * step;
            int z = anchorZ + direction.zStep() * step;
            SkyIslandVerticalColumn column = grid.columnOrNull(x, z);
            boolean currentSupported = column != null;
            supported[step] = currentSupported;

            if (currentSupported != previousSupported) {
                transitions++;
            }
            if (firstOpenDistance.isEmpty() && !currentSupported) {
                firstOpenDistance = OptionalDouble.of(step * normalizedStepDistance);
            }
            if (firstOpenDistance.isEmpty() && currentSupported) {
                consecutiveSupported++;
            }

            if (currentSupported) {
                furthestSupportedDistance = step * normalizedStepDistance;
                double delta = (column.upperY() - anchor.upperY()) / grid.radius();
                maxRise = Math.max(maxRise, delta);
                maxFall = Math.max(maxFall, -delta);
                if (previousSupported && previousColumn != null) {
                    gradeSum += Math.abs(column.upperY() - previousColumn.upperY())
                            / physicalStepDistance;
                    gradeCount++;
                }
            }

            previousSupported = currentSupported;
            previousColumn = column;
        }

        int openTailSteps = 0;
        for (int step = available; step >= 1 && !supported[step]; step--) {
            openTailSteps++;
        }

        return new SkyIslandSurfaceAccessRay(
                direction,
                available,
                normalizedStepDistance,
                consecutiveSupported,
                firstOpenDistance,
                furthestSupportedDistance,
                openTailSteps,
                openTailSteps * normalizedStepDistance,
                transitions,
                maxRise,
                maxFall,
                gradeCount == 0
                        ? OptionalDouble.empty()
                        : OptionalDouble.of(gradeSum / gradeCount));
    }

    private static int availableSteps(
            int gridSize,
            int x,
            int z,
            SkyIslandSurfaceAccessDirection direction) {
        int xSteps = direction.xStep() > 0
                ? gridSize - 1 - x
                : direction.xStep() < 0 ? x : Integer.MAX_VALUE;
        int zSteps = direction.zStep() > 0
                ? gridSize - 1 - z
                : direction.zStep() < 0 ? z : Integer.MAX_VALUE;
        return Math.min(xSteps, zSteps);
    }
}
