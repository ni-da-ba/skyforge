package io.github.nidaba.skyforge.world;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Solves the narrow F3H case where an authored CASCADE starts or ends at an outgoing/incoming
 * confluence leg and the opposite cascade boundary is adjacent to an ordinary profile.
 *
 * <p>The confluence node and cascade endpoint are represented by one shared node-head variable.
 * Ordinary incident legs retain the F3B D2 envelopes and grade constraints; the drop retains the
 * exact F3C authored maximum. Other overlaps stay deferred.
 */
public final class SkyIslandConfluenceCascadeHeadCompatibilityPlanner {
    private static final double EPSILON = 1.0e-9;

    private SkyIslandConfluenceCascadeHeadCompatibilityPlanner() {}

    public static SkyIslandConfluenceCascadeHeadCompatibilityPlan plan(
            SkyIslandDescriptor descriptor) {
        Objects.requireNonNull(descriptor, "descriptor");
        SkyIslandHydraulicTransitionGeometryEvidencePlan geometry =
                SkyIslandHydraulicTransitionGeometryEvidencePlanner.plan(descriptor);
        return plan(
                descriptor,
                geometry,
                SkyIslandPreHydrologicTerrainField.create(descriptor),
                SkyIslandGeomorphicQualificationPolicy.firstEvidenceBacked(),
                SkyIslandWatershedPlanner.plan(descriptor));
    }

    static SkyIslandConfluenceCascadeHeadCompatibilityPlan plan(
            SkyIslandDescriptor descriptor,
            SkyIslandHydraulicTransitionGeometryEvidencePlan geometry,
            SkyIslandSemanticField terrain,
            SkyIslandGeomorphicQualificationPolicy policy,
            SkyIslandWatershedPlan watershed) {
        Objects.requireNonNull(descriptor, "descriptor");
        Objects.requireNonNull(geometry, "geometry");
        Objects.requireNonNull(terrain, "terrain");
        Objects.requireNonNull(policy, "policy");
        Objects.requireNonNull(watershed, "watershed");
        if (!descriptor.equals(geometry.descriptor())) {
            throw new IllegalArgumentException(
                    "transition geometry descriptor must match joint transition descriptor");
        }

        Map<Long, SkyIslandHydraulicReachSkeleton> reaches =
                indexReaches(geometry.topology().skeletonPlan().reaches());
        Map<Integer, SkyIslandWatershedCell> cells = new HashMap<>();
        for (SkyIslandWatershedCell cell : watershed.cells()) {
            cells.put(cell.index(), cell);
        }

        List<SkyIslandConfluenceCascadeHeadCompatibilityOutcome> outcomes =
                new ArrayList<>();
        for (SkyIslandHydraulicConfluenceGeometryCandidate confluence :
                geometry.confluences()) {
            for (SkyIslandHydraulicTransitionLegGeometry leg : confluence.legs()) {
                for (SkyIslandHydraulicCascadeGeometryCandidate cascade : geometry.cascades()) {
                    SkyIslandHydraulicReachSkeleton reach =
                            requireReach(reaches, leg.nodeBoundary());
                    int profileCount = reach.geomorphicRoute().semanticReach().profiles().size();
                    if (matchesBoundary(cascade, leg, profileCount)) {
                        outcomes.add(solve(
                                descriptor,
                                confluence,
                                cascade,
                                leg,
                                reach,
                                cells,
                                terrain,
                                policy));
                    }
                }
            }
        }
        outcomes.sort(java.util.Comparator
                .comparingInt((SkyIslandConfluenceCascadeHeadCompatibilityOutcome value) ->
                        value.confluence().transitionSite().nodeCellIndex())
                .thenComparingInt(value -> value.cascade().transitionSite().reachStartCellIndex())
                .thenComparingInt(value -> value.cascade().transitionSite().reachEndCellIndex())
                .thenComparingInt(value -> value.cascade().transitionSite().firstProfileIndex()));
        return new SkyIslandConfluenceCascadeHeadCompatibilityPlan(
                descriptor, geometry, outcomes);
    }

