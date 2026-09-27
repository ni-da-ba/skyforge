package io.github.nidaba.skyforge.world;

/**
 * Explicit backend-neutral disturbance evidence for one ecological assembly context.
 *
 * <p>Severity is a normalized semantic magnitude in {@code [0, 1]}. Elapsed ecological time is
 * non-negative and intentionally unit-agnostic; any succession profile interpreting this event must
 * use the same time unit for its characteristic time.
 *
 * <p>This record does not identify a disturbance mechanism such as fire, storm, grazing, or
 * civilization.
 */
public record SkyIslandCommunityDisturbanceEvent(
        double severity,
        double elapsedEcologicalTime) {

    public SkyIslandCommunityDisturbanceEvent {
        if (!Double.isFinite(severity) || severity < 0.0 || severity > 1.0) {
            throw new IllegalArgumentException("severity must be finite and in [0, 1]");
        }
        if (!Double.isFinite(elapsedEcologicalTime) || elapsedEcologicalTime < 0.0) {
            throw new IllegalArgumentException(
                    "elapsedEcologicalTime must be finite and non-negative");
        }
    }
}
