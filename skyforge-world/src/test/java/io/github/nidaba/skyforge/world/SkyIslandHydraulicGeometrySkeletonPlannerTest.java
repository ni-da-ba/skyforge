package io.github.nidaba.skyforge.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandIdentity;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class SkyIslandHydraulicGeometrySkeletonPlannerTest {
    private static final long SEED = 0x534B59464F524745L;
    private static final double EPSILON = 1.0e-12;

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
                startDischarge + (endDischarge - startDischarge) * peakStation;
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
                        + " sweeps=" + d.relaxationSweeps()
                        + " blockProposals=" + d.blockMoveProposals()
                        + " blockFieldAdmissible=" + d.blockMoveFieldAdmissible()
                        + " blockCurvatureAdmissible=" + d.blockMoveCurvatureAdmissible()
                        + " blockObjectiveImproving=" + d.blockMoveObjectiveImproving()
                        + " selectedBlockMoves=" + d.selectedBlockMoves()
                        + " blockBestMaxGap=" + d.minimumBlockCandidateMaximumGap()
                        + " blockBestIntegratedGap="
                        + d.minimumBlockCandidateIntegratedSquaredGap()
                        + " restartSweeps=" + d.restartSweeps()
                        + " profileDischargeMidpointMaxAbsError=" + profileDischargeMaximumError
                        + " profileDischargeMidpointWeightedRmsError=" + profileDischargeWeightedRmsError
                        + peakBounds
                        + System.lineSeparator();
        Path report = Path.of("build", "evidence", "hydrology-d2-search-test", "key-287.txt");
        Files.createDirectories(report.getParent());
        Files.writeString(report, summary);
        assertTrue(d.lateralCandidateProposals() > 0);
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

    private static SkyIslandDescriptor descriptor(long key) {
        return SkyIslandDescriptorGenerator.derive(
                SkyIslandIdentity.of(SEED, 8L, 81L, key));
    }
}
