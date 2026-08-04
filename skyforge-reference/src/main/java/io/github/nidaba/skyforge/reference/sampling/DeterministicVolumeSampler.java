package io.github.nidaba.skyforge.reference.sampling;

import io.github.nidaba.skyforge.kernel.coordinate.Coordinate3;
import io.github.nidaba.skyforge.kernel.field.ScalarField3;
import java.util.Objects;
import java.util.function.IntConsumer;
import java.util.stream.IntStream;

/** Samples a three-dimensional field under equivalent schedules into canonical storage. */
public final class DeterministicVolumeSampler {
    /** Samples with x fastest, then z, then y, regardless of evaluation order. */
    public ScalarVolumeGrid sample(
            ScalarField3 field, VolumeGridSpec grid, SamplingOrder order) {
        Objects.requireNonNull(field, "field");
        Objects.requireNonNull(grid, "grid");
        Objects.requireNonNull(order, "order");
        double[] values = new double[grid.sampleCount()];
        int horizontalLayer = grid.xSamples() * grid.zSamples();
        IntConsumer sampleIndex = index -> {
            int yIndex = index / horizontalLayer;
            int horizontalIndex = index % horizontalLayer;
            int zIndex = horizontalIndex / grid.xSamples();
            int xIndex = horizontalIndex % grid.xSamples();
            values[index] = field.sample(new Coordinate3(
                    grid.xAt(xIndex), grid.yAt(yIndex), grid.zAt(zIndex)));
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
        return new ScalarVolumeGrid(grid, values);
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
        int batchSize = 29;
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
