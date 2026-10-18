package io.github.nidaba.skyforge.neoforge1211;

/**
 * Bounded player-facing interpretation of one trusted A4MC gameplay sample.
 *
 * <p>This is presentation mapping only. It preserves the sampled horizontal direction and signed
 * updraft direction, and owns no atmosphere state, simulation, persistence, or authority.
 */
final class SkyforgeAtmospherePresentationCue {
    private static final double MIN_HORIZONTAL_SPEED_MPS = 0.45;
    private static final double MIN_VERTICAL_SPEED_MPS = 0.25;
    private static final double HORIZONTAL_REFERENCE_MPS = 4.0;
    private static final double VERTICAL_REFERENCE_MPS = 1.5;

    private SkyforgeAtmospherePresentationCue() {}

    static Cue from(SkyforgeAtmosphereView.Sample sample) {
        if (sample == null || !sample.trustedForGameplay()) {
            return Cue.inactive();
        }

        double horizontalX = sample.effectiveX();
        double horizontalZ = sample.effectiveZ();
        double vertical = sample.updraftMetersPerSecond();
        if (!finite(horizontalX) || !finite(horizontalZ) || !finite(vertical)) {
            return Cue.inactive();
        }

        double horizontalSpeed = Math.hypot(horizontalX, horizontalZ);
        if (horizontalSpeed < MIN_HORIZONTAL_SPEED_MPS
                && Math.abs(vertical) < MIN_VERTICAL_SPEED_MPS) {
            return Cue.inactive();
        }

        double horizontalIntensity = horizontalSpeed / HORIZONTAL_REFERENCE_MPS;
        double verticalIntensity = Math.abs(vertical) / VERTICAL_REFERENCE_MPS;
        double intensity = clamp(Math.max(horizontalIntensity, verticalIntensity), 0.0, 1.0);
        int audioCooldownTicks = intensity >= 0.70 ? 20 : 35;
        return new Cue(
                true,
                horizontalX,
                vertical,
                horizontalZ,
                intensity,
                0.08f + (float) (0.22 * intensity),
                0.78f + (float) (0.34 * intensity),
                1 + (int) Math.floor(2.0 * intensity),
                0.04 + (0.10 * intensity),
                audioCooldownTicks);
    }

    private static boolean finite(double value) {
        return Double.isFinite(value);
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    record Cue(
            boolean active,
            double horizontalX,
            double vertical,
            double horizontalZ,
            double intensity,
            float audioVolume,
            float audioPitch,
            int particleCount,
            double particleSpeed,
            int audioCooldownTicks) {
        static Cue inactive() {
            return new Cue(false, 0.0, 0.0, 0.0, 0.0, 0.0f, 0.0f, 0, 0.0, 0);
        }
    }
}
