package io.github.nidaba.skyforge.world;

import java.util.Objects;

/**
 * AUTH-0059 one backend-neutral query entry retaining publication identity and exact support proof.
 */
public record SkyIslandPublishedWorldEntry(
        SkyIslandCompiledWorldPublication publication,
        SkyIslandWorldVolume volume,
        SkyIslandWorldVolumeSupportCertificate supportCertificate) {

    public SkyIslandPublishedWorldEntry {
        publication = Objects.requireNonNull(publication, "publication");
        volume = Objects.requireNonNull(volume, "volume");
        supportCertificate = Objects.requireNonNull(supportCertificate, "supportCertificate");

        SkyIslandWorldVolume catalogVolume =
                publication.catalog().volumes().stream()
                        .filter(candidate -> candidate.id().equals(volume.id()))
                        .findFirst()
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "published entry volume is absent from publication catalog"));
        if (!catalogVolume.equals(volume)) {
            throw new IllegalArgumentException(
                    "published entry volume differs from exact publication catalog volume");
        }

        SkyIslandWorldVolumeSupportCertificate expected =
                publication.compilation().supportBundle()
                        .certificateFor(volume.id())
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "published entry volume lacks support certificate"));
        if (!expected.equals(supportCertificate)
                || !supportCertificate.volume().equals(volume)) {
            throw new IllegalArgumentException(
                    "published entry support certificate differs from exact publication proof");
        }
    }

    public static SkyIslandPublishedWorldEntry of(
            SkyIslandCompiledWorldPublication publication,
            SkyIslandWorldVolume volume) {
        Objects.requireNonNull(publication, "publication");
        Objects.requireNonNull(volume, "volume");
        SkyIslandWorldVolumeSupportCertificate certificate =
                publication.compilation().supportBundle()
                        .certificateFor(volume.id())
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "published entry volume lacks support certificate"));
        return new SkyIslandPublishedWorldEntry(publication, volume, certificate);
    }

    public SkyIslandCompiledWorldPublicationId publicationId() {
        return publication.id();
    }

    public WorldBounds certifiedSupportBounds() {
        return supportCertificate.supportBounds();
    }
}
