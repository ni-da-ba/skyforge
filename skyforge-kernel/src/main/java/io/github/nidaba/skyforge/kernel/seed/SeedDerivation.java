package io.github.nidaba.skyforge.kernel.seed;

import java.util.Objects;

/** Central versioned derivation of operation-local seeds from semantic namespaces. */
public final class SeedDerivation {
    /** Version of the seed-derivation algorithm defined by this class. */
    public static final int VERSION = 1;

    private static final long FNV_OFFSET_BASIS = 0xcbf29ce484222325L;
    private static final long FNV_PRIME = 0x100000001b3L;
    private static final long VERSION_DOMAIN = 0x534b594645454431L;

    private SeedDerivation() {}

    /**
     * Derives a local 64-bit seed from a root seed and stable semantic namespace.
     *
     * @throws NullPointerException if {@code namespace} is {@code null}
     * @throws IllegalArgumentException if the namespace is not canonical semantic ASCII
     */
    public static long derive(long rootSeed, String namespace) {
        requireNamespace(namespace);
        long namespaceHash = FNV_OFFSET_BASIS;
        for (int index = 0; index < namespace.length(); index++) {
            namespaceHash ^= namespace.charAt(index);
            namespaceHash *= FNV_PRIME;
        }
        return mix64(rootSeed ^ Long.rotateLeft(namespaceHash, 17) ^ VERSION_DOMAIN);
    }

    /** Validates the stable lowercase semantic namespace grammar used by seeded operations. */
    public static void requireNamespace(String namespace) {
        Objects.requireNonNull(namespace, "namespace");
        boolean previousWasSegment = false;
        for (int index = 0; index < namespace.length(); index++) {
            char character = namespace.charAt(index);
            boolean segment = character >= 'a' && character <= 'z'
                    || character >= '0' && character <= '9';
            if (!segment && (character != '.' && character != '-' || !previousWasSegment)) {
                throw invalidNamespace();
            }
            previousWasSegment = segment;
        }
        if (!previousWasSegment) {
            throw invalidNamespace();
        }
    }

    private static IllegalArgumentException invalidNamespace() {
        return new IllegalArgumentException(
                "namespace must be lowercase semantic ASCII segments separated by '.' or '-'");
    }

    /** Stable SplitMix64 finalizer used after semantic domain separation. */
    public static long mix64(long value) {
        long mixed = value;
        mixed = (mixed ^ (mixed >>> 30)) * 0xbf58476d1ce4e5b9L;
        mixed = (mixed ^ (mixed >>> 27)) * 0x94d049bb133111ebL;
        return mixed ^ (mixed >>> 31);
    }
}
