package io.github.nidaba.skyforge.world;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Partitions F2B reaches around finite F3B/F3C transition ownership, solves each remaining ordinary
 * span, and independently re-applies the shared D1/D2 measurement and violation logic.
 *
 * <p>F3D is evidence only. It does not mutate terrain or grant transition/realization authority.
 */
public final class SkyIslandOrdinarySpanPlanner {
    private static final double EPSILON = 1.0e-10;

    private SkyIslandOrdinarySpanPlanner() {}

    public static SkyIslandOrdinarySpanPlan plan(SkyIslandDescriptor descriptor) {
        Objects.requireNonNull(descriptor, "descriptor");
        return plan(
                descriptor,
                SkyIslandConfluenceHeadCompatibilityPlanner.plan(descriptor),
                SkyIslandCascadeHeadCompatibilityPlanner.plan(descriptor),
                SkyIslandPreHydrologicTerrainField.create(descriptor),
                SkyIslandGeomorphicQualificationPolicy.firstEvidenceBacked());
    }

    static SkyIslandOrdinarySpanPlan plan(
            SkyIslandDescriptor descriptor,
            SkyIslandConfluenceHeadCompatibilityPlan confluencePlan,
            SkyIslandCascadeHeadCompatibilityPlan cascadePlan,
            SkyIslandSemanticField terrain,
            SkyIslandGeomorphicQualificationPolicy policy) {
        Objects.requireNonNull(descriptor, "descriptor");
        Objects.requireNonNull(confluencePlan, "confluencePlan");
        Objects.requireNonNull(cascadePlan, "cascadePlan");
        Objects.requireNonNull(terrain, "terrain");
        Objects.requireNonNull(policy, "policy");
        if (!descriptor.equals(confluencePlan.descriptor())
                || !descriptor.equals(cascadePlan.descriptor())) {
            throw new IllegalArgumentException(
                    "transition plans must match ordinary-span descriptor");
        }

        SkyIslandHydraulicGeometrySkeletonPlan skeletonPlan =
                cascadePlan.transitionGeometry().topology().skeletonPlan();
        SkyIslandGeomorphicChannelNetworkPlan network = skeletonPlan.geomorphicNetwork();
        double planningSpacing = network.planningSpacing();

        Map<BoundaryKey, SkyIslandOrdinarySpanBoundary> confluenceBoundaries =
                confluenceBoundaries(confluencePlan);
        Map<Long, List<SkyIslandCascadeHeadCompatibilityOutcome>> cascadesByReach =
                cascadesByReach(cascadePlan);
        Map<Integer, SkyIslandChannelTerminalFate> terminalFates =
                terminalFates(descriptor, network);

        List<SkyIslandOrdinarySpanOutcome> outcomes = new ArrayList<>();
        for (SkyIslandHydraulicReachSkeleton reach : skeletonPlan.reaches()) {
            SkyIslandSemanticChannelReach semantic =
                    reach.geomorphicRoute().semanticReach();
            SkyIslandOrdinarySpanBoundary initial =
                    initialBoundary(reach, network, confluenceBoundaries);
            SkyIslandOrdinarySpanBoundary terminal =
                    terminalBoundary(
                            reach, network, confluenceBoundaries, terminalFates);

            double terminalArc = terminal.state().arcLength();
            SkyIslandOrdinarySpanBoundary cursor = initial;
            double cursorArc = cursor.state().arcLength();

            List<SkyIslandCascadeHeadCompatibilityOutcome> cascades =
                    cascadesByReach.getOrDefault(reachIdentity(semantic), List.of());
            for (SkyIslandCascadeHeadCompatibilityOutcome cascade : cascades) {
                SkyIslandHydraulicCascadeTransitionSite site =
                        cascade.geometry().transitionSite();
                double startArc = site.upstreamBoundary().arcLength();
                double endArc = site.downstreamBoundary().arcLength();

                if (endArc <= cursorArc + EPSILON) {
                    continue;
                }
                if (startArc >= terminalArc - EPSILON) {
                    break;
                }

                if (startArc < cursorArc + EPSILON) {
                    if (endArc >= terminalArc - EPSILON) {
                        cursorArc = terminalArc;
                        break;
                    }
                    cursor =
                            SkyIslandOrdinarySpanBoundary.deferred(
                                    site.downstreamBoundary(),
                                    "overlapping upstream transition ownership requires combined solve");
                    cursorArc = endArc;
                    continue;
                }

                boolean overlapsDownstreamTransition = endArc > terminalArc - EPSILON;
                SkyIslandOrdinarySpanBoundary cascadeUp =
                        overlapsDownstreamTransition
                                ? SkyIslandOrdinarySpanBoundary.deferred(
                                        site.upstreamBoundary(),
                                        "overlapping downstream transition ownership requires combined solve")
                                : cascadeUpstreamBoundary(cascade);
                addSpanIfPositive(
                        descriptor,
                        reach,
                        cursor,
                        cascadeUp,
                        terrain,
                        policy,
                        planningSpacing,
                        outcomes);

                if (overlapsDownstreamTransition) {
                    cursorArc = terminalArc;
                    break;
                }

                cursor = cascadeDownstreamBoundary(cascade);
                cursorArc = endArc;
            }

            if (cursorArc < terminalArc - EPSILON) {
                addSpanIfPositive(
                        descriptor,
                        reach,
                        cursor,
                        terminal,
                        terrain,
                        policy,
                        planningSpacing,
                        outcomes);
            }
        }

        return new SkyIslandOrdinarySpanPlan(
                descriptor, confluencePlan, cascadePlan, outcomes);
    }

