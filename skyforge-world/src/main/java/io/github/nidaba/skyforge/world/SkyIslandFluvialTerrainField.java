package io.github.nidaba.skyforge.world;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import java.util.ArrayList;
import java.util.List;
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
        this.reaches = coherent.naturalizedChannels().paths().stream()
                .map(path -> geometry(path, coherent.naturalizedChannels().planningSpacing()))
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
            double candidate = crossSectionElevation(base, bed, projection.distance(), reach);
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
        for (SkyIslandFluvialReachGeometry reach : reaches) {
            Projection projection = project(position, reach.path());
            if (projection.distance() > reach.wetHalfWidth()) {
                continue;
            }
            double surface = bedElevation(reach, projection) + reach.waterDepthPotential();
            if (surface > sample(position) + EPSILON) {
                result = Math.min(result, surface);
                found = true;
            }
        }
        return found ? OptionalDouble.of(clamp01(result)) : OptionalDouble.empty();
    }

    private boolean outsideExtent(SkyIslandLocalPosition position) {
        return position.x() < -extent
                || position.x() > extent
                || position.z() < -extent
                || position.z() > extent;
    }

    private double bedElevation(SkyIslandFluvialReachGeometry reach, Projection projection) {
        SkyIslandNaturalizedChannelPath path = reach.path();
        double start = baseTerrain.sample(path.points().getFirst());
        double rawEnd = baseTerrain.sample(path.points().getLast());
        double requiredDrop = MIN_REACH_DROP
                * (0.30 + 0.70 * reach.profile().gradientPotential());
        double end = Math.min(rawEnd, start - requiredDrop);
        double longitudinalReference = lerp(start, end, projection.fraction());
        double intendedBed = longitudinalReference - reach.bedDepthPotential();

        double localCenterline = baseTerrain.sample(
                new SkyIslandLocalPosition(projection.x(), projection.z()));
        double boundedBed = Math.max(
                intendedBed,
                localCenterline - MAX_FLUVIAL_LOWERING);
        return clamp01(boundedBed);
    }

    private static double crossSectionElevation(
            double base,
            double bed,
            double distance,
            SkyIslandFluvialReachGeometry reach) {
        if (distance <= reach.bankfullHalfWidth()) {
            double normalized = clamp01(distance / reach.bankfullHalfWidth());
            double channelRise = reach.bankReliefPotential()
                    * Math.pow(normalized, reach.crossSectionExponent());
            return Math.min(base, bed + channelRise);
        }

        double outer = (distance - reach.bankfullHalfWidth())
                / (reach.valleyHalfWidth() - reach.bankfullHalfWidth());
        double t = clamp01(outer);
        double valleyRise = reach.bankReliefPotential()
                + valleyShoulderRelief(reach) * Math.pow(t, 1.35);
        double target = Math.min(base, bed + valleyRise);
        double influence = 1.0 - smootherstep(t);
        return lerp(base, target, influence);
    }

    private static double valleyShoulderRelief(SkyIslandFluvialReachGeometry reach) {
        return switch (reach.profile().kind()) {
            case ALLUVIAL -> 0.030 + 0.020 * (1.0 - reach.profile().gradientPotential());
            case INCISED -> 0.045 + 0.030 * reach.profile().incisionPotential();
            case CASCADE -> 0.060 + 0.040 * reach.profile().gradientPotential();
        };
    }

    private static SkyIslandFluvialReachGeometry geometry(
            SkyIslandNaturalizedChannelPath path,
            double spacing) {
        SkyIslandChannelProfile profile = path.profile();
        SkyIslandChannelSegment segment = profile.segment();

        double bankfullHalfWidth = spacing * (
                0.48
                        + 0.95 * profile.bankfullWidthPotential()
                        + 0.48 * segment.relativeDischarge()
                        + 0.20 * segment.corridorScale());

        double valleyMultiplier = switch (profile.kind()) {
            case ALLUVIAL -> 3.6 + 1.8 * segment.corridorScale();
            case INCISED -> 2.2 + 1.2 * profile.incisionPotential();
            case CASCADE -> 1.55 + 0.80 * profile.incisionPotential();
        };
        double valleyHalfWidth = bankfullHalfWidth * valleyMultiplier;

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
        double wetHalfWidth = bankfullHalfWidth * clamp(wetFraction, 0.28, 0.78);

        return new SkyIslandFluvialReachGeometry(
                path,
                bankfullHalfWidth,
                wetHalfWidth,
                valleyHalfWidth,
                bedDepth,
                waterDepth,
                bankRelief,
                exponent);
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
            }
            prefix += length;
        }

        double denominator = Math.max(path.pathLength(), prefix);
        double fraction = denominator <= EPSILON ? 0.0 : clamp01(bestAlong / denominator);
        return new Projection(bestDistance, fraction, bestX, bestZ);
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

    private record Projection(double distance, double fraction, double x, double z) {}
}
