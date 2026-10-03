package io.github.nidaba.skyforge.world;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Builds a shared-head QP across the feasible F3D ordinary spans and their F3B/F3C/F3H boundaries.
 *
 * <p>This is diagnostic optimization evidence, not F3I/F3E admission. Ordinary spans with empty
 * pointwise D2 envelopes remain explicitly excluded; the returned partial solution cannot qualify a
 * terminal component. Their valid transition-boundary variables may still constrain the remaining
 * feasible graph, which lets the exact selected-head coupling question be tested independently of
 * an already-known empty source cross-section.
 */
public final class SkyIslandHydraulicComponentHeadSolver {
    private static final double EPSILON = 1.0e-9;

    private SkyIslandHydraulicComponentHeadSolver() {}

    public static Outcome solve(
            SkyIslandDescriptor descriptor,
            SkyIslandOrdinarySpanPlan ordinaryPlan,
            SkyIslandSemanticField terrain,
            SkyIslandGeomorphicQualificationPolicy policy) {
        Objects.requireNonNull(descriptor, "descriptor");
        Objects.requireNonNull(ordinaryPlan, "ordinaryPlan");
        Objects.requireNonNull(terrain, "terrain");
        Objects.requireNonNull(policy, "policy");
        if (!descriptor.equals(ordinaryPlan.descriptor())) {
            throw new IllegalArgumentException("component solve descriptor must match ordinary-span plan");
        }

        SkyIslandHydraulicGeometrySkeletonPlan skeleton =
                ordinaryPlan.cascadePlan().transitionGeometry().topology().skeletonPlan();
        Map<Long, SkyIslandSemanticChannelReach> reaches = new HashMap<>();
        for (SkyIslandHydraulicReachSkeleton reach : skeleton.reaches()) {
            SkyIslandSemanticChannelReach semantic =
                    reach.geomorphicRoute().semanticReach();
            reaches.put(reachIdentity(semantic.startCellIndex(), semantic.endCellIndex()), semantic);
        }

        BuilderState state = new BuilderState(descriptor, terrain, policy, reaches);
        addConfluenceVariablesAndConstraints(ordinaryPlan.confluencePlan(), state);
        addCascadeVariablesAndConstraints(ordinaryPlan, state);
        addJointVariablesAndConstraints(ordinaryPlan.jointPlan(), state);
        addOrdinarySpanVariablesAndConstraints(ordinaryPlan.outcomes(), state);

        if (state.builder.observationCount() == 0) {
            return new Outcome(
                    SkyIslandHydraulicQpStatus.INFEASIBLE,
                    Optional.empty(),
                    Map.of(),
                    List.of(),
                    List.copyOf(state.excludedSpans),
                    List.copyOf(state.blockers),
                    "no feasible ordinary spans or transition heads were available to compose");
        }

        SkyIslandHydraulicHeadComponentProblem.Outcome solved = state.builder.solve();
        if (solved.status() != SkyIslandHydraulicQpStatus.SOLVED) {
            return new Outcome(
                    solved.status(),
                    solved.solve(),
                    Map.of(),
                    List.of(),
                    List.copyOf(state.excludedSpans),
                    List.copyOf(state.blockers),
                    solved.diagnostic().orElse("component head QP did not solve"));
        }

        return new Outcome(
                SkyIslandHydraulicQpStatus.SOLVED,
                solved.solve(),
                solved.heads(),
                List.copyOf(state.includedSpanKeys),
                List.copyOf(state.excludedSpans),
                List.copyOf(state.blockers),
                state.excludedSpans.isEmpty() && state.blockers.isEmpty()
                        ? null
                        : "partial diagnostic solution; excluded spans or unsolved transitions prevent component admission");
    }