    private static void addSpanIfPositive(
            SkyIslandDescriptor descriptor,
            SkyIslandHydraulicReachSkeleton parent,
            SkyIslandOrdinarySpanBoundary upstream,
            SkyIslandOrdinarySpanBoundary downstream,
            SkyIslandSemanticField terrain,
            SkyIslandGeomorphicQualificationPolicy policy,
            double planningSpacing,
            List<SkyIslandOrdinarySpanOutcome> outcomes) {
        if (downstream.state().arcLength() <= upstream.state().arcLength() + EPSILON) {
            return;
        }
        SkyIslandOrdinaryHydraulicSpan span = slice(parent, upstream, downstream);
        outcomes.add(solve(
                descriptor, span, terrain, policy, planningSpacing));
    }

    private static SkyIslandOrdinarySpanOutcome solve(
            SkyIslandDescriptor descriptor,
            SkyIslandOrdinaryHydraulicSpan span,
            SkyIslandSemanticField terrain,
            SkyIslandGeomorphicQualificationPolicy policy,
            double planningSpacing) {
        if (span.boundaryDeferred()) {
            String diagnostic =
                    List.of(span.upstreamBoundary(), span.downstreamBoundary()).stream()
                            .filter(boundary ->
                                    boundary.status()
                                            == SkyIslandOrdinarySpanBoundaryStatus.DEFERRED)
                            .map(boundary -> boundary.diagnostic().orElseThrow())
                            .distinct()
                            .reduce((a, b) -> a + "; " + b)
                            .orElse("ordinary span boundary remains transition-deferred");
            return unsolved(
                    span,
                    SkyIslandOrdinarySpanStatus.BOUNDARY_DEFERRED,
                    Optional.empty(),
                    diagnostic);
        }

        SkyIslandGeomorphicProfileLimits limits =
                policy.limits(span.qualificationClass());
        List<SkyIslandHydraulicGeometrySkeletonSample> samples = span.samples();
        List<SkyIslandLocalPosition> points =
                samples.stream()
                        .map(SkyIslandHydraulicGeometrySkeletonSample::position)
                        .toList();
        double[] target = new double[samples.size()];
        double[] weight = quadratureWeights(samples);
        double[] lower = new double[samples.size()];
        double[] upper = new double[samples.size()];

        for (int i = 0; i < samples.size(); i++) {
            SkyIslandHydraulicGeometrySkeletonSample sample = samples.get(i);
            Vector tangent = tangent(points, i);
            Vector normal = new Vector(-tangent.z(), tangent.x());
            SkyIslandHydraulicHeadEnvelope envelope =
                    SkyIslandHydraulicHeadEnvelopePlanner.evaluateForKind(
                            descriptor,
                            span.sampleProfileKinds().get(i),
                            sample.position(),
                            sample.bankfullHalfWidth(),
                            sample.waterDepthPotential(),
                            sample.terrainElevation(),
                            normal.x(),
                            normal.z(),
                            terrain,
                            limits);
            target[i] = envelope.targetHead();
            lower[i] = envelope.lowerHead();
            upper[i] = envelope.upperHead();

            Optional<Double> fixed =
                    i == 0
                            ? span.upstreamBoundary().fixedHeadWorldUnits()
                            : i == samples.size() - 1
                                    ? span.downstreamBoundary().fixedHeadWorldUnits()
                                    : Optional.empty();
            if (fixed.isPresent()) {
                double head = fixed.orElseThrow();
                if (head < lower[i] - EPSILON || head > upper[i] + EPSILON) {
                    return unsolved(
                            span,
                            SkyIslandOrdinarySpanStatus.INFEASIBLE,
                            Optional.empty(),
                            "transition boundary head lies outside D2 pointwise envelope at sample "
                                    + i);
                }
                lower[i] = head;
                upper[i] = head;
                target[i] = head;
            }

            if (lower[i] > upper[i] + EPSILON) {
                return unsolved(
                        span,
                        SkyIslandOrdinarySpanStatus.INFEASIBLE,
                        Optional.empty(),
                        "D2-derived pointwise head interval is empty at sample " + i);
            }
            if (lower[i] > upper[i]) {
                double common = 0.5 * (lower[i] + upper[i]);
                lower[i] = common;
                upper[i] = common;
            }
        }

        List<SkyIslandHydraulicDifferenceConstraint> constraints =
                new ArrayList<>(Math.max(0, samples.size() - 1));
        for (int i = 0; i + 1 < samples.size(); i++) {
            double ds = samples.get(i + 1).arcLength() - samples.get(i).arcLength();
            if (!(ds > 0.0)) {
                throw new IllegalStateException(
                        "ordinary-span F2B arc length must increase strictly");
            }
            constraints.add(new SkyIslandHydraulicDifferenceConstraint(
                    span.parentReachStartCellIndex()
                            + "->"
                            + span.parentReachEndCellIndex()
                            + ":ordinary-span:"
                            + String.format(java.util.Locale.ROOT, "%.6f", span.parentStartStationFraction())
                            + "-"
                            + String.format(java.util.Locale.ROOT, "%.6f", span.parentEndStationFraction())
                            + ":grade:"
                            + i,
                    i,
                    i + 1,
                    0.0,
                    limits.maximumLongitudinalGrade() * ds));
        }

        SkyIslandHydraulicBoundedQpProblem problem =
                new SkyIslandHydraulicBoundedQpProblem(
                        target, weight, lower, upper, constraints);
        SkyIslandHydraulicQpResult qp =
                SkyIslandHydraulicBoundedQpSolver.solve(problem);
        if (qp.status() == SkyIslandHydraulicQpStatus.INFEASIBLE) {
            return unsolved(
                    span,
                    SkyIslandOrdinarySpanStatus.INFEASIBLE,
                    Optional.of(qp),
                    qp.diagnostic().orElse("bounded ordinary-span QP is infeasible"));
        }
        if (qp.status() == SkyIslandHydraulicQpStatus.NUMERICAL_FAILURE) {
            return unsolved(
                    span,
                    SkyIslandOrdinarySpanStatus.NUMERICAL_FAILURE,
                    Optional.of(qp),
                    qp.diagnostic().orElse("bounded ordinary-span QP numerical failure"));
        }

        List<SkyIslandHydraulicGeometrySample> solved =
                reconstruct(descriptor, span, qp.solution());
        SkyIslandGeomorphicMeasurements measurements =
                SkyIslandGeomorphicReachDiagnosticsPlanner.measureGeometry(
                        descriptor,
                        points,
                        solved,
                        span.sampleProfileKinds(),
                        terrain,
                        planningSpacing);
        List<SkyIslandGeomorphicQualificationViolation> violations =
                SkyIslandGeomorphicQualificationEvaluator.violations(
                        measurements, limits);
        SkyIslandOrdinarySpanStatus status =
                violations.isEmpty()
                        ? SkyIslandOrdinarySpanStatus.SOLVED_QUALIFIED
                        : SkyIslandOrdinarySpanStatus.SOLVED_REJECTED;
        return new SkyIslandOrdinarySpanOutcome(
                span,
                status,
                Optional.of(qp),
                solved,
                Optional.of(measurements),
                violations,
                Optional.empty());
    }

