package io.github.nidaba.skyforge.world;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Discovers exact transition ownership on the accepted C2/F2B geometry.
 *
 * <p>F3 preserves the existing profile-to-station mapping used by D1/F1/F2C:
 * profile i owns station fractions [i/n,(i+1)/n). It does not size a finite transition envelope,
 * solve hydraulic head, mutate terrain, or grant retained-basin authority.
 */
public final class SkyIslandHydraulicTransitionTopologyPlanner {
    private static final double EPSILON = 1.0e-12;

    private SkyIslandHydraulicTransitionTopologyPlanner() {}

    public static SkyIslandHydraulicTransitionTopologyPlan plan(SkyIslandDescriptor descriptor) {
        return plan(descriptor, SkyIslandHydraulicGeometrySkeletonPlanner.plan(descriptor));
    }

    static SkyIslandHydraulicTransitionTopologyPlan plan(
            SkyIslandDescriptor descriptor,
            SkyIslandHydraulicGeometrySkeletonPlan skeletonPlan) {
        Objects.requireNonNull(descriptor, "descriptor");
        Objects.requireNonNull(skeletonPlan, "skeletonPlan");
        if (!descriptor.equals(skeletonPlan.descriptor())) {
            throw new IllegalArgumentException("skeleton descriptor must match transition descriptor");
        }

        SkyIslandSemanticField terrain = SkyIslandPreHydrologicTerrainField.create(descriptor);
        List<SkyIslandHydraulicConfluenceTransitionSite> confluences =
                confluenceSites(skeletonPlan);
        List<SkyIslandHydraulicCascadeTransitionSite> cascades =
                cascadeSites(descriptor, terrain, skeletonPlan);
        List<SkyIslandChannelTerminalFate> terminalFates =
                SkyIslandChannelTerminalFatePlanner.plan(
                        descriptor, skeletonPlan.geomorphicNetwork());
        List<SkyIslandHydraulicBasinTransitionSite> basins = new ArrayList<>();
        List<SkyIslandChannelTerminalFate> unresolved = new ArrayList<>();

        for (SkyIslandChannelTerminalFate fate : terminalFates) {
            switch (fate.kind()) {
                case EDGE_OUTLET -> {
                    // Explicitly ordinary terminal: no transition site.
                }
                case UNRESOLVED -> unresolved.add(fate);
                case RETAINED_OPEN_WATER, RETAINED_WETLAND -> {
                    SkyIslandHydraulicReachSkeleton reach =
                            requireTerminalReach(
                                    skeletonPlan.reaches(),
                                    fate.channelTerminalCellIndex());
                    basins.add(new SkyIslandHydraulicBasinTransitionSite(
                            fate,
                            boundaryStateAtEndpoint(
                                    reach,
                                    SkyIslandHydraulicTransitionBoundaryRole.INCOMING,
                                    true)));
                }
            }
        }

        basins.sort(Comparator.comparingInt(
                site -> site.terminalFate().channelTerminalCellIndex()));
        unresolved.sort(Comparator.comparingInt(
                SkyIslandChannelTerminalFate::channelTerminalCellIndex));

        return new SkyIslandHydraulicTransitionTopologyPlan(
                descriptor,
                skeletonPlan,
                confluences,
                cascades,
                basins,
                unresolved);
    }

    private static List<SkyIslandHydraulicConfluenceTransitionSite> confluenceSites(
            SkyIslandHydraulicGeometrySkeletonPlan skeletonPlan) {
        List<SkyIslandHydraulicConfluenceTransitionSite> result = new ArrayList<>();
        List<SkyIslandGeomorphicNetworkNode> nodes =
                skeletonPlan.geomorphicNetwork().nodes().stream()
                        .filter(node -> node.kind() == SkyIslandGeomorphicNetworkNodeKind.CONFLUENCE)
                        .sorted(Comparator.comparingInt(SkyIslandGeomorphicNetworkNode::cellIndex))
                        .toList();

        for (SkyIslandGeomorphicNetworkNode node : nodes) {
            List<SkyIslandHydraulicTransitionBoundaryState> boundaries = new ArrayList<>();
            for (SkyIslandHydraulicReachSkeleton reach : skeletonPlan.reaches()) {
                SkyIslandSemanticChannelReach semantic =
                        reach.geomorphicRoute().semanticReach();
                if (semantic.endCellIndex() == node.cellIndex()) {
                    boundaries.add(boundaryStateAtEndpoint(
                            reach,
                            SkyIslandHydraulicTransitionBoundaryRole.INCOMING,
                            true));
                }
                if (semantic.startCellIndex() == node.cellIndex()) {
                    boundaries.add(boundaryStateAtEndpoint(
                            reach,
                            SkyIslandHydraulicTransitionBoundaryRole.OUTGOING,
                            false));
                }
            }
            boundaries.sort(Comparator
                    .comparing(SkyIslandHydraulicTransitionBoundaryState::role)
                    .thenComparingInt(SkyIslandHydraulicTransitionBoundaryState::reachStartCellIndex)
                    .thenComparingInt(SkyIslandHydraulicTransitionBoundaryState::reachEndCellIndex));
            result.add(new SkyIslandHydraulicConfluenceTransitionSite(
                    node.cellIndex(), node.physicalPosition(), boundaries));
        }
        return List.copyOf(result);
    }