    private static void addConfluenceVariablesAndConstraints(
            SkyIslandConfluenceHeadCompatibilityPlan plan,
            BuilderState state) {
        Map<Integer, SkyIslandConfluenceHeadCompatibilityOutcome> outcomes = new HashMap<>();
        for (SkyIslandConfluenceHeadCompatibilityOutcome outcome : plan.outcomes()) {
            outcomes.put(outcome.geometry().transitionSite().nodeCellIndex(), outcome);
            for (SkyIslandHydraulicTransitionLegGeometry leg : outcome.geometry().legs()) {
                SkyIslandSemanticChannelReach semantic = state.requireReach(leg.nodeBoundary());
                String nodeKey = state.nodeKey(outcome.geometry().transitionSite().nodeCellIndex());
                SkyIslandChannelProfileKind nodeKind = profileKind(semantic, leg.nodeBoundary());
                SkyIslandChannelProfileKind finiteKind = profileKind(semantic, leg.finiteBoundary());
                if (nodeKind != SkyIslandChannelProfileKind.CASCADE) {
                    state.addStateHead(
                            nodeKey,
                            leg.nodeBoundary(),
                            nodeKind,
                            legDirection(leg),
                            0.5 * leg.retreatLength(),
                            state.policy.limits(semantic));
                }
                if (finiteKind != SkyIslandChannelProfileKind.CASCADE) {
                    state.addStateHead(
                            state.pointKey(leg.finiteBoundary()),
                            leg.finiteBoundary(),
                            finiteKind,
                            legDirection(leg),
                            0.5 * leg.retreatLength(),
                            state.policy.limits(semantic));
                }

                if (outcome.status() == SkyIslandConfluenceHeadCompatibilityStatus.SOLVED) {
                    String finiteKey = state.pointKey(leg.finiteBoundary());
                    double maxDrop =
                            state.policy.limits(semantic).maximumLongitudinalGrade()
                                    * leg.retreatLength();
                    if (leg.nodeBoundary().role()
                            == SkyIslandHydraulicTransitionBoundaryRole.INCOMING) {
                        state.addDifference(
                                "component-confluence:"
                                        + outcome.geometry().transitionSite().nodeCellIndex()
                                        + ":incoming:"
                                        + semantic.startCellIndex()
                                        + "->"
                                        + semantic.endCellIndex(),
                                finiteKey,
                                nodeKey,
                                0.0,
                                maxDrop);
                    } else {
                        state.addDifference(
                                "component-confluence:"
                                        + outcome.geometry().transitionSite().nodeCellIndex()
                                        + ":outgoing:"
                                        + semantic.startCellIndex()
                                        + "->"
                                        + semantic.endCellIndex(),
                                nodeKey,
                                finiteKey,
                                0.0,
                                maxDrop);
                    }
                }
            }
            if (outcome.status() != SkyIslandConfluenceHeadCompatibilityStatus.SOLVED
                    && outcome.status()
                            != SkyIslandConfluenceHeadCompatibilityStatus.CASCADE_COUPLED) {
                state.blockers.add(
                        "confluence "
                                + outcome.geometry().transitionSite().nodeCellIndex()
                                + " remains "
                                + outcome.status().name()
                                + outcome.diagnostic().map(value -> ": " + value).orElse(""));
            }
        }
    }

