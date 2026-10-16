package io.github.nidaba.skyforge.world;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Builds a bounded, terrain-aware channel/valley candidate and solves hydraulics on its bed.
 *
 * <p>The candidate preserves the supplied planform route. It uses an explicit SI scale, a positive
 * minimum bed grade, an upstream incision allowance, and a trapezoidal section. The local terrain
 * response forms a channel and bankfull valley around the route, blending back to the authored
 * terrain at the configured valley edge. No roughness, discharge, scale, or boundary condition is
 * inferred; callers must provide them.
 */
public final class SkyIslandHydraulicCandidateBuilder {
    private static final double EPSILON = 1.0e-10;

    private SkyIslandHydraulicCandidateBuilder() {}

    public static Candidate build(
            SkyIslandGeomorphicCandidateRoute route,
            SkyIslandSemanticField terrain,
            Configuration configuration) {
        Objects.requireNonNull(route, "route");
        Objects.requireNonNull(terrain, "terrain");
        Objects.requireNonNull(configuration, "configuration");

        List<SkyIslandLocalPosition> points = route.points();
        double[] stations = new double[points.size()];
        double[] rawElevations = new double[points.size()];
        double[] bedElevations = new double[points.size()];
        for (int i = 0; i < points.size(); i++) {
            rawElevations[i] = normalizedElevation(terrain, points.get(i), configuration);
            if (i > 0) {
                double distance = distance(points.get(i - 1), points.get(i))
                        * configuration.horizontalMetresPerLocalUnit();
                if (!(distance > 0.0) || !Double.isFinite(distance)) {
                    throw new IllegalArgumentException("route stations must be distinct and finite");
                }
                stations[i] = stations[i - 1] + distance;
                if (!Double.isFinite(stations[i])) {
                    throw new IllegalArgumentException("route length must be finite");
                }
            }
        }

        double[] projected = minimumGradeProjection(
                rawElevations, stations, configuration.minimumBedSlope());
        for (int i = 0; i < points.size(); i++) {
            bedElevations[i] = projected[i]
                    - configuration.minimumBedSlope() * stations[i]
                    - configuration.upstreamIncisionMetres();
            requireWithinEditEnvelope(
                    rawElevations[i], bedElevations[i], configuration.maximumTerrainEditMetres());
        }
        return new Candidate(points, stations, rawElevations, bedElevations, terrain, configuration);
    }

    /**
     * Weighted least-squares projection onto the minimum-grade constraint. After removing the
     * required grade, the adjusted profile is non-increasing downstream; pool-adjacent-violators
     * computes that projection in linear time while weighting each station by its represented reach
     * length.
     */
    private static double[] minimumGradeProjection(
            double[] elevations,
            double[] stations,
            double minimumSlope) {
        int count = elevations.length;
        int[] starts = new int[count];
        int[] ends = new int[count];
        double[] means = new double[count];
        double[] weights = new double[count];
        int blocks = 0;

        for (int i = 0; i < count; i++) {
            double representedLength;
            if (i == 0) {
                representedLength = (stations[1] - stations[0]) * 0.5;
            } else if (i == count - 1) {
                representedLength = (stations[i] - stations[i - 1]) * 0.5;
            } else {
                representedLength = (stations[i + 1] - stations[i - 1]) * 0.5;
            }
            starts[blocks] = i;
            ends[blocks] = i;
            means[blocks] = elevations[i] + minimumSlope * stations[i];
            weights[blocks] = representedLength;
            blocks++;

            while (blocks >= 2 && means[blocks - 2] < means[blocks - 1]) {
                double combinedWeight = weights[blocks - 2] + weights[blocks - 1];
                means[blocks - 2] = (means[blocks - 2] * weights[blocks - 2]
                        + means[blocks - 1] * weights[blocks - 1]) / combinedWeight;
                weights[blocks - 2] = combinedWeight;
                ends[blocks - 2] = ends[blocks - 1];
                blocks--;
            }
        }

        double[] projected = new double[count];
        for (int block = 0; block < blocks; block++) {
            Arrays.fill(projected, starts[block], ends[block] + 1, means[block]);
        }
        return projected;
    }

