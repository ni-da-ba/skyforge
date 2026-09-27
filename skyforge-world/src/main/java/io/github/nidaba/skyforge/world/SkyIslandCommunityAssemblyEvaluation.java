package io.github.nidaba.skyforge.world;

import java.util.Objects;
import java.util.OptionalDouble;

/**
 * Transparent evaluation of one explicit community-assembly profile at one local position.
 *
 * <p>The result retains each contributing factor separately. Composite assembly support is a
 * profile-specific support signal, not occupancy probability or a community winner.
 */
public record SkyIslandCommunityAssemblyEvaluation(
        SkyIslandCommunityAssemblyProfile profile,
        SkyIslandCommunityDisturbanceEvidence disturbanceEvidence,
        SkyIslandLocalPosition position,
        double localSuitability,
        SkyIslandCommunityAssemblyAccessibility dispersalAccessibility,
        SkyIslandCommunitySuccessionAssessment successionAssessment,
        OptionalDouble successionAffinity,
        OptionalDouble assemblySupport) {

    public SkyIslandCommunityAssemblyEvaluation {
        profile = Objects.requireNonNull(profile, "profile");
        disturbanceEvidence =
                Objects.requireNonNull(disturbanceEvidence, "disturbanceEvidence");
        position = Objects.requireNonNull(position, "position");
        dispersalAccessibility =
                Objects.requireNonNull(dispersalAccessibility, "dispersalAccessibility");
        successionAssessment =
                Objects.requireNonNull(successionAssessment, "successionAssessment");
        successionAffinity = Objects.requireNonNull(successionAffinity, "successionAffinity");
        assemblySupport = Objects.requireNonNull(assemblySupport, "assemblySupport");

        requireNormalized("localSuitability", localSuitability);
        successionAffinity.ifPresent(
                value -> requireNormalized("successionAffinity", value));
        assemblySupport.ifPresent(
                value -> requireNormalized("assemblySupport", value));

        if (!dispersalAccessibility.evidence()
                .equals(disturbanceEvidence.assemblyEvidence())) {
            throw new IllegalArgumentException(
                    "dispersal accessibility and disturbance evidence must share assembly provenance");
        }
        if (!successionAssessment.disturbanceEvidence().equals(disturbanceEvidence)) {
            throw new IllegalArgumentException(
                    "succession assessment and evaluation must share disturbance provenance");
        }
    }

    public boolean supportResolved() {
        return assemblySupport.isPresent();
    }

    private static void requireNormalized(String name, double value) {
        if (!Double.isFinite(value) || value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException(name + " must be finite and in [0, 1]");
        }
    }
}