    private static void addCascadeVariablesAndConstraints(
            SkyIslandOrdinarySpanPlan plan,
            BuilderState state) {
        for (SkyIslandCascadeHeadCompatibilityOutcome outcome
                : plan.cascadePlan().outcomes()) {
            SkyIslandHydraulicCascadeGeometryCandidate geometry = outcome.geometry();
            SkyIslandHydraulicCascadeTransitionSite site = geometry.transitionSite();
            boolean jointlyOwned = plan.jointPlan().outcomes().stream()
                    .anyMatch(joint ->
                            joint.status()
                                            == SkyIslandConfluenceCascadeHeadCompatibilityStatus.SOLVED
                                    && joint.cascade().transitionSite().equals(site));
            if (outcome.status() != SkyIslandCascadeHeadCompatibilityStatus.SOLVED) {
                if (outcome.status()
                                != SkyIslandCascadeHeadCompatibilityStatus.BOUNDARY_COUPLED
                        || !jointlyOwned) {
                    state.blockers.add(
                            "CASCADE "
                                    + site.reachStartCellIndex()
                                    + "->"
                                    + site.reachEndCellIndex()
                                    + " remains "
                                    + outcome.status().name()
                                    + outcome.diagnostic().map(value -> ": " + value).orElse(""));
                }
                continue;
            }
            if (jointlyOwned) {
                continue;
            }

            SkyIslandSemanticChannelReach semantic =
                    state.requireReach(site.reachStartCellIndex(), site.reachEndCellIndex());
            List<SkyIslandChannelProfile> profiles = semantic.profiles();
            SkyIslandHydraulicTransitionBoundaryState upstream = site.upstreamBoundary();
            SkyIslandHydraulicTransitionBoundaryState downstream = site.downstreamBoundary();
            SkyIslandChannelProfileKind upstreamKind =
                    profiles.get(site.firstProfileIndex() - 1).kind();
            SkyIslandChannelProfileKind downstreamKind =
                    profiles.get(site.lastProfileIndexExclusive()).kind();
            List<SkyIslandLocalPosition> points = geometry.centerlinePoints();
            Direction upstreamDirection = direction(points.get(0), points.get(1));
            Direction downstreamDirection =
                    direction(points.get(points.size() - 2), points.getLast());
            String upstreamKey = state.pointKey(upstream);
            String downstreamKey = state.pointKey(downstream);
            SkyIslandGeomorphicProfileLimits limits = state.policy.limits(semantic);
            state.addStateHead(
                    upstreamKey,
                    upstream,
                    upstreamKind,
                    upstreamDirection,
                    0.5 * geometry.pathLength(),
                    limits);
            state.addStateHead(
                    downstreamKey,
                    downstream,
                    downstreamKind,
                    downstreamDirection,
                    0.5 * geometry.pathLength(),
                    limits);
            state.addDifference(
                    "component-cascade:"
                            + site.reachStartCellIndex()
                            + "->"
                            + site.reachEndCellIndex()
                            + ":"
                            + site.firstProfileIndex()
                            + "-"
                            + site.lastProfileIndexExclusive(),
                    upstreamKey,
                    downstreamKey,
                    0.0,
                    outcome.authoredMaximumDropWorldUnits());
        }
    }

