package io.github.nidaba.skyforge.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptorJson;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandIdentity;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class SkyIslandDescriptorGeneratorTest {
    @Test
    void identicalStableIdentityProducesEqualDescriptorAndCanonicalEvidence() {
        SkyIslandIdentity identity = SkyIslandIdentity.of(
                0x1234_5678_9ABC_DEF0L,
                0x1111_2222_3333_4444L,
                0x5555_6666_7777_8888L,
                0x1357_9BDF_2468_ACE0L);

        SkyIslandDescriptor first = SkyIslandDescriptorGenerator.derive(identity);
        SkyIslandDescriptor second = SkyIslandDescriptorGenerator.derive(identity);
        SkyIslandDescriptorJson json = new SkyIslandDescriptorJson();

        assertEquals(first, second);
        assertEquals(json.writeString(first), json.writeString(second));
    }

    @Test
    void worldProvinceClusterAndIslandIdentityEachAffectAuthorship() {
        SkyIslandIdentity base = SkyIslandIdentity.of(10L, 20L, 30L, 40L);
        SkyIslandDescriptor baseDescriptor = SkyIslandDescriptorGenerator.derive(base);

        assertNotEquals(
                baseDescriptor,
                SkyIslandDescriptorGenerator.derive(SkyIslandIdentity.of(11L, 20L, 30L, 40L)));
        assertNotEquals(
                baseDescriptor,
                SkyIslandDescriptorGenerator.derive(SkyIslandIdentity.of(10L, 21L, 30L, 40L)));
        assertNotEquals(
                baseDescriptor,
                SkyIslandDescriptorGenerator.derive(SkyIslandIdentity.of(10L, 20L, 31L, 40L)));
        assertNotEquals(
                baseDescriptor,
                SkyIslandDescriptorGenerator.derive(SkyIslandIdentity.of(10L, 20L, 30L, 41L)));
    }

    @Test
    void representativeIdentityCorpusDifferentiatesAuthorshipSeeds() {
        Set<Long> authorshipSeeds = new HashSet<>();
        for (long islandKey = 0L; islandKey < 512L; islandKey++) {
            SkyIslandIdentity identity = SkyIslandIdentity.of(99L, 7L, 13L, islandKey);
            long authorshipSeed = SkyIslandDescriptorGenerator.derive(identity).authorshipSeed();
            assertTrue(authorshipSeeds.add(authorshipSeed));
        }
    }

    @Test
    void generatedDescriptorsRemainInsideDeclaredPolicyRanges() {
        for (long sample = 0L; sample < 1024L; sample++) {
            SkyIslandIdentity identity = SkyIslandIdentity.of(
                    sample * 0x9E37L,
                    sample - 511L,
                    sample * 17L + 3L,
                    ~sample);
            SkyIslandDescriptor descriptor = SkyIslandDescriptorGenerator.derive(identity);

            assertTrue(descriptor.nominalRadius() >= SkyIslandDescriptorGenerator.MIN_NOMINAL_RADIUS);
            assertTrue(descriptor.nominalRadius() <= SkyIslandDescriptorGenerator.MAX_NOMINAL_RADIUS);
            assertTrue(descriptor.reliefBudget() >= SkyIslandDescriptorGenerator.MIN_RELIEF_BUDGET);
            assertTrue(descriptor.reliefBudget() <= SkyIslandDescriptorGenerator.MAX_RELIEF_BUDGET);
            assertNormalized(descriptor.rockCompetence());
            assertNormalized(descriptor.permeability());
            assertNormalized(descriptor.temperatureTendency());
            assertNormalized(descriptor.moistureTendency());
            assertNormalized(descriptor.exposureTendency());
            assertNormalized(descriptor.erosionMaturity());
            assertNormalized(descriptor.hydrologicalPotential());
            assertNormalized(descriptor.ecologicalPotential());
        }
    }

    @Test
    void hydrologyAndEcologyAreDerivedFromSemanticCausesRatherThanIndependentNoise() {
        SkyIslandDescriptor descriptor = SkyIslandDescriptorGenerator.derive(
                SkyIslandIdentity.of(123L, 456L, 789L, 101112L));

        double reliefControl = (descriptor.reliefBudget() - SkyIslandDescriptorGenerator.MIN_RELIEF_BUDGET)
                / (SkyIslandDescriptorGenerator.MAX_RELIEF_BUDGET
                        - SkyIslandDescriptorGenerator.MIN_RELIEF_BUDGET);
        double expectedHydrology = clamp01(
                0.52 * descriptor.moistureTendency()
                        + 0.23 * (1.0 - descriptor.permeability())
                        + 0.15 * reliefControl
                        + 0.10 * (1.0 - descriptor.exposureTendency()));
        double temperatureSuitability = clamp01(
                1.0 - Math.abs(descriptor.temperatureTendency() - 0.55) / 0.55);
        double expectedEcology = clamp01(
                0.46 * descriptor.moistureTendency()
                        + 0.32 * temperatureSuitability
                        + 0.14 * (1.0 - descriptor.exposureTendency())
                        + 0.08 * (1.0 - descriptor.erosionMaturity()));

        assertEquals(expectedHydrology, descriptor.hydrologicalPotential());
        assertEquals(expectedEcology, descriptor.ecologicalPotential());
    }

    private static void assertNormalized(double value) {
        assertTrue(Double.isFinite(value));
        assertTrue(value >= 0.0 && value <= 1.0);
    }

    private static double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }
}
