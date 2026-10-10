package io.github.nidaba.skyforge.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
        int localBedRiseCount = 0;
        for (SkyIslandHydraulicLandformCandidatePlanner.ReachCandidate reach : candidate.reaches()) {
            assertEquals(
                    reach.skeleton().samples().size(),
                    reach.sections().size(),
                    "hydraulic sections and candidate drainage samples must share stations");
            for (int i = 0; i < reach.sections().size(); i++) {
                SkyIslandHydraulicGeometrySkeletonSample sectionSample =
                        reach.skeleton().samples().get(i);
                double localTerrainMeters = sectionSample.terrainElevation()
                        * descriptor.reliefBudget() * CALIBRATION.metersPerWorldUnit();
                assertTrue(
                        reach.sections().get(i).bedElevationMeters() < localTerrainMeters,
                        "candidate channel bed must remain below its source terrain");
                double candidateDepth = localTerrainMeters
                        - reach.sections().get(i).bedElevationMeters();
                double reconstructedBankfullWidth =
                        reach.sections().get(i).bottomWidthMeters()
                                + 2.0 * CALIBRATION.sideSlopeHorizontalToVertical() * candidateDepth;
                assertEquals(
                        2.0 * sectionSample.bankfullHalfWidth()
                                * CALIBRATION.metersPerWorldUnit(),
                        reconstructedBankfullWidth,
                        1.0e-8,
                        "conditioned bed depth and bottom width must preserve the authored bankfull top width");
                if (i > 0) {
                    double bedRise = reach.sections().get(i).bedElevationMeters()
                            - reach.sections().get(i - 1).bedElevationMeters();
                    double chainageStep = reach.sections().get(i).chainageMeters()
                            - reach.sections().get(i - 1).chainageMeters();
                    if (bedRise > 1.0e-8) {
                        localBedRiseCount++;
                    }
                }
            }
        }

        System.out.printf(
                Locale.ROOT,
                "CANDIDATE_BED key=700 localRises=%d totalSections=%d%n",
                localBedRiseCount,
                candidate.reaches().stream().mapToInt(reach -> reach.sections().size()).sum());

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
                "NATURAL_COMPONENT key=700 incoming=%d cascadeSections=%d terminalFate=%s attempted=%s solved=%s "
                        + "maxEnergyResidualMeters=%.9g failure=%s%n",
                naturalComponent.incomingBranches(),
                naturalComponent.cascadeSections(),
                naturalComponent.terminalFate(),
                naturalComponent.attempted(),
                naturalComponent.solved(),
                naturalComponent.maximumEnergyResidualMeters(),
                naturalComponent.failure());
        assertEquals(2, naturalComponent.incomingBranches());
        assertTrue(naturalComponent.cascadeSections() >= 2);
        assertTrue(naturalComponent.attempted());
        assertTrue(
                naturalComponent.solved(),
                "key 700's complete natural confluence/CASCADE component must solve before review: "
                        + naturalComponent.failure());
    }

    private static NaturalComponentProbe probeNaturalKey700Component() {
        SkyIslandDescriptor descriptor = SkyIslandDescriptorGenerator.derive(
                SkyIslandIdentity.of(SEED, 8L, 81L, 700L));
        NaturalComponentProbe last = new NaturalComponentProbe(
                2, 0, "unknown", true, false, 0.0, "no candidate route resolution attempted");
        var parameters = CALIBRATION.solverParameters();

        // Let bounded bed/valley candidate generation respond to the exact natural component,
        // not only to a local cascade residual. A complete confluence/CASCADE closure is the only
        // zero-residual outcome; otherwise the physical cascade momentum residual ranks trials.
        try {
            var feedback = SkyIslandHydraulicLandformCandidatePlanner.planWithHydraulicFeedback(
                    descriptor,
                    CALIBRATION,
                    candidate -> naturalComponentResidualVector(descriptor, candidate, parameters),
                    1.0e-8,
                    6);
            last = solveNaturalCandidate(descriptor, feedback.plan(), "coupled-feedback", parameters);
            if (last.solved()) {
                return last;
            }
        } catch (IllegalArgumentException | IllegalStateException infeasibleCandidate) {
            last = new NaturalComponentProbe(
                    2,
                    0,
                    "coupled-feedback",
                    true,
                    false,
                    0.0,
                    "coupled candidate generation failed: "
                            + infeasibleCandidate.getClass().getSimpleName()
                            + ":" + infeasibleCandidate.getMessage());
        }

        // Retain independent finer-resolution and established-routing controls so this bounded
        // feedback experiment does not erase previously available candidate evidence.
        for (int divisionsPerPlanningCell : List.of(8, 16)) {
            try {
                SkyIslandHydraulicLandformCandidatePlanner.Plan candidate =
                        SkyIslandHydraulicLandformCandidatePlanner.planAtResolution(
                                descriptor, CALIBRATION, divisionsPerPlanningCell);
                last = solveNaturalCandidate(
                        descriptor, candidate, "least-cost@" + divisionsPerPlanningCell, parameters);
                if (last.solved()) {
                    return last;
                }
            } catch (IllegalArgumentException | IllegalStateException infeasibleCandidate) {
                last = new NaturalComponentProbe(
                        2,
                        0,
                        "unknown@" + divisionsPerPlanningCell,
                        true,
                        false,
                        0.0,
                        "least-cost candidate generation failed: "
                                + infeasibleCandidate.getClass().getSimpleName()
                                + ":" + infeasibleCandidate.getMessage());
            }
        }

        // Independent established routing candidate, evaluated against the same physical controls.
        try {
            SkyIslandHydraulicLandformCandidatePlanner.Plan priorityFlood =
                    SkyIslandHydraulicLandformCandidatePlanner.planPriorityFloodAtResolution(
                            descriptor, CALIBRATION, 4);
            NaturalComponentProbe result = solveNaturalCandidate(
                    descriptor, priorityFlood, "priority-flood", parameters);
            if (result.solved()) {
                return result;
            }
            last = result;
        } catch (IllegalArgumentException | IllegalStateException infeasibleCandidate) {
            last = new NaturalComponentProbe(
                    2,
                    0,
                    "priority-flood",
                    true,
                    false,
                    0.0,
                    "Priority-Flood candidate generation failed: "
                            + infeasibleCandidate.getClass().getSimpleName()
                            + ":" + infeasibleCandidate.getMessage());
        }
        return last;
    }

    private static double[] naturalComponentResidualVector(
            SkyIslandDescriptor descriptor,
            SkyIslandHydraulicLandformCandidatePlanner.Plan candidate,
            SkyIslandGraduallyVariedFlowSolver.Parameters parameters) {
        NaturalComponentProbe result =
                solveNaturalCandidate(descriptor, candidate, "candidate-feedback", parameters);
        if (result.solved()) {
            return new double[] {0.0, 0.0, 0.0};
        }
        double[] cascadeResiduals = cascadeResidualVector(candidate);
        return new double[] {1.0, cascadeResiduals[0], cascadeResiduals[1]};
    }

    private static NaturalComponentProbe solveNaturalCandidate(
            SkyIslandDescriptor descriptor,
            SkyIslandHydraulicLandformCandidatePlanner.Plan candidate,
            String candidateLabel,
            SkyIslandGraduallyVariedFlowSolver.Parameters parameters) {
        int incomingBranches = 2;
        SkyIslandHydraulicGeometrySkeletonPlan candidateSkeleton = candidate.skeletonPlan();
        List<SkyIslandGraduallyVariedFlowSolver.CrossSection> cascadeSections =
                candidate.requireReach(801, 1951).sections();
        SkyIslandChannelTerminalFate terminalFate =
                SkyIslandChannelTerminalFatePlanner.plan(
                                descriptor, candidateSkeleton.geomorphicNetwork())
                        .stream()
                        .filter(fate -> fate.channelTerminalCellIndex() == 1951)
                        .findFirst()
                        .orElseThrow(() -> new IllegalStateException(
                                "key-700 CASCADE has no authored terminal fate"));
        String label = candidateLabel + ":" + terminalFate.kind().name();
        try {
            List<SkyIslandHydraulicEnergyConfluenceComponentSolver.IncomingReach> incoming =
                    List.of(
                            new SkyIslandHydraulicEnergyConfluenceComponentSolver.IncomingReach(
                                    candidate.requireReach(660, 801).sections()),
                            new SkyIslandHydraulicEnergyConfluenceComponentSolver.IncomingReach(
                                    candidate.requireReach(1140, 801).sections()));
            SkyIslandGraduallyVariedFlowSolver.Result cascadeProfile;
            try {
                cascadeProfile = SkyIslandHydraulicCascadeTransitionSolver.solveFromCriticalInlet(
                        cascadeSections, parameters);
            } catch (IllegalArgumentException | IllegalStateException noContinuousChute) {
                if (terminalFate.kind() != SkyIslandChannelTerminalFateKind.EDGE_OUTLET) {
                    throw noContinuousChute;
                }
                cascadeProfile =
                        SkyIslandHydraulicCascadeTransitionSolver
                                .solveFromCriticalInletToCriticalOutlet(
                                        cascadeSections, parameters);
            }
            double sharedJunctionDepth = cascadeProfile.points().getFirst().depthMeters();
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
                    label,
                    true,
                    true,
                    Math.max(
                            cascadeProfile.maximumEnergyResidualMeters(),
                            Math.max(
                                    component.maximumReachEnergyResidualMeters(),
                                    component.confluence().energyResidualMeters())),
                    "");
        } catch (IllegalArgumentException | IllegalStateException failure) {
            return new NaturalComponentProbe(
                    incomingBranches,
                    cascadeSections.size(),
                    label,
                    true,
                    false,
                    0.0,
                    failure.getClass().getSimpleName() + ":" + failure.getMessage()
                            + describeCascadeLimit(
                                    cascadeSections, parameters, failure.getMessage()));
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

    @Test
    void hydraulicMomentumResidualFeedsBackIntoBoundedKey700BedCandidate() {
        SkyIslandDescriptor descriptor = SkyIslandDescriptorGenerator.derive(
                SkyIslandIdentity.of(SEED, 8L, 81L, 700L));
        SkyIslandHydraulicLandformCandidatePlanner.Plan initial =
                SkyIslandHydraulicLandformCandidatePlanner.plan(descriptor, CALIBRATION);
        double initialResidual = residualNorm(cascadeResidualVector(initial));

        var feedback = SkyIslandHydraulicLandformCandidatePlanner.planWithHydraulicFeedback(
                descriptor,
                CALIBRATION,
                SkyIslandCandidateHydrologyCouplingTest::cascadeResidualVector,
                1.0e-8,
                6);
        assertTrue(
                feedback.optimization().residualNorm() <= initialResidual + 1.0e-8,
                "bounded geometry feedback must not worsen the physical momentum mismatch");
        assertEquals(
                initial.skeletonPlan().geomorphicNetwork().routes().size(),
                feedback.plan().reaches().size(),
                "bed feedback must preserve every authored semantic edge");
        SkyIslandHydraulicLandformCandidatePlanner.ReachCandidate cascade =
                feedback.plan().requireReach(801, 1951);
        assertEquals(
                0.0,
                cascade.sections().getFirst().bedElevationMeters()
                        - initial.requireReach(801, 1951).sections().getFirst().bedElevationMeters(),
                1.0e-8,
                "feedback must preserve the shared CASCADE inlet bed");
        assertEquals(
                0.0,
                cascade.sections().getLast().bedElevationMeters()
                        - initial.requireReach(801, 1951).sections().getLast().bedElevationMeters(),
                1.0e-8,
                "feedback must preserve the shared terminal bed");

        boolean exactJumpClosed;
        try {
            SkyIslandHydraulicCascadeTransitionSolver.solveFromCriticalInletToCriticalOutlet(
                    cascade.sections(), CALIBRATION.solverParameters());
            exactJumpClosed = true;
        } catch (IllegalArgumentException | IllegalStateException noPhysicalClosure) {
            exactJumpClosed = false;
        }
        assertFalse(
                feedback.residualsConverged() && !exactJumpClosed,
                "a minimized momentum residual must still pass the exact cascade closure solver");

        System.out.printf(
                Locale.ROOT,
                "HYDRAULIC_GEOMETRY_FEEDBACK key=700 initialHydraulicResidual=%.9g "
                        + "finalHydraulicResidual=%.9g controls=%s residualsConverged=%s "
                        + "exactJumpClosed=%s%n",
                initialResidual,
                feedback.optimization().residualNorm(),
                java.util.Arrays.toString(feedback.optimization().controls()),
                feedback.residualsConverged(),
                exactJumpClosed);
    }

    private static double[] cascadeResidualVector(
            SkyIslandHydraulicLandformCandidatePlanner.Plan candidate) {
        List<SkyIslandGraduallyVariedFlowSolver.CrossSection> cascade =
                candidate.requireReach(801, 1951).sections();
        var parameters = CALIBRATION.solverParameters();
        double bestForce = Double.NaN;
        double bestBranchGap = Double.POSITIVE_INFINITY;
        for (int station = 2; station <= cascade.size() - 4; station++) {
            List<SkyIslandGraduallyVariedFlowSolver.CrossSection> prefix =
                    cascade.subList(0, station + 1);
            List<SkyIslandGraduallyVariedFlowSolver.CrossSection> suffix =
                    cascade.subList(station, cascade.size());
            try {
                var supercritical = SkyIslandGraduallyVariedFlowSolver
                        .solveSupercriticalDownstreamFromCriticalControl(prefix, parameters);
                try {
                    var subcritical = SkyIslandGraduallyVariedFlowSolver
                            .solveSubcriticalUpstreamFromCriticalControl(suffix, parameters);
                    var section = cascade.get(station);
                    double mismatch = SkyIslandHydraulicJumpSolver.specificForce(
                                    section,
                                    supercritical.points().getLast().depthMeters(),
                                    parameters.gravityMetersPerSecondSquared())
                            - SkyIslandHydraulicJumpSolver.specificForce(
                                    section,
                                    subcritical.points().getFirst().depthMeters(),
                                    parameters.gravityMetersPerSecondSquared());
                    if (!Double.isFinite(bestForce) || Math.abs(mismatch) < Math.abs(bestForce)) {
                        bestForce = mismatch;
                    }
                } catch (IllegalArgumentException | IllegalStateException noSubcriticalBranch) {
                    double gap = SkyIslandGraduallyVariedFlowSolver
                            .subcriticalCriticalControlEnergyGap(suffix, parameters);
                    bestBranchGap = Math.min(bestBranchGap, gap);
                }
            } catch (IllegalArgumentException | IllegalStateException noSupercriticalPrefix) {
                // This station is beyond the admissible supercritical branch for this geometry.
            }
        }
        var scaleSection = cascade.get(cascade.size() / 2);
        double forceScale = Math.max(
                1.0, Math.pow(scaleSection.bottomWidthMeters(), 3.0));
        double energyScale = Math.max(
                1.0, SkyIslandGraduallyVariedFlowSolver.criticalDepth(cascade.getLast(), parameters));
        return new double[] {
            Double.isFinite(bestForce) ? bestForce / forceScale : 1.0,
            Double.isFinite(bestBranchGap) ? bestBranchGap / energyScale : 0.0
        };
    }

    private static double residualNorm(double[] residuals) {
        double sum = 0.0;
        for (double residual : residuals) {
            sum += residual * residual;
        }
        return Math.sqrt(sum);
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
                for (int i = 0; i < span.samples().size(); i++) {
                    double chainageMeters = (span.parentStartArcLength()
                                    + span.samples().get(i).arcLength())
                            * CALIBRATION.metersPerWorldUnit();
                    assertEquals(
                            interpolatedCandidateBed(candidateBed, chainageMeters),
                            physical.hydraulicProfile().points().get(i)
                                    .section().bedElevationMeters(),
                            1.0e-9,
                            "the physical solver must use the coupled candidate bed at every span station");
                }
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

    private static double interpolatedCandidateBed(
            List<SkyIslandGraduallyVariedFlowSolver.CrossSection> parentBed,
            double chainageMeters) {
        for (int i = 0; i + 1 < parentBed.size(); i++) {
            SkyIslandGraduallyVariedFlowSolver.CrossSection upstream = parentBed.get(i);
            SkyIslandGraduallyVariedFlowSolver.CrossSection downstream = parentBed.get(i + 1);
            if (chainageMeters <= downstream.chainageMeters()) {
                double fraction = (chainageMeters - upstream.chainageMeters())
                        / (downstream.chainageMeters() - upstream.chainageMeters());
                return upstream.bedElevationMeters()
                        + fraction * (downstream.bedElevationMeters() - upstream.bedElevationMeters());
            }
        }
        return parentBed.getLast().bedElevationMeters();
    }

    private static long reachKey(int startCellIndex, int endCellIndex) {
        return ((long) startCellIndex << 32) | (endCellIndex & 0xffffffffL);
    }

    private record NaturalComponentProbe(
            int incomingBranches,
            int cascadeSections,
            String terminalFate,
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
