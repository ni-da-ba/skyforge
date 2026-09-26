package io.github.nidaba.skyforge.world;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Assembles F3D ordinary spans, F3C drops, F3B confluences, and terminal fate into complete
 * source-to-terminal admission evidence.
 *
 * <p>F3E grants no terrain authority. A QUALIFIED terminal component only means that every known
 * continuous hydraulic subproblem in that semantic drainage component has independently cleared its
 * current mathematical gate and that terminal fate itself is production-admissible.
 */
public final class SkyIslandHydraulicNetworkAssemblyPlanner {
    private SkyIslandHydraulicNetworkAssemblyPlanner() {}

    public static SkyIslandHydraulicNetworkAssemblyPlan plan(
            SkyIslandDescriptor descriptor) {
        Objects.requireNonNull(descriptor, "descriptor");
        return plan(descriptor, SkyIslandOrdinarySpanPlanner.plan(descriptor));
    }

    static SkyIslandHydraulicNetworkAssemblyPlan plan(
            SkyIslandDescriptor descriptor,
            SkyIslandOrdinarySpanPlan ordinarySpanPlan) {
        Objects.requireNonNull(descriptor, "descriptor");
        Objects.requireNonNull(ordinarySpanPlan, "ordinarySpanPlan");
        if (!descriptor.equals(ordinarySpanPlan.descriptor())) {
            throw new IllegalArgumentException(
                    "ordinary-span descriptor must match network assembly descriptor");
        }

        SkyIslandHydraulicGeometrySkeletonPlan skeleton =
                ordinarySpanPlan.cascadePlan()
                        .transitionGeometry()
                        .topology()
                        .skeletonPlan();
        SkyIslandGeomorphicChannelNetworkPlan network =
                skeleton.geomorphicNetwork();

        Map<Long, List<SkyIslandOrdinarySpanOutcome>> spansByReach =
                groupSpans(ordinarySpanPlan.outcomes());
        Map<Long, List<SkyIslandCascadeHeadCompatibilityOutcome>> cascadesByReach =
                groupCascades(ordinarySpanPlan.cascadePlan().outcomes());
        Map<Integer, SkyIslandConfluenceHeadCompatibilityOutcome> confluences =
                confluencesByNode(ordinarySpanPlan.confluencePlan().outcomes());

        List<SkyIslandHydraulicReachAssembly> reachAssemblies =
                new ArrayList<>(skeleton.reaches().size());
        Map<Long, SkyIslandHydraulicReachAssembly> reachByIdentity = new HashMap<>();
        for (SkyIslandHydraulicReachSkeleton reach : skeleton.reaches()) {
            SkyIslandSemanticChannelReach semantic =
                    reach.geomorphicRoute().semanticReach();
            long identity = identity(semantic);
            SkyIslandHydraulicReachAssembly assembly =
                    assembleReach(
                            semantic,
                            network,
                            spansByReach.getOrDefault(identity, List.of()),
                            cascadesByReach.getOrDefault(identity, List.of()),
                            confluences);
            reachAssemblies.add(assembly);
            if (reachByIdentity.put(identity, assembly) != null) {
                throw new IllegalStateException(
                        "duplicate semantic reach identity in F3E assembly");
            }
        }
        reachAssemblies.sort(Comparator
                .comparingInt((SkyIslandHydraulicReachAssembly value) ->
                        value.semanticReach().startCellIndex())
                .thenComparingInt(value -> value.semanticReach().endCellIndex()));

        Map<Integer, List<SkyIslandHydraulicReachAssembly>> incomingByEnd =
                new HashMap<>();
        for (SkyIslandHydraulicReachAssembly assembly : reachAssemblies) {
            incomingByEnd
                    .computeIfAbsent(
                            assembly.semanticReach().endCellIndex(),
                            ignored -> new ArrayList<>())
                    .add(assembly);
        }
        incomingByEnd.values().forEach(list -> list.sort(Comparator
                .comparingInt((SkyIslandHydraulicReachAssembly value) ->
                        value.semanticReach().startCellIndex())
                .thenComparingInt(value -> value.semanticReach().endCellIndex())));

        List<SkyIslandChannelTerminalFate> terminalFates =
                new ArrayList<>(SkyIslandChannelTerminalFatePlanner.plan(descriptor, network));
        terminalFates.sort(
                Comparator.comparingInt(SkyIslandChannelTerminalFate::channelTerminalCellIndex));

        List<SkyIslandHydraulicTerminalComponent> terminalComponents =
                new ArrayList<>(terminalFates.size());
        for (SkyIslandChannelTerminalFate fate : terminalFates) {
            LinkedHashMap<Long, SkyIslandHydraulicReachAssembly> componentReaches =
                    new LinkedHashMap<>();
            collectUpstream(
                    fate.channelTerminalCellIndex(),
                    incomingByEnd,
                    new LinkedHashSet<>(),
                    componentReaches);
            if (componentReaches.isEmpty()) {
                throw new IllegalStateException(
                        "semantic terminal has no upstream reach "
                                + fate.channelTerminalCellIndex());
            }

            List<SkyIslandHydraulicReachAssembly> ordered =
                    new ArrayList<>(componentReaches.values());
            ordered.sort(Comparator
                    .comparingInt((SkyIslandHydraulicReachAssembly value) ->
                            value.semanticReach().startCellIndex())
                    .thenComparingInt(value -> value.semanticReach().endCellIndex()));

            List<String> blockers = new ArrayList<>();
            SkyIslandHydraulicAssemblyStatus status = terminalStatus(fate);
            if (status != SkyIslandHydraulicAssemblyStatus.QUALIFIED) {
                blockers.add(
                        "terminal "
                                + fate.channelTerminalCellIndex()
                                + " fate "
                                + fate.kind().name()
                                + " lacks production terminal authority");
            }

            for (SkyIslandHydraulicReachAssembly assembly : ordered) {
                status = combine(status, assembly.status());
                for (String blocker : assembly.blockers()) {
                    blockers.add(
                            assembly.semanticReach().startCellIndex()
                                    + "->"
                                    + assembly.semanticReach().endCellIndex()
                                    + ": "
                                    + blocker);
                }
            }

            terminalComponents.add(new SkyIslandHydraulicTerminalComponent(
                    fate,
                    ordered,
                    status,
                    List.copyOf(blockers)));
        }

        return new SkyIslandHydraulicNetworkAssemblyPlan(
                descriptor,
                ordinarySpanPlan,
                reachAssemblies,
                terminalComponents);
    }

