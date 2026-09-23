package io.github.nidaba.skyforge.world;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalDouble;

/**
 * AUTH-0105 continuous fluvial landform field.
 *
 * <p>This field is downstream of accepted watershed/channel authorship. It does not reroute water or
 * invent reaches. Instead it turns each accepted naturalized channel into a profile-sensitive
 * channel-and-valley terrain primitive, blended into the accepted AUTH-0019 hydrologic surface.
 * Backends can therefore project dry geomorphology before adding visible water or materials.
 */
public final class SkyIslandFluvialTerrainField implements SkyIslandSemanticField {
    private static final double MIN_REACH_DROP = 0.0015;
    public static final double MAX_FLUVIAL_LOWERING = 0.20;
    private static final double EPSILON = 1.0e-12;

    private final SkyIslandDescriptor descriptor;
    private final SkyIslandContinuousHydrologicTerrainField baseTerrain;
    private final List<SkyIslandFluvialReachGeometry> reaches;
    private final double extent;

    private SkyIslandFluvialTerrainField(
            SkyIslandDescriptor descriptor,
            SkyIslandCoherentHydrologicRealizationPlan coherent) {
        this.descriptor = Objects.requireNonNull(descriptor, "descriptor");
        Objects.requireNonNull(coherent, "coherent");
        if (!coherent.descriptor().equals(descriptor)) {
            throw new IllegalArgumentException("coherent hydrology descriptor must match field descriptor");
        }
        this.baseTerrain = coherent.continuousTerrain();
        this.extent = descriptor.nominalRadius();

        Map<Integer, Integer> incomingCounts = new HashMap<>();
        for (SkyIslandNaturalizedChannelPath path : coherent.naturalizedChannels().paths()) {
            incomingCounts.merge(path.profile().segment().downstreamCellIndex(), 1, Integer::sum);
        }
        this.reaches = coherent.naturalizedChannels().paths().stream()
                .map(path -> geometry(
                        path,
                        descriptor.nominalRadius(),
                        baseTerrain,
                        incomingCounts.getOrDefault(path.profile().segment().sourceCellIndex(), 0)))
                .toList();
    }

    public static SkyIslandFluvialTerrainField create(SkyIslandDescriptor descriptor) {
        Objects.requireNonNull(descriptor, "descriptor");
        return create(descriptor, SkyIslandCoherentHydrologicRealizationPlanner.plan(descriptor));
    }

    public static SkyIslandFluvialTerrainField create(
            SkyIslandDescriptor descriptor,
            SkyIslandCoherentHydrologicRealizationPlan coherent) {
        return new SkyIslandFluvialTerrainField(descriptor, coherent);
    }

    public SkyIslandDescriptor descriptor() {
        return descriptor;
    }

    public SkyIslandContinuousHydrologicTerrainField baseTerrain() {
        return baseTerrain;
    }

    public List<SkyIslandFluvialReachGeometry> reaches() {
        return reaches;
    }

    /**
     * Classifies one surface position against the accepted fine fluvial geometry.
     *
     * <p>This is semantic geometry only. Backends may map the zone to native biome/surface
     * machinery, but they must not use it to invent or reroute channels.
     */
    public SkyIslandFluvialSurfaceZone surfaceZone(SkyIslandLocalPosition position) {
        Objects.requireNonNull(position, "position");
        if (outsideExtent(position)) {
            return SkyIslandFluvialSurfaceZone.NONE;
        }
        SkyIslandFluvialSurfaceZone result = SkyIslandFluvialSurfaceZone.NONE;
        for (SkyIslandFluvialReachGeometry reach : reaches) {
            Projection projection = project(position, reach.path());
            if (projection.distance() <= reach.wetHalfWidth()) {
                return SkyIslandFluvialSurfaceZone.WET_CHANNEL;
            }
            if (projection.distance() <= reach.bankfullHalfWidth()) {
                result = SkyIslandFluvialSurfaceZone.BANKFULL;
            } else if (result == SkyIslandFluvialSurfaceZone.NONE
                    && projection.distance() <= reach.valleyHalfWidth()) {
                result = SkyIslandFluvialSurfaceZone.VALLEY;
            }
        }
        return result;
    }

