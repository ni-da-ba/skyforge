package io.github.nidaba.skyforge.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandIdentity;
import org.junit.jupiter.api.Test;

final class SkyIslandCommunitySuitabilityFieldSetTest {
    @Test
    void repeatedSamplingIsDeterministicNormalizedAndIndependentlyConsumable() {
        SkyIslandDescriptor descriptor = SkyIslandDescriptorGenerator.derive(
                SkyIslandIdentity.of(1198L, 5L, 23L, 4L));
        SkyIslandCommunitySuitabilityFieldSet communities =
                SkyIslandCommunitySuitabilityFieldSet.create(descriptor);
        SkyIslandLocalPosition position = new SkyIslandLocalPosition(
                descriptor.nominalRadius() * 0.14,
                descriptor.nominalRadius() * -0.19);

        SkyIslandCommunitySuitabilitySample first = communities.sample(position);
        assertEquals(first, communities.sample(position));
        assertNormalized(first.closedWoodland());
        assertNormalized(first.openHerbaceous());
        assertNormalized(first.saturatedWetland());
        assertNormalized(first.alpineTundra());
        assertNormalized(first.xericScrub());

        assertBits(first.closedWoodland(), communities.closedWoodland().sample(position));
        assertBits(first.openHerbaceous(), communities.openHerbaceous().sample(position));
        assertBits(first.saturatedWetland(), communities.saturatedWetland().sample(position));
        assertBits(first.alpineTundra(), communities.alpineTundra().sample(position));
        assertBits(first.xericScrub(), communities.xericScrub().sample(position));
    }

    @Test
    void outsideAuthoredDomainHasNoCommunitySuitability() {
        SkyIslandDescriptor descriptor = SkyIslandDescriptorGenerator.derive(
                SkyIslandIdentity.of(1198L, 5L, 23L, 5L));
        SkyIslandCommunitySuitabilityFieldSet communities =
                SkyIslandCommunitySuitabilityFieldSet.create(descriptor);

        assertEquals(
                SkyIslandCommunitySuitabilitySample.outside(),
                communities.sample(new SkyIslandLocalPosition(
                        descriptor.nominalRadius() * 1.25,
                        0.0)));
    }

    @Test
    void communitySuitabilitiesOverlapRatherThanSelectOneWinner() {
        boolean foundOverlap = false;
        for (long islandKey = 0L; islandKey < 96L && !foundOverlap; islandKey++) {
            SkyIslandDescriptor descriptor = SkyIslandDescriptorGenerator.derive(
                    SkyIslandIdentity.of(0x534B59464F524745L, 13L, 67L, islandKey));
            SkyIslandCommunitySuitabilityFieldSet communities =
                    SkyIslandCommunitySuitabilityFieldSet.create(descriptor);
            double radius = descriptor.nominalRadius();

            for (int z = -4; z <= 4 && !foundOverlap; z++) {
                for (int x = -4; x <= 4; x++) {
                    SkyIslandCommunitySuitabilitySample sample = communities.sample(
                            new SkyIslandLocalPosition(radius * x / 5.0, radius * z / 5.0));
                    int materiallySuitable = 0;
                    materiallySuitable += sample.closedWoodland() >= 0.18 ? 1 : 0;
                    materiallySuitable += sample.openHerbaceous() >= 0.18 ? 1 : 0;
                    materiallySuitable += sample.saturatedWetland() >= 0.18 ? 1 : 0;
                    materiallySuitable += sample.alpineTundra() >= 0.18 ? 1 : 0;
                    materiallySuitable += sample.xericScrub() >= 0.18 ? 1 : 0;
                    if (materiallySuitable >= 2) {
                        foundOverlap = true;
                        break;
                    }
                }
            }
        }

        assertTrue(foundOverlap, "expected deterministic overlap among community suitability fields");
    }

    @Test
    void shelteredWetConditionsFavorClosedWoodlandOverXericScrub() {
        SkyIslandDescriptor descriptor = SkyIslandDescriptorGenerator.derive(
                SkyIslandIdentity.of(1198L, 5L, 23L, 6L));
        SkyIslandCommunitySuitabilitySample sample =
                SkyIslandCommunitySuitabilityFieldSet.create(
                                inputs(descriptor, 0.82, 0.28, 0.08, 0.75, 0.82))
                        .sample(new SkyIslandLocalPosition(0.0, 0.0));

        assertTrue(sample.closedWoodland() > sample.xericScrub());
    }

    @Test
    void dryExposedConditionsFavorXericScrubOverClosedWoodland() {
        SkyIslandDescriptor descriptor = SkyIslandDescriptorGenerator.derive(
                SkyIslandIdentity.of(1198L, 5L, 23L, 7L));
        SkyIslandCommunitySuitabilitySample sample =
                SkyIslandCommunitySuitabilityFieldSet.create(
                                inputs(descriptor, 0.10, 0.34, 0.92, 0.20, 0.72))
                        .sample(new SkyIslandLocalPosition(0.0, 0.0));

        assertTrue(sample.xericScrub() > sample.closedWoodland());
    }

    @Test
    void wetLowlandConditionsFavorSaturatedWetlandOverAlpineTundra() {
        SkyIslandDescriptor descriptor = SkyIslandDescriptorGenerator.derive(
                SkyIslandIdentity.of(1198L, 5L, 23L, 8L));
        SkyIslandCommunitySuitabilitySample sample =
                SkyIslandCommunitySuitabilityFieldSet.create(
                                inputs(descriptor, 0.90, 0.12, 0.15, 0.92, 0.75))
                        .sample(new SkyIslandLocalPosition(0.0, 0.0));

        assertTrue(sample.saturatedWetland() > sample.alpineTundra());
    }

    @Test
    void highExposedConditionsFavorAlpineTundraOverSaturatedWetland() {
        SkyIslandDescriptor descriptor = SkyIslandDescriptorGenerator.derive(
                SkyIslandIdentity.of(1198L, 5L, 23L, 9L));
        SkyIslandCommunitySuitabilitySample sample =
                SkyIslandCommunitySuitabilityFieldSet.create(
                                inputs(descriptor, 0.45, 0.92, 0.88, 0.20, 0.60))
                        .sample(new SkyIslandLocalPosition(0.0, 0.0));

        assertTrue(sample.alpineTundra() > sample.saturatedWetland());
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

    private static void assertBits(double expected, double actual) {
        assertEquals(Double.doubleToLongBits(expected), Double.doubleToLongBits(actual));
    }

    private static void assertNormalized(double value) {
        assertTrue(value >= 0.0 && value <= 1.0);
    }
}
