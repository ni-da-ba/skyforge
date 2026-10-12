package io.github.nidaba.skyforge.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandIdentity;
import org.junit.jupiter.api.Test;

class SkyIslandHydraulicCandidateBedPlannerTest {
    private static final long SEED = 0x534B59464F524745L;
    private static final double EPSILON = 1.0e-10;

    @Test
    void key77CandidateBedsAreDeterministicAndBoundedBeforeAnyHydraulicSolve() {
        SkyIslandDescriptor descriptor = descriptor(77L);
        SkyIslandHydraulicGeometrySkeletonPlan skeleton =
                SkyIslandHydraulicGeometrySkeletonPlanner.plan(descriptor);
        SkyIslandGeomorphicQualificationPolicy policy =
                SkyIslandGeomorphicQualificationPolicy.firstEvidenceBacked();
        int candidates = 0;

        for (SkyIslandHydraulicReachSkeleton reach : skeleton.reaches()) {
            var first = SkyIslandHydraulicCandidateBedPlanner.plan(descriptor, skeleton, reach, policy);
            var second = SkyIslandHydraulicCandidateBedPlanner.plan(descriptor, skeleton, reach, policy);
            assertEquals(first, second);
            if (first.isEmpty()) {
                continue;
            }
            candidates++;
            SkyIslandHydraulicCandidateBed bed = first.orElseThrow();
            assertEquals(reach.pathLength(), bed.pathLengthWorldUnits(), EPSILON);
            assertEquals(reach.samples().size(), bed.stations().size());
            for (int i = 0; i < bed.stations().size(); i++) {
                SkyIslandHydraulicCandidateBed.Station station = bed.stations().get(i);
                SkyIslandHydraulicGeometrySkeletonSample source = reach.samples().get(i);
                assertEquals(source.position(), station.position());
                assertEquals(source.arcLength(), station.stationWorldUnits(), EPSILON);
                assertEquals(source.bankfullHalfWidth(), station.bankfullHalfWidthWorldUnits(), EPSILON);
                assertEquals(
                        source.waterDepthPotential() * descriptor.reliefBudget(),
                        station.targetFlowDepthWorldUnits(),
                        EPSILON);
                assertTrue(station.candidateBedElevationWorldUnits()
                        < station.originalTerrainElevationWorldUnits());
                assertTrue(station.originalTerrainElevationWorldUnits()
                                - station.candidateBedElevationWorldUnits()
                        <= policy.limits(reach.geomorphicRoute().semanticReach())
                                .maximumCenterlineLoweringPotential()
                                * descriptor.reliefBudget()
                                + EPSILON);
                assertEquals(5, station.crossSection().size());
                assertTrue(station.crossSection().stream()
                        .allMatch(point -> point.candidateElevationWorldUnits()
                                <= point.originalElevationWorldUnits() + EPSILON));
                assertTrue(station.crossSection().stream()
                        .anyMatch(point -> point.lateralOffsetWorldUnits() == 0.0
                                && Math.abs(point.candidateElevationWorldUnits()
                                        - station.candidateBedElevationWorldUnits()) <= EPSILON));
            }
        }
        assertTrue(candidates > 0, "the accepted ordinary key-77 control must produce bed candidates");
    }

    @Test
    void key700ConfluenceAndCascadeGeometryRemainTransitionOwned() {
        SkyIslandDescriptor descriptor = descriptor(700L);
        SkyIslandHydraulicGeometrySkeletonPlan skeleton =
                SkyIslandHydraulicGeometrySkeletonPlanner.plan(descriptor);
        SkyIslandGeomorphicQualificationPolicy policy =
                SkyIslandGeomorphicQualificationPolicy.firstEvidenceBacked();

        for (SkyIslandHydraulicReachSkeleton reach : skeleton.reaches()) {
            SkyIslandSemanticChannelReach semantic = reach.geomorphicRoute().semanticReach();
            if (semantic.startCellIndex() == 801 && semantic.endCellIndex() == 1951) {
                assertTrue(semantic.profiles().stream()
                        .anyMatch(profile -> profile.kind() == SkyIslandChannelProfileKind.CASCADE));
                assertFalse(SkyIslandHydraulicCandidateBedPlanner.plan(
                        descriptor, skeleton, reach, policy).isPresent());
            }
            if (semantic.endCellIndex() == 801) {
                assertFalse(SkyIslandHydraulicCandidateBedPlanner.plan(
                        descriptor, skeleton, reach, policy).isPresent());
            }
        }
    }

    private static SkyIslandDescriptor descriptor(long key) {
        return SkyIslandDescriptorGenerator.derive(
                SkyIslandIdentity.of(SEED, 8L, 81L, key));
    }
}
