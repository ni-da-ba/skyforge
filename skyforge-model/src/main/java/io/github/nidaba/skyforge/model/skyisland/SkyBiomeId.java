package io.github.nidaba.skyforge.model.skyisland;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Stable backend-neutral identity for a Skyforge sky-biome class.
 *
 * <p>This class identifies a coherent sky/environment zone type. It is not an island, cluster,
 * province, zone-instance identity, ecology regime, or Minecraft biome registry key. A zone
 * assignment may resolve to different backend biomes at different admitted island positions.
 *
 * <p>The value uses a canonical {@code namespace:path} string and deliberately does not depend on
 * Minecraft's {@code ResourceLocation} or any other backend type.
 */
public record SkyBiomeId(String namespace, String path) implements Comparable<SkyBiomeId> {
    private static final Pattern NAMESPACE = Pattern.compile("[a-z0-9_.-]+");
    private static final Pattern PATH = Pattern.compile("[a-z0-9_.-]+(?:/[a-z0-9_.-]+)*");

    public SkyBiomeId {
        namespace = Objects.requireNonNull(namespace, "namespace");
        path = Objects.requireNonNull(path, "path");
        if (!NAMESPACE.matcher(namespace).matches()) {
            throw new IllegalArgumentException("SkyBiome namespace must use lowercase namespaced-ID characters");
        }
        if (!PATH.matcher(path).matches()) {
            throw new IllegalArgumentException("SkyBiome path must use lowercase namespaced-ID characters");
        }
    }

    /** Parses a canonical lowercase {@code namespace:path} identity. */
    public static SkyBiomeId parse(String value) {
        Objects.requireNonNull(value, "value");
        int separator = value.indexOf(':');
        if (separator <= 0 || separator != value.lastIndexOf(':') || separator == value.length() - 1) {
            throw new IllegalArgumentException("SkyBiome ID must have the form namespace:path");
        }
        return new SkyBiomeId(value.substring(0, separator), value.substring(separator + 1));
    }

    /** Returns the canonical stable string form suitable for backend-neutral persistence. */
    public String value() {
        return namespace + ":" + path;
    }

    @Override
    public int compareTo(SkyBiomeId other) {
        Objects.requireNonNull(other, "other");
        int namespaceOrder = namespace.compareTo(other.namespace);
        return namespaceOrder != 0 ? namespaceOrder : path.compareTo(other.path);
    }

    @Override
    public String toString() {
        return value();
    }
}