    private static double normalizedElevation(
            SkyIslandSemanticField terrain,
            SkyIslandLocalPosition position,
            Configuration configuration) {
        double normalized = terrain.sample(position);
        if (!Double.isFinite(normalized) || normalized < 0.0 || normalized > 1.0) {
            throw new IllegalArgumentException("terrain elevation must be finite and normalized to [0, 1]");
        }
        return configuration.verticalDatumMetres()
                + normalized * configuration.verticalMetresPerNormalizedUnit();
    }

    private static void requireWithinEditEnvelope(double original, double candidate, double maximumEdit) {
        if (Math.abs(candidate - original) > maximumEdit + EPSILON) {
            throw new IllegalArgumentException("candidate channel bed exceeds the terrain edit envelope");
        }
    }

    private static double distance(SkyIslandLocalPosition first, SkyIslandLocalPosition second) {
        return Math.hypot(first.x() - second.x(), first.z() - second.z());
    }

    /** Explicit game-scale conversion and bounded candidate-geometry parameters, all dimensional values SI. */
    public record Configuration(
            double horizontalMetresPerLocalUnit,
            double verticalMetresPerNormalizedUnit,
            double verticalDatumMetres,
            double minimumBedSlope,
            double upstreamIncisionMetres,
            double bottomWidthMetres,
            double sideSlopeHorizontalToVertical,
            double bankfullDepthMetres,
            double valleyHalfWidthMetres,
            double maximumTerrainEditMetres) {
        public Configuration {
            requirePositive(horizontalMetresPerLocalUnit, "horizontal scale");
            requirePositive(verticalMetresPerNormalizedUnit, "vertical scale");
            if (!Double.isFinite(verticalDatumMetres)) {
                throw new IllegalArgumentException("vertical datum must be finite");
            }
            requirePositive(minimumBedSlope, "minimum bed slope");
            requireNonNegative(upstreamIncisionMetres, "upstream incision");
            requireNonNegative(bottomWidthMetres, "bottom width");
            requirePositive(sideSlopeHorizontalToVertical, "side slope");
            requirePositive(bankfullDepthMetres, "bankfull depth");
            requireNonNegative(maximumTerrainEditMetres, "maximum terrain edit");
            double bankEdge = bottomWidthMetres * 0.5
                    + sideSlopeHorizontalToVertical * bankfullDepthMetres;
            if (!Double.isFinite(valleyHalfWidthMetres) || valleyHalfWidthMetres <= bankEdge) {
                throw new IllegalArgumentException("valley half-width must exceed the bankfull section edge");
            }
        }

        private static void requirePositive(double value, String name) {
            if (!(value > 0.0) || !Double.isFinite(value)) {
                throw new IllegalArgumentException(name + " must be finite and positive");
            }
        }

        private static void requireNonNegative(double value, String name) {
            if (value < 0.0 || !Double.isFinite(value)) {
                throw new IllegalArgumentException(name + " must be finite and non-negative");
            }
        }
    }

    /** Immutable route, bed, and terrain candidate. Array accessors return defensive copies. */
    public static final class Candidate {
        private final List<SkyIslandLocalPosition> points;
        private final double[] stationsMetres;
        private final double[] rawBedElevationsMetres;
        private final double[] bedElevationsMetres;
        private final SkyIslandSemanticField terrain;
        private final Configuration configuration;

        private Candidate(
                List<SkyIslandLocalPosition> points,
                double[] stationsMetres,
                double[] rawBedElevationsMetres,
                double[] bedElevationsMetres,
                SkyIslandSemanticField terrain,
                Configuration configuration) {
            this.points = List.copyOf(points);
            this.stationsMetres = stationsMetres.clone();
            this.rawBedElevationsMetres = rawBedElevationsMetres.clone();
            this.bedElevationsMetres = bedElevationsMetres.clone();
            this.terrain = terrain;
            this.configuration = configuration;
        }

        public List<SkyIslandLocalPosition> routePoints() {
            return points;
        }

        public double[] stationsMetres() {
            return stationsMetres.clone();
        }

        public double[] rawBedElevationsMetres() {
            return rawBedElevationsMetres.clone();
        }

        public double[] bedElevationsMetres() {
            return bedElevationsMetres.clone();
        }

        public double totalLengthMetres() {
            return stationsMetres[stationsMetres.length - 1];
        }

