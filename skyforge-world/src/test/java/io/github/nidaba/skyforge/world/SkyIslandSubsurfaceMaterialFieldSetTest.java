package io.github.nidaba.skyforge.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandIdentity;
import org.junit.jupiter.api.Test;

class SkyIslandSubsurfaceMaterialFieldSetTest {
    private static final long SEED = 0x534B59464F524745L;

    @Test
    void repeatedSamplingIsDeterministicAndNormalized() {
        SkyIslandSubsurfaceMaterialFieldSet material =
                SkyIslandSubsurfaceMaterialFieldSet.create(descriptor(91L, 7L, 71L));
        SkyIslandSubsurfacePosition position =
                new SkyIslandSubsurfacePosition(12.5, -18.0, 0.47);

        SkyIslandSubsurfaceMaterialSample first = material.sample(position);
        SkyIslandSubsurfaceMaterialSample second = material.sample(position);

        assertEquals(first, second);
        if (first.materialPresent()) {
            assertNormalized(first.matrixIntegrity());
            assertNormalized(first.alteration());
            assertNormalized(first.saturation());
            assertNormalized(first.mineralizationTendency());
            assertNormalized(first.caveWallAlteration());
        }
    }

    @Test
    void outsideNaturalizedOwnershipHasNoMaterialMeaning() {
        SkyIslandDescriptor descriptor = descriptor(7L, 7L, 71L);
        SkyIslandSubsurfaceMaterialFieldSet material =
                SkyIslandSubsurfaceMaterialFieldSet.create(descriptor);
        SkyIslandNaturalizedDomainField domain =
                SkyIslandNaturalizedDomainField.create(descriptor);

        double angle = Math.PI / 2.0;
        double radius = domain.boundaryRadius(angle) * 1.03;
        SkyIslandSubsurfaceMaterialSample sample = material.sample(
                new SkyIslandSubsurfacePosition(
                        radius * Math.cos(angle),
                        radius * Math.sin(angle),
                        0.4));

        assertEquals(SkyIslandSubsurfaceMaterialSample.outside(), sample);
        assertFalse(sample.owned());
        assertFalse(sample.materialPresent());
    }

    @Test
    void authoredCaveVoidContainsNoMaterial() {
        SkyIslandDescriptor descriptor = descriptor(653L, 8L, 81L);
        SkyIslandSubsurfaceMaterialFieldSet material =
                SkyIslandSubsurfaceMaterialFieldSet.create(descriptor);
        SkyIslandCaveGeometryPlan geometry = material.caveField().baseField().geometry();
        SkyIslandCaveChamberGeometry chamber =
                geometry.systems().getFirst().chambers().getFirst();

        SkyIslandSubsurfaceMaterialSample sample = material.sample(chamber.center());

        assertTrue(sample.owned());
        assertFalse(sample.materialPresent());
        assertEquals(SkyIslandSubsurfaceMaterialSample.authoredVoid(), sample);
    }

    @Test
    void caveBearingIslandsProduceSolidWallAlterationButCaveFreeControlDoesNot() {
        double caveWallMax = maximumWallAlteration(descriptor(653L, 8L, 81L));
        double controlWallMax = maximumWallAlteration(descriptor(2332L, 8L, 81L));

        assertTrue(caveWallMax > 0.05);
        assertEquals(0.0, controlWallMax, 0.0);
    }

    @Test
    void rockCompetenceRemainsVisibleInMaterialIntegrity() {
        SkyIslandDescriptor low = descriptorWithExtreme(
                7L, 71L, false, true);
        SkyIslandDescriptor high = descriptorWithExtreme(
                7L, 71L, true, true);

        double lowMean = meanMaterialMetric(
                low,
                SkyIslandSubsurfaceMaterialSample::matrixIntegrity);
        double highMean = meanMaterialMetric(
                high,
                SkyIslandSubsurfaceMaterialSample::matrixIntegrity);

        assertTrue(highMean > lowMean);
    }

    @Test
    void hydrologicalPotentialRemainsVisibleInMaterialSaturation() {
        SkyIslandDescriptor low = descriptorWithExtreme(
                7L, 71L, false, false);
        SkyIslandDescriptor high = descriptorWithExtreme(
                7L, 71L, true, false);

        double lowMean = meanMaterialMetric(
                low,
                SkyIslandSubsurfaceMaterialSample::saturation);
        double highMean = meanMaterialMetric(
                high,
                SkyIslandSubsurfaceMaterialSample::saturation);

        assertTrue(highMean > lowMean);
    }