    private static SkyIslandConfluenceCascadeHeadCompatibilityOutcome solve(
            SkyIslandDescriptor descriptor,
            SkyIslandHydraulicConfluenceGeometryCandidate confluence,
            SkyIslandHydraulicCascadeGeometryCandidate cascade,
            SkyIslandHydraulicTransitionLegGeometry coupledLeg,
            SkyIslandHydraulicReachSkeleton reach,
            Map<Integer, SkyIslandWatershedCell> cells,
            SkyIslandSemanticField terrain,
            SkyIslandGeomorphicQualificationPolicy policy) {
        SkyIslandHydraulicCascadeTransitionSite site = cascade.transitionSite();
        List<SkyIslandChannelProfile> profiles =
                reach.geomorphicRoute().semanticReach().profiles();
        boolean startsAtNode = site.firstProfileIndex() == 0
                && coupledLeg.nodeBoundary().role()
                        == SkyIslandHydraulicTransitionBoundaryRole.OUTGOING;
        boolean endsAtNode = site.lastProfileIndexExclusive() == profiles.size()
                && coupledLeg.nodeBoundary().role()
                        == SkyIslandHydraulicTransitionBoundaryRole.INCOMING;
        if (startsAtNode == endsAtNode
                || site.firstProfileIndex() == 0
                        && site.lastProfileIndexExclusive() == profiles.size()) {
            return deferred(
                    confluence,
                    cascade,
                    coupledLeg,
                    "CASCADE also touches a non-confluence semantic endpoint or orientation is ambiguous");
        }

        List<SkyIslandHydraulicTransitionLegGeometry> ordinaryLegs =
                confluence.legs().stream()
                        .filter(leg -> leg != coupledLeg)
                        .toList();
        if (ordinaryLegs.isEmpty()) {
            return deferred(
                    confluence,
                    cascade,
                    coupledLeg,
                    "joint confluence requires at least one ordinary incident leg to bound the shared node head");
        }

        double relief = descriptor.reliefBudget();
        double nodeLower = Double.NEGATIVE_INFINITY;
        double nodeUpper = Double.POSITIVE_INFINITY;
        double nodeTargetWeighted = 0.0;
        double nodeWeight = 0.0;
        List<HeadEnvelopePair> ordinaryEnvelopes = new ArrayList<>();
        for (SkyIslandHydraulicTransitionLegGeometry leg : ordinaryLegs) {
            SkyIslandSemanticChannelReach semantic = requireReach(reach, leg).geomorphicRoute()
                    .semanticReach();
            SkyIslandChannelProfileKind nodeKind =
                    SkyIslandHydraulicHeadEnvelopePlanner.profileKind(
                            semantic.profiles(), leg.nodeBoundary().stationFraction());
            SkyIslandChannelProfileKind finiteKind =
                    SkyIslandHydraulicHeadEnvelopePlanner.profileKind(
                            semantic.profiles(), leg.finiteBoundary().stationFraction());
            if (nodeKind == SkyIslandChannelProfileKind.CASCADE
                    || finiteKind == SkyIslandChannelProfileKind.CASCADE) {
                return deferred(
                        confluence,
                        cascade,
                        coupledLeg,
                        "another incident CASCADE overlap requires a larger joint transition system");
            }

            Direction direction = direction(leg);
            SkyIslandHydraulicHeadEnvelope atNode = evaluate(
                    descriptor, semantic, leg.nodeBoundary(), direction, terrain, policy);
            SkyIslandHydraulicHeadEnvelope atFinite = evaluate(
                    descriptor, semantic, leg.finiteBoundary(), direction, terrain, policy);
            if (!atNode.feasible(EPSILON) || !atFinite.feasible(EPSILON)) {
                return unsolved(
                        confluence,
                        cascade,
                        coupledLeg,
                        SkyIslandConfluenceCascadeHeadCompatibilityStatus.INFEASIBLE,
                        nodeLower,
                        nodeUpper,
                        0.0,
                        Optional.empty(),
                        "ordinary incident D2-derived pointwise head envelope is empty");
            }

            double legWeight = 0.5 * leg.retreatLength();
            nodeWeight += legWeight;
            nodeTargetWeighted += legWeight * atNode.targetHead();
            nodeLower = Math.max(nodeLower, normalizedLower(atNode));
            nodeUpper = Math.min(nodeUpper, normalizedUpper(atNode));
            ordinaryEnvelopes.add(new HeadEnvelopePair(leg, atFinite));
        }
        if (nodeLower > nodeUpper + EPSILON) {
            return unsolved(
                    confluence,
                    cascade,
                    coupledLeg,
                    SkyIslandConfluenceCascadeHeadCompatibilityStatus.INFEASIBLE,
                    nodeLower,
                    nodeUpper,
                    0.0,
                    Optional.empty(),
                    "ordinary incident D2 envelopes have no shared confluence-head interval");
        }
        if (nodeLower > nodeUpper) {
            double common = 0.5 * (nodeLower + nodeUpper);
            nodeLower = common;
            nodeUpper = common;
        }
        if (!(nodeWeight > 0.0)) {
            throw new IllegalStateException("joint confluence node weight must be positive");
        }

        boolean cascadeStartsAtNode = startsAtNode;
        SkyIslandChannelProfileKind cascadeOrdinaryKind = cascadeStartsAtNode
                ? profiles.get(site.lastProfileIndexExclusive()).kind()
                : profiles.get(site.firstProfileIndex() - 1).kind();
        if (cascadeOrdinaryKind == SkyIslandChannelProfileKind.CASCADE) {
            return deferred(
                    confluence,
                    cascade,
                    coupledLeg,
                    "CASCADE boundary has no adjacent ordinary hydraulic profile");
        }
        SkyIslandHydraulicTransitionBoundaryState otherBoundary = cascadeStartsAtNode
                ? site.downstreamBoundary()
                : site.upstreamBoundary();
        Direction cascadeDirection = cascadeStartsAtNode
                ? direction(cascade.centerlinePoints().get(cascade.centerlinePoints().size() - 2),
                        cascade.centerlinePoints().getLast())
                : direction(cascade.centerlinePoints().get(0), cascade.centerlinePoints().get(1));
        SkyIslandHydraulicHeadEnvelope cascadeEnvelope =
                SkyIslandHydraulicHeadEnvelopePlanner.evaluateForKind(
                        descriptor,
                        cascadeOrdinaryKind,
                        otherBoundary.position(),
                        otherBoundary.bankfullHalfWidth(),
                        otherBoundary.waterDepthPotential(),
                        otherBoundary.terrainElevation(),
                        -cascadeDirection.z(),
                        cascadeDirection.x(),
                        terrain,
                        policy.limits(reach.geomorphicRoute().semanticReach()));
        if (!cascadeEnvelope.feasible(EPSILON)) {
            return unsolved(
                    confluence,
                    cascade,
                    coupledLeg,
                    SkyIslandConfluenceCascadeHeadCompatibilityStatus.INFEASIBLE,
                    nodeLower,
                    nodeUpper,
                    authoredDrop(descriptor, profiles, site, cells),
                    Optional.empty(),
                    "ordinary-side D2 pointwise envelope is empty at the non-confluence CASCADE boundary");
        }

        double authoredMaximumDrop = authoredDrop(descriptor, profiles, site, cells);
        int variableCount = ordinaryLegs.size() + 2;
        int cascadeVariable = variableCount - 1;
        double[] target = new double[variableCount];
        double[] weight = new double[variableCount];
        double[] lower = new double[variableCount];
        double[] upper = new double[variableCount];
        target[0] = nodeTargetWeighted / nodeWeight;
        weight[0] = nodeWeight;
        lower[0] = nodeLower;
        upper[0] = nodeUpper;
        List<SkyIslandHydraulicDifferenceConstraint> differences = new ArrayList<>();
        for (int i = 0; i < ordinaryEnvelopes.size(); i++) {
            HeadEnvelopePair pair = ordinaryEnvelopes.get(i);
            SkyIslandHydraulicTransitionLegGeometry leg = pair.leg();
            SkyIslandSemanticChannelReach semantic =
                    requireReach(reach, leg).geomorphicRoute().semanticReach();
            int variable = i + 1;
            SkyIslandHydraulicHeadEnvelope finite = pair.finite();
            target[variable] = finite.targetHead();
            weight[variable] = 0.5 * leg.retreatLength();
            lower[variable] = normalizedLower(finite);
            upper[variable] = normalizedUpper(finite);
            double maximumDrop = policy.limits(semantic).maximumLongitudinalGrade()
                    * leg.retreatLength();
            if (leg.nodeBoundary().role()
                    == SkyIslandHydraulicTransitionBoundaryRole.INCOMING) {
                differences.add(new SkyIslandHydraulicDifferenceConstraint(
                        "joint-confluence:" + confluence.transitionSite().nodeCellIndex()
                                + ":incoming:" + semantic.startCellIndex() + "->"
                                + semantic.endCellIndex(),
                        variable, 0, 0.0, maximumDrop));
            } else {
                differences.add(new SkyIslandHydraulicDifferenceConstraint(
                        "joint-confluence:" + confluence.transitionSite().nodeCellIndex()
                                + ":outgoing:" + semantic.startCellIndex() + "->"
                                + semantic.endCellIndex(),
                        0, variable, 0.0, maximumDrop));
            }
        }
        target[cascadeVariable] = cascadeEnvelope.targetHead();
        weight[cascadeVariable] = 0.5 * cascade.pathLength();
        lower[cascadeVariable] = normalizedLower(cascadeEnvelope);
        upper[cascadeVariable] = normalizedUpper(cascadeEnvelope);
        if (cascadeStartsAtNode) {
            differences.add(new SkyIslandHydraulicDifferenceConstraint(
                    "joint-cascade:" + site.reachStartCellIndex() + "->"
                            + site.reachEndCellIndex() + ":downstream",
                    0, cascadeVariable, 0.0, authoredMaximumDrop));
        } else {
            differences.add(new SkyIslandHydraulicDifferenceConstraint(
                    "joint-cascade:" + site.reachStartCellIndex() + "->"
                            + site.reachEndCellIndex() + ":upstream",
                    cascadeVariable, 0, 0.0, authoredMaximumDrop));
        }

        SkyIslandHydraulicQpResult qp = SkyIslandHydraulicBoundedQpSolver.solve(
                new SkyIslandHydraulicBoundedQpProblem(
                        target, weight, lower, upper, differences));
        if (qp.status() == SkyIslandHydraulicQpStatus.INFEASIBLE) {
            return unsolved(
                    confluence, cascade, coupledLeg,
                    SkyIslandConfluenceCascadeHeadCompatibilityStatus.INFEASIBLE,
                    nodeLower, nodeUpper, authoredMaximumDrop, Optional.of(qp), qp.diagnostic());
        }
        if (qp.status() == SkyIslandHydraulicQpStatus.NUMERICAL_FAILURE) {
            return unsolved(
                    confluence, cascade, coupledLeg,
                    SkyIslandConfluenceCascadeHeadCompatibilityStatus.NUMERICAL_FAILURE,
                    nodeLower, nodeUpper, authoredMaximumDrop, Optional.of(qp), qp.diagnostic());
        }

        double[] solution = qp.solution();
        List<SkyIslandConfluenceLegHeadSolution> ordinarySolutions = new ArrayList<>();
        for (int i = 0; i < ordinaryLegs.size(); i++) {
            ordinarySolutions.add(new SkyIslandConfluenceLegHeadSolution(
                    ordinaryLegs.get(i), solution[i + 1]));
        }
        double boundaryHead = solution[cascadeVariable];
        double sharedNodeHead = solution[0];
        double drop = cascadeStartsAtNode
                ? sharedNodeHead - boundaryHead
                : boundaryHead - sharedNodeHead;
        return new SkyIslandConfluenceCascadeHeadCompatibilityOutcome(
                confluence,
                cascade,
                coupledLeg,
                SkyIslandConfluenceCascadeHeadCompatibilityStatus.SOLVED,
                nodeLower,
                nodeUpper,
                authoredMaximumDrop,
                Optional.of(qp),
                Optional.of(sharedNodeHead),
                Optional.of(boundaryHead),
                Optional.of(drop),
                ordinarySolutions,
                Optional.empty());
    }