    private static List<SkyIslandHydraulicGeometrySample> reconstruct(
            SkyIslandDescriptor descriptor,
            SkyIslandOrdinaryHydraulicSpan span,
            double[] solvedHead) {
        if (solvedHead.length != span.samples().size()) {
            throw new IllegalArgumentException(
                    "solved ordinary-span head count must match span samples");
        }
        double relief = descriptor.reliefBudget();
        List<SkyIslandHydraulicGeometrySample> result =
                new ArrayList<>(span.samples().size());
        for (int i = 0; i < span.samples().size(); i++) {
            SkyIslandHydraulicGeometrySkeletonSample source = span.samples().get(i);
            double surfacePotential = solvedHead[i] / relief;
            double bedPotential =
                    (solvedHead[i] - source.waterDepthPotential() * relief) / relief;
            if (surfacePotential < -EPSILON
                    || surfacePotential > 1.0 + EPSILON
                    || bedPotential < -EPSILON
                    || bedPotential >= surfacePotential) {
                throw new IllegalStateException(
                        "ordinary-span solution escaped authored vertical domain");
            }
            surfacePotential = clamp01(surfacePotential);
            bedPotential = clamp01(bedPotential);
            result.add(new SkyIslandHydraulicGeometrySample(
                    source.position(),
                    source.stationFraction(),
                    source.relativeDischarge(),
                    source.bankfullHalfWidth(),
                    source.waterDepthPotential(),
                    source.terrainElevation(),
                    surfacePotential,
                    bedPotential,
                    Math.max(0.0, source.terrainElevation() - bedPotential)));
        }
        return List.copyOf(result);
    }