    @Test
    void identityAndDepthChangeMaterialCharacterWithoutChangingVocabulary() {
        SkyIslandSubsurfaceMaterialFieldSet first =
                SkyIslandSubsurfaceMaterialFieldSet.create(descriptor(44L, 7L, 71L));
        SkyIslandSubsurfaceMaterialFieldSet second =
                SkyIslandSubsurfaceMaterialFieldSet.create(descriptor(45L, 7L, 71L));

        SkyIslandSubsurfaceMaterialSample shallow =
                first.sample(new SkyIslandSubsurfacePosition(0.0, 0.0, 0.10));
        SkyIslandSubsurfaceMaterialSample deep =
                first.sample(new SkyIslandSubsurfacePosition(0.0, 0.0, 0.82));
        SkyIslandSubsurfaceMaterialSample other =
                second.sample(new SkyIslandSubsurfacePosition(0.0, 0.0, 0.10));

        assertNotEquals(shallow, deep);
        assertNotEquals(shallow, other);
    }

    private static double maximumWallAlteration(SkyIslandDescriptor descriptor) {
        SkyIslandSubsurfaceMaterialFieldSet material =
                SkyIslandSubsurfaceMaterialFieldSet.create(descriptor);
        double radius = descriptor.nominalRadius();
        double maximum = 0.0;
        for (int iz = 0; iz <= 36; iz++) {
            double z = -radius + 2.0 * radius * iz / 36.0;
            for (int ix = 0; ix <= 36; ix++) {
                double x = -radius + 2.0 * radius * ix / 36.0;
                for (int id = 0; id <= 24; id++) {
                    SkyIslandSubsurfaceMaterialSample sample = material.sample(
                            new SkyIslandSubsurfacePosition(x, z, id / 24.0));
                    if (sample.materialPresent()) {
                        maximum = Math.max(maximum, sample.caveWallAlteration());
                    }
                }
            }
        }
        return maximum;
    }

    private static double meanMaterialMetric(
            SkyIslandDescriptor descriptor,
            Metric metric) {
        SkyIslandSubsurfaceMaterialFieldSet material =
                SkyIslandSubsurfaceMaterialFieldSet.create(descriptor);
        double radius = descriptor.nominalRadius();
        double sum = 0.0;
        long count = 0L;
        for (int iz = 0; iz <= 14; iz++) {
            double z = -radius + 2.0 * radius * iz / 14.0;
            for (int ix = 0; ix <= 14; ix++) {
                double x = -radius + 2.0 * radius * ix / 14.0;
                for (int id = 0; id <= 8; id++) {
                    SkyIslandSubsurfaceMaterialSample sample = material.sample(
                            new SkyIslandSubsurfacePosition(x, z, id / 8.0));
                    if (!sample.materialPresent()) {
                        continue;
                    }
                    sum += metric.value(sample);
                    count++;
                }
            }
        }
        if (count == 0L) {
            throw new IllegalStateException("material corpus had no present samples");
        }
        return sum / count;
    }

    private static SkyIslandDescriptor descriptorWithExtreme(
            long province,
            long cluster,
            boolean maximum,
            boolean competence) {
        SkyIslandDescriptor best = descriptor(0L, province, cluster);
        double bestValue = competence ? best.rockCompetence() : best.hydrologicalPotential();
        for (long key = 1; key < 512; key++) {
            SkyIslandDescriptor candidate = descriptor(key, province, cluster);
            double value = competence
                    ? candidate.rockCompetence()
                    : candidate.hydrologicalPotential();
            if ((maximum && value > bestValue) || (!maximum && value < bestValue)) {
                best = candidate;
                bestValue = value;
            }
        }
        return best;
    }

    private static SkyIslandDescriptor descriptor(
            long key,
            long province,
            long cluster) {
        return SkyIslandDescriptorGenerator.derive(
                SkyIslandIdentity.of(SEED, province, cluster, key));
    }

    private static void assertNormalized(double value) {
        assertTrue(Double.isFinite(value));
        assertTrue(value >= 0.0 && value <= 1.0);
    }

    @FunctionalInterface
    private interface Metric {
        double value(SkyIslandSubsurfaceMaterialSample sample);
    }
}
