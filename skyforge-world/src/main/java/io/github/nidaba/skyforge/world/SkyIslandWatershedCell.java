package io.github.nidaba.skyforge.world;

/**
 * One backend-neutral planning cell in an island watershed graph.
 *
 * <p>Accumulation is expressed in relative authored runoff units rather than blocks or cubic metres.
 */
public record SkyIslandWatershedCell(
        int index,
        SkyIslandLocalPosition position,
        double surfacePotential,
        double localRunoff,
        double flowAccumulation,
        int downstreamIndex,
        boolean retainedSink,
        boolean edgeOutlet) {

    public SkyIslandWatershedCell {
        if (index < 0 || position == null || downstreamIndex < -1) {
            throw new IllegalArgumentException("invalid watershed cell identity");
        }
        requireFiniteNonNegative(surfacePotential, "surfacePotential");
        requireFiniteNonNegative(localRunoff, "localRunoff");
        requireFiniteNonNegative(flowAccumulation, "flowAccumulation");
        if (downstreamIndex == index) {
            throw new IllegalArgumentException("watershed cell cannot drain to itself");
        }
    }

    public boolean terminal() {
        return downstreamIndex < 0;
    }

    private static void requireFiniteNonNegative(double value, String name) {
        if (!Double.isFinite(value) || value < 0.0) {
            throw new IllegalArgumentException(name + " must be finite and non-negative");
        }
    }
}
