package io.github.nidaba.skyforge.world;

/**
 * Explicit event-based Rational Method conversion for accumulated effective runoff.
 *
 * <p>{@code flowAccumulation} is the sum of dimensionless per-cell runoff potentials. Interpreting
 * those potentials as runoff coefficients is a caller-selected calibration, not an empirical fact.
 * Rainfall intensity must correspond to the event duration/time-of-concentration assumption and the
 * catchment must be within the Rational Method's applicable small-basin domain.
 */
public record SkyIslandRationalRunoffCalibration(
        double designRainfallIntensityMillimetersPerHour,
        double metersPerWorldUnit) {
    private static final double MILLIMETERS_PER_HOUR_PER_METERS_PER_SECOND = 3_600_000.0;

    public SkyIslandRationalRunoffCalibration {
        if (!Double.isFinite(designRainfallIntensityMillimetersPerHour)
                || designRainfallIntensityMillimetersPerHour <= 0.0
                || !Double.isFinite(metersPerWorldUnit)
                || metersPerWorldUnit <= 0.0) {
            throw new IllegalArgumentException("rainfall intensity and world scale must be finite and positive");
        }
    }

    /**
     * Estimates peak discharge as {@code i * sum(C_j * cellArea)} in SI units.
     *
     * @param flowAccumulation sum of effective runoff coefficients over contributing cells
     * @param cellSpacingWorldUnits square-cell spacing in authored world units
     * @return discharge in cubic metres per second
     */
    public double peakDischargeCubicMetersPerSecond(
            double flowAccumulation, double cellSpacingWorldUnits) {
        if (!Double.isFinite(flowAccumulation) || flowAccumulation < 0.0
                || !Double.isFinite(cellSpacingWorldUnits) || cellSpacingWorldUnits <= 0.0) {
            throw new IllegalArgumentException("runoff accumulation must be non-negative and spacing positive");
        }
        double spacingMeters = cellSpacingWorldUnits * metersPerWorldUnit;
        double effectiveAreaSquareMeters = flowAccumulation * spacingMeters * spacingMeters;
        return designRainfallIntensityMillimetersPerHour
                * effectiveAreaSquareMeters
                / MILLIMETERS_PER_HOUR_PER_METERS_PER_SECOND;
    }
}
