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
                        + " sweeps=" + d.relaxationSweeps()
                        + " profileDischargeMidpointMaxAbsError=" + profileDischargeMaximumError
                        + " profileDischargeMidpointWeightedRmsError=" + profileDischargeWeightedRmsError
                        + peakBounds
                        + System.lineSeparator();
        Path report = Path.of("build", "evidence", "hydrology-d2-search-test", "key-287.txt");
        Files.createDirectories(report.getParent());
        Files.writeString(report, summary);
        CorridorFeasibilityAudit corridorAudit = auditD2LateralCorridor(
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
                maximumWidthFor( descriptor, endDischarge));
        assertEquals(514, corridorAudit.sampleCount());
        assertTrue(corridorAudit.admissibleSampleCount() > 0, corridorAudit.summary());
        assertTrue(Double.isFinite(corridorAudit.minimumAdmissibleGap()), corridorAudit.summary());
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

    private static double maximumWidthFor(SkyIslandDescriptor descriptor, double discharge) {
        return 2.0 * SkyIslandHydraulicGeometryCalibration.bankfullHalfWidth(
                descriptor.nominalRadius(), discharge);
    }

    private static CorridorFeasibilityAudit auditD2LateralCorridor(
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
        double corridorHalfWidth =
                network.planningSpacing()
                        * SkyIslandGeomorphicChannelNetworkPlanner.ROUTE_CORRIDOR_SPACING_FRACTION;
        StringBuilder rows = new StringBuilder(
                "criticalIndex,offsetWorld,stationFraction,lowerHead,upperHead,gap,"
                        + "lowerConstraint,lowerBound,upperConstraint,upperBound,"
                        + "insideSemanticCorridor,terrainAdmissible,interiorityAdmissible,"
                        + "curvatureAdmissible,admissible\\n");
        double minimumGap = Double.POSITIVE_INFINITY;
        double minimumAdmissibleGap = Double.POSITIVE_INFINITY;
        double minimumAdmissibleOffset = Double.NaN;
        int admissibleSamples = 0;
        int feasibleAdmissibleSamples = 0;
        int sampleCount = 0;
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
            double normalX = -tangentZ / tangentLength;
            double normalZ = tangentX / tangentLength;
            for (int sample = 0; sample <= 256; sample++) {
                double offset = -corridorHalfWidth
                        + 2.0 * corridorHalfWidth * sample / 256.0;
                SkyIslandLocalPosition candidate =
                        new SkyIslandLocalPosition(
                                current.x() + normalX * offset,
                                current.z() + normalZ * offset);
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
                    throw new IllegalStateException(
                            "D2 lateral corridor audit must remain on an ordinary profile");
                }
                double halfWidth = SkyIslandHydraulicGeometryCalibration.bankfullHalfWidth(
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
                                candidate,
                                halfWidth,
                                depth,
                                Math.max(0.0, Math.min(1.0, terrain.sample(candidate))),
                                normalX,
                                normalZ,
                                terrain,
                                policy.limits(qualificationClass));
                double gap = evaluation.positiveGap();
                minimumGap = Math.min(minimumGap, gap);
                boolean insideCorridor =
                        distanceToPolyline(candidate, semantic.guidancePoints())
                                <= corridorHalfWidth + EPSILON;
                SkyIslandLocalPosition seedProjection =
                        projectToPolyline(candidate, route.route().points());
                boolean terrainAllowed =
                        terrain.sample(candidate) - terrain.sample(seedProjection)
                                <= SkyIslandSemanticCorridorCenterlinePlanner
                                                .MAXIMUM_TERRAIN_RISE_FROM_SEED
                                        + EPSILON;
                boolean interiorityAllowed =
                        interiority.sample(candidate)
                                >= SkyIslandSemanticCorridorCenterlinePlanner.MINIMUM_INTERIORITY;
                boolean curvatureAllowed =
                        maximumCurvatureAround(candidatePoints, criticalIndex)
                                        * minimumBendRadius
                                <= 1.0 + EPSILON;
                boolean admissible = insideCorridor
                        && terrainAllowed
                        && interiorityAllowed
                        && curvatureAllowed;
                if (admissible) {
                    admissibleSamples++;
                    if (gap < minimumAdmissibleGap) {
                        minimumAdmissibleGap = gap;
                        minimumAdmissibleOffset = offset;
                    }
                    if (evaluation.envelope().feasible(EPSILON)) {
                        feasibleAdmissibleSamples++;
                    }
                }
                rows.append(String.format(
                        Locale.ROOT,
                        "%d,%.9f,%.12f,%.9f,%.9f,%.9f,%s,%.9f,%s,%.9f,"
                                + "%s,%s,%s,%s,%s%n",
                        criticalIndex,
                        offset,
                        station,
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
        Path evidenceDirectory =
                Path.of("build", "evidence", "hydrology-d2-search-test");
        Files.createDirectories(evidenceDirectory);
        Files.writeString(evidenceDirectory.resolve("key-287-corridor.csv"), rows);
        String summary = String.format(
                Locale.ROOT,
                "F3G_D2_CORRIDOR key=287 stations=%d samplesPerStation=257 "
                        + "totalSamples=%d admissibleSamples=%d feasibleAdmissibleSamples=%d "
                        + "minimumGapAny=%.9f minimumGapAdmissible=%.9f "
                        + "minimumAdmissibleOffset=%.9f%n",
                criticalIndices.length,
                sampleCount,
                admissibleSamples,
                feasibleAdmissibleSamples,
                minimumGap,
                minimumAdmissibleGap,
                minimumAdmissibleOffset);
        Files.writeString(evidenceDirectory.resolve("key-287-corridor-summary.txt"), summary);
        return new CorridorFeasibilityAudit(
                sampleCount,
                admissibleSamples,
                minimumAdmissibleGap,
                summary);
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

    private static SkyIslandDescriptor descriptor(long key) {
        return SkyIslandDescriptorGenerator.derive(
                SkyIslandIdentity.of(SEED, 8L, 81L, key));
    }
}
