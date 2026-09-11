package io.github.nidaba.skyforge.world.content;

import io.github.nidaba.skyforge.world.SkyIslandAuthoredRealizationAssociation;
import io.github.nidaba.skyforge.world.SkyIslandSurfaceAccessCapabilityCell;
import io.github.nidaba.skyforge.world.SkyIslandSurfaceAccessCapabilityProfile;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;

/**
 * CONTENT C27 Bootstrap Guild-destination policy over accepted AUTH-0096/AUTH-0097 evidence.
 *
 * <p>The policy intentionally consumes only physical-surface presence and an observed-open ray from
 * the accepted site/access profile. It adds no terrain score, length, grade, clearance, runway, or
 * Minecraft geometry threshold. Reachability is an explicit upstream Bootstrap-route assertion, not
 * a second geometry graph.
 */
public final class BootstrapGuildDestinationPolicy {
    public enum HallService {
        ACCOUNT_ACCESS,
        BELLANCA_CLAIM_AND_RESTITUTION,
        BASIC_MARKET,
        BASIC_CONTRACT_ACCESS,
        BASIC_ROUTE_AND_SETTLEMENT_INFORMATION
    }

    public enum Outcome {
        DESTINATION_AVAILABLE,
        REPLAN_REQUIRED
    }

    public record Candidate(
            String destinationId,
            SkyIslandAuthoredRealizationAssociation association,
            SkyIslandSurfaceAccessCapabilityCell siteEvidence,
            EnumSet<HallService> services,
            boolean intendedReachable) {
        public Candidate {
            destinationId = requireId(destinationId, "destinationId");
            association = Objects.requireNonNull(association, "association");
            siteEvidence = Objects.requireNonNull(siteEvidence, "siteEvidence");
            services = EnumSet.copyOf(Objects.requireNonNull(services, "services"));
            if (!siteEvidence.sourceCell().physicalSurfacePresent()
                    || siteEvidence.rays().stream().noneMatch(ray -> ray.observedOpenSample())) {
                throw new IllegalArgumentException(
                        "Guild destination requires accepted AUTH-0096 surface support and AUTH-0097 open access evidence");
            }
            if (!services.containsAll(requiredHallServices())) {
                throw new IllegalArgumentException("Guild Hall candidate lacks the Bootstrap MVP services");
            }
        }

        /** Any fully capable Hall can resolve the network-owned Bellanca claim. */
        public boolean resolvesBellancaClaim() {
            return services.contains(HallService.BELLANCA_CLAIM_AND_RESTITUTION);
        }
    }

    public record Plan(Outcome outcome, List<Candidate> canonicalCandidates, Candidate guaranteedDestination) {
        public Plan {
            outcome = Objects.requireNonNull(outcome, "outcome");
            canonicalCandidates = List.copyOf(Objects.requireNonNull(canonicalCandidates, "canonicalCandidates"));
            if (canonicalCandidates.stream().map(Candidate::destinationId).distinct().count()
                    != canonicalCandidates.size()) {
                throw new IllegalArgumentException("Guild destination ids must be unique");
            }
            Candidate expected = canonicalCandidates.stream().filter(Candidate::intendedReachable).findFirst().orElse(null);
            if (outcome == Outcome.DESTINATION_AVAILABLE && (expected == null || guaranteedDestination != expected)) {
                throw new IllegalArgumentException("Bootstrap guarantee must be the first reachable canonical candidate");
            }
            if (outcome == Outcome.REPLAN_REQUIRED && (expected != null || guaranteedDestination != null)) {
                throw new IllegalArgumentException("unreachable Bootstrap candidates require replan, not a destination");
            }
        }

        public boolean requiresReplan() {
            return outcome == Outcome.REPLAN_REQUIRED;
        }
    }

    /**
     * Derives Hall candidates in unchanged AUTH-0097 anchor order for one exact association.
     * Caller supplies the route-planner's reachability assertion; this class does not infer routing.
     */
    public List<Candidate> candidates(
            String destinationIdPrefix,
            SkyIslandSurfaceAccessCapabilityProfile accessProfile,
            boolean intendedReachable) {
        destinationIdPrefix = requireId(destinationIdPrefix, "destinationIdPrefix");
        Objects.requireNonNull(accessProfile, "accessProfile");
        ArrayList<Candidate> candidates = new ArrayList<>();
        for (SkyIslandSurfaceAccessCapabilityCell cell : accessProfile.cells()) {
            if (cell.sourceCell().physicalSurfacePresent()
                    && cell.rays().stream().anyMatch(ray -> ray.observedOpenSample())) {
                candidates.add(new Candidate(
                        destinationIdPrefix + "-" + cell.watershedCellIndex(),
                        accessProfile.association(),
                        cell,
                        requiredHallServices(),
                        intendedReachable));
            }
        }
        return List.copyOf(candidates);
    }

    /** Selects only by supplied canonical order; no evidence magnitude becomes a hidden ranking score. */
    public Plan plan(List<Candidate> canonicalCandidates) {
        canonicalCandidates = List.copyOf(Objects.requireNonNull(canonicalCandidates, "canonicalCandidates"));
        Candidate guaranteed = canonicalCandidates.stream().filter(Candidate::intendedReachable).findFirst().orElse(null);
        return new Plan(
                guaranteed == null ? Outcome.REPLAN_REQUIRED : Outcome.DESTINATION_AVAILABLE,
                canonicalCandidates,
                guaranteed);
    }

    public static EnumSet<HallService> requiredHallServices() {
        return EnumSet.allOf(HallService.class);
    }

    private static String requireId(String value, String name) {
        value = Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
