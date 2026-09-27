package io.github.nidaba.skyforge.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandIdentity;
import org.junit.jupiter.api.Test;

final class SkyIslandHabitatOpportunityFieldSetTest {
    @Test
    void repeatedSamplingIsDeterministicNormalizedAndIndependentlyConsumable() {
        SkyIslandDescriptor descriptor = SkyIslandDescriptorGenerator.derive(
                SkyIslandIdentity.of(1196L, 3L, 17L, 4L));
        SkyIslandHabitatOpportunityFieldSet habitats =
                SkyIslandHabitatOpportunityFieldSet.create(descriptor);
        SkyIslandLocalPosition position = new SkyIslandLocalPosition(
                descriptor.nominalRadius() * 0.16,
                descriptor.nominalRadius() * -0.23);

        SkyIslandHabitatOpportunitySample first = habitats.sample(position);
        SkyIslandHabitatOpportunitySample second = habitats.sample(position);
        assertEquals(first, second);
        assertNormalized(first.woodland());
        assertNormalized(first.openVegetation());
        assertNormalized(first.saturatedLowland());
        assertNormalized(first.alpineExposed());
        assertNormalized(first.xericExposed());

        assertEquals(
                Double.doubleToLongBits(first.woodland()),
                Double.doubleToLongBits(habitats.woodland().sample(position)));
        assertEquals(
                Double.doubleToLongBits(first.openVegetation()),
                Double.doubleToLongBits(habitats.openVegetation().sample(position)));
        assertEquals(
                Double.doubleToLongBits(first.saturatedLowland()),
                Double.doubleToLongBits(habitats.saturatedLowland().sample(position)));
        assertEquals(
                Double.doubleToLongBits(first.alpineExposed()),
                Double.doubleToLongBits(habitats.alpineExposed().sample(position)));
        assertEquals(
                Double.doubleToLongBits(first.xericExposed()),
                Double.doubleToLongBits(habitats.xericExposed().sample(position)));
    }

    @Test
    void outsideAuthoredDomainHasNoHabitatOpportunity() {
        SkyIslandDescriptor descriptor = SkyIslandDescriptorGenerator.derive(
                SkyIslandIdentity.of(1196L, 3L, 17L, 5L));
        SkyIslandHabitatOpportunityFieldSet habitats =
                SkyIslandHabitatOpportunityFieldSet.create(descriptor);

        assertEquals(
                SkyIslandHabitatOpportunitySample.outside(),
                habitats.sample(new SkyIslandLocalPosition(
                        descriptor.nominalRadius() * 1.25,
                        0.0)));
    }

    @Test
    void habitatOpportunitiesOverlapRatherThanClassifyWinnerTakeAll() {
        boolean foundOverlap = false;
        for (long islandKey = 0L; islandKey < 96L && !foundOverlap; islandKey++) {
            SkyIslandDescriptor descriptor = SkyIslandDescriptorGenerator.derive(
                    SkyIslandIdentity.of(0x534B59464F524745L, 11L, 61L, islandKey));
            SkyIslandHabitatOpportunityFieldSet habitats =
                    SkyIslandHabitatOpportunityFieldSet.create(descriptor);
            double radius = descriptor.nominalRadius();

            for (int z = -4; z <= 4 && !foundOverlap; z++) {
                for (int x = -4; x <= 4; x++) {
                    SkyIslandHabitatOpportunitySample sample = habitats.sample(
                            new SkyIslandLocalPosition(radius * x / 5.0, radius * z / 5.0));
                    int materiallySuitable = 0;
                    materiallySuitable += sample.woodland() >= 0.25 ? 1 : 0;
                    materiallySuitable += sample.openVegetation() >= 0.25 ? 1 : 0;
                    materiallySuitable += sample.saturatedLowland() >= 0.25 ? 1 : 0;
                    materiallySuitable += sample.alpineExposed() >= 0.25 ? 1 : 0;
                    materiallySuitable += sample.xericExposed() >= 0.25 ? 1 : 0;
                    if (materiallySuitable >= 2) {
                        foundOverlap = true;
                        break;
                    }
                }
            }
        }
        assertTrue(foundOverlap, "expected at least one deterministic multi-opportunity habitat sample");
    }

