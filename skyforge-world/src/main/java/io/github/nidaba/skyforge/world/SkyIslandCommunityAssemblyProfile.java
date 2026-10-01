package io.github.nidaba.skyforge.world;

import java.util.Objects;
import java.util.OptionalDouble;

/**
 * Explicit backend-neutral assembly policy for one accepted structural community archetype.
 *
 * <p>The profile declares every interpretive step: community suitability selection, dispersal
 * response, succession-state interpretation, succession affinity, and factor combination. It never
 * asserts occupancy.
 */
public record SkyIslandCommunityAssemblyProfile(
        SkyIslandCommunityArchetype community,
        SkyIslandCommunityDispersalProfile dispersalProfile,
        SkyIslandCommunitySuccessionProfile successionProfile,
        SkyIslandCommunitySuccessionAffinityProfile successionAffinityProfile,
        SkyIslandCommunityAssemblyCombiner combiner) {

    public SkyIslandCommunityAssemblyProfile {
        community = Objects.requireNonNull(community, "community");
        dispersalProfile = Objects.requireNonNull(dispersalProfile, "dispersalProfile");
        successionProfile = Objects.requireNonNull(successionProfile, "successionProfile");
        successionAffinityProfile =
                Objects.requireNonNull(successionAffinityProfile, "successionAffinityProfile");
        combiner = Objects.requireNonNull(combiner, "combiner");
    }

    /**
     * Evaluates this profile against exact disturbance/assembly evidence at one island-local position.
     */
    public SkyIslandCommunityAssemblyEvaluation evaluate(
            SkyIslandCommunityDisturbanceEvidence disturbanceEvidence,
            SkyIslandLocalPosition position) {
        Objects.requireNonNull(disturbanceEvidence, "disturbanceEvidence");
        Objects.requireNonNull(position, "position");

        SkyIslandCommunityAssemblyEvidence assemblyEvidence =
                disturbanceEvidence.assemblyEvidence();
        SkyIslandCommunitySuitabilityFieldSet communities =
                assemblyEvidence.communitySuitability();

        double localSuitability =
                requireNormalized(
                        "localSuitability",
                        community.field(communities).sample(position));

        SkyIslandCommunityAssemblyAccessibility accessibility =
                SkyIslandCommunityAssemblyAccessibility.evaluate(
                        assemblyEvidence,
                        dispersalProfile);

        SkyIslandCommunitySuccessionAssessment succession =
                SkyIslandCommunitySuccessionAssessment.evaluate(
                        disturbanceEvidence,
                        successionProfile);

        OptionalDouble successionAffinity =
                Objects.requireNonNull(
                        successionAffinityProfile.affinity(succession),
                        "succession affinity profile result");
        successionAffinity.ifPresent(
                value -> requireNormalized("successionAffinity", value));

        OptionalDouble support =
                Objects.requireNonNull(
                        combiner.support(
                                localSuitability,
                                accessibility.accessibility(),
                                successionAffinity),
                        "assembly combiner result");
        support.ifPresent(value -> requireNormalized("assemblySupport", value));

        return new SkyIslandCommunityAssemblyEvaluation(
                this,
                disturbanceEvidence,
                position,
                localSuitability,
                accessibility,
                succession,
                successionAffinity,
                support);
    }

    private static double requireNormalized(String name, double value) {
        if (!Double.isFinite(value) || value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException(name + " must be finite and in [0, 1]");
        }
        return value;
    }
}
