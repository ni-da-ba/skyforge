package io.github.nidaba.skyforge.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;

class SkyIslandHydraulicHeadComponentProblemTest {
    @Test
    void repeatedBoundaryKeyBecomesOneVariableWithIntersectedEnvelopeAndSharedConstraints() {
        SkyIslandHydraulicHeadComponentProblem.Outcome outcome =
                SkyIslandHydraulicHeadComponentProblem.builder()
                        .addHead("node:801", 8.0, 1.0, 0.0, 10.0)
                        .addHead("node:801", 4.0, 3.0, 2.0, 9.0)
                        .addHead("upstream:660-801", 9.0, 1.0, 0.0, 10.0)
                        .addHead("downstream:801-1951", 1.0, 1.0, 0.0, 10.0)
                        .addDifference("incoming-grade", "upstream:660-801", "node:801", 0.0, 2.0)
                        .addDifference("outgoing-grade", "node:801", "downstream:801-1951", 0.0, 2.0)
                        .solve();

        assertEquals(SkyIslandHydraulicQpStatus.SOLVED, outcome.status());
        assertEquals(Map.of(
                "node:801", outcome.heads().get("node:801"),
                "upstream:660-801", outcome.heads().get("upstream:660-801"),
                "downstream:801-1951", outcome.heads().get("downstream:801-1951")),
                outcome.heads());
        assertEquals(3, outcome.heads().size());
        assertTrue(outcome.heads().get("node:801") >= 2.0);
        assertTrue(outcome.heads().get("node:801") <= 9.0);
        assertTrue(outcome.heads().get("upstream:660-801")
                >= outcome.heads().get("node:801") - 1.0e-8);
        assertTrue(outcome.heads().get("upstream:660-801")
                <= outcome.heads().get("node:801") + 2.0 + 1.0e-8);
        assertTrue(outcome.heads().get("node:801")
                >= outcome.heads().get("downstream:801-1951") - 1.0e-8);
        assertTrue(outcome.heads().get("node:801")
                <= outcome.heads().get("downstream:801-1951") + 2.0 + 1.0e-8);
        assertTrue(outcome.solve().orElseThrow().status() == SkyIslandHydraulicQpStatus.SOLVED);
        assertFalse(outcome.diagnostic().isPresent());
    }

    @Test
    void disjointSharedBoundaryEnvelopesFailClosedWithoutCallingTheQp() {
        SkyIslandHydraulicHeadComponentProblem.Outcome outcome =
                SkyIslandHydraulicHeadComponentProblem.builder()
                        .addHead("shared:801", 2.0, 1.0, 0.0, 1.0)
                        .addHead("shared:801", 3.0, 1.0, 2.0, 4.0)
                        .solve();

        assertEquals(SkyIslandHydraulicQpStatus.INFEASIBLE, outcome.status());
        assertTrue(outcome.solve().isEmpty());
        assertTrue(outcome.heads().isEmpty());
        assertTrue(outcome.diagnostic().orElseThrow().contains("infeasibilityGapWorld=1.0"));
    }

    @Test
    void sharedObservationOrderingDoesNotChangeTheComponentSolution() {
        var first = SkyIslandHydraulicHeadComponentProblem.builder()
                .addHead("shared", 8.0, 1.0, 0.0, 10.0)
                .addHead("shared", 4.0, 3.0, 2.0, 9.0)
                .addHead("downstream", 1.0, 1.0, 0.0, 10.0)
                .addDifference("grade", "shared", "downstream", 0.0, 4.0)
                .solve();
        var second = SkyIslandHydraulicHeadComponentProblem.builder()
                .addHead("downstream", 1.0, 1.0, 0.0, 10.0)
                .addHead("shared", 4.0, 3.0, 2.0, 9.0)
                .addHead("shared", 8.0, 1.0, 0.0, 10.0)
                .addDifference("grade", "shared", "downstream", 0.0, 4.0)
                .solve();

        assertEquals(SkyIslandHydraulicQpStatus.SOLVED, first.status());
        assertEquals(first.heads(), second.heads());
        assertEquals(first.solve().orElseThrow().objective(), second.solve().orElseThrow().objective());
    }

    @Test
    void differenceConstraintMustReferenceRegisteredVariables() {
        var builder = SkyIslandHydraulicHeadComponentProblem.builder()
                .addHead("left", 1.0, 1.0, 0.0, 2.0)
                .addDifference("missing", "left", "right", 0.0, 1.0);

        assertThrows(IllegalStateException.class, builder::solve);
    }
}
