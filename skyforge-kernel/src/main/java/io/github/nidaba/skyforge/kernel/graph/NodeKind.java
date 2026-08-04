package io.github.nidaba.skyforge.kernel.graph;

/** Stable identifiers for the initial graph node families. */
public enum NodeKind {
    CONSTANT("constant"),
    COORDINATE("coordinate"),
    ARITHMETIC("arithmetic"),
    PLANAR_VALUE_SIGNAL("planar-value-signal");

    private final String identifier;

    NodeKind(String identifier) {
        this.identifier = identifier;
    }

    /** Returns the stable external identifier for this node family. */
    public String identifier() {
        return identifier;
    }
}
