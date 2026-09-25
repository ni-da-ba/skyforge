package io.github.nidaba.skyforge.world;

import java.util.Objects;
import java.util.Optional;

/** Success-or-failure result for constrained longitudinal hydraulic profile solving. */
public record SkyIslandHydraulicProfileSolveResult(
        Optional<SkyIslandHydraulicLongitudinalProfile> profile,
        Optional<SkyIslandHydraulicProfileFailureReason> failureReason,
        int failureStation) {

    public SkyIslandHydraulicProfileSolveResult {
        profile = Objects.requireNonNull(profile, "profile");
        failureReason = Objects.requireNonNull(failureReason, "failureReason");
        if (profile.isPresent() == failureReason.isPresent()) {
            throw new IllegalArgumentException("solve result must contain exactly one of profile or failure reason");
        }
        if (profile.isPresent() && failureStation != -1) {
            throw new IllegalArgumentException("successful solve must use failureStation=-1");
        }
        if (failureReason.isPresent() && failureStation < 0) {
            throw new IllegalArgumentException("failed solve requires a non-negative failure station");
        }
    }

    public static SkyIslandHydraulicProfileSolveResult success(
            SkyIslandHydraulicLongitudinalProfile profile) {
        return new SkyIslandHydraulicProfileSolveResult(
                Optional.of(Objects.requireNonNull(profile, "profile")),
                Optional.empty(),
                -1);
    }

    public static SkyIslandHydraulicProfileSolveResult failure(
            SkyIslandHydraulicProfileFailureReason reason,
            int station) {
        return new SkyIslandHydraulicProfileSolveResult(
                Optional.empty(),
                Optional.of(Objects.requireNonNull(reason, "reason")),
                station);
    }

    public boolean feasible() {
        return profile.isPresent();
    }
}
