package io.github.nidaba.skyforge.neoforge1211;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

final class SkyforgeGliderLiftCouplingTest {
    private static final double EPSILON = 1.0e-12;

    @Test
    void convertsPhysicalUpdraftIntoReliableGliderVerticalTarget() {
        assertEquals(-0.05, SkyforgeGliderLiftCoupling.targetVerticalVelocity(0.0), EPSILON);
        assertEquals(0.00, SkyforgeGliderLiftCoupling.targetVerticalVelocity(1.0), EPSILON);
        assertEquals(0.05, SkyforgeGliderLiftCoupling.targetVerticalVelocity(2.0), EPSILON);
        assertEquals(0.15, SkyforgeGliderLiftCoupling.targetVerticalVelocity(4.0), EPSILON);
        assertEquals(0.70, SkyforgeGliderLiftCoupling.targetVerticalVelocity(15.0), EPSILON);
    }

    @Test
    void trustedThermalSmoothlyRaisesStockBaseline() {
        assertEquals(
                -0.01,
                SkyforgeGliderLiftCoupling.apply(-0.05, true, 4.0),
                EPSILON);
    }

    @Test
    void strongerNativeUpdraftWinsWithoutSummation() {
        assertEquals(
                0.70,
                SkyforgeGliderLiftCoupling.apply(0.70, true, 4.0),
                EPSILON);
    }

    @Test
    void untrustedNonFiniteAndNonPositiveAtmosphereAreInert() {
        assertEquals(-0.05, SkyforgeGliderLiftCoupling.apply(-0.05, false, 8.0), EPSILON);
        assertEquals(-0.05, SkyforgeGliderLiftCoupling.apply(-0.05, true, 0.0), EPSILON);
        assertEquals(-0.05, SkyforgeGliderLiftCoupling.apply(-0.05, true, -2.0), EPSILON);
        assertEquals(-0.05, SkyforgeGliderLiftCoupling.apply(-0.05, true, Double.NaN), EPSILON);
    }
}
