package io.github.nidaba.skyforge.reference.benchmark;

import java.util.Objects;

/** One observational timing of canonical reference height-field evaluation. */
public record ReferenceBenchmarkObservation(
        String memberId,
        long rootSeed,
        int sampleCount,
        long wallTimeNanoseconds,
        double samplesPerSecond) {
    /** Validates a finite positive observation without imposing a performance threshold. */
    public ReferenceBenchmarkObservation {
        Objects.requireNonNull(memberId, "memberId");
        if (memberId.isBlank()) {
            throw new IllegalArgumentException("memberId must not be blank");
        }
        if (sampleCount <= 0) {
            throw new IllegalArgumentException("sampleCount must be positive");
        }
        if (wallTimeNanoseconds <= 0L) {
            throw new IllegalArgumentException("wallTimeNanoseconds must be positive");
        }
        if (!Double.isFinite(samplesPerSecond) || samplesPerSecond <= 0.0) {
            throw new IllegalArgumentException("samplesPerSecond must be finite and positive");
        }
    }
}