    public double adjustment(SkyIslandLocalPosition position) {
        Objects.requireNonNull(position, "position");
        return sample(position) - baseTerrain.sample(position);
    }

    @Override
    public double sample(SkyIslandLocalPosition position) {
        Objects.requireNonNull(position, "position");
        double base = baseTerrain.sample(position);
        if (outsideExtent(position) || reaches.isEmpty()) {
            return base;
        }

        double shaped = base;
        for (SkyIslandFluvialReachGeometry reach : reaches) {
            Projection projection = project(position, reach.path());
            if (projection.distance() >= reach.valleyHalfWidth()) {
                continue;
            }
            double bed = bedElevation(reach, projection);
            double candidate = crossSectionElevation(
                    base, bed, projection.distance(), projection.signedDistance(), reach);
            shaped = Math.min(shaped, candidate);
        }
        return clamp01(Math.max(shaped, base - MAX_FLUVIAL_LOWERING));
    }

    /**
     * Returns the authored water-surface potential only inside an accepted wet channel corridor.
     *
     * <p>At confluences the lowest compatible surface wins, preventing overlapping reaches from
     * manufacturing an uphill pool. Retained standing-water bodies remain governed separately by
     * AUTH-0009/AUTH-0086.
     */
    public OptionalDouble waterSurfacePotential(SkyIslandLocalPosition position) {
        Objects.requireNonNull(position, "position");
        if (outsideExtent(position)) {
            return OptionalDouble.empty();
        }
        double result = Double.POSITIVE_INFINITY;
        boolean found = false;
        // The dry fluvial surface is independent of which overlapping wet reach is currently being
        // inspected. Compute it once; calling sample(position) inside the reach loop multiplies the
        // full reach-projection pass at confluences without changing semantics.
        double drySurface = sample(position);
        for (SkyIslandFluvialReachGeometry reach : reaches) {
            Projection projection = project(position, reach.path());
            if (projection.distance() > reach.wetHalfWidth()) {
                continue;
            }
            // Water owns a longitudinal hydraulic grade, not "bed + constant depth" at each local
            // sample. The bed may deepen into a terrain-conditioned pool and recover downstream;
            // the free surface must not follow that recovery uphill.
            double surface = reachWaterSurfacePotential(reach, projection.fraction());
            if (surface > drySurface + EPSILON) {
                result = Math.min(result, surface);
                found = true;
            }
        }
        return found ? OptionalDouble.of(clamp01(result)) : OptionalDouble.empty();
    }

    /**
     * Returns one reach's authored hydraulic-grade potential at a longitudinal fraction.
     *
     * <p>The grade is intentionally independent of local bed excursions. It descends linearly from
     * the reach's accepted upstream terrain reference to its bounded downstream reference, offset
     * by the same profile-sensitive grade cut used to constrain bed shaping, then adds the authored
     * water-depth potential. Local depressions therefore increase effective water depth instead of
     * pulling the free surface down and forcing it to climb again downstream.
     */
    public double reachWaterSurfacePotential(
            SkyIslandFluvialReachGeometry reach,
            double fraction) {
        Objects.requireNonNull(reach, "reach");
        if (!reaches.contains(reach)) {
            throw new IllegalArgumentException("reach must belong to this fluvial field");
        }
        if (!Double.isFinite(fraction) || fraction < 0.0 || fraction > 1.0) {
            throw new IllegalArgumentException("fraction must be finite and in [0, 1]");
        }
        double gradeBed = longitudinalReference(reach, fraction)
                - reach.bedDepthPotential() * gradeCutFraction(reach);
        return clamp01(gradeBed + reach.waterDepthPotential());
    }

    private boolean outsideExtent(SkyIslandLocalPosition position) {
        return position.x() < -extent
                || position.x() > extent
                || position.z() < -extent
                || position.z() > extent;
    }

    private double bedElevation(SkyIslandFluvialReachGeometry reach, Projection projection) {
        double localCenterline = baseTerrain.sample(
                new SkyIslandLocalPosition(projection.x(), projection.z()));
        double localBed = localCenterline - reach.bedDepthPotential();
        double gradeCeiling = longitudinalReference(reach, projection.fraction())
                - reach.bedDepthPotential() * gradeCutFraction(reach);
        double intendedBed = Math.min(localBed, gradeCeiling);
        double boundedBed = Math.max(
                intendedBed,
                localCenterline - MAX_FLUVIAL_LOWERING);
        return clamp01(boundedBed);
    }

