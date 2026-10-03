package io.github.nidaba.skyforge.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandIdentity;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;

class SkyIslandHydraulicGeometrySkeletonPlannerTest {
    private static final long SEED = 0x534B59464F524745L;
    private static final double EPSILON = 1.0e-12;

    @Test
    void naturalKey700D2RefinementIsReportedAlongsideComponentSpanOutcomes() throws Exception {
        SkyIslandDescriptor descriptor = descriptor(700L);
        SkyIslandGeomorphicChannelNetworkPlan network =
                SkyIslandGeomorphicChannelNetworkPlanner.plan(descriptor);
        SkyIslandPreHydrologicTerrainField terrain =
                SkyIslandPreHydrologicTerrainField.create(descriptor);
        SkyIslandSemanticField interiority =
                SkyIslandSemanticFieldSet.create(descriptor).interiority();
        java.util.Set<String> incidentReachIds = java.util.Set.of(
                "660->801", "801->1951", "1140->801");
        List<SkyIslandGeomorphicReachRoute> incidentRoutes = network.routes().stream()
                .filter(route -> incidentReachIds.contains(
                        route.semanticReach().startCellIndex()
                                + "->"
                                + route.semanticReach().endCellIndex()))
                .toList();
        assertEquals(3, incidentRoutes.size(), "key 700 incident-reach identities are fixed");

        StringBuilder report = new StringBuilder(
                "F3L_KEY700_D2_COMPONENT seed=0x534B59464F524745 key=700"
                        + System.lineSeparator());
        String objectiveFailure = null;
        for (SkyIslandGeomorphicReachRoute route : incidentRoutes) {
            var first = SkyIslandHydraulicGeometrySkeletonPlanner.refineCenterline(
                    descriptor, network, route, terrain, interiority);
            var second = SkyIslandHydraulicGeometrySkeletonPlanner.refineCenterline(
                    descriptor, network, route, terrain, interiority);
            assertEquals(first.centerline(), second.centerline());
            assertEquals(first.diagnostics(), second.diagnostics());
            var d = first.diagnostics();
            assertTrue(
                    d.finalIntegratedSquaredHeadEnvelopeGap()
                            <= d.initialIntegratedSquaredHeadEnvelopeGap() + EPSILON);
            assertTrue(
                    d.finalLongitudinalHeadFeasibilityGap()
                            <= d.initialLongitudinalHeadFeasibilityGap() + EPSILON);
            String routeLabel = route.semanticReach().startCellIndex()
                    + "->" + route.semanticReach().endCellIndex();
            assertTrue(
                    d.finalMaximumSourceEndpointEnvelopeConflict()
                                    <= d.initialMaximumSourceEndpointEnvelopeConflict() + EPSILON,
                    "source endpoint envelope conflict regressed on " + routeLabel + ": "
                            + d.initialMaximumSourceEndpointEnvelopeConflict() + " -> "
                            + d.finalMaximumSourceEndpointEnvelopeConflict());
            if (routeLabel.equals("660->801") || routeLabel.equals("1140->801")) {
                assertTrue(
                        d.finalMaximumSourceEndpointEnvelopeConflict()
                                        < d.initialMaximumSourceEndpointEnvelopeConflict() - EPSILON,
                        "source endpoint envelope conflict did not improve on " + routeLabel
                                + ": " + d.initialMaximumSourceEndpointEnvelopeConflict()
                                + " -> " + d.finalMaximumSourceEndpointEnvelopeConflict());
            }
            assertTrue(
                    d.finalMaximumLocalEnvelopeConflict()
                                    <= d.initialMaximumLocalEnvelopeConflict() + EPSILON,
                    "local envelope conflict regressed on " + routeLabel + ": "
                            + d.initialMaximumLocalEnvelopeConflict() + " -> "
                            + d.finalMaximumLocalEnvelopeConflict());
            assertTrue(
                    d.finalMaximumLongitudinalGradeConflict()
                                    <= d.initialMaximumLongitudinalGradeConflict() + EPSILON,
                    "grade-propagation conflict regressed on " + routeLabel + ": "
                            + d.initialMaximumLongitudinalGradeConflict() + " -> "
                            + d.finalMaximumLongitudinalGradeConflict());
            if (route.semanticReach().startCellIndex() == 801
                    && route.semanticReach().endCellIndex() == 1951) {
                var initialTargetScore =
                        SkyIslandHydraulicGeometrySkeletonPlanner.confluenceCascadeGradeConflictDetails(
                                descriptor, network, route.semanticReach(),
                                route.route().points(), terrain);
                var finalTargetScore =
                        SkyIslandHydraulicGeometrySkeletonPlanner.confluenceCascadeGradeConflictDetails(
                                descriptor, network, route.semanticReach(),
                                first.centerline().points(), terrain);
                report.append("OBJECTIVE_SPAN initial=")
                        .append(initialTargetScore)
                        .append(" final=")
                        .append(finalTargetScore)
                        .append(System.lineSeparator());
                if (objectiveFailure == null
                        && (initialTargetScore.sampleCount() == 0
                                || Math.abs(initialTargetScore.startStation()
                                        - 0.13043478260869565) > EPSILON
                                || Math.abs(initialTargetScore.endStation()
                                        - 0.7391304347826086) > EPSILON)) {
                    objectiveFailure =
                            "objective must score the exact F3D free confluence-to-CASCADE window: "
                                    + initialTargetScore;
                }
                assertTrue(d.initialLongitudinalHeadFeasibilityGap() > EPSILON);
                assertTrue(
                        d.finalLongitudinalHeadFeasibilityGap()
                                < d.initialLongitudinalHeadFeasibilityGap(),
                        "whole-route refinement must reduce the integrated key-700 grade conflict");
                assertTrue(
                        d.finalMaximumLongitudinalGradeConflict()
                                < d.initialMaximumLongitudinalGradeConflict(),
                        "minimax refinement must reduce the worst key-700 grade conflict");
                if (!(d.finalMaximumConfluenceCascadeGradeConflict()
                        < d.initialMaximumConfluenceCascadeGradeConflict())) {
                    objectiveFailure =
                            "refinement must reduce free grade conflict on the confluence-to-CASCADE ordinary span: "
                                    + d.initialMaximumConfluenceCascadeGradeConflict() + " -> "
                                    + d.finalMaximumConfluenceCascadeGradeConflict();
                }
            }
            report.append("CENTERLINE ")
                    .append(route.semanticReach().startCellIndex())
                    .append("->")
                    .append(route.semanticReach().endCellIndex())
                    .append(" initialMaxGap=").append(d.initialMaximumHeadEnvelopeGap())
                    .append(" finalMaxGap=").append(d.finalMaximumHeadEnvelopeGap())
                    .append(" initialIntegratedGap=")
                    .append(d.initialIntegratedSquaredHeadEnvelopeGap())
                    .append(" finalIntegratedGap=")
                    .append(d.finalIntegratedSquaredHeadEnvelopeGap())
                    .append(" initialLongitudinalGap=")
                    .append(d.initialLongitudinalHeadFeasibilityGap())
                    .append(" finalLongitudinalGap=")
                    .append(d.finalLongitudinalHeadFeasibilityGap())
                    .append(" initialMaxLocalEnvelopeConflict=")
                    .append(d.initialMaximumLocalEnvelopeConflict())
                    .append(" finalMaxLocalEnvelopeConflict=")
                    .append(d.finalMaximumLocalEnvelopeConflict())
                    .append(" initialMaxSourceEndpointEnvelopeConflict=")
                    .append(d.initialMaximumSourceEndpointEnvelopeConflict())
                    .append(" finalMaxSourceEndpointEnvelopeConflict=")
                    .append(d.finalMaximumSourceEndpointEnvelopeConflict())
                    .append(" initialMaxLongitudinalGradeConflict=")
                    .append(d.initialMaximumLongitudinalGradeConflict())
                    .append(" finalMaxLongitudinalGradeConflict=")
                    .append(d.finalMaximumLongitudinalGradeConflict())
                    .append(" initialConfluenceCascadeGradeConflict=")
                    .append(d.initialMaximumConfluenceCascadeGradeConflict())
                    .append(" finalConfluenceCascadeGradeConflict=")
                    .append(d.finalMaximumConfluenceCascadeGradeConflict())
                    .append(" selectedLateralMoves=").append(d.selectedLateralMoves())
                    .append(" globalModeAcceptedMoves=")
                    .append(d.globalModeSearchAcceptedMoves())
                    .append(System.lineSeparator());
        }

        SkyIslandHydraulicTransitionGeometryEvidencePlan transitionGeometry =
                SkyIslandHydraulicTransitionGeometryEvidencePlanner.plan(descriptor);
        SkyIslandSemanticField transitionTerrain =
                SkyIslandPreHydrologicTerrainField.create(descriptor);
        SkyIslandGeomorphicQualificationPolicy transitionPolicy =
                SkyIslandGeomorphicQualificationPolicy.firstEvidenceBacked();
        SkyIslandWatershedPlan transitionWatershed =
                SkyIslandWatershedPlanner.plan(descriptor);
        SkyIslandConfluenceHeadCompatibilityPlan confluenceHeads =
                SkyIslandConfluenceHeadCompatibilityPlanner.plan(
                        descriptor, transitionGeometry, transitionTerrain, transitionPolicy);
        SkyIslandCascadeHeadCompatibilityPlan cascadeHeads =
                SkyIslandCascadeHeadCompatibilityPlanner.plan(
                        descriptor, transitionGeometry, transitionTerrain,
                        transitionPolicy, transitionWatershed);
        SkyIslandConfluenceCascadeHeadCompatibilityPlan jointHeads =
                SkyIslandConfluenceCascadeHeadCompatibilityPlanner.plan(
                        descriptor, transitionGeometry, transitionTerrain,
                        transitionPolicy, transitionWatershed);
        SkyIslandOrdinarySpanPlan spans = SkyIslandOrdinarySpanPlanner.plan(
                descriptor, confluenceHeads, cascadeHeads, jointHeads,
                transitionTerrain, transitionPolicy);
        for (SkyIslandConfluenceCascadeHeadCompatibilityOutcome joint : jointHeads.outcomes()) {
            if (joint.confluence().transitionSite().nodeCellIndex() == 801
                    && joint.cascade().transitionSite().reachStartCellIndex() == 801
                    && joint.cascade().transitionSite().reachEndCellIndex() == 1951) {
                double nearHead = joint.cascadeConfluenceSideHeadWorldUnits()
                        .orElseGet(() -> joint.sharedNodeHeadWorldUnits().orElse(Double.NaN));
                report.append("JOINT 801@801->1951 status=")
                        .append(joint.status())
                        .append(" role=").append(joint.coupledLeg().nodeBoundary().role())
                        .append(" nodeArc=").append(joint.coupledLeg().nodeBoundary().arcLength())
                        .append(" confluenceFiniteArc=")
                        .append(joint.coupledLeg().finiteBoundary().arcLength())
                        .append(" cascadeNearArc=")
                        .append(joint.coupledLeg().nodeBoundary().role()
                                        == SkyIslandHydraulicTransitionBoundaryRole.OUTGOING
                                ? joint.cascade().transitionSite().upstreamBoundary().arcLength()
                                : joint.cascade().transitionSite().downstreamBoundary().arcLength())
                        .append(" nodeHead=")
                        .append(joint.sharedNodeHeadWorldUnits().orElse(Double.NaN))
                        .append(" cascadeNearHead=").append(nearHead)
                        .append(" cascadeRemoteHead=")
                        .append(joint.cascadeBoundaryHeadWorldUnits().orElse(Double.NaN))
                        .append(System.lineSeparator());
            }
        }
        List<SkyIslandOrdinarySpanOutcome> incidentSpans = spans.outcomes().stream()
                .filter(outcome -> incidentReachIds.contains(
                        outcome.span().parentReachStartCellIndex()
                                + "->"
                                + outcome.span().parentReachEndCellIndex()))
                .toList();
        assertTrue(incidentSpans.size() >= 3, "key 700 component must expose its ordinary spans");
        for (SkyIslandOrdinarySpanOutcome outcome : incidentSpans) {
            report.append("SPAN ")
                    .append(outcome.span().parentReachStartCellIndex())
                    .append("->")
                    .append(outcome.span().parentReachEndCellIndex())
                    .append(" stations=")
                    .append(outcome.span().parentStartStationFraction())
                    .append("..")
                    .append(outcome.span().parentEndStationFraction())
                    .append(" status=").append(outcome.status())
                    .append(" diagnostic=")
                    .append(outcome.diagnostic().orElse("none"))
                    .append(" upstreamBoundary=")
                    .append(outcome.span().upstreamBoundary().status())
                    .append(":")
                    .append(outcome.span().upstreamBoundary().state().role())
                    .append("@")
                    .append(outcome.span().upstreamBoundary().state().stationFraction())
                    .append("=")
                    .append(outcome.span().upstreamBoundary().fixedHeadWorldUnits().orElse(Double.NaN))
                    .append(" downstreamBoundary=")
                    .append(outcome.span().downstreamBoundary().status())
                    .append(":")
                    .append(outcome.span().downstreamBoundary().state().role())
                    .append("@")
                    .append(outcome.span().downstreamBoundary().state().stationFraction())
                    .append("=")
                    .append(outcome.span().downstreamBoundary().fixedHeadWorldUnits().orElse(Double.NaN))
                    .append(" selectedHeadGradePath=")
                    .append(gradeFeasibilityLocus(descriptor, outcome.span(), terrain, true))
                    .append(" freeTransitionHeadGradePath=")
                    .append(gradeFeasibilityLocus(descriptor, outcome.span(), terrain, false))
                    .append(System.lineSeparator());
        }

        Path evidence = Path.of(
                "build", "evidence", "hydrology-key700-d2-component-test", "key-700.txt");
        Files.createDirectories(evidence.getParent());
        Files.writeString(evidence, report);
        if (objectiveFailure != null) {
            System.out.println(report);
            assertTrue(false, objectiveFailure);
        }
    }

