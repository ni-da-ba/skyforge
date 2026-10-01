package io.github.nidaba.skyforge.world;

/**
 * Deterministic normalized semantic field evaluated in island-local coordinates.
 *
 * <p>AUTH-0002 fields return finite values in {@code [0, 1]}. Implementations are immutable and
 * backend-neutral.
 */
@FunctionalInterface
public interface SkyIslandSemanticField {
    /** Evaluates this field at one island-local position. */
    double sample(SkyIslandLocalPosition position);
}
