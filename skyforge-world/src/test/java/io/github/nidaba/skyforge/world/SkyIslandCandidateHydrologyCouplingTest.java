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
                                * reach.channelWidthScaleAtSection(i)
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
        SkyIslandHydraulicLandformCandidatePlanner.Plan baseline =
                SkyIslandHydraulicLandformCandidatePlanner.plan(descriptor, CALIBRATION);
        int[] feedbackTrialIndex = {0};

        // Let bounded bed/valley candidate generation respond to the exact natural component,
        // not only to a local cascade residual. A complete confluence/CASCADE closure is the only
        // zero-residual outcome; otherwise the physical cascade momentum residual ranks trials.
        try {
            var feedback = SkyIslandHydraulicLandformCandidatePlanner.planWithHydraulicFeedback(
                    descriptor,
                    CALIBRATION,
                    candidate -> {
                        double[] residuals =
                                naturalComponentResidualVector(descriptor, candidate, parameters);
                        var candidateCascade = candidate.requireReach(801, 1951);
                        var baselineCascade = baseline.requireReach(801, 1951);
                        double outletBedDelta = candidateCascade.sections().getLast().bedElevationMeters()
                                - baselineCascade.sections().getLast().bedElevationMeters();
                        double minimumWidthScale = candidateCascade.sectionWidthScales().stream()
                                .mapToDouble(Double::doubleValue).min().orElse(1.0);
                        double maximumWidthScale = candidateCascade.sectionWidthScales().stream()
                                .mapToDouble(Double::doubleValue).max().orElse(1.0);
                        SkyIslandHydraulicGeometrySkeletonSample outletSample =
                                baselineCascade.skeleton().samples().getLast();
                        double outletSurfaceMeters = outletSample.terrainElevation()
                                * descriptor.reliefBudget() * CALIBRATION.metersPerWorldUnit();
                        double outletDepthMeters = outletSurfaceMeters
                                - baselineCascade.sections().getLast().bedElevationMeters();
                        double outletIncisionSlackMeters =
                                CALIBRATION.maximumCrossSectionDepthMeters(outletSample)
                                        - outletDepthMeters;
                        System.out.printf(
                                Locale.ROOT,
                                "HYDRAULIC_GEOMETRY_TRIAL key=700 trial=%d outletBedDeltaMeters=%.9g "
                                        + "outletIncisionSlackMeters=%.9g poolWidthScaleRange=%.6g..%.6g "
                                        + "residuals=%s%n",
                                feedbackTrialIndex[0]++,
                                outletBedDelta,
                                outletIncisionSlackMeters,
                                minimumWidthScale,
                                maximumWidthScale,
                                java.util.Arrays.toString(residuals));
                        return residuals;
                    },
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
        CascadeResidualDiagnostics diagnostics = cascadeResidualDiagnostics(candidate);
        System.out.printf(
                Locale.ROOT,
                "HYDRAULIC_GEOMETRY_DIAGNOSTIC key=700 %s%n",
                diagnostics);
        return new double[] {
            diagnostics.normalizedForceResidual(),
            diagnostics.normalizedBranchEnergyGap(),
            diagnostics.normalizedBranchStationSeparation()
        };
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
        assertTrue(
                cascade.sectionWidthScales().stream()
                        .allMatch(scale -> scale >= 0.75 && scale <= 1.25),
                "candidate channel width must remain inside the explicit morphology envelope");
        for (int source : List.of(660, 1140)) {
            SkyIslandHydraulicLandformCandidatePlanner.ReachCandidate incoming =
                    feedback.plan().requireReach(source, 801);
            assertTrue(
                    incoming.sectionWidthScales().stream().allMatch(scale -> scale == 1.0),
                    "localized cascade-pool widening must leave source branches unchanged");
        }
        for (int i = 0; i < cascade.sections().size(); i++) {
            SkyIslandHydraulicGeometrySkeletonSample sample = cascade.skeleton().samples().get(i);
            double localTerrainMeters = sample.terrainElevation()
                    * descriptor.reliefBudget() * CALIBRATION.metersPerWorldUnit();
            double depth = localTerrainMeters - cascade.sections().get(i).bedElevationMeters();
            double reconstructedBankfullWidth = cascade.sections().get(i).bottomWidthMeters()
                    + 2.0 * CALIBRATION.sideSlopeHorizontalToVertical() * depth;
            assertEquals(
                    2.0 * sample.bankfullHalfWidth()
                            * cascade.channelWidthScaleAtSection(i)
                            * CALIBRATION.metersPerWorldUnit(),
                    reconstructedBankfullWidth,
                    1.0e-8,
                    "hydraulic section width must match the jointly scaled valley/channel candidate");
        }
        List<SkyIslandChannelProfile> cascadeProfiles =
                cascade.skeleton().geomorphicRoute().semanticReach().profiles();
        for (int i = 1; i < cascade.sections().size(); i++) {
            SkyIslandChannelProfileKind upstreamKind = profileKindAt(
                    cascadeProfiles, cascade.skeleton().samples().get(i - 1).stationFraction());
            SkyIslandChannelProfileKind downstreamKind = profileKindAt(
                    cascadeProfiles, cascade.skeleton().samples().get(i).stationFraction());
            if (upstreamKind == SkyIslandChannelProfileKind.CASCADE
                    && downstreamKind == SkyIslandChannelProfileKind.CASCADE) {
                assertTrue(
                        cascade.sections().get(i).bedElevationMeters()
                                <= cascade.sections().get(i - 1).bedElevationMeters() + 1.0e-8,
                        "CASCADE bed must not rise downstream within its controlled chute");
            }
        }
        assertEquals(
                0.0,
                cascade.sections().getFirst().bedElevationMeters()
                        - initial.requireReach(801, 1951).sections().getFirst().bedElevationMeters(),
                1.0e-8,
                "feedback must preserve the shared CASCADE inlet bed");
        assertTrue(
                cascade.sections().getLast().bedElevationMeters()
                        <= initial.requireReach(801, 1951).sections().getLast().bedElevationMeters()
                                + 1.0e-8,
                "edge-outlet feedback may lower, but must not raise, the terminal bed");

        String exactScanDetails = exactCascadeClosureDiagnostics(cascade.sections());
        boolean exactJumpClosed;
        String exactJumpFailure = "none";
        try {
            SkyIslandHydraulicCascadeTransitionSolver.solveFromCriticalInletToCriticalOutlet(
                    cascade.sections(), CALIBRATION.solverParameters());
            exactJumpClosed = true;
        } catch (IllegalArgumentException | IllegalStateException noPhysicalClosure) {
            exactJumpClosed = false;
            exactJumpFailure = noPhysicalClosure.getClass().getSimpleName()
                    + ":" + noPhysicalClosure.getMessage();
        }
        assertFalse(
                feedback.residualsConverged() && !exactJumpClosed,
                "a minimized momentum residual must still pass the exact cascade closure solver");

        System.out.printf(
                Locale.ROOT,
                "HYDRAULIC_GEOMETRY_FEEDBACK key=700 initialHydraulicResidual=%.9g "
                        + "finalHydraulicResidual=%.9g controls=%s residualsConverged=%s "
                        + "exactScan=%s exactJumpClosed=%s exactJumpFailure=%s%n",
                initialResidual,
                feedback.optimization().residualNorm(),
                java.util.Arrays.toString(feedback.optimization().controls()),
                feedback.residualsConverged(),
                exactScanDetails,
                exactJumpClosed,
                exactJumpFailure);
    }

    private static double[] cascadeResidualVector(
            SkyIslandHydraulicLandformCandidatePlanner.Plan candidate) {
        CascadeResidualDiagnostics diagnostics = cascadeResidualDiagnostics(candidate);
        System.out.printf(
                Locale.ROOT,
                "HYDRAULIC_GEOMETRY_DIAGNOSTIC key=700 %s%n",
                diagnostics);
        return new double[] {
            diagnostics.normalizedForceResidual(),
            diagnostics.normalizedBranchEnergyGap()
        };
    }

    /**
     * Mirrors the production cascade solver's split search and the jump solver's complete
     * station scan (including its terminal-interval subdivisions). This reports whether the
     * production candidate set contains any paired branches; it does not accept a jump.
     */
    private static String exactCascadeClosureDiagnostics(
            List<SkyIslandGraduallyVariedFlowSolver.CrossSection> cascade) {
        var parameters = CALIBRATION.solverParameters();
        int candidateSplits = 0;
        int validInletSplits = 0;
        int minimumValidSplit = Integer.MAX_VALUE;
        int maximumValidSplit = -1;
        int pairedBranchSplits = 0;
        int unpairedBranchSplits = 0;
        int bestSplit = -1;
        double bestAbsoluteForceResidual = Double.POSITIVE_INFINITY;
        String firstPrefixFailure = "none";
        String firstUnpairedFailure = "none";
        for (int split = cascade.size() - 4; split >= 2; split--) {
            candidateSplits++;
            SkyIslandGraduallyVariedFlowSolver.Result prefix;
            try {
                prefix = SkyIslandGraduallyVariedFlowSolver
                        .solveSupercriticalDownstreamFromCriticalControl(
                                cascade.subList(0, split + 1), parameters);
            } catch (IllegalArgumentException | IllegalStateException invalidPrefix) {
                if (firstPrefixFailure.equals("none")) {
                    firstPrefixFailure = "split=" + split + ":" + invalidPrefix.getMessage();
                }
                continue;
            }
            validInletSplits++;
            minimumValidSplit = Math.min(minimumValidSplit, split);
            maximumValidSplit = Math.max(maximumValidSplit, split);
            double inletDepth = prefix.points().getLast().depthMeters();
            List<SkyIslandGraduallyVariedFlowSolver.CrossSection> jumpReach =
                    cascade.subList(split, cascade.size());
            try {
                double forceResidual = SkyIslandHydraulicJumpProfileSolver
                        .bestSpecificForceResidual(jumpReach, inletDepth, null, parameters);
                pairedBranchSplits++;
                if (Math.abs(forceResidual) < bestAbsoluteForceResidual) {
                    bestAbsoluteForceResidual = Math.abs(forceResidual);
                    bestSplit = split;
                }
            } catch (IllegalArgumentException | IllegalStateException noPairedBranch) {
                unpairedBranchSplits++;
                if (firstUnpairedFailure.equals("none")) {
                    firstUnpairedFailure = "split=" + split + ":" + noPairedBranch.getMessage();
                }
            }
        }
        String subcriticalDetails = subcriticalCandidateDiagnostics(cascade, parameters);
        String firstPrefixContext = firstPrefixFailure
                + describeCascadeLimit(cascade, parameters, firstPrefixFailure);
        return String.format(
                Locale.ROOT,
                "splits=%d,inletValid=%d,inletSplitRange=%d..%d,paired=%d,unpaired=%d,bestSplit=%d,"
                        + "bestAbsForceResidual=%.9g,firstPrefixFailure=%s,firstUnpairedFailure=%s,"
                        + "subcriticalCandidates={%s}",
                candidateSplits,
                validInletSplits,
                minimumValidSplit == Integer.MAX_VALUE ? -1 : minimumValidSplit,
                maximumValidSplit,
                pairedBranchSplits,
                unpairedBranchSplits,
                bestSplit,
                bestAbsoluteForceResidual,
                firstPrefixContext,
                firstUnpairedFailure,
                subcriticalDetails);
    }

    /**
     * Reports branch availability at every integer and terminal-subdivision jump position
     * reachable by the production split search, together with the local first failed step.
     */
    private static String subcriticalCandidateDiagnostics(
            List<SkyIslandGraduallyVariedFlowSolver.CrossSection> cascade,
            SkyIslandGraduallyVariedFlowSolver.Parameters parameters) {
        int candidates = 0;
        int valid = 0;
        String firstValidCandidate = "none";
        String lastValidCandidate = "none";
        double minimumGap = Double.POSITIVE_INFINITY;
        String firstFailure = "none";
        String minimumGapContext = "none";
        for (int start = 3; start <= cascade.size() - 2; start++) {
            List<SkyIslandGraduallyVariedFlowSolver.CrossSection> suffix =
                    cascade.subList(start, cascade.size());
            candidates++;
            try {
                SkyIslandGraduallyVariedFlowSolver.solveSubcriticalUpstreamFromCriticalControl(
                        suffix, parameters);
                valid++;
                if (firstValidCandidate.equals("none")) {
                    firstValidCandidate = "section=" + start;
                }
                lastValidCandidate = "section=" + start;
            } catch (IllegalArgumentException | IllegalStateException failure) {
                double gap = SkyIslandGraduallyVariedFlowSolver
                        .subcriticalCriticalControlEnergyGap(suffix, parameters);
                String context = subcriticalFailureContext(suffix, start, failure.getMessage(), gap, parameters);
                if (firstFailure.equals("none")) {
                    firstFailure = context;
                }
                if (gap < minimumGap) {
                    minimumGap = gap;
                    minimumGapContext = context;
                }
            }
        }
        int terminalInterval = cascade.size() - 2;
        for (int subdivision = 1; subdivision <= 8; subdivision++) {
            double fraction = (double) subdivision / 9.0;
            var interpolated = interpolateSection(
                    cascade.get(terminalInterval), cascade.get(terminalInterval + 1), fraction);
            List<SkyIslandGraduallyVariedFlowSolver.CrossSection> suffix = List.of(
                    interpolated, cascade.getLast());
            candidates++;
            try {
                SkyIslandGraduallyVariedFlowSolver.solveSubcriticalUpstreamFromCriticalControl(
                        suffix, parameters);
                valid++;
                String candidate = "terminalFraction=" + fraction;
                if (firstValidCandidate.equals("none")) {
                    firstValidCandidate = candidate;
                }
                lastValidCandidate = candidate;
            } catch (IllegalArgumentException | IllegalStateException failure) {
                double gap = SkyIslandGraduallyVariedFlowSolver
                        .subcriticalCriticalControlEnergyGap(suffix, parameters);
                String context = "terminalFraction=" + fraction + ":" + failure.getMessage()
                        + ":energyGapMeters=" + String.format(Locale.ROOT, "%.9g", gap);
                if (firstFailure.equals("none")) {
                    firstFailure = context;
                }
                if (gap < minimumGap) {
                    minimumGap = gap;
                    minimumGapContext = context;
                }
            }
        }
        return String.format(
                Locale.ROOT,
                "tested=%d,valid=%d,invalid=%d,validRange=%s..%s,"
                        + "minGapMeters=%.9g,minGapAt=%s,firstFailure=%s",
                candidates,
                valid,
                candidates - valid,
                firstValidCandidate,
                lastValidCandidate,
                minimumGap,
                minimumGapContext,
                firstFailure);
    }

    private static String subcriticalFailureContext(
            List<SkyIslandGraduallyVariedFlowSolver.CrossSection> suffix,
            int absoluteStart,
            String failure,
            double gapMeters,
            SkyIslandGraduallyVariedFlowSolver.Parameters parameters) {
        String marker = "section ";
        int markerIndex = failure == null ? -1 : failure.lastIndexOf(marker);
        if (markerIndex < 0) {
            return String.format(
                    Locale.ROOT,
                    "start=%d:%s:energyGapMeters=%.9g",
                    absoluteStart, failure, gapMeters);
        }
        int numberStart = markerIndex + marker.length();
        int numberEnd = numberStart;
        while (numberEnd < failure.length() && Character.isDigit(failure.charAt(numberEnd))) {
            numberEnd++;
        }
        try {
            int localSection = Integer.parseInt(failure.substring(numberStart, numberEnd));
            if (localSection < 0 || localSection + 1 >= suffix.size()) {
                return String.format(
                        Locale.ROOT,
                        "start=%d:%s:energyGapMeters=%.9g",
                        absoluteStart, failure, gapMeters);
            }
            var upstream = suffix.get(localSection);
            var downstream = suffix.get(localSection + 1);
            double spacing = downstream.chainageMeters() - upstream.chainageMeters();
            return String.format(
                    Locale.ROOT,
                    "start=%d,failedLocal=%d,failedAbs=%d,energyGapMeters=%.9g,"
                            + "bedRiseDownstream=%.9g,bedSlope=%.9g,dischargeDelta=%.9g,"
                            + "bottomWidthDelta=%.9g,spacing=%.9g",
                    absoluteStart,
                    localSection,
                    absoluteStart + localSection,
                    gapMeters,
                    downstream.bedElevationMeters() - upstream.bedElevationMeters(),
                    (upstream.bedElevationMeters() - downstream.bedElevationMeters()) / spacing,
                    downstream.dischargeCubicMetersPerSecond()
                            - upstream.dischargeCubicMetersPerSecond(),
                    downstream.bottomWidthMeters() - upstream.bottomWidthMeters(),
                    spacing);
        } catch (NumberFormatException malformedFailure) {
            return String.format(
                    Locale.ROOT,
                    "start=%d:%s:energyGapMeters=%.9g",
                    absoluteStart, failure, gapMeters);
        }
    }

    private static SkyIslandGraduallyVariedFlowSolver.CrossSection interpolateSection(
            SkyIslandGraduallyVariedFlowSolver.CrossSection first,
            SkyIslandGraduallyVariedFlowSolver.CrossSection second,
            double fraction) {
        return new SkyIslandGraduallyVariedFlowSolver.CrossSection(
                lerp(first.chainageMeters(), second.chainageMeters(), fraction),
                lerp(first.bedElevationMeters(), second.bedElevationMeters(), fraction),
                lerp(first.dischargeCubicMetersPerSecond(),
                        second.dischargeCubicMetersPerSecond(), fraction),
                lerp(first.bottomWidthMeters(), second.bottomWidthMeters(), fraction),
                lerp(first.sideSlopeHorizontalToVertical(),
                        second.sideSlopeHorizontalToVertical(), fraction));
    }

    private static double lerp(double first, double second, double fraction) {
        return first + fraction * (second - first);
    }

    private static CascadeResidualDiagnostics cascadeResidualDiagnostics(
            SkyIslandHydraulicLandformCandidatePlanner.Plan candidate) {
        List<SkyIslandGraduallyVariedFlowSolver.CrossSection> cascade =
                candidate.requireReach(801, 1951).sections();
        var parameters = CALIBRATION.solverParameters();
        int sampledStations = cascade.size() - 5;
        double[] supercriticalDepths = new double[cascade.size()];
        double[] subcriticalDepths = new double[cascade.size()];
        java.util.Arrays.fill(supercriticalDepths, Double.NaN);
        java.util.Arrays.fill(subcriticalDepths, Double.NaN);

        int supercriticalValid = 0;
        int lastSupercriticalStation = -1;
        String firstSupercriticalFailure = "none";
        try {
            var sourcePair = SkyIslandGraduallyVariedFlowSolver
                    .solveSupercriticalDownstreamFromCriticalControl(cascade.subList(0, 2), parameters);
            supercriticalDepths[0] = sourcePair.points().getFirst().depthMeters();
            supercriticalDepths[1] = sourcePair.points().getLast().depthMeters();
            for (int station = 2; station <= cascade.size() - 4; station++) {
                try {
                    var step = SkyIslandGraduallyVariedFlowSolver.solveSupercriticalDownstream(
                            List.of(cascade.get(station - 1), cascade.get(station)),
                            supercriticalDepths[station - 1],
                            parameters);
                    supercriticalDepths[station] = step.points().getLast().depthMeters();
                    supercriticalValid++;
                    lastSupercriticalStation = station;
                } catch (IllegalArgumentException | IllegalStateException failure) {
                    firstSupercriticalFailure = "station=" + station + ":" + failure.getMessage();
                    break;
                }
            }
        } catch (IllegalArgumentException | IllegalStateException failure) {
            firstSupercriticalFailure = "station=2:" + failure.getMessage();
        }
        int supercriticalInvalid = sampledStations - supercriticalValid;

        int subcriticalValid = 0;
        int firstSubcriticalValidStation = -1;
        String firstSubcriticalFailure = "none";
        try {
            int last = cascade.size() - 1;
            var outletPair = SkyIslandGraduallyVariedFlowSolver
                    .solveSubcriticalUpstreamFromCriticalControl(
                            cascade.subList(last - 1, last + 1), parameters);
            subcriticalDepths[last - 1] = outletPair.points().getFirst().depthMeters();
            subcriticalDepths[last] = outletPair.points().getLast().depthMeters();
            firstSubcriticalValidStation = last - 1;
            for (int station = last - 2; station >= 3; station--) {
                try {
                    var step = SkyIslandGraduallyVariedFlowSolver.solveSubcriticalUpstream(
                            List.of(cascade.get(station), cascade.get(station + 1)),
                            subcriticalDepths[station + 1],
                            parameters);
                    subcriticalDepths[station] = step.points().getFirst().depthMeters();
                    firstSubcriticalValidStation = station;
                } catch (IllegalArgumentException | IllegalStateException failure) {
                    firstSubcriticalFailure = "station=" + station + ":" + failure.getMessage();
                    break;
                }
            }
        } catch (IllegalArgumentException | IllegalStateException failure) {
            firstSubcriticalFailure = "station=" + (cascade.size() - 2) + ":" + failure.getMessage();
        }
        int firstCandidateStation = Math.max(2, firstSubcriticalValidStation);
        int lastCandidateStation = cascade.size() - 4;
        subcriticalValid = firstSubcriticalValidStation < 0
                ? 0
                : Math.max(0, lastCandidateStation - firstCandidateStation + 1);
        int subcriticalInvalid = sampledStations - subcriticalValid;
        int stationSeparation = firstSubcriticalValidStation < 0 || lastSupercriticalStation < 0
                ? sampledStations
                : Math.max(0, firstSubcriticalValidStation - lastSupercriticalStation);
        double normalizedStationSeparation = (double) stationSeparation / Math.max(1, cascade.size());

        double bestForce = Double.NaN;
        int bestForceStation = -1;
        double bestUpstreamDepth = Double.NaN;
        double bestDownstreamDepth = Double.NaN;
        double bestUpstreamFroude = Double.NaN;
        double bestDownstreamFroude = Double.NaN;
        int pairedStations = 0;
        int adjacentForceSignChanges = 0;
        int previousValidStation = -2;
        double previousForce = Double.NaN;
        for (int station = 2; station <= cascade.size() - 4; station++) {
            double upstreamDepth = supercriticalDepths[station];
            double downstreamDepth = subcriticalDepths[station];
            if (!Double.isFinite(upstreamDepth) || !Double.isFinite(downstreamDepth)) {
                previousValidStation = -2;
                previousForce = Double.NaN;
                continue;
            }
            pairedStations++;
            var section = cascade.get(station);
            double mismatch = SkyIslandHydraulicJumpSolver.specificForce(
                            section, upstreamDepth, parameters.gravityMetersPerSecondSquared())
                    - SkyIslandHydraulicJumpSolver.specificForce(
                            section, downstreamDepth, parameters.gravityMetersPerSecondSquared());
            if (previousValidStation == station - 1
                    && Double.isFinite(previousForce)
                    && previousForce * mismatch <= 0.0) {
                adjacentForceSignChanges++;
            }
            previousValidStation = station;
            previousForce = mismatch;
            if (!Double.isFinite(bestForce) || Math.abs(mismatch) < Math.abs(bestForce)) {
                bestForce = mismatch;
                bestForceStation = station;
                bestUpstreamDepth = upstreamDepth;
                bestDownstreamDepth = downstreamDepth;
                bestUpstreamFroude =
                        SkyIslandGraduallyVariedFlowSolver.froudeNumber(section, upstreamDepth, parameters);
                bestDownstreamFroude =
                        SkyIslandGraduallyVariedFlowSolver.froudeNumber(section, downstreamDepth, parameters);
            }
        }

        int bestBranchGapStation = -1;
        double bestBranchGap = 0.0;
        if (pairedStations == 0 && lastSupercriticalStation >= 2) {
            bestBranchGapStation = lastSupercriticalStation;
            bestBranchGap = SkyIslandGraduallyVariedFlowSolver.subcriticalCriticalControlEnergyGap(
                    cascade.subList(lastSupercriticalStation, cascade.size()), parameters);
        }
        var scaleSection = cascade.get(cascade.size() / 2);
        double forceScale = Math.max(1.0, Math.pow(scaleSection.bottomWidthMeters(), 3.0));
        double energyScale = Math.max(
                1.0, SkyIslandGraduallyVariedFlowSolver.criticalDepth(cascade.getLast(), parameters));
        double normalizedForceResidual = Double.isFinite(bestForce) ? bestForce / forceScale : 1.0;
        double normalizedBranchGap = bestBranchGap / energyScale;
        double conjugateDepthGap = Double.NaN;
        String conjugateCheck = "no-paired-station";
        if (bestForceStation >= 0) {
            try {
                var conjugate = SkyIslandHydraulicJumpSolver.solveConjugateDepth(
                        cascade.get(bestForceStation),
                        bestUpstreamDepth,
                        parameters.gravityMetersPerSecondSquared(),
                        parameters.relativeTolerance(),
                        parameters.maximumIterations());
                conjugateDepthGap = conjugate.downstreamDepthMeters() - bestDownstreamDepth;
                conjugateCheck = "solved";
            } catch (IllegalArgumentException | IllegalStateException invalidConjugate) {
                conjugateCheck = invalidConjugate.getMessage();
            }
        }
        return new CascadeResidualDiagnostics(
                normalizedForceResidual,
                normalizedBranchGap,
                normalizedStationSeparation,
                sampledStations,
                supercriticalValid,
                supercriticalInvalid,
                subcriticalValid,
                subcriticalInvalid,
                pairedStations,
                adjacentForceSignChanges,
                lastSupercriticalStation,
                firstSubcriticalValidStation,
                bestForceStation,
                bestForce,
                bestUpstreamDepth,
                bestDownstreamDepth,
                bestUpstreamFroude,
                bestDownstreamFroude,
                bestBranchGapStation,
                bestBranchGap,
                conjugateDepthGap,
                conjugateCheck,
                firstSupercriticalFailure,
                firstSubcriticalFailure);
    }


    private static SkyIslandChannelProfileKind profileKindAt(
            List<SkyIslandChannelProfile> profiles, double station) {
        int index = Math.min(
                profiles.size() - 1,
                (int) Math.floor(Math.max(0.0, Math.min(0.999999999, station)) * profiles.size()));
        return profiles.get(index).kind();
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

    private record CascadeResidualDiagnostics(
            double normalizedForceResidual,
            double normalizedBranchEnergyGap,
            double normalizedBranchStationSeparation,
            int sampledStations,
            int supercriticalValidStations,
            int supercriticalInvalidStations,
            int subcriticalValidStations,
            int subcriticalInvalidStations,
            int pairedStations,
            int adjacentForceSignChanges,
            int lastSupercriticalStation,
            int firstSubcriticalValidStation,
            int bestForceStation,
            double bestForceResidualCubicMeters,
            double bestUpstreamDepthMeters,
            double bestDownstreamDepthMeters,
            double bestUpstreamFroude,
            double bestDownstreamFroude,
            int bestBranchGapStation,
            double bestBranchEnergyGapMeters,
            double conjugateDepthGapMeters,
            String conjugateCheck,
            String firstSupercriticalFailure,
            String firstSubcriticalFailure) {}

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