    private static SkyIslandHydraulicReachAssembly assembleReach(
            SkyIslandSemanticChannelReach semantic,
            SkyIslandGeomorphicChannelNetworkPlan network,
            List<SkyIslandOrdinarySpanOutcome> spans,
            List<SkyIslandCascadeHeadCompatibilityOutcome> cascades,
            Map<Integer, SkyIslandConfluenceHeadCompatibilityOutcome> confluences) {
        List<String> blockers = new ArrayList<>();
        SkyIslandHydraulicAssemblyStatus status =
                SkyIslandHydraulicAssemblyStatus.QUALIFIED;

        if (spans.isEmpty()
                && cascades.isEmpty()
                && semantic.profiles().stream()
                        .anyMatch(profile ->
                                profile.kind() != SkyIslandChannelProfileKind.CASCADE)) {
            status = SkyIslandHydraulicAssemblyStatus.TRANSITION_DEFERRED;
            blockers.add(
                    "finite transition ownership leaves no independently qualified ordinary span");
        }

        for (SkyIslandOrdinarySpanOutcome span : spans) {
            SkyIslandHydraulicAssemblyStatus spanStatus =
                    switch (span.status()) {
                        case SOLVED_QUALIFIED ->
                                SkyIslandHydraulicAssemblyStatus.QUALIFIED;
                        case SOLVED_REJECTED, INFEASIBLE ->
                                SkyIslandHydraulicAssemblyStatus.PHYSICAL_REJECTION;
                        case NUMERICAL_FAILURE ->
                                SkyIslandHydraulicAssemblyStatus.NUMERICAL_FAILURE;
                        case BOUNDARY_DEFERRED ->
                                SkyIslandHydraulicAssemblyStatus.TRANSITION_DEFERRED;
                    };
            status = combine(status, spanStatus);
            if (spanStatus != SkyIslandHydraulicAssemblyStatus.QUALIFIED) {
                String detail =
                        span.diagnostic()
                                .orElseGet(() ->
                                        span.violations().isEmpty()
                                                ? span.status().name()
                                                : span.violations().toString());
                blockers.add(
                        String.format(
                                java.util.Locale.ROOT,
                                "ordinary span %.6f..%.6f %s: %s",
                                span.span().parentStartStationFraction(),
                                span.span().parentEndStationFraction(),
                                span.status().name(),
                                detail));
            }
        }

        for (SkyIslandCascadeHeadCompatibilityOutcome cascade : cascades) {
            SkyIslandHydraulicAssemblyStatus cascadeStatus =
                    switch (cascade.status()) {
                        case SOLVED -> SkyIslandHydraulicAssemblyStatus.QUALIFIED;
                        case INFEASIBLE ->
                                SkyIslandHydraulicAssemblyStatus.PHYSICAL_REJECTION;
                        case NUMERICAL_FAILURE ->
                                SkyIslandHydraulicAssemblyStatus.NUMERICAL_FAILURE;
                        case BOUNDARY_COUPLED ->
                                SkyIslandHydraulicAssemblyStatus.TRANSITION_DEFERRED;
                    };
            status = combine(status, cascadeStatus);
            if (cascadeStatus != SkyIslandHydraulicAssemblyStatus.QUALIFIED) {
                blockers.add(
                        "CASCADE profiles "
                                + cascade.geometry().transitionSite().firstProfileIndex()
                                + ".."
                                + cascade.geometry().transitionSite().lastProfileIndexExclusive()
                                + " "
                                + cascade.status().name()
                                + cascade.diagnostic()
                                        .map(value -> ": " + value)
                                        .orElse(""));
            }
        }

        SkyIslandGeomorphicNetworkNode start =
                network.requireNode(semantic.startCellIndex());
        SkyIslandGeomorphicNetworkNode end =
                network.requireNode(semantic.endCellIndex());
        if (start.kind() == SkyIslandGeomorphicNetworkNodeKind.CONFLUENCE) {
            status = combineConfluence(
                    status, blockers, start.cellIndex(), confluences);
        }
        if (end.kind() == SkyIslandGeomorphicNetworkNodeKind.CONFLUENCE) {
            status = combineConfluence(
                    status, blockers, end.cellIndex(), confluences);
        }

        return new SkyIslandHydraulicReachAssembly(
                semantic,
                spans,
                cascades,
                status,
                List.copyOf(blockers));
    }

