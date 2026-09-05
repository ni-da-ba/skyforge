package io.github.nidaba.skyforge.world;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * AUTH-0045 application seam from an AUTH-0044 semantic winner to an adapter-owned concrete
 * material binding.
 *
 * <p>Backend values are opaque generic values passed through transiently. Skyforge never chooses,
 * stores, inspects, compares, serializes, or derives backend material identity. The only selection
 * key available to this contract is the exact stable AUTH-0038 winner key.
 */
public final class SkyIslandMaterialBindingApplicator {
    private SkyIslandMaterialBindingApplicator() {}

    /**
     * Applies the adapter-owned binding for one realized sample.
     *
     * <p>Non-material samples return empty without consulting the binding table. Material-present
     * samples require exactly the concrete value supplied under the AUTH-0044 winner key.
     */
    public static <T> Optional<T> apply(
            SkyIslandMaterialRealizationSelection realization,
            Map<SkyIslandSemanticPaletteBindingKey, ? extends T> bindings) {
        Objects.requireNonNull(realization, "realization");
        Objects.requireNonNull(bindings, "bindings");

        Optional<SkyIslandMaterialBindingApplication> application =
                SkyIslandMaterialBindingApplication.from(realization);
        if (application.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(apply(application.orElseThrow(), bindings));
    }

    /**
     * Applies an already validated AUTH-0045 envelope.
     *
     * <p>A missing or null backend binding is a contract failure. The world layer does not invent a
     * fallback material because that would make backend policy part of native authorship.
     */
    public static <T> T apply(
            SkyIslandMaterialBindingApplication application,
            Map<SkyIslandSemanticPaletteBindingKey, ? extends T> bindings) {
        Objects.requireNonNull(application, "application");
        Objects.requireNonNull(bindings, "bindings");
        SkyIslandSemanticPaletteBindingKey key = application.bindingKey();
        if (!bindings.containsKey(key)) {
            throw new IllegalArgumentException(
                    "missing backend material binding for " + key.canonicalToken());
        }
        T material = bindings.get(key);
        if (material == null) {
            throw new IllegalArgumentException(
                    "backend material binding cannot be null for " + key.canonicalToken());
        }
        return material;
    }
}