    private static SkyIslandOrdinaryHydraulicSpan slice(
            SkyIslandHydraulicReachSkeleton parent,
            SkyIslandOrdinarySpanBoundary upstream,
            SkyIslandOrdinarySpanBoundary downstream) {
        SkyIslandSemanticChannelReach semantic =
                parent.geomorphicRoute().semanticReach();
        double startArc = upstream.state().arcLength();
        double endArc = downstream.state().arcLength();
        double length = endArc - startArc;
        double startFraction = startArc / parent.pathLength();
        double endFraction = endArc / parent.pathLength();

        List<SkyIslandHydraulicGeometrySkeletonSample> localSamples = new ArrayList<>();
        List<SkyIslandChannelProfileKind> sampleKinds = new ArrayList<>();
        localSamples.add(localSample(upstream.state(), 0.0, 0.0));
        sampleKinds.add(ordinaryKindAtBoundary(semantic.profiles(), startFraction, true));

        for (SkyIslandHydraulicGeometrySkeletonSample sample : parent.samples()) {
            if (sample.arcLength() <= startArc + EPSILON
                    || sample.arcLength() >= endArc - EPSILON) {
                continue;
            }
            double localArc = sample.arcLength() - startArc;
            localSamples.add(new SkyIslandHydraulicGeometrySkeletonSample(
                    sample.position(),
                    localArc,
                    localArc / length,
                    sample.relativeDischarge(),
                    sample.bankfullHalfWidth(),
                    sample.waterDepthPotential(),
                    sample.terrainElevation()));
            SkyIslandChannelProfileKind kind =
                    profileKind(semantic.profiles(), sample.arcLength() / parent.pathLength());
            if (kind == SkyIslandChannelProfileKind.CASCADE) {
                throw new IllegalStateException(
                        "ordinary span retained a CASCADE interior sample");
            }
            sampleKinds.add(kind);
        }

        localSamples.add(localSample(downstream.state(), length, 1.0));
        sampleKinds.add(ordinaryKindAtBoundary(semantic.profiles(), endFraction, false));

        List<SkyIslandChannelProfileKind> qualificationKinds =
                overlappingOrdinaryKinds(
                        semantic.profiles(), startFraction, endFraction);
        return new SkyIslandOrdinaryHydraulicSpan(
                semantic.startCellIndex(),
                semantic.endCellIndex(),
                startFraction,
                endFraction,
                startArc,
                endArc,
                localSamples,
                sampleKinds,
                SkyIslandGeomorphicQualificationClass.classifyKinds(qualificationKinds),
                upstream,
                downstream);
    }