    private double longitudinalReference(
            SkyIslandFluvialReachGeometry reach,
            double fraction) {
        SkyIslandNaturalizedChannelPath path = reach.path();
        double start = baseTerrain.sample(path.points().getFirst());
        double rawEnd = baseTerrain.sample(path.points().getLast());
        double requiredDrop = MIN_REACH_DROP
                * (0.30 + 0.70 * reach.profile().gradientPotential());
        double end = Math.min(rawEnd, start - requiredDrop);
        return lerp(start, end, fraction);
    }

    private static double gradeCutFraction(SkyIslandFluvialReachGeometry reach) {
        return switch (reach.profile().kind()) {
            case ALLUVIAL -> 0.35;
            case INCISED -> 0.55;
            case CASCADE -> 0.75;
        };
    }

    private static double crossSectionElevation(
            double base,
            double bed,
            double distance,
            double signedDistance,
            SkyIslandFluvialReachGeometry reach) {
        double side = Math.signum(signedDistance);
        double asymmetry = reach.profile().kind() == SkyIslandChannelProfileKind.ALLUVIAL
                ? 0.22 * reach.lateralAsymmetryPotential() * side
                : reach.profile().kind() == SkyIslandChannelProfileKind.INCISED
                        ? 0.08 * reach.lateralAsymmetryPotential() * side
                        : 0.0;
        double localBankfullHalfWidth = reach.bankfullHalfWidth() * (1.0 + asymmetry);
        double localValleyHalfWidth = reach.valleyHalfWidth() * (1.0 + 0.55 * asymmetry);
        localBankfullHalfWidth = Math.max(reach.wetHalfWidth() * 1.04, localBankfullHalfWidth);
        localValleyHalfWidth = Math.max(localBankfullHalfWidth * 1.05, localValleyHalfWidth);

        if (distance <= localBankfullHalfWidth) {
            double normalized = clamp01(distance / localBankfullHalfWidth);
            double channelRise = reach.bankReliefPotential()
                    * Math.pow(normalized, reach.crossSectionExponent());
            return Math.min(base, bed + channelRise);
        }

        double outer = (distance - localBankfullHalfWidth)
                / (localValleyHalfWidth - localBankfullHalfWidth);
        double t = clamp01(outer);
        double valleyRise = reach.bankReliefPotential()
                + valleyShoulderRelief(reach) * Math.pow(t, 1.35);
        double target = Math.min(base, bed + valleyRise);
        double influence = 1.0 - smootherstep(t);
        return lerp(base, target, influence);
    }

    private static double valleyShoulderRelief(SkyIslandFluvialReachGeometry reach) {
        return switch (reach.profile().kind()) {
            case ALLUVIAL -> 0.024
                    + 0.018 * (1.0 - reach.profile().gradientPotential())
                    + 0.008 * reach.confinementPotential();
            case INCISED -> 0.040
                    + 0.034 * reach.profile().incisionPotential()
                    + 0.020 * reach.confinementPotential();
            case CASCADE -> 0.055
                    + 0.040 * reach.profile().gradientPotential()
                    + 0.025 * reach.confinementPotential();
        };
    }