    @Test
    void skeletonIsDeterministicAcrossFixedCorpus() {
        for (long key : new long[] {77L, 118L, 241L, 287L, 512L, 632L, 811L}) {
            SkyIslandDescriptor descriptor = descriptor(key);
            assertEquals(
                    SkyIslandHydraulicGeometrySkeletonPlanner.plan(descriptor),
                    SkyIslandHydraulicGeometrySkeletonPlanner.plan(descriptor));
        }
    }

    @Test
    void legacyPlannerPreservesExactHeadIndependentGeometry() {
        for (long key : new long[] {118L, 287L, 512L}) {
            SkyIslandDescriptor descriptor = descriptor(key);
            SkyIslandHydraulicGeometrySkeletonPlan skeleton =
                    SkyIslandHydraulicGeometrySkeletonPlanner.plan(descriptor);
            SkyIslandHydraulicChannelNetworkPlan legacy =
                    SkyIslandHydraulicChannelNetworkPlanner.plan(descriptor);

            assertEquals(skeleton.reaches().size(), legacy.reaches().size());
            for (int r = 0; r < skeleton.reaches().size(); r++) {
                SkyIslandHydraulicReachSkeleton source = skeleton.reaches().get(r);
                SkyIslandHydraulicReachGeometry realized = legacy.reaches().get(r);
                assertEquals(source.geomorphicRoute(), realized.geomorphicRoute());
                assertEquals(source.centerline(), realized.centerline());
                assertEquals(source.pathLength(), realized.pathLength(), EPSILON);
                assertEquals(
                        source.maximumBankfullHalfWidth(),
                        realized.maximumBankfullHalfWidth(),
                        EPSILON);
                assertEquals(
                        source.maximumWaterDepthPotential(),
                        realized.maximumWaterDepthPotential(),
                        EPSILON);
                assertEquals(source.samples().size(), realized.samples().size());

                for (int i = 0; i < source.samples().size(); i++) {
                    SkyIslandHydraulicGeometrySkeletonSample a = source.samples().get(i);
                    SkyIslandHydraulicGeometrySample b = realized.samples().get(i);
                    assertEquals(a.position(), b.position());
                    assertEquals(a.stationFraction() * source.pathLength(), a.arcLength(), EPSILON);
                    assertEquals(a.stationFraction(), b.stationFraction(), EPSILON);
                    assertEquals(a.relativeDischarge(), b.relativeDischarge(), EPSILON);
                    assertEquals(a.bankfullHalfWidth(), b.bankfullHalfWidth(), EPSILON);
                    assertEquals(a.waterDepthPotential(), b.waterDepthPotential(), EPSILON);
                    assertEquals(a.terrainElevation(), b.terrainElevation(), EPSILON);
                }
            }
        }
    }