    private static List<SkyIslandChannelProfileKind> overlappingOrdinaryKinds(
            List<SkyIslandChannelProfile> profiles,
            double startFraction,
            double endFraction) {
        List<SkyIslandChannelProfileKind> result = new ArrayList<>();
        int n = profiles.size();
        for (int i = 0; i < n; i++) {
            double a = (double) i / n;
            double b = (double) (i + 1) / n;
            if (Math.min(endFraction, b) - Math.max(startFraction, a) <= EPSILON) {
                continue;
            }
            SkyIslandChannelProfileKind kind = profiles.get(i).kind();
            if (kind == SkyIslandChannelProfileKind.CASCADE) {
                throw new IllegalStateException(
                        "ordinary span overlaps authored CASCADE profile interval");
            }
            result.add(kind);
        }
        if (result.isEmpty()) {
            throw new IllegalStateException(
                    "ordinary span does not overlap an ordinary semantic profile");
        }
        return List.copyOf(result);
    }

    private static SkyIslandChannelProfileKind ordinaryKindAtBoundary(
            List<SkyIslandChannelProfile> profiles,
            double stationFraction,
            boolean downstreamSide) {
        int n = profiles.size();
        int index;
        if (downstreamSide) {
            index = Math.min(
                    n - 1,
                    (int) Math.floor(Math.min(1.0 - EPSILON, stationFraction + EPSILON) * n));
        } else {
            index = Math.max(
                    0,
                    (int) Math.ceil(Math.max(EPSILON, stationFraction - EPSILON) * n) - 1);
        }
        SkyIslandChannelProfileKind kind = profiles.get(index).kind();
        if (kind == SkyIslandChannelProfileKind.CASCADE) {
            throw new IllegalStateException(
                    "ordinary span boundary ordinary side resolves to CASCADE");
        }
        return kind;
    }

    private static SkyIslandChannelProfileKind profileKind(
            List<SkyIslandChannelProfile> profiles,
            double stationFraction) {
        int index =
                Math.min(
                        profiles.size() - 1,
                        (int) Math.floor(
                                Math.max(0.0, Math.min(0.999999999, stationFraction))
                                        * profiles.size()));
        return profiles.get(index).kind();
    }

    private static SkyIslandHydraulicGeometrySkeletonSample localSample(
            SkyIslandHydraulicTransitionBoundaryState state,
            double localArc,
            double localFraction) {
        return new SkyIslandHydraulicGeometrySkeletonSample(
                state.position(),
                localArc,
                localFraction,
                state.relativeDischarge(),
                state.bankfullHalfWidth(),
                state.waterDepthPotential(),
                state.terrainElevation());
    }