    private static void addJointVariablesAndConstraints(
            SkyIslandConfluenceCascadeHeadCompatibilityPlan plan,
            BuilderState state) {
        for (SkyIslandConfluenceCascadeHeadCompatibilityOutcome outcome : plan.outcomes()) {
            if (outcome.status() != SkyIslandConfluenceCascadeHeadCompatibilityStatus.SOLVED) {
                state.blockers.add(
                        "joint transition at confluence "
                                + outcome.confluence().transitionSite().nodeCellIndex()
                                + " remains "
                                + outcome.status().name()
                                + outcome.diagnostic().map(value -> ": " + value).orElse(""));
                continue;
            }

            int nodeCell = outcome.confluence().transitionSite().nodeCellIndex();
            String nodeKey = state.nodeKey(nodeCell);
            for (SkyIslandHydraulicTransitionLegGeometry leg : outcome.confluence().legs()) {
                if (leg == outcome.coupledLeg()) {
                    continue;
                }
                SkyIslandSemanticChannelReach semantic = state.requireReach(leg.nodeBoundary());
                Direction direction = legDirection(leg);
                SkyIslandChannelProfileKind nodeKind = profileKind(semantic, leg.nodeBoundary());
                SkyIslandChannelProfileKind finiteKind = profileKind(semantic, leg.finiteBoundary());
                if (nodeKind == SkyIslandChannelProfileKind.CASCADE
                        || finiteKind == SkyIslandChannelProfileKind.CASCADE) {
                    state.blockers.add(
                            "joint ordinary leg at confluence " + nodeCell
                                    + " crosses CASCADE-owned profile");
                    continue;
                }
                SkyIslandGeomorphicProfileLimits limits = state.policy.limits(semantic);
                state.addStateHead(
                        nodeKey,
                        leg.nodeBoundary(),
                        nodeKind,
                        direction,
                        0.5 * leg.retreatLength(),
                        limits);
                String finiteKey = state.pointKey(leg.finiteBoundary());
                state.addStateHead(
                        finiteKey,
                        leg.finiteBoundary(),
                        finiteKind,
                        direction,
                        0.5 * leg.retreatLength(),
                        limits);
                double maxDrop = limits.maximumLongitudinalGrade() * leg.retreatLength();
                if (leg.nodeBoundary().role()
                        == SkyIslandHydraulicTransitionBoundaryRole.INCOMING) {
                    state.addDifference(
                            "component-joint-confluence:"
                                    + nodeCell
                                    + ":incoming:"
                                    + semantic.startCellIndex()
                                    + "->"
                                    + semantic.endCellIndex(),
                            finiteKey,
                            nodeKey,
                            0.0,
                            maxDrop);
                } else {
                    state.addDifference(
                            "component-joint-confluence:"
                                    + nodeCell
                                    + ":outgoing:"
                                    + semantic.startCellIndex()
                                    + "->"
                                    + semantic.endCellIndex(),
                            nodeKey,
                            finiteKey,
                            0.0,
                            maxDrop);
                }
            }

            SkyIslandHydraulicTransitionLegGeometry coupled = outcome.coupledLeg();
            SkyIslandSemanticChannelReach coupledSemantic =
                    state.requireReach(coupled.nodeBoundary());
            SkyIslandHydraulicCascadeTransitionSite site =
                    outcome.cascade().transitionSite();
            boolean cascadeUpstreamAtConfluence =
                    coupled.nodeBoundary().role()
                            == SkyIslandHydraulicTransitionBoundaryRole.OUTGOING;
            SkyIslandHydraulicTransitionBoundaryState near =
                    cascadeUpstreamAtConfluence
                            ? site.upstreamBoundary()
                            : site.downstreamBoundary();
            SkyIslandHydraulicTransitionBoundaryState remote =
                    cascadeUpstreamAtConfluence
                            ? site.downstreamBoundary()
                            : site.upstreamBoundary();
            boolean touchesNode = sameLocation(near, coupled.nodeBoundary());
            Direction approachDirection = legDirection(coupled);
            String nearKey = touchesNode ? nodeKey : state.pointKey(near);
            if (!touchesNode) {
                List<SkyIslandChannelProfile> profiles = coupledSemantic.profiles();
                int nearIndex = cascadeUpstreamAtConfluence
                        ? site.firstProfileIndex() - 1
                        : site.lastProfileIndexExclusive();
                SkyIslandChannelProfileKind nearKind = profiles.get(nearIndex).kind();
                state.addStateHead(
                        nearKey,
                        near,
                        nearKind,
                        approachDirection,
                        0.5 * Math.abs(near.arcLength() - coupled.nodeBoundary().arcLength()),
                        state.policy.limits(coupledSemantic));
                double maximumGrade = state.policy.limits(coupledSemantic).maximumLongitudinalGrade()
                        * Math.abs(near.arcLength() - coupled.nodeBoundary().arcLength());
                if (cascadeUpstreamAtConfluence) {
                    state.addDifference(
                            "component-joint-approach:" + nodeCell,
                            nodeKey,
                            nearKey,
                            0.0,
                            maximumGrade);
                } else {
                    state.addDifference(
                            "component-joint-approach:" + nodeCell,
                            nearKey,
                            nodeKey,
                            0.0,
                            maximumGrade);
                }
            }

            int remoteIndex = cascadeUpstreamAtConfluence
                    ? site.lastProfileIndexExclusive()
                    : site.firstProfileIndex() - 1;
            SkyIslandChannelProfileKind remoteKind =
                    coupledSemantic.profiles().get(remoteIndex).kind();
            Direction cascadeDirection = cascadeUpstreamAtConfluence
                    ? direction(
                            outcome.cascade().centerlinePoints()
                                    .get(outcome.cascade().centerlinePoints().size() - 2),
                            outcome.cascade().centerlinePoints().getLast())
                    : direction(
                            outcome.cascade().centerlinePoints().get(0),
                            outcome.cascade().centerlinePoints().get(1));
            String remoteKey = state.pointKey(remote);
            state.addStateHead(
                    remoteKey,
                    remote,
                    remoteKind,
                    cascadeDirection,
                    0.5 * outcome.cascade().pathLength(),
                    state.policy.limits(coupledSemantic));

            String cascadeUpstream = cascadeUpstreamAtConfluence ? nearKey : remoteKey;
            String cascadeDownstream = cascadeUpstreamAtConfluence ? remoteKey : nearKey;
            state.addDifference(
                    "component-joint-cascade:"
                            + site.reachStartCellIndex()
                            + "->"
                            + site.reachEndCellIndex()
                            + ":authored-drop",
                    cascadeUpstream,
                    cascadeDownstream,
                    0.0,
                    outcome.authoredMaximumDropWorldUnits());
        }
    }

