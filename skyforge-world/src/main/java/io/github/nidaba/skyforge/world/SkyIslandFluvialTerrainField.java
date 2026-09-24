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
    private final Map<SkyIslandFluvialReachGeometry, HydraulicGradeProfile> hydraulicGrades;
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
        Map<SkyIslandFluvialReachGeometry, HydraulicGradeProfile> grades = new HashMap<>();
        for (SkyIslandFluvialReachGeometry reach : reaches) {
            grades.put(reach, buildHydraulicGradeProfile(reach));
        }
        this.hydraulicGrades = Map.copyOf(grades);
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

    /** Local wet-channel half-width, including only the short topology-derived confluence throat. */
    public double wetHalfWidthAt(
            SkyIslandFluvialReachGeometry reach,
            double fraction) {
        requireReachFraction(reach, fraction);
        return wetHalfWidthAtStatic(reach, fraction);
    }

    /** Whether this position lies inside this exact reach's authored wet corridor. */
    public boolean wetCorridorContains(
            SkyIslandFluvialReachGeometry reach,
            SkyIslandLocalPosition position) {
        Objects.requireNonNull(position, "position");
        requireReachFraction(reach, 0.0);
        Projection projection = project(position, reach.path());
        return projection.distance() <= wetHalfWidthAt(reach, projection.fraction());
    }

    /** Local bankfull half-width; tributary-junction expansion tapers out downstream. */
    public double bankfullHalfWidthAt(
            SkyIslandFluvialReachGeometry reach,
            double fraction) {
        requireReachFraction(reach, fraction);
        return bankfullHalfWidthAtStatic(reach, fraction);
    }

    /** Local valley half-width used by the dry fluvial terrain field. */
    public double valleyHalfWidthAt(
            SkyIslandFluvialReachGeometry reach,
            double fraction) {
        requireReachFraction(reach, fraction);
        return valleyHalfWidthAtStatic(reach, fraction);
    }

    private void requireReachFraction(
            SkyIslandFluvialReachGeometry reach,
            double fraction) {
        Objects.requireNonNull(reach, "reach");
        if (!reaches.contains(reach)) {
            throw new IllegalArgumentException("reach must belong to this fluvial field");
        }
        if (!Double.isFinite(fraction) || fraction < 0.0 || fraction > 1.0) {
            throw new IllegalArgumentException("fraction must be finite and in [0, 1]");
        }
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
            if (projection.distance() <= wetHalfWidthAt(reach, projection.fraction())) {
                return SkyIslandFluvialSurfaceZone.WET_CHANNEL;
            }
            if (projection.distance() <= bankfullHalfWidthAt(reach, projection.fraction())) {
                result = SkyIslandFluvialSurfaceZone.BANKFULL;
            } else if (result == SkyIslandFluvialSurfaceZone.NONE
                    && projection.distance() <= valleyHalfWidthAt(reach, projection.fraction())) {
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
            if (projection.distance() >= valleyHalfWidthAt(reach, projection.fraction())) {
                continue;
            }
            double bed = bedElevation(reach, projection);
            double candidate = crossSectionElevation(base, bed, projection, reach);
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
            if (projection.distance() > wetHalfWidthAt(reach, projection.fraction())) {
                continue;
            }
            // Water owns a longitudinal hydraulic grade, not "bed + constant depth" at each local
            // sample. The bed may deepen into a terrain-conditioned pool and recover downstream;
            // the free surface must not follow that recovery uphill.
            double surface = reachWaterSurfacePotentialUnchecked(reach, projection.fraction());
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
     * <p>The raw hydraulic requirement is the terrain-conditioned centerline bed plus authored water
     * depth. The retained grade is the minimal downstream-nonclimbing envelope over those path
     * samples. A local bed recovery therefore raises the upstream pool/backwater surface instead of
     * either making the free surface climb downstream or deleting visible water from the reach.
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
        return reachWaterSurfacePotentialUnchecked(reach, fraction);
    }

    private double reachWaterSurfacePotentialUnchecked(
            SkyIslandFluvialReachGeometry reach,
            double fraction) {
        HydraulicGradeProfile profile = hydraulicGrades.get(reach);
        if (profile == null) {
            throw new IllegalStateException("fluvial reach lost its hydraulic-grade profile");
        }
        return profile.sample(fraction);
    }

    private HydraulicGradeProfile buildHydraulicGradeProfile(
            SkyIslandFluvialReachGeometry reach) {
        var points = reach.path().points();
        List<Double> fractions = new ArrayList<>(points.size());
        List<Double> surfaces = new ArrayList<>(points.size());
        double cumulative = 0.0;
        for (int index = 0; index < points.size(); index++) {
            if (index > 0) {
                SkyIslandLocalPosition previous = points.get(index - 1);
                SkyIslandLocalPosition current = points.get(index);
                cumulative += Math.hypot(
                        current.x() - previous.x(),
                        current.z() - previous.z());
            }
            double fraction = index == points.size() - 1
                    ? 1.0
                    : clamp01(cumulative / reach.path().pathLength());
            SkyIslandLocalPosition point = points.get(index);
            Projection projection = new Projection(
                    0.0,
                    0.0,
                    fraction,
                    point.x(),
                    point.z(),
                    0.0);
            fractions.add(fraction);
            surfaces.add(clamp01(
                    bedElevation(reach, projection) + reach.waterDepthPotential()));
        }

        // Minimal non-increasing majorant of the local bed+depth requirement. Walking upstream,
        // raise only the samples required to prevent a later bed recovery from forcing an uphill
        // free surface. This preserves every accepted reach's minimum local water depth.
        for (int index = surfaces.size() - 2; index >= 0; index--) {
            surfaces.set(index, Math.max(surfaces.get(index), surfaces.get(index + 1)));
        }
        return new HydraulicGradeProfile(fractions, surfaces);
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
            Projection projection,
            SkyIslandFluvialReachGeometry reach) {
        double signedDistance = projection.signedDistance();
        double side = Math.signum(signedDistance);

        // Terrain confinement may favor one side of an entire reach, while planform curvature
        // changes side locally from bend to bend. Keep both backend-neutral: on an alluvial bend the
        // inside bank receives the broader depositional shoulder and the thalweg shifts modestly
        // toward the outside bank. Incised channels retain a smaller response; cascades remain
        // essentially symmetric.
        double terrainAsymmetry = reach.profile().kind() == SkyIslandChannelProfileKind.ALLUVIAL
                ? 0.14 * reach.lateralAsymmetryPotential() * side
                : reach.profile().kind() == SkyIslandChannelProfileKind.INCISED
                        ? 0.06 * reach.lateralAsymmetryPotential() * side
                        : 0.0;
        double bendAsymmetry = reach.profile().kind() == SkyIslandChannelProfileKind.ALLUVIAL
                ? 0.24 * projection.bendPotential() * side
                : reach.profile().kind() == SkyIslandChannelProfileKind.INCISED
                        ? 0.08 * projection.bendPotential() * side
                        : 0.0;
        double asymmetry = clamp(terrainAsymmetry + bendAsymmetry, -0.32, 0.32);

        double thalwegShift = switch (reach.profile().kind()) {
            case ALLUVIAL -> -0.22 * projection.bendPotential() * reach.wetHalfWidth();
            case INCISED -> -0.08 * projection.bendPotential() * reach.wetHalfWidth();
            case CASCADE -> 0.0;
        };
        double distance = Math.abs(signedDistance - thalwegShift);

        double localWetHalfWidth = wetHalfWidthAtStatic(reach, projection.fraction());
        double localBankfullHalfWidth = bankfullHalfWidthAtStatic(reach, projection.fraction())
                * (1.0 + asymmetry);
        double localValleyHalfWidth = valleyHalfWidthAtStatic(reach, projection.fraction())
                * (1.0 + 0.55 * asymmetry);
        localBankfullHalfWidth = Math.max(localWetHalfWidth * 1.04, localBankfullHalfWidth);
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

        double confinement = confinementPotential(
                path, baseTerrain, bankfullHalfWidth, islandRadius);
        double valleyMultiplier = switch (profile.kind()) {
            case ALLUVIAL -> lerp(5.4, 2.7, confinement);
            case INCISED -> lerp(3.4, 1.85, confinement);
            case CASCADE -> lerp(2.2, 1.40, confinement);
        };
        double valleyHalfWidth = bankfullHalfWidth * valleyMultiplier;
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
         * The wet corridor must survive integer-grid realization as a coherent landform, not merely
         * as a one-pixel polyline. A ~3.2-unit minimum full width keeps small accepted headwaters
         * face-connected and visually legible while remaining well inside the >=6-unit minimum
         * bankfull corridor. Above that floor, discharge/profile geometry remains authoritative.
         */
        double wetHalfWidth = Math.min(
                bankfullHalfWidth * 0.78,
                Math.max(1.60, bankfullHalfWidth * clamp(wetFraction, 0.28, 0.78)));

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
        double bestBendPotential = 0.0;

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
                int bendVertex = t < 0.5 ? i - 1 : i;
                bestBendPotential = localBendPotential(points, bendVertex);
            }
            prefix += length;
        }

        double denominator = Math.max(path.pathLength(), prefix);
        double fraction = denominator <= EPSILON ? 0.0 : clamp01(bestAlong / denominator);
        return new Projection(
                bestDistance,
                bestSignedDistance,
                fraction,
                bestX,
                bestZ,
                bestBendPotential);
    }

    private static double localBendPotential(
            List<SkyIslandLocalPosition> points,
            int vertexIndex) {
        if (vertexIndex <= 0 || vertexIndex + 1 >= points.size()) {
            return 0.0;
        }
        SkyIslandLocalPosition previous = points.get(vertexIndex - 1);
        SkyIslandLocalPosition center = points.get(vertexIndex);
        SkyIslandLocalPosition next = points.get(vertexIndex + 1);
        double ax = center.x() - previous.x();
        double az = center.z() - previous.z();
        double bx = next.x() - center.x();
        double bz = next.z() - center.z();
        double aLength = Math.hypot(ax, az);
        double bLength = Math.hypot(bx, bz);
        if (aLength <= EPSILON || bLength <= EPSILON) {
            return 0.0;
        }
        double signedSine = (ax * bz - az * bx) / (aLength * bLength);
        return clamp(signedSine, -1.0, 1.0);
    }

    private static double confluenceScaleAt(
            SkyIslandFluvialReachGeometry reach,
            double fraction,
            double strength) {
        if (reach.confluenceScale() <= 1.0 || strength <= 0.0) {
            return 1.0;
        }
        // Junction expansion belongs to the merge, not the entire downstream reach. Fade the
        // tributary-count bonus over the first third; normal discharge/profile geometry then owns
        // the persistent channel scale downstream.
        double fade = 1.0 - smootherstep(clamp01(fraction / 0.34));
        return 1.0 + strength * (reach.confluenceScale() - 1.0) * fade;
    }

    private static double wetHalfWidthAtStatic(
            SkyIslandFluvialReachGeometry reach,
            double fraction) {
        return reach.wetHalfWidth() * confluenceScaleAt(reach, fraction, 0.70);
    }

    private static double bankfullHalfWidthAtStatic(
            SkyIslandFluvialReachGeometry reach,
            double fraction) {
        return reach.bankfullHalfWidth() * confluenceScaleAt(reach, fraction, 1.0);
    }

    private static double valleyHalfWidthAtStatic(
            SkyIslandFluvialReachGeometry reach,
            double fraction) {
        return reach.valleyHalfWidth() * confluenceScaleAt(reach, fraction, 0.65);
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

    private record HydraulicGradeProfile(
            List<Double> fractions,
            List<Double> surfaces) {
        private HydraulicGradeProfile {
            fractions = List.copyOf(fractions);
            surfaces = List.copyOf(surfaces);
            if (fractions.size() != surfaces.size() || fractions.size() < 2) {
                throw new IllegalArgumentException(
                        "hydraulic-grade profile requires matching multi-sample arrays");
            }
        }

        private double sample(double fraction) {
            if (fraction <= fractions.getFirst()) {
                return surfaces.getFirst();
            }
            if (fraction >= fractions.getLast()) {
                return surfaces.getLast();
            }

            int low = 0;
            int high = fractions.size() - 1;
            while (high - low > 1) {
                int middle = (low + high) >>> 1;
                if (fractions.get(middle) <= fraction) {
                    low = middle;
                } else {
                    high = middle;
                }
            }
            double lowerFraction = fractions.get(low);
            double upperFraction = fractions.get(high);
            if (upperFraction - lowerFraction <= EPSILON) {
                return Math.max(surfaces.get(low), surfaces.get(high));
            }
            double t = (fraction - lowerFraction) / (upperFraction - lowerFraction);
            return lerp(surfaces.get(low), surfaces.get(high), t);
        }
    }

    private record Projection(
            double distance,
            double signedDistance,
            double fraction,
            double x,
            double z,
            double bendPotential) {}
}