    private static Map<BoundaryKey, SkyIslandOrdinarySpanBoundary> confluenceBoundaries(
            SkyIslandConfluenceHeadCompatibilityPlan plan) {
        Map<BoundaryKey, SkyIslandOrdinarySpanBoundary> result = new HashMap<>();
        for (SkyIslandConfluenceHeadCompatibilityOutcome outcome : plan.outcomes()) {
            Map<SkyIslandHydraulicTransitionLegGeometry, Double> solvedHeads = new HashMap<>();
            for (SkyIslandConfluenceLegHeadSolution solution : outcome.legSolutions()) {
                solvedHeads.put(solution.leg(), solution.finiteBoundaryHeadWorldUnits());
            }
            for (SkyIslandHydraulicTransitionLegGeometry leg : outcome.geometry().legs()) {
                SkyIslandHydraulicTransitionBoundaryState state = leg.finiteBoundary();
                SkyIslandOrdinarySpanBoundary boundary;
                if (outcome.status() == SkyIslandConfluenceHeadCompatibilityStatus.SOLVED) {
                    Double head = solvedHeads.get(leg);
                    if (head == null) {
                        throw new IllegalStateException(
                                "solved confluence omitted finite leg head");
                    }
                    boundary = SkyIslandOrdinarySpanBoundary.fixed(state, head);
                } else {
                    boundary = SkyIslandOrdinarySpanBoundary.deferred(
                            state,
                            "confluence transition "
                                    + outcome.status().name()
                                    + outcome.diagnostic()
                                            .map(value -> ": " + value)
                                            .orElse(""));
                }
                BoundaryKey key = new BoundaryKey(
                        reachIdentity(
                                state.reachStartCellIndex(),
                                state.reachEndCellIndex()),
                        state.role());
                if (result.put(key, boundary) != null) {
                    throw new IllegalStateException(
                            "duplicate confluence boundary for incident reach");
                }
            }
        }
        return Map.copyOf(result);
    }

    private static Map<Long, List<SkyIslandCascadeHeadCompatibilityOutcome>> cascadesByReach(
            SkyIslandCascadeHeadCompatibilityPlan plan) {
        Map<Long, List<SkyIslandCascadeHeadCompatibilityOutcome>> mutable = new HashMap<>();
        for (SkyIslandCascadeHeadCompatibilityOutcome outcome : plan.outcomes()) {
            SkyIslandHydraulicCascadeTransitionSite site =
                    outcome.geometry().transitionSite();
            mutable.computeIfAbsent(
                            reachIdentity(
                                    site.reachStartCellIndex(),
                                    site.reachEndCellIndex()),
                            ignored -> new ArrayList<>())
                    .add(outcome);
        }
        Map<Long, List<SkyIslandCascadeHeadCompatibilityOutcome>> result = new HashMap<>();
        for (Map.Entry<Long, List<SkyIslandCascadeHeadCompatibilityOutcome>> entry :
                mutable.entrySet()) {
            entry.getValue().sort(Comparator.comparingDouble(
                    outcome ->
                            outcome.geometry()
                                    .transitionSite()
                                    .upstreamBoundary()
                                    .arcLength()));
            result.put(entry.getKey(), List.copyOf(entry.getValue()));
        }
        return Map.copyOf(result);
    }

    private static Map<Integer, SkyIslandChannelTerminalFate> terminalFates(
            SkyIslandDescriptor descriptor,
            SkyIslandGeomorphicChannelNetworkPlan network) {
        Map<Integer, SkyIslandChannelTerminalFate> result = new HashMap<>();
        for (SkyIslandChannelTerminalFate fate :
                SkyIslandChannelTerminalFatePlanner.plan(descriptor, network)) {
            result.put(fate.channelTerminalCellIndex(), fate);
        }
        return Map.copyOf(result);
    }