    private static void addOrdinarySpanVariablesAndConstraints(
            List<SkyIslandOrdinarySpanOutcome> outcomes,
            BuilderState state) {
        for (SkyIslandOrdinarySpanOutcome outcome : outcomes) {
            SkyIslandOrdinaryHydraulicSpan span = outcome.span();
            String spanId = spanId(span);
            if (span.boundaryDeferred()) {
                state.excludedSpans.add(spanId + ": boundary deferred");
                continue;
            }
            List<SkyIslandHydraulicGeometrySkeletonSample> samples = span.samples();
            List<SkyIslandLocalPosition> points =
                    samples.stream().map(SkyIslandHydraulicGeometrySkeletonSample::position).toList();
            SkyIslandGeomorphicProfileLimits limits =
                    state.policy.limits(span.qualificationClass());
            double[] weights = quadratureWeights(samples);
            List<String> keys = new ArrayList<>(samples.size());
            List<SkyIslandHydraulicHeadEnvelope> envelopes = new ArrayList<>(samples.size());
            int empty = -1;
            for (int i = 0; i < samples.size(); i++) {
                SkyIslandHydraulicGeometrySkeletonSample sample = samples.get(i);
                Direction tangent = tangent(points, i);
                SkyIslandHydraulicHeadEnvelope envelope =
                        SkyIslandHydraulicHeadEnvelopePlanner.evaluateForKind(
                                state.descriptor,
                                span.sampleProfileKinds().get(i),
                                sample.position(),
                                sample.bankfullHalfWidth(),
                                sample.waterDepthPotential(),
                                sample.terrainElevation(),
                                -tangent.z(),
                                tangent.x(),
                                state.terrain,
                                limits);
                if (envelope.lowerHead() > envelope.upperHead()) {
                    empty = i;
                    break;
                }
                envelopes.add(envelope);
                keys.add(state.sampleKey(span, sample));
            }
            if (empty >= 0) {
                var sample = samples.get(empty);
                state.excludedSpans.add(
                        spanId
                                + ": empty D2 envelope at local sample "
                                + empty
                                + ", stationFraction="
                                + Double.toString(sample.stationFraction())
                                + ", gapWorld="
                                + Double.toString(
                                        envelopes.size() > empty
                                                ? envelopes.get(empty).lowerHead()
                                                        - envelopes.get(empty).upperHead()
                                                : SkyIslandHydraulicHeadEnvelopePlanner
                                                        .evaluateForKind(
                                                                state.descriptor,
                                                                span.sampleProfileKinds().get(empty),
                                                                sample.position(),
                                                                sample.bankfullHalfWidth(),
                                                                sample.waterDepthPotential(),
                                                                sample.terrainElevation(),
                                                                -tangent(points, empty).z(),
                                                                tangent(points, empty).x(),
                                                                state.terrain,
                                                                limits)
                                                        .lowerHead()
                                                        - SkyIslandHydraulicHeadEnvelopePlanner
                                                                .evaluateForKind(
                                                                        state.descriptor,
                                                                        span.sampleProfileKinds().get(empty),
                                                                        sample.position(),
                                                                        sample.bankfullHalfWidth(),
                                                                        sample.waterDepthPotential(),
                                                                        sample.terrainElevation(),
                                                                        -tangent(points, empty).z(),
                                                                        tangent(points, empty).x(),
                                                                        state.terrain,
                                                                        limits)
                                                                .upperHead()));
                continue;
            }

            for (int i = 0; i < samples.size(); i++) {
                state.builder.addHead(
                        keys.get(i),
                        envelopes.get(i).targetHead(),
                        weights[i],
                        envelopes.get(i).lowerHead(),
                        envelopes.get(i).upperHead());
            }
            for (int i = 0; i + 1 < samples.size(); i++) {
                double ds = samples.get(i + 1).arcLength() - samples.get(i).arcLength();
                state.addDifference(
                        "component-span:"
                                + spanId
                                + ":grade:"
                                + i,
                        keys.get(i),
                        keys.get(i + 1),
                        0.0,
                        limits.maximumLongitudinalGrade() * ds);
            }
            state.includedSpanKeys.add(spanId);
        }
    }