    private static SkyIslandFluvialReachGeometry geometry(
            SkyIslandNaturalizedChannelPath path,
            double islandRadius,
            SkyIslandContinuousHydrologicTerrainField baseTerrain,
            int incomingReachCount) {
        SkyIslandChannelProfile profile = path.profile();
        SkyIslandChannelSegment segment = profile.segment();

        /*
         * H3: derive physical width from island scale and the hydraulic-geometry potential, not
         * from watershed lattice spacing. Changing planning resolution must not resize rivers.
         * The three-unit half-width floor is a backend-agnostic quantization guard: even the
         * smallest accepted headwater must retain enough horizontal support to survive a one-unit
         * voxel raster without collapsing to a sparse polyline. Above that floor, discharge remains
         * the dominant source of downstream widening.
         */
        double bankfullHalfWidth = Math.max(
                3.0,
                islandRadius * (0.0060 + 0.022 * profile.bankfullWidthPotential()));
        double confluenceScale = incomingReachCount >= 2
                ? 1.0 + 0.12 * Math.min(2, incomingReachCount - 1)
                : 1.0;
        bankfullHalfWidth *= confluenceScale;

        double confinement = confinementPotential(
                path, baseTerrain, bankfullHalfWidth, islandRadius);
        double valleyMultiplier = switch (profile.kind()) {
            case ALLUVIAL -> lerp(5.4, 2.7, confinement);
            case INCISED -> lerp(3.4, 1.85, confinement);
            case CASCADE -> lerp(2.2, 1.40, confinement);
        };
        double valleyHalfWidth = bankfullHalfWidth
                * valleyMultiplier
                * (1.0 + 0.10 * (confluenceScale - 1.0) / 0.12);
        double lateralAsymmetry = lateralAsymmetryPotential(
                path, baseTerrain, bankfullHalfWidth, islandRadius);

        // Minecraft's one-block vertical quantization makes deep/narrow continuous
        // sections read as artificial trenches. Preserve discharge/profile ordering while biasing
        // the neutral geometry toward wider, shallower channels; the existing MAX_FLUVIAL_LOWERING
        // cap continues to bound total relief modification.
        double waterDepth = 0.004
                + 0.013 * (
                        0.45 * profile.depthPotential()
                                + 0.35 * segment.relativeDischarge()
                                + 0.20 * segment.corridorScale());
        double bankRelief = waterDepth
                + 0.007
                + 0.017 * (
                        0.55 * profile.incisionPotential()
                                + 0.45 * profile.streamPowerPotential());
        double bedDepth = bankRelief
                + 0.010
                + 0.032 * (
                        0.55 * profile.incisionPotential()
                                + 0.25 * profile.streamPowerPotential()
                                + 0.20 * profile.depthPotential());

        double exponent = switch (profile.kind()) {
            case ALLUVIAL -> 1.8;
            case INCISED -> 1.35;
            case CASCADE -> 1.15;
        };
        double wetFraction = Math.pow(waterDepth / bankRelief, 1.0 / exponent) * 0.94;
        /*
         * The continuous field may author sub-block headwaters, but the Minecraft consumer samples
         * integer X/Z columns. Preserve the discharge-driven bankfull scale while guaranteeing that
         * an accepted visible channel has at least one rasterizable wet column near its centerline.
         * 0.75 exceeds the square-grid half-diagonal tolerance (~0.707) without inflating the
         * bankfull envelope itself.
         */
        double wetHalfWidth = Math.min(
                bankfullHalfWidth * 0.78,
                Math.max(0.75, bankfullHalfWidth * clamp(wetFraction, 0.28, 0.78)));

        return new SkyIslandFluvialReachGeometry(
                path,
                bankfullHalfWidth,
                wetHalfWidth,
                valleyHalfWidth,
                bedDepth,
                waterDepth,
                bankRelief,
                exponent,
                confinement,
                lateralAsymmetry,
                confluenceScale);
    }

    private static double confinementPotential(
            SkyIslandNaturalizedChannelPath path,
            SkyIslandContinuousHydrologicTerrainField terrain,
            double bankfullHalfWidth,
            double islandRadius) {
        List<SkyIslandLocalPosition> points = path.points();
        if (points.size() < 3) {
            return 0.0;
        }
        double probe = Math.max(bankfullHalfWidth * 2.4, islandRadius * 0.012);
        double accumulated = 0.0;
        int samples = 0;
        for (int i = 1; i + 1 < points.size(); i++) {
            SkyIslandLocalPosition previous = points.get(i - 1);
            SkyIslandLocalPosition center = points.get(i);
            SkyIslandLocalPosition next = points.get(i + 1);
            double tx = next.x() - previous.x();
            double tz = next.z() - previous.z();
            double length = Math.hypot(tx, tz);
            if (length <= EPSILON) {
                continue;
            }
            double nx = -tz / length;
            double nz = tx / length;
            SkyIslandLocalPosition left =
                    new SkyIslandLocalPosition(center.x() + nx * probe, center.z() + nz * probe);
            SkyIslandLocalPosition right =
                    new SkyIslandLocalPosition(center.x() - nx * probe, center.z() - nz * probe);
            double centerElevation = terrain.sample(center);
            double sideMean = 0.5 * (terrain.sample(left) + terrain.sample(right));
            accumulated += clamp01(Math.max(0.0, sideMean - centerElevation) / 0.075);
            samples++;
        }
        return samples == 0 ? 0.0 : clamp01(accumulated / samples);
    }