    private static SkyIslandConfluenceCascadeHeadCompatibilityOutcome deferred(
            SkyIslandHydraulicConfluenceGeometryCandidate confluence,
            SkyIslandHydraulicCascadeGeometryCandidate cascade,
            SkyIslandHydraulicTransitionLegGeometry coupledLeg,
            String diagnostic) {
        return unsolved(
                confluence, cascade, coupledLeg,
                SkyIslandConfluenceCascadeHeadCompatibilityStatus.BOUNDARY_DEFERRED,
                0.0, 0.0, 0.0, Optional.empty(), diagnostic);
    }

    private static SkyIslandConfluenceCascadeHeadCompatibilityOutcome unsolved(
            SkyIslandHydraulicConfluenceGeometryCandidate confluence,
            SkyIslandHydraulicCascadeGeometryCandidate cascade,
            SkyIslandHydraulicTransitionLegGeometry coupledLeg,
            SkyIslandConfluenceCascadeHeadCompatibilityStatus status,
            double nodeLower,
            double nodeUpper,
            double authoredMaximumDrop,
            Optional<SkyIslandHydraulicQpResult> solve,
            String diagnostic) {
        if (!Double.isFinite(nodeLower) || !Double.isFinite(nodeUpper)) {
            nodeLower = 0.0;
            nodeUpper = 0.0;
        }
        return new SkyIslandConfluenceCascadeHeadCompatibilityOutcome(
                confluence,
                cascade,
                coupledLeg,
                status,
                nodeLower,
                nodeUpper,
                authoredMaximumDrop,
                solve,
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                List.of(),
                Optional.ofNullable(diagnostic));
    }

