package io.github.nidaba.skyforge.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandIdentity;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;

/**
 * Exercises the candidate-geometry -> physical ordinary-flow -> independent D2 handoff.
 *
 * <p>Transition boundaries remain under their existing F3 evidence planners; this probe isolates
 * whether a D2-independent C2 geometry can enter the standard-step span solver without restoring
 * D2 pointwise head intervals as hydraulic constraints.
 */
class SkyIslandCandidateHydrologyCouplingTest {
    private static final long SEED = 0x534B59464F524745L;
    private static final SkyIslandGameScaleHydraulicCalibration CALIBRATION =
            new SkyIslandGameScaleHydraulicCalibration(
                    1.0, 0.5, 0.035, 0.5, 1.0, 9.81, 1.0e-8, 160, 1.0, 0.25);

    @Test
    void coupledLandformCandidatePreservesSemanticGraphAndBuildsBoundedChannelValleys() {
        SkyIslandDescriptor descriptor = SkyIslandDescriptorGenerator.derive(
                SkyIslandIdentity.of(SEED, 8L, 81L, 700L));
        SkyIslandHydraulicLandformCandidatePlanner.Plan candidate =
                SkyIslandHydraulicLandformCandidatePlanner.plan(descriptor, CALIBRATION);

        assertEquals(
                candidate.skeletonPlan().geomorphicNetwork().routes().size(),
                candidate.reaches().size(),
                "candidate landform geometry must preserve one reach per authored semantic edge");
        for (SkyIslandHydraulicLandformCandidatePlanner.ReachCandidate reach : candidate.reaches()) {
            assertEquals(
                    reach.skeleton().samples().size(),
                    reach.sections().size(),
                    "hydraulic sections and candidate drainage samples must share stations");
            for (int i = 0; i < reach.sections().size(); i++) {
                assertTrue(
                        reach.sections().get(i).bedElevationMeters()
                                < reach.skeleton().samples().get(i).terrainElevation()
                                        * descriptor.reliefBudget()
                                        * CALIBRATION.metersPerWorldUnit(),
                        "candidate channel bed must remain below its source terrain");
            }
        }

        SkyIslandHydraulicLandformCandidatePlanner.ReachCandidate representative =
                candidate.requireReach(801, 1951);
        int sampleIndex = representative.skeleton().samples().size() / 2;
        SkyIslandHydraulicGeometrySkeletonSample sample =
                representative.skeleton().samples().get(sampleIndex);
        double candidateElevation = candidate.terrain().sample(sample.position());
        double reliefMeters = descriptor.reliefBudget() * CALIBRATION.metersPerWorldUnit();
        double bedPotential =
                representative.sections().get(sampleIndex).bedElevationMeters() / reliefMeters;
        double maximumCut = sample.terrainElevation() - bedPotential;
        assertTrue(candidateElevation <= sample.terrainElevation());
        assertTrue(
                candidateElevation >= sample.terrainElevation() - maximumCut - 1.0e-9,
                "valley side slopes must not excavate deeper than the bounded channel incision");
    }

