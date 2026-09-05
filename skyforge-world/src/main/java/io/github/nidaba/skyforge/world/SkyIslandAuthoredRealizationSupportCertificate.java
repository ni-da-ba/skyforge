package io.github.nidaba.skyforge.world;

import io.github.nidaba.skyforge.recipes.skyisland.CertifiedSkyIslandSupportEnvelope;
import java.util.Objects;

/**
 * AUTH-0051 proof-grade support certificate bound to one exact AUTH-0046 association.
 *
 * <p>The resulting support bounds contain the certified physical realization and are intentionally
 * independent of broader backend query/reservation bounds.
 */
public record SkyIslandAuthoredRealizationSupportCertificate(
        SkyIslandAuthoredRealizationAssociation association,
        CertifiedSkyIslandSupportEnvelope envelope) {

    public SkyIslandAuthoredRealizationSupportCertificate {
        association = Objects.requireNonNull(association, "association");
        envelope = Objects.requireNonNull(envelope, "envelope");
    }

    public WorldBounds supportBounds() {
        var descriptor =
                association.realizedVolume().compiledVolume().descriptor();
        return new WorldBounds(
                descriptor.centerX() - envelope.maximumHorizontalRadius(),
                descriptor.centerX() + envelope.maximumHorizontalRadius(),
                descriptor.suspensionElevation() - envelope.maximumUndersideDepth(),
                descriptor.suspensionElevation() + envelope.maximumUpperOffset(),
                descriptor.centerZ() - envelope.maximumHorizontalRadius(),
                descriptor.centerZ() + envelope.maximumHorizontalRadius());
    }

    public String associationToken() {
        return association.canonicalToken();
    }
}
