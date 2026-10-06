package io.github.nidaba.skyforge.world;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Builds an exploratory continuous channel-bed and valley-terrain candidate before hydraulic
 * qualification.
 *
 * <p>The authored drainage graph and C2 centerlines remain the candidate's semantic authority.
 * Game-scale hydraulic geometry supplies the candidate bed; a bounded, no-fill cross-section
 * response constructs the associated dry valley surface around it. The returned reach sections and
 * terrain field are one immutable candidate so a hydraulic solve can be evaluated against the
 * geometry that would later be qualified. This planner does not grant realization authority and
 * does not use D2 head envelopes to choose or solve the candidate.
 */
public final class SkyIslandHydraulicLandformCandidatePlanner {
    private static final double EPSILON = 1.0e-12;

    private SkyIslandHydraulicLandformCandidatePlanner() {}

    public static Plan plan(
            SkyIslandDescriptor descriptor,
            SkyIslandGameScaleHydraulicCalibration calibration) {
        Objects.requireNonNull(descriptor, "descriptor");
        Objects.requireNonNull(calibration, "calibration");
        SkyIslandTerrainAwareRouteSolver.HydraulicRouteFeasibilityEnvelope feasibilityEnvelope =
                new SkyIslandTerrainAwareRouteSolver.HydraulicRouteFeasibilityEnvelope(
                        calibration.maximumDownstreamBedSlope() / descriptor.reliefBudget(),
                        calibration.bedIncisionScale()
                                * SkyIslandHydraulicGeometryCalibration.waterDepthPotential(1.0));
        List<String> rejectedResolutions = new ArrayList<>();
        for (int divisionsPerPlanningCell : new int[] {4, 8, 16}) {
            SkyIslandHydraulicGeometrySkeletonPlan skeleton =
                    SkyIslandHydraulicGeometrySkeletonPlanner.planHydraulicCandidate(
                            descriptor, divisionsPerPlanningCell, feasibilityEnvelope, calibration);
            try {
                return buildPlan(descriptor, calibration, skeleton);
            } catch (IllegalStateException failure) {
                String message = failure.getMessage();
                if (message == null
                        || !message.startsWith(
                                "bounded channel-bed candidate is infeasible under terrain, incision, and local-grade constraints")) {
                    throw failure;
                }
                rejectedResolutions.add(
                        divisionsPerPlanningCell + "x: " + message);
            }
        }
        throw new IllegalStateException(
                "no coupled drainage/bed candidate is feasible within the accepted corridor at route resolutions 4x/8x/16x"
                        + ";rejectedCandidates=" + String.join(" || ", rejectedResolutions));
    }

    private static Plan buildPlan(
            SkyIslandDescriptor descriptor,
            SkyIslandGameScaleHydraulicCalibration calibration,
            SkyIslandHydraulicGeometrySkeletonPlan skeleton) {
        double reliefMeters = descriptor.reliefBudget() * calibration.metersPerWorldUnit();
        List<ReachCandidate> reaches = new ArrayList<>(skeleton.reaches().size());
        for (SkyIslandHydraulicReachSkeleton reach : skeleton.reaches()) {
            List<SkyIslandGraduallyVariedFlowSolver.CrossSection> rawSections =
                    calibration.crossSections(descriptor, reach.samples(), reach.samples());
            CandidateBedResult bedResult =
                    conditionBedProfile(descriptor, reach, rawSections, calibration, reliefMeters);
            List<SkyIslandGraduallyVariedFlowSolver.CrossSection> sections = bedResult.sections();
            if (sections.size() != reach.samples().size()) {
                throw new IllegalStateException(
                        "candidate bed sections must match candidate drainage geometry");
            }
            for (int i = 0; i < sections.size(); i++) {
                double bedPotential = sections.get(i).bedElevationMeters() / reliefMeters;
                if (bedPotential < 0.0
                        || bedPotential >= reach.samples().get(i).terrainElevation()) {
                    throw new IllegalArgumentException(
                            "candidate bed is outside the no-fill normalized terrain domain at "
                                    + reach.geomorphicRoute().semanticReach().startCellIndex()
                                    + "->"
                                    + reach.geomorphicRoute().semanticReach().endCellIndex()
                                    + " sample "
                                    + i);
                }
            }
            reaches.add(new ReachCandidate(reach, sections, bedResult.qpResult()));
        }

        SkyIslandPreHydrologicTerrainField baseTerrain =
                SkyIslandPreHydrologicTerrainField.create(descriptor);
        SkyIslandSemanticField candidateTerrain =
                new CandidateTerrainField(
                        baseTerrain,
                        reaches,
                        descriptor.reliefBudget() * calibration.metersPerWorldUnit());
        return new Plan(descriptor, skeleton, reaches, candidateTerrain);
    }

