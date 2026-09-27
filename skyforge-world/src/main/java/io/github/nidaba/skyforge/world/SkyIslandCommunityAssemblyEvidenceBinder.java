package io.github.nidaba.skyforge.world;

import java.util.List;
import java.util.Objects;

/**
 * Deterministic binder from accepted community suitability to raw authored-realization isolation
 * evidence.
 *
 * <p>The binder performs identity/provenance matching only. It applies no ecological interpretation
 * to nearest-neighbor distances.
 */
public final class SkyIslandCommunityAssemblyEvidenceBinder {

    /**
     * Binds one explicit community-suitability field set to its exact isolation entry.
     *
     * @throws IllegalArgumentException if the profile does not contain the exact authored descriptor
     */
    public SkyIslandCommunityAssemblyEvidence bind(
            SkyIslandCommunitySuitabilityFieldSet communitySuitability,
            SkyIslandAuthoredRealizationIsolationProfile isolationProfile) {
        Objects.requireNonNull(communitySuitability, "communitySuitability");
        Objects.requireNonNull(isolationProfile, "isolationProfile");

        SkyIslandRegionalIsolationEntry match = isolationProfile.islands().stream()
                .filter(entry -> entry.association()
                        .authoredDescriptor()
                        .equals(communitySuitability.descriptor()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "isolation profile does not contain the exact community descriptor"));

        return new SkyIslandCommunityAssemblyEvidence(communitySuitability, match);
    }

    /**
     * Binds every exact association in canonical profile order using current accepted ecology
     * semantics.
     *
     * <p>This is a convenience path, not new ecological authority. It constructs the already accepted
     * #1198 suitability fields from each exact authored descriptor and preserves raw isolation data.
     */
    public List<SkyIslandCommunityAssemblyEvidence> bindCurrentSemantics(
            SkyIslandAuthoredRealizationIsolationProfile isolationProfile) {
        Objects.requireNonNull(isolationProfile, "isolationProfile");
        return isolationProfile.islands().stream()
                .map(entry -> new SkyIslandCommunityAssemblyEvidence(
                        SkyIslandCommunitySuitabilityFieldSet.create(
                                entry.association().authoredDescriptor()),
                        entry))
                .toList();
    }
}
