package io.github.nidaba.skyforge.neoforge1211;

/**
 * Pure vertical coupling from one trusted atmospheric updraft into Reliable Gliders' completed
 * post-tick vertical velocity.
 */
final class SkyforgeGliderLiftCoupling {
    static final double TICKS_PER_SECOND = 20.0;
    static final double RELIABLE_GLIDER_BASELINE_SINK_BLOCKS_PER_TICK = 0.05;
    static final double SMOOTHING = 0.20;

    private SkyforgeGliderLiftCoupling() {}

    static double targetVerticalVelocity(double updraftMetersPerSecond) {
        return updraftMetersPerSecond / TICKS_PER_SECOND
                - RELIABLE_GLIDER_BASELINE_SINK_BLOCKS_PER_TICK;
    }

    static double apply(double currentY, boolean trusted, double updraftMetersPerSecond) {
        if (!trusted
                || !Double.isFinite(currentY)
                || !Double.isFinite(updraftMetersPerSecond)
                || updraftMetersPerSecond <= 0.0) {
            return currentY;
        }

        double targetY = targetVerticalVelocity(updraftMetersPerSecond);
        if (targetY <= currentY) {
            return currentY;
        }

        return currentY + SMOOTHING * (targetY - currentY);
    }
}
