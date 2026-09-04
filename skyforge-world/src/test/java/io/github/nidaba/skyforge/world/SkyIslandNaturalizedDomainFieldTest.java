package io.github.nidaba.skyforge.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandIdentity;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandMorphologyFamily;
import org.junit.jupiter.api.Test;

class SkyIslandNaturalizedDomainFieldTest {
    private static final long SEED = 0x534B59464F524745L;

    @Test
    void domainIsDeterministicConnectedBoundedAndTotal() {
        SkyIslandDescriptor descriptor = descriptor(10L);
        SkyIslandNaturalizedDomainField first = SkyIslandNaturalizedDomainField.create(descriptor);
        SkyIslandNaturalizedDomainField second = SkyIslandNaturalizedDomainField.create(descriptor);

        assertEquals(1.0, first.sample(new SkyIslandLocalPosition(0.0, 0.0)), 1.0e-12);
        assertEquals(0.0, first.sample(new SkyIslandLocalPosition(
                descriptor.nominalRadius() * 1.01, 0.0)), 1.0e-12);

        for (int i = 0; i < 1440; i++) {
            double angle = 2.0 * Math.PI * i / 1440.0;
            double a = first.boundaryRadius(angle);
            double b = second.boundaryRadius(angle);
            assertEquals(a, b, 0.0);
            assertTrue(a >= descriptor.nominalRadius() * 0.52 - 1.0e-12);
            assertTrue(a <= descriptor.nominalRadius() + 1.0e-12);

            double innerRadius = a * 0.69;
            double edgeRadius = a;
            SkyIslandLocalPosition inner =
                    new SkyIslandLocalPosition(innerRadius * Math.cos(angle), innerRadius * Math.sin(angle));
            SkyIslandLocalPosition edge =
                    new SkyIslandLocalPosition(edgeRadius * Math.cos(angle), edgeRadius * Math.sin(angle));
            assertEquals(1.0, first.sample(inner), 1.0e-12);
            assertEquals(0.0, first.sample(edge), 1.0e-10);
        }
    }

    @Test
    void builtInFamiliesRetainDistinctPrimaryFootprintIdentity() {
        SkyIslandDescriptor massif = descriptor(1L);
        SkyIslandDescriptor tableland = descriptor(4L);
        SkyIslandDescriptor spine = descriptor(7L);
        SkyIslandDescriptor lobed = descriptor(10L);
        SkyIslandDescriptor basin = descriptor(3L);

        assertEquals(SkyIslandMorphologyFamily.MASSIF, massif.morphologyFamily());
        assertEquals(SkyIslandMorphologyFamily.TABLELAND, tableland.morphologyFamily());
        assertEquals(SkyIslandMorphologyFamily.SPINE, spine.morphologyFamily());
        assertEquals(SkyIslandMorphologyFamily.LOBED, lobed.morphologyFamily());
        assertEquals(SkyIslandMorphologyFamily.BASIN, basin.morphologyFamily());

        SkyIslandNaturalizedDomainField spineDomain = SkyIslandNaturalizedDomainField.create(spine);
        assertTrue(
                spineDomain.boundaryRadius(0.0) / spineDomain.boundaryRadius(Math.PI / 2.0)
                        > 1.35);

        SkyIslandNaturalizedDomainField lobedDomain = SkyIslandNaturalizedDomainField.create(lobed);
        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < 720; i++) {
            double radius = lobedDomain.boundaryRadius(2.0 * Math.PI * i / 720.0);
            min = Math.min(min, radius);
            max = Math.max(max, radius);
        }
        assertTrue((max - min) / lobed.nominalRadius() > 0.08);

        assertTrue(axisRatio(tableland) < 1.20);
        assertTrue(axisRatio(basin) < 1.18);
        assertTrue(axisRatio(massif) < 1.22);
    }

    @Test
    void islandIdentityChangesNaturalizedBoundaryDetail() {
        SkyIslandNaturalizedDomainField first =
                SkyIslandNaturalizedDomainField.create(descriptor(14L));
        SkyIslandNaturalizedDomainField second =
                SkyIslandNaturalizedDomainField.create(descriptor(17L));

        assertNotEquals(
                first.boundaryRadius(0.37),
                second.boundaryRadius(0.37));
    }

    private static double axisRatio(SkyIslandDescriptor descriptor) {
        SkyIslandNaturalizedDomainField domain = SkyIslandNaturalizedDomainField.create(descriptor);
        double x = domain.boundaryRadius(0.0);
        double z = domain.boundaryRadius(Math.PI / 2.0);
        return Math.max(x, z) / Math.min(x, z);
    }

    private static SkyIslandDescriptor descriptor(long key) {
        return SkyIslandDescriptorGenerator.derive(SkyIslandIdentity.of(SEED, 6L, 61L, key));
    }
}
