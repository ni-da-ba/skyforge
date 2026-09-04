package io.github.nidaba.skyforge.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandIdentity;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class SkyIslandSemanticDepthRealizationTransformTest {
    private static final long SEED = 0x534B59464F524745L;

    @Test
    void semanticDepthRoundTripsThroughVariablePhysicalColumns() {
        SyntheticColumns columns = new SyntheticColumns(128.0);
        SkyIslandSemanticDepthRealizationTransform transform =
                new SkyIslandSemanticDepthRealizationTransform(columns);

        for (SkyIslandLocalPosition horizontal : new SkyIslandLocalPosition[] {
            new SkyIslandLocalPosition(0.0, 0.0),
            new SkyIslandLocalPosition(32.0, -18.0),
            new SkyIslandLocalPosition(-54.0, 21.0)
        }) {
            for (double depth : new double[] {0.0, 0.1, 0.25, 0.5, 0.75, 0.9, 1.0}) {
                SkyIslandSubsurfacePosition semantic =
                        new SkyIslandSubsurfacePosition(horizontal, depth);
                SkyIslandRealizedSubsurfacePosition physical =
                        transform.toPhysical(semantic).orElseThrow();
                SkyIslandSubsurfacePosition recovered =
                        transform.toSemantic(physical).orElseThrow();

                assertEquals(horizontal, recovered.surfacePosition());
                assertEquals(depth, recovered.depthFraction(), 1.0e-12);
            }
        }
    }

    @Test
    void physicalPositionsOutsideColumnDoNotMapIntoSemanticInterior() {
        SyntheticColumns columns = new SyntheticColumns(128.0);
        SkyIslandSemanticDepthRealizationTransform transform =
                new SkyIslandSemanticDepthRealizationTransform(columns);
        SkyIslandLocalPosition horizontal = new SkyIslandLocalPosition(20.0, 10.0);
        SkyIslandVerticalColumn column = columns.columnAt(horizontal).orElseThrow();

        assertTrue(transform.toSemantic(new SkyIslandRealizedSubsurfacePosition(
                        horizontal, column.upperY()))
                .isPresent());
        assertTrue(transform.toSemantic(new SkyIslandRealizedSubsurfacePosition(
                        horizontal, column.undersideY()))
                .isPresent());
        assertTrue(transform.toSemantic(new SkyIslandRealizedSubsurfacePosition(
                        horizontal, column.upperY() + 0.01))
                .isEmpty());
        assertTrue(transform.toSemantic(new SkyIslandRealizedSubsurfacePosition(
                        horizontal, column.undersideY() - 0.01))
                .isEmpty());
    }

    @Test
    void absentPhysicalColumnCannotRealizeSemanticDepth() {
        SyntheticColumns columns = new SyntheticColumns(128.0);
        SkyIslandSemanticDepthRealizationTransform transform =
                new SkyIslandSemanticDepthRealizationTransform(columns);
        SkyIslandSubsurfacePosition outside =
                new SkyIslandSubsurfacePosition(127.0, 0.0, 0.5);

        assertTrue(transform.toPhysical(outside).isEmpty());
    }

    @Test
    void realizedCaveSamplingPreservesAuth0026AtMappedPoints() {
        SkyIslandDescriptor descriptor = descriptor(653L);
        SkyIslandCaveVolumeField semantic = SkyIslandCaveVolumeField.create(descriptor);
        SyntheticColumns columns = new SyntheticColumns(descriptor.nominalRadius());
        SkyIslandRealizedCaveVolumeField realized =
                new SkyIslandRealizedCaveVolumeField(semantic, columns);

        for (SkyIslandCaveSystemGeometry system : semantic.geometry().systems()) {
            for (SkyIslandCaveChamberGeometry chamber : system.chambers()) {
                assertEquivalentAt(semantic, realized, chamber.center());
            }
            for (SkyIslandCavePassageGeometry passage : system.passages()) {
                for (SkyIslandCavePassagePoint point : passage.points()) {
                    assertEquivalentAt(semantic, realized, point.position());
                }
            }
        }
    }

    @Test
    void realizedCaveFieldRejectsHorizontalScaleMismatch() {
        SkyIslandDescriptor descriptor = descriptor(653L);
        SkyIslandCaveVolumeField cave = SkyIslandCaveVolumeField.create(descriptor);

        assertThrows(
                IllegalArgumentException.class,
                () -> new SkyIslandRealizedCaveVolumeField(
                        cave,
                        new SyntheticColumns(descriptor.nominalRadius() + 1.0)));
    }

    @Test
    void realizedCaveFieldReturnsOutsideForPhysicalAirAboveOrBelowIsland() {
        SkyIslandDescriptor descriptor = descriptor(653L);
        SyntheticColumns columns = new SyntheticColumns(descriptor.nominalRadius());
        SkyIslandRealizedCaveVolumeField realized =
                new SkyIslandRealizedCaveVolumeField(
                        SkyIslandCaveVolumeField.create(descriptor),
                        columns);
        SkyIslandLocalPosition horizontal = new SkyIslandLocalPosition(0.0, 0.0);
        SkyIslandVerticalColumn column = columns.columnAt(horizontal).orElseThrow();

        assertFalse(realized.contains(new SkyIslandRealizedSubsurfacePosition(
                horizontal, column.upperY() + 10.0)));
        assertFalse(realized.contains(new SkyIslandRealizedSubsurfacePosition(
                horizontal, column.undersideY() - 10.0)));
    }

    private static void assertEquivalentAt(
            SkyIslandCaveVolumeField semantic,
            SkyIslandRealizedCaveVolumeField realized,
            SkyIslandSubsurfacePosition position) {
        SkyIslandCaveVolumeSample expected = semantic.sample(position);
        SkyIslandRealizedSubsurfacePosition physical =
                realized.transform().toPhysical(position).orElseThrow();
        SkyIslandCaveVolumeSample actual = realized.sample(physical);

        assertEquals(expected.primitiveKind(), actual.primitiveKind());
        assertEquals(expected.systemId(), actual.systemId());
        assertEquals(expected.primitiveId(), actual.primitiveId());
        assertEquals(expected.signedClearance(), actual.signedClearance(), 1.0e-10);
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
            if (radial >= radius * 0.92) {
                return Optional.empty();
            }
            double upper = 310.0
                    + 0.08 * position.x()
                    - 0.05 * position.z();
            double thickness = 72.0
                    + 16.0 * (1.0 - radial / (radius * 0.92));
            return Optional.of(new SkyIslandVerticalColumn(upper, upper - thickness));
        }
    }
}
