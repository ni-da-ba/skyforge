package io.github.nidaba.skyforge.world.content;

import io.github.nidaba.skyforge.world.SkyIslandAuthoredRealizationAssociation;
import io.github.nidaba.skyforge.world.SkyIslandRegionalPetroleumSystemOpportunityEntry;
import io.github.nidaba.skyforge.world.SkyIslandSurfaceAccessCapabilityCell;
import io.github.nidaba.skyforge.world.SkyIslandSurfaceAccessCapabilityProfile;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * CONTENT C24 coarse petroleum-infrastructure candidate matcher over accepted C23/AUTH-0096/AUTH-0097
 * evidence.
 *
 * <p>This matcher nominates broad local candidates only. It does not select a site or prove concrete
 * pumpjack, refinery, freight, runway, dock, structure, or deposit geometry.
 */
public final class SkyIslandPetroleumInfrastructureCandidateMatcher {
    public enum InfrastructureRole {
        PETROLEUM_EXTRACTION_INTERFACE,
        REFINERY_PROCESSING,
        FREIGHT_TRANSFER_EDGE
    }

    public record Candidate(
            InfrastructureRole role,
            SkyIslandAuthoredRealizationAssociation association,
            SkyIslandSurfaceAccessCapabilityCell evidence) {
        public Candidate {
            role = Objects.requireNonNull(role, "role");
            association = Objects.requireNonNull(association, "association");
            evidence = Objects.requireNonNull(evidence, "evidence");

            if (!evidence.sourceCell().physicalSurfacePresent()) {
                throw new IllegalArgumentException(
                        "C24 infrastructure candidates require accepted physical surface support");
            }
            if (role == InfrastructureRole.FREIGHT_TRANSFER_EDGE
                    && evidence.rays().stream().noneMatch(ray -> ray.observedOpenSample())) {
                throw new IllegalArgumentException(
                        "C24 freight-edge candidate requires accepted observed-open directional evidence");
            }
        }
    }

    /**
     * Returns coarse candidates on one exact AUTH-0097 island profile within the C23 province.
     *
     * <p>Candidate order is the unchanged AUTH-0097 anchor order. No candidate is promoted to a
     * selected site.
     */
    public List<Candidate> candidates(
            SkyIslandPetroleumProvincePlanner.Plan provincePlan,
            SkyIslandSurfaceAccessCapabilityProfile accessProfile,
            InfrastructureRole role) {
        Objects.requireNonNull(provincePlan, "provincePlan");
        Objects.requireNonNull(accessProfile, "accessProfile");
        Objects.requireNonNull(role, "role");

        SkyIslandAuthoredRealizationAssociation association = accessProfile.association();
        requirePublishedRegionMembership(provincePlan, association);

        boolean petroleumEligible = provincePlan.canonicalEligibleCandidates().stream()
                .map(SkyIslandRegionalPetroleumSystemOpportunityEntry::association)
                .anyMatch(association::equals);
        if (!roleAllowsAssociation(role, petroleumEligible)) {
            return List.of();
        }

        ArrayList<Candidate> result = new ArrayList<>();
        for (SkyIslandSurfaceAccessCapabilityCell cell : accessProfile.cells()) {
            if (!cell.sourceCell().physicalSurfacePresent()) {
                continue;
            }
            if (role == InfrastructureRole.FREIGHT_TRANSFER_EDGE
                    && cell.rays().stream().noneMatch(ray -> ray.observedOpenSample())) {
                continue;
            }
            result.add(new Candidate(role, association, cell));
        }
        return List.copyOf(result);
    }

    static boolean roleAllowsAssociation(InfrastructureRole role, boolean petroleumEligible) {
        Objects.requireNonNull(role, "role");
        return switch (role) {
            case PETROLEUM_EXTRACTION_INTERFACE -> petroleumEligible;
            case REFINERY_PROCESSING, FREIGHT_TRANSFER_EDGE -> true;
        };
    }

    private static void requirePublishedRegionMembership(
            SkyIslandPetroleumProvincePlanner.Plan plan,
            SkyIslandAuthoredRealizationAssociation association) {
        boolean present = plan.sourceProfile().islands().stream()
                .map(SkyIslandRegionalPetroleumSystemOpportunityEntry::association)
                .anyMatch(association::equals);
        if (!present) {
            throw new IllegalArgumentException(
                    "C24 site/access profile must belong to the exact C23/AUTH-0100 published region");
        }
    }
}
