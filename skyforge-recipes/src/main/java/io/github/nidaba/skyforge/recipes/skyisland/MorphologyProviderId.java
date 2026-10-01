package io.github.nidaba.skyforge.recipes.skyisland;

import java.util.Objects;
import java.util.regex.Pattern;

/** Stable namespaced identity for one sky-island morphology provider. */
public record MorphologyProviderId(String namespace, String path)
        implements Comparable<MorphologyProviderId> {
    private static final Pattern NAMESPACE = Pattern.compile("[a-z][a-z0-9_.-]*");
    private static final Pattern PATH = Pattern.compile("[a-z][a-z0-9_./-]*");

    /** Validates a canonical lowercase provider identifier. */
    public MorphologyProviderId {
        Objects.requireNonNull(namespace, "namespace");
        Objects.requireNonNull(path, "path");
        if (!NAMESPACE.matcher(namespace).matches()) {
            throw new IllegalArgumentException("invalid morphology provider namespace: " + namespace);
        }
        if (!PATH.matcher(path).matches()) {
            throw new IllegalArgumentException("invalid morphology provider path: " + path);
        }
    }

    /** Parses a canonical {@code namespace:path} identifier. */
    public static MorphologyProviderId parse(String value) {
        Objects.requireNonNull(value, "value");
        int separator = value.indexOf(':');
        if (separator <= 0 || separator != value.lastIndexOf(':') || separator == value.length() - 1) {
            throw new IllegalArgumentException("morphology provider id must be namespace:path");
        }
        return new MorphologyProviderId(value.substring(0, separator), value.substring(separator + 1));
    }

    /** Returns the stable serialized identifier. */
    @Override
    public String toString() {
        return namespace + ":" + path;
    }

    /** Orders providers by their stable serialized identifier. */
    @Override
    public int compareTo(MorphologyProviderId other) {
        return toString().compareTo(Objects.requireNonNull(other, "other").toString());
    }
}
