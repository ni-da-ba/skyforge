package io.github.nidaba.skyforge.world;

import io.github.nidaba.skyforge.kernel.coordinate.Coordinate3;
import io.github.nidaba.skyforge.kernel.evaluation.ReferenceEvaluator;
import io.github.nidaba.skyforge.kernel.field.ScalarField3;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Deliberately simple backend that samples world-catalog density graphs on a global lattice.
 *
 * <p>Tiles own disjoint lattice-index ranges. Catalog queries are conservative closed world boxes,
 * so an island may be considered by adjacent tiles without any sampled voxel being owned twice.
 */
public final class ReferenceTiledSkyIslandBackend {
    private final ReferenceEvaluator evaluator = new ReferenceEvaluator();

    /** Realizes the whole grid from one catalog query. */
    public WorldRegionOccupancy realizeMonolithic(
            SkyIslandWorldCatalog catalog, WorldSampleGrid grid) {
        Objects.requireNonNull(catalog, "catalog");
        Objects.requireNonNull(grid, "grid");
        List<SkyIslandWorldVolume> candidates = catalog.query(grid.bounds());
        byte[] occupancy = new byte[grid.sampleCount()];
        Map<SkyIslandWorldVolumeId, ScalarField3> fields = compileFields(candidates);
        sampleRange(grid, occupancy, candidates, fields, 0, grid.xSamples(), 0, grid.zSamples());
        return new WorldRegionOccupancy(grid, occupancy, 1, candidates.size());
    }

    /**
     * Realizes the same lattice through independently queried chunk-like X/Z tiles.
     *
     * <p>{@code tileXSamples} and {@code tileZSamples} are sample counts rather than world lengths,
     * keeping tile ownership exact even when the final edge tile is partial.
     */
    public WorldRegionOccupancy realizeTiled(
            SkyIslandWorldCatalog catalog,
            WorldSampleGrid grid,
            int tileXSamples,
            int tileZSamples) {
        Objects.requireNonNull(catalog, "catalog");
        Objects.requireNonNull(grid, "grid");
        requirePositive("tileXSamples", tileXSamples);
        requirePositive("tileZSamples", tileZSamples);

        byte[] occupancy = new byte[grid.sampleCount()];
        Map<SkyIslandWorldVolumeId, ScalarField3> fieldCache = new HashMap<>();
        int spatialQueries = 0;
        int candidateReferences = 0;

        for (int zStart = 0; zStart < grid.zSamples(); zStart += tileZSamples) {
            int zEnd = Math.min(grid.zSamples(), zStart + tileZSamples);
            for (int xStart = 0; xStart < grid.xSamples(); xStart += tileXSamples) {
                int xEnd = Math.min(grid.xSamples(), xStart + tileXSamples);
                WorldBounds query = new WorldBounds(
                        grid.xAt(xStart),
                        grid.xAt(xEnd - 1),
                        grid.minimumY(),
                        grid.maximumY(),
                        grid.zAt(zStart),
                        grid.zAt(zEnd - 1));
                List<SkyIslandWorldVolume> candidates = catalog.query(query);
                spatialQueries++;
                candidateReferences += candidates.size();
                for (SkyIslandWorldVolume candidate : candidates) {
                    fieldCache.computeIfAbsent(
                            candidate.id(),
                            ignored -> evaluator.field3(candidate.compiledVolume().densityGraph()));
                }
                sampleRange(
                        grid,
                        occupancy,
                        candidates,
                        fieldCache,
                        xStart,
                        xEnd,
                        zStart,
                        zEnd);
            }
        }
        return new WorldRegionOccupancy(grid, occupancy, spatialQueries, candidateReferences);
    }

    private static void sampleRange(
            WorldSampleGrid grid,
            byte[] occupancy,
            List<SkyIslandWorldVolume> candidates,
            Map<SkyIslandWorldVolumeId, ScalarField3> fields,
            int xStart,
            int xEnd,
            int zStart,
            int zEnd) {
        for (int y = 0; y < grid.ySamples(); y++) {
            double worldY = grid.yAt(y);
            for (int z = zStart; z < zEnd; z++) {
                double worldZ = grid.zAt(z);
                for (int x = xStart; x < xEnd; x++) {
                    double worldX = grid.xAt(x);
                    Coordinate3 point = new Coordinate3(worldX, worldY, worldZ);
                    for (SkyIslandWorldVolume candidate : candidates) {
                        if (fields.get(candidate.id()).sample(point) > 0.0) {
                            occupancy[grid.linearIndex(x, y, z)] = 1;
                            break;
                        }
                    }
                }
            }
        }
    }

    private Map<SkyIslandWorldVolumeId, ScalarField3> compileFields(
            List<SkyIslandWorldVolume> candidates) {
        Map<SkyIslandWorldVolumeId, ScalarField3> result = new HashMap<>();
        for (SkyIslandWorldVolume candidate : candidates) {
            result.put(candidate.id(), evaluator.field3(candidate.compiledVolume().densityGraph()));
        }
        return result;
    }

    private static void requirePositive(String property, int value) {
        if (value <= 0) {
            throw new IllegalArgumentException(property + " must be positive");
        }
    }
}
