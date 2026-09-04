package io.github.nidaba.skyforge.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandIdentity;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SkyIslandLithologicRealizationFieldTest {
    private static final long SEED = 0x534B59464F524745L;

    @Test
    void realizationSamplingIsDeterministic() {
        SkyIslandDescriptor descriptor = descriptor(2211L);
        SkyIslandLithologicRealizationField first =
                SkyIslandLithologicRealizationField.create(descriptor);
        SkyIslandLithologicRealizationField second =
                SkyIslandLithologicRealizationField.create(descriptor);
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
    void realizationPreservesAuth0031MaterialPresenceExactly() {
        for (long key : new long[] {653L, 1051L, 2211L, 1439L}) {
            SkyIslandDescriptor descriptor = descriptor(key);
            SkyIslandSubsurfaceMaterialFieldSet material =
                    SkyIslandSubsurfaceMaterialFieldSet.create(descriptor);
            SkyIslandLithologicRealizationField field =
                    SkyIslandLithologicRealizationField.create(descriptor);
            double radius = descriptor.nominalRadius();

            for (int iz = 0; iz < 13; iz++) {
                double z = -radius + iz * (2.0 * radius / 12.0);
                for (int id = 0; id < 7; id++) {
                    double depth = id / 6.0;
                    for (int ix = 0; ix < 13; ix++) {
                        double x = -radius + ix * (2.0 * radius / 12.0);
                        SkyIslandSubsurfacePosition position =
                                new SkyIslandSubsurfacePosition(x, z, depth);
                        SkyIslandSubsurfaceMaterialSample source = material.sample(position);
                        SkyIslandLithologicRealizationSample sample = field.sample(position);
                        assertEquals(source.owned(), sample.owned());
                        assertEquals(source.materialPresent(), sample.materialPresent());
                        if (sample.materialPresent()) {
                            assertTrue(Math.max(
                                            sample.massiveMatrix(),
                                            sample.fabricRichMatrix())
                                    > 0.0);
                        }
                    }
                }
            }
        }
    }

    @Test
    void nonContactPlanningCellsPreserveAuth0033CharacterExactly() {
        boolean observed = false;
        for (long key : new long[] {2332L, 653L, 1051L, 2211L, 1439L, 3670L}) {
            SkyIslandDescriptor descriptor = descriptor(key);
            SkyIslandLithologicRealizationField field =
                    SkyIslandLithologicRealizationField.create(descriptor);
            SkyIslandLithologicContactRealizationField contacts =
                    SkyIslandLithologicContactRealizationField.create(descriptor);

            for (SkyIslandLithologicAssemblageCell cell :
                    field.assemblagePlan().cells()) {
                SkyIslandLithologicContactRealizationSample contact =
                        contacts.sample(cell.position());
                if (contact.contactActive()) {
                    continue;
                }
                SkyIslandLithologicRealizationSample sample =
                        field.sample(cell.position());
                SkyIslandMaterialFamilyCell family = cell.familyCharacter();

                assertEquals(cell.assemblageId(), sample.localAssemblageId());
                assertEquals(cell.assemblageKind(), sample.localAssemblageKind());
                assertEquals(family.coherentMassiveHost(), sample.massiveMatrix());
                assertEquals(family.layeredFabricRichHost(), sample.fabricRichMatrix());
                assertEquals(family.stronglyAlteredHost(), sample.alterationOverprint());
                assertEquals(family.waterConditionedHost(), sample.waterConditioning());
                assertEquals(
                        family.mineralBearingStructuralHost(),
                        sample.mineralBearingStructure());
                observed = true;
                break;
            }
            if (observed) {
                break;
            }
        }
        assertTrue(observed);
    }

    @Test
    void contactSamplesRetainValidAuth0034ProvenanceAndNormalizedBlend() {
        boolean observed = false;
        for (long key : new long[] {653L, 1051L, 2211L, 1439L, 3670L}) {
            SkyIslandDescriptor descriptor = descriptor(key);
            SkyIslandLithologicRealizationField field =
                    SkyIslandLithologicRealizationField.create(descriptor);
            Set<Integer> validContacts = new HashSet<>();
            for (SkyIslandLithologicContact contact :
                    field.assemblagePlan().contacts()) {
                validContacts.add(contact.contactId());
            }

            for (SkyIslandLithologicContactRealization realization :
                    field.contactPlan().realizations()) {
                for (SkyIslandLithologicContactPatch patch : realization.patches()) {
                    SkyIslandLithologicRealizationSample sample =
                            field.sample(patch.center());
                    if (!sample.materialPresent() || !sample.contactActive()) {
                        continue;
                    }
                    assertTrue(validContacts.contains(sample.contactId()));
                    assertTrue(sample.firstAssemblageId() < sample.secondAssemblageId());
                    assertEquals(
                            1.0,
                            sample.firstAssemblageWeight()
                                    + sample.secondAssemblageWeight(),
                            1.0e-9);
                    for (SkyIslandLithologicRealizationChannel channel :
                            SkyIslandLithologicRealizationChannel.values()) {
                        assertTrue(sample.channel(channel) >= 0.0);
                        assertTrue(sample.channel(channel) <= 1.0);
                    }
                    observed = true;
                    break;
                }
                if (observed) {
                    break;
                }
            }
            if (observed) {
                break;
            }
        }
        assertTrue(observed);
    }

    @Test
    void canonicalRepresentativesRemainCompositionallyDistinct() {
        Set<String> signatures = new HashSet<>();
        for (long key : new long[] {2332L, 653L, 1051L, 2211L, 1439L, 3670L}) {
            SkyIslandDescriptor descriptor = descriptor(key);
            SkyIslandLithologicRealizationField field =
                    SkyIslandLithologicRealizationField.create(descriptor);
            double radius = descriptor.nominalRadius();
            double[] sums = new double[SkyIslandLithologicRealizationChannel.values().length];
            int materialCount = 0;
            int contactCount = 0;

            for (int iz = 0; iz < 9; iz++) {
                double z = -radius + iz * (2.0 * radius / 8.0);
                for (int ix = 0; ix < 9; ix++) {
                    double x = -radius + ix * (2.0 * radius / 8.0);
                    SkyIslandLithologicRealizationSample sample =
                            field.sample(new SkyIslandSubsurfacePosition(x, z, 0.52));
                    if (!sample.materialPresent()) {
                        continue;
                    }
                    materialCount++;
                    if (sample.contactActive()) {
                        contactCount++;
                    }
                    for (SkyIslandLithologicRealizationChannel channel :
                            SkyIslandLithologicRealizationChannel.values()) {
                        sums[channel.ordinal()] += sample.channel(channel);
                    }
                }
            }

            StringBuilder signature = new StringBuilder();
            signature.append(materialCount).append(':').append(contactCount);
            for (double sum : sums) {
                signature.append(':').append(Math.round(sum * 100.0));
            }
            signatures.add(signature.toString());
        }
        assertTrue(signatures.size() > 3);
    }

    private static SkyIslandDescriptor descriptor(long key) {
        return SkyIslandDescriptorGenerator.derive(
                SkyIslandIdentity.of(SEED, 8L, 81L, key));
    }
}
