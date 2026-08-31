package io.github.nidaba.skyforge.reference.acceptance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nidaba.skyforge.recipes.skyisland.archipelago.SkyIslandArchipelagoPlan;
import io.github.nidaba.skyforge.recipes.skyisland.archipelago.SkyIslandArchipelagoPlanner;
import io.github.nidaba.skyforge.recipes.skyisland.archipelago.SkyIslandArchipelagoRequest;
import io.github.nidaba.skyforge.reference.evidence.SkyIslandArchipelagoEvidence;
import io.github.nidaba.skyforge.reference.evidence.SkyIslandArchipelagoEvidenceGenerator;
import io.github.nidaba.skyforge.reference.volume.SkyIslandArchipelagoReferenceCorpus;
import java.util.stream.LongStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/** Regional realization acceptance for hierarchical hub and arc archipelagos. */
final class SkyIslandArchipelagoRealizationAcceptanceTest {

    @ParameterizedTest(name = "{0}")
    @MethodSource("archipelagos")
    @Execution(ExecutionMode.CONCURRENT)
    void hierarchyRealizesWithoutFlatteningOverlapOrDisappearance(Member member) {
        SkyIslandArchipelagoPlanner planner = new SkyIslandArchipelagoPlanner();
        SkyIslandArchipelagoPlan plan = planner.plan(member.request());
        SkyIslandArchipelagoEvidence evidence = new SkyIslandArchipelagoEvidenceGenerator().generate(
                plan, SkyIslandArchipelagoReferenceCorpus.registry());
        var metrics = evidence.metrics();

        assertEquals(plan.groupCount(), metrics.groupCount(), member.id());
        assertEquals(plan.totalMemberCount(), metrics.islandCount(), member.id());
        assertEquals(plan.totalMemberCount(), metrics.connectedComponents(), member.id());
        assertEquals(0, metrics.overlappingSolidSamples(), member.id());
        assertEquals(0, metrics.crossGroupOverlappingSolidSamples(), member.id());
        assertEquals(0, metrics.faceContacts(), member.id());
        assertTrue(metrics.minimumObservedGroupGap() >= plan.minimumGroupGap(), member.id());
        for (int group = 0; group < plan.groupCount(); group++) {
            assertTrue(
                    metrics.groupSolidSampleCounts().get(group) > 0,
                    member.id() + " group " + group + " disappeared");
        }
        for (int island = 0; island < plan.totalMemberCount(); island++) {
            assertTrue(
                    metrics.islandSolidSampleCounts().get(island) > 0,
                    member.id() + " island " + island + " disappeared");
        }

        // Repeat the entire hierarchy: placement, child planning, compilation, and regional sampling.
        SkyIslandArchipelagoPlan repeatedPlan = planner.plan(member.request());
        SkyIslandArchipelagoEvidence repeated = new SkyIslandArchipelagoEvidenceGenerator().generate(
                repeatedPlan, SkyIslandArchipelagoReferenceCorpus.registry());
        assertEquals(plan, repeatedPlan, member.id());
        assertEquals(evidence.occupancy().sha256(), repeated.occupancy().sha256(), member.id());
    }

    private static Stream<Member> archipelagos() {
        return LongStream.of(SkyIslandArchipelagoReferenceCorpus.ACCEPTANCE_SEEDS)
                .boxed()
                .flatMap(seed -> Stream.of(
                        new Member("hub-seed-" + seed, SkyIslandArchipelagoReferenceCorpus.hub(seed)),
                        new Member("arc-seed-" + seed, SkyIslandArchipelagoReferenceCorpus.arc(seed))));
    }

    private record Member(String id, SkyIslandArchipelagoRequest request) {
        @Override
        public String toString() {
            return id;
        }
    }
}
