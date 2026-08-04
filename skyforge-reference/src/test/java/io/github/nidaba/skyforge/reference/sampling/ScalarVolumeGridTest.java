package io.github.nidaba.skyforge.reference.sampling;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import org.junit.jupiter.api.Test;

final class ScalarVolumeGridTest {
    private static final VolumeGridSpec GRID =
            new VolumeGridSpec(-1.0, 1.0, 0.0, 1.0, -2.0, 2.0, 3, 2, 2);

    @Test
    void fixesXThenZThenYIndexingAndCanonicalBinaryHeader() throws IOException {
        double[] values = new double[GRID.sampleCount()];
        for (int index = 0; index < values.length; index++) {
            values[index] = index + 0.25;
        }
        ScalarVolumeGrid volume = new ScalarVolumeGrid(GRID, values);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        volume.writeCanonical(output);

        assertEquals(0.25, volume.valueAt(0, 0, 0));
        assertEquals(3.25, volume.valueAt(0, 0, 1));
        assertEquals(6.25, volume.valueAt(0, 1, 0));
        assertEquals(168, output.size());
        assertArrayEquals(
                new byte[] {'S', 'F', 'V', 'O', 'L', 0, 0, 1},
                java.util.Arrays.copyOf(output.toByteArray(), 8));
    }

    @Test
    void preservesRawBinary64IdentityAndRejectsNonFiniteValues() {
        double[] positiveZero = new double[GRID.sampleCount()];
        double[] negativeZero = positiveZero.clone();
        negativeZero[4] = -0.0;
        ScalarVolumeGrid first = new ScalarVolumeGrid(GRID, positiveZero);
        ScalarVolumeGrid second = new ScalarVolumeGrid(GRID, positiveZero);
        ScalarVolumeGrid signed = new ScalarVolumeGrid(GRID, negativeZero);

        assertTrue(first.rawValuesEqual(second));
        assertNotEquals(first.sha256(), signed.sha256());
        double[] nonFinite = positiveZero.clone();
        nonFinite[0] = Double.NaN;
        assertThrows(IllegalArgumentException.class, () -> new ScalarVolumeGrid(GRID, nonFinite));
    }
}