    private static SkyIslandHydraulicHeadEnvelope evaluate(
            SkyIslandDescriptor descriptor,
            SkyIslandSemanticChannelReach semantic,
            SkyIslandHydraulicTransitionBoundaryState boundary,
            Direction direction,
            SkyIslandSemanticField terrain,
            SkyIslandGeomorphicQualificationPolicy policy) {
        return SkyIslandHydraulicHeadEnvelopePlanner.evaluate(
                descriptor,
                semantic,
                boundary.stationFraction(),
                boundary.position(),
                boundary.bankfullHalfWidth(),
                boundary.waterDepthPotential(),
                boundary.terrainElevation(),
                -direction.z(),
                direction.x(),
                terrain,
                policy);
    }

    private static Direction direction(SkyIslandHydraulicTransitionLegGeometry leg) {
        double dx = leg.finiteBoundary().position().x() - leg.nodeBoundary().position().x();
        double dz = leg.finiteBoundary().position().z() - leg.nodeBoundary().position().z();
        if (leg.nodeBoundary().role()
                == SkyIslandHydraulicTransitionBoundaryRole.INCOMING) {
            dx = -dx;
            dz = -dz;
        }
        return direction(dx, dz);
    }

    private static Direction direction(SkyIslandLocalPosition upstream, SkyIslandLocalPosition downstream) {
        double dx = downstream.x() - upstream.x();
        double dz = downstream.z() - upstream.z();
        double length = Math.hypot(dx, dz);
        if (!(length > EPSILON)) {
            throw new IllegalStateException("transition tangent must be non-zero");
        }
        return new Direction(dx / length, dz / length);
    }

