package io.github.nidaba.skyforge.kernel.graph;

import java.util.Objects;

/** A graph-local, human-readable node identifier. */
public record NodeId(String value) {
    /**
     * Creates a node identifier without silently normalizing it.
     *
     * @throws NullPointerException if {@code value} is {@code null}
     * @throws IllegalArgumentException if {@code value} is blank or has surrounding whitespace
     */
    public NodeId {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) {
            throw new IllegalArgumentException("value must not be blank");
        }
        if (!value.equals(value.strip())) {
            throw new IllegalArgumentException("value must not have surrounding whitespace");
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
