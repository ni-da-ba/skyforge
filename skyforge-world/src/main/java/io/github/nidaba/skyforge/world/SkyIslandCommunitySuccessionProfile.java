package io.github.nidaba.skyforge.world;

import java.util.Optional;

/**
 * Backend-neutral interpretation of explicit disturbance evidence into succession/recovery state.
 *
 * <p>Profiles are responsible for declaring their own temporal response semantics. Missing
 * disturbance evidence must remain unresolved rather than being reinterpreted as a pristine state.
 */
@FunctionalInterface
public interface SkyIslandCommunitySuccessionProfile {

    /** Evaluates one explicit disturbance-evidence context. */
    Optional<SkyIslandCommunitySuccessionState> state(
            SkyIslandCommunityDisturbanceEvidence evidence);
}