    private static double authoredDrop(
            SkyIslandDescriptor descriptor,
            List<SkyIslandChannelProfile> profiles,
            SkyIslandHydraulicCascadeTransitionSite site,
            Map<Integer, SkyIslandWatershedCell> cells) {
        double dropPotential = 0.0;
        for (int i = site.firstProfileIndex(); i < site.lastProfileIndexExclusive(); i++) {
            SkyIslandChannelSegment segment = profiles.get(i).segment();
            SkyIslandWatershedCell source = requireCell(cells, segment.sourceCellIndex());
            SkyIslandWatershedCell downstream = requireCell(cells, segment.downstreamCellIndex());
            dropPotential += Math.max(0.0, source.surfacePotential() - downstream.surfacePotential());
        }
        return dropPotential * descriptor.reliefBudget();
    }

    private static double normalizedLower(SkyIslandHydraulicHeadEnvelope envelope) {
        return envelope.lowerHead() <= envelope.upperHead()
                ? envelope.lowerHead()
                : 0.5 * (envelope.lowerHead() + envelope.upperHead());
    }

    private static double normalizedUpper(SkyIslandHydraulicHeadEnvelope envelope) {
        return envelope.lowerHead() <= envelope.upperHead()
                ? envelope.upperHead()
                : 0.5 * (envelope.lowerHead() + envelope.upperHead());
    }

