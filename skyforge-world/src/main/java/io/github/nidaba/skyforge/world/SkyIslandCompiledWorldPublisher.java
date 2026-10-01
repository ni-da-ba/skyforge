package io.github.nidaba.skyforge.world;

import java.util.Objects;

/**
 * AUTH-0058 publication gate from accepted proof-backed compilation to backend-consumable world
 * capability.
 *
 * <p>This class does not compile, plan, retry, mutate reservations, or touch backend APIs.
 */
public final class SkyIslandCompiledWorldPublisher {

    public SkyIslandCompiledWorldPublication publish(
            SkyIslandAcceptedConvergenceCompilation compilation,
            long publicationRevision) {
        Objects.requireNonNull(compilation, "compilation");
        long rootSeed = compilation.supportBundle().catalog().rootSeed();
        return new SkyIslandCompiledWorldPublication(
                SkyIslandCompiledWorldPublicationId.of(rootSeed, publicationRevision),
                compilation);
    }
}
