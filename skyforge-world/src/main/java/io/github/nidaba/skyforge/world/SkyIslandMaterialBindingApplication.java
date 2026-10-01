package io.github.nidaba.skyforge.world;

import java.util.Objects;
import java.util.Optional;

/**
 * AUTH-0045 backend-neutral application envelope for one realized semantic material winner.
 *
 * <p>The envelope retains only the exact AUTH-0044 realization and its winning stable AUTH-0038
 * binding key. Concrete backend material identity remains outside the world model.
 */
public record SkyIslandMaterialBindingApplication(
        SkyIslandMaterialRealizationSelection realization,
        SkyIslandSemanticPaletteBindingKey bindingKey) {

    public SkyIslandMaterialBindingApplication {
        realization = Objects.requireNonNull(realization, "realization");
        bindingKey = Objects.requireNonNull(bindingKey, "bindingKey");
        if (!realization.materialPresent()) {
            throw new IllegalArgumentException(
                    "material-binding application requires an AUTH-0044 material winner");
        }
        SkyIslandSemanticPaletteBindingKey winnerKey =
                realization.winnerBindingKey().orElseThrow();
        if (!winnerKey.equals(bindingKey)) {
            throw new IllegalArgumentException(
                    "material-binding application key must equal the AUTH-0044 winner key");
        }
    }

    /**
     * Creates an application envelope only where AUTH-0044 authored material is present.
     *
     * <p>Outside-island and authored cave void therefore produce no application request.
     */
    public static Optional<SkyIslandMaterialBindingApplication> from(
            SkyIslandMaterialRealizationSelection realization) {
        Objects.requireNonNull(realization, "realization");
        return realization.winnerBindingKey()
                .map(key -> new SkyIslandMaterialBindingApplication(realization, key));
    }

    public SkyIslandSemanticMaterialPaletteRole role() {
        return realization.winner().role();
    }

    public SkyIslandMaterialResolutionDecision resolutionDecision() {
        return realization.winner().decision();
    }

    public SkyIslandMaterialBindingRequest request() {
        return realization.winner().use().request();
    }
}