    @Test
    void wetterLowlandInputsIncreaseSaturatedLowlandOpportunity() {
        SkyIslandDescriptor descriptor = SkyIslandDescriptorGenerator.derive(
                SkyIslandIdentity.of(1196L, 3L, 17L, 6L));
        SkyIslandLocalPosition origin = new SkyIslandLocalPosition(0.0, 0.0);

        SkyIslandHabitatOpportunitySample dry = SkyIslandHabitatOpportunityFieldSet.create(
                        inputs(descriptor, 0.20, 0.18, 0.20, 0.55, 0.70))
                .sample(origin);
        SkyIslandHabitatOpportunitySample wet = SkyIslandHabitatOpportunityFieldSet.create(
                        inputs(descriptor, 0.85, 0.18, 0.20, 0.90, 0.70))
                .sample(origin);

        assertTrue(wet.saturatedLowland() > dry.saturatedLowland());
        assertTrue(wet.woodland() > dry.woodland());
    }

    @Test
    void elevationAndExposureIncreaseAlpineExposedOpportunity() {
        SkyIslandDescriptor descriptor = SkyIslandDescriptorGenerator.derive(
                SkyIslandIdentity.of(1196L, 3L, 17L, 7L));
        SkyIslandLocalPosition origin = new SkyIslandLocalPosition(0.0, 0.0);

        SkyIslandHabitatOpportunitySample shelteredLowland = SkyIslandHabitatOpportunityFieldSet.create(
                        inputs(descriptor, 0.55, 0.20, 0.12, 0.55, 0.60))
                .sample(origin);
        SkyIslandHabitatOpportunitySample exposedHighland = SkyIslandHabitatOpportunityFieldSet.create(
                        inputs(descriptor, 0.55, 0.88, 0.86, 0.30, 0.60))
                .sample(origin);

        assertTrue(exposedHighland.alpineExposed() > shelteredLowland.alpineExposed());
    }

    @Test
    void drynessAndExposureIncreaseXericOpportunityAndReduceWoodland() {
        SkyIslandDescriptor descriptor = SkyIslandDescriptorGenerator.derive(
                SkyIslandIdentity.of(1196L, 3L, 17L, 8L));
        SkyIslandLocalPosition origin = new SkyIslandLocalPosition(0.0, 0.0);

        SkyIslandHabitatOpportunitySample shelteredWet = SkyIslandHabitatOpportunityFieldSet.create(
                        inputs(descriptor, 0.80, 0.35, 0.12, 0.58, 0.65))
                .sample(origin);
        SkyIslandHabitatOpportunitySample dryExposed = SkyIslandHabitatOpportunityFieldSet.create(
                        inputs(descriptor, 0.12, 0.35, 0.88, 0.58, 0.65))
                .sample(origin);

        assertTrue(dryExposed.xericExposed() > shelteredWet.xericExposed());
        assertTrue(dryExposed.woodland() < shelteredWet.woodland());
    }

    private static SkyIslandEcologyInputFieldSet inputs(
            SkyIslandDescriptor descriptor,
            double moisture,
            double elevation,
            double exposure,
            double hydrology,
            double ecology) {
        return new SkyIslandEcologyInputFieldSet(
                descriptor,
                constant(1.0),
                constant(0.58),
                constant(moisture),
                constant(elevation),
                constant(exposure),
                hydrology,
                ecology);
    }

    private static SkyIslandSemanticField constant(double value) {
        return ignored -> value;
    }

    private static void assertNormalized(double value) {
        assertTrue(value >= 0.0 && value <= 1.0);
    }
}
