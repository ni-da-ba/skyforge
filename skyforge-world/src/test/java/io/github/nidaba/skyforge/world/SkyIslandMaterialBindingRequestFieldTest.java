package io.github.nidaba.skyforge.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandIdentity;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SkyIslandMaterialBindingRequestFieldTest {
    private static final long SEED = 0x534B59464F524745L;

    @Test
    void requestCatalogIsDeterministic() {
        SkyIslandDescriptor descriptor = descriptor(2211L);
        SkyIslandMaterialBindingRequestCatalog first =
                SkyIslandMaterialBindingRequestCatalog.create(descriptor);
        SkyIslandMaterialBindingRequestCatalog second =
                SkyIslandMaterialBindingRequestCatalog.create(descriptor);

        assertEquals(first.plannedRequests(), second.plannedRequests());
        for (SkyIslandMaterialBindingRequest request : first.plannedRequests()) {
            assertEquals(request, second.request(request.bindingKey()));
            assertTrue(request.bindingKey().canonicalToken().startsWith("sfbind:v1:"));
        }
    }

    @Test
    void plannedRequestsPreserveCoherenceContextAndStableRolePolicy() {
        for (long key : new long[] {2332L, 653L, 1051L, 2211L, 1439L, 3670L}) {
            SkyIslandDescriptor descriptor = descriptor(key);
            SkyIslandMaterialBindingRequestCatalog catalog =
                    SkyIslandMaterialBindingRequestCatalog.create(descriptor);

            for (SkyIslandSemanticPaletteBindingDomain domain :
                    catalog.bindingPlan().domains()) {
                SkyIslandMaterialBindingRequest request =
                        catalog.request(domain.key());
                assertEquals(domain.key(), request.bindingKey());
                assertEquals(
                        SkyIslandMaterialBindingRequestPolicy.required(domain.key().role()),
                        request.required());
                assertEquals(
                        SkyIslandMaterialBindingRequestPolicy.minimumEligibleSupport(
                                domain.key().role()),
                        request.minimumEligibleSupport());
                assertEquals(
                        SkyIslandMaterialBindingRequestPolicy.minimumSecondaryHostRatio(
                                domain.key().role()),
                        request.minimumSecondaryHostRatio());
                assertEquals(
                        SkyIslandMaterialBindingRequestPolicy.maximumExpressionCeiling(
                                domain.key().role()),
                        request.maximumExpressionCeiling());
                assertEquals(-1, request.contactId());
                assertEquals(null, request.contactKind());

                if (domain.key().domainKind()
                        == SkyIslandSemanticPaletteBindingDomainKind.ASSEMBLAGE_REGION) {
                    assertEquals(1, request.assemblages().size());
                } else {
                    assertFalse(request.assemblages().isEmpty());
                }
            }
        }
    }

    @Test
    void everyLocalBindingReceivesOneRequestWithoutChangingLocalExpressionState() {
        for (long key : new long[] {653L, 1051L, 2211L, 1439L}) {
            SkyIslandDescriptor descriptor = descriptor(key);
            SkyIslandSemanticPaletteBindingField bindings =
                    SkyIslandSemanticPaletteBindingField.create(descriptor);
            SkyIslandMaterialBindingRequestField requests =
                    SkyIslandMaterialBindingRequestField.create(descriptor);
            double radius = descriptor.nominalRadius();

            for (int iz = 0; iz < 9; iz++) {
                double z = -radius + iz * (2.0 * radius / 8.0);
                for (int id = 0; id < 5; id++) {
                    double depth = id / 4.0;
                    for (int ix = 0; ix < 9; ix++) {
                        double x = -radius + ix * (2.0 * radius / 8.0);
                        SkyIslandSubsurfacePosition position =
                                new SkyIslandSubsurfacePosition(x, z, depth);
                        SkyIslandSemanticPaletteBindingSelection source =
                                bindings.sample(position);
                        SkyIslandMaterialBindingRequestSelection result =
                                requests.sample(position);

                        assertEquals(source.owned(), result.owned());
                        assertEquals(source.materialPresent(), result.materialPresent());
                        if (!source.materialPresent()) {
                            assertTrue(result.uses().isEmpty());
                            continue;
                        }
                        assertEquals(source.localAssemblageId(), result.localAssemblageId());
                        assertEquals(source.contactId(), result.contactId());
                        assertEquals(source.bindings().size(), result.uses().size());

                        for (SkyIslandSemanticPaletteBindingCandidate binding :
                                source.bindings()) {
                            SkyIslandMaterialBindingRequestUse use =
                                    result.use(binding.candidate().role()).orElseThrow();
                            assertEquals(binding, use.binding());
                            assertEquals(binding.bindingKey(), use.request().bindingKey());
                            assertEquals(
                                    binding.candidate().support(),
                                    use.localSupport());
                            assertEquals(
                                    binding.candidate().expressionCeiling(),
                                    use.localExpressionCeiling());
                        }
                    }
                }
            }
        }
    }

    @Test
    void oneBindingKeyAlwaysProducesOneStableRequestAcrossContinuousSampling() {
        Map<SkyIslandSemanticPaletteBindingKey, SkyIslandMaterialBindingRequest> seen =
                new HashMap<>();
        int repeated = 0;

        for (long key : new long[] {2332L, 653L, 1051L, 2211L, 1439L, 3670L}) {
            SkyIslandDescriptor descriptor = descriptor(key);
            SkyIslandMaterialBindingRequestField field =
                    SkyIslandMaterialBindingRequestField.create(descriptor);
            double radius = descriptor.nominalRadius();

            for (int iz = 0; iz < 15; iz++) {
                double z = -radius + iz * (2.0 * radius / 14.0);
                for (int ix = 0; ix < 15; ix++) {
                    double x = -radius + ix * (2.0 * radius / 14.0);
                    SkyIslandMaterialBindingRequestSelection selection =
                            field.sample(new SkyIslandSubsurfacePosition(x, z, 0.52));
                    for (SkyIslandMaterialBindingRequestUse use : selection.uses()) {
                        SkyIslandMaterialBindingRequest previous =
                                seen.putIfAbsent(
                                        use.request().bindingKey(), use.request());
                        if (previous != null) {
                            assertEquals(previous, use.request());
                            repeated++;
                        }
                    }
                }
            }
        }
        assertTrue(repeated > 0);
    }

    @Test
    void contactFallbackRequestsAreContactScopedAndCarryBothParentAssemblages() {
        int observed = 0;
        Map<SkyIslandSemanticPaletteBindingKey, SkyIslandMaterialBindingRequest> seen =
                new HashMap<>();

        for (long key : new long[] {2332L, 653L, 1051L, 2211L, 1439L, 3670L}) {
            SkyIslandDescriptor descriptor = descriptor(key);
            SkyIslandMaterialBindingRequestField field =
                    SkyIslandMaterialBindingRequestField.create(descriptor);
            double radius = descriptor.nominalRadius();

            for (int iz = 0; iz < 28; iz++) {
                double z = -radius + iz * (2.0 * radius / 27.0);
                for (int ix = 0; ix < 28; ix++) {
                    double x = -radius + ix * (2.0 * radius / 27.0);
                    SkyIslandMaterialBindingRequestSelection selection =
                            field.sample(new SkyIslandSubsurfacePosition(x, z, 0.52));
                    for (SkyIslandMaterialBindingRequestUse use : selection.uses()) {
                        SkyIslandMaterialBindingRequest request = use.request();
                        if (request.domainKind()
                                != SkyIslandSemanticPaletteBindingDomainKind.CONTACT_TRANSITION) {
                            continue;
                        }
                        assertTrue(selection.contactId() >= 0);
                        assertEquals(selection.contactId(), request.contactId());
                        assertEquals(request.bindingKey().anchorId(), request.contactId());
                        assertNotNull(request.contactKind());
                        assertEquals(2, request.assemblages().size());
                        SkyIslandMaterialBindingRequest previous =
                                seen.putIfAbsent(request.bindingKey(), request);
                        if (previous != null) {
                            assertEquals(previous, request);
                        }
                        observed++;
                    }
                }
            }
        }
        assertTrue(observed > 0);
    }

    @Test
    void requestMaximumsAlwaysContainTheLocalAuth0037Expression() {
        for (long key : new long[] {653L, 1051L, 2211L, 1439L, 3670L}) {
            SkyIslandDescriptor descriptor = descriptor(key);
            SkyIslandMaterialBindingRequestField field =
                    SkyIslandMaterialBindingRequestField.create(descriptor);
            double radius = descriptor.nominalRadius();

            for (int iz = 0; iz < 11; iz++) {
                double z = -radius + iz * (2.0 * radius / 10.0);
                for (int ix = 0; ix < 11; ix++) {
                    double x = -radius + ix * (2.0 * radius / 10.0);
                    SkyIslandMaterialBindingRequestSelection selection =
                            field.sample(new SkyIslandSubsurfacePosition(x, z, 0.52));
                    for (SkyIslandMaterialBindingRequestUse use : selection.uses()) {
                        assertTrue(use.localSupport() + 1.0e-12
                                >= use.request().minimumEligibleSupport());
                        assertTrue(use.localExpressionCeiling()
                                <= use.request().maximumExpressionCeiling() + 1.0e-12);
                    }
                }
            }
        }
    }

    private static SkyIslandDescriptor descriptor(long key) {
        return SkyIslandDescriptorGenerator.derive(
                SkyIslandIdentity.of(SEED, 8L, 81L, key));
    }
}