    private static SkyIslandOrdinarySpanBoundary initialBoundary(
            SkyIslandHydraulicReachSkeleton reach,
            SkyIslandGeomorphicChannelNetworkPlan network,
            Map<BoundaryKey, SkyIslandOrdinarySpanBoundary> confluenceBoundaries) {
        SkyIslandSemanticChannelReach semantic =
                reach.geomorphicRoute().semanticReach();
        SkyIslandGeomorphicNetworkNode node =
                network.requireNode(semantic.startCellIndex());
        if (node.kind() == SkyIslandGeomorphicNetworkNodeKind.CONFLUENCE) {
            return requireBoundary(
                    confluenceBoundaries,
                    semantic,
                    SkyIslandHydraulicTransitionBoundaryRole.OUTGOING);
        }
        return SkyIslandOrdinarySpanBoundary.free(
                endpointState(
                        reach,
                        SkyIslandHydraulicTransitionBoundaryRole.OUTGOING,
                        false));
    }

    private static SkyIslandOrdinarySpanBoundary terminalBoundary(
            SkyIslandHydraulicReachSkeleton reach,
            SkyIslandGeomorphicChannelNetworkPlan network,
            Map<BoundaryKey, SkyIslandOrdinarySpanBoundary> confluenceBoundaries,
            Map<Integer, SkyIslandChannelTerminalFate> terminalFates) {
        SkyIslandSemanticChannelReach semantic =
                reach.geomorphicRoute().semanticReach();
        SkyIslandGeomorphicNetworkNode node =
                network.requireNode(semantic.endCellIndex());
        if (node.kind() == SkyIslandGeomorphicNetworkNodeKind.CONFLUENCE) {
            return requireBoundary(
                    confluenceBoundaries,
                    semantic,
                    SkyIslandHydraulicTransitionBoundaryRole.INCOMING);
        }

        SkyIslandHydraulicTransitionBoundaryState endpoint =
                endpointState(
                        reach,
                        SkyIslandHydraulicTransitionBoundaryRole.INCOMING,
                        true);
        if (node.kind() != SkyIslandGeomorphicNetworkNodeKind.TERMINAL) {
            return SkyIslandOrdinarySpanBoundary.free(endpoint);
        }

        SkyIslandChannelTerminalFate fate = terminalFates.get(node.cellIndex());
        if (fate == null) {
            throw new IllegalStateException(
                    "missing watershed fate for channel terminal " + node.cellIndex());
        }
        return switch (fate.kind()) {
            case EDGE_OUTLET -> SkyIslandOrdinarySpanBoundary.free(endpoint);
            case RETAINED_OPEN_WATER, RETAINED_WETLAND, UNRESOLVED ->
                    SkyIslandOrdinarySpanBoundary.deferred(
                            endpoint,
                            "terminal fate " + fate.kind().name() + " remains transition-owned");
        };
    }

    private static SkyIslandOrdinarySpanBoundary requireBoundary(
            Map<BoundaryKey, SkyIslandOrdinarySpanBoundary> boundaries,
            SkyIslandSemanticChannelReach semantic,
            SkyIslandHydraulicTransitionBoundaryRole role) {
        SkyIslandOrdinarySpanBoundary boundary =
                boundaries.get(new BoundaryKey(reachIdentity(semantic), role));
        if (boundary == null) {
            throw new IllegalStateException(
                    "missing F3B finite confluence boundary for reach "
                            + semantic.startCellIndex()
                            + "->"
                            + semantic.endCellIndex()
                            + " role "
                            + role);
        }
        return boundary;
    }

    private static SkyIslandOrdinarySpanBoundary cascadeUpstreamBoundary(
            SkyIslandCascadeHeadCompatibilityOutcome outcome) {
        SkyIslandHydraulicTransitionBoundaryState state =
                outcome.geometry().transitionSite().upstreamBoundary();
        if (outcome.status() == SkyIslandCascadeHeadCompatibilityStatus.SOLVED) {
            return SkyIslandOrdinarySpanBoundary.fixed(
                    state, outcome.upstreamHeadWorldUnits().orElseThrow());
        }
        return SkyIslandOrdinarySpanBoundary.deferred(
                state,
                "CASCADE transition "
                        + outcome.status().name()
                        + outcome.diagnostic().map(value -> ": " + value).orElse(""));
    }