    private static SkyIslandHydraulicAssemblyStatus combineConfluence(
            SkyIslandHydraulicAssemblyStatus current,
            List<String> blockers,
            int nodeCellIndex,
            Map<Integer, SkyIslandConfluenceHeadCompatibilityOutcome> confluences) {
        SkyIslandConfluenceHeadCompatibilityOutcome outcome =
                confluences.get(nodeCellIndex);
        if (outcome == null) {
            throw new IllegalStateException(
                    "missing F3B outcome for confluence " + nodeCellIndex);
        }
        SkyIslandHydraulicAssemblyStatus confluenceStatus =
                switch (outcome.status()) {
                    case SOLVED -> SkyIslandHydraulicAssemblyStatus.QUALIFIED;
                    case INFEASIBLE ->
                            SkyIslandHydraulicAssemblyStatus.PHYSICAL_REJECTION;
                    case NUMERICAL_FAILURE ->
                            SkyIslandHydraulicAssemblyStatus.NUMERICAL_FAILURE;
                    case CASCADE_COUPLED ->
                            SkyIslandHydraulicAssemblyStatus.TRANSITION_DEFERRED;
                };
        if (confluenceStatus != SkyIslandHydraulicAssemblyStatus.QUALIFIED) {
            blockers.add(
                    "confluence "
                            + nodeCellIndex
                            + " "
                            + outcome.status().name()
                            + outcome.diagnostic()
                                    .map(value -> ": " + value)
                                    .orElse(""));
        }
        return combine(current, confluenceStatus);
    }

    private static void collectUpstream(
            int nodeCellIndex,
            Map<Integer, List<SkyIslandHydraulicReachAssembly>> incomingByEnd,
            Set<Integer> visitingNodes,
            LinkedHashMap<Long, SkyIslandHydraulicReachAssembly> result) {
        if (!visitingNodes.add(nodeCellIndex)) {
            throw new IllegalStateException(
                    "cycle detected in semantic hydrology at node " + nodeCellIndex);
        }
        for (SkyIslandHydraulicReachAssembly incoming :
                incomingByEnd.getOrDefault(nodeCellIndex, List.of())) {
            result.put(incoming.identity(), incoming);
            collectUpstream(
                    incoming.semanticReach().startCellIndex(),
                    incomingByEnd,
                    visitingNodes,
                    result);
        }
        visitingNodes.remove(nodeCellIndex);
    }

