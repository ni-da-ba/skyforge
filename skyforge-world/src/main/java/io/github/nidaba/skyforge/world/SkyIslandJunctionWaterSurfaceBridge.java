package io.github.nidaba.skyforge.world;

import java.util.Objects;

/**
 * Interpolates the visualization-only water surface across the section-excluded junction mixing
 * region. The HEC energy-junction solve supplies the computational-section depths; this class adds
 * no hydraulic equation and rejects any bridge that crosses the shared candidate bed.
 */
final class SkyIslandJunctionWaterSurfaceBridge {
    private SkyIslandJunctionWaterSurfaceBridge() {}

    static double receiverStageAtSemanticNode(
            double[] stationsMetres,
            double[] bedElevationsMetres,
            double[] solvedSectionDepthsMetres,
            int firstSectionIndex) {
        validateArrays(stationsMetres, bedElevationsMetres);
        Objects.requireNonNull(solvedSectionDepthsMetres, "solvedSectionDepthsMetres");
        if (firstSectionIndex < 0
                || firstSectionIndex + 1 >= stationsMetres.length
                || solvedSectionDepthsMetres.length < 2) {
            throw new IllegalArgumentException(
                    "receiver continuation requires two solved sections after the semantic junction");
        }
        double ds = stationsMetres[firstSectionIndex + 1] - stationsMetres[firstSectionIndex];
        if (!(ds > 0.0)) {
            throw new IllegalArgumentException("receiver sections must have increasing stationing");
        }
        double firstStage = bedElevationsMetres[firstSectionIndex] + solvedSectionDepthsMetres[0];
        double nextStage = bedElevationsMetres[firstSectionIndex + 1] + solvedSectionDepthsMetres[1];
        double nodeStage = firstStage
                - (nextStage - firstStage) / ds * stationsMetres[firstSectionIndex];
        if (!Double.isFinite(nodeStage)) {
            throw new IllegalArgumentException("receiver continuation produced a non-finite node stage");
        }
        return nodeStage;
    }

    static double[] expandAcrossJunctionGaps(
            double[] stationsMetres,
            double[] bedElevationsMetres,
            double[] solvedSectionDepthsMetres,
            int firstSolvedIndex,
            int lastSolvedIndex,
            Double upstreamNodeStageMetres,
            Double downstreamNodeStageMetres) {
        validateArrays(stationsMetres, bedElevationsMetres);
        Objects.requireNonNull(solvedSectionDepthsMetres, "solvedSectionDepthsMetres");
        int count = stationsMetres.length;
        if (firstSolvedIndex < 0
                || lastSolvedIndex <= firstSolvedIndex
                || lastSolvedIndex >= count
                || solvedSectionDepthsMetres.length != lastSolvedIndex - firstSolvedIndex + 1) {
            throw new IllegalArgumentException("solved profile must match a nonempty reach slice");
        }
        double[] stage = new double[count];
        for (int i = firstSolvedIndex; i <= lastSolvedIndex; i++) {
            stage[i] = bedElevationsMetres[i] + solvedSectionDepthsMetres[i - firstSolvedIndex];
        }
        if (firstSolvedIndex > 0) {
            requireFinite(upstreamNodeStageMetres, "upstream junction node stage");
            double span = stationsMetres[firstSolvedIndex] - stationsMetres[0];
            if (!(span > 0.0)) {
                throw new IllegalArgumentException("upstream junction gap must have positive length");
            }
            for (int i = 0; i < firstSolvedIndex; i++) {
                double fraction = (stationsMetres[i] - stationsMetres[0]) / span;
                stage[i] = upstreamNodeStageMetres
                        + fraction * (stage[firstSolvedIndex] - upstreamNodeStageMetres);
            }
        }
        if (lastSolvedIndex < count - 1) {
            requireFinite(downstreamNodeStageMetres, "downstream junction node stage");
            double span = stationsMetres[count - 1] - stationsMetres[lastSolvedIndex];
            if (!(span > 0.0)) {
                throw new IllegalArgumentException("downstream junction gap must have positive length");
            }
            for (int i = lastSolvedIndex + 1; i < count; i++) {
                double fraction = (stationsMetres[i] - stationsMetres[lastSolvedIndex]) / span;
                stage[i] = stage[lastSolvedIndex]
                        + fraction * (downstreamNodeStageMetres - stage[lastSolvedIndex]);
            }
        }
        for (int i = 0; i < count; i++) {
            if (!Double.isFinite(stage[i]) || !(stage[i] > bedElevationsMetres[i])) {
                throw new IllegalArgumentException(
                        "junction water-surface bridge must remain above the shared candidate bed");
            }
        }
        return stage;
    }

    private static void validateArrays(double[] stations, double[] beds) {
        Objects.requireNonNull(stations, "stationsMetres");
        Objects.requireNonNull(beds, "bedElevationsMetres");
        if (stations.length < 2 || beds.length != stations.length) {
            throw new IllegalArgumentException("station and bed arrays must share at least two samples");
        }
        for (int i = 0; i < stations.length; i++) {
            if (!Double.isFinite(stations[i]) || !Double.isFinite(beds[i])
                    || (i > 0 && !(stations[i] > stations[i - 1]))) {
                throw new IllegalArgumentException("junction bridge stationing and bed must be finite and ordered");
            }
        }
    }

    private static void requireFinite(Double value, String label) {
        if (value == null || !Double.isFinite(value)) {
            throw new IllegalArgumentException(label + " is required for a junction gap");
        }
    }
}