    @Test
    void key287D2SearchReportsAdmissibilityAndObjectiveProgress() throws Exception {
        SkyIslandDescriptor descriptor = descriptor(287L);
        SkyIslandGeomorphicChannelNetworkPlan network =
                SkyIslandGeomorphicChannelNetworkPlanner.plan(descriptor);
        SkyIslandGeomorphicReachRoute route = network.routes().stream()
                .filter(candidate -> candidate.semanticReach().startCellIndex() == 1090
                        && candidate.semanticReach().endCellIndex() == 1758)
                .findFirst()
                .orElseThrow();
        SkyIslandPreHydrologicTerrainField terrain =
                SkyIslandPreHydrologicTerrainField.create(descriptor);
        SkyIslandSemanticField interiority =
                SkyIslandSemanticFieldSet.create(descriptor).interiority();

        var result = SkyIslandHydraulicGeometrySkeletonPlanner.refineCenterline(
                descriptor, network, route, terrain, interiority);
        var d = result.diagnostics();
        SkyIslandSemanticChannelReach semantic = route.semanticReach();
        SkyIslandGeomorphicQualificationPolicy policy =
                SkyIslandGeomorphicQualificationPolicy.firstEvidenceBacked();
        int peakIndex = d.finalMaximumHeadEnvelopeGapIndex();
        double peakStation = d.finalMaximumHeadEnvelopeGapStation();
        SkyIslandLocalPosition peakPosition = result.centerline().points().get(peakIndex);
        SkyIslandLocalPosition before = result.centerline().points().get(peakIndex - 1);
        SkyIslandLocalPosition after = result.centerline().points().get(peakIndex + 1);
        double tangentX = after.x() - before.x();
        double tangentZ = after.z() - before.z();
        double tangentLength = Math.hypot(tangentX, tangentZ);
        double normalX = -tangentZ / tangentLength;
        double normalZ = tangentX / tangentLength;
        double startDischarge = Math.max(
                SkyIslandHydraulicGeometryCalibration.MINIMUM_DISCHARGE,
                semantic.profiles().getFirst().segment().relativeDischarge());
        double endDischarge = Math.max(
                startDischarge,
                semantic.profiles().getLast().segment().relativeDischarge());
        double discharge =
                SkyIslandHydraulicGeometrySkeletonPlanner.relativeDischargeAtStation(
                        semantic, peakStation);
        double peakHalfWidth =
                SkyIslandHydraulicGeometryCalibration.bankfullHalfWidth(
                        descriptor.nominalRadius(), discharge);
        double peakDepth =
                SkyIslandHydraulicGeometryCalibration.waterDepthPotential(discharge);
        SkyIslandChannelProfileKind peakKind =
                SkyIslandHydraulicHeadEnvelopePlanner.profileKind(
                        semantic.profiles(), peakStation);
        SkyIslandGeomorphicQualificationClass peakClass =
                peakKind == SkyIslandChannelProfileKind.ALLUVIAL
                        ? SkyIslandGeomorphicQualificationClass.ALLUVIAL
                        : SkyIslandGeomorphicQualificationClass.INCISED;
        var peakEnvelope =
                SkyIslandHydraulicHeadEnvelopePlanner.evaluateForKindWithDiagnostics(
                        descriptor,
                        peakKind,
                        peakPosition,
                        peakHalfWidth,
                        peakDepth,
                        Math.max(0.0, Math.min(1.0, terrain.sample(peakPosition))),
                        normalX,
                        normalZ,
                        terrain,
                        policy.limits(peakClass));
        String peakBounds =
                " plateauLower=" + peakEnvelope.activeLowerBound().constraint() + "@"
                        + peakEnvelope.activeLowerBound().head()
                        + " plateauUpper=" + peakEnvelope.activeUpperBound().constraint() + "@"
                        + peakEnvelope.activeUpperBound().head();
        List<SkyIslandLocalPosition> guidance = semantic.guidancePoints();
        double[] guidanceDistance = new double[guidance.size()];
        for (int i = 1; i < guidance.size(); i++) {
            SkyIslandLocalPosition a = guidance.get(i - 1);
            SkyIslandLocalPosition b = guidance.get(i);
            guidanceDistance[i] = guidanceDistance[i - 1]
                    + Math.hypot(b.x() - a.x(), b.z() - a.z());
        }
        double guidanceLength = guidanceDistance[guidanceDistance.length - 1];
        double profileDischargeSquaredError = 0.0;
        double profileDischargeWeight = 0.0;
        double profileDischargeMaximumError = 0.0;
        for (int i = 0; i < semantic.profiles().size(); i++) {
            double segmentLength = guidanceDistance[i + 1] - guidanceDistance[i];
            double segmentMidpointStation =
                    (guidanceDistance[i] + 0.5 * segmentLength) / guidanceLength;
            double semanticDischarge =
                    semantic.profiles().get(i).segment().relativeDischarge();
            double interpolatedDischarge =
                    startDischarge + (endDischarge - startDischarge) * segmentMidpointStation;
            double error = Math.abs(semanticDischarge - interpolatedDischarge);
            profileDischargeMaximumError = Math.max(profileDischargeMaximumError, error);
            profileDischargeSquaredError += segmentLength * error * error;
            profileDischargeWeight += segmentLength;
        }
        double profileDischargeWeightedRmsError =
                Math.sqrt(profileDischargeSquaredError / profileDischargeWeight);
        assertTrue(Double.isFinite(guidanceLength) && guidanceLength > 0.0);
        assertTrue(Double.isFinite(profileDischargeWeightedRmsError));

        String summary =
                "F3G_D2_SEARCH key=287 initialMaxGap=" + d.initialMaximumHeadEnvelopeGap()
                        + " initialMaxAt=" + d.initialMaximumHeadEnvelopeGapIndex() + "@"
                        + d.initialMaximumHeadEnvelopeGapStation()
                        + " finalMaxGap=" + d.finalMaximumHeadEnvelopeGap()
                        + " finalMaxAt=" + d.finalMaximumHeadEnvelopeGapIndex() + "@"
                        + d.finalMaximumHeadEnvelopeGapStation()
                        + " initialIntegratedGap=" + d.initialIntegratedSquaredHeadEnvelopeGap()
                        + " finalIntegratedGap=" + d.finalIntegratedSquaredHeadEnvelopeGap()
                        + " lateralProposals=" + d.lateralCandidateProposals()
                        + " admissible=" + d.lateralCandidateAdmissible()
                        + " corridorRejected=" + d.lateralCandidateCorridorRejected()
                        + " terrainRejected=" + d.lateralCandidateTerrainRejected()
                        + " interiorityRejected=" + d.lateralCandidateInteriorityRejected()
                        + " curvatureRejected=" + d.lateralCandidateCurvatureRejected()
                        + " gapImproving=" + d.lateralCandidateGapImproving()
                        + " selected=" + d.selectedLateralMoves()
                        + " curvatureBlocked=" + d.globalGapImprovementsBlockedByCurvature()
                        + " globalModeProposals=" + d.globalModeSearchProposals()
                        + " globalModeAdmissible=" + d.globalModeSearchAdmissible()
                        + " globalModeAcceptedMoves=" + d.globalModeSearchAcceptedMoves()
                        + " globalModeStages=" + d.globalModeSearchStages()
                        + " globalModeMaximumBudget=" + d.globalModeSearchMaximumBudget()
                        + " sweeps=" + d.relaxationSweeps()
                        + " profileDischargeMidpointMaxAbsError=" + profileDischargeMaximumError
                        + " profileDischargeMidpointWeightedRmsError=" + profileDischargeWeightedRmsError
                        + peakBounds
                        + System.lineSeparator();
        Path report = Path.of("build", "evidence", "hydrology-d2-search-test", "key-287.txt");
        Files.createDirectories(report.getParent());
        CorridorFeasibilityAudit corridorAudit = auditD2Corridor(
                descriptor,
                network,
                route,
                result.centerline().points(),
                terrain,
                interiority,
                semantic,
                policy,
                d.initialMaximumHeadEnvelopeGapIndex(),
                d.finalMaximumHeadEnvelopeGapIndex(),
                maximumWidthFor(descriptor, endDischarge));
        SmoothRouteAudit smoothRouteAudit = auditSmoothRoute(
                descriptor,
                network,
                route,
                result.centerline().points(),
                terrain,
                interiority,
                semantic,
                policy,
                d.initialMaximumHeadEnvelopeGapIndex(),
                d.finalMaximumHeadEnvelopeGapIndex(),
                maximumWidthFor(descriptor, endDischarge));
        Files.writeString(
                report, summary + corridorAudit.summary() + smoothRouteAudit.summary());
        assertEquals(8_450, corridorAudit.sampleCount());
        assertTrue(corridorAudit.admissibleSampleCount() > 0, corridorAudit.summary());
        assertTrue(Double.isFinite(corridorAudit.minimumAdmissibleGap()), corridorAudit.summary());
        assertTrue(smoothRouteAudit.candidateCount() > 0, smoothRouteAudit.summary());
        assertTrue(
                smoothRouteAudit.admissibleCandidateCount() > 0,
                smoothRouteAudit.summary());
        assertTrue(Double.isFinite(smoothRouteAudit.minimumIntegratedGap()), smoothRouteAudit.summary());
        assertTrue(d.lateralCandidateProposals() > 0);
        assertTrue(d.globalModeSearchProposals() > 0);
        assertTrue(d.globalModeSearchAdmissible() > 0);
        assertEquals(
                d.finalMaximumHeadEnvelopeGap(),
                peakEnvelope.positiveGap(),
                EPSILON,
                summary);
        assertTrue(
                d.finalMaximumHeadEnvelopeGap()
                        <= d.initialMaximumHeadEnvelopeGap() + EPSILON);
        assertTrue(
                d.finalIntegratedSquaredHeadEnvelopeGap()
                        < d.initialIntegratedSquaredHeadEnvelopeGap(),
                summary);
        assertTrue(d.finalMaximumHeadEnvelopeGap() >= 0.0);
    }

