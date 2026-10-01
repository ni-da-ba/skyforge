package io.github.nidaba.skyforge.kernel.graph;

/** Reports a structural or type-integrity failure in a procedural graph. */
public final class GraphValidationException extends IllegalArgumentException {
    private static final long serialVersionUID = 1L;

    /** Creates a validation failure with an actionable diagnostic. */
    public GraphValidationException(String message) {
        super(message);
    }
}