    private static SkyIslandOrdinarySpanBoundary cascadeDownstreamBoundary(
            SkyIslandCascadeHeadCompatibilityOutcome outcome) {
        SkyIslandHydraulicTransitionBoundaryState state =
                outcome.geometry().transitionSite().downstreamBoundary();
        if (outcome.status() == SkyIslandCascadeHeadCompatibilityStatus.SOLVED) {
            return SkyIslandOrdinarySpanBoundary.fixed(
                    state, outcome.downstreamHeadWorldUnits().orElseThrow());
        }
        return SkyIslandOrdinarySpanBoundary.deferred(
                state,
                "CASCADE transition "
                        + outcome.status().name()
                        + outcome.diagnostic().map(value -> ": " + value).orElse(""));
    }

    private static SkyIslandHydraulicTransitionBoundaryState endpointState(
            SkyIslandHydraulicReachSkeleton reach,
            SkyIslandHydraulicTransitionBoundaryRole role,
            boolean downstream) {
        SkyIslandHydraulicGeometrySkeletonSample sample =
                downstream ? reach.samples().getLast() : reach.samples().getFirst();
        SkyIslandSemanticChannelReach semantic =
                reach.geomorphicRoute().semanticReach();
        return new SkyIslandHydraulicTransitionBoundaryState(
                semantic.startCellIndex(),
                semantic.endCellIndex(),
                role,
                downstream ? 1.0 : 0.0,
                sample.arcLength(),
                sample.position(),
                sample.relativeDischarge(),
                sample.bankfullHalfWidth(),
                sample.waterDepthPotential(),
                sample.terrainElevation());
    }

    private static SkyIslandOrdinarySpanOutcome unsolved(
            SkyIslandOrdinaryHydraulicSpan span,
            SkyIslandOrdinarySpanStatus status,
            Optional<SkyIslandHydraulicQpResult> solve,
            String diagnostic) {
        return new SkyIslandOrdinarySpanOutcome(
                span,
                status,
                solve,
                List.of(),
                Optional.empty(),
                List.of(),
                Optional.of(diagnostic));
    }

    private static double[] quadratureWeights(
            List<SkyIslandHydraulicGeometrySkeletonSample> samples) {
        double[] result = new double[samples.size()];
        for (int i = 0; i < samples.size(); i++) {
            double length;
            if (i == 0) {
                length = 0.5 * (samples.get(1).arcLength() - samples.get(0).arcLength());
            } else if (i == samples.size() - 1) {
                length = 0.5
                        * (samples.get(i).arcLength()
                                - samples.get(i - 1).arcLength());
            } else {
                length = 0.5
                        * (samples.get(i + 1).arcLength()
                                - samples.get(i - 1).arcLength());
            }
            if (!(length > 0.0) || !Double.isFinite(length)) {
                throw new IllegalStateException(
                        "ordinary-span quadrature weights must be finite and positive");
            }
            result[i] = length;
        }
        return result;
    }

    private static Vector tangent(
            List<SkyIslandLocalPosition> points,
            int index) {
        SkyIslandLocalPosition a =
                index == 0 ? points.getFirst() : points.get(index - 1);
        SkyIslandLocalPosition b =
                index == points.size() - 1 ? points.getLast() : points.get(index + 1);
        double dx = b.x() - a.x();
        double dz = b.z() - a.z();
        double length = Math.hypot(dx, dz);
        if (length <= EPSILON) {
            return new Vector(1.0, 0.0);
        }
        return new Vector(dx / length, dz / length);
    }

    private static long reachIdentity(SkyIslandSemanticChannelReach reach) {
        return reachIdentity(reach.startCellIndex(), reach.endCellIndex());
    }

    private static long reachIdentity(int start, int end) {
        return ((long) start << 32) ^ Integer.toUnsignedLong(end);
    }

    private static double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    private record BoundaryKey(
            long reachIdentity,
            SkyIslandHydraulicTransitionBoundaryRole role) {}

    private record Vector(double x, double z) {}
}
