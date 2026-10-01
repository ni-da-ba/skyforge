package io.github.nidaba.skyforge.world.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandIdentity;
import io.github.nidaba.skyforge.world.SkyIslandDescriptorGenerator;
import io.github.nidaba.skyforge.world.SkyIslandPetroleumSystemOpportunityCell;
import io.github.nidaba.skyforge.world.SkyIslandPetroleumSystemOpportunityProfile;
import io.github.nidaba.skyforge.world.SkyIslandPetroleumSystemOpportunityProfiler;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

final class SkyIslandPetroleumLocalSourcePolicyTest {
    private static final long WORLD = 0x434f4e54454e5436L;

    @Test
    void eligibleCellsAreExactlyNonzeroAuth0098CellsInSourceOrder() {
        SkyIslandPetroleumSystemOpportunityProfile profile = profile(260001L);
        SkyIslandPetroleumLocalSourcePolicy.Plan plan =
                new SkyIslandPetroleumLocalSourcePolicy().plan(profile);

        List<SkyIslandPetroleumSystemOpportunityCell> expected = profile.cells().stream()
                .filter(cell -> cell.systemOpportunity() > 0.0)
                .toList();

        assertFalse(expected.isEmpty());
        assertEquals(expected, plan.eligibleCells());
        assertTrue(plan.eligibleCells().stream()
                .allMatch(SkyIslandPetroleumLocalSourcePolicy::locallyEligible));
        assertTrue(profile.cells().stream()
                .filter(cell -> cell.systemOpportunity() == 0.0)
                .noneMatch(plan.eligibleCells()::contains));
    }

    @Test
    void eligibleColumnsContainOnlyNonzeroCellsAndPreserveFirstCanonicalOccurrenceOrder() {
        SkyIslandPetroleumLocalSourcePolicy.Plan plan =
                new SkyIslandPetroleumLocalSourcePolicy().plan(profile(260002L));
        assertFalse(plan.eligibleColumns().isEmpty());

        int previousFirstOrdinal = -1;
        for (SkyIslandPetroleumLocalSourcePolicy.SourceColumn column :
                plan.eligibleColumns()) {
            assertFalse(column.eligibleCells().isEmpty());
            assertTrue(column.eligibleCells().stream()
                    .allMatch(SkyIslandPetroleumLocalSourcePolicy::locallyEligible));
            assertTrue(column.eligibleCells().stream()
                    .allMatch(cell -> cell.sourceCell().xIndex() == column.xIndex()
                            && cell.sourceCell().zIndex() == column.zIndex()));

            int firstOrdinal = plan.eligibleCells().indexOf(column.eligibleCells().getFirst());
            assertTrue(firstOrdinal > previousFirstOrdinal);
            previousFirstOrdinal = firstOrdinal;
        }
    }

    @Test
    void repeatedPlanningIsDeterministicAndExposesNoSelectedSource() {
        SkyIslandPetroleumSystemOpportunityProfile profile = profile(260003L);
        SkyIslandPetroleumLocalSourcePolicy policy = new SkyIslandPetroleumLocalSourcePolicy();

        SkyIslandPetroleumLocalSourcePolicy.Plan first = policy.plan(profile);
        SkyIslandPetroleumLocalSourcePolicy.Plan second = policy.plan(profile);

        assertEquals(first, second);
        assertTrue(Arrays.stream(SkyIslandPetroleumLocalSourcePolicy.Plan.class.getRecordComponents())
                .noneMatch(component -> component.getName().toLowerCase().contains("selected")));
        assertTrue(Arrays.stream(
                        SkyIslandPetroleumLocalSourcePolicy.SourceColumn.class.getRecordComponents())
                .noneMatch(component -> component.getName().toLowerCase().contains("selected")));
    }

    @Test
    void planEnvelopeRejectsDroppedEligibleEvidence() {
        SkyIslandPetroleumSystemOpportunityProfile profile = profile(260004L);
        SkyIslandPetroleumLocalSourcePolicy.Plan valid =
                new SkyIslandPetroleumLocalSourcePolicy().plan(profile);
        assertFalse(valid.eligibleCells().isEmpty());

        assertThrows(
                IllegalArgumentException.class,
                () -> new SkyIslandPetroleumLocalSourcePolicy.Plan(
                        profile,
                        valid.eligibleCells().subList(1, valid.eligibleCells().size()),
                        valid.eligibleColumns()));
    }

    @Test
    void publicPlannerAcceptsOnlyExactAuth0098Profile() {
        Method[] plans = Arrays.stream(
                        SkyIslandPetroleumLocalSourcePolicy.class.getDeclaredMethods())
                .filter(method -> Modifier.isPublic(method.getModifiers()))
                .filter(method -> method.getName().equals("plan"))
                .toArray(Method[]::new);

        assertEquals(1, plans.length);
        assertEquals(
                List.of(SkyIslandPetroleumSystemOpportunityProfile.class),
                List.of(plans[0].getParameterTypes()));
        assertEquals(SkyIslandPetroleumLocalSourcePolicy.Plan.class, plans[0].getReturnType());
    }

    private static SkyIslandPetroleumSystemOpportunityProfile profile(long islandKey) {
        SkyIslandDescriptor base = SkyIslandDescriptorGenerator.derive(
                SkyIslandIdentity.of(WORLD, 26L, 260L, islandKey));
        SkyIslandDescriptor descriptor = new SkyIslandDescriptor(
                base.schemaVersion(),
                base.identity(),
                base.authorshipSeed(),
                base.morphologyFamily(),
                180.0,
                base.reliefBudget(),
                0.72,
                0.68,
                0.70,
                0.70,
                base.exposureTendency(),
                0.60,
                0.76,
                base.ecologicalPotential());
        return new SkyIslandPetroleumSystemOpportunityProfiler().profile(descriptor);
    }
}
