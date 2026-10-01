package io.github.nidaba.skyforge.world;

import io.github.nidaba.skyforge.kernel.coordinate.Coordinate3;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Reference backend that applies terrain semantics over independently queried world tiles. */
public final class ReferenceTiledSkyIslandTerrainBackend {
    public WorldRegionTerrain realizeMonolithic(
            SkyIslandWorldCatalog catalog,
            WorldSampleGrid grid,
            SkyIslandTerrainProfile profile) {
        Objects.requireNonNull(catalog, "catalog");
        Objects.requireNonNull(grid, "grid");
        Objects.requireNonNull(profile, "profile");
        List<SkyIslandWorldVolume> candidates = catalog.query(grid.bounds());
        byte[] semantics = new byte[grid.sampleCount()];
        Map<SkyIslandWorldVolumeId, SkyIslandTerrainInterpreter> interpreters =
                compileInterpreters(candidates, profile);
        sampleRange(grid, semantics, candidates, interpreters, 0, grid.xSamples(), 0, grid.zSamples());
        return new WorldRegionTerrain(grid, semantics, 1, candidates.size());
    }

    public WorldRegionTerrain realizeTiled(
            SkyIslandWorldCatalog catalog,
            WorldSampleGrid grid,
            SkyIslandTerrainProfile profile,
            int tileXSamples,
            int tileZSamples) {
        Objects.requireNonNull(catalog, "catalog");
        Objects.requireNonNull(grid, "grid");
        Objects.requireNonNull(profile, "profile");
        requirePositive("tileXSamples", tileXSamples);
        requirePositive("tileZSamples", tileZSamples);
        byte[] semantics = new byte[grid.sampleCount()];
        Map<SkyIslandWorldVolumeId, SkyIslandTerrainInterpreter> cache = new HashMap<>();
        int spatialQueries = 0;
        int candidateReferences = 0;
        for (int zStart = 0; zStart < grid.zSamples(); zStart += tileZSamples) {
            int zEnd = Math.min(grid.zSamples(), zStart + tileZSamples);
            for (int xStart = 0; xStart < grid.xSamples(); xStart += tileXSamples) {
                int xEnd = Math.min(grid.xSamples(), xStart + tileXSamples);
                WorldBounds query = new WorldBounds(
                        grid.xAt(xStart), grid.xAt(xEnd - 1),
                        grid.minimumY(), grid.maximumY(),
                        grid.zAt(zStart), grid.zAt(zEnd - 1));
                List<SkyIslandWorldVolume> candidates = catalog.query(query);
                spatialQueries++;
                candidateReferences += candidates.size();
                for (SkyIslandWorldVolume candidate : candidates) {
                    cache.computeIfAbsent(candidate.id(), key ->
                            new SkyIslandTerrainInterpreter(candidate.compiledVolume(), profile));
                }
                sampleRange(grid, semantics, candidates, cache, xStart, xEnd, zStart, zEnd);
            }
        }
        return new WorldRegionTerrain(grid, semantics, spatialQueries, candidateReferences);
    }

    private static void sampleRange(
            WorldSampleGrid grid,
            byte[] semantics,
            List<SkyIslandWorldVolume> candidates,
            Map<SkyIslandWorldVolumeId, SkyIslandTerrainInterpreter> interpreters,
            int xStart,
            int xEnd,
            int zStart,
            int zEnd) {
        for (int y = 0; y < grid.ySamples(); y++) {
            double worldY = grid.yAt(y);
            for (int z = zStart; z < zEnd; z++) {
                double worldZ = grid.zAt(z);
                for (int x = xStart; x < xEnd; x++) {
                    Coordinate3 point = new Coordinate3(grid.xAt(x), worldY, worldZ);
                    for (SkyIslandWorldVolume candidate : candidates) {
                        SkyIslandTerrainSemantic semantic = interpreters.get(candidate.id()).classify(point);
                        if (semantic.isSolid()) {
                            semantics[grid.linearIndex(x, y, z)] = (byte) semantic.ordinal();
                            break;
                        }
                    }
                }
            }
        }
    }

    private static Map<SkyIslandWorldVolumeId, SkyIslandTerrainInterpreter> compileInterpreters(
            List<SkyIslandWorldVolume> candidates,
            SkyIslandTerrainProfile profile) {
        Map<SkyIslandWorldVolumeId, SkyIslandTerrainInterpreter> result = new HashMap<>();
        for (SkyIslandWorldVolume candidate : candidates) {
            result.put(candidate.id(), new SkyIslandTerrainInterpreter(candidate.compiledVolume(), profile));
        }
        return result;
    }

    private static void requirePositive(String property, int value) {
        if (value <= 0) {
            throw new IllegalArgumentException(property + " must be positive");
        }
    }
}