    private static Map<Long, SkyIslandHydraulicReachSkeleton> indexReaches(
            List<SkyIslandHydraulicReachSkeleton> reaches) {
        Map<Long, SkyIslandHydraulicReachSkeleton> result = new HashMap<>();
        for (SkyIslandHydraulicReachSkeleton reach : reaches) {
            SkyIslandSemanticChannelReach semantic = reach.geomorphicRoute().semanticReach();
            if (result.put(identity(semantic.startCellIndex(), semantic.endCellIndex()), reach) != null) {
                throw new IllegalStateException("duplicate hydraulic reach identity");
            }
        }
        return Map.copyOf(result);
    }

    private static SkyIslandHydraulicReachSkeleton requireReach(
            Map<Long, SkyIslandHydraulicReachSkeleton> reaches,
            SkyIslandHydraulicTransitionBoundaryState boundary) {
        SkyIslandHydraulicReachSkeleton reach =
                reaches.get(identity(boundary.reachStartCellIndex(), boundary.reachEndCellIndex()));
        if (reach == null) {
            throw new IllegalStateException("joint transition boundary has no semantic reach");
        }
        return reach;
    }

    private static SkyIslandHydraulicReachSkeleton requireReach(
            SkyIslandHydraulicReachSkeleton reach,
            SkyIslandHydraulicTransitionLegGeometry leg) {
        SkyIslandSemanticChannelReach semantic = reach.geomorphicRoute().semanticReach();
        if (semantic.startCellIndex() != leg.nodeBoundary().reachStartCellIndex()
                || semantic.endCellIndex() != leg.nodeBoundary().reachEndCellIndex()) {
            throw new IllegalStateException("joint confluence leg refers to another semantic reach");
        }
        return reach;
    }

    private static SkyIslandWatershedCell requireCell(
            Map<Integer, SkyIslandWatershedCell> cells, int index) {
        SkyIslandWatershedCell cell = cells.get(index);
        if (cell == null) {
            throw new IllegalStateException("missing authored cascade watershed cell " + index);
        }
        return cell;
    }

