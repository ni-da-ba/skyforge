package io.github.nidaba.skyforge.world;

import io.github.nidaba.skyforge.recipes.skyisland.archipelago.SkyIslandArchipelagoGroupPlan;
import io.github.nidaba.skyforge.recipes.skyisland.archipelago.SkyIslandArchipelagoPlan;
import java.util.Objects;

/** AUTH-0057 proof-backed world compilation bound to one accepted AUTH-0056 convergence report. */
public record SkyIslandAcceptedConvergenceCompilation(
        SkyIslandSupportConvergenceReport convergence,
        SkyIslandSupportReservationPreflightReport reproducedPreflight,
        SkyIslandWorldCatalogSupportBundle supportBundle) {

    public SkyIslandAcceptedConvergenceCompilation {
        convergence = Objects.requireNonNull(convergence, "convergence");
        reproducedPreflight =
                Objects.requireNonNull(reproducedPreflight, "reproducedPreflight");
        supportBundle = Objects.requireNonNull(supportBundle, "supportBundle");

        if (!convergence.accepted()) {
            throw new IllegalArgumentException(
                    "AUTH-0057 compilation requires ACCEPTED_ONE_PASS convergence");
        }
        SkyIslandArchipelagoPlan plan = convergence.freshPlan().orElseThrow();
        if (!reproducedPreflight.equals(convergence.freshPreflight().orElseThrow())) {
            throw new IllegalArgumentException(
                    "AUTH-0057 reproduced preflight differs from accepted convergence preflight");
        }
        if (!reproducedPreflight.admitted()) {
            throw new IllegalArgumentException(
                    "AUTH-0057 reproduced preflight must remain admitted");
        }
        if (!supportBundle.fullyCertified()) {
            throw new IllegalArgumentException(
                    "AUTH-0057 compiled support bundle must be fully certified");
        }
        if (supportBundle.catalog().rootSeed() != plan.rootSeed()) {
            throw new IllegalArgumentException(
                    "AUTH-0057 compiled catalog root seed differs from accepted fresh plan");
        }
        if (supportBundle.catalog().volumeCount() != plan.totalMemberCount()) {
            throw new IllegalArgumentException(
                    "AUTH-0057 compiled catalog volume count differs from accepted fresh plan");
        }

        int volumeIndex = 0;
        for (SkyIslandArchipelagoGroupPlan group : plan.groups()) {
            for (int memberOrdinal = 0;
                    memberOrdinal < group.groupPlan().memberCount();
                    memberOrdinal++) {
                var member = group.groupPlan().members().get(memberOrdinal);
                SkyIslandWorldVolumeId expected =
                        new SkyIslandWorldVolumeId(
                                plan.rootSeed(),
                                group.identifier(),
                                group.ordinal(),
                                memberOrdinal,
                                member.descriptor().seed());
                SkyIslandWorldVolume actual =
                        supportBundle.catalog().volumes().get(volumeIndex++);
                if (!expected.equals(actual.id())) {
                    throw new IllegalArgumentException(
                            "AUTH-0057 compiled world-volume identity diverges from accepted fresh plan");
                }
                if (supportBundle.certificateFor(actual).isEmpty()) {
                    throw new IllegalArgumentException(
                            "AUTH-0057 accepted compiled volume lacks support certificate");
                }
            }
        }
    }

    public int compiledVolumeCount() {
        return supportBundle.catalog().volumeCount();
    }

    public int certifiedVolumeCount() {
        return supportBundle.certifiedCount();
    }
}