    private static double lateralAsymmetryPotential(
            SkyIslandNaturalizedChannelPath path,
            SkyIslandContinuousHydrologicTerrainField terrain,
            double bankfullHalfWidth,
            double islandRadius) {
        List<SkyIslandLocalPosition> points = path.points();
        if (points.size() < 3) {
            return 0.0;
        }
        double probe = Math.max(bankfullHalfWidth * 1.8, islandRadius * 0.010);
        double signedRelief = 0.0;
        int samples = 0;
        for (int i = 1; i + 1 < points.size(); i++) {
            SkyIslandLocalPosition previous = points.get(i - 1);
            SkyIslandLocalPosition center = points.get(i);
            SkyIslandLocalPosition next = points.get(i + 1);
            double tx = next.x() - previous.x();
            double tz = next.z() - previous.z();
            double length = Math.hypot(tx, tz);
            if (length <= EPSILON) {
                continue;
            }
            double nx = -tz / length;
            double nz = tx / length;
            SkyIslandLocalPosition left =
                    new SkyIslandLocalPosition(center.x() + nx * probe, center.z() + nz * probe);
            SkyIslandLocalPosition right =
                    new SkyIslandLocalPosition(center.x() - nx * probe, center.z() - nz * probe);
            signedRelief += terrain.sample(left) - terrain.sample(right);
            samples++;
        }
        return samples == 0
                ? 0.0
                : clamp(signedRelief / samples / 0.05, -1.0, 1.0);
    }

    private static Projection project(
            SkyIslandLocalPosition position,
            SkyIslandNaturalizedChannelPath path) {
        List<SkyIslandLocalPosition> points = path.points();
        double prefix = 0.0;
        double bestDistance = Double.POSITIVE_INFINITY;
        double bestAlong = 0.0;
        double bestX = points.getFirst().x();
        double bestZ = points.getFirst().z();
        double bestSignedDistance = 0.0;

        for (int i = 1; i < points.size(); i++) {
            SkyIslandLocalPosition a = points.get(i - 1);
            SkyIslandLocalPosition b = points.get(i);
            double dx = b.x() - a.x();
            double dz = b.z() - a.z();
            double lengthSquared = dx * dx + dz * dz;
            double length = Math.sqrt(lengthSquared);
            if (length <= EPSILON) {
                continue;
            }
            double px = position.x() - a.x();
            double pz = position.z() - a.z();
            double t = clamp01((px * dx + pz * dz) / lengthSquared);
            double qx = a.x() + t * dx;
            double qz = a.z() + t * dz;
            double distance = Math.hypot(position.x() - qx, position.z() - qz);
            if (distance < bestDistance - EPSILON) {
                bestDistance = distance;
                bestAlong = prefix + t * length;
                bestX = qx;
                bestZ = qz;
                double cross = dx * (position.z() - qz) - dz * (position.x() - qx);
                bestSignedDistance = Math.copySign(distance, cross);
            }
            prefix += length;
        }

        double denominator = Math.max(path.pathLength(), prefix);
        double fraction = denominator <= EPSILON ? 0.0 : clamp01(bestAlong / denominator);
        return new Projection(bestDistance, bestSignedDistance, fraction, bestX, bestZ);
    }

    private static double smootherstep(double value) {
        double t = clamp01(value);
        return t * t * t * (t * (t * 6.0 - 15.0) + 10.0);
    }

    private static double lerp(double a, double b, double fraction) {
        return a + (b - a) * fraction;
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static double clamp01(double value) {
        return clamp(value, 0.0, 1.0);
    }

    private record Projection(
            double distance,
            double signedDistance,
            double fraction,
            double x,
            double z) {}
}
