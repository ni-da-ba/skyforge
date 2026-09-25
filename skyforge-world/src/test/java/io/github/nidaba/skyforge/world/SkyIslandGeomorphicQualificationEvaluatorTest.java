package io.github.nidaba.skyforge.world;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandIdentity;
import java.util.List;
import org.junit.jupiter.api.Test;

class SkyIslandGeomorphicQualificationEvaluatorTest {
    private static final long SEED = 0x534B59464F524745L;

    @Test
    void explicitPermissiveEnvelopeAcceptsMeasuredReachWithoutMutation() {
        SkyIslandDescriptor descriptor = descriptor(287L);
        List<SkyIslandGeomorphicReachDiagnostics> diagnostics =
                SkyIslandGeomorphicReachDiagnosticsPlanner.measure(descriptor);
        assertFalse(diagnostics.isEmpty());

        SkyIslandGeomorphicProfileLimits permissive =
                new SkyIslandGeomorphicProfileLimits(
                        1.0,
                        100.0,
                        1000.0,
                        100.0,
                        100.0,
                        1.0,
                        100.0,
                        1.0,
                        100.0);
        SkyIslandGeomorphicQualificationPolicy policy =
                new SkyIslandGeomorphicQualificationPolicy(
                        permissive, permissive, permissive, permissive);

        for (SkyIslandGeomorphicReachDiagnostics diagnostic : diagnostics) {
            SkyIslandGeomorphicReachQualification result =
                    SkyIslandGeomorphicQualificationEvaluator.evaluate(diagnostic, policy);
            assertTrue(result.accepted(), result.violations().toString());
        }
    }

    @Test
    void explicitLoweringEnvelopeRejectsObjectiveQuarryPressure() {
        SkyIslandDescriptor descriptor = descriptor(287L);
        SkyIslandGeomorphicReachDiagnostics diagnostic =
                SkyIslandGeomorphicReachDiagnosticsPlanner.measure(descriptor).stream()
                        .filter(d -> d.maximumCenterlineLoweringPotential() > 0.10)
                        .findFirst()
                        .orElseThrow();

        SkyIslandGeomorphicProfileLimits tightLowering =
                new SkyIslandGeomorphicProfileLimits(
                        0.10,
                        100.0,
                        1000.0,
                        100.0,
                        100.0,
                        1.0,
                        100.0,
                        1.0,
                        100.0);
        SkyIslandGeomorphicQualificationPolicy policy =
                new SkyIslandGeomorphicQualificationPolicy(
                        tightLowering, tightLowering, tightLowering, tightLowering);

        SkyIslandGeomorphicReachQualification result =
                SkyIslandGeomorphicQualificationEvaluator.evaluate(diagnostic, policy);
        assertFalse(result.accepted());
        assertTrue(result.violations().contains(
                SkyIslandGeomorphicQualificationViolation.CENTERLINE_LOWERING));
    }


    @Test
    void semanticCorridorEvidenceFreezesCurvatureWidthSafetyAtOne() {
        SkyIslandGeomorphicQualificationPolicy policy =
                SkyIslandGeomorphicQualificationPolicy.firstEvidenceBacked();

        assertTrue(policy.alluvial().maximumCurvatureWidthRatio() == 1.0);
        assertTrue(policy.incised().maximumCurvatureWidthRatio() == 1.0);
        assertTrue(policy.cascade().maximumCurvatureWidthRatio() == 1.0);
        assertTrue(policy.mixed().maximumCurvatureWidthRatio() == 1.0);
    }

    @Test
    void firstEvidenceBackedEnvelopeSeparatesLowSurgeryControlsFromPrimaryFailure() {
        SkyIslandGeomorphicQualificationPolicy policy =
                SkyIslandGeomorphicQualificationPolicy.firstEvidenceBacked();

        assertAllAccepted(descriptor(6L, 61L, 118L), policy);
        assertAllAccepted(descriptor(6L, 61L, 512L), policy);

        List<SkyIslandGeomorphicReachQualification> primary =
                SkyIslandGeomorphicReachDiagnosticsPlanner.measure(descriptor(8L, 81L, 287L)).stream()
                        .map(diagnostic ->
                                SkyIslandGeomorphicQualificationEvaluator.evaluate(diagnostic, policy))
                        .toList();
        assertTrue(primary.stream().anyMatch(result -> !result.accepted()));
        assertTrue(primary.stream()
                .flatMap(result -> result.violations().stream())
                .anyMatch(violation ->
                        violation == SkyIslandGeomorphicQualificationViolation.CENTERLINE_LOWERING
                                || violation == SkyIslandGeomorphicQualificationViolation.EXCAVATION_BURDEN
                                || violation == SkyIslandGeomorphicQualificationViolation.LATERAL_RECOVERY_GRADE));
    }

    private static void assertAllAccepted(
            SkyIslandDescriptor descriptor,
            SkyIslandGeomorphicQualificationPolicy policy) {
        List<SkyIslandGeomorphicReachQualification> results =
                SkyIslandGeomorphicReachDiagnosticsPlanner.measure(descriptor).stream()
                        .map(diagnostic ->
                                SkyIslandGeomorphicQualificationEvaluator.evaluate(diagnostic, policy))
                        .toList();
        assertFalse(results.isEmpty());
        assertTrue(results.stream().allMatch(SkyIslandGeomorphicReachQualification::accepted),
                () -> results.stream()
                        .filter(result -> !result.accepted())
                        .map(result -> result.violations().toString())
                        .toList()
                        .toString());
    }

    private static SkyIslandDescriptor descriptor(long key) {
        return descriptor(8L, 81L, key);
    }

    private static SkyIslandDescriptor descriptor(long province, long cluster, long key) {
        return SkyIslandDescriptorGenerator.derive(
                SkyIslandIdentity.of(SEED, province, cluster, key));
    }
}
