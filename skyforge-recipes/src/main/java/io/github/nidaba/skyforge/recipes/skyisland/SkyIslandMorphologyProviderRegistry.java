package io.github.nidaba.skyforge.recipes.skyisland;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.TreeMap;

/** Immutable deterministic lookup registry for explicit morphology providers. */
public final class SkyIslandMorphologyProviderRegistry {
    private final NavigableMap<MorphologyProviderId, SkyIslandMorphologyProvider> providers;

    private SkyIslandMorphologyProviderRegistry(
            NavigableMap<MorphologyProviderId, SkyIslandMorphologyProvider> providers) {
        this.providers = Collections.unmodifiableNavigableMap(new TreeMap<>(providers));
    }

    /** Creates an empty registry builder. */
    public static Builder builder() {
        return new Builder();
    }

    /** Resolves one provider or throws if the stable ID is unknown. */
    public SkyIslandMorphologyProvider require(MorphologyProviderId id) {
        SkyIslandMorphologyProvider provider = providers.get(Objects.requireNonNull(id, "id"));
        if (provider == null) {
            throw new IllegalArgumentException("unknown morphology provider: " + id);
        }
        return provider;
    }

    /** Returns canonical sorted provider IDs independent of registration order. */
    public List<MorphologyProviderId> ids() {
        return List.copyOf(providers.navigableKeySet());
    }

    /** Returns all providers in canonical ID order. */
    public Collection<SkyIslandMorphologyProvider> providers() {
        return List.copyOf(providers.values());
    }

    /** Mutable construction phase for one immutable registry snapshot. */
    public static final class Builder {
        private final Map<MorphologyProviderId, SkyIslandMorphologyProvider> providers = new TreeMap<>();

        /** Registers one provider, rejecting duplicate stable IDs. */
        public Builder register(SkyIslandMorphologyProvider provider) {
            Objects.requireNonNull(provider, "provider");
            MorphologyProviderId id = Objects.requireNonNull(provider.id(), "provider.id()");
            SkyIslandMorphologyProvider previous = providers.putIfAbsent(id, provider);
            if (previous != null) {
                throw new IllegalArgumentException("duplicate morphology provider id: " + id);
            }
            return this;
        }

        /** Registers every provider in the supplied iterable. */
        public Builder registerAll(Iterable<? extends SkyIslandMorphologyProvider> values) {
            Objects.requireNonNull(values, "values");
            for (SkyIslandMorphologyProvider provider : values) {
                register(provider);
            }
            return this;
        }

        /** Builds one immutable deterministic registry snapshot. */
        public SkyIslandMorphologyProviderRegistry build() {
            return new SkyIslandMorphologyProviderRegistry(new TreeMap<>(providers));
        }
    }
}