    private static Map<Long, List<SkyIslandOrdinarySpanOutcome>> groupSpans(
            List<SkyIslandOrdinarySpanOutcome> outcomes) {
        Map<Long, List<SkyIslandOrdinarySpanOutcome>> mutable = new HashMap<>();
        for (SkyIslandOrdinarySpanOutcome outcome : outcomes) {
            long identity =
                    identity(
                            outcome.span().parentReachStartCellIndex(),
                            outcome.span().parentReachEndCellIndex());
            mutable.computeIfAbsent(identity, ignored -> new ArrayList<>()).add(outcome);
        }
        Map<Long, List<SkyIslandOrdinarySpanOutcome>> result = new HashMap<>();
        for (Map.Entry<Long, List<SkyIslandOrdinarySpanOutcome>> entry : mutable.entrySet()) {
            entry.getValue().sort(Comparator.comparingDouble(
                    value -> value.span().parentStartArcLength()));
            result.put(entry.getKey(), List.copyOf(entry.getValue()));
        }
        return Map.copyOf(result);
    }

    private static Map<Long, List<SkyIslandCascadeHeadCompatibilityOutcome>> groupCascades(
            List<SkyIslandCascadeHeadCompatibilityOutcome> outcomes) {
        Map<Long, List<SkyIslandCascadeHeadCompatibilityOutcome>> mutable = new HashMap<>();
        for (SkyIslandCascadeHeadCompatibilityOutcome outcome : outcomes) {
            SkyIslandHydraulicCascadeTransitionSite site =
                    outcome.geometry().transitionSite();
            long identity =
                    identity(site.reachStartCellIndex(), site.reachEndCellIndex());
            mutable.computeIfAbsent(identity, ignored -> new ArrayList<>()).add(outcome);
        }
        Map<Long, List<SkyIslandCascadeHeadCompatibilityOutcome>> result = new HashMap<>();
        for (Map.Entry<Long, List<SkyIslandCascadeHeadCompatibilityOutcome>> entry :
                mutable.entrySet()) {
            entry.getValue().sort(Comparator.comparingDouble(
                    value ->
                            value.geometry()
                                    .transitionSite()
                                    .upstreamBoundary()
                                    .arcLength()));
            result.put(entry.getKey(), List.copyOf(entry.getValue()));
        }
        return Map.copyOf(result);
    }

    private static Map<Integer, SkyIslandConfluenceHeadCompatibilityOutcome> confluencesByNode(
            List<SkyIslandConfluenceHeadCompatibilityOutcome> outcomes) {
        Map<Integer, SkyIslandConfluenceHeadCompatibilityOutcome> result = new HashMap<>();
        for (SkyIslandConfluenceHeadCompatibilityOutcome outcome : outcomes) {
            int node = outcome.geometry().transitionSite().nodeCellIndex();
            if (result.put(node, outcome) != null) {
                throw new IllegalStateException(
                        "duplicate F3B outcome for confluence " + node);
            }
        }
        return Map.copyOf(result);
    }

    private static SkyIslandHydraulicAssemblyStatus terminalStatus(
            SkyIslandChannelTerminalFate fate) {
        return switch (fate.kind()) {
            case EDGE_OUTLET -> SkyIslandHydraulicAssemblyStatus.QUALIFIED;
            case RETAINED_OPEN_WATER, RETAINED_WETLAND, UNRESOLVED ->
                    SkyIslandHydraulicAssemblyStatus.TERMINAL_DEFERRED;
        };
    }

    private static SkyIslandHydraulicAssemblyStatus combine(
            SkyIslandHydraulicAssemblyStatus a,
            SkyIslandHydraulicAssemblyStatus b) {
        return severity(a) >= severity(b) ? a : b;
    }

    private static int severity(SkyIslandHydraulicAssemblyStatus status) {
        return switch (status) {
            case QUALIFIED -> 0;
            case TERMINAL_DEFERRED -> 1;
            case TRANSITION_DEFERRED -> 2;
            case PHYSICAL_REJECTION -> 3;
            case NUMERICAL_FAILURE -> 4;
        };
    }

    private static long identity(SkyIslandSemanticChannelReach reach) {
        return identity(reach.startCellIndex(), reach.endCellIndex());
    }

    private static long identity(int start, int end) {
        return ((long) start << 32) ^ Integer.toUnsignedLong(end);
    }
}
