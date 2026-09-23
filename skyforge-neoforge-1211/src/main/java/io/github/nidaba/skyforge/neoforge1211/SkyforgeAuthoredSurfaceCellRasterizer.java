package io.github.nidaba.skyforge.neoforge1211;

import io.github.nidaba.skyforge.world.SkyIslandLocalPosition;
import io.github.nidaba.skyforge.world.SkyIslandSurfaceSiteCapabilityCell;
import io.github.nidaba.skyforge.world.SkyIslandSurfaceSiteCapabilityProfile;
import io.github.nidaba.skyforge.world.SkyIslandWorldVolumeId;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Minecraft-side rasterization of accepted AUTH-0096 watershed anchors.
 *
 * <p>AUTH-0046 supplies the direct unit-scale island-local frame and AUTH-0096 preserves the exact
 * 49x49 watershed cell identity plus realized physical-surface evidence. This adapter performs only
 * backend rasterization: integer block columns are assigned to the nearest accepted watershed
 * anchor in that existing lattice, and accepted anchors are projected with the same
 * {@code round(realizedCenter + local)} convention already used by the accepted DR-20 hydrology
 * adapter. No hydrology threshold, ecology regime, biome identity, or density policy is authored
 * here.
 */
final class SkyforgeAuthoredSurfaceCellRasterizer {
    private final SkyIslandSurfaceSiteCapabilityProfile profile;
    private final SkyIslandWorldVolumeId volumeId;
    private final Map<Integer, SkyIslandSurfaceSiteCapabilityCell> cellsByIndex;
    private final double centerX;
    private final double centerZ;
    private final double radius;
    private final double spacing;
    private final int gridSize;

    SkyforgeAuthoredSurfaceCellRasterizer(SkyIslandSurfaceSiteCapabilityProfile profile) {
        this.profile = Objects.requireNonNull(profile, "profile");
        this.volumeId = profile.association().realizedVolumeId();
        var realized = profile.association().realizedVolume().compiledVolume().descriptor();
        this.centerX = realized.centerX();
        this.centerZ = realized.centerZ();
        this.radius = profile.association().authoredDescriptor().nominalRadius();
        this.spacing = profile.spacing();
        this.gridSize = profile.gridSize();

        Map<Integer, SkyIslandSurfaceSiteCapabilityCell> indexed = new HashMap<>();
        for (SkyIslandSurfaceSiteCapabilityCell cell : profile.cells()) {
            SkyIslandSurfaceSiteCapabilityCell previous =
                    indexed.put(cell.watershedCellIndex(), cell);
            if (previous != null) {
                throw new IllegalArgumentException(
                        "AUTH-0096 profile contains duplicate watershed cell identity");
            }
        }
        this.cellsByIndex = Map.copyOf(indexed);
    }

    SkyIslandSurfaceSiteCapabilityProfile profile() {
        return profile;
    }

    /**
     * Returns the accepted watershed cell whose existing lattice site owns this Minecraft column.
     *
     * <p>The quantization is a backend presentation choice, not a new authored planning grid.
     * Columns outside the accepted [-R,+R] watershed square fail closed. An inactive watershed
     * lattice site likewise returns empty rather than synthesizing semantic evidence.
     */
    Optional<SkyIslandSurfaceSiteCapabilityCell> cellForWorldColumn(
            SkyIslandWorldVolumeId candidateVolumeId,
            int worldX,
            int worldZ) {
        requireVolume(candidateVolumeId);
        double localX = worldX - centerX;
        double localZ = worldZ - centerZ;
        if (localX < -radius || localX > radius || localZ < -radius || localZ > radius) {
            return Optional.empty();
        }

        int gridX = nearestGridCoordinate(localX);
        int gridZ = nearestGridCoordinate(localZ);
        int index = Math.addExact(Math.multiplyExact(gridZ, gridSize), gridX);
        return Optional.ofNullable(cellsByIndex.get(index));
    }