        /** Samples the coupled channel/valley surface, blending to authored terrain at the valley edge. */
        public double sampleTerrainMetres(SkyIslandLocalPosition position) {
            Objects.requireNonNull(position, "position");
            double raw = normalizedElevation(terrain, position, configuration);
            Projection projection = nearestProjection(position);
            double lateral = projection.lateralDistanceMetres();
            if (lateral >= configuration.valleyHalfWidthMetres()) {
                return raw;
            }

            double bed = interpolate(bedElevationsMetres, projection.segmentIndex(), projection.fraction());
            double bottomHalfWidth = configuration.bottomWidthMetres() * 0.5;
            double bankEdge = bottomHalfWidth
                    + configuration.sideSlopeHorizontalToVertical() * configuration.bankfullDepthMetres();
            double target;
            double weight;
            if (lateral <= bankEdge) {
                target = bed + Math.max(0.0, lateral - bottomHalfWidth)
                        / configuration.sideSlopeHorizontalToVertical();
                weight = 1.0;
            } else {
                double t = (lateral - bankEdge)
                        / (configuration.valleyHalfWidthMetres() - bankEdge);
                weight = 1.0 - smoothstep(t);
                target = bed + configuration.bankfullDepthMetres();
            }
            double candidate = Math.min(raw, raw + weight * (target - raw));
            requireWithinEditEnvelope(raw, candidate, configuration.maximumTerrainEditMetres());
            return candidate;
        }


        /**
         * Lowers a source-controlled candidate rigidly without changing its grade or cross section.
         * All edited elevations remain subject to the original excavation envelope.
         */
        public Candidate loweredBy(double metres) {
            if (!Double.isFinite(metres) || metres < 0.0) {
                throw new IllegalArgumentException("candidate lowering must be finite and non-negative");
            }
            if (metres == 0.0) {
                return this;
            }
            double[] loweredBeds = bedElevationsMetres.clone();
            for (int i = 0; i < loweredBeds.length; i++) {
                loweredBeds[i] -= metres;
                requireWithinEditEnvelope(rawBedElevationsMetres[i], loweredBeds[i],
                        configuration.maximumTerrainEditMetres());
            }
            Configuration loweredConfiguration = new Configuration(
                    configuration.horizontalMetresPerLocalUnit(),
                    configuration.verticalMetresPerNormalizedUnit(),
                    configuration.verticalDatumMetres(),
                    configuration.minimumBedSlope(),
                    configuration.upstreamIncisionMetres() + metres,
                    configuration.bottomWidthMetres(),
                    configuration.sideSlopeHorizontalToVertical(),
                    configuration.bankfullDepthMetres(),
                    configuration.valleyHalfWidthMetres(),
                    configuration.maximumTerrainEditMetres());
            return new Candidate(points, stationsMetres, rawBedElevationsMetres, loweredBeds,
                    terrain, loweredConfiguration);
        }

        /** Solves from the standard boundary associated with the selected regime. */
        public Profile solve(
                double[] dischargeAtStationsCubicMetresPerSecond,
                double manningRoughness,
                double boundaryDepthMetres,
                SkyIslandStandardStepSolver.FlowRegime regime) {
            SkyIslandStandardStepSolver.BoundaryLocation boundaryLocation =
                    regime == SkyIslandStandardStepSolver.FlowRegime.SUBCRITICAL
                            ? SkyIslandStandardStepSolver.BoundaryLocation.DOWNSTREAM
                            : SkyIslandStandardStepSolver.BoundaryLocation.UPSTREAM;
            return solve(
                    dischargeAtStationsCubicMetresPerSecond,
                    manningRoughness,
                    boundaryDepthMetres,
                    regime,
                    boundaryLocation);
        }