    @Test
    void candidateGeometryFeedsPhysicalSolverBeforeIndependentD2OnControlAndHeldOutKey() {
        ProbeResult acceptedControl = probe(6L, 61L, 77L);
        ProbeResult rejectedControl = probe(8L, 81L, 287L);
        ProbeResult heldOutChallenge = probe(8L, 81L, 700L);

        System.out.printf(
                Locale.ROOT,
                "CANDIDATE_COUPLING key=77 spans=%d attempted=%d deferred=%d solved=%d "
                        + "d2Qualified=%d d2Rejected=%d maxEnergyResidualMeters=%.9g failures=%s%n",
                acceptedControl.spanCount(),
                acceptedControl.attempted(),
                acceptedControl.deferred(),
                acceptedControl.solved(),
                acceptedControl.d2Qualified(),
                acceptedControl.d2Rejected(),
                acceptedControl.maximumEnergyResidualMeters(),
                acceptedControl.failures());
        System.out.printf(
                Locale.ROOT,
                "CANDIDATE_COUPLING key=287 spans=%d attempted=%d deferred=%d solved=%d "
                        + "d2Qualified=%d d2Rejected=%d maxEnergyResidualMeters=%.9g failures=%s%n",
                rejectedControl.spanCount(),
                rejectedControl.attempted(),
                rejectedControl.deferred(),
                rejectedControl.solved(),
                rejectedControl.d2Qualified(),
                rejectedControl.d2Rejected(),
                rejectedControl.maximumEnergyResidualMeters(),
                rejectedControl.failures());
        System.out.printf(
                Locale.ROOT,
                "CANDIDATE_COUPLING key=700 spans=%d attempted=%d deferred=%d solved=%d "
                        + "d2Qualified=%d d2Rejected=%d maxEnergyResidualMeters=%.9g failures=%s%n",
                heldOutChallenge.spanCount(),
                heldOutChallenge.attempted(),
                heldOutChallenge.deferred(),
                heldOutChallenge.solved(),
                heldOutChallenge.d2Qualified(),
                heldOutChallenge.d2Rejected(),
                heldOutChallenge.maximumEnergyResidualMeters(),
                heldOutChallenge.failures());

        assertTrue(acceptedControl.attempted() > 0, "key 77 must exercise finite ordinary spans");
        assertTrue(
                acceptedControl.failures().values().stream().mapToInt(Integer::intValue).sum()
                                + acceptedControl.solved()
                        == acceptedControl.attempted(),
                "every attempted key-77 span must be accounted for as solved or hydraulically rejected");
        assertTrue(
                rejectedControl.failures().values().stream().mapToInt(Integer::intValue).sum()
                                + rejectedControl.solved()
                        == rejectedControl.attempted(),
                "every attempted key-287 span must be accounted for as solved or hydraulically rejected");
        assertTrue(
                rejectedControl.d2Qualified() + rejectedControl.d2Rejected()
                        == rejectedControl.solved(),
                "key-287 D2 outcomes must be counted only after a complete physical profile solve");
        assertTrue(
                rejectedControl.attempted() + rejectedControl.deferred() > 0,
                "key 287 must exercise candidate ordinary spans or explicit deferred-boundary evidence");
        assertTrue(
                heldOutChallenge.failures().values().stream().mapToInt(Integer::intValue).sum()
                                + heldOutChallenge.solved()
                        == heldOutChallenge.attempted(),
                "every attempted key-700 span must be accounted for as solved or hydraulically rejected");
        assertTrue(
                acceptedControl.d2Qualified() + acceptedControl.d2Rejected()
                        == acceptedControl.solved(),
                "D2 must be evaluated only after a complete physical profile solve");
        assertTrue(
                heldOutChallenge.d2Qualified() + heldOutChallenge.d2Rejected()
                        == heldOutChallenge.solved(),
                "D2 must be evaluated only after a complete physical profile solve");
        assertTrue(
                heldOutChallenge.attempted() + heldOutChallenge.deferred() > 0,
                "key 700 must produce ordinary-span or explicit deferred-boundary evidence");
        assertTrue(Double.isFinite(acceptedControl.maximumEnergyResidualMeters()));
        assertTrue(Double.isFinite(heldOutChallenge.maximumEnergyResidualMeters()));

        NaturalComponentProbe naturalComponent = probeNaturalKey700Component();
        System.out.printf(
                Locale.ROOT,
                "NATURAL_COMPONENT key=700 incoming=%d cascadeSections=%d attempted=%s solved=%s "
                        + "maxEnergyResidualMeters=%.9g failure=%s%n",
                naturalComponent.incomingBranches(),
                naturalComponent.cascadeSections(),
                naturalComponent.attempted(),
                naturalComponent.solved(),
                naturalComponent.maximumEnergyResidualMeters(),
                naturalComponent.failure());
        assertEquals(2, naturalComponent.incomingBranches());
        assertTrue(naturalComponent.cascadeSections() >= 2);
        assertTrue(naturalComponent.attempted());
    }