    private static List<SkyIslandHydraulicCascadeTransitionSite> cascadeSites(
            SkyIslandDescriptor descriptor,
            SkyIslandSemanticField terrain,
            SkyIslandHydraulicGeometrySkeletonPlan skeletonPlan) {
        List<SkyIslandHydraulicCascadeTransitionSite> result = new ArrayList<>();
        for (SkyIslandHydraulicReachSkeleton reach : skeletonPlan.reaches()) {
            List<SkyIslandChannelProfile> profiles =
                    reach.geomorphicRoute().semanticReach().profiles();
            int index = 0;
            while (index < profiles.size()) {
                if (profiles.get(index).kind() != SkyIslandChannelProfileKind.CASCADE) {
                    index++;
                    continue;
                }
                int first = index;
                while (index < profiles.size()
                        && profiles.get(index).kind() == SkyIslandChannelProfileKind.CASCADE) {
                    index++;
                }
                int lastExclusive = index;
                double startFraction = (double) first / profiles.size();
                double endFraction = (double) lastExclusive / profiles.size();
                SkyIslandChannelSegment firstSegment = profiles.get(first).segment();
                SkyIslandChannelSegment lastSegment =
                        profiles.get(lastExclusive - 1).segment();

                result.add(new SkyIslandHydraulicCascadeTransitionSite(
                        reach.geomorphicRoute().semanticReach().startCellIndex(),
                        reach.geomorphicRoute().semanticReach().endCellIndex(),
                        first,
                        lastExclusive,
                        firstSegment.sourceCellIndex(),
                        lastSegment.downstreamCellIndex(),
                        sampleBoundaryState(
                                descriptor,
                                terrain,
                                reach,
                                startFraction,
                                SkyIslandHydraulicTransitionBoundaryRole.INCOMING),
                        sampleBoundaryState(
                                descriptor,
                                terrain,
                                reach,
                                endFraction,
                                SkyIslandHydraulicTransitionBoundaryRole.OUTGOING)));
            }
        }
        return List.copyOf(result);
    }

    private static SkyIslandHydraulicReachSkeleton requireTerminalReach(
            List<SkyIslandHydraulicReachSkeleton> reaches,
            int terminalCellIndex) {
        List<SkyIslandHydraulicReachSkeleton> matches = reaches.stream()
                .filter(reach ->
                        reach.geomorphicRoute().semanticReach().endCellIndex()
                                == terminalCellIndex)
                .toList();
        if (matches.size() != 1) {
            throw new IllegalStateException(
                    "retained terminal must have exactly one incident channel reach: "
                            + terminalCellIndex);
        }
        return matches.getFirst();
    }

    static SkyIslandHydraulicTransitionBoundaryState boundaryStateAtEndpoint(
            SkyIslandHydraulicReachSkeleton reach,
            SkyIslandHydraulicTransitionBoundaryRole role,
            boolean downstream) {
        SkyIslandHydraulicGeometrySkeletonSample sample =
                downstream ? reach.samples().getLast() : reach.samples().getFirst();
        SkyIslandSemanticChannelReach semantic =
                reach.geomorphicRoute().semanticReach();
        return new SkyIslandHydraulicTransitionBoundaryState(
                semantic.startCellIndex(),
                semantic.endCellIndex(),
                role,
                downstream ? 1.0 : 0.0,
                sample.arcLength(),
                sample.position(),
                sample.relativeDischarge(),
                sample.bankfullHalfWidth(),
                sample.waterDepthPotential(),
                sample.terrainElevation());
    }

    static SkyIslandHydraulicTransitionBoundaryState sampleBoundaryState(
            SkyIslandDescriptor descriptor,
            SkyIslandSemanticField terrain,
            SkyIslandHydraulicReachSkeleton reach,
            double stationFraction,
            SkyIslandHydraulicTransitionBoundaryRole role) {
        if (stationFraction <= EPSILON) {
            return boundaryStateAtEndpoint(reach, role, false);
        }
        if (stationFraction >= 1.0 - EPSILON) {
            return boundaryStateAtEndpoint(reach, role, true);
        }

        double targetArc = stationFraction * reach.pathLength();
        List<SkyIslandHydraulicGeometrySkeletonSample> samples = reach.samples();
        for (int i = 0; i + 1 < samples.size(); i++) {
            SkyIslandHydraulicGeometrySkeletonSample a = samples.get(i);
            SkyIslandHydraulicGeometrySkeletonSample b = samples.get(i + 1);
            if (targetArc > b.arcLength() + EPSILON) {
                continue;
            }
            double ds = b.arcLength() - a.arcLength();
            if (!(ds > 0.0)) {
                throw new IllegalStateException("F2B arc length must increase strictly");
            }
            double t = Math.max(0.0, Math.min(1.0, (targetArc - a.arcLength()) / ds));
            SkyIslandLocalPosition position = new SkyIslandLocalPosition(
                    lerp(a.position().x(), b.position().x(), t),
                    lerp(a.position().z(), b.position().z(), t));
            double discharge = lerp(a.relativeDischarge(), b.relativeDischarge(), t);
            double width = SkyIslandHydraulicGeometryCalibration.bankfullHalfWidth(
                    descriptor.nominalRadius(), discharge);
            double depth =
                    SkyIslandHydraulicGeometryCalibration.waterDepthPotential(discharge);
            double terrainElevation = clamp01(terrain.sample(position));
            SkyIslandSemanticChannelReach semantic =
                    reach.geomorphicRoute().semanticReach();
            return new SkyIslandHydraulicTransitionBoundaryState(
                    semantic.startCellIndex(),
                    semantic.endCellIndex(),
                    role,
                    stationFraction,
                    targetArc,
                    position,
                    discharge,
                    width,
                    depth,
                    terrainElevation);
        }
        throw new IllegalStateException("transition station escaped F2B centerline");
    }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    private static double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }
}
