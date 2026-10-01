package io.github.nidaba.skyforge.world;

import java.util.Objects;
import java.util.Optional;

/**
 * Generic exponential ecological recovery profile.
 *
 * <p>The characteristic recovery time is supplied explicitly by the caller and uses the same
 * ecological-time unit as {@link SkyIslandCommunityDisturbanceEvent#elapsedEcologicalTime()}.
 *
 * <p>For severity {@code s}, elapsed time {@code t}, and characteristic time {@code tau}:
 *
 * <pre>
 * decay = exp(-t / tau)
 * residualDisturbance = s * decay
 * recoveryProgress = 1 - decay
 * </pre>
 *
 * <p>The profile does not imply that every community benefits from recovery or is harmed by
 * disturbance; it only produces reusable assembly-state signals.
 */
public record SkyIslandExponentialSuccessionProfile(double characteristicRecoveryTime)
        implements SkyIslandCommunitySuccessionProfile {

    public SkyIslandExponentialSuccessionProfile {
        if (!Double.isFinite(characteristicRecoveryTime) || characteristicRecoveryTime <= 0.0) {
            throw new IllegalArgumentException(
                    "characteristicRecoveryTime must be finite and positive");
        }
    }

    @Override
    public Optional<SkyIslandCommunitySuccessionState> state(
            SkyIslandCommunityDisturbanceEvidence evidence) {
        Objects.requireNonNull(evidence, "evidence");
        Optional<SkyIslandCommunityDisturbanceEvent> event = evidence.latestDisturbance();
        if (event.isEmpty()) {
            return Optional.empty();
        }

        SkyIslandCommunityDisturbanceEvent disturbance = event.orElseThrow();
        double decay = Math.exp(
                -disturbance.elapsedEcologicalTime() / characteristicRecoveryTime);
        double residual = disturbance.severity() * decay;
        double recovery = 1.0 - decay;
        return Optional.of(new SkyIslandCommunitySuccessionState(residual, recovery));
    }
}
