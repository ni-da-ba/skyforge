package io.github.nidaba.skyforge.reference.acceptance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nidaba.skyforge.recipes.skyisland.group.SkyIslandGroupPlan;
import io.github.nidaba.skyforge.recipes.skyisland.group.SkyIslandGroupPlanner;
import io.github.nidaba.skyforge.recipes.skyisland.group.SkyIslandGroupRequest;
import io.github.nidaba.skyforge.recipes.skyisland.group.SkyIslandMorphologySpecCompiler;
import io.github.nidaba.skyforge.reference.evidence.SkyIslandGroupEvidence;
import io.github.nidaba.skyforge.reference.evidence.SkyIslandGroupEvidenceGenerator;
import io.github.nidaba.skyforge.reference.volume.SkyIslandGroupReferenceCorpus;
import java.util.stream.LongStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/** Group-scale acceptance for mixed built-in, blended, and external provider islands. */
final class SkyIslandGroupRealizationAcceptanceTest {
    private static final double RESERVATION_TOLERANCE =
            Math.sqrt(2.0) * SkyIslandGroupEvidenceGenerator.HORIZONTAL_SPACING;

    @ParameterizedTest(name = "{0}")
    @MethodSource("groups")
    @Execution(ExecutionMode.CONCURRENT)
    void everyMixedGroupRealizesAsExactlyOneSeparatedComponentPerPlannedIsland(Member member) {
        SkyIslandGroupPlanner planner = new SkyIslandGroupPlanner();
        SkyIslandGroupPlan plan = planner.plan(member.request());
        var registry = SkyIslandGroupReferenceCorpus.registry();
        var compiled = new SkyIslandMorphologySpecCompiler().compile(plan, registry);
        SkyIslandGroupEvidence evidence = new SkyIslandGroupEvidenceGenerator().generate(plan, compiled);
        var metrics = evidence.metrics();

        assertEquals(plan.memberCount(), metrics.memberCount(), member.id());
        assertEquals(plan.memberCount(), metrics.connectedComponents(), member.id());
        assertEquals(0, metrics.overlappingSolidSamples(), member.id());
        assertEquals(0, metrics.faceContacts(), member.id());
        assertTrue(metrics.minimumReservedGap() >= SkyIslandGroupReferenceCorpus.MINIMUM_GAP, member.id());
        for (int ordinal = 0; ordinal < plan.memberCount(); ordinal++) {
            assertTrue(
                    metrics.memberSolidSampleCounts().get(ordinal) > 0,
                    member.id() + " member " + ordinal + " disappeared");
        }
        assertEveryRealizedHorizontalSampleFitsItsReservation(evidence, member.id());

        // Repeat the full realization to verify planner, compilation, and union sampling identity.
        SkyIslandGroupPlan repeatedPlan = planner.plan(member.request());
        var repeatedCompiled = new SkyIslandMorphologySpecCompiler().compile(repeatedPlan, registry);
        SkyIslandGroupEvidence repeated = new SkyIslandGroupEvidenceGenerator().generate(
                repeatedPlan, repeatedCompiled);
        assertEquals(evidence.occupancy().sha256(), repeated.occupancy().sha256(), member.id());
    }

    private static void assertEveryRealizedHorizontalSampleFitsItsReservation(
            SkyIslandGroupEvidence evidence, String label) {
        var grid = evidence.occupancy().specification();
        int[] owners = evidence.ownerByHorizontalSample();
        for (int z = 0; z < grid.zSamples(); z++) {
            for (int x = 0; x < grid.xSamples(); x++) {
                int owner = owners[z * grid.xSamples() + x];
                if (owner < 0) {
                    continue;
                }
                var member = evidence.plan().members().get(owner);
                double distance = Math.hypot(
                        grid.xAt(x) - member.descriptor().centerX(),
                        grid.zAt(z) - member.descriptor().centerZ());
                assertTrue(
                        distance <= member.reservedHorizontalRadius() + RESERVATION_TOLERANCE,
                        () -> label + " member " + owner + " exceeded reservation at x="
                                + grid.xAt(x) + ", z=" + grid.zAt(z) + ", distance=" + distance);
            }
        }
    }

    private static Stream<Member> groups() {
        return LongStream.of(SkyIslandGroupReferenceCorpus.ACCEPTANCE_SEEDS)
                .boxed()
                .flatMap(seed -> Stream.of(
                        new Member("chain-seed-" + seed, SkyIslandGroupReferenceCorpus.chain(seed)),
                        new Member("cluster-seed-" + seed, SkyIslandGroupReferenceCorpus.cluster(seed))));
    }

    private record Member(String id, SkyIslandGroupRequest request) {
        @Override
        public String toString() {
            return id;
        }
    }
}
