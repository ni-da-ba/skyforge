package io.github.nidaba.skyforge.world;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Selects a physically feasible shared bed elevation for every semantic network junction.
 *
 * <p>Each reach is still fitted by its bounded local QP. Shared endpoint values are fixed during
 * those local solves, avoiding an unnecessarily large dense network QP while making junction
 * geometry continuous.
 */
final class SkyIslandHydraulicSharedNodeBedCoupler {
    private SkyIslandHydraulicSharedNodeBedCoupler() {}

    static Map<Integer, Double> sharedNodeBedTargets(
            SkyIslandDescriptor descriptor,
            SkyIslandGameScaleHydraulicCalibration calibration,
            SkyIslandHydraulicGeometrySkeletonPlan skeleton) {
        Objects.requireNonNull(descriptor, "descriptor");
        Objects.requireNonNull(calibration, "calibration");
        Objects.requireNonNull(skeleton, "skeleton");
        double reliefMeters = descriptor.reliefBudget() * calibration.metersPerWorldUnit();
        Map<Integer, List<Endpoint>> endpointsByNode = new HashMap<>();
        for (SkyIslandHydraulicReachSkeleton reach : skeleton.reaches()) {
            List<SkyIslandHydraulicGeometrySkeletonSample> samples = reach.samples();
            List<SkyIslandGraduallyVariedFlowSolver.CrossSection> rawSections =
                    calibration.crossSections(descriptor, samples, samples);
            SkyIslandSemanticChannelReach semantic =
                    reach.geomorphicRoute().semanticReach();
            addEndpoint(
                    endpointsByNode,
                    semantic.startCellIndex(),
                    samples.getFirst(),
                    rawSections.getFirst(),
                    rawSections.get(1).chainageMeters() - rawSections.getFirst().chainageMeters(),
                    reliefMeters,
                    calibration);
            int last = samples.size() - 1;
            addEndpoint(
                    endpointsByNode,
                    semantic.endCellIndex(),
                    samples.getLast(),
                    rawSections.getLast(),
                    rawSections.getLast().chainageMeters()
                            - rawSections.get(last - 1).chainageMeters(),
                    reliefMeters,
                    calibration);
        }

        Map<Integer, Double> targets = new HashMap<>();
        for (Map.Entry<Integer, List<Endpoint>> entry : endpointsByNode.entrySet()) {
            List<Endpoint> endpoints = entry.getValue();
            if (endpoints.size() < 2) {
                continue;
            }
            double lower = endpoints.stream().mapToDouble(Endpoint::lower).max().orElseThrow();
            double upper = endpoints.stream().mapToDouble(Endpoint::upper).min().orElseThrow();
            if (lower > upper) {
                throw new IllegalStateException(
                        "incident channel sections have no shared feasible junction-bed elevation"
                                + ";node=" + entry.getKey()
                                + ";lowerMeters=" + lower
                                + ";upperMeters=" + upper);
            }
            double weightedTarget = 0.0;
            double totalWeight = 0.0;
            for (Endpoint endpoint : endpoints) {
                weightedTarget += endpoint.target() * endpoint.weight();
                totalWeight += endpoint.weight();
            }
            targets.put(entry.getKey(), Math.max(lower, Math.min(upper, weightedTarget / totalWeight)));
        }
        return Map.copyOf(targets);
    }

    private static void addEndpoint(
            Map<Integer, List<Endpoint>> endpointsByNode,
            int nodeCellIndex,
            SkyIslandHydraulicGeometrySkeletonSample sample,
            SkyIslandGraduallyVariedFlowSolver.CrossSection section,
            double weight,
            double reliefMeters,
            SkyIslandGameScaleHydraulicCalibration calibration) {
        double surface = sample.terrainElevation() * reliefMeters;
        double maximumIncision = Math.min(
                surface, calibration.maximumCrossSectionDepthMeters(sample));
        double fullBankfullWidth = section.bottomWidthMeters()
                + 2.0 * calibration.sideSlopeHorizontalToVertical()
                        * (surface - section.bedElevationMeters());
        double minimumBottomWidth = Math.max(1.0e-8, fullBankfullWidth * 1.0e-8);
        double maximumDepthFromWidth = (fullBankfullWidth - minimumBottomWidth)
                / (2.0 * calibration.sideSlopeHorizontalToVertical());
        if (!(weight > 0.0) || !(maximumDepthFromWidth > 0.0)
                || !Double.isFinite(maximumDepthFromWidth)) {
            throw new IllegalStateException("shared junction endpoint geometry is invalid");
        }
        double lower = Math.max(
                0.0, surface - Math.min(maximumIncision, maximumDepthFromWidth));
        double upper = surface;
        endpointsByNode.computeIfAbsent(nodeCellIndex, ignored -> new ArrayList<>())
                .add(new Endpoint(
                        section.bedElevationMeters(),
                        weight,
                        lower,
                        upper));
    }

    private record Endpoint(double target, double weight, double lower, double upper) {}
}
