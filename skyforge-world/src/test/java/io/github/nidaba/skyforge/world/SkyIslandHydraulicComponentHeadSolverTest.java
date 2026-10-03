package io.github.nidaba.skyforge.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandIdentity;
import org.junit.jupiter.api.Test;

class SkyIslandHydraulicComponentHeadSolverTest {
    private static final long SEED = 0x534B59464F524745L;

    @Test
    void naturalKey700ComponentHeadProblemIsDeterministicAndFailsClosedOnExcludedSpans() {
        SkyIslandDescriptor descriptor = SkyIslandDescriptorGenerator.derive(
                SkyIslandIdentity.of(SEED, 8L, 81L, 700L));
        SkyIslandOrdinarySpanPlan ordinaryPlan =
                SkyIslandOrdinarySpanPlanner.plan(descriptor);
        SkyIslandSemanticField terrain =
                SkyIslandPreHydrologicTerrainField.create(descriptor);
        SkyIslandGeomorphicQualificationPolicy policy =
                SkyIslandGeomorphicQualificationPolicy.firstEvidenceBacked();

        SkyIslandHydraulicComponentHeadSolver.Outcome first =
                SkyIslandHydraulicComponentHeadSolver.solve(
                        descriptor, ordinaryPlan, terrain, policy);
        SkyIslandHydraulicComponentHeadSolver.Outcome second =
                SkyIslandHydraulicComponentHeadSolver.solve(
                        descriptor, ordinaryPlan, terrain, policy);

        assertEquals(first.status(), second.status());
        assertEquals(first.heads(), second.heads());
        assertEquals(first.includedSpans(), second.includedSpans());
        assertEquals(first.excludedSpans(), second.excludedSpans());
        assertEquals(first.transitionBlockers(), second.transitionBlockers());
        assertEquals(first.diagnostic(), second.diagnostic());
        assertFalse(first.excludedSpans().isEmpty(),
                "the natural key-700 component retains its known pointwise D2 failures");
        assertFalse(first.complete(),
                "a partial component-head outcome must never be treated as F3I/F3E admission");
        String detail = "status=" + first.status()
                + ", qp=" + first.solve().map(SkyIslandHydraulicQpResult::status)
                + ", diagnostic=" + first.diagnostic()
                + ", exclusions=" + first.excludedSpans()
                + ", transitionBlockers=" + first.transitionBlockers();
        assertNotNull(first.diagnostic(), detail);
        assertEquals(
                SkyIslandHydraulicQpStatus.INFEASIBLE,
                first.status(),
                "joint hard D2/grade/CASCADE constraints remain physically infeasible: " + detail);
        if (first.solve().isPresent()) {
            assertEquals(
                    first.status(),
                    first.solve().orElseThrow().status(),
                    "component outcome must preserve the underlying QP status: " + detail);
            if (first.status() == SkyIslandHydraulicQpStatus.INFEASIBLE) {
                assertTrue(
                        first.diagnostic().contains("negativeCycleGapWorld="),
                        "natural key-700 rejection must expose a quantitative infeasibility witness: "
                                + detail);
            }
        }
    }
}