    private static CandidateBedResult conditionBedProfile(
            SkyIslandDescriptor descriptor,
            SkyIslandHydraulicReachSkeleton reach,
            List<SkyIslandGraduallyVariedFlowSolver.CrossSection> rawSections,
            SkyIslandGameScaleHydraulicCalibration calibration,
            double reliefMeters) {
        int count = rawSections.size();
        double[] target = new double[count];
        double[] weight = new double[count];
        double[] lower = new double[count];
        double[] upper = new double[count];
        for (int i = 0; i < count; i++) {
            SkyIslandHydraulicGeometrySkeletonSample sample = reach.samples().get(i);
            SkyIslandGraduallyVariedFlowSolver.CrossSection section = rawSections.get(i);
            double surface = sample.terrainElevation() * reliefMeters;
            double maximumIncision = calibration.bedIncisionScale()
                    * sample.waterDepthPotential() * reliefMeters;
            target[i] = section.bedElevationMeters();
            lower[i] = Math.max(0.0, surface - maximumIncision);
            upper[i] = surface;
            double left = i == 0
                    ? rawSections.get(1).chainageMeters() - section.chainageMeters()
                    : section.chainageMeters() - rawSections.get(i - 1).chainageMeters();
            double right = i + 1 == count
                    ? section.chainageMeters() - rawSections.get(i - 1).chainageMeters()
                    : rawSections.get(i + 1).chainageMeters() - section.chainageMeters();
            weight[i] = 0.5 * (left + right);
        }

        List<SkyIslandHydraulicDifferenceConstraint> gradeConstraints =
                new ArrayList<>(count - 1);
        for (int i = 0; i + 1 < count; i++) {
            SkyIslandGraduallyVariedFlowSolver.CrossSection upstream = rawSections.get(i);
            SkyIslandGraduallyVariedFlowSolver.CrossSection downstream = rawSections.get(i + 1);
            double spacing = downstream.chainageMeters() - upstream.chainageMeters();
            double localBedRelief =
                    calibration.maximumDownstreamBedSlope() * spacing;
            // Preserve a downstream-lowering trend across the semantic reach, but do not make
            // every fine cross section a one-way step. Local bed reversals are admissible within
            // the same game-scale grade envelope; the candidate target and hydraulic solve decide
            // whether those local forms are useful and physically passable.
            gradeConstraints.add(new SkyIslandHydraulicDifferenceConstraint(
                    "local-bed-grade:" + i,
                    i,
                    i + 1,
                    -localBedRelief,
                    localBedRelief));
        }

        SkyIslandHydraulicQpResult qp =
                SkyIslandHydraulicBoundedQpSolver.solve(
                        new SkyIslandHydraulicBoundedQpProblem(
                                target, weight, lower, upper, gradeConstraints));
        if (qp.status() != SkyIslandHydraulicQpStatus.SOLVED) {
            double requiredIncisionScale = minimumFeasibleIncisionScale(
                    target, weight, upper, reach.samples(), reliefMeters,
                    gradeConstraints, calibration.bedIncisionScale());
            throw new IllegalStateException(
                    "bounded channel-bed candidate is infeasible under terrain, incision, local-grade, and reach-trend constraints"
                            + ";reach=" + reach.geomorphicRoute().semanticReach().startCellIndex()
                            + "->" + reach.geomorphicRoute().semanticReach().endCellIndex()
                            + ";requiredUniformIncisionScale=" + requiredIncisionScale
                            + ";diagnostic=" + qp.diagnostic().orElse("none"));
        }

        double[] bed = qp.solution();
        List<SkyIslandGraduallyVariedFlowSolver.CrossSection> sections =
                new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            SkyIslandHydraulicGeometrySkeletonSample sample = reach.samples().get(i);
            double surface = sample.terrainElevation() * reliefMeters;
            double maximumIncision = calibration.bedIncisionScale()
                    * sample.waterDepthPotential() * reliefMeters;
            if (bed[i] > surface + 1.0e-8
                    || surface - bed[i] > maximumIncision + 1.0e-8
                    || bed[i] < 0.0) {
                throw new IllegalStateException(
                        "bounded candidate bed escaped no-fill, incision, or vertical-domain bounds at section "
                                + i);
            }
            SkyIslandGraduallyVariedFlowSolver.CrossSection raw = rawSections.get(i);
            sections.add(new SkyIslandGraduallyVariedFlowSolver.CrossSection(
                    raw.chainageMeters(),
                    bed[i],
                    raw.dischargeCubicMetersPerSecond(),
                    raw.bottomWidthMeters(),
                    raw.sideSlopeHorizontalToVertical()));
        }
        return new CandidateBedResult(List.copyOf(sections), qp);
    }

    /**
     * Measures how much uniform bed-incision authority this fixed candidate path would require
     * before changing calibration. A finite result is diagnostic only; it does not relax the
     * authored candidate envelope or approve the resulting terrain.
     */
    private static double minimumFeasibleIncisionScale(
            double[] target,
            double[] weight,
            double[] upper,
            List<SkyIslandHydraulicGeometrySkeletonSample> samples,
            double reliefMeters,
            List<SkyIslandHydraulicDifferenceConstraint> gradeConstraints,
            double currentScale) {
        double low = currentScale;
        double high = currentScale;
        while (high < 1.0e6 && !bedProfileSolvesAtScale(
                target, weight, upper, samples, reliefMeters, gradeConstraints, high)) {
            low = high;
            high = Math.min(1.0e6, high * 2.0);
        }
        if (!bedProfileSolvesAtScale(
                target, weight, upper, samples, reliefMeters, gradeConstraints, high)) {
            return Double.POSITIVE_INFINITY;
        }
        for (int i = 0; i < 48; i++) {
            double middle = low + 0.5 * (high - low);
            if (bedProfileSolvesAtScale(
                    target, weight, upper, samples, reliefMeters, gradeConstraints, middle)) {
                high = middle;
            } else {
                low = middle;
            }
        }
        return high;
    }

    private static boolean bedProfileSolvesAtScale(
            double[] target,
            double[] weight,
            double[] upper,
            List<SkyIslandHydraulicGeometrySkeletonSample> samples,
            double reliefMeters,
            List<SkyIslandHydraulicDifferenceConstraint> gradeConstraints,
            double incisionScale) {
        double[] lower = new double[samples.size()];
        for (int i = 0; i < samples.size(); i++) {
            double surface = samples.get(i).terrainElevation() * reliefMeters;
            double maximumIncision =
                    incisionScale * samples.get(i).waterDepthPotential() * reliefMeters;
            lower[i] = Math.max(0.0, surface - maximumIncision);
        }
        return SkyIslandHydraulicBoundedQpSolver.solve(
                        new SkyIslandHydraulicBoundedQpProblem(
                                target, weight, lower, upper, gradeConstraints))
                .status() == SkyIslandHydraulicQpStatus.SOLVED;
    }

    /** One semantic reach with a bed profile that is shared by terrain construction and hydraulics. */
    public record ReachCandidate(
            SkyIslandHydraulicReachSkeleton skeleton,
            List<SkyIslandGraduallyVariedFlowSolver.CrossSection> sections,
            SkyIslandHydraulicQpResult bedGeometrySolve) {
        public ReachCandidate {
            skeleton = Objects.requireNonNull(skeleton, "skeleton");
            sections = List.copyOf(Objects.requireNonNull(sections, "sections"));
            bedGeometrySolve = Objects.requireNonNull(bedGeometrySolve, "bedGeometrySolve");
            if (bedGeometrySolve.status() != SkyIslandHydraulicQpStatus.SOLVED) {
                throw new IllegalArgumentException("candidate bed geometry must have a solved QP witness");
            }
            if (sections.size() != skeleton.samples().size() || sections.size() < 2) {
                throw new IllegalArgumentException(
                        "candidate sections must match at least two skeleton samples");
            }
            for (int i = 0; i < sections.size(); i++) {
                Objects.requireNonNull(sections.get(i), "candidate section");
                if (i > 0
                        && !(sections.get(i).chainageMeters()
                                > sections.get(i - 1).chainageMeters())) {
                    throw new IllegalArgumentException(
                            "candidate section chainage must increase strictly downstream");
                }
            }
        }

        public int startCellIndex() {
            return skeleton.geomorphicRoute().semanticReach().startCellIndex();
        }

        public int endCellIndex() {
            return skeleton.geomorphicRoute().semanticReach().endCellIndex();
        }
    }

    /** Immutable pre-solve geometry packet. Hydraulic and D2 qualification remain separate steps. */
    public record Plan(
            SkyIslandDescriptor descriptor,
            SkyIslandHydraulicGeometrySkeletonPlan skeletonPlan,
            List<ReachCandidate> reaches,
            SkyIslandSemanticField terrain) {
        public Plan {
            descriptor = Objects.requireNonNull(descriptor, "descriptor");
            skeletonPlan = Objects.requireNonNull(skeletonPlan, "skeletonPlan");
            reaches = List.copyOf(Objects.requireNonNull(reaches, "reaches"));
            terrain = Objects.requireNonNull(terrain, "terrain");
            if (!descriptor.equals(skeletonPlan.descriptor())
                    || !descriptor.equals(skeletonPlan.geomorphicNetwork().descriptor())) {
                throw new IllegalArgumentException(
                        "candidate descriptor must match its authored drainage graph");
            }
            if (reaches.size() != skeletonPlan.reaches().size()) {
                throw new IllegalArgumentException(
                        "every authored semantic reach must have one joint bed/valley candidate");
            }
            for (int i = 0; i < reaches.size(); i++) {
                ReachCandidate candidate = Objects.requireNonNull(reaches.get(i), "reach candidate");
                if (candidate.skeleton() != skeletonPlan.reaches().get(i)) {
                    throw new IllegalArgumentException(
                            "candidate reach order/identity must match the authored graph plan");
                }
            }
        }

        public ReachCandidate requireReach(int startCellIndex, int endCellIndex) {
            return reaches.stream()
                    .filter(reach -> reach.startCellIndex() == startCellIndex
                            && reach.endCellIndex() == endCellIndex)
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException(
                            "candidate semantic reach not found: "
                                    + startCellIndex + "->" + endCellIndex));
        }
    }

    private record CandidateBedResult(
            List<SkyIslandGraduallyVariedFlowSolver.CrossSection> sections,
            SkyIslandHydraulicQpResult qpResult) {}

    private static final class CandidateTerrainField implements SkyIslandSemanticField {
        private final SkyIslandSemanticField baseTerrain;
        private final List<ReachCandidate> reaches;
        private final double reliefMeters;

        private CandidateTerrainField(
                SkyIslandSemanticField baseTerrain,
                List<ReachCandidate> reaches,
                double reliefMeters) {
            this.baseTerrain = Objects.requireNonNull(baseTerrain, "baseTerrain");
            if (!Double.isFinite(reliefMeters) || reliefMeters <= 0.0) {
                throw new IllegalArgumentException("reliefMeters must be finite and positive");
            }
            this.reliefMeters = reliefMeters;
            this.reaches = reaches.stream()
                    .map(reach -> Objects.requireNonNull(reach, "reach"))
                    .sorted(Comparator
                            .comparingInt(ReachCandidate::startCellIndex)
                            .thenComparingInt(ReachCandidate::endCellIndex))
                    .toList();
        }

        @Override
        public double sample(SkyIslandLocalPosition position) {
            Objects.requireNonNull(position, "position");
            double original = clamp01(baseTerrain.sample(position));
            CandidateSection selected = null;
            for (ReachCandidate reach : reaches) {
                CandidateSection candidate = section(reach, position, original);
                if (candidate == null) {
                    continue;
                }
                if (selected == null) {
                    selected = candidate;
                    continue;
                }
                if (!sharesSemanticNode(candidate.reach(), selected.reach())) {
                    throw new IllegalStateException(
                            "candidate valley overlap lacks an authored junction owner: "
                                    + candidate.reach().startCellIndex() + "->"
                                    + candidate.reach().endCellIndex() + " and "
                                    + selected.reach().startCellIndex() + "->"
                                    + selected.reach().endCellIndex());
                }
                selected = candidate.targetPotential() < selected.targetPotential()
                        ? candidate
                        : selected;
            }
            return selected == null ? original : Math.min(original, selected.targetPotential());
        }

        private CandidateSection section(
                ReachCandidate reach,
                SkyIslandLocalPosition position,
                double originalTerrain) {
            List<SkyIslandLocalPosition> points = reach.skeleton().centerline().points();
            Projection projection = nearestProjection(points, position);
            if (projection == null) {
                return null;
            }
            int i = projection.segmentIndex();
            double f = projection.segmentFraction();
            SkyIslandHydraulicGeometrySkeletonSample a = reach.skeleton().samples().get(i);
            SkyIslandHydraulicGeometrySkeletonSample b = reach.skeleton().samples().get(i + 1);
            double bankfullHalfWidth = lerp(a.bankfullHalfWidth(), b.bankfullHalfWidth(), f);
            double valleyHalfWidth =
                    bankfullHalfWidth
                            * valleyMultiplier(reach.skeleton().geomorphicRoute()
                                    .semanticReach().profiles(), lerp(a.stationFraction(), b.stationFraction(), f));
            if (projection.distance() > valleyHalfWidth + EPSILON) {
                return null;
            }

            // Cross-section bed elevations are converted with the same relief scale used to
            // generate the SI sections; the solver and terrain candidate therefore share one bed.
            double bedPotential = normalizedBedPotential(reach, i, f, this.reliefMeters);
            double centerTerrain = lerp(a.terrainElevation(), b.terrainElevation(), f);
            double distance = projection.distance();
            double target;
            if (distance <= bankfullHalfWidth + EPSILON) {
                double lateral = bankfullHalfWidth <= EPSILON
                        ? 1.0
                        : clamp01(distance / bankfullHalfWidth);
                double exponent = crossSectionExponent(
                        reach.skeleton().geomorphicRoute().semanticReach().profiles(),
                        lerp(a.stationFraction(), b.stationFraction(), f));
                target = bedPotential
                        + Math.max(0.0, centerTerrain - bedPotential)
                                * Math.pow(lateral, exponent);
            } else {
                double recovery = clamp01(
                        (distance - bankfullHalfWidth)
                                / Math.max(EPSILON, valleyHalfWidth - bankfullHalfWidth));
                target = centerTerrain
                        + (originalTerrain - centerTerrain) * smoothstep(recovery);
            }
            double maximumLateralCut = Math.max(0.0, centerTerrain - bedPotential);
            target = Math.max(target, originalTerrain - maximumLateralCut);
            if (originalTerrain - target <= EPSILON) {
                return null;
            }
            return new CandidateSection(reach, Math.min(originalTerrain, clamp01(target)));
        }

        private static double normalizedBedPotential(
                ReachCandidate reach, int sampleIndex, double fraction, double reliefMeters) {
            double bedAtA = reach.sections().get(sampleIndex).bedElevationMeters();
            double bedAtB = reach.sections().get(sampleIndex + 1).bedElevationMeters();
            return (bedAtA + (bedAtB - bedAtA) * fraction) / reliefMeters;
        }

        private static boolean sharesSemanticNode(ReachCandidate first, ReachCandidate second) {
            return first.startCellIndex() == second.startCellIndex()
                    || first.startCellIndex() == second.endCellIndex()
                    || first.endCellIndex() == second.startCellIndex()
                    || first.endCellIndex() == second.endCellIndex();
        }

        private static double valleyMultiplier(
                List<SkyIslandChannelProfile> profiles, double station) {
            return switch (profileKind(profiles, station)) {
                case ALLUVIAL -> 3.5;
                case INCISED -> 2.5;
                case CASCADE -> 1.8;
            };
        }

        private static double crossSectionExponent(
                List<SkyIslandChannelProfile> profiles, double station) {
            return switch (profileKind(profiles, station)) {
                case ALLUVIAL -> 2.0;
                case INCISED -> 1.45;
                case CASCADE -> 1.20;
            };
        }

        private static SkyIslandChannelProfileKind profileKind(
                List<SkyIslandChannelProfile> profiles, double station) {
            int index = Math.min(
                    profiles.size() - 1,
                    (int) Math.floor(Math.max(0.0, Math.min(0.999999999, station))
                            * profiles.size()));
            return profiles.get(index).kind();
        }

        private static Projection nearestProjection(
                List<SkyIslandLocalPosition> points, SkyIslandLocalPosition position) {
            Projection best = null;
            for (int i = 0; i + 1 < points.size(); i++) {
                SkyIslandLocalPosition a = points.get(i);
                SkyIslandLocalPosition b = points.get(i + 1);
                double dx = b.x() - a.x();
                double dz = b.z() - a.z();
                double lengthSquared = dx * dx + dz * dz;
                double fraction = lengthSquared <= EPSILON
                        ? 0.0
                        : clamp01(((position.x() - a.x()) * dx + (position.z() - a.z()) * dz)
                                / lengthSquared);
                double x = a.x() + fraction * dx;
                double z = a.z() + fraction * dz;
                double distance = Math.hypot(position.x() - x, position.z() - z);
                if (best == null || distance < best.distance() - EPSILON
                        || (Math.abs(distance - best.distance()) <= EPSILON
                                && i < best.segmentIndex())) {
                    best = new Projection(i, fraction, distance);
                }
            }
            return best;
        }

        private static double smoothstep(double value) {
            double x = clamp01(value);
            return x * x * (3.0 - 2.0 * x);
        }

        private static double lerp(double a, double b, double f) {
            return a + (b - a) * f;
        }

        private static double clamp01(double value) {
            return Math.max(0.0, Math.min(1.0, value));
        }
    }

    private record CandidateSection(ReachCandidate reach, double targetPotential) {}

    private record Projection(int segmentIndex, double segmentFraction, double distance) {}
}
