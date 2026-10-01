package io.github.nidaba.skyforge.world.content;

import io.github.nidaba.skyforge.world.SkyIslandAuthoredRealizationAssociation;
import io.github.nidaba.skyforge.world.SkyIslandSurfaceAccessCapabilityCell;
import io.github.nidaba.skyforge.world.SkyIslandSurfaceAccessCapabilityProfile;
import io.github.nidaba.skyforge.world.SkyIslandSurfaceAccessRay;
import java.util.Objects;
import java.util.Optional;

/**
 * Content-owned deterministic site/route binding for the accepted C24 FREIGHT_TRANSFER_EDGE role.
 *
 * <p>The policy operationalizes the accepted C24/AUTH-0096/AUTH-0097 predicate on one exact
 * authored-realization association. It selects only by the already-canonical AUTH-0097 anchor and
 * direction order: the first physically supported anchor with an observed-open ray, then the first
 * observed-open ray at that anchor. It introduces no score, threshold, route geometry, structure
 * geometry, settlement meaning, petroleum progression, or backend-specific policy.
 */
public final class FreightTransferEdgeSitePolicy {
    public enum Outcome {
        SITE_AND_ROUTE_BOUND,
        REPLAN_REQUIRED
    }

    /**
     * Exact semantic handoff retained by downstream Implementation.
     *
     * <p>{@code routeRay} authorizes only the route connection direction represented by the accepted
     * AUTH-0097 evidence. Exact route geometry, length, clearance, traversal, and structure geometry
     * remain downstream Implementation concerns.
     */
    public record Selection(
            SkyIslandAuthoredRealizationAssociation association,
            SkyIslandSurfaceAccessCapabilityCell siteEvidence,
            SkyIslandSurfaceAccessRay routeRay) {
        public Selection {
            association = Objects.requireNonNull(association, "association");
            siteEvidence = Objects.requireNonNull(siteEvidence, "siteEvidence");
            routeRay = Objects.requireNonNull(routeRay, "routeRay");

            if (!siteEvidence.sourceCell().physicalSurfacePresent()) {
                throw new IllegalArgumentException(
                        "freight-edge site requires accepted AUTH-0096 physical surface support");
            }
            if (!siteEvidence.rays().contains(routeRay) || !routeRay.observedOpenSample()) {
                throw new IllegalArgumentException(
                        "freight-edge route binding requires an observed-open AUTH-0097 ray from the selected site");
            }
        }

        public int watershedCellIndex() {
            return siteEvidence.watershedCellIndex();
        }
    }

    public record Plan(Outcome outcome, Optional<Selection> selection) {
        public Plan {
            outcome = Objects.requireNonNull(outcome, "outcome");
            selection = Objects.requireNonNull(selection, "selection");
            if (outcome == Outcome.SITE_AND_ROUTE_BOUND && selection.isEmpty()) {
                throw new IllegalArgumentException("bound freight-edge plan requires a selection");
            }
            if (outcome == Outcome.REPLAN_REQUIRED && selection.isPresent()) {
                throw new IllegalArgumentException("replan freight-edge plan cannot retain a selection");
            }
        }

        public boolean requiresReplan() {
            return outcome == Outcome.REPLAN_REQUIRED;
        }
    }

    /**
     * Selects the first eligible C24 freight-edge site and first observed-open route ray in unchanged
     * AUTH-0097 canonical order. Absence fails closed to replan.
     */
    public Plan plan(SkyIslandSurfaceAccessCapabilityProfile accessProfile) {
        Objects.requireNonNull(accessProfile, "accessProfile");

        for (SkyIslandSurfaceAccessCapabilityCell cell : accessProfile.cells()) {
            if (!cell.sourceCell().physicalSurfacePresent()) {
                continue;
            }
            for (SkyIslandSurfaceAccessRay ray : cell.rays()) {
                if (ray.observedOpenSample()) {
                    return new Plan(
                            Outcome.SITE_AND_ROUTE_BOUND,
                            Optional.of(new Selection(accessProfile.association(), cell, ray)));
                }
            }
        }
        return new Plan(Outcome.REPLAN_REQUIRED, Optional.empty());
    }
}