    private static SmoothRouteAudit auditSmoothRoute(
            SkyIslandDescriptor descriptor,
            SkyIslandGeomorphicChannelNetworkPlan network,
            SkyIslandGeomorphicReachRoute route,
            List<SkyIslandLocalPosition> basePoints,
            SkyIslandSemanticField terrain,
            SkyIslandSemanticField interiority,
            SkyIslandSemanticChannelReach semantic,
            SkyIslandGeomorphicQualificationPolicy policy,
            int initialPeakIndex,
            int finalPeakIndex,
            double minimumBendRadius) {
        double corridorHalfWidth =
                network.planningSpacing()
                        * SkyIslandGeomorphicChannelNetworkPlanner.ROUTE_CORRIDOR_SPACING_FRACTION;
        double[] arc = cumulativeArc(basePoints);
        double nominalStep = arc[arc.length - 1] / (basePoints.size() - 1.0);
        int[] supportSteps = {2, 4, 8, 12, 16, 24, 32, 48, 64, 96, 128};
        int[] peakIndices = initialPeakIndex == finalPeakIndex
                ? new int[] {finalPeakIndex}
                : new int[] {initialPeakIndex, finalPeakIndex};
        int amplitudeSteps = 64;
        int candidateCount = 0;
        int admissibleCandidateCount = 0;
        int feasibleRouteCount = 0;
        int corridorRejected = 0;
        int terrainRejected = 0;
        int interiorityRejected = 0;
        int curvatureRejected = 0;
        double minimumIntegratedGap = Double.POSITIVE_INFINITY;
        double bestMaximumGap = Double.POSITIVE_INFINITY;
        double bestAmplitude = Double.NaN;
        int bestSupport = -1;
        int bestPeak = -1;

        for (int peakIndex : peakIndices) {
            if (peakIndex <= 0 || peakIndex >= basePoints.size() - 1) {
                throw new IllegalStateException("smooth D2 peak must be an interior route point");
            }
            for (int support : supportSteps) {
                double supportLength = support * nominalStep;
                if (supportLength
                        >= 0.9 * Math.min(
                                arc[peakIndex], arc[arc.length - 1] - arc[peakIndex])) {
                    continue;
                }
                for (int amplitudeIndex = -amplitudeSteps;
                        amplitudeIndex <= amplitudeSteps;
                        amplitudeIndex++) {
                    double amplitude = corridorHalfWidth * amplitudeIndex / amplitudeSteps;
                    List<SkyIslandLocalPosition> candidate = smoothDisplacement(
                            basePoints,
                            arc,
                            peakIndex,
                            supportLength,
                            amplitude,
                            -1,
                            0.0,
                            0.0);
                    candidateCount++;
                    SmoothCandidateMetrics metrics = evaluateSmoothCandidate(
                            descriptor, route, candidate, terrain, interiority, semantic,
                            policy, minimumBendRadius, corridorHalfWidth);
                    corridorRejected += metrics.corridorAllowed() ? 0 : 1;
                    terrainRejected += metrics.terrainAllowed() ? 0 : 1;
                    interiorityRejected += metrics.interiorityAllowed() ? 0 : 1;
                    curvatureRejected += metrics.curvatureAllowed() ? 0 : 1;
                    if (!metrics.admissible()) {
                        continue;
                    }
                    admissibleCandidateCount++;
                    if (metrics.feasible()) {
                        feasibleRouteCount++;
                    }
                    if (metrics.integratedGap() < minimumIntegratedGap) {
                        minimumIntegratedGap = metrics.integratedGap();
                        bestMaximumGap = metrics.maximumGap();
                        bestAmplitude = amplitude;
                        bestSupport = support;
                        bestPeak = peakIndex;
                    }
                }
            }
        }

        int multiCandidateCount = 0;
        int multiAdmissibleCount = 0;
        int multiFeasibleCount = 0;
        int multiImprovingCount = 0;
        double multiBestIntegratedGap = Double.POSITIVE_INFINITY;
        double multiBestMaximumGap = Double.POSITIVE_INFINITY;
        String multiBestControls = "none";
        double baselineIntegratedGap = evaluateSmoothCandidate(
                descriptor, route, basePoints, terrain, interiority, semantic,
                policy, minimumBendRadius, corridorHalfWidth).integratedGap();
        int[] controlFractions = {-4, -2, 0, 2, 4};
        for (int peakIndex : peakIndices) {
            for (int support : new int[] {8, 12, 16, 24, 32, 48, 64}) {
                double supportLength = support * nominalStep;
                if (supportLength
                        >= 0.75 * Math.min(
                                arc[peakIndex], arc[arc.length - 1] - arc[peakIndex])) {
                    continue;
                }
                for (int direction : new int[] {-1, 1}) {
                    int secondPeak = nearestArcIndex(
                            arc, arc[peakIndex] + direction * 1.5 * supportLength);
                    if (secondPeak <= 0 || secondPeak >= basePoints.size() - 1
                            || secondPeak == peakIndex
                            || supportLength
                                    >= 0.75 * Math.min(
                                            arc[secondPeak],
                                            arc[arc.length - 1] - arc[secondPeak])) {
                        continue;
                    }
                    for (int firstFraction : controlFractions) {
                        for (int secondFraction : controlFractions) {
                            double firstAmplitude =
                                    corridorHalfWidth * firstFraction / 8.0;
                            double secondAmplitude =
                                    corridorHalfWidth * secondFraction / 8.0;
                            List<SkyIslandLocalPosition> candidate = smoothDisplacement(
                                    basePoints,
                                    arc,
                                    peakIndex,
                                    supportLength,
                                    firstAmplitude,
                                    secondPeak,
                                    supportLength,
                                    secondAmplitude);
                            multiCandidateCount++;
                            SmoothCandidateMetrics metrics = evaluateSmoothCandidate(
                                    descriptor, route, candidate, terrain, interiority, semantic,
                                    policy, minimumBendRadius, corridorHalfWidth);
                            if (!metrics.admissible()) {
                                continue;
                            }
                            multiAdmissibleCount++;
                            if (metrics.feasible()) {
                                multiFeasibleCount++;
                            }
                            if (metrics.integratedGap() < baselineIntegratedGap - EPSILON) {
                                multiImprovingCount++;
                            }
                            if (metrics.integratedGap() < multiBestIntegratedGap) {
                                multiBestIntegratedGap = metrics.integratedGap();
                                multiBestMaximumGap = metrics.maximumGap();
                                multiBestControls = String.format(
                                        Locale.ROOT,
                                        "%d:%d:%.6f|%d:%.6f",
                                        peakIndex,
                                        support,
                                        firstAmplitude,
                                        secondPeak,
                                        secondAmplitude);
                            }
                        }
                    }
                }
            }
        }
        String summary = String.format(
                Locale.ROOT,
                "F3G_D2_SMOOTH_ROUTE key=287 candidateCount=%d admissibleCandidates=%d "
                        + "feasibleRoutes=%d corridorRejected=%d terrainRejected=%d "
                        + "interiorityRejected=%d curvatureRejected=%d "
                        + "bestIntegratedGap=%.9f bestMaximumGap=%.9f "
                        + "bestAmplitude=%.9f bestSupportSteps=%d bestPeakIndex=%d%n",
                candidateCount,
                admissibleCandidateCount,
                feasibleRouteCount,
                corridorRejected,
                terrainRejected,
                interiorityRejected,
                curvatureRejected,
                minimumIntegratedGap,
                bestMaximumGap,
                bestAmplitude,
                bestSupport,
                bestPeak)
                + String.format(
                        Locale.ROOT,
                        "F3G_D2_MULTI_ROUTE key=287 candidateCount=%d admissibleCandidates=%d "
                                + "feasibleRoutes=%d improvingRoutes=%d "
                                + "bestIntegratedGap=%.9f bestMaximumGap=%.9f "
                                + "bestControls=%s baselineIntegratedGap=%.9f%n",
                        multiCandidateCount,
                        multiAdmissibleCount,
                        multiFeasibleCount,
                        multiImprovingCount,
                        multiBestIntegratedGap,
                        multiBestMaximumGap,
                        multiBestControls,
                        baselineIntegratedGap);
        return new SmoothRouteAudit(
                candidateCount, admissibleCandidateCount, feasibleRouteCount,
                minimumIntegratedGap, summary);
    }