        /** Solves the standard-step water profile against this candidate's bed and section geometry. */
        public Profile solve(
                double[] dischargeAtStationsCubicMetresPerSecond,
                double manningRoughness,
                double boundaryDepthMetres,
                SkyIslandStandardStepSolver.FlowRegime regime,
                SkyIslandStandardStepSolver.BoundaryLocation boundaryLocation) {
            Objects.requireNonNull(dischargeAtStationsCubicMetresPerSecond, "discharges");
            Objects.requireNonNull(regime, "regime");
            Objects.requireNonNull(boundaryLocation, "boundaryLocation");
            if (dischargeAtStationsCubicMetresPerSecond.length != stationsMetres.length) {
                throw new IllegalArgumentException("one discharge is required at each candidate station");
            }
            SkyIslandStandardStepSolver.CrossSection section = trapezoid(configuration);
            SkyIslandStandardStepSolver.CrossSection[] sections =
                    new SkyIslandStandardStepSolver.CrossSection[stationsMetres.length];
            Arrays.fill(sections, section);
            double[] depths = SkyIslandStandardStepSolver.solve(
                    stationsMetres,
                    bedElevationsMetres,
                    sections,
                    dischargeAtStationsCubicMetresPerSecond,
                    manningRoughness,
                    boundaryDepthMetres,
                    regime,
                    boundaryLocation);
            double[] stages = new double[depths.length];
            for (int i = 0; i < depths.length; i++) {
                stages[i] = bedElevationsMetres[i] + depths[i];
            }
            return new Profile(stationsMetres, bedElevationsMetres, depths, stages);
        }

        private Projection nearestProjection(SkyIslandLocalPosition position) {
            double bestDistance = Double.POSITIVE_INFINITY;
            Projection best = null;
            for (int i = 0; i < points.size() - 1; i++) {
                SkyIslandLocalPosition a = points.get(i);
                SkyIslandLocalPosition b = points.get(i + 1);
                double dx = b.x() - a.x();
                double dz = b.z() - a.z();
                double denominator = dx * dx + dz * dz;
                double fraction = ((position.x() - a.x()) * dx + (position.z() - a.z()) * dz)
                        / denominator;
                fraction = Math.max(0.0, Math.min(1.0, fraction));
                double projectedX = a.x() + fraction * dx;
                double projectedZ = a.z() + fraction * dz;
                double lateral = Math.hypot(position.x() - projectedX, position.z() - projectedZ)
                        * configuration.horizontalMetresPerLocalUnit();
                if (lateral < bestDistance) {
                    bestDistance = lateral;
                    best = new Projection(i, fraction, lateral);
                }
            }
            if (best == null) {
                throw new IllegalStateException("candidate route has no segments");
            }
            return best;
        }

        private double interpolate(double[] values, int segment, double fraction) {
            return values[segment] + fraction * (values[segment + 1] - values[segment]);
        }

        private static double smoothstep(double value) {
            double clamped = Math.max(0.0, Math.min(1.0, value));
            return clamped * clamped * (3.0 - 2.0 * clamped);
        }
    }

    /** Cross-sectional dimensions follow a trapezoid: bottom width b and side slope zH:1V. */
    private static SkyIslandStandardStepSolver.CrossSection trapezoid(Configuration configuration) {
        return new SkyIslandStandardStepSolver.CrossSection() {
            @Override
            public double area(double depthMetres) {
                double z = configuration.sideSlopeHorizontalToVertical();
                return configuration.bottomWidthMetres() * depthMetres + z * depthMetres * depthMetres;
            }

            @Override
            public double wettedPerimeter(double depthMetres) {
                double z = configuration.sideSlopeHorizontalToVertical();
                return configuration.bottomWidthMetres()
                        + 2.0 * depthMetres * Math.hypot(1.0, z);
            }

            @Override
            public double topWidth(double depthMetres) {
                return configuration.bottomWidthMetres()
                        + 2.0 * configuration.sideSlopeHorizontalToVertical() * depthMetres;
            }
        };
    }

    /** Solved profile in SI units. */
    public record Profile(
            double[] stationsMetres,
            double[] bedElevationsMetres,
            double[] depthsMetres,
            double[] waterSurfaceElevationsMetres) {
        public Profile {
            stationsMetres = stationsMetres.clone();
            bedElevationsMetres = bedElevationsMetres.clone();
            depthsMetres = depthsMetres.clone();
            waterSurfaceElevationsMetres = waterSurfaceElevationsMetres.clone();
        }

        @Override
        public double[] stationsMetres() {
            return stationsMetres.clone();
        }

        @Override
        public double[] bedElevationsMetres() {
            return bedElevationsMetres.clone();
        }

        @Override
        public double[] depthsMetres() {
            return depthsMetres.clone();
        }

        @Override
        public double[] waterSurfaceElevationsMetres() {
            return waterSurfaceElevationsMetres.clone();
        }
    }

    private record Projection(int segmentIndex, double fraction, double lateralDistanceMetres) {}
}