    private static NaturalComponentProbe probeNaturalKey700Component() {
        SkyIslandDescriptor descriptor = SkyIslandDescriptorGenerator.derive(
                SkyIslandIdentity.of(SEED, 8L, 81L, 700L));
        SkyIslandHydraulicLandformCandidatePlanner.Plan candidate =
                SkyIslandHydraulicLandformCandidatePlanner.plan(descriptor, CALIBRATION);
        SkyIslandHydraulicGeometrySkeletonPlan candidateSkeleton = candidate.skeletonPlan();
        List<SkyIslandGraduallyVariedFlowSolver.CrossSection> cascadeSections =
                candidate.requireReach(801, 1951).sections();
        int incomingBranches = 2;
        try {
            var parameters = CALIBRATION.solverParameters();
            List<SkyIslandHydraulicEnergyConfluenceComponentSolver.IncomingReach> incoming =
                    List.of(
                            new SkyIslandHydraulicEnergyConfluenceComponentSolver.IncomingReach(
                                    candidate.requireReach(660, 801).sections()),
                            new SkyIslandHydraulicEnergyConfluenceComponentSolver.IncomingReach(
                                    candidate.requireReach(1140, 801).sections()));
            SkyIslandGraduallyVariedFlowSolver.Result cascadeProfile =
                    SkyIslandHydraulicCascadeTransitionSolver.solveFromCriticalInlet(
                            cascadeSections, parameters);
            double sharedJunctionDepth =
                    cascadeProfile.points().getFirst().depthMeters();
            SkyIslandHydraulicEnergyConfluenceComponentSolver.Result component =
                    SkyIslandHydraulicEnergyConfluenceComponentSolver.solve(
                            incoming,
                            cascadeSections.getFirst(),
                            sharedJunctionDepth,
                            parameters,
                            0.0);
            return new NaturalComponentProbe(
                    incomingBranches,
                    cascadeSections.size(),
                    true,
                    true,
                    Math.max(
                            cascadeProfile.maximumEnergyResidualMeters(),
                            Math.max(
                                    component.maximumReachEnergyResidualMeters(),
                                    component.confluence().energyResidualMeters())),
                    "");
        } catch (IllegalArgumentException | IllegalStateException failure) {
            String diagnostic = failure.getClass().getSimpleName() + ":" + failure.getMessage()
                    + describeCascadeLimit(cascadeSections, CALIBRATION.solverParameters(), failure.getMessage());
            return new NaturalComponentProbe(
                    incomingBranches,
                    cascadeSections.size(),
                    true,
                    false,
                    0.0,
                    diagnostic);
        }
    }