    private static double[] cumulativeArc(List<SkyIslandLocalPosition> points) {
        double[] arc = new double[points.size()];
        for (int i = 1; i < points.size(); i++) {
            SkyIslandLocalPosition a = points.get(i - 1);
            SkyIslandLocalPosition b = points.get(i);
            arc[i] = arc[i - 1] + Math.hypot(b.x() - a.x(), b.z() - a.z());
        }
        return arc;
    }

    private static int nearestArcIndex(double[] arc, double target) {
        int bestIndex = 0;
        double bestDistance = Double.POSITIVE_INFINITY;
        for (int i = 0; i < arc.length; i++) {
            double distance = Math.abs(arc[i] - target);
            if (distance < bestDistance) {
                bestDistance = distance;
                bestIndex = i;
            }
        }
        return bestIndex;
    }

    private static List<SkyIslandLocalPosition> smoothDisplacement(
            List<SkyIslandLocalPosition> basePoints,
            double[] arc,
            int firstCenter,
            double firstSupportLength,
            double firstAmplitude,
            int secondCenter,
            double secondSupportLength,
            double secondAmplitude) {
        List<SkyIslandLocalPosition> candidate = new ArrayList<>(basePoints);
        for (int i = 1; i < basePoints.size() - 1; i++) {
            SkyIslandLocalPosition previous = basePoints.get(i - 1);
            SkyIslandLocalPosition next = basePoints.get(i + 1);
            double tangentX = next.x() - previous.x();
            double tangentZ = next.z() - previous.z();
            double tangentLength = Math.hypot(tangentX, tangentZ);
            if (!(tangentLength > 0.0)) {
                throw new IllegalStateException("smooth D2 route tangent must be non-zero");
            }
            double firstWeight = cosineBumpWeight(
                    Math.abs(arc[i] - arc[firstCenter]), firstSupportLength);
            double secondWeight = secondCenter < 0
                    ? 0.0
                    : cosineBumpWeight(
                            Math.abs(arc[i] - arc[secondCenter]), secondSupportLength);
            double amplitude =
                    firstAmplitude * firstWeight + secondAmplitude * secondWeight;
            candidate.set(
                    i,
                    new SkyIslandLocalPosition(
                            basePoints.get(i).x() - tangentZ / tangentLength * amplitude,
                            basePoints.get(i).z() + tangentX / tangentLength * amplitude));
        }
        return candidate;
    }

    private static double cosineBumpWeight(double distance, double supportLength) {
        return distance >= supportLength
                ? 0.0
                : 0.5 * (1.0 + Math.cos(Math.PI * distance / supportLength));
    }

    private static SmoothCandidateMetrics evaluateSmoothCandidate(
            SkyIslandDescriptor descriptor,
            SkyIslandGeomorphicReachRoute route,
            List<SkyIslandLocalPosition> candidate,
            SkyIslandSemanticField terrain,
            SkyIslandSemanticField interiority,
            SkyIslandSemanticChannelReach semantic,
            SkyIslandGeomorphicQualificationPolicy policy,
            double minimumBendRadius,
            double corridorHalfWidth) {
        double[] stations = normalizedStations(candidate);
        boolean corridorAllowed = true;
        boolean terrainAllowed = true;
        boolean interiorityAllowed = true;
        boolean curvatureAllowed = true;
        for (int i = 0; i < candidate.size(); i++) {
            SkyIslandLocalPosition point = candidate.get(i);
            if (distanceToPolyline(point, semantic.guidancePoints())
                    > corridorHalfWidth + EPSILON) {
                corridorAllowed = false;
            }
            SkyIslandLocalPosition seedProjection =
                    projectToPolyline(point, route.route().points());
            if (terrain.sample(point) - terrain.sample(seedProjection)
                    > SkyIslandSemanticCorridorCenterlinePlanner.MAXIMUM_TERRAIN_RISE_FROM_SEED
                            + EPSILON) {
                terrainAllowed = false;
            }
            if (interiority.sample(point)
                    < SkyIslandSemanticCorridorCenterlinePlanner.MINIMUM_INTERIORITY) {
                interiorityAllowed = false;
            }
            if (i > 0 && i < candidate.size() - 1
                    && localCurvature(
                                    candidate.get(i - 1),
                                    point,
                                    candidate.get(i + 1))
                                    * minimumBendRadius
                            > 1.0 + EPSILON) {
                curvatureAllowed = false;
            }
        }
        boolean admissible =
                corridorAllowed && terrainAllowed && interiorityAllowed && curvatureAllowed;
        if (!admissible) {
            return new SmoothCandidateMetrics(
                    corridorAllowed, terrainAllowed, interiorityAllowed, curvatureAllowed,
                    false, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY);
        }
        double candidateLength = 0.0;
        for (int i = 1; i < candidate.size(); i++) {
            SkyIslandLocalPosition a = candidate.get(i - 1);
            SkyIslandLocalPosition b = candidate.get(i);
            candidateLength += Math.hypot(b.x() - a.x(), b.z() - a.z());
        }
        double integratedGap = 0.0;
        double maximumGap = 0.0;
        boolean feasible = true;
        for (int i = 0; i < candidate.size(); i++) {
            double station = stations[i];
            SkyIslandChannelProfileKind kind =
                    SkyIslandHydraulicHeadEnvelopePlanner.profileKind(
                            semantic.profiles(), station);
            if (kind == SkyIslandChannelProfileKind.CASCADE) {
                continue;
            }
            SkyIslandLocalPosition point = candidate.get(i);
            SkyIslandLocalPosition previous = candidate.get(Math.max(0, i - 1));
            SkyIslandLocalPosition next =
                    candidate.get(Math.min(candidate.size() - 1, i + 1));
            double tangentX = next.x() - previous.x();
            double tangentZ = next.z() - previous.z();
            double tangentLength = Math.hypot(tangentX, tangentZ);
            if (!(tangentLength > 0.0)) {
                return new SmoothCandidateMetrics(
                        corridorAllowed, terrainAllowed, interiorityAllowed, false,
                        false, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY);
            }
            double discharge =
                    SkyIslandHydraulicGeometrySkeletonPlanner.relativeDischargeAtStation(
                            semantic, station);
            double halfWidth =
                    SkyIslandHydraulicGeometryCalibration.bankfullHalfWidth(
                            descriptor.nominalRadius(), discharge);
            double depth =
                    SkyIslandHydraulicGeometryCalibration.waterDepthPotential(discharge);
            SkyIslandGeomorphicQualificationClass qualificationClass =
                    kind == SkyIslandChannelProfileKind.ALLUVIAL
                            ? SkyIslandGeomorphicQualificationClass.ALLUVIAL
                            : SkyIslandGeomorphicQualificationClass.INCISED;
            var evaluation =
                    SkyIslandHydraulicHeadEnvelopePlanner.evaluateForKindWithDiagnostics(
                            descriptor,
                            kind,
                            point,
                            halfWidth,
                            depth,
                            Math.max(0.0, Math.min(1.0, terrain.sample(point))),
                            -tangentZ / tangentLength,
                            tangentX / tangentLength,
                            terrain,
                            policy.limits(qualificationClass));
            double gap = evaluation.positiveGap();
            maximumGap = Math.max(maximumGap, gap);
            double segmentWeight = i == 0
                    ? 0.5 * stations[1] * candidateLength
                    : i == candidate.size() - 1
                            ? 0.5 * (stations[i] - stations[i - 1]) * candidateLength
                            : 0.5 * (stations[i + 1] - stations[i - 1]) * candidateLength;
            integratedGap += segmentWeight * gap * gap;
            feasible &= evaluation.envelope().feasible(EPSILON);
        }
        return new SmoothCandidateMetrics(
                corridorAllowed, terrainAllowed, interiorityAllowed, curvatureAllowed,
                feasible, integratedGap, maximumGap);
    }

    private record SmoothCandidateMetrics(
            boolean corridorAllowed,
            boolean terrainAllowed,
            boolean interiorityAllowed,
            boolean curvatureAllowed,
            boolean feasible,
            double integratedGap,
            double maximumGap) {
        private boolean admissible() {
            return corridorAllowed && terrainAllowed
                    && interiorityAllowed && curvatureAllowed;
        }
    }

