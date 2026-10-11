package io.github.nidaba.skyforge.world;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Couples independently proposed reach beds at their shared semantic network nodes.
 *
 * <p>Reach-local candidate fitting supplies the objective and local geometry. This final bounded
 * projection makes every incident endpoint share one bed elevation, so a hydraulic junction cannot
 * be asked to carry a water surface below one of its channel beds.
 */
final class SkyIslandHydraulicSharedNodeBedCoupler {
    private SkyIslandHydraulicSharedNodeBedCoupler() {}

    static List<SkyIslandHydraulicLandformCandidatePlanner.ReachCandidate> couple(
            SkyIslandDescriptor descriptor,
            SkyIslandGameScaleHydraulicCalibration calibration,
            SkyIslandHydraulicGeometrySkeletonPlan skeleton,
            List<SkyIslandHydraulicLandformCandidatePlanner.ReachCandidate> reaches) {
        Objects.requireNonNull(descriptor, "descriptor");
        Objects.requireNonNull(calibration, "calibration");
        Objects.requireNonNull(skeleton, "skeleton");
        reaches = List.copyOf(Objects.requireNonNull(reaches, "reaches"));
        if (reaches.isEmpty()) {
            throw new IllegalArgumentException("shared-node bed solve requires candidate reaches");
        }

        int totalSections = reaches.stream().mapToInt(reach -> reach.sections().size()).sum();
        double reliefMeters = descriptor.reliefBudget() * calibration.metersPerWorldUnit();
        double[] target = new double[totalSections];
        double[] weight = new double[totalSections];
        double[] lower = new double[totalSections];
        double[] upper = new double[totalSections];
        int[] offsets = new int[reaches.size()];
        List<SkyIslandHydraulicDifferenceConstraint> constraints = new ArrayList<>();
        Map<Integer, List<Integer>> nodeEndpoints = new HashMap<>();

        int offset = 0;
        for (int reachIndex = 0; reachIndex < reaches.size(); reachIndex++) {
            SkyIslandHydraulicLandformCandidatePlanner.ReachCandidate reach = reaches.get(reachIndex);
            offsets[reachIndex] = offset;
            List<SkyIslandGraduallyVariedFlowSolver.CrossSection> sections = reach.sections();
            List<SkyIslandHydraulicGeometrySkeletonSample> samples = reach.skeleton().samples();
            SkyIslandSemanticChannelReach semantic = reach.skeleton()
                    .geomorphicRoute().semanticReach();
            nodeEndpoints.computeIfAbsent(semantic.startCellIndex(), ignored -> new ArrayList<>())
                    .add(offset);
            nodeEndpoints.computeIfAbsent(semantic.endCellIndex(), ignored -> new ArrayList<>())
                    .add(offset + sections.size() - 1);

            for (int i = 0; i < sections.size(); i++) {
                SkyIslandGraduallyVariedFlowSolver.CrossSection section = sections.get(i);
                SkyIslandHydraulicGeometrySkeletonSample sample = samples.get(i);
                int variable = offset + i;
                double surface = sample.terrainElevation() * reliefMeters;
                double incision = Math.min(
                        surface, calibration.maximumCrossSectionDepthMeters(sample));
                double fullBankfullWidth = section.bottomWidthMeters()
                        + 2.0 * calibration.sideSlopeHorizontalToVertical()
                                * (surface - section.bedElevationMeters());
                double minimumBottomWidth = Math.max(1.0e-8, fullBankfullWidth * 1.0e-8);
                double maximumDepthFromWidth = (fullBankfullWidth - minimumBottomWidth)
                        / (2.0 * calibration.sideSlopeHorizontalToVertical());
                if (!(maximumDepthFromWidth > 0.0) || !Double.isFinite(maximumDepthFromWidth)) {
                    throw new IllegalStateException(
                            "shared-node bed solve found invalid trapezoid geometry");
                }
                target[variable] = section.bedElevationMeters();
                lower[variable] = Math.max(
                        0.0, surface - Math.min(incision, maximumDepthFromWidth));
                upper[variable] = surface;
                double left = i == 0
                        ? sections.get(1).chainageMeters() - section.chainageMeters()
                        : section.chainageMeters() - sections.get(i - 1).chainageMeters();
                double right = i + 1 == sections.size()
                        ? section.chainageMeters() - sections.get(i - 1).chainageMeters()
                        : sections.get(i + 1).chainageMeters() - section.chainageMeters();
                weight[variable] = 0.5 * (left + right);
            }
            offset += sections.size();
        }

        List<Integer> sourceNodes = skeleton.geomorphicNetwork().nodes().stream()
                .filter(node -> node.kind() == SkyIslandGeomorphicNetworkNodeKind.SOURCE)
                .map(SkyIslandGeomorphicNetworkNode::cellIndex)
                .toList();
        for (int reachIndex = 0; reachIndex < reaches.size(); reachIndex++) {
            SkyIslandHydraulicLandformCandidatePlanner.ReachCandidate reach = reaches.get(reachIndex);
            List<SkyIslandGraduallyVariedFlowSolver.CrossSection> sections = reach.sections();
            List<SkyIslandHydraulicGeometrySkeletonSample> samples = reach.skeleton().samples();
            List<SkyIslandChannelProfile> profiles = reach.skeleton()
                    .geomorphicRoute().semanticReach().profiles();
            SkyIslandSemanticChannelReach semantic = reach.skeleton()
                    .geomorphicRoute().semanticReach();
            int reachOffset = offsets[reachIndex];

            if (sourceNodes.contains(semantic.startCellIndex())) {
                int windowEnd = Math.min(
                        sections.size() - 1,
                        Math.max(2, (int) Math.ceil((sections.size() - 1) * 0.25)));
                SkyIslandGraduallyVariedFlowSolver.CrossSection source = sections.getFirst();
                double maximumDepth = calibration.maximumCrossSectionDepthMeters(samples.getFirst());
                double minimumSlope = SkyIslandManningHydraulics.uniformFlowEnergySlope(
                        source.dischargeCubicMetersPerSecond(),
                        calibration.manningRoughness(),
                        maximumDepth,
                        source.bottomWidthMeters(),
                        source.sideSlopeHorizontalToVertical());
                double windowLength = sections.get(windowEnd).chainageMeters()
                        - source.chainageMeters();
                double minimumDrop = minimumSlope * windowLength;
                double maximumDrop = calibration.maximumDownstreamBedSlope() * windowLength;
                if (!(windowLength > 0.0)
                        || !Double.isFinite(minimumDrop)
                        || minimumDrop > maximumDrop) {
                    throw new IllegalStateException(
                            "shared-node bed solve cannot preserve source normal-depth grade");
                }
                constraints.add(new SkyIslandHydraulicDifferenceConstraint(
                        "shared-source-grade-" + semantic.startCellIndex() + "-"
                                + semantic.endCellIndex(),
                        reachOffset,
                        reachOffset + windowEnd,
                        minimumDrop,
                        maximumDrop));
            }

            for (int i = 0; i + 1 < sections.size(); i++) {
                if (profileKind(profiles, samples.get(i).stationFraction())
                                != SkyIslandChannelProfileKind.CASCADE
                        && profileKind(profiles, samples.get(i + 1).stationFraction())
                                != SkyIslandChannelProfileKind.CASCADE) {
                    continue;
                }
                constraints.add(new SkyIslandHydraulicDifferenceConstraint(
                        "shared-cascade-grade-" + semantic.startCellIndex() + "-"
                                + semantic.endCellIndex() + "-" + i,
                        reachOffset + i,
                        reachOffset + i + 1,
                        0.0,
                        reliefMeters));
            }
        }

        for (Map.Entry<Integer, List<Integer>> entry : nodeEndpoints.entrySet()) {
            List<Integer> endpoints = entry.getValue();
            if (endpoints.size() < 2) {
                continue;
            }
            int anchor = endpoints.getFirst();
            for (int i = 1; i < endpoints.size(); i++) {
                constraints.add(new SkyIslandHydraulicDifferenceConstraint(
                        "shared-node-bed-" + entry.getKey() + "-" + i,
                        anchor,
                        endpoints.get(i),
                        0.0,
                        0.0));
            }
        }

        SkyIslandHydraulicQpResult result = SkyIslandHydraulicBoundedQpSolver.solve(
                new SkyIslandHydraulicBoundedQpProblem(
                        target, weight, lower, upper, constraints));
        if (result.status() != SkyIslandHydraulicQpStatus.SOLVED) {
            throw new IllegalStateException(
                    "network-coupled channel-bed candidate is infeasible"
                            + ";diagnostic=" + result.diagnostic().orElse("none"));
        }

        double[] bed = result.solution();
        List<SkyIslandHydraulicLandformCandidatePlanner.ReachCandidate> coupled =
                new ArrayList<>(reaches.size());
        for (int reachIndex = 0; reachIndex < reaches.size(); reachIndex++) {
            SkyIslandHydraulicLandformCandidatePlanner.ReachCandidate reach = reaches.get(reachIndex);
            int reachOffset = offsets[reachIndex];
            List<SkyIslandGraduallyVariedFlowSolver.CrossSection> sections = new ArrayList<>();
            for (int i = 0; i < reach.sections().size(); i++) {
                SkyIslandGraduallyVariedFlowSolver.CrossSection old = reach.sections().get(i);
                SkyIslandHydraulicGeometrySkeletonSample sample = reach.skeleton().samples().get(i);
                double surface = sample.terrainElevation() * reliefMeters;
                double fullBankfullWidth = old.bottomWidthMeters()
                        + 2.0 * calibration.sideSlopeHorizontalToVertical()
                                * (surface - old.bedElevationMeters());
                double newBed = bed[reachOffset + i];
                double newBottomWidth = fullBankfullWidth
                        - 2.0 * calibration.sideSlopeHorizontalToVertical() * (surface - newBed);
                if (!Double.isFinite(newBottomWidth) || newBottomWidth <= 0.0) {
                    throw new IllegalStateException(
                            "network-coupled bed collapsed the candidate trapezoid section");
                }
                sections.add(new SkyIslandGraduallyVariedFlowSolver.CrossSection(
                        old.chainageMeters(),
                        newBed,
                        old.dischargeCubicMetersPerSecond(),
                        newBottomWidth,
                        old.sideSlopeHorizontalToVertical()));
            }
            coupled.add(new SkyIslandHydraulicLandformCandidatePlanner.ReachCandidate(
                    reach.skeleton(),
                    sections,
                    reach.sectionWidthScales(),
                    result));
        }
        return List.copyOf(coupled);
    }

    private static SkyIslandChannelProfileKind profileKind(
            List<SkyIslandChannelProfile> profiles, double station) {
        int index = Math.min(
                profiles.size() - 1,
                (int) Math.floor(Math.max(0.0, Math.min(0.999999999, station)) * profiles.size()));
        return profiles.get(index).kind();
    }
}
