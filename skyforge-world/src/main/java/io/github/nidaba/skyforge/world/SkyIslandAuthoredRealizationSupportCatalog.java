package io.github.nidaba.skyforge.world;

import io.github.nidaba.skyforge.recipes.skyisland.CertifiedSkyIslandSupportEnvelopeCompiler;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * AUTH-0051 immutable support-certificate catalog keyed by exact AUTH-0046 association identity.
 *
 * <p>Absence is meaningful: an uncertified association receives no invented proof bounds.
 */
public final class SkyIslandAuthoredRealizationSupportCatalog {
    private final SkyIslandAuthoredRealizationCatalog associationCatalog;
    private final Map<String, SkyIslandAuthoredRealizationSupportCertificate> certificates;

    public SkyIslandAuthoredRealizationSupportCatalog(
            SkyIslandAuthoredRealizationCatalog associationCatalog,
            List<SkyIslandAuthoredRealizationSupportCertificate> certificates) {
        this.associationCatalog =
                Objects.requireNonNull(associationCatalog, "associationCatalog");
        Objects.requireNonNull(certificates, "certificates");

        Map<String, SkyIslandAuthoredRealizationAssociation> associationsByToken =
                new HashMap<>();
        for (SkyIslandAuthoredRealizationAssociation association :
                associationCatalog.associations()) {
            associationsByToken.put(association.canonicalToken(), association);
        }

        Map<String, SkyIslandAuthoredRealizationSupportCertificate> built =
                new HashMap<>();
        for (SkyIslandAuthoredRealizationSupportCertificate certificate :
                certificates) {
            certificate = Objects.requireNonNull(certificate, "support certificate");
            String token = certificate.associationToken();
            SkyIslandAuthoredRealizationAssociation expected = associationsByToken.get(token);
            if (expected == null) {
                throw new IllegalArgumentException(
                        "support certificate references an association absent from the catalog");
            }
            if (!expected.equals(certificate.association())) {
                throw new IllegalArgumentException(
                        "support certificate association does not equal the catalog association");
            }
            if (built.put(token, certificate) != null) {
                throw new IllegalArgumentException(
                        "duplicate support certificate for association " + token);
            }
        }
        this.certificates = Map.copyOf(built);
    }

    /**
     * Certifies every association supported by the accepted AUTH-0051 semantic built-in recipe
     * path. Unsupported associations remain absent.
     */
    public static SkyIslandAuthoredRealizationSupportCatalog certifyAccepted(
            SkyIslandAuthoredRealizationCatalog associationCatalog) {
        Objects.requireNonNull(associationCatalog, "associationCatalog");
        CertifiedSkyIslandSupportEnvelopeCompiler compiler =
                new CertifiedSkyIslandSupportEnvelopeCompiler();
        ArrayList<SkyIslandAuthoredRealizationSupportCertificate> certificates =
                new ArrayList<>();
        for (SkyIslandAuthoredRealizationAssociation association :
                associationCatalog.associations()) {
            compiler.certify(association.realizedVolume().compiledVolume())
                    .ifPresent(
                            envelope ->
                                    certificates.add(
                                            new SkyIslandAuthoredRealizationSupportCertificate(
                                                    association, envelope)));
        }
        return new SkyIslandAuthoredRealizationSupportCatalog(
                associationCatalog, certificates);
    }

    /**
     * Binds AUTH-0052 world-volume proof to an already explicit AUTH-0046 association catalog.
     *
     * <p>No authored identity is inferred from the world catalog. Every association must already
     * exist, and its realized volume must equal the exact world-catalog volume with the same ID.
     */
    public static SkyIslandAuthoredRealizationSupportCatalog fromWorldSupport(
            SkyIslandAuthoredRealizationCatalog associationCatalog,
            SkyIslandWorldCatalogSupportBundle worldSupport) {
        Objects.requireNonNull(associationCatalog, "associationCatalog");
        Objects.requireNonNull(worldSupport, "worldSupport");
        if (associationCatalog.realizationRootSeed()
                != worldSupport.catalog().rootSeed()) {
            throw new IllegalArgumentException(
                    "AUTH-0046 realization root differs from AUTH-0052 world catalog root");
        }

        Map<SkyIslandWorldVolumeId, SkyIslandWorldVolume> worldVolumes =
                new HashMap<>();
        for (SkyIslandWorldVolume volume : worldSupport.catalog().volumes()) {
            worldVolumes.put(volume.id(), volume);
        }

        ArrayList<SkyIslandAuthoredRealizationSupportCertificate> certificates =
                new ArrayList<>();
        for (SkyIslandAuthoredRealizationAssociation association :
                associationCatalog.associations()) {
            SkyIslandWorldVolume realized = association.realizedVolume();
            SkyIslandWorldVolume expected = worldVolumes.get(realized.id());
            if (expected == null || !expected.equals(realized)) {
                throw new IllegalArgumentException(
                        "AUTH-0046 association does not bind an exact AUTH-0052 world-catalog volume");
            }
            worldSupport.certificateFor(realized)
                    .ifPresent(
                            worldCertificate ->
                                    certificates.add(
                                            new SkyIslandAuthoredRealizationSupportCertificate(
                                                    association,
                                                    worldCertificate.envelope())));
        }
        return new SkyIslandAuthoredRealizationSupportCatalog(
                associationCatalog, certificates);
    }

    public SkyIslandAuthoredRealizationCatalog associationCatalog() {
        return associationCatalog;
    }

    public Optional<SkyIslandAuthoredRealizationSupportCertificate> certificateFor(
            SkyIslandAuthoredRealizationAssociation association) {
        Objects.requireNonNull(association, "association");
        SkyIslandAuthoredRealizationSupportCertificate certificate =
                certificates.get(association.canonicalToken());
        if (certificate != null && !certificate.association().equals(association)) {
            throw new IllegalArgumentException(
                    "association token collision against support catalog");
        }
        return Optional.ofNullable(certificate);
    }

    public int certifiedCount() {
        return certificates.size();
    }

    public int uncertifiedCount() {
        return associationCatalog.size() - certificates.size();
    }

    public List<SkyIslandAuthoredRealizationSupportCertificate> certificates() {
        return associationCatalog.associations().stream()
                .map(association -> certificates.get(association.canonicalToken()))
                .filter(Objects::nonNull)
                .toList();
    }
}
