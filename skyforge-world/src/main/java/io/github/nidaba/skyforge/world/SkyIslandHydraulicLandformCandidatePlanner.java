package io.github.nidaba.skyforge.world;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

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
                                "bounded channel-bed candidate is infeasible within authored trapezoid geometry")) {
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

    /**
     * Rebuilds bounded bed/valley candidates from hydraulic feedback controls and returns the
     * best candidate found by minimizing caller-supplied physical energy/momentum residuals.
     *
     * <p>The feedback vector contains two broad bed-shape amplitudes per profile kind, three
     * endpoint-vanishing local bed modes across the reach, three bounded CASCADE width modes, and
     * one edge-outlet ramp. Bed modes preserve shared semantic reach endpoints.
     * Bed, cross-section, and valley terrain are rebuilt as one candidate; D2 qualification stays
     * outside this hydraulic objective.
     */
    public static FeedbackResult planWithHydraulicFeedback(
            SkyIslandDescriptor descriptor,
            SkyIslandGameScaleHydraulicCalibration calibration,
            Function<Plan, double[]> hydraulicResidualEvaluator,
            double residualTolerance,
            int maximumIterations) {
        Objects.requireNonNull(descriptor, "descriptor");
        Objects.requireNonNull(calibration, "calibration");
        Objects.requireNonNull(hydraulicResidualEvaluator, "hydraulicResidualEvaluator");
        Plan initial = plan(descriptor, calibration);
        if (!Double.isFinite(residualTolerance) || residualTolerance <= 0.0 || maximumIterations < 1) {
            throw new IllegalArgumentException("tolerance and candidate limit must be positive");
        }
        int cascadeKind = SkyIslandChannelProfileKind.CASCADE.ordinal();
        double maximumAmplitude = 0.0;
        for (ReachCandidate reach : initial.reaches()) {
            List<SkyIslandChannelProfile> profiles =
                    reach.skeleton().geomorphicRoute().semanticReach().profiles();
            for (SkyIslandHydraulicGeometrySkeletonSample sample : reach.skeleton().samples()) {
                if (profileKind(profiles, sample.stationFraction())
                        == SkyIslandChannelProfileKind.CASCADE) {
                    maximumAmplitude = Math.max(
                            maximumAmplitude, calibration.maximumCrossSectionDepthMeters(sample));
                }
            }
        }
        if (!(maximumAmplitude > 0.0) || !Double.isFinite(maximumAmplitude)) {
            throw new IllegalStateException("hydraulic bed feedback has no positive cascade geometry envelope");
        }

        // Sweep the broad CASCADE bed mode against both signs of small endpoint-safe local bed
        // adjustment, so the ALLUVIAL near-critical section can move either way. A bounded,
        // localized CASCADE width response targets the measured terminal expansion. Shared reach
        // endpoints and hydraulic acceptance rules remain unchanged.
        CascadeShapeTrial[] trials = {
            new CascadeShapeTrial(0.119, 0.05, 0.05, 0.00, -0.05, 0.70),
            new CascadeShapeTrial(0.119, 0.05, -0.05, 0.00, -0.05, 0.70),
            new CascadeShapeTrial(0.119, 0.15, 0.05, 0.00, -0.05, 0.70),
            new CascadeShapeTrial(0.119, 0.15, 0.05, 0.05, -0.05, 0.70),
            new CascadeShapeTrial(0.119, 0.15, 0.05, -0.05, -0.05, 0.70),
            new CascadeShapeTrial(0.119, 0.25, 0.10, 0.00, -0.05, 0.70)
        };
        double[] bestControls = new double[feedbackControlCount()];
        double[] bestResiduals = validatedResiduals(
                hydraulicResidualEvaluator.apply(initial), -1);
        Plan selected = initial;
        double bestNorm = residualNorm(bestResiduals);
        int evaluated = 0;
        for (int candidateIndex = 0;
                candidateIndex < trials.length && evaluated < maximumIterations;
                candidateIndex++) {
            CascadeShapeTrial trial = trials[candidateIndex];
            double[] controls = new double[feedbackControlCount()];
            int localControlStart = 2 * SkyIslandChannelProfileKind.values().length;
            controls[cascadeKind] = -trial.broadBedLoweringFraction() * maximumAmplitude;
            for (int localMode = 0; localMode < 3; localMode++) {
                controls[localControlStart + localMode] =
                        trial.localBedAdjustmentFraction(localMode) * maximumAmplitude;
            }
            // The third local mode is outlet-centered; widen that approach smoothly to reduce
            // the measured terminal width step without collapsing the trapezoid bottom width.
            controls[localControlStart + 3 + 2] = trial.widthScaleOffset();
            controls[localControlStart + 6] = trial.terminalDropFraction() * maximumAmplitude;
            Plan candidate;
            try {
                candidate = buildPlan(descriptor, calibration, initial.skeletonPlan(), controls);
            } catch (IllegalArgumentException | IllegalStateException infeasibleCandidate) {
                String message = infeasibleCandidate.getMessage();
                boolean boundedBedFailure = message != null
                        && (message.startsWith(
                                        "bounded channel-bed candidate is infeasible within authored trapezoid geometry")
                                || message.startsWith(
                                        "candidate bed is outside the no-fill normalized terrain domain")
                                || message.startsWith(
                                        "conditioned channel bed would collapse the authored trapezoid bottom width"));
                if (!boundedBedFailure) {
                    throw infeasibleCandidate;
                }
                evaluated++;
                continue;
            }
            double[] residuals = validatedResiduals(
                    hydraulicResidualEvaluator.apply(candidate), bestResiduals.length);
            double norm = residualNorm(residuals);
            evaluated++;
            if (norm < bestNorm) {
                bestNorm = norm;
                bestControls = controls;
                bestResiduals = residuals;
                selected = candidate;
            }
        }
        var result = new SkyIslandHydraulicResidualFeedbackSolver.Result(
                bestControls,
                bestResiduals,
                evaluated,
                bestNorm <= residualTolerance);
        return new FeedbackResult(selected, result);
    }

    private static int feedbackControlCount() {
        return 2 * SkyIslandChannelProfileKind.values().length + 7;
    }

    private record CascadeShapeTrial(
            double widthScaleOffset,
            double broadBedLoweringFraction,
            double inletBedAdjustmentFraction,
            double middleBedAdjustmentFraction,
            double outletBedAdjustmentFraction,
            double terminalDropFraction) {
        private double localBedAdjustmentFraction(int localMode) {
            return switch (localMode) {
                case 0 -> inletBedAdjustmentFraction;
                case 1 -> middleBedAdjustmentFraction;
                case 2 -> outletBedAdjustmentFraction;
                default -> throw new IllegalArgumentException("local bed mode must be in 0..2");
            };
        }
    }

    static double[] profileFeedbackWeights(
            List<SkyIslandChannelProfileKind> profileKinds, double station) {
        Objects.requireNonNull(profileKinds, "profileKinds");
        if (profileKinds.isEmpty() || !Double.isFinite(station)) {
            throw new IllegalArgumentException("profile kinds and finite station are required");
        }
        double clampedStation = Math.max(0.0, Math.min(0.999999999, station));
        int profileCount = profileKinds.size();
        double scaledStation = clampedStation * profileCount;
        int index = Math.min(profileCount - 1, (int) Math.floor(scaledStation));
        double withinProfile = scaledStation - index;
        double[] weights = new double[SkyIslandChannelProfileKind.values().length];
        SkyIslandChannelProfileKind current = Objects.requireNonNull(
                profileKinds.get(index), "profile kind");
        weights[current.ordinal()] = 1.0;
        double blendHalfWidth = 0.25;
        if (index > 0
                && withinProfile < blendHalfWidth
                && profileKinds.get(index - 1) != current) {
            SkyIslandChannelProfileKind previous = profileKinds.get(index - 1);
            double currentWeight = smoothStep01(
                    (withinProfile + blendHalfWidth) / (2.0 * blendHalfWidth));
            weights[previous.ordinal()] = 1.0 - currentWeight;
            weights[current.ordinal()] = currentWeight;
        } else if (index + 1 < profileCount
                && withinProfile > 1.0 - blendHalfWidth
                && profileKinds.get(index + 1) != current) {
            SkyIslandChannelProfileKind next = profileKinds.get(index + 1);
            double nextWeight = smoothStep01(
                    (withinProfile - (1.0 - blendHalfWidth)) / (2.0 * blendHalfWidth));
            weights[current.ordinal()] = 1.0 - nextWeight;
            weights[next.ordinal()] = nextWeight;
        }
        return weights;
    }

    private static double smoothStep01(double value) {
        double x = Math.max(0.0, Math.min(1.0, value));
        return x * x * (3.0 - 2.0 * x);
    }

    private static double reachLocalBedModeCenter(int localMode) {
        return switch (localMode) {
            case 0 -> 0.15;
            case 1 -> 0.50;
            case 2 -> 0.90;
            default -> throw new IllegalArgumentException("local bed mode must be in 0..2");
        };
    }

    private static double cascadeWidthModeCenter(int localMode) {
        return switch (localMode) {
            case 0 -> 0.25;
            case 1 -> 0.50;
            case 2 -> 0.88;
            default -> throw new IllegalArgumentException("cascade width mode must be in 0..2");
        };
    }

    private static double cascadeWidthModeRadius(int localMode) {
        return localMode == 2 ? 0.04 : 0.20;
    }

    private static double localizedReachBump(double station, double center, double radius) {
        double normalizedDistance = Math.abs(station - center) / radius;
        if (normalizedDistance >= 1.0) {
            return 0.0;
        }
        return 0.5 * (1.0 + Math.cos(Math.PI * normalizedDistance));
    }

    private static double[] validatedResiduals(double[] values, int expectedLength) {
        Objects.requireNonNull(values, "hydraulic residuals");
        if (values.length == 0 || (expectedLength >= 0 && values.length != expectedLength)) {
            throw new IllegalArgumentException(
                    "hydraulic residual vector must have a stable positive length");
        }
        double[] copy = values.clone();
        for (double value : copy) {
            if (!Double.isFinite(value)) {
                throw new IllegalArgumentException("hydraulic residuals must be finite");
            }
        }
        return copy;
    }

    private static double residualNorm(double[] residuals) {
        double sum = 0.0;
        for (double residual : residuals) {
            sum += residual * residual;
        }
        return Math.sqrt(sum);
    }

    static Plan planAtResolution(
            SkyIslandDescriptor descriptor,
            SkyIslandGameScaleHydraulicCalibration calibration,
            int divisionsPerPlanningCell) {
        Objects.requireNonNull(descriptor, "descriptor");
        Objects.requireNonNull(calibration, "calibration");
        if (divisionsPerPlanningCell < 1) {
            throw new IllegalArgumentException("candidate route resolution must be positive");
        }
        SkyIslandTerrainAwareRouteSolver.HydraulicRouteFeasibilityEnvelope feasibilityEnvelope =
                new SkyIslandTerrainAwareRouteSolver.HydraulicRouteFeasibilityEnvelope(
                        calibration.maximumDownstreamBedSlope() / descriptor.reliefBudget(),
                        calibration.bedIncisionScale()
                                * SkyIslandHydraulicGeometryCalibration.waterDepthPotential(1.0));
        SkyIslandHydraulicGeometrySkeletonPlan skeleton =
                SkyIslandHydraulicGeometrySkeletonPlanner.planHydraulicCandidate(
                        descriptor, divisionsPerPlanningCell, feasibilityEnvelope, calibration);
        return buildPlan(descriptor, calibration, skeleton);
    }

    static Plan planPriorityFloodAtResolution(
            SkyIslandDescriptor descriptor,
            SkyIslandGameScaleHydraulicCalibration calibration,
            int divisionsPerPlanningCell) {
        Objects.requireNonNull(descriptor, "descriptor");
        Objects.requireNonNull(calibration, "calibration");
        if (divisionsPerPlanningCell < 2) {
            throw new IllegalArgumentException("candidate route resolution must be at least two");
        }
        SkyIslandTerrainAwareRouteSolver.HydraulicRouteFeasibilityEnvelope envelope =
                new SkyIslandTerrainAwareRouteSolver.HydraulicRouteFeasibilityEnvelope(
                        calibration.maximumDownstreamBedSlope() / descriptor.reliefBudget(),
                        calibration.bedIncisionScale()
                                * SkyIslandHydraulicGeometryCalibration.waterDepthPotential(1.0));
        SkyIslandHydraulicGeometrySkeletonPlan base =
                SkyIslandHydraulicGeometrySkeletonPlanner.planHydraulicCandidate(
                        descriptor, divisionsPerPlanningCell, envelope, calibration);
        SkyIslandGeomorphicChannelNetworkPlan network = base.geomorphicNetwork();
        SkyIslandSemanticField terrain = SkyIslandPreHydrologicTerrainField.create(descriptor);
        SkyIslandSemanticField interiority =
                SkyIslandSemanticFieldSet.create(descriptor).interiority();
        double corridorHalfWidth = network.planningSpacing()
                * SkyIslandGeomorphicChannelNetworkPlanner.ROUTE_CORRIDOR_SPACING_FRACTION;
        List<SkyIslandGeomorphicReachRoute> routes = new ArrayList<>(network.routes().size());
        for (SkyIslandGeomorphicReachRoute route : network.routes()) {
            SkyIslandSemanticChannelReach semantic = route.semanticReach();
            SkyIslandGeomorphicNetworkNode start =
                    network.requireNode(semantic.startCellIndex());
            SkyIslandGeomorphicNetworkNode end =
                    network.requireNode(semantic.endCellIndex());
            SkyIslandGeomorphicCandidateRoute priorityFlood =
                    SkyIslandTerrainAwareRouteSolver.solveByPriorityFlood(
                            terrain,
                            interiority,
                            semantic.guidancePoints(),
                            network.planningSpacing(),
                            corridorHalfWidth,
                            new SkyIslandGeomorphicRouteAnchor(start.physicalPosition(), 0.0),
                            new SkyIslandGeomorphicRouteAnchor(end.physicalPosition(), 0.0));
            routes.add(new SkyIslandGeomorphicReachRoute(semantic, priorityFlood));
        }
        SkyIslandGeomorphicChannelNetworkPlan candidateNetwork =
                new SkyIslandGeomorphicChannelNetworkPlan(
                        descriptor, network.planningSpacing(), network.nodes(), routes);
        SkyIslandHydraulicGeometrySkeletonPlan candidateSkeleton =
                SkyIslandHydraulicGeometrySkeletonPlanner.plan(
                        descriptor, candidateNetwork, terrain, interiority);
        return buildPlan(descriptor, calibration, candidateSkeleton);
    }

    private static Plan buildPlan(
            SkyIslandDescriptor descriptor,
            SkyIslandGameScaleHydraulicCalibration calibration,
            SkyIslandHydraulicGeometrySkeletonPlan skeleton) {
        return buildPlan(descriptor, calibration, skeleton, new double[feedbackControlCount()]);
    }

    private static Plan buildPlan(
            SkyIslandDescriptor descriptor,
            SkyIslandGameScaleHydraulicCalibration calibration,
            SkyIslandHydraulicGeometrySkeletonPlan skeleton,
            double[] feedbackControls) {
        if (feedbackControls.length != feedbackControlCount()) {
            throw new IllegalArgumentException(
                    "hydraulic feedback control vector has an invalid length");
        }
        double reliefMeters = descriptor.reliefBudget() * calibration.metersPerWorldUnit();
        List<Integer> sourceNodes = skeleton.geomorphicNetwork().nodes().stream()
                .filter(node -> node.kind() == SkyIslandGeomorphicNetworkNodeKind.SOURCE)
                .map(SkyIslandGeomorphicNetworkNode::cellIndex)
                .toList();
        List<Integer> edgeOutletNodes = SkyIslandChannelTerminalFatePlanner.plan(
                        descriptor, skeleton.geomorphicNetwork()).stream()
                .filter(fate -> fate.kind() == SkyIslandChannelTerminalFateKind.EDGE_OUTLET)
                .map(SkyIslandChannelTerminalFate::channelTerminalCellIndex)
                .toList();
        List<ReachCandidate> reaches = new ArrayList<>(skeleton.reaches().size());
        for (SkyIslandHydraulicReachSkeleton reach : skeleton.reaches()) {
            List<SkyIslandGraduallyVariedFlowSolver.CrossSection> rawSections =
                    calibration.crossSections(descriptor, reach.samples(), reach.samples());
            boolean sourceReach = sourceNodes.contains(
                    reach.geomorphicRoute().semanticReach().startCellIndex());
            boolean edgeOutletReach = edgeOutletNodes.contains(
                    reach.geomorphicRoute().semanticReach().endCellIndex());
            CandidateBedResult bedResult = conditionBedProfile(
                    descriptor, reach, rawSections, calibration, reliefMeters, sourceReach,
                    edgeOutletReach, feedbackControls);
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
            reaches.add(new ReachCandidate(
                    reach, sections, bedResult.sectionWidthScales(), bedResult.qpResult()));
        }

        SkyIslandPreHydrologicTerrainField baseTerrain =
                SkyIslandPreHydrologicTerrainField.create(descriptor);
        SkyIslandSemanticField candidateTerrain =
                new CandidateTerrainField(
                        baseTerrain,
                        reaches,
                        descriptor.reliefBudget() * calibration.metersPerWorldUnit(),
                        calibration.sideSlopeHorizontalToVertical(),
                        calibration.metersPerWorldUnit());
        return new Plan(descriptor, skeleton, reaches, candidateTerrain);
    }

    private static SkyIslandChannelProfileKind profileKind(
            List<SkyIslandChannelProfile> profiles,
            double station) {
        int index = Math.min(
                profiles.size() - 1,
                (int) Math.floor(Math.max(0.0, Math.min(0.999999999, station)) * profiles.size()));
        return profiles.get(index).kind();
    }

    private static CandidateBedResult conditionBedProfile(
            SkyIslandDescriptor descriptor,
            SkyIslandHydraulicReachSkeleton reach,
            List<SkyIslandGraduallyVariedFlowSolver.CrossSection> rawSections,
            SkyIslandGameScaleHydraulicCalibration calibration,
            double reliefMeters,
            boolean sourceReach,
            boolean edgeOutletReach,
            double[] feedbackControls) {
        int count = rawSections.size();
        double[] target = new double[count];
        double[] weight = new double[count];
        double[] lower = new double[count];
        double[] upper = new double[count];
        List<SkyIslandChannelProfile> profiles =
                reach.geomorphicRoute().semanticReach().profiles();
        double[][] profileWeightsAtSection = new double[count][];
        List<SkyIslandChannelProfileKind> profileKinds =
                profiles.stream().map(SkyIslandChannelProfile::kind).toList();
        for (int i = 0; i < count; i++) {
            SkyIslandHydraulicGeometrySkeletonSample sample = reach.samples().get(i);
            SkyIslandGraduallyVariedFlowSolver.CrossSection section = rawSections.get(i);
            double surface = sample.terrainElevation() * reliefMeters;
            double maximumIncision = Math.min(
                    surface, calibration.maximumCrossSectionDepthMeters(sample));
            double[] profileWeights =
                    profileFeedbackWeights(profileKinds, sample.stationFraction());
            profileWeightsAtSection[i] = profileWeights;
            double cascadeWeight = profileWeights[SkyIslandChannelProfileKind.CASCADE.ordinal()];
            double modeStation = bedFeedbackStation(sample.stationFraction(), sourceReach, count);
            double targetOffset = 0.0;
            for (SkyIslandChannelProfileKind kind : SkyIslandChannelProfileKind.values()) {
                targetOffset += profileWeights[kind.ordinal()]
                        * (feedbackControls[kind.ordinal()] * Math.sin(Math.PI * modeStation)
                                + feedbackControls[SkyIslandChannelProfileKind.values().length + kind.ordinal()]
                                        * Math.sin(2.0 * Math.PI * modeStation));
            }
            target[i] = section.bedElevationMeters() + targetOffset;
            for (int localMode = 0; localMode < 3; localMode++) {
                target[i] += Math.sin(Math.PI * modeStation) * feedbackControls[
                                    2 * SkyIslandChannelProfileKind.values().length + localMode]
                            * localizedReachBump(modeStation, reachLocalBedModeCenter(localMode), 0.20);
            }
            if (edgeOutletReach) {
                double normalizedOutletRamp =
                        Math.max(0.0, Math.min(1.0, (modeStation - 0.60) / 0.40));
                double outletRamp = normalizedOutletRamp * normalizedOutletRamp
                        * (3.0 - 2.0 * normalizedOutletRamp);
                target[i] -= feedbackControls[
                                2 * SkyIslandChannelProfileKind.values().length + 6]
                        * outletRamp;
            }
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

        // Keep terrain fitting independent from the flow law except at a declared source:
        // normal depth is only a valid source boundary when the candidate's first-quarter reach
        // has a finite positive energy slope. Derive its minimum from Manning's equation at the
        // maximum geometrically admissible depth, and cap it by the accepted reach-grade limit.
        // Energy, jump, and CASCADE solvers remain the admission authority downstream.
        List<SkyIslandHydraulicDifferenceConstraint> gradeConstraints = new ArrayList<>();
        // Do not impose a globally monotone bed: local adverse grades and drops are valid parts
        // of static cascade morphology. Source reaches retain only their explicit normal-depth
        // control window; the exact hydraulic transition solvers decide whether the full profile
        // is physically admissible.
        if (sourceReach) {
            int windowEnd = Math.min(
                    count - 1, Math.max(2, (int) Math.ceil((count - 1) * 0.25)));
            SkyIslandGraduallyVariedFlowSolver.CrossSection source = rawSections.getFirst();
            double maximumDepth = calibration.maximumCrossSectionDepthMeters(reach.samples().getFirst());
            double minimumSourceSlope = SkyIslandManningHydraulics.uniformFlowEnergySlope(
                    source.dischargeCubicMetersPerSecond(),
                    calibration.manningRoughness(),
                    maximumDepth,
                    source.bottomWidthMeters(),
                    source.sideSlopeHorizontalToVertical());
            double windowLength = rawSections.get(windowEnd).chainageMeters()
                    - source.chainageMeters();
            double maximumWindowDrop = calibration.maximumDownstreamBedSlope() * windowLength;
            double minimumWindowDrop = minimumSourceSlope * windowLength;
            if (!(windowLength > 0.0)
                    || !Double.isFinite(minimumWindowDrop)
                    || minimumWindowDrop > maximumWindowDrop) {
                throw new IllegalStateException(
                        "bounded channel-bed candidate is infeasible within authored trapezoid geometry"
                                + ";source normal-depth bed window has no physically admissible grade"
                                + ";reach=" + reach.geomorphicRoute().semanticReach().startCellIndex()
                                + "->" + reach.geomorphicRoute().semanticReach().endCellIndex()
                                + ";minimumSlope=" + minimumSourceSlope
                                + ";maximumSlope=" + calibration.maximumDownstreamBedSlope());
            }
            gradeConstraints.add(new SkyIslandHydraulicDifferenceConstraint(
                    "source-normal-depth-window",
                    0,
                    windowEnd,
                    minimumWindowDrop,
                    maximumWindowDrop));
        }

        // A CASCADE reach is an authored chute: its bed may flatten into pools, but must not
        // climb downstream. Keep this morphological grade constraint local to adjacent samples
        // both owned by CASCADE; ordinary reaches and transitions retain their own controls.
        for (int i = 0; i + 1 < count; i++) {
            if (profileKind(profiles, reach.samples().get(i).stationFraction())
                            != SkyIslandChannelProfileKind.CASCADE
                    && profileKind(profiles, reach.samples().get(i + 1).stationFraction())
                            != SkyIslandChannelProfileKind.CASCADE) {
                continue;
            }
            gradeConstraints.add(new SkyIslandHydraulicDifferenceConstraint(
                    "cascade-non-rising-bed-" + i,
                    i,
                    i + 1,
                    0.0,
                    reliefMeters));
        }

        // Do not infer a locally uniform supercritical regime from bed grade alone. CASCADE
        // reaches may contain rapidly varied flow; the standard-step/jump solver evaluates the
        // candidate's actual depth and energy profile under its authored boundary conditions.
        // The candidate bed remains bounded by the existing terrain, incision, and maximum-grade
        // constraints above/below, and is accepted only when physical closure succeeds.

        SkyIslandHydraulicQpResult qp =
                SkyIslandHydraulicBoundedQpSolver.solve(
                        new SkyIslandHydraulicBoundedQpProblem(
                                target, weight, lower, upper, gradeConstraints));
        if (qp.status() != SkyIslandHydraulicQpStatus.SOLVED) {
            throw new IllegalStateException(
                    "bounded channel-bed candidate is infeasible within authored trapezoid geometry"
                            + ";reach=" + reach.geomorphicRoute().semanticReach().startCellIndex()
                            + "->" + reach.geomorphicRoute().semanticReach().endCellIndex()
                            + ";diagnostic=" + qp.diagnostic().orElse("none"));
        }

        double[] bed = qp.solution();
        List<SkyIslandGraduallyVariedFlowSolver.CrossSection> sections =
                new ArrayList<>(count);
        List<Double> sectionWidthScales = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            SkyIslandHydraulicGeometrySkeletonSample sample = reach.samples().get(i);
            double surface = sample.terrainElevation() * reliefMeters;
            double maximumIncision = Math.min(
                    surface, calibration.maximumCrossSectionDepthMeters(sample));
            if (bed[i] > surface + 1.0e-8
                    || surface - bed[i] > maximumIncision + 1.0e-8
                    || bed[i] < 0.0) {
                throw new IllegalStateException(
                        "bounded candidate bed escaped no-fill, incision, or vertical-domain bounds at section "
                                + i);
            }
            SkyIslandGraduallyVariedFlowSolver.CrossSection raw = rawSections.get(i);
            double sectionWidthScale = 1.0;
            double cascadeWeight =
                    profileWeightsAtSection[i][SkyIslandChannelProfileKind.CASCADE.ordinal()];
            if (cascadeWeight > 0.0) {
                double modeStation = bedFeedbackStation(
                        reach.samples().get(i).stationFraction(), sourceReach, count);
                int widthControlStart = 2 * SkyIslandChannelProfileKind.values().length + 3;
                for (int localMode = 0; localMode < 3; localMode++) {
                    sectionWidthScale += cascadeWeight
                            * feedbackControls[widthControlStart + localMode]
                            * localizedReachBump(
                                    modeStation,
                                    cascadeWidthModeCenter(localMode),
                                    cascadeWidthModeRadius(localMode));
                }
            }
            if (!Double.isFinite(sectionWidthScale)
                    || sectionWidthScale < 0.75
                    || sectionWidthScale > 1.25) {
                throw new IllegalArgumentException(
                        "candidate CASCADE pool width scale must remain within 0.75..1.25");
            }
            double fullBankfullWidthMeters =
                    2.0 * reach.samples().get(i).bankfullHalfWidth()
                            * sectionWidthScale
                            * calibration.metersPerWorldUnit();
            double bottomWidthMeters = fullBankfullWidthMeters
                    - 2.0 * calibration.sideSlopeHorizontalToVertical() * (surface - bed[i]);
            if (!Double.isFinite(bottomWidthMeters) || bottomWidthMeters <= 0.0) {
                throw new IllegalStateException(
                        "conditioned channel bed would collapse the authored trapezoid bottom width at section "
                                + i);
            }
            sections.add(new SkyIslandGraduallyVariedFlowSolver.CrossSection(
                    raw.chainageMeters(),
                    bed[i],
                    raw.dischargeCubicMetersPerSecond(),
                    bottomWidthMeters,
                    calibration.sideSlopeHorizontalToVertical()));
            sectionWidthScales.add(sectionWidthScale);
        }
        return new CandidateBedResult(List.copyOf(sections), List.copyOf(sectionWidthScales), qp);
    }

    /** One semantic reach with a bed profile that is shared by terrain construction and hydraulics. */
    public record ReachCandidate(
            SkyIslandHydraulicReachSkeleton skeleton,
            List<SkyIslandGraduallyVariedFlowSolver.CrossSection> sections,
            List<Double> sectionWidthScales,
            SkyIslandHydraulicQpResult bedGeometrySolve) {
        public ReachCandidate {
            skeleton = Objects.requireNonNull(skeleton, "skeleton");
            sections = List.copyOf(Objects.requireNonNull(sections, "sections"));
            sectionWidthScales = List.copyOf(
                    Objects.requireNonNull(sectionWidthScales, "sectionWidthScales"));
            bedGeometrySolve = Objects.requireNonNull(bedGeometrySolve, "bedGeometrySolve");
            if (sectionWidthScales.size() != sections.size()) {
                throw new IllegalArgumentException("candidate width scales must match section count");
            }
            for (double scale : sectionWidthScales) {
                if (!Double.isFinite(scale) || scale < 0.75 || scale > 1.25) {
                    throw new IllegalArgumentException("candidate section width scale must remain within 0.75..1.25");
                }
            }
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

        public double channelWidthScale() {
            return sectionWidthScales.stream().mapToDouble(Double::doubleValue).average().orElse(1.0);
        }

        public double channelWidthScaleAtSection(int index) {
            return sectionWidthScales.get(index);
        }
    }

    /** Immutable pre-solve geometry packet. Hydraulic and D2 qualification remain separate steps. */
    private static double bedFeedbackStation(double station, boolean sourceReach, int sectionCount) {
        double adjustedStation = Math.max(0.0, Math.min(1.0, station));
        if (sourceReach) {
            // Keep the exact discrete normal-depth source-control window untouched. The feedback
            // acts downstream of that window and returns to zero at the shared reach endpoint.
            int windowEnd = Math.min(
                    sectionCount - 1, Math.max(2, (int) Math.ceil((sectionCount - 1) * 0.25)));
            double windowFraction = (double) windowEnd / (sectionCount - 1);
            adjustedStation = (adjustedStation - windowFraction) / (1.0 - windowFraction);
            if (adjustedStation <= 0.0) {
                return 0.0;
            }
        }
        return Math.max(0.0, Math.min(1.0, adjustedStation));
    }

    public record FeedbackResult(
            Plan plan, SkyIslandHydraulicResidualFeedbackSolver.Result optimization) {
        public FeedbackResult {
            plan = Objects.requireNonNull(plan, "plan");
            optimization = Objects.requireNonNull(optimization, "optimization");
        }

        public boolean residualsConverged() {
            return optimization.converged();
        }
    }

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
            List<Double> sectionWidthScales,
            SkyIslandHydraulicQpResult qpResult) {}

    private static final class CandidateTerrainField implements SkyIslandSemanticField {
        private final SkyIslandSemanticField baseTerrain;
        private final List<ReachCandidate> reaches;
        private final double reliefMeters;
        private final double sideSlopeHorizontalToVertical;
        private final double metersPerWorldUnit;

        private CandidateTerrainField(
                SkyIslandSemanticField baseTerrain,
                List<ReachCandidate> reaches,
                double reliefMeters,
                double sideSlopeHorizontalToVertical,
                double metersPerWorldUnit) {
            this.baseTerrain = Objects.requireNonNull(baseTerrain, "baseTerrain");
            if (!Double.isFinite(reliefMeters) || reliefMeters <= 0.0) {
                throw new IllegalArgumentException("reliefMeters must be finite and positive");
            }
            this.reliefMeters = reliefMeters;
            this.sideSlopeHorizontalToVertical = sideSlopeHorizontalToVertical;
            this.metersPerWorldUnit = metersPerWorldUnit;
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
            double widthA = (reach.sections().get(i).bottomWidthMeters()
                            + 2.0 * this.sideSlopeHorizontalToVertical
                                    * (a.terrainElevation() * this.reliefMeters
                                            - reach.sections().get(i).bedElevationMeters()))
                    / (2.0 * this.metersPerWorldUnit);
            double widthB = (reach.sections().get(i + 1).bottomWidthMeters()
                            + 2.0 * this.sideSlopeHorizontalToVertical
                                    * (b.terrainElevation() * this.reliefMeters
                                            - reach.sections().get(i + 1).bedElevationMeters()))
                    / (2.0 * this.metersPerWorldUnit);
            double bankfullHalfWidth = lerp(widthA, widthB, f);
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