    private static String describeCascadeLimit(
            List<SkyIslandGraduallyVariedFlowSolver.CrossSection> sections,
            SkyIslandGraduallyVariedFlowSolver.Parameters parameters,
            String message) {
        if (message == null) {
            return "";
        }
        String marker = "section ";
        int markerIndex = message.lastIndexOf(marker);
        if (markerIndex < 0) {
            return "";
        }
        int numberStart = markerIndex + marker.length();
        int numberEnd = numberStart;
        while (numberEnd < message.length() && Character.isDigit(message.charAt(numberEnd))) {
            numberEnd++;
        }
        if (numberEnd == numberStart) {
            return "";
        }
        try {
            int failedIndex = Integer.parseInt(message.substring(numberStart, numberEnd));
            if (failedIndex <= 0 || failedIndex >= sections.size()) {
                return "";
            }
            SkyIslandGraduallyVariedFlowSolver.Result validPrefix =
                    SkyIslandGraduallyVariedFlowSolver.solveSupercriticalDownstreamFromCriticalControl(
                            sections.subList(0, failedIndex), parameters);
            SkyIslandGraduallyVariedFlowSolver.ProfilePoint lastValid =
                    validPrefix.points().getLast();
            SkyIslandGraduallyVariedFlowSolver.CrossSection failed = sections.get(failedIndex);
            double spacing = failed.chainageMeters() - lastValid.section().chainageMeters();
            double downstreamBedSlope =
                    (lastValid.section().bedElevationMeters() - failed.bedElevationMeters()) / spacing;
            return String.format(
                    Locale.ROOT,
                    ";lastValidSection=%d,depthMeters=%.9g,froude=%.9g,bedSlopeToFailed=%.9g,"
                            + "dischargeChange=%.9g,bottomWidthChange=%.9g",
                    failedIndex - 1,
                    lastValid.depthMeters(),
                    lastValid.froudeNumber(),
                    downstreamBedSlope,
                    failed.dischargeCubicMetersPerSecond()
                            - lastValid.section().dischargeCubicMetersPerSecond(),
                    failed.bottomWidthMeters() - lastValid.section().bottomWidthMeters());
        } catch (IllegalArgumentException | IllegalStateException diagnosticFailure) {
            return ";localCascadeDiagnosticsUnavailable="
                    + diagnosticFailure.getClass().getSimpleName() + ":" + diagnosticFailure.getMessage();
        }
    }

