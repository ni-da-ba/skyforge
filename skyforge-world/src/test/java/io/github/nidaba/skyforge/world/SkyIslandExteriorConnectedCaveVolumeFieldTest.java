package io.github.nidaba.skyforge.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandIdentity;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class SkyIslandExteriorConnectedCaveVolumeFieldTest {
    private static final long SEED = 0x534B59464F524745L;

    @Test
    void fieldIsDeterministic() {
        for (long key : new long[] {653L, 3670L, 1051L, 1439L, 2332L, 2211L}) {
            SkyIslandExteriorConnectedCaveVolumeField first =
                    SkyIslandExteriorConnectedCaveVolumeField.create(descriptor(key));
            SkyIslandExteriorConnectedCaveVolumeField second =
                    SkyIslandExteriorConnectedCaveVolumeField.create(descriptor(key));

            assertEquals(first.exposureGeometry(), second.exposureGeometry());
            for (int iz = 0; iz <= 8; iz++) {
                double z = -first.descriptor().nominalRadius()
                        + 2.0 * first.descriptor().nominalRadius() * iz / 8.0;
                for (int ix = 0; ix <= 8; ix++) {
                    double x = -first.descriptor().nominalRadius()
                            + 2.0 * first.descriptor().nominalRadius() * ix / 8.0;
                    for (int id = 0; id <= 6; id++) {
                        SkyIslandSubsurfacePosition position =
                                new SkyIslandSubsurfacePosition(x, z, id / 6.0);
                        assertEquals(first.sample(position), second.sample(position));
                    }
                }
            }
        }
    }

    @Test
    void sealedSystemsRemainExactlyAuth0026AtAllSampledPoints() {
        for (long key : new long[] {2332L, 2211L, 1051L, 1439L}) {
            SkyIslandDescriptor descriptor = descriptor(key);
            SkyIslandCaveVolumeField base = SkyIslandCaveVolumeField.create(descriptor);
            SkyIslandExteriorConnectedCaveVolumeField connected =
                    SkyIslandExteriorConnectedCaveVolumeField.create(descriptor);

            assertTrue(connected.exposureGeometry().connections().isEmpty());

            for (int iz = 0; iz <= 12; iz++) {
                double z = -descriptor.nominalRadius()
                        + 2.0 * descriptor.nominalRadius() * iz / 12.0;
                for (int ix = 0; ix <= 12; ix++) {
                    double x = -descriptor.nominalRadius()
                            + 2.0 * descriptor.nominalRadius() * ix / 12.0;
                    for (int id = 0; id <= 8; id++) {
                        SkyIslandSubsurfacePosition position =
                                new SkyIslandSubsurfacePosition(x, z, id / 8.0);
                        SkyIslandCaveVolumeSample baseSample = base.sample(position);
                        SkyIslandExteriorConnectedCaveVolumeSample connectedSample =
                                connected.sample(position);

                        assertEquals(
                                Double.doubleToLongBits(baseSample.signedClearance()),
                                Double.doubleToLongBits(connectedSample.signedClearance()));
                        assertEquals(baseSample.inside(), connectedSample.inside());
                        if (baseSample.primitiveKind()
                                == SkyIslandCaveVolumeSample.PrimitiveKind.NONE) {
                            assertEquals(
                                    SkyIslandExteriorConnectedCaveVolumeSample.SourceKind.NONE,
                                    connectedSample.sourceKind());
                        } else {
                            assertEquals(
                                    SkyIslandExteriorConnectedCaveVolumeSample.SourceKind.BASE_CAVE,
                                    connectedSample.sourceKind());
                            assertEquals(baseSample.systemId(), connectedSample.systemId());
                            assertEquals(baseSample.primitiveKind(), connectedSample.sourcePrimitiveKind());
                            assertEquals(baseSample.primitiveId(), connectedSample.sourcePrimitiveId());
                        }
                    }
                }
            }
        }
    }

    @Test
    void everyAcceptedConnectionIsPositiveContinuouslyAndReachesItsBoundary() {
        for (long key : new long[] {653L, 3670L}) {
            SkyIslandExteriorConnectedCaveVolumeField field =
                    SkyIslandExteriorConnectedCaveVolumeField.create(descriptor(key));
            assertEquals(1, field.exposureGeometry().connectionCount());

            for (SkyIslandCaveExposureConnectionGeometry connection :
                    field.exposureGeometry().connections()) {
                for (SkyIslandCavePassagePoint point : connection.points()) {
                    assertTrue(field.contains(point.position()));
                }
                for (int index = 1; index < connection.points().size(); index++) {
                    SkyIslandSubsurfacePosition a = connection.points().get(index - 1).position();
                    SkyIslandSubsurfacePosition b = connection.points().get(index).position();
                    for (int step = 0; step <= 8; step++) {
                        double t = step / 8.0;
                        SkyIslandSubsurfacePosition position = new SkyIslandSubsurfacePosition(
                                a.x() + (b.x() - a.x()) * t,
                                a.z() + (b.z() - a.z()) * t,
                                a.depthFraction() + (b.depthFraction() - a.depthFraction()) * t);
                        assertTrue(field.contains(position));
                    }
                }

                SkyIslandExteriorConnectedCaveVolumeSample mouth =
                        field.sample(connection.mouthPoint().position());
                assertTrue(mouth.inside());
                assertEquals(
                        SkyIslandExteriorConnectedCaveVolumeSample.SourceKind.EXPOSURE_CONNECTION,
                        mouth.sourceKind());
                assertEquals(connection.systemId(), mouth.systemId());
                assertEquals(connection.side(), mouth.exposureSide());

                double expectedDepth =
                        connection.side() == SkyIslandCaveExposureSide.UPPER_SURFACE ? 0.0 : 1.0;
                assertEquals(expectedDepth, connection.mouthPoint().position().depthFraction(), 0.0);
            }
        }
    }

    @Test
    void exposureConnectionOverlapsExistingCaveVolumeNearCaveAnchor() {
        for (long key : new long[] {653L, 3670L}) {
            SkyIslandExteriorConnectedCaveVolumeField connected =
                    SkyIslandExteriorConnectedCaveVolumeField.create(descriptor(key));
            SkyIslandCaveVolumeField base = connected.baseField();

            for (SkyIslandCaveExposureConnectionGeometry connection :
                    connected.exposureGeometry().connections()) {
                SkyIslandCavePassagePoint first = connection.caveSidePoint();
                double sign = connection.side() == SkyIslandCaveExposureSide.UPPER_SURFACE
                        ? 1.0
                        : -1.0;
                double depth = first.position().depthFraction()
                        + sign * 0.45 * first.depthRadius();
                depth = Math.max(0.0, Math.min(1.0, depth));
                SkyIslandSubsurfacePosition overlap = new SkyIslandSubsurfacePosition(
                        first.position().surfacePosition(),
                        depth);

                assertTrue(base.contains(overlap));
                assertTrue(connected.contains(overlap));
            }
        }
    }

    @Test
    void exposureVolumeDoesNotCreateOppositeBoundaryBreakthroughs() {
        for (long key : new long[] {653L, 3670L}) {
            SkyIslandExteriorConnectedCaveVolumeField field =
                    SkyIslandExteriorConnectedCaveVolumeField.create(descriptor(key));
            for (SkyIslandCaveExposureConnectionGeometry connection :
                    field.exposureGeometry().connections()) {
                double oppositeDepth =
                        connection.side() == SkyIslandCaveExposureSide.UPPER_SURFACE ? 1.0 : 0.0;
                SkyIslandSubsurfacePosition opposite = new SkyIslandSubsurfacePosition(
                        connection.mouthPoint().position().surfacePosition(),
                        oppositeDepth);
                SkyIslandExteriorConnectedCaveVolumeSample sample = field.sample(opposite);
                assertFalse(sample.sourceKind()
                        == SkyIslandExteriorConnectedCaveVolumeSample.SourceKind.EXPOSURE_CONNECTION);
            }

            double radius = field.descriptor().nominalRadius();
            for (int iz = 0; iz <= 24; iz++) {
                double z = -radius + 2.0 * radius * iz / 24.0;
                for (int ix = 0; ix <= 24; ix++) {
                    double x = -radius + 2.0 * radius * ix / 24.0;
                    for (double depth : new double[] {0.0, 1.0}) {
                        SkyIslandExteriorConnectedCaveVolumeSample sample =
                                field.sample(new SkyIslandSubsurfacePosition(x, z, depth));
                        if (sample.sourceKind()
                                == SkyIslandExteriorConnectedCaveVolumeSample.SourceKind.EXPOSURE_CONNECTION) {
                            assertEquals(
                                    depth == 0.0
                                            ? SkyIslandCaveExposureSide.UPPER_SURFACE
                                            : SkyIslandCaveExposureSide.UNDERSIDE,
                                    sample.exposureSide());
                        }
                    }
                }
            }
        }
    }

    @Test
    void positiveExposureSamplesRemainInsideNaturalizedHorizontalOwnership() {
        for (long key : new long[] {653L, 3670L}) {
            SkyIslandDescriptor descriptor = descriptor(key);
            SkyIslandExteriorConnectedCaveVolumeField field =
                    SkyIslandExteriorConnectedCaveVolumeField.create(descriptor);
            SkyIslandSemanticFieldSet semantic = SkyIslandSemanticFieldSet.create(descriptor);
            double radius = descriptor.nominalRadius();

            for (int iz = 0; iz <= 30; iz++) {
                double z = -radius + 2.0 * radius * iz / 30.0;
                for (int ix = 0; ix <= 30; ix++) {
                    double x = -radius + 2.0 * radius * ix / 30.0;
                    for (int id = 0; id <= 20; id++) {
                        SkyIslandSubsurfacePosition position =
                                new SkyIslandSubsurfacePosition(x, z, id / 20.0);
                        SkyIslandExteriorConnectedCaveVolumeSample sample = field.sample(position);
                        if (sample.inside()) {
                            assertTrue(semantic.interiority().sample(position.surfacePosition()) > 0.0);
                        }
                    }
                }
            }
        }
    }

    @Test
    void realizedPhysicalYViewPreservesExteriorConnectedSamples() {
        for (long key : new long[] {653L, 3670L}) {
            SkyIslandDescriptor descriptor = descriptor(key);
            SkyIslandExteriorConnectedCaveVolumeField semantic =
                    SkyIslandExteriorConnectedCaveVolumeField.create(descriptor);
            SyntheticColumns columns = new SyntheticColumns(descriptor.nominalRadius());
            SkyIslandRealizedExteriorConnectedCaveVolumeField realized =
                    new SkyIslandRealizedExteriorConnectedCaveVolumeField(semantic, columns);

            for (SkyIslandCaveExposureConnectionGeometry connection :
                    semantic.exposureGeometry().connections()) {
                for (SkyIslandCavePassagePoint point : connection.points()) {
                    SkyIslandRealizedSubsurfacePosition physical =
                            realized.transform().toPhysical(point.position()).orElseThrow();
                    SkyIslandExteriorConnectedCaveVolumeSample expected =
                            semantic.sample(point.position());
                    SkyIslandExteriorConnectedCaveVolumeSample actual =
                            realized.sample(physical);
                    assertEquals(expected.sourceKind(), actual.sourceKind());
                    assertEquals(expected.systemId(), actual.systemId());
                    assertEquals(expected.exposureSide(), actual.exposureSide());
                    assertEquals(expected.signedClearance(), actual.signedClearance(), 1.0e-10);
                }
            }
        }
    }

    private static SkyIslandDescriptor descriptor(long key) {
        return SkyIslandDescriptorGenerator.derive(
                SkyIslandIdentity.of(SEED, 8L, 81L, key));
    }

    private static final class SyntheticColumns implements SkyIslandVerticalColumnField {
        private final double radius;

        private SyntheticColumns(double radius) {
            this.radius = radius;
        }

        @Override
        public double nominalRadius() {
            return radius;
        }

        @Override
        public Optional<SkyIslandVerticalColumn> columnAt(SkyIslandLocalPosition position) {
            double radial = Math.hypot(position.x(), position.z());
            if (radial >= radius * 0.96) {
                return Optional.empty();
            }
            double upper = 300.0 + 0.06 * position.x() - 0.04 * position.z();
            double thickness = 70.0 + 18.0 * (1.0 - radial / (radius * 0.96));
            return Optional.of(new SkyIslandVerticalColumn(upper, upper - thickness));
        }
    }
}
