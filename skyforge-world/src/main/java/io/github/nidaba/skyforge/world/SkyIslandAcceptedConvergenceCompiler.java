package io.github.nidaba.skyforge.world;

import io.github.nidaba.skyforge.recipes.skyisland.SkyIslandMorphologyProviderRegistry;
import io.github.nidaba.skyforge.recipes.skyisland.archipelago.SkyIslandArchipelagoPlan;
import java.util.Objects;

/**
 * AUTH-0057 hands one ACCEPTED_ONE_PASS convergence report into proof-backed world compilation.
 *
 * <p>No re-plan, retry, candidate mutation, or alternate fresh plan is permitted.
 */
public final class SkyIslandAcceptedConvergenceCompiler {

    public SkyIslandAcceptedConvergenceCompilation compileOnce(
            SkyIslandSupportConvergenceReport convergence,
            SkyIslandMorphologyProviderRegistry registry) {
        Objects.requireNonNull(convergence, "convergence");
        Objects.requireNonNull(registry, "registry");
        if (!convergence.accepted()) {
            throw new IllegalArgumentException(
                    "AUTH-0057 requires ACCEPTED_ONE_PASS convergence");
        }

        SkyIslandArchipelagoPlan freshPlan = convergence.freshPlan().orElseThrow();
        SkyIslandWorldVerticalReservation verticalReservation =
                convergence.proposal().candidateVerticalReservation().orElseThrow();

        SkyIslandSupportReservationPreflightReport reproducedPreflight =
                new SkyIslandSupportReservationPreflight()
                        .evaluate(freshPlan, registry, verticalReservation);
        SkyIslandSupportReservationPreflightReport acceptedPreflight =
                convergence.freshPreflight().orElseThrow();
        if (!reproducedPreflight.equals(acceptedPreflight)) {
            throw new IllegalStateException(
                    "AUTH-0057 accepted fresh preflight does not reproduce under supplied registry");
        }
        if (!reproducedPreflight.admitted()) {
            throw new IllegalStateException(
                    "AUTH-0057 reproduced accepted preflight is no longer admitted");
        }

        SkyIslandWorldCatalogSupportBundle supportBundle;
        try {
            supportBundle =
                    new SkyIslandWorldCatalogCompiler()
                            .compileProofBacked(
                                    freshPlan,
                                    registry,
                                    verticalReservation);
        } catch (RuntimeException compilationFailure) {
            throw new IllegalStateException(
                    "AUTH-0057 proof-backed compilation failed after accepted preflight reproduced",
                    compilationFailure);
        }

        return new SkyIslandAcceptedConvergenceCompilation(
                convergence,
                reproducedPreflight,
                supportBundle);
    }
}