    /**
     * Projects one accepted AUTH-0096 anchor to its exact Minecraft solid column.
     *
     * <p>A physically absent AUTH-0096 anchor returns empty. A physically present anchor that loses
     * exact integer support fails closed because continuing would silently detach the runtime column
     * from accepted realized evidence.
     */
    Optional<ProjectedAnchor> projectAnchor(
            int watershedCellIndex,
            SkyforgeNeoForge1211ChunkAdapter terrain) {
        Objects.requireNonNull(terrain, "terrain");
        SkyIslandSurfaceSiteCapabilityCell cell = cellsByIndex.get(watershedCellIndex);
        if (cell == null) {
            throw new IllegalArgumentException(
                    "unknown AUTH-0096 watershed cell: " + watershedCellIndex);
        }
        if (!cell.physicalSurfacePresent()) {
            return Optional.empty();
        }

        SkyIslandLocalPosition local = cell.position();
        int worldX = Math.toIntExact(Math.round(centerX + local.x()));
        int worldZ = Math.toIntExact(Math.round(centerZ + local.z()));
        var range = terrain.integerSolidRange(volumeId, worldX, worldZ)
                .orElseThrow(() -> new IllegalStateException(
                        "AUTH-0096 physical anchor lost exact Minecraft solid support: "
                                + watershedCellIndex));
        if (!terrain.isSolidOwnedBy(volumeId, worldX, range.maximumY(), worldZ)) {
            throw new IllegalStateException(
                    "projected AUTH-0096 anchor is not owned by its exact realized volume");
        }
        return Optional.of(new ProjectedAnchor(
                cell, worldX, worldZ, range.minimumY(), range.maximumY()));
    }

    boolean hasAuthoredFreshwaterOrRiparianContext(
            SkyIslandSurfaceSiteCapabilityCell cell) {
        return hasAuthoredRetainedOrRiparianContext(cell)
                || canonicalCell(cell).channelRelativeDischarge() > 0.0;
    }

    boolean hasAuthoredRetainedOrRiparianContext(
            SkyIslandSurfaceSiteCapabilityCell cell) {
        SkyIslandSurfaceSiteCapabilityCell canonical = canonicalCell(cell);
        return canonical.retainedWaterbody()
                || canonical.shoreline()
                || canonical.waterDepthPotential() > 0.0
                || canonical.waterbodyMarginPotential() > 0.0
                || canonical.riparianPotential() > 0.0;
    }

    private SkyIslandSurfaceSiteCapabilityCell canonicalCell(
            SkyIslandSurfaceSiteCapabilityCell cell) {
        Objects.requireNonNull(cell, "cell");
        SkyIslandSurfaceSiteCapabilityCell canonical =
                cellsByIndex.get(cell.watershedCellIndex());
        if (canonical == null || !canonical.equals(cell)) {
            throw new IllegalArgumentException(
                    "surface cell does not belong to this exact AUTH-0096 profile");
        }
        return canonical;
    }

    private int nearestGridCoordinate(double localCoordinate) {
        long nearest = Math.round((localCoordinate + radius) / spacing);
        if (nearest < 0L || nearest >= gridSize) {
            throw new IllegalStateException(
                    "in-domain AUTH-0096 coordinate quantized outside its accepted lattice");
        }
        return Math.toIntExact(nearest);
    }

    private void requireVolume(SkyIslandWorldVolumeId candidateVolumeId) {
        Objects.requireNonNull(candidateVolumeId, "candidateVolumeId");
        if (!volumeId.equals(candidateVolumeId)) {
            throw new IllegalArgumentException(
                    "AUTH-0096 rasterization references a foreign exact volume: "
                            + candidateVolumeId.path());
        }
    }

    record ProjectedAnchor(
            SkyIslandSurfaceSiteCapabilityCell sourceCell,
            int worldX,
            int worldZ,
            int minimumSolidY,
            int maximumSolidY) {
        ProjectedAnchor {
            Objects.requireNonNull(sourceCell, "sourceCell");
            if (!sourceCell.physicalSurfacePresent()) {
                throw new IllegalArgumentException(
                        "projected anchor requires accepted physical surface evidence");
            }
            if (maximumSolidY < minimumSolidY) {
                throw new IllegalArgumentException("projected solid interval is inverted");
            }
        }
    }
}
