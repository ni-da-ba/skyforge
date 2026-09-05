package io.github.nidaba.skyforge.world;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * AUTH-0052 immutable world-catalog compilation plus optional proof-grade support metadata.
 *
 * <p>Certificates are keyed by exact world-volume identity and must bind the exact catalog volume.
 * Uncertified volumes remain present in the world catalog without invented support proof.
 */
public final class SkyIslandWorldCatalogSupportBundle {
    private final SkyIslandWorldCatalog catalog;
    private final Map<SkyIslandWorldVolumeId, SkyIslandWorldVolumeSupportCertificate> certificates;

    public SkyIslandWorldCatalogSupportBundle(
            SkyIslandWorldCatalog catalog,
            List<SkyIslandWorldVolumeSupportCertificate> certificates) {
        this.catalog = Objects.requireNonNull(catalog, "catalog");
        Objects.requireNonNull(certificates, "certificates");

        Map<SkyIslandWorldVolumeId, SkyIslandWorldVolume> volumes = new HashMap<>();
        for (SkyIslandWorldVolume volume : catalog.volumes()) {
            volumes.put(volume.id(), volume);
        }

        Map<SkyIslandWorldVolumeId, SkyIslandWorldVolumeSupportCertificate> built =
                new HashMap<>();
        for (SkyIslandWorldVolumeSupportCertificate certificate : certificates) {
            certificate = Objects.requireNonNull(certificate, "support certificate");
            SkyIslandWorldVolume expected = volumes.get(certificate.volumeId());
            if (expected == null) {
                throw new IllegalArgumentException(
                        "support certificate references a world volume absent from the catalog");
            }
            if (!expected.equals(certificate.volume())) {
                throw new IllegalArgumentException(
                        "support certificate does not bind the exact catalog world volume");
            }
            if (!certificate.queryBoundsContainSupport()) {
                throw new IllegalArgumentException(
                        "world query bounds do not contain certified realized support for "
                                + certificate.volumeId().path());
            }
            if (built.put(certificate.volumeId(), certificate) != null) {
                throw new IllegalArgumentException(
                        "duplicate support certificate for world volume "
                                + certificate.volumeId().path());
            }
        }
        this.certificates = Map.copyOf(built);
    }

    public SkyIslandWorldCatalog catalog() {
        return catalog;
    }

    public Optional<SkyIslandWorldVolumeSupportCertificate> certificateFor(
            SkyIslandWorldVolume volume) {
        Objects.requireNonNull(volume, "volume");
        SkyIslandWorldVolumeSupportCertificate certificate =
                certificates.get(volume.id());
        if (certificate != null && !certificate.volume().equals(volume)) {
            throw new IllegalArgumentException(
                    "world volume identity collides with another catalog volume");
        }
        return Optional.ofNullable(certificate);
    }

    public Optional<SkyIslandWorldVolumeSupportCertificate> certificateFor(
            SkyIslandWorldVolumeId volumeId) {
        return Optional.ofNullable(
                certificates.get(Objects.requireNonNull(volumeId, "volumeId")));
    }

    public int certifiedCount() {
        return certificates.size();
    }

    public int uncertifiedCount() {
        return catalog.volumeCount() - certificates.size();
    }

    public List<SkyIslandWorldVolumeSupportCertificate> certificates() {
        ArrayList<SkyIslandWorldVolumeSupportCertificate> ordered = new ArrayList<>();
        for (SkyIslandWorldVolume volume : catalog.volumes()) {
            SkyIslandWorldVolumeSupportCertificate certificate =
                    certificates.get(volume.id());
            if (certificate != null) {
                ordered.add(certificate);
            }
        }
        return List.copyOf(ordered);
    }
}
