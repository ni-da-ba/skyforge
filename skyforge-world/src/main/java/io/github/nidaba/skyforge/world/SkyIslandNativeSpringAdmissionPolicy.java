package io.github.nidaba.skyforge.world;

import java.util.Objects;

/**
 * AUTH-0085 backend-neutral semantic admission policy for candidate native subsurface springs.
 *
 * <p>The policy reuses accepted geology and cave authorship. It does not create groundwater,
 * caves, geothermal systems, backend fluids, or Minecraft placement behavior.
 */
public final class SkyIslandNativeSpringAdmissionPolicy {
    private SkyIslandNativeSpringAdmissionPolicy() {}

    /**
     * Evaluates one semantic candidate against exact AUTH-0023 aquifer regions and AUTH-0030 cave
     * volume.
     *
     * <p>Water is admitted only where the candidate is both inside authored cave volume and mapped
     * to an already-accepted aquifer-body cell. Molten fluid fails closed because Skyforge
     * currently has no geothermal/volcanic authorship semantics.
     */
    public static SkyIslandNativeSpringAdmission evaluate(
            SkyIslandGeologicRegionPlan geologyRegions,
            SkyIslandExteriorConnectedCaveVolumeField caves,
            SkyIslandSubsurfacePosition position,
            SkyIslandNativeSpringFluidKind fluidKind) {
        Objects.requireNonNull(geologyRegions, "geologyRegions");
        Objects.requireNonNull(caves, "caves");
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(fluidKind, "fluidKind");

        if (!geologyRegions.descriptor().equals(caves.descriptor())) {
            throw new IllegalArgumentException(
                    "native spring geology and cave semantics must describe the same island");
        }

        SkyIslandGeologySample geology =
                SkyIslandGeologyFieldSet.create(geologyRegions.descriptor()).sample(position);
        if (!geology.owned()) {
            return decision(
                    position,
                    fluidKind,
                    SkyIslandNativeSpringAdmissionStatus.OUTSIDE_AUTHORED_ISLAND,
                    null,
                    null);
        }

        SkyIslandExteriorConnectedCaveVolumeSample cave = caves.sample(position);
        if (!cave.inside()) {
            return decision(
                    position,
                    fluidKind,
                    SkyIslandNativeSpringAdmissionStatus.NOT_AUTHORED_CAVE_INTERIOR,
                    null,
                    null);
        }

        if (fluidKind == SkyIslandNativeSpringFluidKind.MOLTEN) {
            return decision(
                    position,
                    fluidKind,
                    SkyIslandNativeSpringAdmissionStatus.MISSING_GEOTHERMAL_SEMANTICS,
                    cave,
                    null);
        }

        AquiferMatch aquifer = aquiferMatch(geologyRegions, position);
        if (aquifer == null) {
            return decision(
                    position,
                    fluidKind,
                    SkyIslandNativeSpringAdmissionStatus.NO_AQUIFER_SUPPORT,
                    cave,
                    null);
        }

        return decision(
                position,
                fluidKind,
                SkyIslandNativeSpringAdmissionStatus.ADMITTED_AQUIFER_CAVE_WATER,
                cave,
                aquifer);
    }

    private static SkyIslandNativeSpringAdmission decision(
            SkyIslandSubsurfacePosition position,
            SkyIslandNativeSpringFluidKind fluidKind,
            SkyIslandNativeSpringAdmissionStatus status,
            SkyIslandExteriorConnectedCaveVolumeSample cave,
            AquiferMatch aquifer) {
        return new SkyIslandNativeSpringAdmission(
                position,
                fluidKind,
                status,
                cave == null
                        ? SkyIslandExteriorConnectedCaveVolumeSample.SourceKind.NONE
                        : cave.sourceKind(),
                cave == null ? -1 : cave.systemId(),
                aquifer == null ? -1 : aquifer.regionId(),
                aquifer == null ? -1 : aquifer.cell().index(),
                aquifer == null ? 0.0 : aquifer.cell().membership());
    }

    private static AquiferMatch aquiferMatch(
            SkyIslandGeologicRegionPlan plan,
            SkyIslandSubsurfacePosition position) {
        double radius = plan.descriptor().nominalRadius();
        int xIndex = nearestIndex(
                (position.x() + radius) / plan.horizontalSpacing(),
                plan.gridSize());
        int zIndex = nearestIndex(
                (position.z() + radius) / plan.horizontalSpacing(),
                plan.gridSize());
        int depthIndex = nearestIndex(
                position.depthFraction() / plan.depthSpacing(),
                plan.depthSamples());

        int cellIndex =
                (zIndex * plan.depthSamples() + depthIndex) * plan.gridSize() + xIndex;

        for (SkyIslandGeologicRegion region : plan.regions()) {
            if (region.kind() != SkyIslandGeologicRegionKind.AQUIFER_BODY) {
                continue;
            }
            for (SkyIslandGeologicRegionCell cell : region.cells()) {
                if (cell.index() == cellIndex) {
                    return new AquiferMatch(region.regionId(), cell);
                }
            }
        }
        return null;
    }

    private static int nearestIndex(double coordinate, int samples) {
        long rounded = Math.round(coordinate);
        return (int) Math.max(0L, Math.min(samples - 1L, rounded));
    }

    private record AquiferMatch(int regionId, SkyIslandGeologicRegionCell cell) {}
}
