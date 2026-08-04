package io.github.nidaba.skyforge.kernel.density;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class SignedDensityTest {
    @Test
    void classifiesTheAcceptedPositiveInsideConventionExactly() {
        assertAll(
                () -> assertEquals(SignedDensity.Region.AIR, SignedDensity.classify(-Double.MIN_VALUE)),
                () -> assertEquals(SignedDensity.Region.SURFACE, SignedDensity.classify(-0.0)),
                () -> assertEquals(SignedDensity.Region.SURFACE, SignedDensity.classify(0.0)),
                () -> assertEquals(SignedDensity.Region.SOLID, SignedDensity.classify(Double.MIN_VALUE)),
                () -> assertFalse(SignedDensity.isSolid(-1.0)),
                () -> assertFalse(SignedDensity.isSolid(0.0)),
                () -> assertTrue(SignedDensity.isSolid(1.0)));
    }

    @Test
    void rejectsEveryNonfiniteEvidenceValue() {
        assertAll(
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> SignedDensity.classify(Double.NaN)),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> SignedDensity.classify(Double.NEGATIVE_INFINITY)),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> SignedDensity.classify(Double.POSITIVE_INFINITY)));
    }
}
