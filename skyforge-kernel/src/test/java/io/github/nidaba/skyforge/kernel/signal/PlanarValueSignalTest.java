package io.github.nidaba.skyforge.kernel.signal;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nidaba.skyforge.kernel.graph.GraphValueType;
import io.github.nidaba.skyforge.kernel.graph.NodeId;
import io.github.nidaba.skyforge.kernel.graph.PlanarValueSignalNode;
import io.github.nidaba.skyforge.kernel.seed.SeedDerivation;
import org.junit.jupiter.api.Test;

final class PlanarValueSignalTest {
    private static final long ROOT_SEED = 0x534b59464f524745L;
    private static final double SCALE = 32.0;

    @Test
    void matchesVersionOneGoldenSamplesByRawBinary64Bits() {
        PlanarValueSignalNode node = node(ROOT_SEED, "island.height-detail");

        assertAll(
                () -> assertBits(0xbfe9795d25538c58L, PlanarValueSignal.sample(node, 0.0, 0.0)),
                () -> assertBits(0xbfe8def38fbf258bL, PlanarValueSignal.sample(node, 1.0, 2.0)),
                () -> assertBits(0x3fd006d2dd638aa7L, PlanarValueSignal.sample(node, -17.25, 44.5)),
                () -> assertBits(0x3feaa937bcd4bb26L, PlanarValueSignal.sample(node, 128.0, -96.0)));
    }

    @Test
    void remainsBoundedAndContinuousAcrossRepresentativeCells() {
        PlanarValueSignalNode node = node(ROOT_SEED, "island.height-detail");
        double previous = PlanarValueSignal.sample(node, -256.0, 7.25);
        for (int index = 1; index <= 4096; index++) {
            double x = -256.0 + index * 0.125;
            double value = PlanarValueSignal.sample(node, x, 7.25);
            assertTrue(value >= -1.0 && value <= 1.0);
            assertTrue(Math.abs(value - previous) < 0.02);
            previous = value;
        }
    }

    @Test
    void seedAndNamespaceChangeValuesWithoutGlobalState() {
        PlanarValueSignalNode baseline = node(7L, "island.height-detail");
        PlanarValueSignalNode otherSeed = node(8L, "island.height-detail");
        PlanarValueSignalNode otherNamespace = node(7L, "island.ridge-detail");
        double first = PlanarValueSignal.sample(baseline, 12.5, -9.25);

        assertAll(
                () -> assertEquals(
                        Double.doubleToRawLongBits(first),
                        Double.doubleToRawLongBits(PlanarValueSignal.sample(baseline, 12.5, -9.25))),
                () -> assertNotEquals(first, PlanarValueSignal.sample(otherSeed, 12.5, -9.25)),
                () -> assertNotEquals(first, PlanarValueSignal.sample(otherNamespace, 12.5, -9.25)));
    }

    @Test
    void declaredPeriodIsExactAtWorldScale() {
        PlanarValueSignalNode node = node(ROOT_SEED, "island.height-detail");
        double period = SCALE * (1L << 20);
        double first = PlanarValueSignal.sample(node, 123.5, -456.25);
        double repeated = PlanarValueSignal.sample(node, 123.5 + period, -456.25 - period);

        assertEquals(Double.doubleToRawLongBits(first), Double.doubleToRawLongBits(repeated));
    }

    @Test
    void rejectsNullNodesAndNonfiniteCoordinates() {
        PlanarValueSignalNode node = node(ROOT_SEED, "island.height-detail");

        assertAll(
                () -> assertThrows(
                        NullPointerException.class,
                        () -> PlanarValueSignal.sample(null, 0.0, 0.0)),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> PlanarValueSignal.sample(node, Double.NaN, 0.0)),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> PlanarValueSignal.sample(node, 0.0, Double.POSITIVE_INFINITY)));
    }

    private static PlanarValueSignalNode node(long seed, String namespace) {
        return new PlanarValueSignalNode(
                new NodeId("signal"),
                GraphValueType.SCALAR_FIELD_2,
                PlanarValueSignal.VERSION,
                SeedDerivation.VERSION,
                seed,
                namespace,
                SCALE);
    }

    private static void assertBits(long expected, double actual) {
        assertEquals(expected, Double.doubleToRawLongBits(actual));
    }
}