    private static double[] quadratureWeights(
            List<SkyIslandHydraulicGeometrySkeletonSample> samples) {
        double[] weights = new double[samples.size()];
        for (int i = 0; i < samples.size(); i++) {
            double left = i == 0
                    ? 0.0
                    : samples.get(i).arcLength() - samples.get(i - 1).arcLength();
            double right = i + 1 == samples.size()
                    ? 0.0
                    : samples.get(i + 1).arcLength() - samples.get(i).arcLength();
            weights[i] = 0.5 * (left + right);
            if (!(weights[i] > 0.0)) {
                throw new IllegalStateException("component head quadrature weight must be positive");
            }
        }
        return weights;
    }

    private static Direction tangent(List<SkyIslandLocalPosition> points, int index) {
        int from = Math.max(0, index - 1);
        int to = Math.min(points.size() - 1, index + 1);
        if (from == to) {
            throw new IllegalStateException("ordinary span tangent requires multiple points");
        }
        return direction(points.get(from), points.get(to));
    }

    private static Direction legDirection(SkyIslandHydraulicTransitionLegGeometry leg) {
        double dx = leg.finiteBoundary().position().x() - leg.nodeBoundary().position().x();
        double dz = leg.finiteBoundary().position().z() - leg.nodeBoundary().position().z();
        if (leg.nodeBoundary().role()
                == SkyIslandHydraulicTransitionBoundaryRole.INCOMING) {
            dx = -dx;
            dz = -dz;
        }
        return direction(dx, dz);
    }

    private static Direction direction(
            SkyIslandLocalPosition upstream,
            SkyIslandLocalPosition downstream) {
        return direction(
                downstream.x() - upstream.x(),
                downstream.z() - upstream.z());
    }

    private static Direction direction(double dx, double dz) {
        double length = Math.hypot(dx, dz);
        if (!(length > EPSILON)) {
            throw new IllegalStateException("hydraulic component tangent must be non-zero");
        }
        return new Direction(dx / length, dz / length);
    }

    private static SkyIslandChannelProfileKind profileKind(
            SkyIslandSemanticChannelReach semantic,
            SkyIslandHydraulicTransitionBoundaryState state) {
        return SkyIslandHydraulicHeadEnvelopePlanner.profileKind(
                semantic.profiles(), state.stationFraction());
    }

    private static boolean sameLocation(
            SkyIslandHydraulicTransitionBoundaryState first,
            SkyIslandHydraulicTransitionBoundaryState second) {
        return Math.abs(first.stationFraction() - second.stationFraction()) <= EPSILON
                && Math.abs(first.arcLength() - second.arcLength()) <= EPSILON
                && Math.abs(first.position().x() - second.position().x()) <= EPSILON
                && Math.abs(first.position().z() - second.position().z()) <= EPSILON;
    }

