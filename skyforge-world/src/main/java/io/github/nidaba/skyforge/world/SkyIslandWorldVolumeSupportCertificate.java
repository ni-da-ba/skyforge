package io.github.nidaba.skyforge.world;

import io.github.nidaba.skyforge.recipes.skyisland.CertifiedSkyIslandSupportEnvelope;
import java.util.Objects;

/** AUTH-0052 proof-grade support envelope bound to one exact world-catalog volume. */
public record SkyIslandWorldVolumeSupportCertificate(
        SkyIslandWorldVolume volume,
        CertifiedSkyIslandSupportEnvelope envelope) {

    public SkyIslandWorldVolumeSupportCertificate {
        volume = Objects.requireNonNull(volume, "volume");
        envelope = Objects.requireNonNull(envelope, "envelope");
    }

    public SkyIslandWorldVolumeId volumeId() {
        return volume.id();
    }

    public WorldBounds supportBounds() {
        var descriptor = volume.compiledVolume().descriptor();
        return new WorldBounds(
                Math.nextDown(
                        descriptor.centerX() - envelope.maximumHorizontalRadius()),
                Math.nextUp(
                        descriptor.centerX() + envelope.maximumHorizontalRadius()),
                Math.nextDown(
                        descriptor.suspensionElevation()
                                - envelope.maximumUndersideDepth()),
                Math.nextUp(
                        descriptor.suspensionElevation()
                                + envelope.maximumUpperOffset()),
                Math.nextDown(
                        descriptor.centerZ() - envelope.maximumHorizontalRadius()),
                Math.nextUp(
                        descriptor.centerZ() + envelope.maximumHorizontalRadius()));
    }

    /** Whether the backend query reservation safely contains the proof-grade support envelope. */
    public boolean queryBoundsContainSupport() {
        return volume.bounds().contains(supportBounds());
    }
}
