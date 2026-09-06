package io.github.nidaba.skyforge.neoforge1211;

import java.util.LinkedHashMap;
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
 * levels, positions, or other mutable Minecraft state.
 */
final class SkyforgeRuntimePerformanceMetrics {
    static final String ENABLE_PROPERTY = "skyforge.dev.performanceMetrics";

    private static final ConcurrentHashMap<String, Metric> METRICS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, SampleMetric> SAMPLES = new ConcurrentHashMap<>();
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

    static void recordSince(String stage, long startNanos) {
        if (!enabled() || startNanos == 0L) {
            return;
        }
        long elapsed = Math.max(0L, System.nanoTime() - startNanos);
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
        return Map.copyOf(evidence);
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
}