    private static String spanId(SkyIslandOrdinaryHydraulicSpan span) {
        return span.parentReachStartCellIndex()
                + "->"
                + span.parentReachEndCellIndex()
                + ":"
                + String.format(
                        java.util.Locale.ROOT,
                        "%.9f-%.9f",
                        span.parentStartStationFraction(),
                        span.parentEndStationFraction());
    }

    private static long reachIdentity(int start, int end) {
        return ((long) start << 32) ^ Integer.toUnsignedLong(end);
    }

    public record Outcome(
            SkyIslandHydraulicQpStatus status,
            Optional<SkyIslandHydraulicQpResult> solve,
            Map<String, Double> heads,
            List<String> includedSpans,
            List<String> excludedSpans,
            List<String> transitionBlockers,
            String diagnostic) {
        public Outcome {
            status = Objects.requireNonNull(status, "status");
            solve = Objects.requireNonNull(solve, "solve");
            heads = immutableSorted(heads);
            includedSpans = List.copyOf(includedSpans);
            excludedSpans = List.copyOf(excludedSpans);
            transitionBlockers = List.copyOf(transitionBlockers);
            if (status == SkyIslandHydraulicQpStatus.SOLVED) {
                if (solve.isEmpty()
                        || solve.orElseThrow().status() != SkyIslandHydraulicQpStatus.SOLVED
                        || heads.isEmpty()) {
                    throw new IllegalArgumentException(
                            "solved component-head outcome requires a valid QP and heads");
                }
            } else if (!heads.isEmpty()) {
                throw new IllegalArgumentException(
                        "failed component-head outcome cannot expose candidate heads");
            }
            boolean complete = status == SkyIslandHydraulicQpStatus.SOLVED
                    && includedSpans.size() > 0
                    && excludedSpans.isEmpty()
                    && transitionBlockers.isEmpty();
            if ((diagnostic == null) != complete) {
                throw new IllegalArgumentException(
                        "complete component solve has no diagnostic; partial or failed solve requires one");
            }
        }

        public boolean complete() {
            return status == SkyIslandHydraulicQpStatus.SOLVED
                    && !includedSpans.isEmpty()
                    && excludedSpans.isEmpty()
                    && transitionBlockers.isEmpty();
        }

        private static Map<String, Double> immutableSorted(Map<String, Double> source) {
            TreeMap<String, Double> ordered = new TreeMap<>();
            source.forEach((key, value) -> {
                if (key == null || key.isBlank() || value == null || !Double.isFinite(value)) {
                    throw new IllegalArgumentException("component heads require finite keyed values");
                }
                ordered.put(key, value);
            });
            return Collections.unmodifiableMap(new LinkedHashMap<>(ordered));
        }
    }

    private static final class BuilderState {
        private final SkyIslandDescriptor descriptor;
        private final SkyIslandSemanticField terrain;
        private final SkyIslandGeomorphicQualificationPolicy policy;
        private final Map<Long, SkyIslandSemanticChannelReach> reaches;
        private final SkyIslandHydraulicHeadComponentProblem.Builder builder =
                SkyIslandHydraulicHeadComponentProblem.builder();
        private final Map<Long, String> nodeKeys = new HashMap<>();
        private final List<Alias> aliases = new ArrayList<>();
        private final List<String> includedSpanKeys = new ArrayList<>();
        private final List<String> excludedSpans = new ArrayList<>();
        private final List<String> blockers = new ArrayList<>();

        private BuilderState(
                SkyIslandDescriptor descriptor,
                SkyIslandSemanticField terrain,
                SkyIslandGeomorphicQualificationPolicy policy,
                Map<Long, SkyIslandSemanticChannelReach> reaches) {
            this.descriptor = descriptor;
            this.terrain = terrain;
            this.policy = policy;
            this.reaches = reaches;
        }

        private String nodeKey(int nodeCellIndex) {
            return nodeKeys.computeIfAbsent(nodeCellIndex, value -> "node:" + value);
        }

