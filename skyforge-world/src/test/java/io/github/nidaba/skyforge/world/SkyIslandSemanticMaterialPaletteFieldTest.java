package io.github.nidaba.skyforge.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandIdentity;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SkyIslandSemanticMaterialPaletteFieldTest {
    private static final long SEED = 0x534B59464F524745L;

    @Test
    void paletteSelectionIsDeterministic() {
        SkyIslandDescriptor descriptor = descriptor(2211L);
        SkyIslandSemanticMaterialPaletteField first =
                SkyIslandSemanticMaterialPaletteField.create(descriptor);
        SkyIslandSemanticMaterialPaletteField second =
                SkyIslandSemanticMaterialPaletteField.create(descriptor);
        double radius = descriptor.nominalRadius();

        for (int iz = 0; iz < 7; iz++) {
            double z = -radius + iz * (2.0 * radius / 6.0);
            for (int id = 0; id < 5; id++) {
                double depth = id / 4.0;
                for (int ix = 0; ix < 7; ix++) {
                    double x = -radius + ix * (2.0 * radius / 6.0);
                    SkyIslandSubsurfacePosition position =
                            new SkyIslandSubsurfacePosition(x, z, depth);
                    assertEquals(first.sample(position), second.sample(position));
                }
            }
        }
    }

    @Test
    void paletteSelectionPreservesAuth0036MaterialBoundaryAndProvenance() {
        for (long key : new long[] {653L, 1051L, 2211L, 1439L}) {
            SkyIslandDescriptor descriptor = descriptor(key);
            SkyIslandLithologicRealizationField realization =
                    SkyIslandLithologicRealizationField.create(descriptor);
            SkyIslandSemanticMaterialPaletteField palette =
                    SkyIslandSemanticMaterialPaletteField.create(descriptor);
            double radius = descriptor.nominalRadius();

            for (int iz = 0; iz < 11; iz++) {
                double z = -radius + iz * (2.0 * radius / 10.0);
                for (int id = 0; id < 6; id++) {
                    double depth = id / 5.0;
                    for (int ix = 0; ix < 11; ix++) {
                        double x = -radius + ix * (2.0 * radius / 10.0);
                        SkyIslandSubsurfacePosition position =
                                new SkyIslandSubsurfacePosition(x, z, depth);
                        SkyIslandLithologicRealizationSample source =
                                realization.sample(position);
                        SkyIslandSemanticMaterialPaletteSelection selection =
                                palette.sample(position);

                        assertEquals(source.owned(), selection.owned());
                        assertEquals(source.materialPresent(), selection.materialPresent());
                        if (!source.materialPresent()) {
                            assertTrue(selection.candidates().isEmpty());
                            continue;
                        }
                        assertEquals(source.localAssemblageId(), selection.localAssemblageId());
                        assertEquals(source.localAssemblageKind(), selection.localAssemblageKind());
                        assertEquals(source.contactId(), selection.contactId());
                        assertEquals(source.contactKind(), selection.contactKind());
                    }
                }
            }
        }
    }

    @Test
    void everyMaterialSampleHasExactlyOneRequiredPrimaryMatrixCandidate() {
        for (long key : new long[] {2332L, 653L, 1051L, 2211L, 1439L, 3670L}) {
            SkyIslandDescriptor descriptor = descriptor(key);
            SkyIslandSemanticMaterialPaletteField field =
                    SkyIslandSemanticMaterialPaletteField.create(descriptor);
            double radius = descriptor.nominalRadius();

            for (int iz = 0; iz < 9; iz++) {
                double z = -radius + iz * (2.0 * radius / 8.0);
                for (int ix = 0; ix < 9; ix++) {
                    double x = -radius + ix * (2.0 * radius / 8.0);
                    SkyIslandSemanticMaterialPaletteSelection selection =
                            field.sample(new SkyIslandSubsurfacePosition(x, z, 0.52));
                    if (!selection.materialPresent()) {
                        continue;
                    }
                    long required = selection.candidates().stream()
                            .filter(SkyIslandSemanticMaterialPaletteCandidate::required)
                            .count();
                    assertEquals(1L, required);
                    SkyIslandSemanticMaterialPaletteCandidate primary =
                            selection.candidate(
                                            SkyIslandSemanticMaterialPaletteRole.PRIMARY_MATRIX)
                                    .orElseThrow();
                    assertTrue(primary.required());
                    assertEquals(1.0, primary.expressionCeiling());
                }
            }
        }
    }

    @Test
    void optionalRolesOnlyAppearWithRequiredAuth0036Support() {
        for (long key : new long[] {653L, 1051L, 2211L, 1439L, 3670L}) {
            SkyIslandDescriptor descriptor = descriptor(key);
            SkyIslandLithologicRealizationField realization =
                    SkyIslandLithologicRealizationField.create(descriptor);
            SkyIslandSemanticMaterialPaletteField palette =
                    SkyIslandSemanticMaterialPaletteField.create(descriptor);
            double radius = descriptor.nominalRadius();

            for (int iz = 0; iz < 11; iz++) {
                double z = -radius + iz * (2.0 * radius / 10.0);
                for (int ix = 0; ix < 11; ix++) {
                    double x = -radius + ix * (2.0 * radius / 10.0);
                    SkyIslandSubsurfacePosition position =
                            new SkyIslandSubsurfacePosition(x, z, 0.52);
                    SkyIslandLithologicRealizationSample source =
                            realization.sample(position);
                    SkyIslandSemanticMaterialPaletteSelection selection =
                            palette.sample(position);
                    if (!source.materialPresent()) {
                        continue;
                    }

                    if (selection.roleEligible(
                            SkyIslandSemanticMaterialPaletteRole.SECONDARY_MATRIX)) {
                        double weak = Math.min(source.massiveMatrix(), source.fabricRichMatrix());
                        double strong = Math.max(source.massiveMatrix(), source.fabricRichMatrix());
                        assertTrue(weak >=
                                SkyIslandSemanticMaterialPaletteField.SECONDARY_MATRIX_MIN_SUPPORT);
                        assertTrue(weak / strong >=
                                SkyIslandSemanticMaterialPaletteField.SECONDARY_MATRIX_MIN_RATIO);
                    }

                    assertSupportGate(
                            selection,
                            SkyIslandSemanticMaterialPaletteRole.ALTERATION_OVERPRINT,
                            source.alterationOverprint(),
                            SkyIslandSemanticMaterialPaletteField.ALTERATION_MIN_SUPPORT);
                    assertSupportGate(
                            selection,
                            SkyIslandSemanticMaterialPaletteRole.HYDROLOGIC_CONDITIONING,
                            source.waterConditioning(),
                            SkyIslandSemanticMaterialPaletteField.HYDROLOGIC_MIN_SUPPORT);
                    assertSupportGate(
                            selection,
                            SkyIslandSemanticMaterialPaletteRole.MINERAL_BEARING_STRUCTURE,
                            source.mineralBearingStructure(),
                            SkyIslandSemanticMaterialPaletteField.MINERAL_MIN_SUPPORT);
                }
            }
        }
    }

    @Test
    void optionalExpressionCeilingsCannotReplacePrimaryMatrixWholesale() {
        for (long key : new long[] {653L, 1051L, 2211L, 1439L, 3670L}) {
            SkyIslandDescriptor descriptor = descriptor(key);
            SkyIslandSemanticMaterialPaletteField field =
                    SkyIslandSemanticMaterialPaletteField.create(descriptor);
            double radius = descriptor.nominalRadius();

            for (int iz = 0; iz < 9; iz++) {
                double z = -radius + iz * (2.0 * radius / 8.0);
                for (int ix = 0; ix < 9; ix++) {
                    double x = -radius + ix * (2.0 * radius / 8.0);
                    SkyIslandSemanticMaterialPaletteSelection selection =
                            field.sample(new SkyIslandSubsurfacePosition(x, z, 0.52));
                    for (SkyIslandSemanticMaterialPaletteCandidate candidate :
                            selection.candidates()) {
                        if (candidate.role()
                                == SkyIslandSemanticMaterialPaletteRole.PRIMARY_MATRIX) {
                            continue;
                        }
                        assertFalse(candidate.required());
                        assertTrue(candidate.expressionCeiling() < 0.60);
                    }
                }
            }
        }
    }

    @Test
    void canonicalRepresentativesRetainDistinctRoleEligibilitySignatures() {
        Set<String> signatures = new HashSet<>();
        for (long key : new long[] {2332L, 653L, 1051L, 2211L, 1439L, 3670L}) {
            SkyIslandDescriptor descriptor = descriptor(key);
            SkyIslandSemanticMaterialPaletteField field =
                    SkyIslandSemanticMaterialPaletteField.create(descriptor);
            int[] counts = new int[SkyIslandSemanticMaterialPaletteRole.values().length];
            int material = 0;
            double radius = descriptor.nominalRadius();

            for (int iz = 0; iz < 9; iz++) {
                double z = -radius + iz * (2.0 * radius / 8.0);
                for (int ix = 0; ix < 9; ix++) {
                    double x = -radius + ix * (2.0 * radius / 8.0);
                    SkyIslandSemanticMaterialPaletteSelection selection =
                            field.sample(new SkyIslandSubsurfacePosition(x, z, 0.52));
                    if (!selection.materialPresent()) {
                        continue;
                    }
                    material++;
                    for (SkyIslandSemanticMaterialPaletteCandidate candidate :
                            selection.candidates()) {
                        counts[candidate.role().ordinal()]++;
                    }
                }
            }

            StringBuilder signature = new StringBuilder().append(material);
            for (int count : counts) {
                signature.append(':').append(count);
            }
            signatures.add(signature.toString());
        }
        assertTrue(signatures.size() > 3);
    }

    private static void assertSupportGate(
            SkyIslandSemanticMaterialPaletteSelection selection,
            SkyIslandSemanticMaterialPaletteRole role,
            double support,
            double threshold) {
        if (selection.roleEligible(role)) {
            assertTrue(support >= threshold);
        } else {
            assertTrue(support < threshold);
        }
    }

    private static SkyIslandDescriptor descriptor(long key) {
        return SkyIslandDescriptorGenerator.derive(
                SkyIslandIdentity.of(SEED, 8L, 81L, key));
    }
}
