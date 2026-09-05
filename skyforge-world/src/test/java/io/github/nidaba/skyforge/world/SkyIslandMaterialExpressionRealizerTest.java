package io.github.nidaba.skyforge.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandIdentity;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SkyIslandMaterialExpressionRealizerTest {
    private static final long SEED = 0x534B59464F524745L;

    private static final List<SkyIslandMaterialCapabilityProfile> CANDIDATES =
            List.of(
                    new SkyIslandMaterialCapabilityProfile(
                            0.94, 0.20, 0.20, 0.20, 0.20),
                    new SkyIslandMaterialCapabilityProfile(
                            0.92, 0.94, 0.20, 0.20, 0.20),
                    new SkyIslandMaterialCapabilityProfile(
                            0.20, 0.20, 0.94, 0.20, 0.20),
                    new SkyIslandMaterialCapabilityProfile(
                            0.20, 0.20, 0.20, 0.94, 0.20),
                    new SkyIslandMaterialCapabilityProfile(
                            0.20, 0.20, 0.20, 0.20, 0.94),
                    SkyIslandMaterialCapabilityProfile.uniform(0.86));

    @Test
    void sameExactPointAlwaysProducesSameSemanticWinner() {
        SkyIslandDescriptor descriptor = descriptor(1439L);
        SkyIslandMaterialBindingRequestField field =
                SkyIslandMaterialBindingRequestField.create(descriptor);
        Map<SkyIslandSemanticPaletteBindingKey, SkyIslandMaterialResolutionDecision>
                decisions = new HashMap<>();
        SkyIslandSubsurfacePosition position =
                new SkyIslandSubsurfacePosition(4.25, -7.75, 0.52);

        SkyIslandMaterialExpressionSample sample =
                expressionAt(field, position, decisions);
        SkyIslandMaterialRealizationSelection first =
                SkyIslandMaterialExpressionRealizer.realize(position, sample);
        SkyIslandMaterialRealizationSelection second =
                SkyIslandMaterialExpressionRealizer.realize(position, sample);

        assertEquals(first, second);
    }

    @Test
    void allocationEncounterOrderDoesNotChangeWinner() {
        SkyIslandDescriptor descriptor = descriptor(2211L);
        SkyIslandMaterialBindingRequestField field =
                SkyIslandMaterialBindingRequestField.create(descriptor);
        Map<SkyIslandSemanticPaletteBindingKey, SkyIslandMaterialResolutionDecision>
                decisions = new HashMap<>();
        SkyIslandSubsurfacePosition position =
                new SkyIslandSubsurfacePosition(-3.5, 5.5, 0.52);

        SkyIslandMaterialExpressionSample sample =
                expressionAt(field, position, decisions);
        if (!sample.source().materialPresent()) {
            return;
        }

        List<SkyIslandMaterialExpressionAllocation> reversed =
                new ArrayList<>(sample.allocations());
        java.util.Collections.reverse(reversed);
        SkyIslandMaterialExpressionSample reordered =
                new SkyIslandMaterialExpressionSample(sample.source(), reversed);

        SkyIslandMaterialRealizationSelection first =
                SkyIslandMaterialExpressionRealizer.realize(position, sample);
        SkyIslandMaterialRealizationSelection second =
                SkyIslandMaterialExpressionRealizer.realize(position, reordered);

        assertEquals(first.winnerBindingKey(), second.winnerBindingKey());
        assertEquals(first.winnerRole(), second.winnerRole());
        assertEquals(first.activeConditionedClaims(), second.activeConditionedClaims());
    }

    @Test
    void structuralWinnerMatchesSecondaryThresholdOrPrimaryResidual() {
        SkyIslandDescriptor descriptor = descriptor(2332L);
        SkyIslandMaterialBindingRequestField field =
                SkyIslandMaterialBindingRequestField.create(descriptor);
        Map<SkyIslandSemanticPaletteBindingKey, SkyIslandMaterialResolutionDecision>
                decisions = new HashMap<>();
        double radius = descriptor.nominalRadius();

        for (int iz = 0; iz < 17; iz++) {
            double z = -radius + iz * (2.0 * radius / 16.0);
            for (int ix = 0; ix < 17; ix++) {
                double x = -radius + ix * (2.0 * radius / 16.0);
                SkyIslandSubsurfacePosition position =
                        new SkyIslandSubsurfacePosition(x, z, 0.52);
                SkyIslandMaterialExpressionSample sample =
                        expressionAt(field, position, decisions);
                SkyIslandMaterialRealizationSelection realization =
                        SkyIslandMaterialExpressionRealizer.realize(position, sample);

                if (!sample.source().materialPresent()) {
                    assertFalse(realization.materialPresent());
                    continue;
                }

                var secondary =
                        sample.allocation(
                                SkyIslandSemanticMaterialPaletteRole.SECONDARY_MATRIX);
                if (secondary.isEmpty()) {
                    assertEquals(
                            SkyIslandSemanticMaterialPaletteRole.PRIMARY_MATRIX,
                            realization.structuralWinner().role());
                } else {
                    var allocation = secondary.orElseThrow();
                    double selector =
                            SkyIslandMaterialExpressionRealizer.spatialField(
                                    allocation, position);
                    SkyIslandSemanticMaterialPaletteRole expected =
                            selector < allocation.targetExpression()
                                    ? SkyIslandSemanticMaterialPaletteRole.SECONDARY_MATRIX
                                    : SkyIslandSemanticMaterialPaletteRole.PRIMARY_MATRIX;
                    assertEquals(expected, realization.structuralWinner().role());
                }
            }
        }
    }

    @Test
    void strongestActiveConditionedMarginWins() {
        SkyIslandDescriptor descriptor = descriptor(1439L);
        SkyIslandMaterialBindingRequestField field =
                SkyIslandMaterialBindingRequestField.create(descriptor);
        Map<SkyIslandSemanticPaletteBindingKey, SkyIslandMaterialResolutionDecision>
                decisions = new HashMap<>();
        double radius = descriptor.nominalRadius();
        boolean observedConditionedWinner = false;
        boolean observedMultiActive = false;

        for (int iz = 0; iz < 25; iz++) {
            double z = -radius + iz * (2.0 * radius / 24.0);
            for (int ix = 0; ix < 25; ix++) {
                double x = -radius + ix * (2.0 * radius / 24.0);
                SkyIslandSubsurfacePosition position =
                        new SkyIslandSubsurfacePosition(x, z, 0.52);
                SkyIslandMaterialExpressionSample sample =
                        expressionAt(field, position, decisions);
                if (!sample.source().materialPresent()) {
                    continue;
                }

                SkyIslandMaterialExpressionAllocation expected = null;
                double bestMargin = -1.0;
                int active = 0;
                for (SkyIslandMaterialExpressionAllocation allocation :
                        sample.allocations()) {
                    if (allocation.mode()
                            != SkyIslandMaterialExpressionMode
                                    .CONDITIONED_EXPRESSION_CLAIM) {
                        continue;
                    }
                    double fieldValue =
                            SkyIslandMaterialExpressionRealizer.spatialField(
                                    allocation, position);
                    if (fieldValue >= allocation.targetExpression()) {
                        continue;
                    }
                    active++;
                    double margin =
                            SkyIslandMaterialExpressionRealizer.conditionedMargin(
                                    allocation, position);
                    if (expected == null
                            || margin > bestMargin + 1.0e-12
                            || (Math.abs(margin - bestMargin) <= 1.0e-12
                                    && allocation.bindingKey().canonicalToken()
                                                    .compareTo(
                                                            expected.bindingKey()
                                                                    .canonicalToken())
                                            < 0)) {
                        expected = allocation;
                        bestMargin = margin;
                    }
                }

                SkyIslandMaterialRealizationSelection realization =
                        SkyIslandMaterialExpressionRealizer.realize(position, sample);
                assertEquals(active, realization.activeConditionedClaims());
                if (expected != null) {
                    observedConditionedWinner = true;
                    observedMultiActive |= active > 1;
                    assertEquals(expected.bindingKey(), realization.winner().bindingKey());
                } else {
                    assertEquals(
                            realization.structuralWinner().bindingKey(),
                            realization.winner().bindingKey());
                }
            }
        }

        assertTrue(observedConditionedWinner);
        assertTrue(observedMultiActive);
    }

    @Test
    void spatialFieldIsSmoothAtNearbyPointsAndBindingSpecific() {
        SkyIslandDescriptor descriptor = descriptor(1439L);
        SkyIslandMaterialBindingRequestField field =
                SkyIslandMaterialBindingRequestField.create(descriptor);
        Map<SkyIslandSemanticPaletteBindingKey, SkyIslandMaterialResolutionDecision>
                decisions = new HashMap<>();
        SkyIslandMaterialBindingRequestSelection source =
                firstMaterialSample(field, descriptor);
        ensureDecisions(source, decisions);
        SkyIslandMaterialExpressionSample sample =
                SkyIslandMaterialExpressionAllocator.allocate(source, decisions);

        SkyIslandMaterialExpressionAllocation first = sample.allocations().get(0);
        SkyIslandSubsurfacePosition p0 =
                new SkyIslandSubsurfacePosition(2.0, -3.0, 0.52);
        SkyIslandSubsurfacePosition p1 =
                new SkyIslandSubsurfacePosition(2.01, -3.01, 0.5201);

        double v0 =
                SkyIslandMaterialExpressionRealizer.spatialField(first, p0);
        double v1 =
                SkyIslandMaterialExpressionRealizer.spatialField(first, p1);
        assertTrue(Math.abs(v0 - v1) < 0.01);

        if (sample.allocations().size() > 1) {
            double other =
                    SkyIslandMaterialExpressionRealizer.spatialField(
                            sample.allocations().get(1), p0);
            assertNotEquals(v0, other);
        }
    }

    @Test
    void nonMaterialSamplesNeverReceiveWinner() {
        SkyIslandMaterialExpressionSample outside =
                new SkyIslandMaterialExpressionSample(
                        SkyIslandMaterialBindingRequestSelection.outside(),
                        List.of());
        SkyIslandMaterialRealizationSelection result =
                SkyIslandMaterialExpressionRealizer.realize(
                        new SkyIslandSubsurfacePosition(0.0, 0.0, 0.52), outside);

        assertFalse(result.materialPresent());
        assertTrue(result.winnerRole().isEmpty());
        assertTrue(result.winnerBindingKey().isEmpty());
    }

    private static SkyIslandMaterialExpressionSample expressionAt(
            SkyIslandMaterialBindingRequestField field,
            SkyIslandSubsurfacePosition position,
            Map<SkyIslandSemanticPaletteBindingKey, SkyIslandMaterialResolutionDecision>
                    decisions) {
        SkyIslandMaterialBindingRequestSelection source = field.sample(position);
        ensureDecisions(source, decisions);
        return SkyIslandMaterialExpressionAllocator.allocate(source, decisions);
    }

    private static SkyIslandMaterialBindingRequestSelection firstMaterialSample(
            SkyIslandMaterialBindingRequestField field,
            SkyIslandDescriptor descriptor) {
        double radius = descriptor.nominalRadius();
        for (int iz = 0; iz < 21; iz++) {
            double z = -radius + iz * (2.0 * radius / 20.0);
            for (int ix = 0; ix < 21; ix++) {
                double x = -radius + ix * (2.0 * radius / 20.0);
                SkyIslandMaterialBindingRequestSelection selection =
                        field.sample(new SkyIslandSubsurfacePosition(x, z, 0.52));
                if (selection.materialPresent()) {
                    return selection;
                }
            }
        }
        throw new IllegalStateException("canonical island produced no material sample");
    }

    private static void ensureDecisions(
            SkyIslandMaterialBindingRequestSelection source,
            Map<SkyIslandSemanticPaletteBindingKey, SkyIslandMaterialResolutionDecision>
                    decisions) {
        for (SkyIslandMaterialBindingRequestUse use : source.uses()) {
            decisions.computeIfAbsent(
                    use.request().bindingKey(),
                    ignored -> decision(use.request()));
        }
    }

    private static SkyIslandMaterialResolutionDecision decision(
            SkyIslandMaterialBindingRequest request) {
        SkyIslandMaterialResolutionFrontier frontier =
                SkyIslandMaterialResolutionDecisionFactory.frontier(
                        request, CANDIDATES);
        return SkyIslandMaterialResolutionDecisionFactory.decide(
                frontier,
                frontier.topRank().profile(),
                SkyIslandMaterialResolutionSelectionMethod.SEMANTIC_RANK_WINNER);
    }

    private static SkyIslandDescriptor descriptor(long key) {
        return SkyIslandDescriptorGenerator.derive(
                SkyIslandIdentity.of(SEED, 8L, 81L, key));
    }
}