        private String pointKey(SkyIslandHydraulicTransitionBoundaryState state) {
            String key = "reach:"
                    + state.reachStartCellIndex()
                    + "->"
                    + state.reachEndCellIndex()
                    + "@"
                    + Double.toHexString(state.arcLength());
            addAlias(state, key);
            return key;
        }

        private String sampleKey(
                SkyIslandOrdinaryHydraulicSpan span,
                SkyIslandHydraulicGeometrySkeletonSample sample) {
            double arc = span.parentStartArcLength() + sample.arcLength();
            for (Alias alias : aliases) {
                if (alias.reachStart() == span.parentReachStartCellIndex()
                        && alias.reachEnd() == span.parentReachEndCellIndex()
                        && Math.abs(alias.arcLength() - arc) <= EPSILON
                        && Math.hypot(
                                        alias.position().x() - sample.position().x(),
                                        alias.position().z() - sample.position().z())
                                <= EPSILON) {
                    return alias.key();
                }
            }
            return "reach:"
                    + span.parentReachStartCellIndex()
                    + "->"
                    + span.parentReachEndCellIndex()
                    + "@"
                    + Double.toHexString(arc);
        }

        private void addAlias(
                SkyIslandHydraulicTransitionBoundaryState state,
                String key) {
            boolean duplicate = aliases.stream().anyMatch(alias ->
                    alias.key().equals(key)
                            && alias.reachStart() == state.reachStartCellIndex()
                            && alias.reachEnd() == state.reachEndCellIndex()
                            && Math.abs(alias.arcLength() - state.arcLength()) <= EPSILON);
            if (!duplicate) {
                aliases.add(new Alias(
                        state.reachStartCellIndex(),
                        state.reachEndCellIndex(),
                        state.arcLength(),
                        state.position(),
                        key));
            }
        }

        private void addStateHead(
                String key,
                SkyIslandHydraulicTransitionBoundaryState state,
                SkyIslandChannelProfileKind kind,
                Direction direction,
                double weight,
                SkyIslandGeomorphicProfileLimits limits) {
            addAlias(state, key);
            SkyIslandHydraulicHeadEnvelope envelope =
                    SkyIslandHydraulicHeadEnvelopePlanner.evaluateForKind(
                            descriptor,
                            kind,
                            state.position(),
                            state.bankfullHalfWidth(),
                            state.waterDepthPotential(),
                            state.terrainElevation(),
                            -direction.z(),
                            direction.x(),
                            terrain,
                            limits);
            if (envelope.lowerHead() > envelope.upperHead()) {
                blockers.add(
                        key
                                + ": empty D2 transition head envelope gap="
                                + Double.toString(envelope.lowerHead() - envelope.upperHead()));
                return;
            }
            builder.addHead(
                    key,
                    envelope.targetHead(),
                    weight,
                    envelope.lowerHead(),
                    envelope.upperHead());
        }

        private void addDifference(
                String id,
                String left,
                String right,
                double lower,
                double upper) {
            if (left.equals(right)) {
                return;
            }
            if (!builder.hasHead(left) || !builder.hasHead(right)) {
                blockers.add(
                        id + ": omitted difference constraint because a boundary head has no "
                                + "feasible D2 envelope (leftRegistered="
                                + builder.hasHead(left)
                                + ", rightRegistered="
                                + builder.hasHead(right)
                                + ")");
                return;
            }
            builder.addDifference(id, left, right, lower, upper);
        }

        private SkyIslandSemanticChannelReach requireReach(
                SkyIslandHydraulicTransitionBoundaryState state) {
            return requireReach(state.reachStartCellIndex(), state.reachEndCellIndex());
        }

        private SkyIslandSemanticChannelReach requireReach(int start, int end) {
            SkyIslandSemanticChannelReach reach =
                    reaches.get(reachIdentity(start, end));
            if (reach == null) {
                throw new IllegalStateException(
                        "component transition references missing reach " + start + "->" + end);
            }
            return reach;
        }
    }

    private record Alias(
            int reachStart,
            int reachEnd,
            double arcLength,
            SkyIslandLocalPosition position,
            String key) {}

    private record Direction(double x, double z) {}
}