    private record SmoothRouteAudit(
            int candidateCount,
            int admissibleCandidateCount,
            int feasibleRouteCount,
            double minimumIntegratedGap,
            String summary) {}

    private static double maximumWidthFor(SkyIslandDescriptor descriptor, double discharge) {
        return 2.0 * SkyIslandHydraulicGeometryCalibration.bankfullHalfWidth(
                descriptor.nominalRadius(), discharge);
    }

    private static CorridorFeasibilityAudit auditD2Corridor(
            SkyIslandDescriptor descriptor,
            SkyIslandGeomorphicChannelNetworkPlan network,
            SkyIslandGeomorphicReachRoute route,
            List<SkyIslandLocalPosition> centerline,
            SkyIslandSemanticField terrain,
            SkyIslandSemanticField interiority,
            SkyIslandSemanticChannelReach semantic,
            SkyIslandGeomorphicQualificationPolicy policy,
            int initialPeakIndex,
            int finalPeakIndex,
            double minimumBendRadius) throws Exception {
        final int samplesPerAxis = 65;
        final int samplesPerStation = samplesPerAxis * samplesPerAxis;
        double corridorHalfWidth =
                network.planningSpacing()
                        * SkyIslandGeomorphicChannelNetworkPlanner.ROUTE_CORRIDOR_SPACING_FRACTION;
        StringBuilder rows = new StringBuilder(
                "criticalIndex,alongOffsetWorld,lateralOffsetWorld,stationFraction,profileKind,"
                        + "lowerHead,upperHead,gap,lowerConstraint,lowerBound,"
                        + "upperConstraint,upperBound,insideSemanticCorridor,"
                        + "terrainAdmissible,interiorityAdmissible,curvatureAdmissible,"
                        + "admissible\\n");
        double minimumGapAny = Double.POSITIVE_INFINITY;
        double minimumGapInCorridor = Double.POSITIVE_INFINITY;
        double minimumGapBeforeCurvature = Double.POSITIVE_INFINITY;
        double minimumAdmissibleGap = Double.POSITIVE_INFINITY;
        int ordinaryProfileSamples = 0;
        int cascadeSamples = 0;
        int semanticCorridorSamples = 0;
        int terrainAdmissibleSamples = 0;
        int interiorityAdmissibleSamples = 0;
        int curvatureAdmissibleSamples = 0;
        int baseAdmissibleSamples = 0;
        int admissibleSamples = 0;
        int feasibleAnySamples = 0;
        int feasibleAdmissibleSamples = 0;
        int sampleCount = 0;
        double[] widthScales = {0.5, 0.75, 1.0, 1.25, 1.5, 2.0};
        double[] minimumWidthGapInCorridor = new double[widthScales.length];
        double[] minimumWidthGapBeforeCurvature = new double[widthScales.length];
        int[] feasibleWidthSamplesInCorridor = new int[widthScales.length];
        int[] feasibleWidthSamplesBeforeCurvature = new int[widthScales.length];
        java.util.Arrays.fill(minimumWidthGapInCorridor, Double.POSITIVE_INFINITY);
        java.util.Arrays.fill(minimumWidthGapBeforeCurvature, Double.POSITIVE_INFINITY);
        int[] criticalIndices = {initialPeakIndex, finalPeakIndex};
        for (int criticalIndex : criticalIndices) {
            if (criticalIndex <= 0 || criticalIndex >= centerline.size() - 1) {
                throw new IllegalStateException("D2 critical station must be interior");
            }
            SkyIslandLocalPosition previous = centerline.get(criticalIndex - 1);
            SkyIslandLocalPosition current = centerline.get(criticalIndex);
            SkyIslandLocalPosition next = centerline.get(criticalIndex + 1);
            double tangentX = next.x() - previous.x();
            double tangentZ = next.z() - previous.z();
            double tangentLength = Math.hypot(tangentX, tangentZ);
            if (!(tangentLength > 0.0)) {
                throw new IllegalStateException("D2 critical station tangent must be non-zero");
            }
            tangentX /= tangentLength;
            tangentZ /= tangentLength;
            double normalX = -tangentZ;
            double normalZ = tangentX;
            for (int alongSample = 0; alongSample < samplesPerAxis; alongSample++) {
                double alongOffset = -corridorHalfWidth
                        + 2.0 * corridorHalfWidth * alongSample / (samplesPerAxis - 1.0);
                for (int lateralSample = 0;
                        lateralSample < samplesPerAxis;
                        lateralSample++) {
                    double lateralOffset = -corridorHalfWidth
                            + 2.0 * corridorHalfWidth * lateralSample / (samplesPerAxis - 1.0);
                    SkyIslandLocalPosition candidate =
                            new SkyIslandLocalPosition(
                                    current.x()
                                            + tangentX * alongOffset
                                            + normalX * lateralOffset,
                                    current.z()
                                            + tangentZ * alongOffset
                                            + normalZ * lateralOffset);
                    List<SkyIslandLocalPosition> candidatePoints = new ArrayList<>(centerline);
                    candidatePoints.set(criticalIndex, candidate);
                    double station = normalizedStations(candidatePoints)[criticalIndex];
                    double discharge =
                            SkyIslandHydraulicGeometrySkeletonPlanner.relativeDischargeAtStation(
                                    semantic, station);
                    SkyIslandChannelProfileKind kind =
                            SkyIslandHydraulicHeadEnvelopePlanner.profileKind(
                                    semantic.profiles(), station);
                    if (kind == SkyIslandChannelProfileKind.CASCADE) {
                        cascadeSamples++;
                        rows.append(String.format(
                                Locale.ROOT,
                                "%d,%.9f,%.9f,%.12f,CASCADE,,,,,,,false,false,false,false,false%n",
                                criticalIndex,
                                alongOffset,
                                lateralOffset,
                                station));
                        sampleCount++;
                        continue;
                    }
                    ordinaryProfileSamples++;
                    double halfWidth =
                            SkyIslandHydraulicGeometryCalibration.bankfullHalfWidth(
                                    descriptor.nominalRadius(), discharge);
                    double depth =
                            SkyIslandHydraulicGeometryCalibration.waterDepthPotential(discharge);
                    SkyIslandGeomorphicQualificationClass qualificationClass =
                            kind == SkyIslandChannelProfileKind.ALLUVIAL
                                    ? SkyIslandGeomorphicQualificationClass.ALLUVIAL
                                    : SkyIslandGeomorphicQualificationClass.INCISED;
                    var evaluation =
                            SkyIslandHydraulicHeadEnvelopePlanner
                                    .evaluateForKindWithDiagnostics(
                                            descriptor,
                                            kind,
                                            candidate,
                                            halfWidth,
                                            depth,
                                            Math.max(0.0, Math.min(1.0, terrain.sample(candidate))),
                                            normalX,
                                            normalZ,
                                            terrain,
                                            policy.limits(qualificationClass));
                    double gap = evaluation.positiveGap();
                    minimumGapAny = Math.min(minimumGapAny, gap);
                    boolean feasible = evaluation.envelope().feasible(EPSILON);
                    if (feasible) {
                        feasibleAnySamples++;
                    }
                    boolean insideCorridor =
                            distanceToPolyline(candidate, semantic.guidancePoints())
                                    <= corridorHalfWidth + EPSILON;
                    if (insideCorridor) {
                        semanticCorridorSamples++;
                        minimumGapInCorridor = Math.min(minimumGapInCorridor, gap);
                    }
                    SkyIslandLocalPosition seedProjection =
                            projectToPolyline(candidate, route.route().points());
                    boolean terrainAllowed =
                            terrain.sample(candidate) - terrain.sample(seedProjection)
                                    <= SkyIslandSemanticCorridorCenterlinePlanner
                                                    .MAXIMUM_TERRAIN_RISE_FROM_SEED
                                            + EPSILON;
                    if (terrainAllowed) {
                        terrainAdmissibleSamples++;
                    }
                    boolean interiorityAllowed =
                            interiority.sample(candidate)
                                    >= SkyIslandSemanticCorridorCenterlinePlanner
                                            .MINIMUM_INTERIORITY;
                    if (interiorityAllowed) {
                        interiorityAdmissibleSamples++;
                    }
                    boolean curvatureAllowed =
                            maximumCurvatureAround(candidatePoints, criticalIndex)
                                            * minimumBendRadius
                                    <= 1.0 + EPSILON;
                    if (curvatureAllowed) {
                        curvatureAdmissibleSamples++;
                    }
                    boolean baseAdmissible =
                            insideCorridor && terrainAllowed && interiorityAllowed;
                    if (baseAdmissible) {
                        baseAdmissibleSamples++;
                        minimumGapBeforeCurvature =
                                Math.min(minimumGapBeforeCurvature, gap);
                    }
                    if (insideCorridor) {
                        for (int widthIndex = 0; widthIndex < widthScales.length; widthIndex++) {
                            var widthEvaluation =
                                    SkyIslandHydraulicHeadEnvelopePlanner
                                            .evaluateForKindWithDiagnostics(
                                                    descriptor,
                                                    kind,
                                                    candidate,
                                                    halfWidth * widthScales[widthIndex],
                                                    depth,
                                                    Math.max(
                                                            0.0,
                                                            Math.min(1.0, terrain.sample(candidate))),
                                                    normalX,
                                                    normalZ,
                                                    terrain,
                                                    policy.limits(qualificationClass));
                            double widthGap = widthEvaluation.positiveGap();
                            minimumWidthGapInCorridor[widthIndex] =
                                    Math.min(minimumWidthGapInCorridor[widthIndex], widthGap);
                            if (widthEvaluation.envelope().feasible(EPSILON)) {
                                feasibleWidthSamplesInCorridor[widthIndex]++;
                            }
                            if (baseAdmissible) {
                                minimumWidthGapBeforeCurvature[widthIndex] =
                                        Math.min(
                                                minimumWidthGapBeforeCurvature[widthIndex],
                                                widthGap);
                                if (widthEvaluation.envelope().feasible(EPSILON)) {
                                    feasibleWidthSamplesBeforeCurvature[widthIndex]++;
                                }
                            }
                        }
                    }
                    boolean admissible = baseAdmissible && curvatureAllowed;
                    if (admissible) {
                        admissibleSamples++;
                        minimumAdmissibleGap = Math.min(minimumAdmissibleGap, gap);
                        if (feasible) {
                            feasibleAdmissibleSamples++;
                        }
                    }
                    rows.append(String.format(
                            Locale.ROOT,
                            "%d,%.9f,%.9f,%.12f,%s,%.9f,%.9f,%.9f,%s,%.9f,%s,%.9f,"
                                    + "%s,%s,%s,%s,%s%n",
                            criticalIndex,
                            alongOffset,
                            lateralOffset,
                            station,
                            kind,
                            evaluation.envelope().lowerHead(),
                            evaluation.envelope().upperHead(),
                            gap,
                            evaluation.activeLowerBound().constraint(),
                            evaluation.activeLowerBound().head(),
                            evaluation.activeUpperBound().constraint(),
                            evaluation.activeUpperBound().head(),
                            insideCorridor,
                            terrainAllowed,
                            interiorityAllowed,
                            curvatureAllowed,
                            admissible));
                    sampleCount++;
                }
            }
        }
        Path evidenceDirectory =
                Path.of("build", "evidence", "hydrology-d2-search-test");
        Files.createDirectories(evidenceDirectory);
        Files.writeString(evidenceDirectory.resolve("key-287-corridor.csv"), rows);
        String summary = String.format(
                Locale.ROOT,
                "F3G_D2_CORRIDOR key=287 stations=%d samplesPerStation=%d totalSamples=%d "
                        + "ordinaryProfileSamples=%d cascadeSamples=%d semanticCorridorSamples=%d terrainAdmissibleSamples=%d "
                        + "interiorityAdmissibleSamples=%d curvatureAdmissibleSamples=%d "
                        + "baseAdmissibleSamples=%d admissibleSamples=%d "
                        + "feasibleAnySamples=%d feasibleAdmissibleSamples=%d "
                        + "minimumGapAny=%.9f minimumGapInCorridor=%.9f "
                        + "minimumGapBeforeCurvature=%.9f minimumAdmissibleGap=%.9f%n",
                criticalIndices.length,
                samplesPerStation,
                sampleCount,
                ordinaryProfileSamples,
                cascadeSamples,
                semanticCorridorSamples,
                terrainAdmissibleSamples,
                interiorityAdmissibleSamples,
                curvatureAdmissibleSamples,
                baseAdmissibleSamples,
                admissibleSamples,
                feasibleAnySamples,
                feasibleAdmissibleSamples,
                minimumGapAny,
                minimumGapInCorridor,
                minimumGapBeforeCurvature,
                minimumAdmissibleGap);
        StringBuilder widthSensitivity = new StringBuilder(summary);
        for (int widthIndex = 0; widthIndex < widthScales.length; widthIndex++) {
            widthSensitivity.append(String.format(
                    Locale.ROOT,
                    "F3G_D2_WIDTH_SENSITIVITY key=287 widthScale=%.2f "
                            + "minimumGapInCorridor=%.9f feasibleCorridorSamples=%d "
                            + "minimumGapBeforeCurvature=%.9f feasiblePreCurvatureSamples=%d%n",
                    widthScales[widthIndex],
                    minimumWidthGapInCorridor[widthIndex],
                    feasibleWidthSamplesInCorridor[widthIndex],
                    minimumWidthGapBeforeCurvature[widthIndex],
                    feasibleWidthSamplesBeforeCurvature[widthIndex]));
        }
        Files.writeString(
                evidenceDirectory.resolve("key-287-corridor-summary.txt"),
                widthSensitivity.toString());
        return new CorridorFeasibilityAudit(
                sampleCount,
                admissibleSamples,
                minimumAdmissibleGap,
                widthSensitivity.toString());
    }

