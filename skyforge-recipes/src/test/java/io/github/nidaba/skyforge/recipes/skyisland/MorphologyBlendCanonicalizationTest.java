package io.github.nidaba.skyforge.recipes.skyisland;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

final class MorphologyBlendCanonicalizationTest {
    @Test
    void decimalComplementPairOrderProducesExactCanonicalBlend() {
        MorphologyBlend forward = new MorphologyBlend(
                MorphologyFamily.MASSIF, MorphologyFamily.SPINE, 0.30);
        MorphologyBlend reversed = new MorphologyBlend(
                MorphologyFamily.SPINE, MorphologyFamily.MASSIF, 0.70);

        assertEquals(forward, reversed);
        assertEquals(
                Double.doubleToRawLongBits(forward.secondWeight()),
                Double.doubleToRawLongBits(reversed.secondWeight()));
        assertEquals(
                Double.doubleToRawLongBits(forward.firstWeight()),
                Double.doubleToRawLongBits(reversed.firstWeight()));
    }

    @Test
    void computedComplementDriftIsCanonicalizedAway() {
        double forwardWeight = 0.30;
        double reversedWeight = 1.0 - forwardWeight;

        MorphologyBlend forward = new MorphologyBlend(
                MorphologyFamily.MASSIF, MorphologyFamily.SPINE, forwardWeight);
        MorphologyBlend reversed = new MorphologyBlend(
                MorphologyFamily.SPINE, MorphologyFamily.MASSIF, reversedWeight);

        assertEquals(forward, reversed);
    }
}
