package io.github.nidaba.skyforge.neoforge1211;

import io.github.nidaba.skyforge.model.aircraft.AircraftBlockspaceIR;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/** Plans the static v0.10 Physics Assembler fixture and logical main-body adhesion tree. */
public final class SkyforgeAircraftAssemblyFixturePlanner {
    public SkyforgeAircraftAssemblyFixtureIR plan(
            SkyforgeAircraftProbeManifestIR manifest,
            String fixtureAssetId,
            SkyforgeAircraftAssemblyFixtureProfile profile) {
        Objects.requireNonNull(manifest, "manifest");
        Objects.requireNonNull(profile, "profile");
        if (!manifest.validation().passed() || !manifest.readiness().probePlacementManifestReady()) {
            throw new IllegalArgumentException("refusing fixture planning from non-ready probe manifest");
        }

        Set<String> mainKinds = Set.copyOf(profile.mainBodyKinds());
        Set<String> childKinds = Set.copyOf(profile.nestedChildKinds());
        List<SkyforgeAircraftProbeManifestIR.Placement> main = new ArrayList<>();
        List<SkyforgeAircraftProbeManifestIR.Placement> child = new ArrayList<>();
        TreeSet<String> unclassifiedKinds = new TreeSet<>();
        for (SkyforgeAircraftProbeManifestIR.Placement placement : manifest.placements()) {
            if (mainKinds.contains(placement.kind())) {
                main.add(placement);
            } else if (childKinds.contains(placement.kind())) {
                child.add(placement);
            } else {
                unclassifiedKinds.add(placement.kind());
            }
        }
        if (!unclassifiedKinds.isEmpty()) {
            throw new IllegalArgumentException("unclassified placement kinds: " + unclassifiedKinds);
        }

        TreeSet<AircraftBlockspaceIR.LatticePoint> manifestMainCoordinates = points(main);
        TreeSet<AircraftBlockspaceIR.LatticePoint> childCoordinates = points(child);
        TreeSet<AircraftBlockspaceIR.LatticePoint> overlap = new TreeSet<>(manifestMainCoordinates);
        overlap.retainAll(childCoordinates);
        if (!overlap.isEmpty()) {
            throw new IllegalArgumentException("main and child bodies overlap: " + overlap);
        }

        AircraftBlockspaceIR.LatticePoint seed = profile.seedCoordinate();
        SkyforgeAircraftAssemblyFixtureProfile.Translation offset = profile.assemblerOffsetFromSeed();
        AircraftBlockspaceIR.LatticePoint assemblerPoint = new AircraftBlockspaceIR.LatticePoint(
                seed.x() + offset.dx(),
                seed.y() + offset.dy(),
                seed.z() + offset.dz());
        boolean assemblerCoordinateFree = !manifestMainCoordinates.contains(assemblerPoint)
                && !childCoordinates.contains(assemblerPoint);
        if (!assemblerCoordinateFree) {
            throw new IllegalArgumentException("Physics Assembler fixture coordinate collides with probe manifest");
        }

        TreeSet<AircraftBlockspaceIR.LatticePoint> mainCoordinates = new TreeSet<>(manifestMainCoordinates);
        mainCoordinates.add(assemblerPoint);

        TreeMap<AircraftBlockspaceIR.LatticePoint, AircraftBlockspaceIR.LatticePoint> parent = new TreeMap<>();
        Set<AircraftBlockspaceIR.LatticePoint> reached = new HashSet<>();
        if (mainCoordinates.contains(seed)) {
            reached.add(seed);
            ArrayDeque<AircraftBlockspaceIR.LatticePoint> queue = new ArrayDeque<>();
            queue.add(seed);
            while (!queue.isEmpty()) {
                AircraftBlockspaceIR.LatticePoint current = queue.removeFirst();
                for (AircraftBlockspaceIR.LatticePoint neighbor : neighbors(current)) {
                    if (mainCoordinates.contains(neighbor) && reached.add(neighbor)) {
                        parent.put(neighbor, current);
                        queue.addLast(neighbor);
                    }
                }
            }
        }

        TreeSet<AircraftBlockspaceIR.LatticePoint> unreachable = new TreeSet<>(mainCoordinates);
        unreachable.removeAll(reached);
        ArrayList<SkyforgeAircraftAssemblyFixtureIR.Edge> edges = new ArrayList<>();
        parent.forEach((point, parentPoint) -> edges.add(new SkyforgeAircraftAssemblyFixtureIR.Edge(point, parentPoint)));
        edges.sort(Comparator.comparing(SkyforgeAircraftAssemblyFixtureIR.Edge::a)
                .thenComparing(SkyforgeAircraftAssemblyFixtureIR.Edge::b));

        TreeSet<AircraftBlockspaceIR.LatticePoint> bearingCoordinates = points(main.stream()
                .filter(placement -> "propeller_bearing".equals(placement.kind()))
                .toList());
        TreeSet<AircraftBlockspaceIR.LatticePoint> childHubCoordinates = points(child.stream()
                .filter(placement -> "propeller_hub".equals(placement.kind()))
                .toList());
        TreeSet<AircraftBlockspaceIR.LatticePoint> childSailCoordinates = points(child.stream()
                .filter(placement -> "propeller_sail".equals(placement.kind()))
                .toList());
        ArrayList<SkyforgeAircraftAssemblyFixtureIR.FaceAdjacency> bearingHubAdjacencies = new ArrayList<>();
        for (AircraftBlockspaceIR.LatticePoint bearing : bearingCoordinates) {
            Set<AircraftBlockspaceIR.LatticePoint> bearingNeighbors = Set.copyOf(neighbors(bearing));
            for (AircraftBlockspaceIR.LatticePoint hub : childHubCoordinates) {
                if (bearingNeighbors.contains(hub)) {
                    bearingHubAdjacencies.add(new SkyforgeAircraftAssemblyFixtureIR.FaceAdjacency(bearing, hub));
                }
            }
        }

        SkyforgeAircraftAssemblyFixtureIR.Edge assemblerSeedEdge =
                new SkyforgeAircraftAssemblyFixtureIR.Edge(assemblerPoint, seed);
        boolean nestedExcluded = edges.stream()
                .noneMatch(edge -> childCoordinates.contains(edge.a()) || childCoordinates.contains(edge.b()));
        Map<String, String> assemblerState = profile.physicsAssembler().blockState();
        SkyforgeAircraftAssemblyFixtureIR.TopologyChecks checks = new SkyforgeAircraftAssemblyFixtureIR.TopologyChecks(
                manifestMainCoordinates.contains(seed),
                assemblerCoordinateFree,
                assemblerPoint.equals(new AircraftBlockspaceIR.LatticePoint(seed.x(), seed.y() - 1, seed.z()))
                        && "ceiling".equals(assemblerState.get("face")),
                mainCoordinates.contains(assemblerPoint),
                edges.contains(assemblerSeedEdge),
                reached.size() == mainCoordinates.size() && !mainCoordinates.isEmpty(),
                edges.size() == Math.max(0, mainCoordinates.size() - 1),
                nestedExcluded,
                bearingCoordinates.size() == 1,
                childHubCoordinates.size() == 1 && !childSailCoordinates.isEmpty(),
                bearingHubAdjacencies.size() == 1);
        boolean topologyPassed = checks.passed();

        List<SkyforgeAircraftAssemblyFixtureIR.RuntimeObligation> obligations = profile.runtimeObligations().stream()
                .map(value -> new SkyforgeAircraftAssemblyFixtureIR.RuntimeObligation(value.id(), value.method(), "unverified"))
                .toList();
        List<String> mechanicalBlockers = topologyPassed
                ? List.of("adhesion_application_encoding_unresolved", "control_surface_child_body_topology_unresolved")
                : List.of("assembly_fixture_topology_failed");

        boolean assemblerResolved = checks.seedIsMainBodyPlacement()
                && checks.assemblerCoordinateFree()
                && checks.assemblerCeilingStickyFaceSeedsUpward()
                && checks.physicsAssemblerIncludedInMovingMainBody()
                && checks.physicsAssemblerHasAdhesionEdgeToSeed();
        boolean adhesionGraphResolved = checks.mainBodyAdjacencyGraphConnected()
                && checks.spanningTreeEdgeCountIsNMinusOne();

        return new SkyforgeAircraftAssemblyFixtureIR(
                SkyforgeAircraftAssemblyFixtureIR.SCHEMA_VERSION,
                fixtureAssetId,
                SkyforgeAircraftAssemblyFixtureIR.COMPILER_VERSION,
                manifest.sha256(),
                profile.profileId(),
                new SkyforgeAircraftAssemblyFixtureIR.PhysicsAssemblerPlacement(
                        assemblerPoint,
                        profile.physicsAssembler().resourceId(),
                        assemblerState,
                        seed,
                        "required",
                        "face=ceiling seeds UP; afterMove repairs assembler parent; disassembly path requires assembler to reside in the Sable sublevel"),
                new SkyforgeAircraftAssemblyFixtureIR.MainBody(
                        mainCoordinates.size(),
                        manifestMainCoordinates.size(),
                        1,
                        List.copyOf(mainCoordinates),
                        seed,
                        reached.size(),
                        List.copyOf(unreachable)),
                new SkyforgeAircraftAssemblyFixtureIR.NestedPropellerChild(
                        child.size(),
                        List.copyOf(childCoordinates),
                        bearingHubAdjacencies),
                new SkyforgeAircraftAssemblyFixtureIR.AdhesionIntent(
                        "face_adjacent_spanning_tree_over_moving_main_body_including_physics_assembler",
                        edges.size(),
                        edges,
                        "logical_graph_only_super_glue_entity_encoding_unresolved"),
                checks,
                obligations,
                new SkyforgeAircraftAssemblyFixtureIR.Metrics(
                        manifest.placements().size(),
                        manifest.placements().size() + 1,
                        manifestMainCoordinates.size(),
                        mainCoordinates.size(),
                        child.size(),
                        edges.size(),
                        unreachable.size(),
                        obligations.size(),
                        0),
                new SkyforgeAircraftAssemblyFixtureIR.Readiness(
                        topologyPassed,
                        assemblerResolved,
                        adhesionGraphResolved,
                        false,
                        false,
                        false,
                        false,
                        mechanicalBlockers),
                new SkyforgeAircraftAssemblyFixtureIR.Validation(
                        topologyPassed,
                        "physics_assembler_seed_and_moving_body_membership_plus_main_body_face_adjacency_adhesion_intent_topology",
                        List.of(
                                "Super Glue entity/selection encoding",
                                "actual Physics Assembler capture in the exact runtime",
                                "nested Propeller Bearing contraption capture",
                                "control-surface child-body topology",
                                "runtime mass or center of mass",
                                "runtime force sign or magnitude",
                                "flight qualification")));
    }

    private static TreeSet<AircraftBlockspaceIR.LatticePoint> points(List<SkyforgeAircraftProbeManifestIR.Placement> placements) {
        TreeSet<AircraftBlockspaceIR.LatticePoint> points = new TreeSet<>();
        placements.forEach(placement -> points.add(placement.point()));
        return points;
    }

    private static List<AircraftBlockspaceIR.LatticePoint> neighbors(AircraftBlockspaceIR.LatticePoint point) {
        return List.of(
                new AircraftBlockspaceIR.LatticePoint(point.x() - 1, point.y(), point.z()),
                new AircraftBlockspaceIR.LatticePoint(point.x(), point.y() - 1, point.z()),
                new AircraftBlockspaceIR.LatticePoint(point.x(), point.y(), point.z() - 1),
                new AircraftBlockspaceIR.LatticePoint(point.x(), point.y(), point.z() + 1),
                new AircraftBlockspaceIR.LatticePoint(point.x(), point.y() + 1, point.z()),
                new AircraftBlockspaceIR.LatticePoint(point.x() + 1, point.y(), point.z()));
    }
}
