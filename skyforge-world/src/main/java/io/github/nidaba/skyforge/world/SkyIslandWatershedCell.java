package io.github.nidaba.skyforge.world;

/**
 * One backend-neutral planning cell in an island watershed graph.
 *
 * <p>Accumulation is expressed in relative authored runoff units rather than blocks or cubic metres.
 * Surface and spill values are normalized semantic elevation potentials rather than world Y levels.
 */
public record SkyIslandWatershedCell(
        int index,
        SkyIslandLocalPosition position,
        double surfacePotential,
        double spillSurfacePotential,
        double fillDepthPotential,
        double localRunoff,
        double flowAccumulation,
        int downstreamIndex,
        boolean retainedSink,
        boolean edgeOutlet) {

    private static final double CONSISTENCY_EPSILON = 1.0e-10;

    public SkyIslandWatershedCell {
        if (index < 0 || position == null || downstreamIndex < -1) {
            throw new IllegalArgumentException("invalid watershed cell identity");
        }
        requireFiniteNonNegative(surfacePotential, "surfacePotential");
        requireFiniteNonNegative(spillSurfacePotential, "spillSurfacePotential");
        requireFiniteNonNegative(fillDepthPotential, "fillDepthPotential");
        requireFiniteNonNegative(localRunoff, "localRunoff");
        requireFiniteNonNegative(flowAccumulation, "flowAccumulation");
        if (spillSurfacePotential + CONSISTENCY_EPSILON < surfacePotential) {
            throw new IllegalArgumentException("spill surface cannot lie below the authored surface");
        }
        double expectedFillDepth = Math.max(0.0, spillSurfacePotential - surfacePotential);
        if (Math.abs(expectedFillDepth - fillDepthPotential) > CONSISTENCY_EPSILON) {
            throw new IllegalArgumentException("fillDepthPotential must equal spillSurfacePotential - surfacePotential");
        }
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
