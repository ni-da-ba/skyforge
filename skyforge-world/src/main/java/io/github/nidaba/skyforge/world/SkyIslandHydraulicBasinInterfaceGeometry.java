package io.github.nidaba.skyforge.world;

import java.util.Objects;

/**
 * Geometry-only retained-open-water interface evidence.
 *
 * <p>The nearest continuous shoreline target and basin datum are diagnostic constraints. They do not
 * authorize a connector route, basin terrain mutation, or production E2 policy.
 */
public record SkyIslandHydraulicBasinInterfaceGeometry(
        SkyIslandHydraulicBasinTransitionSite transitionSite,
        SkyIslandContinuousWaterbodyBasin basin,
        SkyIslandLocalPosition nearestShorelinePoint,
        double shorelineGap,
        double preferredRiverToBasinDatumMismatchWorldUnits) {

    public SkyIslandHydraulicBasinInterfaceGeometry {
        transitionSite = Objects.requireNonNull(transitionSite, "transitionSite");
        basin = Objects.requireNonNull(basin, "basin");
        nearestShorelinePoint =
                Objects.requireNonNull(nearestShorelinePoint, "nearestShorelinePoint");
        if (transitionSite.terminalFate().kind()
                != SkyIslandChannelTerminalFateKind.RETAINED_OPEN_WATER) {
            throw new IllegalArgumentException(
                    "open-water interface geometry requires RETAINED_OPEN_WATER fate");
        }
        if (basin.sourceCandidate().sinkCellIndex()
                != transitionSite.terminalFate().watershedTerminalCellIndex()) {
            throw new IllegalArgumentException(
                    "basin sink must match the terminal fate's watershed terminal");
        }
        if (!Double.isFinite(shorelineGap) || shorelineGap < 0.0) {
            throw new IllegalArgumentException("shorelineGap must be finite and non-negative");
        }
        if (!Double.isFinite(preferredRiverToBasinDatumMismatchWorldUnits)
                || preferredRiverToBasinDatumMismatchWorldUnits < 0.0) {
            throw new IllegalArgumentException(
                    "preferred datum mismatch must be finite and non-negative");
        }
    }
}