    private static double[] normalizedStations(List<SkyIslandLocalPosition> points) {
        double[] cumulative = new double[points.size()];
        for (int i = 1; i < points.size(); i++) {
            SkyIslandLocalPosition a = points.get(i - 1);
            SkyIslandLocalPosition b = points.get(i);
            cumulative[i] = cumulative[i - 1]
                    + Math.hypot(b.x() - a.x(), b.z() - a.z());
        }
        double length = cumulative[cumulative.length - 1];
        if (!(length > 0.0)) {
            throw new IllegalArgumentException("candidate centerline must have positive length");
        }
        for (int i = 0; i < cumulative.length; i++) {
            cumulative[i] /= length;
        }
        return cumulative;
    }

    private static double distanceToPolyline(
            SkyIslandLocalPosition point, List<SkyIslandLocalPosition> polyline) {
        double minimum = Double.POSITIVE_INFINITY;
        for (int i = 1; i < polyline.size(); i++) {
            SkyIslandLocalPosition a = polyline.get(i - 1);
            SkyIslandLocalPosition b = polyline.get(i);
            double dx = b.x() - a.x();
            double dz = b.z() - a.z();
            double denominator = dx * dx + dz * dz;
            double fraction = denominator <= EPSILON
                    ? 0.0
                    : Math.max(0.0, Math.min(1.0,
                            ((point.x() - a.x()) * dx + (point.z() - a.z()) * dz)
                                    / denominator));
            double x = a.x() + fraction * dx;
            double z = a.z() + fraction * dz;
            minimum = Math.min(minimum, Math.hypot(point.x() - x, point.z() - z));
        }
        return minimum;
    }

    private static SkyIslandLocalPosition projectToPolyline(
            SkyIslandLocalPosition point, List<SkyIslandLocalPosition> polyline) {
        SkyIslandLocalPosition best = polyline.getFirst();
        double minimum = Double.POSITIVE_INFINITY;
        for (int i = 1; i < polyline.size(); i++) {
            SkyIslandLocalPosition a = polyline.get(i - 1);
            SkyIslandLocalPosition b = polyline.get(i);
            double dx = b.x() - a.x();
            double dz = b.z() - a.z();
            double denominator = dx * dx + dz * dz;
            double fraction = denominator <= EPSILON
                    ? 0.0
                    : Math.max(0.0, Math.min(1.0,
                            ((point.x() - a.x()) * dx + (point.z() - a.z()) * dz)
                                    / denominator));
            SkyIslandLocalPosition candidate = new SkyIslandLocalPosition(
                    a.x() + fraction * dx, a.z() + fraction * dz);
            double distance = Math.hypot(point.x() - candidate.x(), point.z() - candidate.z());
            if (distance < minimum) {
                minimum = distance;
                best = candidate;
            }
        }
        return best;
    }

    private static double maximumCurvatureAround(
            List<SkyIslandLocalPosition> points, int changedIndex) {
        double maximum = 0.0;
        for (int index = Math.max(1, changedIndex - 1);
                index <= Math.min(points.size() - 2, changedIndex + 1);
                index++) {
            maximum = Math.max(
                    maximum,
                    localCurvature(
                            points.get(index - 1), points.get(index), points.get(index + 1)));
        }
        return maximum;
    }

