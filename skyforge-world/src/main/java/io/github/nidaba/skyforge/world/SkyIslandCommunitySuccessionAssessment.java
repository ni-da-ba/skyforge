package io.github.nidaba.skyforge.world;

import java.util.Objects;
import java.util.Optional;

/**
 * One explicit succession/recovery assessment bound to exact disturbance evidence and profile.
 *
 * <p>The assessment remains separate from local community suitability and dispersal accessibility.
 * Downstream assembly policy may later decide how a particular community interprets this state.
 */
public record SkyIslandCommunitySuccessionAssessment(
        SkyIslandCommunityDisturbanceEvidence disturbanceEvidence,
        SkyIslandCommunitySuccessionProfile successionProfile,
        Optional<SkyIslandCommunitySuccessionState> state) {

    public SkyIslandCommunitySuccessionAssessment {
        disturbanceEvidence =
                Objects.requireNonNull(disturbanceEvidence, "disturbanceEvidence");
        successionProfile = Objects.requireNonNull(successionProfile, "successionProfile");
        state = Objects.requireNonNull(state, "state");
    }

    /** Evaluates one exact disturbance context through one explicit succession profile. */
    public static SkyIslandCommunitySuccessionAssessment evaluate(
            SkyIslandCommunityDisturbanceEvidence evidence,
            SkyIslandCommunitySuccessionProfile profile) {
        Objects.requireNonNull(evidence, "evidence");
        Objects.requireNonNull(profile, "profile");
        return new SkyIslandCommunitySuccessionAssessment(
                evidence,
                profile,
                profile.state(evidence));
    }

    public boolean resolved() {
        return state.isPresent();
    }
}