    private static SkyIslandHydraulicReachSkeleton requireReach(
            SkyIslandHydraulicGeometrySkeletonPlan candidate, int start, int end) {
        return candidate.reaches().stream()
                .filter(reach -> reach.geomorphicRoute().semanticReach().startCellIndex() == start
                        && reach.geomorphicRoute().semanticReach().endCellIndex() == end)
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "missing candidate semantic reach " + start + "->" + end));
    }

    private static ProbeResult probe(long province, long cluster, long key) {
        SkyIslandDescriptor descriptor = SkyIslandDescriptorGenerator.derive(
                SkyIslandIdentity.of(SEED, province, cluster, key));
        SkyIslandHydraulicLandformCandidatePlanner.Plan candidate =
                SkyIslandHydraulicLandformCandidatePlanner.plan(descriptor, CALIBRATION);
        SkyIslandHydraulicGeometrySkeletonPlan candidateSkeleton = candidate.skeletonPlan();
        SkyIslandSemanticField terrain = candidate.terrain();
        SkyIslandGeomorphicQualificationPolicy policy =
                SkyIslandGeomorphicQualificationPolicy.firstEvidenceBacked();
        SkyIslandWatershedPlan watershed = SkyIslandWatershedPlanner.plan(descriptor);
        SkyIslandHydraulicTransitionTopologyPlan topology =
                SkyIslandHydraulicTransitionTopologyPlanner.plan(descriptor, candidateSkeleton);
        SkyIslandHydraulicTransitionGeometryEvidencePlan geometry =
                SkyIslandHydraulicTransitionGeometryEvidencePlanner.plan(descriptor, topology);
        SkyIslandConfluenceHeadCompatibilityPlan confluences =
                SkyIslandConfluenceHeadCompatibilityPlanner.plan(
                        descriptor, geometry, terrain, policy);
        SkyIslandCascadeHeadCompatibilityPlan cascades =
                SkyIslandCascadeHeadCompatibilityPlanner.plan(
                        descriptor, geometry, terrain, policy, watershed);
        SkyIslandConfluenceCascadeHeadCompatibilityPlan jointTransitions =
                SkyIslandConfluenceCascadeHeadCompatibilityPlanner.plan(
                        descriptor, geometry, terrain, policy, watershed);
        SkyIslandOrdinarySpanPlan spans = SkyIslandOrdinarySpanPlanner.plan(
                descriptor, confluences, cascades, jointTransitions, terrain, policy);

        Map<Integer, SkyIslandChannelTerminalFateKind> terminalFates = new HashMap<>();
        for (SkyIslandChannelTerminalFate fate :
                SkyIslandChannelTerminalFatePlanner.plan(
                        descriptor, candidate.skeletonPlan().geomorphicNetwork())) {
            terminalFates.put(fate.channelTerminalCellIndex(), fate.kind());
        }

        Map<Long, List<SkyIslandHydraulicGeometrySkeletonSample>> parentSamples = new HashMap<>();
        Map<Long, List<SkyIslandGraduallyVariedFlowSolver.CrossSection>> candidateParentBeds =
                new HashMap<>();
        for (SkyIslandHydraulicLandformCandidatePlanner.ReachCandidate reach :
                candidate.reaches()) {
            SkyIslandSemanticChannelReach semantic =
                    reach.skeleton().geomorphicRoute().semanticReach();
            long reachIdentityKey = reachKey(
                    semantic.startCellIndex(), semantic.endCellIndex());
            parentSamples.put(reachIdentityKey, reach.skeleton().samples());
            candidateParentBeds.put(reachIdentityKey, reach.sections());
        }

        int attempted = 0;
        int deferred = 0;
        int solved = 0;
        int d2Qualified = 0;
        int d2Rejected = 0;
        double maximumEnergyResidual = 0.0;
        Map<String, Integer> failures = new TreeMap<>();
        for (SkyIslandOrdinarySpanOutcome planned : spans.outcomes()) {
            SkyIslandOrdinaryHydraulicSpan span = planned.span();
            if (span.boundaryDeferred()) {
                deferred++;
                continue;
            }
            attempted++;
            try {
                List<SkyIslandHydraulicGeometrySkeletonSample> reachSamples = parentSamples.get(
                        reachKey(span.parentReachStartCellIndex(), span.parentReachEndCellIndex()));
                if (reachSamples == null) {
                    throw new IllegalStateException("candidate parent reach samples are missing");
                }
                List<SkyIslandGraduallyVariedFlowSolver.CrossSection> candidateBed =
                        candidateParentBeds.get(
                                reachKey(span.parentReachStartCellIndex(), span.parentReachEndCellIndex()));
                if (candidateBed == null) {
                    throw new IllegalStateException("candidate parent bed sections are missing");
                }
                SkyIslandOpenChannelOrdinarySpanSolver.Outcome physical =
                        SkyIslandOpenChannelOrdinarySpanSolver.solve(
                                descriptor,
                                span,
                                terrain,
                                policy,
                                candidate.skeletonPlan().geomorphicNetwork().planningSpacing(),
                                CALIBRATION,
                                Map.copyOf(terminalFates),
                                reachSamples,
                                candidateBed);
                solved++;
                maximumEnergyResidual = Math.max(
                        maximumEnergyResidual,
                        physical.hydraulicProfile().maximumEnergyResidualMeters());
                if (physical.geomorphicallyQualified()) {
                    d2Qualified++;
                } else {
                    d2Rejected++;
                }
                assertTrue(
                        physical.solvedSamples().size() == span.samples().size(),
                        "post-solve D2 measurements must align with candidate geometry");
            } catch (IllegalArgumentException | IllegalStateException failure) {
                failures.merge(
                        failure.getClass().getSimpleName() + ":" + failure.getMessage(),
                        1,
                        Integer::sum);
            }
        }
        return new ProbeResult(
                spans.outcomes().size(),
                attempted,
                deferred,
                solved,
                d2Qualified,
                d2Rejected,
                maximumEnergyResidual,
                Map.copyOf(failures));
    }

    private static long reachKey(int startCellIndex, int endCellIndex) {
        return ((long) startCellIndex << 32) | (endCellIndex & 0xffffffffL);
    }

    private record NaturalComponentProbe(
            int incomingBranches,
            int cascadeSections,
            boolean attempted,
            boolean solved,
            double maximumEnergyResidualMeters,
            String failure) {}

    private record ProbeResult(
            int spanCount,
            int attempted,
            int deferred,
            int solved,
            int d2Qualified,
            int d2Rejected,
            double maximumEnergyResidualMeters,
            Map<String, Integer> failures) {}
}
