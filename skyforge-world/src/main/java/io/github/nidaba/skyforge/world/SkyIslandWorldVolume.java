package io.github.nidaba.skyforge.world;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import io.github.nidaba.skyforge.recipes.skyisland.CompiledSkyIslandVolume;
import java.util.Objects;
import java.util.Optional;

/** Backend-neutral handoff for one bounded independently compiled island graph set. */
public record SkyIslandWorldVolume(
        SkyIslandWorldVolumeId id,
        WorldBounds bounds,
        CompiledSkyIslandVolume compiledVolume,
        Optional<SkyIslandDescriptor> authoredDescriptor) {

    /** Preserves compatibility for synthetic/test volumes that have no authored provenance. */
    public SkyIslandWorldVolume(
            SkyIslandWorldVolumeId id,
            WorldBounds bounds,
            CompiledSkyIslandVolume compiledVolume) {
        this(id, bounds, compiledVolume, Optional.empty());
    }

    /** Creates a production world volume while retaining its original authored descriptor. */
    public SkyIslandWorldVolume(
            SkyIslandWorldVolumeId id,
            WorldBounds bounds,
            CompiledSkyIslandVolume compiledVolume,
            SkyIslandDescriptor authoredDescriptor) {
        this(id, bounds, compiledVolume, Optional.of(Objects.requireNonNull(authoredDescriptor, "authoredDescriptor")));
    }

    /** Validates identity against both compiled and, when present, authored provenance. */
    public SkyIslandWorldVolume {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(bounds, "bounds");
        Objects.requireNonNull(compiledVolume, "compiledVolume");
        authoredDescriptor = Objects.requireNonNull(authoredDescriptor, "authoredDescriptor");
        if (compiledVolume.descriptor().seed() != id.geometrySeed()) {
            throw new IllegalArgumentException(
                    "world volume identity seed differs from compiled descriptor seed");
        }
        if (authoredDescriptor.isPresent()
                && authoredDescriptor.orElseThrow().seed() != id.geometrySeed()) {
            throw new IllegalArgumentException(
                    "world volume identity seed differs from authored descriptor seed");
        }
    }

    /** Returns authored provenance for consumers that are defined in authored-descriptor space. */
    public SkyIslandDescriptor requireAuthoredDescriptor() {
        return authoredDescriptor.orElseThrow(() -> new IllegalStateException(
                "world volume has no authored descriptor provenance"));
    }
}
