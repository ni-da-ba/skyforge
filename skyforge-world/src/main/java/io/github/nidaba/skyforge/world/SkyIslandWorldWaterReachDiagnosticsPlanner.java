package io.github.nidaba.skyforge.world;

import java.util.Objects;

/** Measures F4E world-space water head along one accepted F4A reach. */
public final class SkyIslandWorldWaterReachDiagnosticsPlanner {
    private static final double EPSILON = 1.0e-9;

    private SkyIslandWorldWaterReachDiagnosticsPlanner() {}

    public static SkyIslandWorldWaterReachDiagnostics measure(
            SkyIslandComponentFluvialWorldWaterProjection projection,
            SkyIslandHydraulicReachGeometry reach) {
        Objects.requireNonNull(projection, "projection");
        Objects.requireNonNull(reach, "reach");

        var physical =
                projection.association().realizedVolume().compiledVolume().descriptor();
        int wet = 0;
        int uphill = 0;
        double minHead = Double.POSITIVE_INFINITY;
        double maxHead = Double.NEGATIVE_INFINITY;
        double maxUpclimb = 0.0;
        double maxUpclimbGrade = 0.0;
        double maxAbsoluteGrade = 0.0;
        Double previousHead = null;
        SkyIslandLocalPosition previous = null;

        for (SkyIslandLocalPosition local : reach.centerline().points()) {
            SkyIslandProjectedFluvialWaterSample sample =
                    projection.sampleWorld(
                            physical.centerX() + local.x(),
                            physical.centerZ() + local.z());
            if (!sample.wet()) {
                throw new IllegalStateException(
                        "F4A realized centerline lost F4E water authority");
            }
            wet++;
            double head = sample.waterSurfaceWorldY().orElseThrow();
            minHead = Math.min(minHead, head);
            maxHead = Math.max(maxHead, head);

            if (previousHead != null) {
                double ds = Math.hypot(
                        local.x() - previous.x(),
                        local.z() - previous.z());
                if (!(ds > 0.0)) {
                    throw new IllegalStateException(
                            "F4E centerline must advance by positive distance");
                }
                double rise = head - previousHead;
                if (rise > EPSILON) {
                    uphill++;
                    maxUpclimb = Math.max(maxUpclimb, rise);
                    maxUpclimbGrade = Math.max(maxUpclimbGrade, rise / ds);
                }
                maxAbsoluteGrade =
                        Math.max(maxAbsoluteGrade, Math.abs(rise) / ds);
            }
            previousHead = head;
            previous = local;
        }

        return new SkyIslandWorldWaterReachDiagnostics(
                reach,
                reach.centerline().points().size(),
                wet,
                minHead,
                maxHead,
                uphill,
                maxUpclimb,
                maxUpclimbGrade,
                maxAbsoluteGrade);
    }
}
