package io.github.nidaba.skyforge.reference.sampling;

/** Evaluation schedules used to prove that a grid's canonical values do not depend on traversal. */
public enum SamplingOrder {
    /** Increasing row-major index. */
    FORWARD,
    /** Decreasing row-major index. */
    REVERSED,
    /** A deterministic coprime-stride permutation of every row-major index. */
    PERMUTED,
    /** Fixed-size batches visited in reverse order. */
    BATCHED,
    /** Row-major indices evaluated through the common fork-join pool. */
    PARALLEL
}