    private static double localCurvature(
            SkyIslandLocalPosition previous,
            SkyIslandLocalPosition point,
            SkyIslandLocalPosition next) {
        double ax = point.x() - previous.x();
        double az = point.z() - previous.z();
        double bx = next.x() - point.x();
        double bz = next.z() - point.z();
        double aLength = Math.hypot(ax, az);
        double bLength = Math.hypot(bx, bz);
        if (aLength <= EPSILON || bLength <= EPSILON) {
            return Double.POSITIVE_INFINITY;
        }
        double cosine = Math.max(-1.0, Math.min(1.0, (ax * bx + az * bz) / (aLength * bLength)));
        return Math.acos(cosine) / (0.5 * (aLength + bLength));
    }

    private record CorridorFeasibilityAudit(
            int sampleCount,
            int admissibleSampleCount,
            double minimumAdmissibleGap,
            String summary) {}

    @Test
    void semanticDischargeInterpolationPreservesPhysicalSegmentKnots() {
        for (long key : new long[] {118L, 287L, 512L}) {
            SkyIslandDescriptor descriptor = descriptor(key);
            SkyIslandSemanticChannelReachPlan semantics =
                    SkyIslandSemanticChannelReachPlanner.plan(descriptor);
            for (SkyIslandSemanticChannelReach semantic : semantics.reaches()) {
                List<SkyIslandLocalPosition> guidance = semantic.guidancePoints();
                double[] cumulative = new double[guidance.size()];
                for (int i = 1; i < guidance.size(); i++) {
                    SkyIslandLocalPosition a = guidance.get(i - 1);
                    SkyIslandLocalPosition b = guidance.get(i);
                    cumulative[i] = cumulative[i - 1]
                            + Math.hypot(b.x() - a.x(), b.z() - a.z());
                }
                double length = cumulative[cumulative.length - 1];
                double previousDischarge = -1.0;
                for (int i = 0; i < semantic.profiles().size(); i++) {
                    double discharge = Math.max(
                            SkyIslandHydraulicGeometryCalibration.MINIMUM_DISCHARGE,
                            semantic.profiles().get(i).segment().relativeDischarge());
                    assertTrue(discharge + EPSILON >= previousDischarge);
                    double station = cumulative[i] / length;
                    assertEquals(
                            discharge,
                            SkyIslandHydraulicGeometrySkeletonPlanner
                                    .relativeDischargeAtStation(semantic, station),
                            EPSILON);
                    double segmentMidpointStation =
                            (cumulative[i] + 0.5 * (cumulative[i + 1] - cumulative[i]))
                                    / length;
                    if (i + 1 < semantic.profiles().size()) {
                        double nextDischarge = Math.max(
                                SkyIslandHydraulicGeometryCalibration.MINIMUM_DISCHARGE,
                                semantic.profiles().get(i + 1).segment().relativeDischarge());
                        assertEquals(
                                0.5 * (discharge + nextDischarge),
                                SkyIslandHydraulicGeometrySkeletonPlanner
                                        .relativeDischargeAtStation(semantic, segmentMidpointStation),
                                EPSILON);
                    }
                    previousDischarge = discharge;
                }
            }
        }
    }

    @Test
    void calibrationDelegatesRemainBackwardCompatible() {
        double radius = 100.0;
        for (int i = 0; i <= 100; i++) {
            double q = (double) i / 100.0;
            assertEquals(
                    SkyIslandHydraulicGeometryCalibration.bankfullHalfWidth(radius, q),
                    SkyIslandHydraulicChannelNetworkPlanner.bankfullHalfWidth(radius, q),
                    EPSILON);
            assertEquals(
                    SkyIslandHydraulicGeometryCalibration.waterDepthPotential(q),
                    SkyIslandHydraulicChannelNetworkPlanner.waterDepthPotential(q),
                    EPSILON);
        }
    }

    @Test
    void skeletonDischargeWidthAndDepthAreMonotoneWithinReach() {
        SkyIslandHydraulicGeometrySkeletonPlan plan =
                SkyIslandHydraulicGeometrySkeletonPlanner.plan(descriptor(287L));
        for (SkyIslandHydraulicReachSkeleton reach : plan.reaches()) {
            double previousArcLength = -1.0;
            double previousDischarge = -1.0;
            double previousWidth = -1.0;
            double previousDepth = -1.0;
            for (SkyIslandHydraulicGeometrySkeletonSample sample : reach.samples()) {
                assertTrue(sample.arcLength() + EPSILON >= previousArcLength);
                assertTrue(sample.relativeDischarge() + EPSILON >= previousDischarge);
                assertTrue(sample.bankfullHalfWidth() + EPSILON >= previousWidth);
                assertTrue(sample.waterDepthPotential() + EPSILON >= previousDepth);
                previousArcLength = sample.arcLength();
                previousDischarge = sample.relativeDischarge();
                previousWidth = sample.bankfullHalfWidth();
                previousDepth = sample.waterDepthPotential();
            }
        }
    }

    private static String gradeFeasibilityLocus(
            SkyIslandDescriptor descriptor,
            SkyIslandOrdinaryHydraulicSpan span,
            SkyIslandSemanticField terrain,
            boolean preserveSelectedTransitionHeads) {
        SkyIslandGeomorphicProfileLimits limits =
                SkyIslandGeomorphicQualificationPolicy.firstEvidenceBacked()
                        .limits(span.qualificationClass());
        List<SkyIslandHydraulicGeometrySkeletonSample> samples = span.samples();
        double reachableLower = Double.NaN;
        double reachableUpper = Double.NaN;
        for (int i = 0; i < samples.size(); i++) {
            SkyIslandHydraulicGeometrySkeletonSample sample = samples.get(i);
            SkyIslandLocalPosition before = samples.get(Math.max(0, i - 1)).position();
            SkyIslandLocalPosition after =
                    samples.get(Math.min(samples.size() - 1, i + 1)).position();
            double tangentX = after.x() - before.x();
            double tangentZ = after.z() - before.z();
            double tangentLength = Math.hypot(tangentX, tangentZ);
            if (!(tangentLength > 0.0)) {
                throw new IllegalStateException("key-700 span tangent must be non-zero");
            }
            SkyIslandHydraulicHeadEnvelope envelope =
                    SkyIslandHydraulicHeadEnvelopePlanner.evaluateForKind(
                            descriptor,
                            span.sampleProfileKinds().get(i),
                            sample.position(),
                            sample.bankfullHalfWidth(),
                            sample.waterDepthPotential(),
                            sample.terrainElevation(),
                            -tangentZ / tangentLength,
                            tangentX / tangentLength,
                            terrain,
                            limits);
            double localLower = envelope.lowerHead();
            double localUpper = envelope.upperHead();
            if (preserveSelectedTransitionHeads && i == 0) {
                var fixed = span.upstreamBoundary().fixedHeadWorldUnits();
                if (fixed.isPresent()) {
                    localLower = Math.max(localLower, fixed.orElseThrow());
                    localUpper = Math.min(localUpper, fixed.orElseThrow());
                }
            }
            if (preserveSelectedTransitionHeads && i == samples.size() - 1) {
                var fixed = span.downstreamBoundary().fixedHeadWorldUnits();
                if (fixed.isPresent()) {
                    localLower = Math.max(localLower, fixed.orElseThrow());
                    localUpper = Math.min(localUpper, fixed.orElseThrow());
                }
            }
            if (localLower > localUpper + EPSILON) {
                return "LOCAL_EMPTY@index=" + i
                        + ",station=" + sample.stationFraction()
                        + ",gap=" + (localLower - localUpper);
            }
            if (i == 0) {
                reachableLower = localLower;
                reachableUpper = localUpper;
                continue;
            }
            double ds = sample.arcLength() - samples.get(i - 1).arcLength();
            double maxDrop = limits.maximumLongitudinalGrade() * ds;
            double nextLower = Math.max(localLower, reachableLower - maxDrop);
            double nextUpper = Math.min(localUpper, reachableUpper);
            if (nextLower > nextUpper + EPSILON) {
                return "GRADE_EMPTY@index=" + i
                        + ",station=" + sample.stationFraction()
                        + ",previousReachable=" + reachableLower + ".." + reachableUpper
                        + ",local=" + localLower + ".." + localUpper
                        + ",maxDrop=" + maxDrop
                        + ",conflict=" + (nextLower - nextUpper);
            }
            reachableLower = nextLower;
            reachableUpper = nextUpper;
        }
        return "FEASIBLE_FINAL=" + reachableLower + ".." + reachableUpper;
    }

    private static SkyIslandDescriptor descriptor(long key) {
        return SkyIslandDescriptorGenerator.derive(
                SkyIslandIdentity.of(SEED, 8L, 81L, key));
    }
}
