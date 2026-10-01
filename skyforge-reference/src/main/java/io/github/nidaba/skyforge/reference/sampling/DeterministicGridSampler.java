package io.github.nidaba.skyforge.reference.sampling;

import io.github.nidaba.skyforge.kernel.coordinate.Coordinate2;
import io.github.nidaba.skyforge.kernel.field.ScalarField2;
import java.util.Objects;
import java.util.function.IntConsumer;
import java.util.stream.IntStream;

/** Samples a field into a canonical row-major grid under several equivalent schedules. */
public final class DeterministicGridSampler {
    /** Samples a field using the requested traversal while storing canonical row-major values. */
    public ScalarGrid sample(ScalarField2 field, GridSpec grid, SamplingOrder order) {
        Objects.requireNonNull(field, "field");
        Objects.requireNonNull(grid, "grid");
        Objects.requireNonNull(order, "order");
        double[] values = new double[grid.sampleCount()];
        IntConsumer sampleIndex = index -> {
            int xIndex = index % grid.width();
            int zIndex = index / grid.width();
            values[index] = field.sample(new Coordinate2(grid.xAt(xIndex), grid.zAt(zIndex)));
        };

        switch (order) {
            case FORWARD -> IntStream.range(0, values.length).forEach(sampleIndex);
            case REVERSED -> {
                for (int index = values.length - 1; index >= 0; index--) {
                    sampleIndex.accept(index);
                }
            }
            case PERMUTED -> samplePermuted(values.length, sampleIndex);
            case BATCHED -> sampleBatched(values.length, sampleIndex);
            case PARALLEL -> IntStream.range(0, values.length).parallel().forEach(sampleIndex);
        }
        return new ScalarGrid(grid, values);
    }

    private static void samplePermuted(int count, IntConsumer sampleIndex) {
        int stride = Math.max(2, count / 2 + 1);
        while (greatestCommonDivisor(stride, count) != 1) {
            stride++;
        }
        int index = count / 3;
        for (int visited = 0; visited < count; visited++) {
            sampleIndex.accept(index);
            index = (int) (((long) index + stride) % count);
        }
    }

    private static void sampleBatched(int count, IntConsumer sampleIndex) {
        int batchSize = 17;
        int batchCount = (count - 1) / batchSize + 1;
        for (int batch = batchCount - 1; batch >= 0; batch--) {
            int start = batch * batchSize;
            int end = Math.min(start + batchSize, count);
            for (int index = start; index < end; index++) {
                sampleIndex.accept(index);
            }
        }
    }

    private static int greatestCommonDivisor(int first, int second) {
        int left = first;
        int right = second;
        while (right != 0) {
            int remainder = left % right;
            left = right;
            right = remainder;
        }
        return left;
    }
}
