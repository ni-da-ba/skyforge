package io.github.nidaba.skyforge.world;

import io.github.nidaba.skyforge.kernel.graph.GraphValueType;
import io.github.nidaba.skyforge.kernel.graph.NodeId;
import io.github.nidaba.skyforge.kernel.graph.PlanarValueSignalNode;
import io.github.nidaba.skyforge.kernel.seed.SeedDerivation;
import io.github.nidaba.skyforge.kernel.signal.PlanarValueSignal;
import java.util.Objects;

/**
 * Explicit deterministic spatial signal profile for ecological patch realization.
 *
 * <p>The profile delegates signal semantics to the versioned kernel {@link PlanarValueSignal}. Seed,
 * namespace, scale, signal version, and seed-derivation version are retained explicitly so patch
 * provenance never depends on backend state or encounter order.
 */
public record SkyIslandEcologicalPatchSignalProfile(
        int signalVersion,
        int seedVersion,
        long rootSeed,
        String namespace,
        double scale) {

    public SkyIslandEcologicalPatchSignalProfile {
        Objects.requireNonNull(namespace, "namespace");
        SeedDerivation.requireNamespace(namespace);
        if (!Double.isFinite(scale) || scale <= 0.0) {
            throw new IllegalArgumentException("scale must be finite and positive");
        }

        // Reuse the kernel node constructor as the normative version/seed contract validator.
        node(signalVersion, seedVersion, rootSeed, namespace, scale);
    }

    /** Creates a profile using the currently accepted kernel signal and seed versions. */
    public static SkyIslandEcologicalPatchSignalProfile current(
            long rootSeed,
            String namespace,
            double scale) {
        return new SkyIslandEcologicalPatchSignalProfile(
                PlanarValueSignal.VERSION,
                SeedDerivation.VERSION,
                rootSeed,
                namespace,
                scale);
    }

    /** Samples the exact accepted kernel signal at one island-local position. */
    public double sample(SkyIslandLocalPosition position) {
        Objects.requireNonNull(position, "position");
        return PlanarValueSignal.sample(node(), position.x(), position.z());
    }

    /** Reconstructs the exact kernel signal node represented by this profile. */
    public PlanarValueSignalNode node() {
        return node(signalVersion, seedVersion, rootSeed, namespace, scale);
    }

    private static PlanarValueSignalNode node(
            int signalVersion,
            int seedVersion,
            long rootSeed,
            String namespace,
            double scale) {
        return new PlanarValueSignalNode(
                new NodeId("ecology.patch." + namespace),
                GraphValueType.SCALAR_FIELD_2,
                signalVersion,
                seedVersion,
                rootSeed,
                namespace,
                scale);
    }
}
