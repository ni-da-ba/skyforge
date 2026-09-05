package io.github.nidaba.skyforge.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandIdentity;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SkyIslandMaterialExpressionAllocatorTest {
    private static final long SEED = 0x534B59464F524745L;

    private static final SkyIslandMaterialCapabilityProfile MATRIX =
            new SkyIslandMaterialCapabilityProfile(0.94, 0.20, 0.20, 0.20, 0.20);
    private static final SkyIslandMaterialCapabilityProfile FABRIC =
            new SkyIslandMaterialCapabilityProfile(0.92, 0.94, 0.20, 0.20, 0.20);
    private static final SkyIslandMaterialCapabilityProfile ALTERATION =
            new SkyIslandMaterialCapabilityProfile(0.20, 0.20, 0.94, 0.20, 0.20);
    private static final SkyIslandMaterialCapabilityProfile WATER =
            new SkyIslandMaterialCapabilityProfile(0.20, 0.20, 0.20, 0.94, 0.20);
    private static final SkyIslandMaterialCapabilityProfile ACCENT =
            new SkyIslandMaterialCapabilityProfile(0.20, 0.20, 0.20, 0.20, 0.94);
    private static final SkyIslandMaterialCapabilityProfile GENERALIST =
            SkyIslandMaterialCapabilityProfile.uniform(0.86);

    private static final List<SkyIslandMaterialCapabilityProfile> CANDIDATES =
            List.of(MATRIX, FABRIC, ALTERATION, WATER, ACCENT, GENERALIST);

    @Test
    void structuralMatrixAlwaysFormsAnExactPartition() {
        for (long key : new long[] {2332L, 653L, 1051L, 2211L, 1439L, 3670L}) {
            SkyIslandDescriptor descriptor = descriptor(key);
            SkyIslandMaterialBindingRequestField field =
                    SkyIslandMaterialBindingRequestField.create(descriptor);
            Map<SkyIslandSemanticPaletteBindingKey, SkyIslandMaterialResolutionDecision>
                    decisions = new HashMap<>();
            double radius = descriptor.nominalRadius();

            for (int iz = 0; iz < 13; iz++) {
                double z = -radius + iz * (2.0 * radius / 12.0);
                for (int ix = 0; ix < 13; ix++) {
                    double x = -radius + ix * (2.0 * radius / 12.0);
                    SkyIslandMaterialBindingRequestSelection source =
                            field.sample(new SkyIslandSubsurfacePosition(x, z, 0.52));
                    ensureDecisions(source, decisions);
                    SkyIslandMaterialExpressionSample result =
                            SkyIslandMaterialExpressionAllocator.allocate(
                                    source, decisions);

                    assertEquals(source.materialPresent(), !result.allocations().isEmpty());
                    if (!source.materialPresent()) {
                        continue;
                    }

                    assertEquals(
                            1.0,
                            result.primaryMatrixShare()
                                    + result.secondaryMatrixShare(),
                            1.0e-12);
                    assertTrue(result.primaryMatrixShare() >= 0.52 - 1.0e-12);
                    assertTrue(result.primaryMatrixShare() <= 1.0);

                    source.use(SkyIslandSemanticMaterialPaletteRole.SECONDARY_MATRIX)
                            .ifPresentOrElse(
                                    secondary ->
                                            assertEquals(
                                                    secondary.localSupport()
                                                            * secondary
                                                                    .localExpressionCeiling(),
                                                    result.secondaryMatrixShare(),
                                                    1.0e-12),
                                    () -> assertEquals(0.0, result.secondaryMatrixShare()));
                }
            }
        }
    }

    @Test
    void conditionedClaimsFollowAuthoredSupportAndCeilingWithoutRenormalization() {
        SkyIslandDescriptor descriptor = descriptor(1439L);
        SkyIslandMaterialBindingRequestField field =
                SkyIslandMaterialBindingRequestField.create(descriptor);
        Map<SkyIslandSemanticPaletteBindingKey, SkyIslandMaterialResolutionDecision>
                decisions = new HashMap<>();
        double radius = descriptor.nominalRadius();
        boolean observedOverlap = false;

        for (int iz = 0; iz < 17; iz++) {
            double z = -radius + iz * (2.0 * radius / 16.0);
            for (int ix = 0; ix < 17; ix++) {
                double x = -radius + ix * (2.0 * radius / 16.0);
                SkyIslandMaterialBindingRequestSelection source =
                        field.sample(new SkyIslandSubsurfacePosition(x, z, 0.52));
                ensureDecisions(source, decisions);
                SkyIslandMaterialExpressionSample result =
                        SkyIslandMaterialExpressionAllocator.allocate(source, decisions);

                for (SkyIslandMaterialExpressionAllocation allocation :
                        result.allocations()) {
                    if (allocation.mode()
                            != SkyIslandMaterialExpressionMode
                                    .CONDITIONED_EXPRESSION_CLAIM) {
                        continue;
                    }
                    assertEquals(
                            allocation.localSupport()
                                    * allocation.localExpressionCeiling(),
                            allocation.targetExpression(),
                            1.0e-12);
                    assertTrue(
                            allocation.targetExpression()
                                    <= allocation.localExpressionCeiling() + 1.0e-12);
                }
                observedOverlap |= result.conditionedClaimCount() >= 2;
            }
        }
        assertTrue(observedOverlap);
    }

    @Test
    void allocationPreservesExactRequestAndResolutionDecision() {
        SkyIslandDescriptor descriptor = descriptor(2211L);
        SkyIslandMaterialBindingRequestField field =
                SkyIslandMaterialBindingRequestField.create(descriptor);
        Map<SkyIslandSemanticPaletteBindingKey, SkyIslandMaterialResolutionDecision>
                decisions = new HashMap<>();

        SkyIslandMaterialBindingRequestSelection source =
                field.sample(new SkyIslandSubsurfacePosition(0.0, 0.0, 0.52));
        ensureDecisions(source, decisions);
        SkyIslandMaterialExpressionSample result =
                SkyIslandMaterialExpressionAllocator.allocate(source, decisions);

        for (SkyIslandMaterialExpressionAllocation allocation :
                result.allocations()) {
            assertEquals(
                    allocation.use().request(),
                    allocation.decision().request());
            assertEquals(
                    decisions.get(allocation.bindingKey()),
                    allocation.decision());
        }
    }

    @Test
    void repeatedStableBindingCanVaryLocallyWithoutChangingResolutionDecision() {
        SkyIslandDescriptor descriptor = descriptor(1439L);
        SkyIslandMaterialBindingRequestField field =
                SkyIslandMaterialBindingRequestField.create(descriptor);
        Map<SkyIslandSemanticPaletteBindingKey, SkyIslandMaterialResolutionDecision>
                decisions = new HashMap<>();
        Map<SkyIslandSemanticPaletteBindingKey, Double> firstExpression =
                new HashMap<>();
        boolean observedVariation = false;
        double radius = descriptor.nominalRadius();

        for (int iz = 0; iz < 21; iz++) {
            double z = -radius + iz * (2.0 * radius / 20.0);
            for (int ix = 0; ix < 21; ix++) {
                double x = -radius + ix * (2.0 * radius / 20.0);
                SkyIslandMaterialBindingRequestSelection source =
                        field.sample(new SkyIslandSubsurfacePosition(x, z, 0.52));
                ensureDecisions(source, decisions);
                SkyIslandMaterialExpressionSample result =
                        SkyIslandMaterialExpressionAllocator.allocate(source, decisions);

                for (SkyIslandMaterialExpressionAllocation allocation :
                        result.allocations()) {
                    if (allocation.role()
                            == SkyIslandSemanticMaterialPaletteRole.PRIMARY_MATRIX) {
                        continue;
                    }
                    Double previous =
                            firstExpression.putIfAbsent(
                                    allocation.bindingKey(),
                                    allocation.targetExpression());
                    if (previous != null
                            && Math.abs(previous - allocation.targetExpression())
                                    > 1.0e-6) {
                        observedVariation = true;
                    }
                    assertEquals(
                            decisions.get(allocation.bindingKey()),
                            allocation.decision());
                }
            }
        }
        assertTrue(observedVariation);
    }

    @Test
    void missingOrMismatchedDecisionIsRejected() {
        SkyIslandDescriptor descriptor = descriptor(653L);
        SkyIslandMaterialBindingRequestField field =
                SkyIslandMaterialBindingRequestField.create(descriptor);

        SkyIslandMaterialBindingRequestSelection source =
                firstMaterialSample(field, descriptor);
        assertFalse(source.uses().isEmpty());

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        SkyIslandMaterialExpressionAllocator.allocate(
                                source, Map.of()));
    }

    @Test
    void outsideAndAuthoredVoidRemainUnallocated() {
        SkyIslandDescriptor descriptor = descriptor(2211L);
        SkyIslandMaterialBindingRequestField field =
                SkyIslandMaterialBindingRequestField.create(descriptor);
        double radius = descriptor.nominalRadius();

        SkyIslandMaterialBindingRequestSelection outside =
                field.sample(
                        new SkyIslandSubsurfacePosition(
                                radius * 3.0, radius * 3.0, 0.52));
        SkyIslandMaterialExpressionSample outsideResult =
                SkyIslandMaterialExpressionAllocator.allocate(outside, Map.of());
        assertTrue(outsideResult.allocations().isEmpty());

        boolean observedVoid = false;
        for (int iz = 0; iz < 31 && !observedVoid; iz++) {
            double z = -radius + iz * (2.0 * radius / 30.0);
            for (int ix = 0; ix < 31; ix++) {
                double x = -radius + ix * (2.0 * radius / 30.0);
                SkyIslandMaterialBindingRequestSelection sample =
                        field.sample(new SkyIslandSubsurfacePosition(x, z, 0.70));
                if (sample.owned() && !sample.materialPresent()) {
                    SkyIslandMaterialExpressionSample result =
                            SkyIslandMaterialExpressionAllocator.allocate(sample, Map.of());
                    assertTrue(result.allocations().isEmpty());
                    observedVoid = true;
                    break;
                }
            }
        }
        assertTrue(observedVoid);
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
