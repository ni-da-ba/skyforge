package io.github.nidaba.skyforge.neoforge1211;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Supplier;

/**
 * Opt-in low-overhead timing counters for development performance characterization.
 *
 * <p>Production behavior is unchanged unless {@value #ENABLE_PROPERTY} is true. Timings are
 * deliberately aggregate: they identify expensive lifecycle seams without retaining chunks,
 * levels, positions, or other mutable Minecraft state. Exact percentile distributions are retained
 * only for explicitly requested development evidence and therefore have zero storage cost in normal
 * packaged runtime.
 */
final class SkyforgeRuntimePerformanceMetrics {
    static final String ENABLE_PROPERTY = "skyforge.dev.performanceMetrics";

    private static final ConcurrentHashMap<String, Metric> METRICS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, SampleMetric> SAMPLES = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, DistributionMetric> DISTRIBUTIONS = new ConcurrentHashMap<>();
    private static final AtomicLong PROCESS_START_NANOS = new AtomicLong(Long.MIN_VALUE);

    private SkyforgeRuntimePerformanceMetrics() {}

    static boolean enabled() {
        return Boolean.getBoolean(ENABLE_PROPERTY);
    }

    static void initialize() {
        if (enabled()) {
            PROCESS_START_NANOS.compareAndSet(Long.MIN_VALUE, System.nanoTime());
        }
    }

    static long start() {
        return enabled() ? System.nanoTime() : 0L;
    }

    static long elapsedSince(long startNanos) {
        if (!enabled() || startNanos == 0L) {
            return 0L;
        }
        return Math.max(0L, System.nanoTime() - startNanos);
    }

    static void recordElapsed(String stage, long elapsedNanos) {
        if (!enabled()) {
            return;
        }
        if (elapsedNanos < 0L) {
            throw new IllegalArgumentException("performance elapsed time must be nonnegative");
        }
        record(stage, elapsedNanos);
    }

    static void recordSince(String stage, long startNanos) {
        long elapsed = elapsedSince(startNanos);
        if (startNanos == 0L || !enabled()) {
            return;
        }
        record(stage, elapsed);
    }

    static <T> T measure(String stage, Supplier<T> operation) {
        Objects.requireNonNull(operation, "operation");
        long start = start();
        try {
            return operation.get();
        } finally {
            recordSince(stage, start);
        }
    }

    static void measure(String stage, Runnable operation) {
        Objects.requireNonNull(operation, "operation");
        long start = start();
        try {
            operation.run();
        } finally {
            recordSince(stage, start);
        }
    }

    private static void record(String stage, long elapsedNanos) {
        Objects.requireNonNull(stage, "stage");
        Metric metric = METRICS.computeIfAbsent(stage, ignored -> new Metric());
        metric.calls.increment();
        metric.totalNanos.add(elapsedNanos);
        metric.maxNanos.accumulateAndGet(elapsedNanos, Math::max);
    }

    /** Records a non-negative unitless sample alongside, but separately from, nanosecond timers. */
    static void recordSample(String stage, long value) {
        if (!enabled()) {
            return;
        }
        Objects.requireNonNull(stage, "stage");
        if (value < 0L) {
            throw new IllegalArgumentException("performance sample must be nonnegative");
        }
        SampleMetric sample = SAMPLES.computeIfAbsent(stage, ignored -> new SampleMetric());
        sample.samples.increment();
        sample.total.add(value);
        sample.maximum.accumulateAndGet(value, Math::max);
    }

    /**
     * Records one exact non-negative development-only distribution sample.
     *
     * <p>This path is intentionally reserved for bounded acceptance evidence such as deferred-write
     * packet latency and packet block count. Normal packaged runtime never allocates these samples
     * because performance metrics are opt-in.
     */
    static void recordDistributionSample(String stage, long value) {
        if (!enabled()) {
            return;
        }
        Objects.requireNonNull(stage, "stage");
        if (value < 0L) {
            throw new IllegalArgumentException("performance distribution sample must be nonnegative");
        }
        DISTRIBUTIONS.computeIfAbsent(stage, ignored -> new DistributionMetric()).add(value);
    }

    static Map<String, Object> evidence() {
        if (!enabled()) {
            return Map.of();
        }
        initialize();
        LinkedHashMap<String, Object> evidence = new LinkedHashMap<>();
        long processStart = PROCESS_START_NANOS.get();
        evidence.put(
                "perf.processElapsedNanos",
                processStart == Long.MIN_VALUE ? 0L : Math.max(0L, System.nanoTime() - processStart));
        METRICS.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> {
                    String prefix = "perf." + entry.getKey();
                    Metric metric = entry.getValue();
                    evidence.put(prefix + ".calls", metric.calls.sum());
                    evidence.put(prefix + ".totalNanos", metric.totalNanos.sum());
                    evidence.put(prefix + ".maxNanos", metric.maxNanos.get());
                });
        SAMPLES.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> {
                    String prefix = "perf." + entry.getKey();
                    SampleMetric sample = entry.getValue();
                    evidence.put(prefix + ".samples", sample.samples.sum());
                    evidence.put(prefix + ".total", sample.total.sum());
                    evidence.put(prefix + ".max", sample.maximum.get());
                });
        DISTRIBUTIONS.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> {
                    String prefix = "perf." + entry.getKey();
                    DistributionSnapshot snapshot = entry.getValue().snapshot();
                    evidence.put(prefix + ".samples", snapshot.samples());
                    evidence.put(prefix + ".total", snapshot.total());
                    evidence.put(prefix + ".p50", snapshot.p50());
                    evidence.put(prefix + ".p95", snapshot.p95());
                    evidence.put(prefix + ".p99", snapshot.p99());
                    evidence.put(prefix + ".max", snapshot.maximum());
                });
        return Map.copyOf(evidence);
    }

    static long nearestRankPercentile(List<Long> sortedValues, int percentile) {
        Objects.requireNonNull(sortedValues, "sortedValues");
        if (sortedValues.isEmpty()) {
            return 0L;
        }
        if (percentile <= 0 || percentile > 100) {
            throw new IllegalArgumentException("percentile must be in [1, 100]");
        }
        int rank = (int) Math.ceil((percentile / 100.0d) * sortedValues.size());
        return sortedValues.get(Math.max(0, rank - 1));
    }

    private static final class Metric {
        private final LongAdder calls = new LongAdder();
        private final LongAdder totalNanos = new LongAdder();
        private final AtomicLong maxNanos = new AtomicLong();
    }

    private static final class SampleMetric {
        private final LongAdder samples = new LongAdder();
        private final LongAdder total = new LongAdder();
        private final AtomicLong maximum = new AtomicLong();
    }

    private static final class DistributionMetric {
        private final List<Long> values = new ArrayList<>();
        private long total;
        private long maximum;

        synchronized void add(long value) {
            values.add(value);
            total = Math.addExact(total, value);
            maximum = Math.max(maximum, value);
        }

        synchronized DistributionSnapshot snapshot() {
            List<Long> ordered = new ArrayList<>(values);
            ordered.sort(Long::compareTo);
            return new DistributionSnapshot(
                    ordered.size(),
                    total,
                    nearestRankPercentile(ordered, 50),
                    nearestRankPercentile(ordered, 95),
                    nearestRankPercentile(ordered, 99),
                    maximum);
        }
    }

    private record DistributionSnapshot(
            int samples,
            long total,
            long p50,
            long p95,
            long p99,
            long maximum) {}
}
