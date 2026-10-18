package io.github.nidaba.skyforge.model.skyisland;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/**
 * Backend-neutral meaning for one stable SkyBiome class.
 *
 * <p>Habitat traits describe regional sky character. Content may map them to species and spawn
 * policy; this authorship definition does not own population rules or dynamic atmosphere.
 */
public record SkyBiomeDefinition(SkyBiomeId id, Set<SkyBiomeTrait> traits) {
    public SkyBiomeDefinition {
        id = Objects.requireNonNull(id, "id");
        Objects.requireNonNull(traits, "traits");
        if (traits.isEmpty()) {
            throw new IllegalArgumentException("SkyBiome definition must have at least one trait");
        }
        EnumSet<SkyBiomeTrait> copy = EnumSet.copyOf(traits);
        if (copy.contains(null)) {
            throw new IllegalArgumentException("SkyBiome traits must not contain null");
        }
        traits = Collections.unmodifiableSet(copy);
    }

    public boolean hasTrait(SkyBiomeTrait trait) {
        return traits.contains(Objects.requireNonNull(trait, "trait"));
    }
}
