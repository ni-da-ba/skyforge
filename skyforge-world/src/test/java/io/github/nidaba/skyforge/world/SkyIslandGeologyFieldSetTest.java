package io.github.nidaba.skyforge.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandIdentity;
import org.junit.jupiter.api.Test;

class SkyIslandGeologyFieldSetTest {
    private static final long SEED = 0x534B59464F524745L;

    @Test
    void repeatedSamplingIsDeterministicAndNormalized() {
        SkyIslandDescriptor descriptor = descriptor(91L);
        SkyIslandGeologyFieldSet geology = SkyIslandGeologyFieldSet.create(descriptor);
        SkyIslandSubsurfacePosition position = new SkyIslandSubsurfacePosition(12.5, -33.75, 0.43);

        SkyIslandGeologySample first = geology.sample(position);
        SkyIslandGeologySample second = geology.sample(position);

        assertEquals(first, second);
        assertTrue(first.owned());
        assertNormalized(first.bulkCompetence());
        assertNormalized(first.fractureIntensity());
        assertNormalized(first.connectedPermeability());
        assertNormalized(first.groundwaterPotential());
        assertNormalized(first.voidFormationPotential());
    }

    @Test
    void outsideCurrentNaturalizedDomainHasNoGeologicalOwnership() {
        SkyIslandDescriptor descriptor = descriptor(7L);
        SkyIslandGeologyFieldSet geology = SkyIslandGeologyFieldSet.create(descriptor);
        SkyIslandNaturalizedDomainField domain = SkyIslandNaturalizedDomainField.create(descriptor);

        double angle = Math.PI / 2.0;
        double radius = domain.boundaryRadius(angle) * 1.03;
        SkyIslandGeologySample sample = geology.sample(new SkyIslandSubsurfacePosition(
                radius * Math.cos(angle),
                radius * Math.sin(angle),
                0.4));

        assertFalse(sample.owned());
        assertEquals(SkyIslandGeologySample.outside(), sample);
    }

    @Test
    void depthAndIslandIdentityChangeSubsurfaceMeaning() {
        SkyIslandGeologyFieldSet first = SkyIslandGeologyFieldSet.create(descriptor(44L));
        SkyIslandGeologyFieldSet second = SkyIslandGeologyFieldSet.create(descriptor(45L));

        SkyIslandGeologySample shallow =
                first.sample(new SkyIslandSubsurfacePosition(0.0, 0.0, 0.08));
        SkyIslandGeologySample deep =
                first.sample(new SkyIslandSubsurfacePosition(0.0, 0.0, 0.82));
        SkyIslandGeologySample other =
                second.sample(new SkyIslandSubsurfacePosition(0.0, 0.0, 0.08));

        assertNotEquals(shallow, deep);
        assertNotEquals(shallow, other);
    }

    @Test
    void descriptorCommonCausesRemainVisibleAcrossRepresentativeCorpus() {
        SkyIslandDescriptor lowCompetence = descriptorWithMinimumRockCompetence();
        SkyIslandDescriptor highCompetence = descriptorWithMaximumRockCompetence();
        SkyIslandGeologySample low = SkyIslandGeologyFieldSet.create(lowCompetence)
                .sample(new SkyIslandSubsurfacePosition(0.0, 0.0, 0.5));
        SkyIslandGeologySample high = SkyIslandGeologyFieldSet.create(highCompetence)
                .sample(new SkyIslandSubsurfacePosition(0.0, 0.0, 0.5));

        assertTrue(high.bulkCompetence() > low.bulkCompetence());
    }

    private static SkyIslandDescriptor descriptorWithMinimumRockCompetence() {
        SkyIslandDescriptor best = descriptor(0L);
        for (long key = 1; key < 512; key++) {
            SkyIslandDescriptor candidate = descriptor(key);
            if (candidate.rockCompetence() < best.rockCompetence()) {
                best = candidate;
            }
        }
        return best;
    }

    private static SkyIslandDescriptor descriptorWithMaximumRockCompetence() {
        SkyIslandDescriptor best = descriptor(0L);
        for (long key = 1; key < 512; key++) {
            SkyIslandDescriptor candidate = descriptor(key);
            if (candidate.rockCompetence() > best.rockCompetence()) {
                best = candidate;
            }
        }
        return best;
    }

    private static SkyIslandDescriptor descriptor(long key) {
        return SkyIslandDescriptorGenerator.derive(
                SkyIslandIdentity.of(SEED, 7L, 71L, key));
    }

    private static void assertNormalized(double value) {
        assertTrue(Double.isFinite(value));
        assertTrue(value >= 0.0 && value <= 1.0);
    }
}
