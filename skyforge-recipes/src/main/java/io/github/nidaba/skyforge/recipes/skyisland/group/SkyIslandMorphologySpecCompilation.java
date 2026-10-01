package io.github.nidaba.skyforge.recipes.skyisland.group;

import io.github.nidaba.skyforge.recipes.skyisland.CertifiedSkyIslandSupportEnvelope;
import io.github.nidaba.skyforge.recipes.skyisland.CompiledSkyIslandVolume;
import java.util.Objects;
import java.util.Optional;

/** AUTH-0052 provider-spec compilation result with optional proof-grade support metadata. */
public record SkyIslandMorphologySpecCompilation(
        CompiledSkyIslandVolume volume,
        Optional<CertifiedSkyIslandSupportEnvelope> supportEnvelope) {

    public SkyIslandMorphologySpecCompilation {
        volume = Objects.requireNonNull(volume, "volume");
        supportEnvelope = Objects.requireNonNull(supportEnvelope, "supportEnvelope");
    }
}
