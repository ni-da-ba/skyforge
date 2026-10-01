package io.github.nidaba.skyforge.world;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Exact F2C solve/qualification evidence for one semantic reach. */
public record SkyIslandBoundedHydraulicReachOutcome(
        SkyIslandHydraulicReachSkeleton skeleton,
        SkyIslandBoundedHydraulicReachStatus status,
        Optional<SkyIslandHydraulicQpResult> solverResult,
        Optional<SkyIslandHydraulicReachGeometry> hydraulicReach,
        Optional<SkyIslandGeomorphicReachQualification> qualification,
        List<SkyIslandQualifiedFluvialDeferralReason> deferralReasons,
        Optional<String> diagnostic) {

    public SkyIslandBoundedHydraulicReachOutcome {
        skeleton = Objects.requireNonNull(skeleton, "skeleton");
        status = Objects.requireNonNull(status, "status");
        solverResult = Objects.requireNonNull(solverResult, "solverResult");
        hydraulicReach = Objects.requireNonNull(hydraulicReach, "hydraulicReach");
        qualification = Objects.requireNonNull(qualification, "qualification");
        deferralReasons = List.copyOf(deferralReasons);
        deferralReasons.forEach(reason -> Objects.requireNonNull(reason, "deferral reason"));
        diagnostic = Objects.requireNonNull(diagnostic, "diagnostic");

        switch (status) {
            case TRANSITION_DEFERRED -> {
                if (deferralReasons.isEmpty()
                        || solverResult.isPresent()
                        || hydraulicReach.isPresent()
                        || qualification.isPresent()) {
                    throw new IllegalArgumentException(
                            "transition-deferred outcome must contain only deferral evidence");
                }
            }
            case INFEASIBLE, NUMERICAL_FAILURE -> {
                if (!deferralReasons.isEmpty()
                        || hydraulicReach.isPresent()
                        || qualification.isPresent()
                        || diagnostic.isEmpty()) {
                    throw new IllegalArgumentException(
                            "failed solve outcome must contain diagnostic and no realized geometry");
                }
            }
            case SOLVED_QUALIFIED, SOLVED_REJECTED -> {
                if (!deferralReasons.isEmpty()
                        || solverResult.isEmpty()
                        || solverResult.get().status() != SkyIslandHydraulicQpStatus.SOLVED
                        || hydraulicReach.isEmpty()
                        || qualification.isEmpty()) {
                    throw new IllegalArgumentException(
                            "solved outcome requires solved QP, geometry, and D2 qualification");
                }
                if ((status == SkyIslandBoundedHydraulicReachStatus.SOLVED_QUALIFIED)
                        != qualification.get().accepted()) {
                    throw new IllegalArgumentException(
                            "solved outcome status must agree with D2 qualification");
                }
            }
        }
    }

    public boolean accepted() {
        return status == SkyIslandBoundedHydraulicReachStatus.SOLVED_QUALIFIED;
    }
}
