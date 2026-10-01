package io.github.nidaba.skyforge.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandIdentity;
import org.junit.jupiter.api.Test;

class SkyIslandCaveVolumeFieldTest {
    private static final long SEED = 0x534B59464F524745L;

    @Test
    void fieldIsDeterministicAndCaveFreeControlsRemainEmpty() {
        for (long key : new long[] {2332L, 2211L}) {
            SkyIslandDescriptor descriptor = descriptor(key);
            SkyIslandCaveVolumeField first = SkyIslandCaveVolumeField.create(descriptor);
            SkyIslandCaveVolumeField second = SkyIslandCaveVolumeField.create(descriptor);

            assertEquals(first.geometry(), second.geometry());
            for (int iz = 0; iz <= 8; iz++) {
                double z = -descriptor.nominalRadius()
                        + 2.0 * descriptor.nominalRadius() * iz / 8.0;
                for (int ix = 0; ix <= 8; ix++) {
                    double x = -descriptor.nominalRadius()
                            + 2.0 * descriptor.nominalRadius() * ix / 8.0;
                    for (int id = 0; id <= 6; id++) {
                        SkyIslandSubsurfacePosition position =
                                new SkyIslandSubsurfacePosition(x, z, id / 6.0);
                        assertFalse(first.contains(position));
                        assertEquals(
                                first.sample(position),
                                second.sample(position));
                    }
                }
            }
        }
    }

    @Test
    void everyChamberCenterAndPassageCenterlineIsInsideCompiledField() {
        for (long key : new long[] {653L, 1051L, 1439L, 3670L}) {
            SkyIslandCaveVolumeField field = SkyIslandCaveVolumeField.create(descriptor(key));
            for (SkyIslandCaveSystemGeometry system : field.geometry().systems()) {
                for (SkyIslandCaveChamberGeometry chamber : system.chambers()) {
                    SkyIslandCaveVolumeSample sample = field.sample(chamber.center());
                    assertTrue(sample.inside());
                    assertTrue(sample.signedClearance() > 0.99);
                }
                for (SkyIslandCavePassageGeometry passage : system.passages()) {
                    for (SkyIslandCavePassagePoint point : passage.points()) {
                        assertTrue(field.contains(point.position()));
                    }
                    for (int index = 1; index < passage.points().size(); index++) {
                        SkyIslandSubsurfacePosition a = passage.points().get(index - 1).position();
                        SkyIslandSubsurfacePosition b = passage.points().get(index).position();
                        for (int step = 0; step <= 8; step++) {
                            double t = step / 8.0;
                            SkyIslandSubsurfacePosition position =
                                    new SkyIslandSubsurfacePosition(
                                            a.x() + (b.x() - a.x()) * t,
                                            a.z() + (b.z() - a.z()) * t,
                                            a.depthFraction()
                                                    + (b.depthFraction() - a.depthFraction()) * t);
                            assertTrue(field.contains(position));
                        }
                    }
                }
            }
        }
    }

    @Test
    void isolatedSingleChamberHasContinuousSignedBoundary() {
        SkyIslandCaveVolumeField field = SkyIslandCaveVolumeField.create(descriptor(1439L));
        assertEquals(1, field.geometry().chamberCount());
        assertEquals(0, field.geometry().passageCount());

        SkyIslandCaveChamberGeometry chamber =
                field.geometry().systems().getFirst().chambers().getFirst();
        SkyIslandSubsurfacePosition center = chamber.center();

        SkyIslandSubsurfacePosition boundary = new SkyIslandSubsurfacePosition(
                center.x() + chamber.horizontalRadius(),
                center.z(),
                center.depthFraction());
        SkyIslandSubsurfacePosition outside = new SkyIslandSubsurfacePosition(
                center.x() + chamber.horizontalRadius() * 1.05,
                center.z(),
                center.depthFraction());

        assertEquals(0.0, field.signedClearance(boundary), 1.0e-10);
        assertTrue(field.signedClearance(outside) < 0.0);
        assertFalse(field.contains(boundary));
        assertFalse(field.contains(outside));
    }

    @Test
    void outsideNaturalizedOwnershipCannotBecomeCaveVoid() {
        SkyIslandDescriptor descriptor = descriptor(3670L);
        SkyIslandCaveVolumeField field = SkyIslandCaveVolumeField.create(descriptor);
        SkyIslandNaturalizedDomainField domain = SkyIslandNaturalizedDomainField.create(descriptor);

        for (int i = 0; i < 24; i++) {
            double angle = 2.0 * Math.PI * i / 24.0;
            double radius = domain.boundaryRadius(angle) * 1.02;
            SkyIslandSubsurfacePosition position = new SkyIslandSubsurfacePosition(
                    radius * Math.cos(angle),
                    radius * Math.sin(angle),
                    0.5);
            SkyIslandCaveVolumeSample sample = field.sample(position);
            assertFalse(sample.inside());
            assertTrue(sample.signedClearance() <= 0.0);
        }
    }

    @Test
    void positiveSamplesCarryStablePrimitiveProvenance() {
        SkyIslandCaveVolumeField field = SkyIslandCaveVolumeField.create(descriptor(653L));
        for (SkyIslandCaveSystemGeometry system : field.geometry().systems()) {
            for (SkyIslandCaveChamberGeometry chamber : system.chambers()) {
                SkyIslandCaveVolumeSample sample = field.sample(chamber.center());
                assertEquals(system.systemId(), sample.systemId());
                assertEquals(SkyIslandCaveVolumeSample.PrimitiveKind.CHAMBER, sample.primitiveKind());
                assertEquals(chamber.nodeId(), sample.primitiveId());
            }
        }
    }

    private static SkyIslandDescriptor descriptor(long key) {
        return SkyIslandDescriptorGenerator.derive(
                SkyIslandIdentity.of(SEED, 8L, 81L, key));
    }
}