    private static boolean matchesBoundary(
            SkyIslandHydraulicCascadeGeometryCandidate cascade,
            SkyIslandHydraulicTransitionLegGeometry leg,
            int reachProfileCount) {
        SkyIslandHydraulicCascadeTransitionSite site = cascade.transitionSite();
        SkyIslandHydraulicTransitionBoundaryState node = leg.nodeBoundary();
        if (site.reachStartCellIndex() != node.reachStartCellIndex()
                || site.reachEndCellIndex() != node.reachEndCellIndex()) {
            return false;
        }
        boolean startsAtNode = site.firstProfileIndex() == 0
                && site.lastProfileIndexExclusive() < reachProfileCount
                && leg.nodeBoundary().role() == SkyIslandHydraulicTransitionBoundaryRole.OUTGOING
                && sameLocation(site.upstreamBoundary(), node);
        boolean endsAtNode = site.lastProfileIndexExclusive() == reachProfileCount
                && site.firstProfileIndex() > 0
                && leg.nodeBoundary().role() == SkyIslandHydraulicTransitionBoundaryRole.INCOMING
                && sameLocation(site.downstreamBoundary(), node);
        return startsAtNode || endsAtNode;
    }

    private static boolean sameLocation(
            SkyIslandHydraulicTransitionBoundaryState first,
            SkyIslandHydraulicTransitionBoundaryState second) {
        return Math.abs(first.stationFraction() - second.stationFraction()) <= EPSILON
                && Math.abs(first.arcLength() - second.arcLength()) <= EPSILON
                && Math.abs(first.position().x() - second.position().x()) <= EPSILON
                && Math.abs(first.position().z() - second.position().z()) <= EPSILON;
    }

    private static double normalizedLower(SkyIslandHydraulicHeadEnvelope envelope) {
        return envelope.lowerHead() <= envelope.upperHead()
                ? envelope.lowerHead()
                : 0.5 * (envelope.lowerHead() + envelope.upperHead());
    }

    private static double normalizedUpper(SkyIslandHydraulicHeadEnvelope envelope) {
        return envelope.lowerHead() <= envelope.upperHead()
                ? envelope.upperHead()
                : 0.5 * (envelope.lowerHead() + envelope.upperHead());
    }

    private static Direction direction(SkyIslandHydraulicTransitionLegGeometry leg) {
        double dx = leg.finiteBoundary().position().x() - leg.nodeBoundary().position().x();
        double dz = leg.finiteBoundary().position().z() - leg.nodeBoundary().position().z();
        if (leg.nodeBoundary().role() == SkyIslandHydraulicTransitionBoundaryRole.INCOMING) {
            dx = -dx;
            dz = -dz;
        }
        return direction(dx, dz);
    }

    private static Direction direction(SkyIslandLocalPosition upstream, SkyIslandLocalPosition downstream) {
        double dx = downstream.x() - upstream.x();
        double dz = downstream.z() - upstream.z();
        double length = Math.hypot(dx, dz);
        if (!(length > EPSILON)) {
            throw new IllegalStateException("transition tangent must be non-zero");
        }
        return new Direction(dx / length, dz / length);
    }

    private static double authoredDrop(
            SkyIslandDescriptor descriptor,
            List<SkyIslandChannelProfile> profiles,
            SkyIslandHydraulicCascadeTransitionSite site,
            Map<Integer, SkyIslandWatershedCell> cells) {
        double dropPotential = 0.0;
        for (int i = site.firstProfileIndex(); i < site.lastProfileIndexExclusive(); i++) {
            SkyIslandChannelProfile profile = profiles.get(i);
            if (profile.kind() != SkyIslandChannelProfileKind.CASCADE) {
                throw new IllegalStateException(
                        "joint CASCADE interval contains an ordinary profile");
            }
            SkyIslandChannelSegment segment = profile.segment();
            SkyIslandWatershedCell source = requireCell(cells, segment.sourceCellIndex());
            SkyIslandWatershedCell downstream = requireCell(cells, segment.downstreamCellIndex());
            dropPotential += Math.max(0.0, source.surfacePotential() - downstream.surfacePotential());
        }
        return dropPotential * descriptor.reliefBudget();
    }

    private static long identity(int start, int end) {
        return ((long) start << 32) ^ Integer.toUnsignedLong(end);
    }

    private record HeadEnvelopePair(
            SkyIslandHydraulicTransitionLegGeometry leg,
            SkyIslandHydraulicHeadEnvelope finite) {}

    private record Direction(double x, double z) {}
}
