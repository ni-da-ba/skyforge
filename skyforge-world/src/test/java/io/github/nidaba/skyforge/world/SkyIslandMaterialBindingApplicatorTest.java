package io.github.nidaba.skyforge.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandIdentity;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SkyIslandMaterialBindingApplicatorTest {
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
    void applicationEnvelopeRetainsExactWinnerAndProvenance() {
        SkyIslandMaterialRealizationSelection realization = firstMaterialRealization(1439L);
        SkyIslandMaterialBindingApplication application =
                SkyIslandMaterialBindingApplication.from(realization).orElseThrow();

        assertEquals(realization, application.realization());
        assertEquals(realization.winner().bindingKey(), application.bindingKey());
        assertEquals(realization.winner().role(), application.role());
        assertEquals(realization.winner().decision(), application.resolutionDecision());
        assertEquals(realization.winner().use().request(), application.request());
    }

    @Test
    void forgedNonWinnerBindingKeyIsRejected() {
        SkyIslandMaterialRealizationSelection realization = firstMaterialRealization(1439L);
        SkyIslandSemanticPaletteBindingKey winner = realization.winner().bindingKey();
        SkyIslandSemanticPaletteBindingKey other =
                SkyIslandSemanticPaletteBindingKey.of(
                        winner.islandIdentity(),
                        winner.role(),
                        winner.sourceChannel(),
                        winner.domainKind(),
                        winner.anchorId() + 1);

        assertThrows(
                IllegalArgumentException.class,
                () -> new SkyIslandMaterialBindingApplication(realization, other));
    }

    @Test
    void authoredVoidProducesNoApplicationAndNoBackendLookup() {
        SkyIslandMaterialExpressionSample outside =
                new SkyIslandMaterialExpressionSample(
                        SkyIslandMaterialBindingRequestSelection.outside(),
                        List.of());
        SkyIslandMaterialRealizationSelection realization =
                SkyIslandMaterialExpressionRealizer.realize(
                        new SkyIslandSubsurfacePosition(0.0, 0.0, 0.52), outside);

        assertTrue(SkyIslandMaterialBindingApplication.from(realization).isEmpty());
        assertTrue(
                SkyIslandMaterialBindingApplicator.apply(
                                realization,
                                Map.<SkyIslandSemanticPaletteBindingKey, Object>of())
                        .isEmpty());
    }

    @Test
    void exactWinnerKeySelectsAdapterOwnedOpaqueValue() {
        SkyIslandMaterialRealizationSelection realization = firstMaterialRealization(2332L);
        Object backendMaterial = new Object();
        Map<SkyIslandSemanticPaletteBindingKey, Object> bindings =
                Map.of(realization.winner().bindingKey(), backendMaterial);

        Object applied =
                SkyIslandMaterialBindingApplicator.apply(realization, bindings)
                        .orElseThrow();

        assertSame(backendMaterial, applied);
    }

    @Test
    void missingOrNullBindingFailsInsteadOfInventingFallback() {
        SkyIslandMaterialRealizationSelection realization = firstMaterialRealization(2332L);
        SkyIslandMaterialBindingApplication application =
                SkyIslandMaterialBindingApplication.from(realization).orElseThrow();

        assertThrows(
                IllegalArgumentException.class,
                () -> SkyIslandMaterialBindingApplicator.apply(
                        application,
                        Map.<SkyIslandSemanticPaletteBindingKey, Object>of()));

        Map<SkyIslandSemanticPaletteBindingKey, Object> nullBinding =
                new HashMap<>();
        nullBinding.put(application.bindingKey(), null);
        assertThrows(
                IllegalArgumentException.class,
                () -> SkyIslandMaterialBindingApplicator.apply(application, nullBinding));
    }

    @Test
    void conditionedWinnerOverridesStructuralBindingAtApplicationBoundary() {
        SkyIslandMaterialRealizationSelection conditioned = firstConditionedRealization(1439L);
        assertTrue(conditioned.conditionedWinner());
        assertFalse(
                conditioned.winner().bindingKey()
                        .equals(conditioned.structuralWinner().bindingKey()));

        Object structuralMaterial = new Object();
        Object conditionedMaterial = new Object();
        Map<SkyIslandSemanticPaletteBindingKey, Object> bindings = new HashMap<>();
        bindings.put(conditioned.structuralWinner().bindingKey(), structuralMaterial);
        bindings.put(conditioned.winner().bindingKey(), conditionedMaterial);

        Object applied =
                SkyIslandMaterialBindingApplicator.apply(conditioned, bindings)
                        .orElseThrow();

        assertSame(conditionedMaterial, applied);
    }

    @Test
    void repeatedStableWinnerKeyReusesSameBackendBinding() {
        SkyIslandDescriptor descriptor = descriptor(2211L);
        SkyIslandMaterialBindingRequestField field =
                SkyIslandMaterialBindingRequestField.create(descriptor);
        Map<SkyIslandSemanticPaletteBindingKey, SkyIslandMaterialResolutionDecision>
                decisions = new HashMap<>();
        Map<SkyIslandSemanticPaletteBindingKey, Object> backendBindings =
                new HashMap<>();
        Map<SkyIslandSemanticPaletteBindingKey, Object> firstApplied =
                new HashMap<>();
        int repeatedSamples = 0;
        double radius = descriptor.nominalRadius();

        for (int iz = 0; iz < 25; iz++) {
            double z = -radius + iz * (2.0 * radius / 24.0);
            for (int ix = 0; ix < 25; ix++) {
                double x = -radius + ix * (2.0 * radius / 24.0);
                SkyIslandMaterialRealizationSelection realization =
                        realizeAt(
                                field,
                                new SkyIslandSubsurfacePosition(x, z, 0.52),
                                decisions);
                if (!realization.materialPresent()) {
                    continue;
                }

                SkyIslandSemanticPaletteBindingKey key =
                        realization.winner().bindingKey();
                Object binding = backendBindings.computeIfAbsent(key, ignored -> new Object());
                Object applied =
                        SkyIslandMaterialBindingApplicator.apply(realization, backendBindings)
                                .orElseThrow();
                assertSame(binding, applied);

                Object previous = firstApplied.putIfAbsent(key, applied);
                if (previous != null) {
                    repeatedSamples++;
                    assertSame(previous, applied);
                }
            }
        }

        assertTrue(repeatedSamples > 0);
    }

    private static SkyIslandMaterialRealizationSelection firstMaterialRealization(long key) {
        SkyIslandDescriptor descriptor = descriptor(key);
        SkyIslandMaterialBindingRequestField field =
                SkyIslandMaterialBindingRequestField.create(descriptor);
        Map<SkyIslandSemanticPaletteBindingKey, SkyIslandMaterialResolutionDecision>
                decisions = new HashMap<>();
        double radius = descriptor.nominalRadius();

        for (int iz = 0; iz < 21; iz++) {
            double z = -radius + iz * (2.0 * radius / 20.0);
            for (int ix = 0; ix < 21; ix++) {
                double x = -radius + ix * (2.0 * radius / 20.0);
                SkyIslandMaterialRealizationSelection realization =
                        realizeAt(
                                field,
                                new SkyIslandSubsurfacePosition(x, z, 0.52),
                                decisions);
                if (realization.materialPresent()) {
                    return realization;
                }
            }
        }
        throw new IllegalStateException("canonical island produced no material realization");
    }

    private static SkyIslandMaterialRealizationSelection firstConditionedRealization(long key) {
        SkyIslandDescriptor descriptor = descriptor(key);
        SkyIslandMaterialBindingRequestField field =
                SkyIslandMaterialBindingRequestField.create(descriptor);
        Map<SkyIslandSemanticPaletteBindingKey, SkyIslandMaterialResolutionDecision>
                decisions = new HashMap<>();
        double radius = descriptor.nominalRadius();

        for (int iz = 0; iz < 41; iz++) {
            double z = -radius + iz * (2.0 * radius / 40.0);
            for (int ix = 0; ix < 41; ix++) {
                double x = -radius + ix * (2.0 * radius / 40.0);
                SkyIslandMaterialRealizationSelection realization =
                        realizeAt(
                                field,
                                new SkyIslandSubsurfacePosition(x, z, 0.52),
                                decisions);
                if (realization.conditionedWinner()) {
                    return realization;
                }
            }
        }
        throw new IllegalStateException("canonical island produced no conditioned realization");
    }

    private static SkyIslandMaterialRealizationSelection realizeAt(
            SkyIslandMaterialBindingRequestField field,
            SkyIslandSubsurfacePosition position,
            Map<SkyIslandSemanticPaletteBindingKey, SkyIslandMaterialResolutionDecision>
                    decisions) {
        SkyIslandMaterialBindingRequestSelection source = field.sample(position);
        ensureDecisions(source, decisions);
        SkyIslandMaterialExpressionSample expression =
                SkyIslandMaterialExpressionAllocator.allocate(source, decisions);
        return SkyIslandMaterialExpressionRealizer.realize(position, expression);
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
