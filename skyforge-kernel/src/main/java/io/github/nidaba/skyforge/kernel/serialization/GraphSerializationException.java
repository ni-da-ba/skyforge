package io.github.nidaba.skyforge.kernel.serialization;

/** Reports invalid or unsupported canonical graph data. */
public final class GraphSerializationException extends IllegalArgumentException {
    private static final long serialVersionUID = 1L;

    /** Creates a serialization failure with an actionable diagnostic. */
    public GraphSerializationException(String message) {
        super(message);
    }

    /** Creates a serialization failure caused by invalid lower-level data. */
    public GraphSerializationException(String message, Throwable cause) {
        super(message, cause);
    }
}
